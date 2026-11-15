package cz.titulky.tv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Skutečný ML Kit nad stejnými uloženými PNG; bez filtrů Ctecka a bez překladu. */
@RunWith(AndroidJUnit4::class)
class OcrKvalityBenchmark {
    @Test fun porovnej() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assets = instrumentation.context.assets
        val files = assets.list("ocr-bench").orEmpty().filter { it.endsWith(".png") }.sorted()
        assumeTrue("Benchmark přeskočen: doplň lokální PNG podle tools/ocr/README.md", files.isNotEmpty())
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val output = JSONArray()
        try {
            for (file in files) {
                val bytes = assets.open("ocr-bench/$file").use { it.readBytes() }
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
                val source = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size)) { "Neplatné PNG: $file" }
                try {
                    for ((scale, mode) in listOf(1f to "normal", 1.5f to "normal", 2f to "normal",
                        3f to "normal", 2f to "inverse", 2f to "threshold")) {
                        val bitmap = Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
                        val input = if (mode == "normal") bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true).also { b ->
                            val pixels = IntArray(b.width * b.height)
                            b.getPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
                            for (i in pixels.indices) {
                                val gray = (pixels[i] shr 16) and 255
                                val v = if (mode == "inverse") 255 - gray else if (gray > 160) 255 else 0
                                pixels[i] = -0x1000000 or (v shl 16) or (v shl 8) or v
                            }
                            b.setPixels(pixels, 0, b.width, 0, 0, b.width, b.height)
                        }
                        try {
                            val start = SystemClock.elapsedRealtime()
                            val result = Tasks.await(recognizer.process(InputImage.fromBitmap(input, 0)), 30, TimeUnit.SECONDS)
                            val elapsed = SystemClock.elapsedRealtime() - start
                            val lines = result.textBlocks.flatMap { it.lines }
                            val confidence = lines.sumOf { it.confidence.toDouble() * it.text.length } /
                                lines.sumOf { it.text.length }.coerceAtLeast(1)
                            output.put(JSONObject().put("file", file).put("sha256", hash).put("scale", scale).put("mode", mode)
                                .put("ms", elapsed).put("text", result.text).put("confidence", confidence)
                                .put("heights", JSONArray(lines.map { it.boundingBox?.height() ?: 0 })))
                        } finally {
                            if (input !== bitmap) input.recycle()
                            if (bitmap !== source) bitmap.recycle()
                        }
                    }
                } finally { source.recycle() }
            }
            File(instrumentation.targetContext.filesDir, "ocr-porovnani.json").writeText(output.toString())
        } finally { recognizer.close() }
    }
}
