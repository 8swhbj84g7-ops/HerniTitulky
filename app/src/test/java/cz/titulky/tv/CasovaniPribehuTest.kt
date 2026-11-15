package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class CasovaniPribehuTest {
    @Test fun vychoziJeStoProcentHrouOverenehoZakladu() {
        assertEquals(100, CasovaniPribehu.VYCHOZI_PROCENTA)
        assertEquals(1200L, CasovaniPribehu.dobaMs("Ano.",100))
        assertEquals(8000L, CasovaniPribehu.dobaMs("č".repeat(180),100))
    }
    @Test fun procentaNasobiVypoctenyCasIKratkeRepliky() {
        assertEquals(960L, CasovaniPribehu.dobaMs("Ano.",80))
        assertEquals(1440L, CasovaniPribehu.dobaMs("Ano.",120))
        assertEquals(6400L, CasovaniPribehu.dobaMs("a".repeat(180),80))
        assertEquals(9600L, CasovaniPribehu.dobaMs("a".repeat(180),120))
    }
    @Test fun unicodeANfcStejneProVsechnaProcenta() {
        for (p in listOf(50,80,100,150,200)) {
            assertEquals(CasovaniPribehu.dobaMs("č".repeat(36),p),CasovaniPribehu.dobaMs("c\u030c".repeat(36),p))
            assertEquals(CasovaniPribehu.dobaMs("a".repeat(36),p),CasovaniPribehu.dobaMs("😀".repeat(36),p))
        }
    }
    @Test fun zmenaNezkratiPraveCtenouStrankuANeresetujeFrontu() {
        val f=CteciFronta();val a=f.zarad(0)!!;val b=f.zarad(100)!!
        f.priprav(a,listOf("První.","Druhá."));f.priprav(b,listOf("Třetí."))
        assertEquals(1200L,f.zobraz(0)!!.doMs)
        f.nastavCasovani(50)
        assertEquals(1200L,f.zobraz(100)!!.doMs)
        assertEquals(1800L,f.zobraz(1200)!!.doMs)
        assertEquals(b,f.zobraz(1800)!!.token)
        assertNull(f.zobraz(2400));assertEquals(2,f.dokoncenych)
    }
    @Test fun zmenaBehemPauzyZachovaZbyvajiciCas() {
        val f=CteciFronta();f.priprav(f.zarad(0)!!,listOf("První.","Druhá."));f.zobraz(0)
        f.pozastav(true,500);f.nastavCasovani(200);f.pozastav(false,5000)
        assertEquals(5700L,f.zobraz(5000)!!.doMs)
        assertEquals(8100L,f.zobraz(5700)!!.doMs)
    }
    @Test fun procentaSePosilajiNaLgAJdeZachovatBarvyJmen() {
        val id="a".repeat(32)+".1.1.0"
        val p=PribehProtokol.titulek(id,"Ahoj.",false,floatArrayOf(.5f,.9f,.8f,1f),intArrayOf(-65536),80)
        assertTrue(p.endsWith("\t-65536\tD:80"))
    }
    @Test fun neplatnaProcentaNelzeVypocitatAniOdeslat() {
        for (p in listOf(0,49,201,Int.MAX_VALUE)) {
            try { CasovaniPribehu.dobaMs("Ahoj.",p);fail() } catch (_: IllegalArgumentException) {}
            try { PribehProtokol.titulek("a".repeat(32)+".1.1.0","Ahoj.",false,floatArrayOf(.5f,.9f,.8f,1f),intArrayOf(),p);fail() }
            catch (_: IllegalArgumentException) {}
        }
    }
}
