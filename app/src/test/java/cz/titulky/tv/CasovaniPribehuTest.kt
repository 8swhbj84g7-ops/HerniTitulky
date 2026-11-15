package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class CasovaniPribehuTest {
    @Test fun vychoziJeStoProcentRychlejsihoZakladu() {
        assertEquals(100, CasovaniPribehu.VYCHOZI_PROCENTA)
        assertEquals(1500L, CasovaniPribehu.dobaMs("Ano.",100))
        assertEquals(10000L, CasovaniPribehu.dobaMs("č".repeat(180),100))
    }
    @Test fun procentaNasobiVypoctenyCasIKratkeRepliky() {
        assertEquals(1200L, CasovaniPribehu.dobaMs("Ano.",80))
        assertEquals(1800L, CasovaniPribehu.dobaMs("Ano.",120))
        assertEquals(8000L, CasovaniPribehu.dobaMs("a".repeat(180),80))
        assertEquals(12000L, CasovaniPribehu.dobaMs("a".repeat(180),120))
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
        assertEquals(1500L,f.zobraz(0)!!.doMs)
        f.nastavCasovani(50)
        assertEquals(1500L,f.zobraz(100)!!.doMs)
        assertEquals(2250L,f.zobraz(1500)!!.doMs)
        assertEquals(b,f.zobraz(2250)!!.token)
        assertNull(f.zobraz(3000));assertEquals(2,f.dokoncenych)
    }
    @Test fun zmenaBehemPauzyZachovaZbyvajiciCas() {
        val f=CteciFronta();f.priprav(f.zarad(0)!!,listOf("První.","Druhá."));f.zobraz(0)
        f.pozastav(true,500);f.nastavCasovani(200);f.pozastav(false,5000)
        assertEquals(6000L,f.zobraz(5000)!!.doMs)
        assertEquals(9000L,f.zobraz(6000)!!.doMs)
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
