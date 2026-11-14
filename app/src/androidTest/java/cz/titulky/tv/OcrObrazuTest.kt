package cz.titulky.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

/** Skutečný ML Kit; vyžaduje Android zařízení nebo emulátor, nikoli konzoli či LG. */
@RunWith(AndroidJUnit4::class)
class OcrObrazuTest {
    @Test fun triRadkyInstrukceSeZaznamemPuvodnichBloku() {
        val radky = listOf("The Shoebox can store your items. Alan",
            "can access stored items from any", "Shoebox in the Dark Place.")
        val bitmapa = Bitmap.createBitmap(1200, 300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmapa)
        canvas.drawColor(Color.BLACK)
        val stetec = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 40f }
        radky.forEachIndexed { i, text -> canvas.drawText(text, 40f, 70f + 65f * i, stetec) }
        val ctecka = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val vysledek = Tasks.await(ctecka.process(InputImage.fromBitmap(bitmapa, 0)), 30, TimeUnit.SECONDS)
            val bloky = JSONArray()
            val kusy = vysledek.textBlocks.flatMapIndexed { i, b ->
                val zaznam = JSONObject().put("blok", i).put("text", b.text)
                    .put("box", b.boundingBox?.flattenToString())
                val zaznamRadku = JSONArray()
                val kusyBloku = b.lines.mapNotNull { l -> l.boundingBox?.let { r ->
                    zaznamRadku.put(JSONObject().put("text", l.text).put("box", r.flattenToString()))
                    OcrKus(l.text, r.left, r.top, r.right, r.bottom, i)
                } }
                bloky.put(zaznam.put("radky", zaznamRadku))
                kusyBloku
            }
            val kontext = InstrumentationRegistry.getInstrumentation().targetContext
            kontext.openFileOutput("ocr-synteticka-instrukce.json", 0).bufferedWriter().use {
                it.write(bloky.toString(2))
            }
            // Gradle po testu aplikaci odstraní; syntetický výsledek zůstane také v testovacím logu.
            println("PLAYCAPTION_OCR_FIXTURE=" + bloky.toString())
            assertTrue("OCR musí vrátit souřadnice všech tří řádků: $bloky", kusy.size >= 3)
            val prectene = kusy.sortedBy { it.top }.map { it.text }
            assertEquals(radky.joinToString(" "), TextVRamecku.spoj(prectene))
            val celaCtecka = Ctecka()
            try {
                val dokonceno = CountDownLatch(1)
                var text: String? = null
                var chyba: String? = null
                celaCtecka.prectiVolby(bitmapa, { radkyOcr ->
                    text = TextVRamecku().zpracuj(radkyOcr.map { it.first }, true, 0)
                    dokonceno.countDown()
                }, { chyba = it; dokonceno.countDown() })
                assertTrue("Celá čtečka nedokončila OCR", dokonceno.await(30, TimeUnit.SECONDS))
                assertNull(chyba)
                assertEquals(radky.joinToString(" "), text)
            } finally { celaCtecka.zavri() }
        } finally {
            ctecka.close()
            bitmapa.recycle()
        }
    }

    @Test fun dialogyPresSkutecnouCteckuAJazykovyFiltr() {
        val scenare = listOf(
            listOf("Wait for me!"),
            listOf("Arthur: We should", "go back home."),
            listOf("Arthur: We have to find", "the way back", "to our home.")
        )
        val ctecka = Ctecka()
        try {
            for (radky in scenare) {
                val obraz = Bitmap.createBitmap(1200, 300, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(obraz)
                    canvas.drawColor(Color.BLACK)
                    val stetec = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; textSize = 40f; textAlign = Paint.Align.CENTER
                    }
                    radky.forEachIndexed { i, text -> canvas.drawText(text, 600f, 70f + 52f * i, stetec) }
                    val dokonceno = CountDownLatch(1)
                    var text: String? = null
                    var chyba: String? = null
                    ctecka.prectiPosledniTitulek(obraz, { vysledek ->
                        text = vysledek.text; dokonceno.countDown()
                    }, { chyba = it; dokonceno.countDown() })
                    assertTrue("Čtečka nedokončila OCR", dokonceno.await(30, TimeUnit.SECONDS))
                    assertNull(chyba)
                    assertEquals("Diagnostika: ${ctecka.diagnostika}", radky.joinToString(" "), text)
                } finally { obraz.recycle() }
            }
        } finally { ctecka.zavri() }
    }
}
