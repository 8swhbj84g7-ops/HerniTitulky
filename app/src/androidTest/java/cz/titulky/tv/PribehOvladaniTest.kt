package cz.titulky.tv

import android.content.Intent
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PribehOvladaniTest {
    private fun prvky(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { prvky(v.getChildAt(it)) } else emptyList()

    @Test fun skutecnaTlacitkaAProcentaSeUkladajiBezResetuPriPrepnuti() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nast = Nastaveni(context)
        val puvodniRezim = nast.pribehovy
        val puvodniCas = nast.casPribehuProcenta
        try {
            nast.casPribehuProcenta = 100
            ActivityScenario.launch<TelefonActivity>(Intent(context, TelefonActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    val views = prvky(activity.window.decorView)
                    val standard = views.filterIsInstance<Button>().single { it.text == "Standardní" }
                    val pribehovy = views.filterIsInstance<Button>().single { it.text == "Příběhový" }
                    val cas = views.filterIsInstance<SeekBar>().single {
                        it.contentDescription == "Délka zobrazení příběhových titulků v procentech"
                    }
                    assertTrue(standard.isClickable && pribehovy.isClickable)
                    standard.performClick()
                    assertFalse(nast.pribehovy); assertTrue(standard.isSelected)
                    assertEquals(View.GONE, (cas.parent as View).visibility)
                    pribehovy.performClick()
                    assertTrue(nast.pribehovy); assertTrue(pribehovy.isSelected)
                    assertEquals(View.VISIBLE, (cas.parent as View).visibility)
                    cas.requestFocus()
                    cas.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                    cas.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
                    val zvoleno = nast.casPribehuProcenta
                    assertTrue("Posuvník musí měnit uložená procenta", zvoleno > 100)
                    assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == "Délka zobrazení: $zvoleno %" })
                    standard.performClick(); pribehovy.performClick()
                    assertEquals(zvoleno, Nastaveni(context).casPribehuProcenta)
                }
            }
        } finally { nast.casPribehuProcenta = puvodniCas; nast.pribehovy = puvodniRezim }
    }
}
