package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class PrubehUkonceniTest {
    @Test fun vystupSeZavreAzPoDokoncenemDispose() {
        val calls = mutableListOf<String>()
        val p = PrubehUkonceni("test", { 0L }, {})
        p.ukonci(stop = { calls += "stop" }, dispose = { calls += "dispose" }, uvolniVystup = {
            assertEquals(listOf("stop", "dispose"), calls)
            calls += "výstup"
        })
        assertEquals(listOf("stop", "dispose", "výstup"), calls)
    }

    @Test fun cekajiciStaraRelaceDrziSvujVystupANezavreVystupNoveRelace() {
        val inside = java.util.concurrent.CountDownLatch(1)
        val resume = java.util.concurrent.CountDownLatch(1)
        val closed = java.util.concurrent.CopyOnWriteArrayList<String>()
        val clock = java.util.concurrent.atomic.AtomicLong(0)
        val p = PrubehUkonceni("stará", clock::get, {})
        val worker = Thread {
            p.ukonci(stop = {}, dispose = {
                inside.countDown(); check(resume.await(3, java.util.concurrent.TimeUnit.SECONDS))
            }, uvolniVystup = { closed += "stará" })
            p.dokonci()
        }.apply { isDaemon = true; start() }
        try {
            assertTrue(inside.await(2, java.util.concurrent.TimeUnit.SECONDS))
            clock.set(5000); p.kontrola(5000)
            assertTrue(closed.isEmpty())
            val next = PrubehUkonceni("nová", clock::get, {})
            next.ukonci(stop = {}, dispose = {}, uvolniVystup = { closed += "nová" })
            assertEquals(listOf("nová"), closed)
        } finally { resume.countDown(); worker.join(2000) }
        assertFalse(worker.isAlive)
        assertEquals(listOf("nová", "stará"), closed)
    }

    @Test fun selhaniDisposeNesmíUvolnitVystupPouzivanyNativnimVlaknem() {
        val calls = mutableListOf<String>()
        val log = mutableListOf<String>()
        val p = PrubehUkonceni("test", { 0L }, log::add)
        p.ukonci(stop = { calls += "stop" }, dispose = { error("native secret") },
            uvolniVystup = { calls += "výstup" })
        assertEquals(listOf("stop"), calls)
        assertTrue(log.any { "dispose(join/free) chyba IllegalStateException" in it })
        assertFalse(log.any { "native secret" in it })
    }

    @Test fun ukonceniZastaviRelaciPredDisposeBezOdpojovaniSurface() {
        var cas = 0L
        val log = mutableListOf<String>()
        val calls = mutableListOf<String>()
        val p = PrubehUkonceni("test", { cas }, log::add)
        p.ukonci(
            stop = { calls += "stop"; cas += 25 },
            dispose = { assertEquals(listOf("stop"), calls); calls += "dispose"; cas += 25 }
        )
        p.dokonci()
        assertEquals(listOf("stop", "dispose"), calls)
        assertFalse(log.any { "setSurface" in it })
        assertEquals(2, log.count { "konec 25 ms" in it })
        assertTrue(log.any { "dokončeno 50 ms" in it })
    }

    @Test fun blokujiciDisposeUzNemuzeZabranitZavolaniStop() {
        val cas = java.util.concurrent.atomic.AtomicLong(0)
        val log = java.util.concurrent.CopyOnWriteArrayList<String>()
        val uvnitr = java.util.concurrent.CountDownLatch(1)
        val uvolni = java.util.concurrent.CountDownLatch(1)
        val stopped = java.util.concurrent.atomic.AtomicBoolean(false)
        val p = PrubehUkonceni("test", cas::get, { log.add(it) })
        val worker = Thread {
            try {
                p.ukonci(stop = { stopped.set(true) }, dispose = {
                    uvnitr.countDown()
                    check(uvolni.await(2, java.util.concurrent.TimeUnit.SECONDS))
                })
            } finally { p.dokonci() }
        }.apply { name = "ps5-konec"; isDaemon = true; start() }
        try {
            assertTrue(uvnitr.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(stopped.get())
            cas.set(5000)
            assertTrue(p.kontrola(5000))
            assertTrue(log.any { "překročeno: dispose(join/free)" in it })
            assertFalse(log.any { "setSurface" in it })
            assertTrue(worker.isAlive) // limit jen diagnostikuje, neuvolňuje nativní paměť
        } finally { uvolni.countDown(); worker.join(2000) }
        assertFalse(worker.isAlive)
        assertFalse(p.kontrola(5000))
    }

    @Test fun chybaStopSeZapiseBezArgumentuADisposeStaleMusiCekatPresJoin() {
        val log = mutableListOf<String>()
        var disposed = false
        val p = PrubehUkonceni("test", { 0L }, log::add)
        p.ukonci(stop = { error("secret-token-account") }, dispose = { disposed = true })
        assertTrue(disposed)
        assertTrue(log.any { "stop chyba IllegalStateException" in it })
        assertFalse(log.any { "secret-token-account" in it })
    }

    @Test fun kontrolaUvidiStackSkutecneCekajicihoVlakna() {
        val cas = java.util.concurrent.atomic.AtomicLong(0)
        val log = java.util.concurrent.CopyOnWriteArrayList<String>()
        val uvnitr = java.util.concurrent.CountDownLatch(1)
        val uvolni = java.util.concurrent.CountDownLatch(1)
        val stop = java.util.concurrent.atomic.AtomicBoolean(false)
        val p = PrubehUkonceni("test", cas::get, { log.add(it) })
        val vlakno = Thread {
            p.proved("setSurface(null)") { uvnitr.countDown(); check(uvolni.await(2, java.util.concurrent.TimeUnit.SECONDS)) }
            p.proved("stop") { stop.set(true) }
            p.dokonci()
        }.apply { name = "ps5-konec"; isDaemon = true; start() }
        try {
            assertTrue(uvnitr.await(2, java.util.concurrent.TimeUnit.SECONDS))
            cas.set(5000)
            assertTrue(p.kontrola(5000))
            assertTrue(log.any { "vlákno ps5-konec" in it })
            assertTrue(log.any { "[PS5 stack #test]" in it && "CountDownLatch.await" in it })
            assertFalse(stop.get())
        } finally {
            uvolni.countDown()
            vlakno.join(2000)
        }
        assertFalse(vlakno.isAlive)
        assertTrue(stop.get())
        assertFalse(p.kontrola(5000))
    }

    @Test fun zachovaPoradiAZmeriKazdyKrok() {
        var cas = 0L
        val log = mutableListOf<String>()
        val volani = mutableListOf<String>()
        val p = PrubehUkonceni("test", { cas }, log::add)
        for (krok in listOf("setSurface(null)", "stop", "dispose(join/free)")) {
            p.proved(krok) { volani += krok; cas += 25 }
            assertTrue(p.kontrola(5000))
        }
        p.dokonci()
        assertFalse(p.kontrola(5000))
        assertEquals(listOf("setSurface(null)", "stop", "dispose(join/free)"), volani)
        assertEquals(3, log.count { "konec 25 ms" in it })
        assertTrue(log.any { "dokončeno 75 ms" in it })
        assertFalse(log.any { "překročeno" in it })
    }

    @Test fun blokujiciKrokZaznamenaJednouSeStackemAleNeprerusi() {
        var cas = 0L
        val log = mutableListOf<String>()
        val p = PrubehUkonceni("test", { cas }, log::add)
        var pokracovalo = false
        p.proved("setSurface(null)") {
            cas = 4999; p.kontrola(5000)
            assertFalse(log.any { "překročeno" in it })
            cas = 5000; p.kontrola(5000)
            cas = 10000; p.kontrola(5000)
            pokracovalo = true
        }
        assertTrue(pokracovalo)
        assertEquals(1, log.count { "překročeno: setSurface(null)" in it })
        assertTrue(log.any { "[PS5 stack #test]" in it })
        p.proved("stop") { cas += 6000; p.kontrola(5000) }
        assertEquals(1, log.count { "překročeno: stop" in it })
        p.dokonci()
    }

    @Test fun chybaNespotrebujeNasledujiciKrokAniNezapiseCitlivyText() {
        val log = mutableListOf<String>()
        val p = PrubehUkonceni("test", { 0L }, log::add)
        p.proved("setSurface(null)") { error("secret-token-account") }
        var zastaveno = false
        p.proved("stop") { zastaveno = true }
        p.dokonci()
        assertTrue(zastaveno)
        assertTrue(log.any { "IllegalStateException" in it })
        assertFalse(log.any { "secret-token-account" in it })
    }
}
