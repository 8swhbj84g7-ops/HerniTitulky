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
    @Test fun dohanejiSeTriACekajiciReplikyExponencialneSLimitem() {
        val casy = (0..8).map { CasovaniPribehu.priZpozdeni(100, it) }
        assertEquals(listOf(100, 100, 100, 85, 72, 61, 52, 50, 50), casy)
        assertEquals(50, CasovaniPribehu.priZpozdeni(100, Int.MAX_VALUE))
        assertEquals(100, CasovaniPribehu.priZpozdeni(200, 89))
        assertEquals(50, CasovaniPribehu.priZpozdeni(50, 89))
        assertEquals(80, CasovaniPribehu.priZpozdeni(80, 2))
        assertEquals(68, CasovaniPribehu.priZpozdeni(80, 3))
        try { CasovaniPribehu.priZpozdeni(100, -1); fail() } catch (_: IllegalArgumentException) {}
    }
    @Test fun dohaneniZachovaRozectenouStrankuPoradiAObnoviZaklad() {
        val f = CteciFronta()
        val tokens = (0 until 20).map { f.zarad(0)!!.also { f.priprav(it, listOf("Replika.")) } }
        val videne = mutableListOf<CteciFronta.Token>()
        var posledni: CteciFronta.Token? = null
        var minimalni = 100
        var posledniProcenta = 0
        val prvni = f.zobraz(0)!!
        assertEquals(1200L, prvni.doMs)
        for (ted in 0L..24000L step 100L) {
            val cekajici = (f.pocet - if (posledni != null || ted == 0L) 1 else 0).coerceAtLeast(0)
            val procenta = CasovaniPribehu.priZpozdeni(100, cekajici)
            f.nastavCasovani(procenta)
            val p = f.zobraz(ted)
            if (ted < 1200) assertEquals(prvni, p)
            if (p != null && p.token != posledni) videne += p.token
            posledni = p?.token
            minimalni = minOf(minimalni, procenta)
            posledniProcenta = procenta
            if (f.pocet == 0) {
                assertTrue("Dohánění musí frontu vyprázdnit dříve", ted < 24000)
                break
            }
        }
        assertEquals(tokens, videne)
        assertEquals(20, f.dokoncenych)
        assertEquals(0, f.selhanych)
        assertEquals(0, f.pocet)
        assertEquals(50, minimalni)
        assertEquals(100, posledniProcenta)
    }
    @Test fun souvislyDialogNehromadiMinutovouFrontu() {
        val f = CteciFronta()
        val prijate = mutableListOf<CteciFronta.Token>()
        val videne = mutableListOf<CteciFronta.Token>()
        var aktualni: CteciFronta.Token? = null
        var maximum = 0
        for (ted in 0L..65000L step 100L) {
            if (ted <= 59000L && ted % 1000L == 0L) {
                val token = f.zarad(ted)!!
                prijate += token
                f.priprav(token, listOf("Krátká replika."))
            }
            maximum = maxOf(maximum, f.pocet)
            val cekajici = (f.pocet - if (aktualni != null) 1 else 0).coerceAtLeast(0)
            f.nastavCasovani(CasovaniPribehu.priZpozdeni(100, cekajici))
            val p = f.zobraz(ted)
            if (p != null && p.token != aktualni) videne += p.token
            aktualni = p?.token
        }
        assertEquals(prijate, videne)
        assertEquals(60, f.dokoncenych)
        assertEquals(0, f.pocet)
        assertTrue("Průběžné dohánění musí držet frontu krátkou", maximum <= 6)
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
