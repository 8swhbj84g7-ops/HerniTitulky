package cz.titulky.tv.stick

import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.hardware.input.InputManager
import android.os.*
import android.text.InputFilter
import android.text.InputType
import android.view.*
import android.widget.*
import com.google.mlkit.common.MlKit
import com.metallic.chiaki.lib.*
import java.util.concurrent.Executors

class StickGame : Activity(), TextureView.SurfaceTextureListener, InputManager.InputDeviceListener {
    companion object {
        // A recreated activity must also wait for the previous native session to join.
        private val native = Executors.newSingleThreadExecutor()
    }
    private val ui = Handler(Looper.getMainLooper())
    private val config by lazy { StickConfig(this) }
    private val q by lazy { config.quality }
    private var pad = StickPad(0)
    private lateinit var screen: TextureView
    private lateinit var caption: TextView
    private lateinit var status: TextView
    private var surface: Surface? = null
    private var owner: Session? = null // accessed exclusively on native executor
    private var input: Session? = null // UI only, invalidated before disposal is enqueued
    private var subtitles: StickSubtitles? = null
    private var pinDialog: AlertDialog? = null
    private var foreground = false
    private var connecting = false
    private var generation = 0L
    private var retries = 0
    private var frameCount = 0
    private var lastFrame = 0L
    private var firstFrame = 0L
    private var started = 0L
    private var measured = 0L
    private var phase = "Čekám na obrazovku"
    private var lastReport = ""
    private val retry = Runnable { startStream() }
    private val metrics = object : Runnable {
        override fun run() {
            if (!foreground) return
            val now = SystemClock.elapsedRealtime()
            subtitles?.tick(now)
            if (now - measured >= 1000) {
                val fps = frameCount * 1000.0 / (now - measured).coerceAtLeast(1)
                sampleMemory(now)
                val delay = subtitles?.latency?.let { "$it ms" } ?: "—"
                lastReport = "$phase · ${q.label}\nVykresleno %.1f FPS".format(fps) +
                    " · $memory" +
                    "\nVýřez → překlad $delay · ${subtitles?.state ?: "čeká"}\nOvladač · ${pad.name}"
                status.text = if (config.prefs.getInt("stats", 0) == 1) lastReport
                    else if (firstFrame == 0L) phase else ""
                status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
                measured = now
                frameCount = 0
                if (connecting && pinDialog == null && now - maxOf(lastFrame, started) > 20000)
                    failed("PS5 neposílá obraz")
            }
            ui.postDelayed(this, 200)
        }
    }

    private var memory = "měřím paměť"
    private var memoryAt = -5000L
    private var memoryPending = false

    private fun sampleMemory(now: Long) {
        if (memoryPending || now - memoryAt < 5000) return
        memoryAt = now
        memoryPending = true
        Thread {
            val info = ActivityManager.MemoryInfo()
            getSystemService(ActivityManager::class.java).getMemoryInfo(info)
            val pss = Debug.getPss() / 1024
            ui.post {
                memory = "paměť hry $pss MB · volno v systému ${info.availMem / 1048576} MB"
                memoryPending = false
            }
        }.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StickRuntime.prepare()
        MlKit.initialize(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        screen = TextureView(this).apply { surfaceTextureListener = this@StickGame }
        root.addView(screen, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = minOf(r - l, (b - t) * 16 / 9)
            val h = w * 9 / 16
            val params = screen.layoutParams
            if (params.width != w || params.height != h) { params.width = w; params.height = h; screen.layoutParams = params }
        }
        val dp = resources.displayMetrics.density
        caption = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = listOf(18f, 24f, 32f)[config.prefs.getInt("size", 1)]
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
            setPadding((12*dp).toInt(), (4*dp).toInt(), (12*dp).toInt(), (4*dp).toInt())
            setBackgroundColor(Color.argb(listOf(0,128,204,255)[config.prefs.getInt("opacity", 2)],0,0,0))
            visibility = View.GONE
        }
        val edge = if (config.prefs.getInt("position", 0) == 0) Gravity.BOTTOM else Gravity.TOP
        root.addView(caption, FrameLayout.LayoutParams(-2,-2, edge or Gravity.CENTER_HORIZONTAL).apply {
            setMargins((24*dp).toInt(), (28*dp).toInt(), (24*dp).toInt(), (28*dp).toInt())
        })
        status = TextView(this).apply {
            textSize = 15f; setPadding(16, 8, 16, 8); setTextColor(Color.WHITE)
            setBackgroundColor(0xBE17232D.toInt()); text = phase
        }
        root.addView(status, FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.START))
        setContentView(root)
    }
    override fun onStart() {
        super.onStart()
        foreground = true
        measured = SystemClock.elapsedRealtime()
        pad = StickPad(config.prefs.getInt("axes", 0))
        getSystemService(InputManager::class.java).registerInputDeviceListener(this, ui)
        startStream()
        ui.post(metrics)
    }
    override fun onStop() {
        foreground = false
        getSystemService(InputManager::class.java).unregisterInputDeviceListener(this)
        ui.removeCallbacks(metrics)
        if (lastReport.isNotBlank()) config.prefs.edit().putString("report", lastReport).apply()
        stopStream()
        super.onStop()
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        texture.setDefaultBufferSize(q.width, q.height)
        surface = Surface(texture)
        startStream()
    }
    override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) { t.setDefaultBufferSize(q.width, q.height) }
    override fun onSurfaceTextureDestroyed(t: SurfaceTexture): Boolean {
        val old = surface
        surface = null
        stopStream()
        native.execute { old?.release(); t.release() }
        return false
    }
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
        if (!connecting) return
        val now = SystemClock.elapsedRealtime()
        if (firstFrame == 0L) { firstFrame = now; phase = "Hraní" }
        if (now - firstFrame > 15000) retries = 0
        frameCount++
        lastFrame = now
        surface?.let { subtitles?.sample(it, q.width, q.height) }
    }

    private fun startStream() {
        val output = surface ?: return
        if (!foreground || connecting || isFinishing) return
        if (!config.paired) { phase = "Nejdřív připoj PS5 v hlavní nabídce."; return }
        val id = ++generation
        connecting = true
        phase = "Připojuji PS5…"
        started = SystemClock.elapsedRealtime()
        lastFrame = 0L
        firstFrame = 0L
        subtitles = StickSubtitles(this, config) { text ->
            caption.text = text
            caption.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
        }
        native.execute {
            try {
                val connection = config.connection()
                val s = try { Session(connection, null, false) }
                    finally { connection.registKey.fill(0); connection.morning.fill(0) }
                owner = s
                s.eventCallback = { event ->
                    ui.post {
                        if (id == generation) when (event) {
                            ConnectedEvent -> if (firstFrame == 0L) phase = "Připojeno · čekám na obraz"
                            is LoginPinRequestEvent -> loginPin(s, event.pinIncorrect)
                            is QuitEvent -> failed("PS5 ukončila spojení (${event.reason})")
                            else -> Unit
                        }
                    }
                }
                s.setSurface(output)
                check(s.start().isSuccess)
                ui.post { if (id == generation) { input = s; s.setControllerState(pad.state) } }
            } catch (_: Exception) {
                ui.post { if (id == generation) failed("Spojení se nepodařilo spustit") }
            } catch (_: LinkageError) {
                ui.post { if (id == generation) failed("Herní knihovna pro tento procesor není dostupná") }
            }
        }
    }

    private fun stopStream() {
        generation++
        ui.removeCallbacks(retry)
        pinDialog?.dismiss(); pinDialog = null
        input = null
        connecting = false
        firstFrame = 0L
        subtitles?.close()
        subtitles = null
        native.execute {
            owner?.let { s -> s.eventCallback = null; try { s.stop() } finally { s.dispose() } }
            owner = null
        }
    }
    private fun failed(reason: String) {
        stopStream()
        if (foreground && retries < 3 && config.prefs.getInt("retry",0) == 0) {
            retries++
            phase = "$reason · další pokus $retries/3"
            ui.postDelayed(retry, retries * 2000L)
        } else phase = "$reason · stiskni OK pro nové spojení"
    }
    private fun loginPin(s: Session, incorrect: Boolean) {
        pinDialog?.dismiss()
        val field = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(4))
            isSaveEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        pinDialog = AlertDialog.Builder(this).setTitle(if (incorrect) "Nesprávný PIN profilu PS5" else "PIN profilu PS5")
            .setView(field).setPositiveButton("Odeslat") { _, _ ->
                s.setLoginPin(field.text.toString())
                started = SystemClock.elapsedRealtime()
                pinDialog = null
            }.setNegativeButton("Ukončit") { _, _ -> finish() }.setOnCancelListener { finish() }.create()
            .also { dialog ->
                dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialog.show()
            }
    }

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (pinDialog?.isShowing != true && input != null && pad.key(e)) { input?.setControllerState(pad.state); return true }
        if (!connecting && e.action == KeyEvent.ACTION_DOWN && e.keyCode in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BUTTON_A)) {
            ui.removeCallbacks(retry); retries = 0; startStream(); return true
        }
        return super.dispatchKeyEvent(e)
    }
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        if (input != null && pad.move(e)) { input?.setControllerState(pad.state); return true }
        return super.onGenericMotionEvent(e)
    }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus && input != null) resetPad()
    }
    private fun resetPad() {
        pad = StickPad(config.prefs.getInt("axes", 0))
        input?.setControllerState(pad.state)
    }
    override fun onInputDeviceRemoved(id: Int) {
        if (id == pad.deviceId) resetPad()
    }
    override fun onInputDeviceAdded(id: Int) {}
    override fun onInputDeviceChanged(id: Int) { onInputDeviceRemoved(id) }
}
