package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class PribehSpojeniStavTest {
    @Test fun missingAckIsUnverifiedNotSocketFailureAndValidAckStillWorks() {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { tv ->
            tv.soTimeout = 2000
            val id = "a".repeat(32) + ".0.0.0"
            val connection = PribehSpojeni("127.0.0.1", tv.localPort) { System.nanoTime() / 1_000_000 }
            try {
                connection.zprava = PribehProtokol.dotaz(id)
                val packet = DatagramPacket(ByteArray(1024), 1024)
                tv.receive(packet)
                Thread.sleep(400) // skutečný receive timeout nesmí znamenat chybu soketu
                assertNull(connection.odpoved)
                assertNull(connection.chyba)
                val ack = "PCA1\t$id\tready".toByteArray()
                tv.send(DatagramPacket(ack, ack.size, packet.address, packet.port))
                val end = System.nanoTime() + 2_000_000_000
                while (connection.odpoved == null && System.nanoTime() < end) Thread.sleep(10)
                assertEquals(PribehProtokol.Odpoved(id, "ready"), connection.odpoved?.first)
                assertNull(connection.chyba)
            } finally { connection.close() }
        }
    }

    @Test fun invalidTargetReportsConcreteSafeFailure() {
        val connection = PribehSpojeni("256.256.256.256") { 0L }
        try {
            connection.zprava = PribehProtokol.dotaz("a".repeat(32) + ".0.0.0")
            val end = System.nanoTime() + 3_000_000_000
            while (connection.chyba == null && System.nanoTime() < end) Thread.sleep(10)
            assertEquals("UnknownHostException", connection.chyba)
        } finally { connection.close() }
    }
}
