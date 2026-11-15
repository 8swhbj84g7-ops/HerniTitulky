package cz.titulky.tv

import android.os.StrictMode
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

@RunWith(AndroidJUnit4::class)
class PribehSitTest {
    private fun hlavniVlakno(block: () -> Unit) {
        var chyba: Throwable? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val pred = StrictMode.getThreadPolicy()
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeathOnNetwork().build())
            try { block() } catch (e: Throwable) { chyba = e }
            finally { StrictMode.setThreadPolicy(pred) }
        }
        chyba?.let { throw AssertionError("Síťová operace na hlavním vlákně", it) }
    }
    @Test fun otevreniOdeslaniZruseniAUkonceniNedelajiSitNaHlavnimVlakne() {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { tv ->
            tv.soTimeout = 4000
            lateinit var spojeni: PribehSpojeni
            val id = "d".repeat(32) + ".0.0.0"
            hlavniVlakno { spojeni = PribehSpojeni("127.0.0.1", tv.localPort, { SystemClock.elapsedRealtime() }); spojeni.zprava = PribehProtokol.dotaz(id) }
            try {
                val packet = DatagramPacket(ByteArray(1024), 1024); tv.receive(packet)
                assertEquals(PribehProtokol.dotaz(id), String(packet.data,0,packet.length,Charsets.UTF_8))
                val cancel = PribehProtokol.titulek("d".repeat(32)+".1.0.0", "", true, floatArrayOf(.5f,.9f,.8f,1f), intArrayOf())
                hlavniVlakno { spojeni.zrus(cancel); spojeni.close() }
                var received = ""
                val konec = SystemClock.elapsedRealtime()+4000
                while (received != cancel && SystemClock.elapsedRealtime()<konec) {
                    tv.receive(packet);received=String(packet.data,0,packet.length,Charsets.UTF_8)
                }
                assertEquals(cancel, received)
            } finally { hlavniVlakno { spojeni.close() } }
        }
    }
    @Test fun skutecneLgPotvrdiStoryProtokol() {
        val host = InstrumentationRegistry.getArguments().getString("lgHost")
        assumeTrue("Volitelný test skutečné LG vyžaduje lgHost", !host.isNullOrBlank())
        lateinit var spojeni: PribehSpojeni
        val id = java.util.UUID.randomUUID().toString().replace("-", "")+".0.0.0"
        hlavniVlakno { spojeni = PribehSpojeni(host!!, cas = { SystemClock.elapsedRealtime() });spojeni.zprava=PribehProtokol.dotaz(id) }
        try {
            val konec = SystemClock.elapsedRealtime()+7000
            while (spojeni.odpoved == null && SystemClock.elapsedRealtime()<konec) Thread.sleep(20)
            assertEquals(PribehProtokol.Odpoved(id,"ready"),spojeni.odpoved?.first)
        } finally { hlavniVlakno { spojeni.close() } }
    }
}
