package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

/** Hotové české dialogy a virtuální čas; žádné čekání, Android, síť ani konzole. */
class CteciFrontaTest {
    private class Simulace(val fronta: CteciFronta = CteciFronta()) {
        // Scénáře pořadí a pauz drží pevné časy; výchozí základ ověřuje CasovaniPribehuTest.
        init { fronta.nastavCasovani(125) }
        val zmeny = mutableListOf<Pair<Long, CteciFronta.Stranka?>>()
        private var viditelna: CteciFronta.Stranka? = null

        fun v(cas: Long): CteciFronta.Stranka? = fronta.zobraz(cas).also {
            if (it != viditelna) { zmeny += cas to it; viditelna = it }
        }

        fun nova(text: String, cas: Long = 0): CteciFronta.Token = fronta.zarad(cas)!!.also {
            assertEquals(CteciFronta.Prijeti.PRIJATO, fronta.priprav(it, listOf(text)))
        }
    }

    @Test fun jednoslovnaReplikaZacneIhnedAMaMinimalniCas() {
        val s = Simulace()
        val token = s.nova("Ano.", 500)
        val prvni = s.v(500)!!
        assertEquals(token, prvni.token)
        assertEquals(500L, prvni.odMs)
        assertEquals(2000L, prvni.doMs)
        assertEquals(prvni, s.v(1999))
        assertNull(s.v(2000))
        assertEquals(1, s.fronta.dokoncenych)
        assertEquals(0, s.fronta.pocet)
    }

    @Test fun dlouhaCeskaVetaDostaneCelyVypoctenyCasBezStropu() {
        val s = Simulace()
        val text = "Trevor: Musíme se vrátit k domu, protože tam na nás pořád čekají ostatní. ".repeat(12).trim()
        s.nova(text)
        val p = s.v(0)!!
        val doba = (text.codePointCount(0, text.length) * 1000L + 17) / 18
        assertTrue(doba > 15_000)
        assertEquals(doba, p.doMs)
        assertEquals(text, s.v(doba - 1)!!.text)
        assertNull(s.v(doba))
    }

    @Test fun unicodeEmojiSeNepocitaJakoDvaZnakyARychlostSeZaokrouhliNahoru() {
        val s = Simulace()
        s.nova("😀".repeat(29))
        assertEquals(1612L, s.v(0)!!.doMs)
        assertNotNull(s.v(1611))
        assertNull(s.v(1612))
    }

    @Test fun slozenaCeskaDiakritikaMaStejnouCteciDobuJakoNfc() {
        val s = Simulace()
        val text = "c\u030c".repeat(28)
        s.nova(text)
        val p = s.v(0)!!
        assertEquals(text, p.text)
        assertEquals(1556L, p.doMs)
    }

    @Test fun triRychleReplikySeZobraziJednouVPoradiADoctouVPauze() {
        val s = Simulace().apply { fronta.nastavCasovani(100) }
        val a = s.nova("Trevor: Ahoj.")
        assertEquals(a, s.v(0)!!.token)
        val b = s.nova("Michael: Pojď.", 100)
        val c = s.nova("Franklin: Jdu.", 200)
        assertEquals(a, s.v(200)!!.token)
        assertEquals(a, s.v(1199)!!.token)
        assertEquals(b, s.v(1200)!!.token)
        assertEquals(c, s.v(2400)!!.token)
        assertNull(s.v(3600))
        assertEquals(listOf(a, b, c), s.zmeny.mapNotNull { it.second?.token })
        assertEquals(listOf(0L, 1200L, 2400L, 3600L), s.zmeny.map { it.first })
        assertEquals(listOf(0L, 1100L, 2200L), s.zmeny.mapNotNull { it.second }.map { it.odMs - it.puvodniCasMs })
        assertEquals(3, s.fronta.dokoncenych)
        assertEquals(0, s.fronta.pocet)
    }

    @Test fun metrikyRychlehoDialoguNaVychozimCasovani() {
        val s = Simulace().apply { fronta.nastavCasovani(100) }
        val prijate = mutableListOf<CteciFronta.Token>()
        var maximum = 0
        for (ted in 0L..19_200L step 100L) {
            if (ted <= 1500L) prijate += s.nova("Replika ${ted / 100}.", ted)
            maximum = maxOf(maximum, s.fronta.pocet)
            s.v(ted)
        }
        val zobrazeni = s.zmeny.mapNotNull { it.second }
        val cekani = zobrazeni.map { it.odMs - it.puvodniCasMs }.sorted()
        fun percentil(p: Int) = cekani[(cekani.size * p + 99) / 100 - 1]
        val duplicity = zobrazeni.size - zobrazeni.map { it.token }.distinct().size
        val ztracene = prijate.toSet() - zobrazeni.map { it.token }.toSet()
        val prerusene = zobrazeni.zipWithNext().count { (a, b) -> b.odMs < a.doMs }
        assertEquals(prijate, zobrazeni.map { it.token })
        assertEquals(16, s.fronta.dokoncenych)
        assertEquals(0, s.fronta.pocet)
        assertEquals(0, s.fronta.selhanych)
        assertEquals(0, duplicity)
        assertTrue(ztracene.isEmpty())
        assertEquals(0, prerusene)
        assertEquals(15, maximum)
        assertEquals(7700L, percentil(50))
        assertEquals(16500L, percentil(95))
        assertEquals(17700L, s.zmeny.last().first - 1500L)
    }

    @Test fun prekladyDokonceneObraceneNepredbehnouPuvodniRepliku() {
        val s = Simulace()
        val a = s.fronta.zarad(0)!!
        val b = s.fronta.zarad(100)!!
        val c = s.fronta.zarad(200)!!
        s.fronta.priprav(c, listOf("Franklin: Jdu."))
        s.fronta.priprav(b, listOf("Michael: Pojď."))
        assertNull(s.v(500))
        s.fronta.priprav(a, listOf("Trevor: Ahoj."))
        assertEquals(a, s.v(5000)!!.token)
        assertEquals(b, s.v(6500)!!.token)
        assertEquals(c, s.v(8000)!!.token)
        assertNull(s.v(9500))
        assertEquals(3, s.fronta.dokoncenych)
    }

    @Test fun opravenaCekajiciRevizeZachovaPoradiAStaryVysledekNeprijme() {
        val s = Simulace()
        val a = s.nova("Počkej.")
        s.v(0)
        val b = s.fronta.zarad(100)!!
        s.fronta.priprav(b, listOf("Chybné čtení."))
        val c = s.nova("Jdeme.", 200)
        val oprava = s.fronta.oprav(b)!!
        assertEquals(b.poradi, oprava.poradi)
        assertTrue(oprava.revize > b.revize)
        assertEquals(CteciFronta.Prijeti.NEPLATNY_TOKEN, s.fronta.priprav(b, listOf("Pozdní stará revize.")))
        assertFalse(s.fronta.chyba(b))
        assertEquals(a, s.v(1499)!!.token)
        assertNull(s.v(1500))
        s.fronta.priprav(oprava, listOf("Správné čtení."))
        assertEquals(oprava, s.v(2000)!!.token)
        assertEquals(c, s.v(3500)!!.token)
        assertNull(s.v(5000))
        assertFalse(s.zmeny.any { it.second?.text == "Chybné čtení." })
    }

    @Test fun opakovaneVysledkyHeartbeatNerestartujiCteniAniNevytvoriDalsiPolozku() {
        val s = Simulace()
        val token = s.nova("Počkej.")
        val p = s.v(0)
        for (cas in listOf(100L, 500L, 1000L, 1499L)) {
            assertEquals(CteciFronta.Prijeti.OPAKOVANI, s.fronta.priprav(token, listOf("Počkej.")))
            assertEquals(p, s.v(cas))
            assertEquals(1, s.fronta.pocet)
        }
        assertNull(s.v(1500))
        assertEquals(CteciFronta.Prijeti.NEPLATNY_TOKEN, s.fronta.priprav(token, listOf("Počkej.")))
        assertNull(s.v(3000))
        assertEquals(2, s.zmeny.size)
        assertEquals(1, s.fronta.dokoncenych)
    }

    @Test fun dveSamostatneStejneVetyJsouDveRepliky() {
        val s = Simulace()
        val a = s.nova("Ano.")
        val b = s.nova("Ano.", 100)
        assertNotEquals(a, b)
        assertEquals(a, s.v(0)!!.token)
        assertEquals(b, s.v(1500)!!.token)
        assertNull(s.v(3000))
        assertEquals(2, s.fronta.dokoncenych)
    }

    @Test fun rozectenaReplikaSeUzNemeniAniPriPozdniOprave() {
        val s = Simulace()
        val token = s.nova("Trevor: Počkej.")
        val p = s.v(0)
        assertNull(s.fronta.oprav(token))
        assertEquals(CteciFronta.Prijeti.UZ_PRIPRAVENO, s.fronta.priprav(token, listOf("Jiný překlad.")))
        assertFalse(s.fronta.chyba(token))
        assertEquals(p, s.v(1499))
        assertNull(s.v(1500))
    }

    @Test fun vsechnyStrankyVcetnePosledniDostanouCelyCasPredDalsiReplikou() {
        val s = Simulace()
        val a = s.fronta.zarad(0)!!
        val stranky = listOf("Trevor: První stránka.", "Druhá stránka.", "Konec.")
        s.fronta.priprav(a, stranky)
        val b = s.nova("Michael: Ano.", 50)
        assertEquals(stranky[0], s.v(0)!!.text)
        assertEquals(stranky[1], s.v(1500)!!.text)
        assertEquals(stranky[2], s.v(3000)!!.text)
        assertEquals(a, s.v(4499)!!.token)
        assertEquals(b, s.v(4500)!!.token)
        assertNull(s.v(6000))
        assertEquals(stranky, s.zmeny.mapNotNull { it.second }.filter { it.token == a }.map { it.text })
        assertEquals(listOf(0, 1, 2), s.zmeny.mapNotNull { it.second }.filter { it.token == a }.map { it.index })
        assertEquals(2, s.fronta.dokoncenych)
    }

    @Test fun kazdaStrankaSeCasujeZeSveCestinySamostatne() {
        val s = Simulace()
        val a = s.fronta.zarad(0)!!
        s.fronta.priprav(a, listOf("č".repeat(140), "Ano."))
        assertEquals(7778L, s.v(0)!!.doMs)
        assertEquals(9278L, s.v(7778)!!.doMs)
        assertNotNull(s.v(9277))
        assertNull(s.v(9278))
    }

    @Test fun pozdniTickNesmiPreskocitDosudNezobrazeneStranky() {
        val s = Simulace()
        val a = s.fronta.zarad(0)!!
        s.fronta.priprav(a, listOf("První.", "Druhá.", "Třetí."))
        assertEquals(0, s.v(0)!!.index)
        val druha = s.v(100_000)!!
        assertEquals(1, druha.index)
        assertEquals(101500L, druha.doMs)
        assertEquals(1, s.v(101499)!!.index)
        assertEquals(2, s.v(101500)!!.index)
        assertNull(s.v(103000))
    }

    @Test fun interaktivniVolbaPozastaviCteniANovePrekladyMezitimDalePrichazeji() {
        val s = Simulace()
        val a = s.nova("Počkej.")
        s.v(0)
        s.fronta.pozastav(true, 500)
        assertNull(s.v(500))
        val b = s.nova("Jdeme.", 600)
        assertNull(s.v(50_000))
        s.fronta.pozastav(false, 50_000)
        val obnovena = s.v(50_000)!!
        assertEquals(a, obnovena.token)
        assertEquals(51000L, obnovena.doMs)
        assertEquals(a, s.v(50999)!!.token)
        assertEquals(b, s.v(51000)!!.token)
        assertNull(s.v(52500))
        assertEquals(2, s.fronta.dokoncenych)
    }

    @Test fun opakovanePozastaveniNezkratiZbyvajiciCas() {
        val s = Simulace()
        s.nova("Ano.")
        s.v(0)
        s.fronta.pozastav(true, 500)
        s.fronta.pozastav(true, 3000)
        s.fronta.pozastav(false, 5000)
        s.fronta.pozastav(false, 5100)
        assertEquals(6000L, s.v(5100)!!.doMs)
        assertNull(s.v(6000))
    }

    @Test fun pozastaveniPredPrvniStrankouNespotrebujeJejiCas() {
        val s = Simulace()
        val a = s.nova("Ano.")
        s.fronta.pozastav(true, 0)
        assertNull(s.v(20_000))
        s.fronta.pozastav(false, 20_000)
        assertEquals(a, s.v(20_000)!!.token)
        assertEquals(21500L, s.v(20_000)!!.doMs)
    }

    @Test fun odpojeniAObnovaNaPosledniStranceNeztratiZbytekCteni() {
        val s = Simulace()
        val a = s.fronta.zarad(0)!!
        val stranky = listOf("První.", "Poslední.")
        s.fronta.priprav(a, stranky)
        s.v(0)
        s.v(1500)
        s.fronta.pozastav(true, 2000)
        assertNull(s.v(20_000))
        assertEquals(CteciFronta.Prijeti.OPAKOVANI, s.fronta.priprav(a, stranky))
        s.fronta.pozastav(false, 20_000)
        assertEquals(1, s.v(20_000)!!.index)
        assertNotNull(s.v(20999))
        assertNull(s.v(21000))
        assertEquals(1, s.fronta.dokoncenych)
    }

    @Test fun zmenaRelaceZrusiStareTokenyFrontuIPozastaveni() {
        val s = Simulace()
        val a = s.nova("Stará replika.")
        val b = s.fronta.zarad(100)!!
        s.v(0)
        s.fronta.pozastav(true, 500)
        s.fronta.reset()
        assertEquals(0, s.fronta.pocet)
        assertNull(s.fronta.oprav(a))
        assertFalse(s.fronta.chyba(b))
        assertEquals(CteciFronta.Prijeti.NEPLATNY_TOKEN, s.fronta.priprav(b, listOf("Pozdní výsledek.")))
        val c = s.nova("Nová replika.", 500)
        assertNotEquals(a.relace, c.relace)
        assertEquals(c, s.v(500)!!.token)
        assertNull(s.v(2000))
    }

    @Test fun explicitniChybaPrekladuUvolniNasledujiciReplikuAleNezahodiPripravenyText() {
        val s = Simulace()
        val a = s.fronta.zarad(0)!!
        val b = s.nova("Jdeme.", 100)
        assertNull(s.v(100))
        assertTrue(s.fronta.chyba(a))
        assertFalse(s.fronta.chyba(a))
        assertFalse(s.fronta.chyba(b))
        assertEquals(b, s.v(100)!!.token)
        assertEquals(1, s.fronta.selhanych)
        assertEquals(CteciFronta.Prijeti.NEPLATNY_TOKEN, s.fronta.priprav(a, listOf("Pozdní výsledek.")))
        assertNull(s.v(1600))
        assertEquals(1, s.fronta.dokoncenych)
    }

    @Test fun plnaFrontaOdmitneNovouReplikuVyslovneAPoDocteniUvolniMisto() {
        val s = Simulace(CteciFronta(maxReplik = 2))
        val a = s.nova("První.")
        val b = s.nova("Druhá.")
        assertNull(s.fronta.zarad(100))
        assertEquals(2, s.fronta.pocet)
        assertEquals(a, s.v(0)!!.token)
        assertEquals(b, s.v(1500)!!.token)
        val c = s.nova("Třetí.", 1500)
        assertEquals(c, s.v(3000)!!.token)
        assertNull(s.v(4500))
        assertEquals(3, s.fronta.dokoncenych)
    }

    @Test fun limitPametiNicNeorezeANezahodiDrivePrijatouRepliku() {
        val s = Simulace(CteciFronta(maxZnakuRepliky = 8))
        val a = s.nova("Ano.")
        val b = s.fronta.zarad(100)!!
        assertEquals(CteciFronta.Prijeti.LIMIT_TEXTU, s.fronta.priprav(b, listOf("Dlouhý text.")))
        assertEquals(a, s.v(0)!!.token)
        assertNull(s.v(1500))
        assertEquals(1, s.fronta.pocet)
        assertEquals(1, s.fronta.dokoncenych)
        assertEquals(0, s.fronta.selhanych)
    }

    @Test fun opravaCekajicihoTextuUvolniJehoPamet() {
        val f = CteciFronta(maxZnakuRepliky = 6).apply { nastavCasovani(125) }
        val a = f.zarad(0)!!
        f.priprav(a, listOf("Jdeme."))
        val nova = f.oprav(a)!!
        assertEquals(CteciFronta.Prijeti.PRIJATO, f.priprav(nova, listOf("Jdeme.")))
    }

    @Test fun hotovyPozdejsiPrekladNesmiBlokovatPrijetiPrvnihoKvuliPameti() {
        val s = Simulace(CteciFronta(maxReplik = 2, maxZnakuRepliky = 4))
        val a = s.fronta.zarad(0)!!
        val b = s.fronta.zarad(100)!!
        assertEquals(CteciFronta.Prijeti.PRIJATO, s.fronta.priprav(b, listOf("Ano.")))
        assertNull(s.v(1000))
        assertEquals(CteciFronta.Prijeti.PRIJATO, s.fronta.priprav(a, listOf("Jdu.")))
        assertEquals(a, s.v(1000)!!.token)
        assertEquals(b, s.v(2500)!!.token)
        assertNull(s.v(4000))
        assertEquals(2, s.fronta.dokoncenych)
    }

    @Test fun textovyLimitPlatiProSoucetVsechStranekRepliky() {
        val f = CteciFronta(maxZnakuRepliky = 8).apply { nastavCasovani(125) }
        val a = f.zarad(0)!!
        val b = f.zarad(100)!!
        assertEquals(CteciFronta.Prijeti.PRIJATO, f.priprav(a, listOf("Ano.", "Ano.")))
        assertEquals(CteciFronta.Prijeti.LIMIT_TEXTU, f.priprav(b, listOf("Jdeme.", "Ano.")))
        assertEquals("Ano.", f.zobraz(0)!!.text)
        assertEquals(1, f.zobraz(1500)!!.index)
        assertNull(f.zobraz(3000))
        assertEquals(1, f.dokoncenych)
        assertEquals(1, f.pocet)
    }

    @Test fun resetUvolniKapacituTextuIReplik() {
        val f = CteciFronta(maxReplik = 1, maxZnakuRepliky = 4).apply { nastavCasovani(125) }
        val a = f.zarad(0)!!
        f.priprav(a, listOf("Ano."))
        f.zobraz(0)
        f.reset()
        val b = f.zarad(500)!!
        assertEquals(CteciFronta.Prijeti.PRIJATO, f.priprav(b, listOf("Ano.")))
        assertEquals(b, f.zobraz(500)!!.token)
        assertEquals(0, f.dokoncenych)
        assertEquals(0, f.selhanych)
    }

    @Test fun prekladPrvniReplikyLzeZrevidovatJestePredZobrazenim() {
        val f = CteciFronta().apply { nastavCasovani(125) }
        val stara = f.zarad(0)!!
        val nova = f.oprav(stara)!!
        assertEquals(CteciFronta.Prijeti.NEPLATNY_TOKEN, f.priprav(stara, listOf("Stará hypotéza.")))
        assertEquals(CteciFronta.Prijeti.PRIJATO, f.priprav(nova, listOf("Správná replika.")))
        assertEquals(nova, f.zobraz(500)!!.token)
    }

    @Test fun pozastaveniNaKonciStrankyNezkratiNasledujiciStranku() {
        val f = CteciFronta().apply { nastavCasovani(125) }
        val a = f.zarad(0)!!
        f.priprav(a, listOf("První.", "Druhá."))
        f.zobraz(0)
        f.pozastav(true, 1500)
        assertNull(f.zobraz(5000))
        f.pozastav(false, 5000)
        val p = f.zobraz(5000)!!
        assertEquals(1, p.index)
        assertEquals(6500L, p.doMs)
        assertNull(f.zobraz(6500))
    }

    @Test fun viceRychlychReplikMuzeCekatZaDelsiStrankouBezPreruseni() {
        val s = Simulace()
        val a = s.nova("č".repeat(140))
        s.v(0)
        val b = s.nova("Ano.", 100)
        val c = s.nova("Jdu.", 200)
        assertEquals(a, s.v(7777)!!.token)
        assertEquals(b, s.v(7778)!!.token)
        assertEquals(c, s.v(9278)!!.token)
        assertNull(s.v(10778))
        assertEquals(listOf(a, b, c), s.zmeny.mapNotNull { it.second?.token })
        assertEquals(3, s.fronta.dokoncenych)
    }

    @Test fun prijateStrankyNejdeZvenkuZmenitMutaciPuvodnihoSeznamu() {
        val f = CteciFronta().apply { nastavCasovani(125) }
        val token = f.zarad(0)!!
        val stranky = mutableListOf("První.", "Druhá.")
        f.priprav(token, stranky)
        stranky[0] = "Jiný text."
        stranky.clear()
        assertEquals("První.", f.zobraz(0)!!.text)
        assertEquals("Druhá.", f.zobraz(1500)!!.text)
        assertNull(f.zobraz(3000))
    }

    @Test fun cekajiciFinalniTextSeNemeniBezNoveRevize() {
        val f = CteciFronta().apply { nastavCasovani(125) }
        val token = f.zarad(0)!!
        f.priprav(token, listOf("Správně."))
        assertEquals(CteciFronta.Prijeti.UZ_PRIPRAVENO, f.priprav(token, listOf("Něco jiného.")))
        assertEquals("Správně.", f.zobraz(0)!!.text)
    }

    @Test fun dlouhyProudPostupneUvolnujeDokoncenePolozkyAVylucujeJejichPozdniDuplikaty() {
        val f = CteciFronta(maxReplik = 1, maxZnakuRepliky = 4).apply { nastavCasovani(125) }
        repeat(1000) { i ->
            val ted = i * 1500L
            val token = f.zarad(ted)!!
            assertEquals(CteciFronta.Prijeti.PRIJATO, f.priprav(token, listOf("Ano.")))
            assertEquals(token, f.zobraz(ted)!!.token)
            assertNull(f.zobraz(ted + 1500))
            assertEquals(CteciFronta.Prijeti.NEPLATNY_TOKEN, f.priprav(token, listOf("Ano.")))
            assertEquals(0, f.pocet)
        }
        assertEquals(1000, f.dokoncenych)
        assertEquals(0, f.selhanych)
    }

    @Test(expected = IllegalArgumentException::class)
    fun zpetnyCasJeChybaVolajiciho() {
        val f = CteciFronta().apply { nastavCasovani(125) }
        f.zobraz(100)
        f.zobraz(99)
    }

    @Test(expected = IllegalArgumentException::class)
    fun prazdnaStrankaNeniPlatnyPreklad() {
        val f = CteciFronta().apply { nastavCasovani(125) }
        f.priprav(f.zarad(0)!!, listOf("  "))
    }
}
