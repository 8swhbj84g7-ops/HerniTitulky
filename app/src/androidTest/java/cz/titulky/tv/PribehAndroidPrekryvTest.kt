package cz.titulky.tv

import android.content.Intent
import android.os.StrictMode
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PribehAndroidPrekryvTest {
    /** Spouštět mimo hraní, s povoleným překryvem a na TV vstupu / v PlayCaption. */
    @Test fun udpReceiverAndActualOverlayAcknowledgePauseDoneAndCancellationOffMainThread() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val c = instrumentation.targetContext
        assumeTrue(!PrijemService.bezi && !RemotePlayService.aktivni)
        assumeTrue(Settings.canDrawOverlays(c) && Popredi(c).jeVstupTv())
        val autoStart = AutoStart.jeZapnuto(c)
        var policy: StrictMode.ThreadPolicy? = null
        instrumentation.runOnMainSync {
            policy = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build())
        }
        val token = java.util.UUID.randomUUID().toString().replace("-", "") + ".1.1.0"
        try {
            PrijemService.spust(c)
            PribehSpojeni("127.0.0.1", cas = { SystemClock.elapsedRealtime() }).use { phone ->
                fun await(id: String, state: String) {
                    val deadline = SystemClock.elapsedRealtime() + 7000
                    while (SystemClock.elapsedRealtime() < deadline) {
                        if (phone.odpoved?.first == PribehProtokol.Odpoved(id, state)) return
                        Thread.sleep(25)
                    }
                    fail("Chybí PCA1 $state pro $id; poslední odpověď ${phone.odpoved?.first}, chyba ${phone.chyba}")
                }
                phone.zprava = PribehProtokol.dotaz(token)
                await(token, "ready")
                phone.zprava = PribehProtokol.titulek(token, "Příběhový test.", true,
                    floatArrayOf(.5f, .9f, .8f, 1f), intArrayOf(), 50)
                await(token, "reading")
                Thread.sleep(1500)
                assertEquals("reading", phone.odpoved?.first?.stav)
                phone.zprava = PribehProtokol.titulek(token, "Příběhový test.", false,
                    floatArrayOf(.5f, .9f, .8f, 1f), intArrayOf(), 50)
                await(token, "done")
                Thread.sleep(800)
                assertEquals("done", phone.odpoved?.first?.stav)
                val cancel = token.substringBefore('.') + ".2.0.0"
                phone.zprava = PribehProtokol.titulek(cancel, "", true,
                    floatArrayOf(.5f, .9f, .8f, 1f), intArrayOf(), 50)
                await(cancel, "done")
            }
        } finally {
            c.stopService(Intent(c, PrijemService::class.java))
            val deadline = SystemClock.elapsedRealtime() + 3000
            while (PrijemService.bezi && SystemClock.elapsedRealtime() < deadline) Thread.sleep(25)
            instrumentation.runOnMainSync { policy?.let { StrictMode.setThreadPolicy(it) } }
            c.stopService(Intent(c, OverlayService::class.java))
            AutoStart.nastavZapnuto(c, autoStart)
        }
    }
}
