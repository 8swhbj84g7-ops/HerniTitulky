package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class StavSpojeniTest {
    @Test fun runningServiceAndHandshakeDoNotProveVideo() {
        val stav = StavObrazu()
        stav.zacni()
        assertEquals(UrovenSpojeni.CEKANI, stav.prehled(0, true, false, "Připojuji").uroven)
        stav.pripojeno(100)
        assertEquals("stavCekaObraz", stav.prehled(101, true, false, "").klic)
        assertEquals(UrovenSpojeni.CHYBA, stav.prehled(10_100, true, false, "").uroven)
    }

    @Test fun framesCanStopAndRecoverWithoutChangingServiceLifetime() {
        val stav = StavObrazu()
        stav.pripojeno(0)
        stav.snimek(100)
        assertEquals(UrovenSpojeni.OK, stav.prehled(200, true, false, "").uroven)
        val waiting = stav.prehled(29_100, true, false, "")
        assertEquals(29_000L, waiting.stariSnimkuMs)
        assertTrue(Texty.spojeni("cs", waiting).contains("před 29 s"))
        assertEquals("stavObrazUstal", stav.prehled(30_101, true, false, "").klic)
        stav.snimek(30_102)
        assertEquals(UrovenSpojeni.OK, stav.prehled(30_103, true, false, "").uroven)
    }

    @Test fun retryDiscardsPreviousFramesAndPreservesConcreteReason() {
        val stav = StavObrazu()
        stav.pripojeno(0); stav.snimek(10); stav.zacni()
        val s = stav.prehled(11, true, false, "PS5 používá jiné zařízení")
        assertEquals(UrovenSpojeni.CEKANI, s.uroven)
        assertEquals("PS5 používá jiné zařízení", s.detail)
        stav.pripojeno(20)
        assertEquals("stavCekaObraz", stav.prehled(21, true, false, "").klic)
    }

    @Test fun decoderFailureIsVisibleWhileServiceRunsAndNextFrameClearsIt() {
        val stav = StavObrazu()
        stav.pripojeno(0); stav.snimek(1); stav.chyba("Chyba obrazu: IllegalStateException")
        assertEquals(UrovenSpojeni.CHYBA, stav.prehled(2, true, false, "").uroven)
        assertTrue(stav.prehled(2, true, false, "").detail.contains("IllegalStateException"))
        stav.snimek(3)
        assertEquals(UrovenSpojeni.OK, stav.prehled(4, true, false, "").uroven)
    }

    @Test fun pausedAndStoppedAreNeverGreen() {
        val stav = StavObrazu()
        stav.pripojeno(0); stav.snimek(1)
        assertEquals(UrovenSpojeni.VYPNUTO, stav.prehled(2, false, false, "").uroven)
        assertEquals("stavPozastaveno", stav.prehled(2, true, true, "").klic)
    }

    @Test fun udpSendIsNotReceiptAndObsoleteWorkerCannotChangeNewTarget() {
        val stav = StavPrenosu()
        stav.reset(1)
        assertFalse(stav.stav.odeslano)
        stav.vysledek(1, null)
        assertTrue(stav.stav.odeslano)
        assertEquals("tvOdesilanoNeovereno", stav.stav.klic)
        stav.reset(2)
        stav.vysledek(1, java.net.SocketException("secret token"))
        assertNull(stav.stav.chyba)
        assertFalse(stav.stav.odeslano)
    }

    @Test fun concreteNetworkFailureRecoversAndNeverIncludesExceptionPayload() {
        val stav = StavPrenosu()
        stav.reset(1)
        stav.vysledek(1, java.net.UnknownHostException("secret token/account"))
        assertEquals("UnknownHostException", stav.stav.chyba)
        assertEquals("sitAdresa", stav.stav.duvodKlic)
        assertEquals("tvChybaOdesilani", stav.stav.klic)
        stav.vysledek(1, null)
        assertNull(stav.stav.chyba)
        assertEquals("tvOdesilanoNeovereno", stav.stav.klic)
    }

    @Test fun receiverDoesNotConfuseHeartbeatWithWorkingOverlay() {
        val permission = PrehledSpojeni("prekryvPovoleni", UrovenSpojeni.CHYBA)
        assertEquals(permission, prehledPrijmu(true, null, permission, false, false))
        assertEquals("prijemChyba", prehledPrijmu(true, "BindException", null, true, false).klic)
        assertEquals("prekryvCeka", prehledPrijmu(true, null, null, false, false).klic)
        assertEquals("prijemSkryty", prehledPrijmu(true, null, null, true, true).klic)
        assertEquals(UrovenSpojeni.OK, prehledPrijmu(true, null, null, true, false).uroven)
        assertEquals(UrovenSpojeni.CEKANI, prehledPrijmu(false, null, null, true, false).uroven)
    }

    @Test fun statusKeepsWholeReasonAndDistinguishesDataFromDeliveryInBothLanguages() {
        val reason = "PS5 používá jiné zařízení. " + "Konkrétní důvod. ".repeat(10)
        assertTrue(Texty.spojeni("cs", PrehledSpojeni("stavNavazuji", UrovenSpojeni.CEKANI, reason)).endsWith(reason))
        for (language in listOf("cs", "sk")) {
            val text = Texty.prenos(language, PrehledPrenosu(odeslano = true))
            assertTrue(text.contains(if (language == "cs") "nejsou potvrzené" else "nie sú potvrdené"))
        }
    }
}
