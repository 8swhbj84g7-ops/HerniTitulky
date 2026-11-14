package cz.titulky.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class VyberTitulkuTest {
    private fun radky(texty: List<String>, mezera: Int = 8, blok: Int = 0): List<OcrRadek> =
        VyberTitulku.radky(texty.mapIndexed { i, text ->
            OcrKus(text, 250, i * (20 + mezera), 750, i * (20 + mezera) + 20, blok)
        }, 1000) { it }

    private fun repliky(radky: List<OcrRadek>) = VyberTitulku.repliky(VyberTitulku.vyber(radky)).map { it.text }

    @Test fun jedenRadek() {
        assertEquals(listOf("Wait for me!"), repliky(radky(listOf("Wait for me!"))))
    }

    @Test fun dvaRadkyJedneRepliky() {
        assertEquals(listOf("We have to find the way home."),
            repliky(radky(listOf("We have to find", "the way home."))))
    }

    @Test fun triRadkyJedneRepliky() {
        assertEquals(listOf("We have to find the way back to our home."),
            repliky(radky(listOf("We have to find", "the way back", "to our home."))))
    }

    @Test fun vetsiRadkovaniVeStejnemBlokuNezahodiZacatek() {
        assertEquals(listOf("We have to find the way back to our home."),
            repliky(radky(listOf("We have to find", "the way back", "to our home."), mezera = 20)))
    }

    @Test fun blizkeReplikySeJmenyZustanouOddelene() {
        assertEquals(listOf("Lucia: Come with me.", "Jason: I will."),
            repliky(radky(listOf("Lucia: Come with me.", "Jason: I will."))))
    }

    @Test fun jmenoSeZachovaPresViceRadku() {
        assertEquals(listOf("Arthur: We should go back home."),
            repliky(radky(listOf("Arthur: We should", "go back home."))))
    }

    @Test fun stejnyOcrBlokNeniDukazStejnehoMluvciho() {
        assertEquals(listOf("Lucia: Come with me.", "Jason: I will."),
            repliky(radky(listOf("Lucia: Come with me.", "Jason: I will."), blok = 7)))
    }

    @Test fun velkaMezeraNepridaStaryDialog() {
        assertEquals(listOf("This is the new line."),
            repliky(radky(listOf("This was the old line.", "This is the new line."), mezera = 100)))
    }

    @Test fun vetsiMezeraMeziRuznymiBlokySeNespoji() {
        val vstup = listOf(OcrKus("The previous text.", 250, 0, 750, 20, 0),
            OcrKus("The current text.", 250, 40, 750, 60, 1))
        assertEquals(listOf("The current text."), repliky(VyberTitulku.radky(vstup, 1000) { it }))
    }

    @Test fun stejnyBlokNespojiDvaSloupce() {
        val vstup = listOf(OcrKus("Left column.", 290, 0, 330, 20, 0),
            OcrKus("Right column.", 670, 40, 710, 60, 0))
        assertEquals(listOf("Right column."), repliky(VyberTitulku.radky(vstup, 1000) { it }))
    }

    @Test fun dialogVedleHudu() {
        val vstup = listOf(OcrKus("Find the key", 20, 0, 180, 20, 1),
            OcrKus("We have to leave.", 300, 0, 700, 20, 2))
        assertEquals(listOf("We have to leave."), repliky(VyberTitulku.radky(vstup, 1000) { it }))
    }

    @Test fun menuSeNevybere() {
        assertEquals(emptyList<String>(), repliky(radky(listOf("SETTINGS", "PHOTO MODE"))))
    }

    @Test fun prazdneOcr() {
        assertEquals(emptyList<String>(), repliky(emptyList()))
    }
}
