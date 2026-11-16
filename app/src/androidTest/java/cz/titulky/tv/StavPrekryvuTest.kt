package cz.titulky.tv

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StavPrekryvuTest {
    @Test fun serviceFallbackReportsSafeConcreteFailureAndClearsItAfterRecovery() {
        var foregroundFails = true
        var attempts = 0
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startService(service: Intent): ComponentName? {
                throw IllegalStateException("sensitive Intent contents")
            }
            override fun startForegroundService(service: Intent): ComponentName? {
                attempts++
                if (foregroundFails) throw SecurityException("secret account token")
                return ComponentName(this, OverlayService::class.java)
            }
        }
        val intent = Intent(context, OverlayService::class.java).setAction(OverlayService.ACTION_SET_TEXT)
        OverlayService.posli(context, intent)
        assertEquals("prekryvSluzba", OverlayService.chyba?.klic)
        assertEquals("SecurityException", OverlayService.chyba?.detail)
        assertFalse(Texty.spojeni("cs", OverlayService.chyba!!).contains("secret"))
        foregroundFails = false
        OverlayService.posli(context, intent)
        assertFalse(OverlayService.chyba?.klic == "prekryvSluzba")
        assertEquals(2, attempts)
    }
}
