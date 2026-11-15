package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class PribehovaRelaceTest {
    private class Scena {
        val hotovo = mutableListOf<(String) -> Unit>()
        val chyba = mutableListOf<() -> Unit>()
        val zpravy = mutableListOf<String>()
        val r = PribehovaRelace({ _, ok, err -> hotovo += ok; chyba += err }, { zpravy += it })
        fun nova(text: String, oprava: Boolean = false) = r.prijmi(listOf(text), intArrayOf(-65536), oprava, 0)
    }
    @Test fun skutecnyAdapterCekaNaDokonceniLgIAckSeNeopakuje() {
        val s = Scena(); s.nova("A"); s.nova("B")
        s.hotovo[1]("Druhá.")
        assertNull(s.r.krok(0, true, false))
        s.hotovo[0]("První.")
        val a = s.r.krok(100, true, false)!!
        assertEquals("První.", a.text)
        assertEquals(a, s.r.krok(60_000, true, false))
        assertArrayEquals(intArrayOf(-65536), s.r.barvy(a.token))
        assertTrue(s.r.potvrzeno(a.token)); assertFalse(s.r.potvrzeno(a.token))
        assertEquals("Druhá.", s.r.krok(60_000, true, false)!!.text)
    }
    @Test fun opravenaHypotezaNeprejmePozdniStarouOdpoved() {
        val s = Scena(); s.nova("Old"); s.nova("New", true)
        s.hotovo[1]("Správně."); s.hotovo[0]("Stará chyba."); s.chyba[0]()
        assertEquals("Správně.", s.r.krok(0, true, false)!!.text)
        assertTrue(s.zpravy.isEmpty())
    }
    @Test fun poResetuNesmimeOzivitStarouRelaci() {
        val s = Scena(); s.nova("Old"); s.r.reset(); s.nova("New")
        s.hotovo[0]("Staré."); s.chyba[0]()
        assertNull(s.r.krok(0, true, false))
        s.hotovo[1]("Nové.")
        assertEquals("Nové.", s.r.krok(10, true, false)!!.text)
    }
    @Test fun rozectenaOpravaNevytvoriDalsiPrekladAniPolozku() {
        val s = Scena(); s.nova("A"); s.hotovo[0]("Čtu."); s.r.krok(0, true, false)
        s.nova("A revised", true)
        assertEquals(1, s.hotovo.size); assertEquals(1, s.r.pocet)
    }
    @Test fun chybaPrekladuNesmiZablokovatDalsiRepliku() {
        val s = Scena(); s.nova("A"); s.nova("B"); s.hotovo[1]("Pokračuj."); s.chyba[0]()
        assertEquals("Pokračuj.", s.r.krok(0, true, false)!!.text)
        assertEquals(1, s.zpravy.size)
    }
    @Test fun lokalniVystupPouzivaCasovaniFronty() {
        val s = Scena(); s.nova("A"); s.nova("B"); s.hotovo[0]("Ano."); s.hotovo[1]("Jdu.")
        assertEquals("Ano.", s.r.krok(0, false, false)!!.text)
        assertEquals("Jdu.", s.r.krok(1800, false, false)!!.text)
        assertNull(s.r.krok(3600, false, false))
    }
    @Test fun selhaniVystupuSeNepocitaJakoDokonceneCteni() {
        val f=CteciFronta();val t=f.zarad(0)!!;f.priprav(t,listOf("Připravená."));f.zobraz(0,true)
        assertTrue(f.chybaZobrazeni(t));assertFalse(f.potvrzeno(t))
        assertEquals(0,f.dokoncenych);assertEquals(1,f.selhanych);assertEquals(0,f.pocet)
    }
    @Test fun prazdnyPrekladJeViditelnaChybaNeAnglickyFallback() {
        val s = Scena(); s.nova("English"); s.hotovo[0](" ")
        assertNull(s.r.krok(0, true, false)); assertEquals(1, s.zpravy.size)
    }
    @Test fun chybyProtokoluANadmernyPaketJsouOdmitnute() {
        val id = "a".repeat(32) + ".1.2.3"
        assertEquals("done", PribehProtokol.odpoved("PCA1\t$id\tdone")!!.stav)
        for (p in listOf("PCA1\tx\tdone", "PCA1\t$id\tother", "PCA1\t$id\tdone\textra")) assertNull(PribehProtokol.odpoved(p))
        val packet = PribehProtokol.titulek(id, "První\nDruhý\\konec", false, floatArrayOf(.5f,.9f,.8f,1f), intArrayOf(-65536))
        assertTrue(packet.contains("První\\nDruhý\\\\konec"))
        try { PribehProtokol.titulek(id, "ž".repeat(5000), false, floatArrayOf(.5f,.9f,.8f,1f), intArrayOf()); fail() }
        catch (_: IllegalArgumentException) { }
    }
    @Test(timeout = 4000) fun obousmernyUdpSocketPrijmePouzeOdpovedNaAktualniId() {
        DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { tv ->
            tv.soTimeout = 2000
            val id = "b".repeat(32) + ".1.0.0"
            PribehSpojeni("127.0.0.1", tv.localPort, { 100L }).use { spojeni ->
                spojeni.zprava = PribehProtokol.dotaz(id)
                val packet = DatagramPacket(ByteArray(1024), 1024); tv.receive(packet)
                assertEquals("PCQ1\t$id", String(packet.data,0,packet.length,Charsets.UTF_8))
                for (reply in listOf("PCA1\t" + "c".repeat(32) + ".1.0.0\tdone", "PCA1\t$id\tready")) {
                    val data = reply.toByteArray(); tv.send(DatagramPacket(data,data.size,packet.address,packet.port))
                }
                val deadline = System.nanoTime() + 2_000_000_000
                while (spojeni.odpoved == null && System.nanoTime() < deadline) Thread.sleep(5)
                assertEquals(PribehProtokol.Odpoved(id,"ready") to 100L, spojeni.odpoved)
            }
        }
    }
}
