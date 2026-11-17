package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class HlidaniPs5Test {
    @Test fun thirdConnectedAttemptWithoutAnyFrameLatchesRestartUntilProcessEnds() {
        val h = HlidaniPs5()
        repeat(3) { n ->
            val s = Any(); h.nova(s); h.pripojeno(s)
            assertEquals(n == 2, h.bezObrazu(s))
            h.ukonci(s)
        }
        assertTrue(h.restartNutny)
        h.nova(Any())
        assertTrue(h.restartNutny)
    }

    @Test fun realFrameResetsConsecutiveWatchdogsButHandshakeDoesNot() {
        val h = HlidaniPs5()
        repeat(2) { val s = Any(); h.nova(s); h.pripojeno(s); assertFalse(h.bezObrazu(s)); h.ukonci(s) }
        val s = Any(); h.nova(s); h.pripojeno(s)
        assertTrue(h.snimek(s)); assertFalse(h.snimek(s))
        assertFalse(h.bezObrazu(s)); h.ukonci(s)
        repeat(2) { val next = Any(); h.nova(next); h.pripojeno(next); assertFalse(h.bezObrazu(next)); h.ukonci(next) }
    }

    @Test fun oldConnectedQuitAndFrameCannotChangeNewSessionOrClearFailureCount() {
        val h = HlidaniPs5(); val old = Any(); val current = Any()
        h.nova(old); h.pripojeno(old); h.ukonci(old)
        h.nova(current)
        assertTrue(h.obsazeno(current)); assertEquals(1, h.obsazenych)
        assertFalse(h.aktualni(old)); assertFalse(h.pripojeno(old)); assertFalse(h.snimek(old))
        h.ukonci(old)
        assertTrue(h.aktualni(current)); assertEquals(1, h.obsazenych)
        assertFalse(h.bezObrazu(old)); assertFalse(h.restartNutny)
    }

    @Test fun sixthBusyAttemptEscalatesIndependentlyOfReconnectBackoffAndResetsOnValidConnection() {
        val h = HlidaniPs5()
        repeat(8) { n ->
            val s = Any(); h.nova(s); h.obsazeno(s)
            assertEquals(n + 1 >= 6, h.obsazenych >= 6)
            h.ukonci(s)
        }
        val s = Any(); h.nova(s); h.pripojeno(s)
        assertEquals(0, h.obsazenych)
    }

    @Test fun busyRefusalOrUnconnectedAttemptDoesNotCountAsMissingVideoCycle() {
        val h = HlidaniPs5()
        repeat(20) { val s = Any(); h.nova(s); h.obsazeno(s); assertFalse(h.bezObrazu(s)); h.ukonci(s) }
        assertFalse(h.restartNutny)
    }

    @Test fun repeatedWatchdogForSameSessionCountsOnlyOnce() {
        val h = HlidaniPs5(); val first = Any(); h.nova(first); h.pripojeno(first)
        repeat(10) { assertFalse(h.bezObrazu(first)) }
        h.ukonci(first)
        val second = Any(); h.nova(second); h.pripojeno(second)
        assertFalse(h.bezObrazu(second)); h.ukonci(second)
        val third = Any(); h.nova(third); h.pripojeno(third)
        assertTrue(h.bezObrazu(third))
    }
}
