package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class DokonceniVystupuTest {
    @Test fun zanikSluzbyNeukonciObsluhuYuvDokudNeskonciObaDekodery() {
        var zavreno = 0
        val zivot = DokonceniVystupu { zavreno++ }
        zivot.pridej(); zivot.pridej()
        zivot.uklidHotov()
        assertEquals(0, zavreno)
        zivot.uvolni(); assertEquals(0, zavreno)
        zivot.uvolni(); assertEquals(1, zavreno)
        zivot.uklidHotov(); assertEquals(1, zavreno)
    }

    @Test fun obsluhaSkonciAzPoUkliduSluzbyIKdyzDekoderSkoncilDrive() {
        var zavreno = 0
        val zivot = DokonceniVystupu { zavreno++ }
        zivot.pridej(); zivot.uvolni()
        assertEquals(0, zavreno)
        zivot.uklidHotov(); assertEquals(1, zavreno)
    }

    @Test fun sluzbaBezDekoderuNemusiCekatNaZadnyVystup() {
        var zavreno = 0
        val zivot = DokonceniVystupu { zavreno++ }
        zivot.uklidHotov(); zivot.uklidHotov()
        assertEquals(1, zavreno)
    }
}
