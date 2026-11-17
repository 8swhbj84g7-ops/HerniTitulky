package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class PrihlaseniTelefonemTest {
    private val ulohy = LinkedBlockingQueue<Runnable>()
    private val id = "AQIDBAUGBwg="
    private val spoustec = Executor { ulohy.add(it) }
    private fun dokonci() = requireNotNull(ulohy.poll(2, TimeUnit.SECONDS)).run()
    private fun cesta(kod: String) = "/kod?u=" + URLEncoder.encode("${PsnPrihlaseni.PRESMEROVANI}?code=$kod", "UTF-8")

    private fun nacti(cesta: String, host: String = "127.0.0.1"): String {
        val doKdy = System.nanoTime() + 2_000_000_000L
        while (true) {
            try {
                val c = URL("http://$host:${PrihlaseniTelefonem.PORT}$cesta").openConnection() as HttpURLConnection
                c.connectTimeout = 200; c.readTimeout = 2000
                try {
                    val telo = c.inputStream.bufferedReader().use { it.readText() }
                    assertEquals("no-store", c.getHeaderField("Cache-Control"))
                    if (cesta.contains("code%3D")) assertEquals("/stav", c.url.path)
                    return telo
                }
                finally { c.disconnect() }
            } catch (e: java.net.ConnectException) {
                if (System.nanoTime() > doKdy) throw e
                Thread.sleep(10)
            }
        }
    }

    @Test fun prijetiKoduSamoNepotvrzujeUlozeniUctu() {
        var ulozeno = false
        val pokus = PsnPokus({ id }, { ulozeno = true; true }, spoustec)
        val server = PrihlaseniTelefonem(true, pokus)
        try {
            server.spust()
            val html = nacti(cesta("test-code"))
            assertFalse("Před uložením nesmí být potvrzený účet", html.contains("Účet je uložený"))
            assertTrue(html.contains("Ověřuji přihlášení"))
            assertTrue(html.contains("http-equiv=refresh"))
            assertFalse(ulozeno)
            dokonci()
            assertTrue(ulozeno)
            assertTrue(nacti("/stav").contains("Účet je uložený"))
            assertFalse(nacti("/stav").contains("http-equiv=refresh"))
            nacti(cesta("test-code"))
            assertTrue(ulohy.isEmpty())
        } finally { server.zastav() }
    }

    @Test fun chybaZustaneViditelnaAZnovuLzeZadatNovyKod() {
        val pokus = PsnPokus({ if (it == "stary") throw PsnHttpChyba("token", 400) else id }, { true }, spoustec)
        val server = PrihlaseniTelefonem(true, pokus)
        try {
            server.spust()
            nacti(cesta("stary")); dokonci()
            repeat(2) {
                val html = nacti("/stav")
                assertTrue(html.contains("Sony odmítlo přihlašovací kód"))
                assertTrue(html.contains("HTTP 400"))
                assertTrue(html.contains("<form"))
                assertFalse(html.contains("Hotovo!"))
            }
            assertTrue(nacti(cesta("novy")).contains("Ověřuji"))
            dokonci()
            assertTrue(nacti("/stav").contains("Účet je uložený"))
        } finally { server.zastav() }
    }

    @Test fun neplatnyVstupNespustiOverovani() {
        val server = PrihlaseniTelefonem(true, PsnPokus({ id }, { true }, spoustec))
        try {
            server.spust()
            val html = nacti("/kod?u=neplatny-vstup")
            assertTrue(html.contains("V adrese jsem nenašel kód"))
            assertTrue(nacti("/kod?u=%zz").contains("V adrese jsem nenašel kód"))
            assertTrue(ulohy.isEmpty())
        } finally { server.zastav() }
    }

    @Test fun qrPostupZachovaLanAdresuATvPotvrzeni() {
        val pokus = PsnPokus({ id }, { true }, spoustec)
        val server = PrihlaseniTelefonem(true, pokus)
        val qr = server.stranka(mistni = false)
        assertTrue(qr.contains("QR kód z televize znovu"))
        assertTrue(qr.contains(PsnPrihlaseni.ADRESA_PRIHLASENI))
        assertTrue(qr.contains("Vložit do televize"))
        assertFalse(server.stranka(mistni = true).contains("Vložit do televize"))
        pokus.prihlas("z-telefonu-do-tv"); dokonci()
        assertTrue(server.stranka(mistni = false).contains("Účet je v televizi"))
        assertTrue(server.stranka(mistni = true).contains("Účet je uložený"))
    }

    @Test fun qrPrihlaseniPoMistiSitiPotvrdiUlozeniVTelevizi() {
        val ip = java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isPointToPoint && !it.isVirtual }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && it.isSiteLocalAddress && !it.isLoopbackAddress }
            ?.hostAddress
        org.junit.Assume.assumeNotNull(ip)
        val server = PrihlaseniTelefonem(true, PsnPokus({ id }, { true }, spoustec))
        try {
            server.spust()
            assertTrue(nacti(cesta("kod-z-qr"), ip!!).contains("Ověřuji přihlášení"))
            dokonci()
            assertTrue(nacti("/stav", ip).contains("Účet je v televizi"))
        } finally { server.zastav() }
    }

    @Test fun anglickaStrankaTakeCekaAZobraziDuvodChyby() {
        val pokus = PsnPokus({ throw java.net.SocketTimeoutException("secret-code") }, { true }, spoustec)
        val server = PrihlaseniTelefonem(false, pokus)
        pokus.prihlas("secret-code")
        assertTrue(server.stranka(false).contains("Verifying your PlayStation"))
        dokonci()
        val html = server.stranka(false)
        assertTrue(html.contains("Sony did not respond in time"))
        assertFalse(html.contains("secret-code"))
        assertFalse(html.contains("Done!"))
    }

    @Test fun obsazenyPortNeoznaciPrihlasovaciStrankuZaPripravenou() {
        val chyba = CountDownLatch(1)
        val pokus = PsnPokus({ id }, { true }, spoustec) { chyba.countDown() }
        ServerSocket(PrihlaseniTelefonem.PORT).use {
            val server = PrihlaseniTelefonem(true, pokus)
            try {
                server.spust()
                assertTrue(chyba.await(2, TimeUnit.SECONDS))
                assertEquals(PsnPokus.Stav.CHYBA, pokus.stav)
                assertEquals("psnStranka", pokus.chyba)
            } finally { server.zastav() }
        }
    }
}
