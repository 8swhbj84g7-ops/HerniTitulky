package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class PribehPrijimacTest {
    private val root = "0123456789abcdef0123456789abcdef"
    private fun paket(n: Int = 1, text: String = "Celá česká replika.", pauza: Boolean = false, generace: Int = 1,
        procenta: Int = 100) = PribehProtokol.Titulek("$root.$generace.$n.0", text, pauza,
        floatArrayOf(.5f, .9f, .8f, 1f), intArrayOf(-65536), procenta)
    private fun PribehPrehravac.ukaz(ted: Long, skryto: Boolean = false) {
        krok(ted, skryto)
        if (text.isNotEmpty()) zobrazeno(ted)
    }
    private fun prehravac() = PribehPrehravac { text, _ -> minOf(20, text.length) }

    @Test fun parserSeparatesStoryFromPairingAndRejectsMalformedPackets() {
        val p = paket(text = "Arthur: Řádek\\cesta\nDruhý řádek.")
        val raw = PribehProtokol.titulek(p.id, p.text, p.pauza, p.rozvrh, p.barvy, 80)
        val parsed = PribehProtokol.prijmi(raw)!!.titulek!!
        assertEquals(p.text, parsed.text); assertEquals(80, parsed.procenta)
        assertArrayEquals(p.barvy, parsed.barvy)
        assertNull(PribehProtokol.prijmi(raw.substringBeforeLast('\t'))!!.titulek!!.procenta)
        assertNull(PribehProtokol.prijmi("PCS1\tZADOST\tTelefon"))
        assertNull(PribehProtokol.prijmi("PCS1\tKOD\t1234"))
        assertNull(PribehProtokol.prijmi("PCQ1\tx"))
        for (bad in listOf(raw.replace("D:80", "D:49"), raw.replace("0.5", "NaN"), raw + "\textra",
            raw.replace("\t0\t", "\t2\t"), "PCQ1\t${p.id}\textra")) assertNull(bad, PribehProtokol.prijmi(bad))
        assertEquals(p.id, PribehProtokol.prijmi(PribehProtokol.dotaz(p.id))!!.id)
        assertNull(PribehProtokol.prijmi("PCS1\t" + "x".repeat(8192)))
    }

    @Test fun repeatedPacketDoesNotRestartReadingAndDoneCanBeAcknowledgedAgain() {
        val s = prehravac(); val p = paket(text = "Krátká replika.")
        assertTrue(s.prijmi(p, 0)); s.ukaz(0)
        val duration = CasovaniPribehu.dobaMs(p.text, 100)
        s.prijmi(p, duration - 1); s.ukaz(duration - 1)
        assertEquals("reading", s.stav); assertEquals(p.text, s.text)
        s.ukaz(duration); assertEquals("done", s.stav); assertEquals("", s.text)
        s.prijmi(p, duration + 100); s.ukaz(duration + 100)
        assertEquals("done", s.stav); assertEquals("", s.text)
    }

    @Test fun slowMeasurementCannotConsumeReadingTimeBeforeFirstDisplay() {
        val s = prehravac(); val p = paket(text = "Ještě neukázáno.")
        s.prijmi(p, 0); s.krok(2000)
        assertEquals("reading", s.stav); assertEquals(p.text, s.text)
        s.zobrazeno(2000)
        val duration = CasovaniPribehu.dobaMs(p.text, 100)
        s.krok(2000 + duration - 1); assertEquals("reading", s.stav)
        s.krok(2000 + duration); assertEquals("done", s.stav)
    }

    @Test fun longTextIsPagedWithoutLossAndLateTickDoesNotSkipUnreadPages() {
        val s = prehravac(); val p = paket(text = "A".repeat(65))
        s.prijmi(p, 0); s.ukaz(0)
        val pages = mutableListOf(s.text)
        var now = 0L
        while (s.stav != "done") {
            now += 4000
            s.prijmi(p, now); s.ukaz(now)
            if (s.text.isNotEmpty()) pages.add(s.text)
        }
        assertEquals(p.text, pages.joinToString("")); assertEquals(4, pages.size)
    }

    @Test fun choicesHiddenInputAndDisconnectPauseRemainingReadingTime() {
        val s = prehravac(); val p = paket(text = "Pozastaveno.")
        s.prijmi(p, 0); s.ukaz(0); s.ukaz(200, skryto = true)
        s.prijmi(p.copy(pauza = true), 500); s.ukaz(500)
        s.ukaz(10000); assertEquals("reading", s.stav); assertEquals("", s.text)
        s.prijmi(p, 10000); s.ukaz(10000)
        val remaining = CasovaniPribehu.dobaMs(p.text, 100) - 200
        s.ukaz(10000 + remaining - 1); assertEquals(p.text, s.text)
        s.ukaz(10000 + remaining); assertEquals("done", s.stav)
        val long = paket(2, "X".repeat(200))
        s.prijmi(long, 20000); s.ukaz(20000); s.ukaz(26000)
        assertEquals("", s.text); assertEquals("reading", s.stav)
        s.prijmi(long, 30000); s.ukaz(30000); assertTrue(s.text.isNotEmpty())
    }

    @Test fun cancellationRejectsOldGenerationAndNewSessionRejectsLateOldSession() {
        val s = prehravac(); val p = paket()
        s.prijmi(p, 0); s.ukaz(0)
        assertTrue(s.prijmi(paket(0, "", true, 2), 100)); assertFalse(s.aktivni)
        assertFalse(s.prijmi(p, 200)); assertEquals("", s.text)
        val next = paket().copy(id = "f".repeat(32) + ".1.1.0")
        assertTrue(s.prijmi(next, 300)); s.ukaz(300)
        assertFalse(s.prijmi(paket(2, generace = 2), 400))
        assertEquals(next.text, s.text)
    }

    @Test fun durationChangesApplyToNextPageAndColorsFollowOriginalLineOffsets() {
        val s = prehravac(); val p = paket(text = "Arthur: První řádek.\nJohn: Druhý řádek.").copy(barvy = intArrayOf(-65536, -16711936))
        s.prijmi(p, 0); s.ukaz(0)
        val first = s.text; val duration = CasovaniPribehu.dobaMs(first, 100)
        s.prijmi(p.copy(procenta = 50), 100); s.ukaz(duration - 1)
        assertEquals(first, s.text)
        s.ukaz(duration); assertTrue(s.text.startsWith("John:")); assertEquals(-16711936, s.barvy[0])
        s.ukaz(duration + CasovaniPribehu.dobaMs(s.text, 50)); assertEquals("done", s.stav)
    }

    @Test fun paginationKeepsWordsSentencesAndUnicodeCodePoints() {
        assertEquals(1, PribehStranky.konec("A") { true })
        assertEquals(11, PribehStranky.konec("První věta. Další věta.") { it.length <= 20 })
        val text = "😀".repeat(10)
        val end = PribehStranky.konec(text) { it.codePointCount(0, it.length) <= 3 }
        assertEquals("😀".repeat(3), text.substring(0, end))
    }

    @Test fun initialPauseLayoutChangeAndLegacyTimingDoNotLoseUnreadContent() {
        val s = prehravac(); val p = paket(text = "X".repeat(40), pauza = true).copy(procenta = null)
        s.prijmi(p, 0); s.ukaz(0); assertEquals("", s.text)
        s.prijmi(p.copy(pauza = false), 10000); s.ukaz(10000)
        val first = s.text
        s.ukaz(11799); assertEquals(first, s.text); assertEquals("reading", s.stav)
        s.prijmi(p.copy(pauza = false, rozvrh = floatArrayOf(.4f, .9f, .6f, 1f)), 11800)
        s.ukaz(11800); assertEquals(first, s.text)
        s.prijmi(p.copy(pauza = false, rozvrh = floatArrayOf(.4f, .9f, .6f, 1f)), 13599)
        s.ukaz(13599); assertEquals(first, s.text)
        s.ukaz(13600); assertEquals(20, s.text.length)
    }

    @Test fun actualUdpQueryAndStoryReturnReadyReadingAndDoneWithoutPairingCollision() {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { server ->
            DatagramSocket().use { phone ->
                phone.soTimeout = 1000
                var now = 0L
                val player = prehravac()
                val receiver = PribehPrijem({ packet, ack ->
                    if (player.prijmi(packet, now)) { player.ukaz(now); ack(player.stav) }
                }, { now })
                fun exchange(raw: String): String {
                    val data = raw.toByteArray()
                    phone.send(DatagramPacket(data, data.size, InetAddress.getLoopbackAddress(), server.localPort))
                    val incoming = DatagramPacket(ByteArray(8192), 8192); server.receive(incoming)
                    assertTrue(receiver.zpracuj(incoming))
                    receiver.odesliOdpovedi(server)
                    val answer = DatagramPacket(ByteArray(256), 256); phone.receive(answer)
                    assertEquals(server.localPort, answer.port)
                    return String(answer.data, 0, answer.length)
                }
                val p = paket(text = "Celá replika.")
                assertEquals("PCA1\t${p.id}\tready", exchange(PribehProtokol.dotaz(p.id)))
                val raw = PribehProtokol.titulek(p.id, p.text, false, p.rozvrh, p.barvy)
                assertEquals("PCA1\t${p.id}\treading", exchange(raw))
                now = CasovaniPribehu.dobaMs(p.text, 100)
                assertEquals("PCA1\t${p.id}\tdone", exchange(raw))
                val pairing = "PCS1\tZADOST\ttelefon".toByteArray()
                assertFalse(receiver.zpracuj(DatagramPacket(pairing, pairing.size)))
            }
        }
    }

    @Test fun overlayCallbackOnlyQueuesAckUntilNetworkWorkerFlushesIt() {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { server ->
            DatagramSocket().use { phone ->
                phone.soTimeout = 50
                var callback: ((String) -> Unit)? = null
                val receiver = PribehPrijem({ _, ack -> callback = ack }, { 0L })
                val p = paket()
                val data = PribehProtokol.titulek(p.id, p.text, false, p.rozvrh, p.barvy).toByteArray()
                assertTrue(receiver.zpracuj(DatagramPacket(data, data.size, InetAddress.getLoopbackAddress(), phone.localPort)))
                callback!!("reading")
                try { phone.receive(DatagramPacket(ByteArray(256), 256)); fail("ACK se odeslalo mimo síťové vlákno") }
                catch (_: java.net.SocketTimeoutException) { }
                receiver.odesliOdpovedi(server)
                val answer = DatagramPacket(ByteArray(256), 256); phone.receive(answer)
                assertEquals("PCA1\t${p.id}\treading", String(answer.data, 0, answer.length))
            }
        }
    }

    @Test fun pairingStandardChoicesAndUnsupportedReceiverStayOnTheirExistingPaths() {
        val receiver = PribehPrijem(null, { 0L })
        for (raw in listOf("PCS1\tZADOST\tTelefon", "PCS1\tKOD\t1234", "PCS1\tDATA\taccount",
            "PCT1\t1\t6000\tStandardní\t", "PCV1\t1\t0\t0\t0\t1\t1\t0\tAno", "PCO1\t1\t0\t0\t1\t1")) {
            val data = raw.toByteArray(); assertFalse(raw, receiver.zpracuj(DatagramPacket(data, data.size)))
        }
        DatagramSocket().use { socket ->
            socket.soTimeout = 50
            val query = PribehProtokol.dotaz(paket().id).toByteArray()
            assertTrue(receiver.zpracuj(DatagramPacket(query, query.size, InetAddress.getLoopbackAddress(), socket.localPort)))
            receiver.odesliOdpovedi(socket)
            try { socket.receive(DatagramPacket(ByteArray(256), 256)); fail("Přijímač bez rendereru nesmí potvrdit podporu") }
            catch (_: java.net.SocketTimeoutException) { }
        }
    }
}
