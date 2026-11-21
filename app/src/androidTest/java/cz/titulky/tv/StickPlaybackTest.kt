package cz.titulky.tv

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.metallic.chiaki.lib.ControllerState
import com.metallic.chiaki.lib.Session
import cz.titulky.tv.stick.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StickPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext
    private val testContext = object : ContextWrapper(app) {
        override fun getSharedPreferences(name: String, mode: Int) =
            app.getSharedPreferences("test_only_" + name, mode)
    }

    @Test fun encryptedPairingAndArmControllerInterface() {
        val config = StickConfig(testContext)
        val reg = ByteArray(16) { (it + 1).toByte() }
        val key = ByteArray(16) { (it + 24).toByte() }
        config.save("127.0.0.1", reg, key)
        val saved = StickConfig(testContext).connection()
        assertArrayEquals(reg, saved.registKey)
        assertArrayEquals(key, saved.morning)
        assertFalse(config.prefs.getString("console", "")!!.contains("127.0.0.1"))
        StickRuntime.prepare()
        // Only exercises the real ARM JNI structure, without starting a network connection.
        val session = Session(saved, null, false)
        try { session.setControllerState(ControllerState(buttons = ControllerState.BUTTON_CROSS)) }
        finally { session.dispose() }
    }

    @Test fun hd30() = decodedVideo("dialog", Quality.HD)
    @Test fun hd60() = decodedVideo("dialog60", Quality.HD60)
    @Test fun fullHd30() = decodedVideo("dialog1080", Quality.FULL)

    private fun decodedVideo(clip: String, quality: Quality) {
        val activity = instrumentation.startActivitySync(Intent(app, VyvojActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val captionReady = CountDownLatch(1)
        val firstFrame = CountDownLatch(1)
        val measured = CountDownLatch(1)
        val windowMs = InstrumentationRegistry.getArguments().getString("windowMs")?.toLongOrNull()?.coerceIn(8000, 180000) ?: 8000L
        val config = StickConfig(testContext)
        val player = instrumentation.context.assets.openFd("stick/$clip.mp4").use { FixtureFrames(it, quality.fps) }
        var pipeline: StickSubtitles? = null
        var output: Surface? = null
        var shown = ""
        var frames = 0
        var first = 0L
        var elapsed = 0L
        var sampleFrames = 0
        var baselineMs = 0L
        var memoryStart = 0L
        var summary = ""
        var latency: Long? = null
        lateinit var overlay: TextView
        try {
            instrumentation.runOnMainSync {
                pipeline = StickSubtitles(activity, config) { line ->
                    overlay.text = line
                    if (line.isNotBlank()) {
                        shown = overlay.text.toString()
                        elapsed = SystemClock.elapsedRealtime() - first
                        latency = pipeline?.latency
                        captionReady.countDown()
                    }
                }
                val video = TextureView(activity)
                val root = FrameLayout(activity)
                overlay = TextView(activity)
                root.addView(video)
                root.addView(overlay, FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM))
                video.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(t: SurfaceTexture, w: Int, h: Int) {
                        t.setDefaultBufferSize(quality.width, quality.height)
                        output = Surface(t)
                        player.start(requireNotNull(output), quality.width, quality.height)
                    }
                    override fun onSurfaceTextureUpdated(t: SurfaceTexture) {
                        if (first == 0L) { first = SystemClock.elapsedRealtime(); firstFrame.countDown() }
                        frames++
                        output?.let { pipeline?.sample(it,quality.width,quality.height) }
                        val now = SystemClock.elapsedRealtime()
                        if (baselineMs == 0L && now - first > 1500) {
                            baselineMs = now
                            sampleFrames = frames
                            memoryStart = android.os.Debug.getPss()
                        } else if (baselineMs > 0 && now - baselineMs >= windowMs && summary.isEmpty()) {
                            val fps = (frames - sampleFrames) * 1000.0 / (now - baselineMs)
                            summary = "fps=%.1f, pssKiB=%d->%d".format(java.util.Locale.US, fps, memoryStart, android.os.Debug.getPss())
                            measured.countDown()
                        }
                    }
                    override fun onSurfaceTextureDestroyed(t: SurfaceTexture) = true
                    override fun onSurfaceTextureSizeChanged(t: SurfaceTexture, w: Int, h: Int) {}
                }
                activity.setContentView(root)
            }
            assertTrue("Dekodér nepředal první snímek na plochu", firstFrame.await(12, TimeUnit.SECONDS))
            val received = captionReady.await(45,TimeUnit.SECONDS)
            assertTrue("Výřez se nepřeložil: ${pipeline?.state}, snímků $frames", received)
            assertTrue("Chybí vzorek vykreslování", measured.await(windowMs + 8000,TimeUnit.MILLISECONDS))
            instrumentation.runOnMainSync { assertTrue(overlay.isShown && overlay.width > 0 && overlay.height > 0) }
            assertTrue("Překlad testovací věty: $shown", Regex("(?i)(počkej|čekej|počk|čekat)").containsMatchIn(shown))
            android.util.Log.i("HerniTest", "${quality.label}, $summary, firstCaptionMs=$elapsed, captionMs=$latency, translation=$shown")
        } finally {
            instrumentation.runOnMainSync {
                player.close()
                output?.release()
                pipeline?.close()
                pipeline = null
                activity.finish()
            }
        }
    }
}
