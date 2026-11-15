package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class VysilacTest {
    @Test fun switchingClearsOldReceiverAndNeverReplaysOldTextOnNewReceiver() {
        val a = DatagramSocket(Propojeni.PORT_TITULKY, InetAddress.getByName("127.0.0.1"))
        // Dva cíle na jednom loopbacku fungují i na macOS bez dalšího síťového aliasu.
        val b = a
        a.soTimeout = 100
        b.soTimeout = 100
        fun read(s: DatagramSocket, ms: Long): List<String> {
            val out = mutableListOf<String>()
            val until = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < until) {
                val p = DatagramPacket(ByteArray(8192), 8192)
                try { s.receive(p); out.add(String(p.data, 0, p.length, Charsets.UTF_8)) }
                catch (_: SocketTimeoutException) {}
            }
            return out
        }
        try {
            Propojeni.Vysilac.nastavCil("127.0.0.1", "A")
            Propojeni.Vysilac.zmena("Jen na A", 6000, "Stará nápověda")
            Propojeni.Vysilac.nastavVolby(listOf("Staré volby"), 0)
            assertTrue(read(a, 200).any { "Jen na A" in it })
            Propojeni.Vysilac.nastavCil("localhost", "B")
            val cleared = read(a, 300)
            assertTrue(cleared.any { it.startsWith("PCT1") && it.split("\t")[3].isEmpty() })
            assertTrue(cleared.any { it.startsWith("PCV1") && it.split("\t")[8].isEmpty() })
            assertTrue(cleared.any { it.startsWith("PCO1\t0") })
            Propojeni.Vysilac.zmena("Jen na B", 6000, "")
            val new = read(b, 1300)
            assertTrue(new.any { "Jen na B" in it })
            assertFalse(new.any { "Jen na A" in it || "Stará nápověda" in it || "Staré volby" in it })
            assertTrue(read(a, 200).isEmpty())
            Propojeni.Vysilac.nastavCil(null, "")
            assertTrue(read(b, 300).any { it.startsWith("PCT1") && it.split("\t")[3].isEmpty() })
            assertTrue(read(b, 1200).isEmpty())
        } finally {
            Propojeni.Vysilac.nastavCil(null, "")
            a.close()
        }
    }
}
