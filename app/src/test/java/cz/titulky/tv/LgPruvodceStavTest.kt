package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class LgPruvodceStavTest {
    @Test fun invalidSavedStepStaysWithinGuide() {
        assertEquals(0, LgPruvodceStav(-1).krok)
        assertEquals(LgPruvodceStav.POSLEDNI, LgPruvodceStav(100).krok)
    }
    @Test fun resumeAndBackDoNotJumpToInstallation() {
        val s = LgPruvodceStav(2)
        assertTrue(s.zpet())
        assertEquals(1, s.krok)
        assertTrue(s.dalsi())
        assertEquals(2, s.krok)
        assertEquals(2, LgPruvodceStav(s.krok).krok)
    }
    @Test fun existingReceiverSkipsPreparationButStillRequiresDiscovery() {
        val s = LgPruvodceStav(0)
        s.prijimacMam()
        assertEquals(LgPruvodceStav.POSLEDNI, s.krok)
        assertFalse(s.dalsi())
        assertTrue(s.zpet())
        assertEquals(3, s.krok)
    }
    @Test fun firstBackClosesInsteadOfUnderflowAndLastNextDoesNotWrap() {
        val s = LgPruvodceStav(0)
        assertFalse(s.zpet())
        repeat(LgPruvodceStav.POSLEDNI) { assertTrue(s.dalsi()) }
        assertFalse(s.dalsi())
        assertEquals(LgPruvodceStav.POSLEDNI, s.krok)
    }
}
