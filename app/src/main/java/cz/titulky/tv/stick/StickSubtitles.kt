package cz.titulky.tv.stick

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Surface
import cz.titulky.tv.CteciFronta
import cz.titulky.tv.Ctecka
import cz.titulky.tv.Prekladac
import java.util.ArrayDeque

/** PixelCopy reads only the decoder surface. Our Czech text never enters the OCR image. */
internal class StickSubtitles(context: Context, private val config: StickConfig, private val show: (String) -> Unit) {
    private val reader = Ctecka()
    private val translator = Prekladac(context, config.language, online = StickPlatform.onlineTranslation)
    private val main = Handler(Looper.getMainLooper())
    private val story = CteciFronta(maxReplik = 8)
    private val pending = ArrayDeque<Pair<String, Long>>()
    private var translating = false
    private var reading = false
    private var closed = false
    private var lastSample = -500L
    private var lastText = ""
    private var lastSeen = 0L
    private var shown = ""
    private var image: Bitmap? = null
    var latency: Long? = null
        private set
    var state = "čeká na anglické titulky"
        private set

    init {
        if (StickPlatform.onlineTranslation) translator.priprav({}, {})
        else {
            state = "připravuji jazykový model"
            translator.priprav(
                { if (!closed) state = "čeká na anglické titulky" },
                { if (!closed) state = "jazykový model není dostupný" }
            )
        }
    }

    fun sample(surface: Surface, width: Int, height: Int) {
        val now = SystemClock.elapsedRealtime()
        if (closed || reading || now - lastSample < 450 || !surface.isValid) return
        val w = minOf(width, 1280)
        val h = w * 9 / 16 / 3
        val target = image ?: Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { image = it }
        lastSample = now
        reading = true
        try {
            PixelCopy.request(surface, Rect(0, height - height / 3, width, height), target, { result ->
                if (!closed && result == PixelCopy.SUCCESS) {
                    reader.prectiPosledniTitulek(target, { line ->
                        reading = false
                        if (!closed) recognized(line.text.trim(), now)
                    }, { reading = false; state = "čtení obrazu selhalo" })
                } else {
                    reading = false
                    if (!closed) state = "výřez zatím není k dispozici"
                }
            }, main)
        } catch (_: IllegalArgumentException) { reading = false }
    }

    private fun recognized(text: String, sampleTime: Long) {
        val now = SystemClock.elapsedRealtime()
        if (text.isEmpty()) {
            if (now - lastSeen > 900) {
                lastText = ""
                if (!config.story) { pending.clear(); display("") }
            }
            return
        }
        lastSeen = now
        if (text == lastText) return
        lastText = text
        if (!config.story) {
            pending.clear()
            display("")
        }
        if (pending.size >= 4) pending.removeFirst()
        pending.addLast(text to sampleTime)
        translateNext()
    }

    private fun translateNext() {
        if (closed || translating) return
        val (text, time) = pending.pollFirst() ?: return
        val token = if (config.story) story.zarad(time) else null
        if (config.story && token == null) { pending.addFirst(text to time); return }
        translating = true
        state = "překládám"
        val ok: (String) -> Unit = { translated ->
            if (!closed) {
                translating = false
                latency = SystemClock.elapsedRealtime() - time
                state = "překlad připraven"
                if (translated.isNotBlank()) {
                    if (token != null) {
                        if (story.priprav(token, listOf(translated)) != CteciFronta.Prijeti.PRIJATO) story.chyba(token)
                    } else if (text == lastText) display(translated)
                } else if (token != null) story.chyba(token)
                translateNext()
            }
        }
        val error: (String) -> Unit = {
            if (!closed) {
                translating = false
                if (token != null) story.chyba(token)
                if (text == lastText) lastText = ""
                state = "překlad nedostupný"
                translateNext()
            }
        }
        if (config.story) translator.prelozPribeh(text, ok, error) else translator.preloz(text, ok, error)
    }
    fun tick(now: Long) {
        if (!closed && config.story) {
            display(story.zobraz(now)?.text.orEmpty())
            translateNext()
        }
    }
    private fun display(value: String) {
        if (shown != value) { shown = value; show(value) }
    }
    fun close() {
        closed = true
        pending.clear()
        translator.zavri()
        reader.zavri()
        image = null
        display("")
    }
}
