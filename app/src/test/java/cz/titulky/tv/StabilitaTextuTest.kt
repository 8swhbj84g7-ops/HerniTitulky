package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class StabilitaTextuTest {
    private val cely = "We have to find the way back to our home."

    @Test fun stejnePixelyNepotvrdiOpakovaneZkraceneOcr() {
        val s = StabilitaTextu()
        assertTrue(s.prijmi(cely, "snimek42", 3))
        repeat(4) { assertFalse(s.prijmi("to our home.", "snimek42", 1)) }
        assertTrue(s.prijmi(cely, "snimek42", 3))
        assertFalse(s.prijmi("to our home.", "snimek43", 1))
        assertTrue(s.prijmi("to our home.", "snimek43", 1))
    }

    @Test fun jineZalomeniStejnehoCteniNezrusiOchranuRadku() {
        val s = StabilitaTextu()
        assertTrue(s.prijmi(cely, "stejny", 3))
        assertTrue(s.prijmi(cely, "stejny", 1))
        repeat(3) { assertFalse(s.prijmi("to our home.", "stejny", 1)) }
        repeat(3) { assertFalse(s.prijmi("", "stejny", 0)) }
    }

    @Test fun jednoradkoveCteniZustavaSPuvodnimPotvrzovanim() {
        val s = StabilitaTextu()
        assertTrue(s.prijmi(cely, "stejny", 1))
        assertFalse(s.prijmi("to our home.", "stejny", 1))
        assertTrue(s.prijmi("to our home.", "stejny", 1))
    }

    @Test fun gtaOpravaCteniNeniPokracovaniVety() {
        assertTrue(StabilitaTextu.opakovaneCteni("Oh. thank fuck Youve goiti", "hi thank fuck! You've gotit"))
        assertTrue(StabilitaTextu.opakovaneCteni("hi thank fuck! You've gotit", "Oh thank fuck! You've got it"))
    }

    @Test fun doctenyTitulekSeNeslepiSeSvojimZacatkem() {
        assertTrue(StabilitaTextu.opakovaneCteni("We have to find", "We have to find the key."))
        assertTrue(StabilitaTextu.opakovaneCteni("We have to find the key.", "We have to find"))
    }

    @Test fun skutecnePokracovaniZustavaMozne() {
        assertFalse(StabilitaTextu.opakovaneCteni("we need to", "get that fire lit quick."))
        assertFalse(StabilitaTextu.opakovaneCteni("well we're gonna be...", "gone."))
        assertFalse(StabilitaTextu.opakovaneCteni("", "gone."))
    }

    @Test fun chybejiciProstredniRadekJenJednouNahraduNepotvrdi() {
        val s = StabilitaTextu()
        assertTrue(s.prijmi(cely))
        assertFalse(s.prijmi("We have to find to our home."))
        assertTrue(s.prijmi(cely))
        assertFalse(s.prijmi("to our home."))
    }

    @Test fun skutecnaKratsiReplikaSePotvrdiDruhymCtenim() {
        val s = StabilitaTextu()
        assertTrue(s.prijmi(cely))
        assertFalse(s.prijmi("We have to find"))
        assertTrue(s.prijmi("We have to find"))
    }

    @Test fun jinaReplikaNecekaNaPotvrzeni() {
        val s = StabilitaTextu()
        assertTrue(s.prijmi(cely))
        assertTrue(s.prijmi("Arthur: Get out of here!"))
    }

    @Test fun postupneDopsanyTextSeNezasekneNaPrvniCast() {
        val s = StabilitaTextu()
        assertTrue(s.prijmi("We have to"))
        assertFalse(s.prijmi("We have to find"))
        assertTrue(s.prijmi("We have to find"))
        assertTrue(s.prijmi(cely))
    }

    @Test fun zalomeniNeniZmenaObsahu() {
        val s = StabilitaTextu()
        assertTrue(s.prijmi(cely))
        assertTrue(s.prijmi("We have to find\nthe way back\nto our home."))
    }

    @Test fun prazdnySnimekSeMusiPotvrdit() {
        val s = StabilitaTextu()
        assertTrue(s.prijmi(cely))
        assertFalse(s.prijmi(""))
        assertTrue(s.prijmi(""))
    }

    @Test fun noveCteniPoZmeneOblastiNecekaNaPredchoziObsah() {
        val s = StabilitaTextu()
        s.prijmi(cely)
        s.reset()
        assertTrue(s.prijmi("to our home."))
    }

    @Test fun pozdniPrekladPoZmizeniSeNeukaze() {
        val p = PlatnostPrekladu()
        val token = p.novy()
        p.zrus()
        assertFalse(p.plati(token))
    }

    @Test fun noveVerziNeprepiseVysledekPredchoziVerze() {
        val p = PlatnostPrekladu()
        val starsi = p.novy()
        val novejsi = p.novy()
        assertFalse(p.plati(starsi))
        assertTrue(p.plati(novejsi))
    }

    @Test fun dveRuzneReplikySeOdeslouObeAlePozdniOdpovedNeniAktualni() {
        val p = PlatnostPrekladu()
        val fronta = mutableListOf<Pair<String, Long>>()
        val vystup = mutableListOf<String>()
        fronta += "First line." to p.novy()
        fun odpovez(i: Int) { if (p.plati(fronta[i].second)) vystup += fronta[i].first }
        odpovez(0)
        fronta += "Second line." to p.novy()
        odpovez(1)
        odpovez(0)
        assertEquals(listOf("First line.", "Second line."), fronta.map { it.first })
        assertEquals(listOf("First line.", "Second line."), vystup)
    }

    @Test fun kompletniTextNeprobliknePriJednomNeuplnemSnimku() {
        val s = StabilitaTextu()
        val preklady = mutableListOf<String>()
        var posledni = ""
        for (text in listOf(cely, "to our home.", cely)) {
            if (s.prijmi(text) && text != posledni) { preklady += text; posledni = text }
        }
        assertEquals(listOf(cely), preklady)
    }

    @Test fun doplneniNeniZavisleNaTecceNeboStariRepliky() {
        assertTrue(StabilitaTextu.doplneno("We have to find the key", "We have to find"))
        assertTrue(StabilitaTextu.doplneno("They get here...", "They get her"))
        assertFalse(StabilitaTextu.doplneno("A different sentence.", "We have to find"))
        assertFalse(StabilitaTextu.doplneno("We have to find", "We have to find"))
    }

    @Test fun psaniPoPismenechNevytvoriFrontuPrekladuKazdehoPismene() {
        val s = StabilitaTextu()
        var text = "We have to"
        val pozadavky = mutableListOf<String>()
        if (s.prijmi(text)) pozadavky += text
        for (znak in " find the hidden key") {
            text += znak
            if (s.prijmi(text) && text.trim() != pozadavky.last()) pozadavky += text
        }
        text += "."
        if (s.prijmi(text)) pozadavky += text
        assertEquals("We have to", pozadavky.first())
        assertEquals("We have to find the hidden key.", pozadavky.last())
        assertTrue("Nejvýš začátek, tři dokončená slova a konec: $pozadavky", pozadavky.size <= 5)
    }
}
