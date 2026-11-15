package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

/** Časová posloupnost nad produkční geometrií a stabilitou; neobsahuje ML Kit ani Android Service. */
class PrehraniOcrTest {
    private data class Snimek(val cas: Long, val radky: List<String>, val mezera: Int = 20)
    private class Prehravac {
        private val stabilita = StabilitaTextu()
        private val platnost = PlatnostPrekladu()
        private var posledni = ""
        private var viditelny = ""
        val pozadavky = mutableListOf<Pair<String, Long>>()
        val zmeny = mutableListOf<Pair<Long, String>>()

        fun snimek(s: Snimek) {
            val kusy = s.radky.mapIndexed { i, text ->
                OcrKus(text, 250, i * (20 + s.mezera), 750, i * (20 + s.mezera) + 20, 0)
            }
            val radky = VyberTitulku.radky(kusy, 1000) { it }
            val text = VyberTitulku.repliky(VyberTitulku.vyber(radky)).joinToString("\n") { it.text }
            if (!stabilita.prijmi(text) || text == posledni) return
            posledni = text
            if (text.isEmpty()) { platnost.zrus(); zobraz(s.cas, "") }
            else pozadavky += text to platnost.novy()
        }

        fun odpoved(index: Int, cas: Long) {
            val (text, token) = pozadavky[index]
            if (platnost.plati(token)) zobraz(cas, "Překlad: $text")
        }

        private fun zobraz(cas: Long, text: String) {
            if (text != viditelny) { viditelny = text; zmeny += cas to text }
        }
    }

    @Test fun celeTriRadkyJednouPrelozenePriChybnemSnimkuUprostred() {
        val p = Prehravac()
        val radky = listOf("We have to find", "the way back", "to our home.")
        p.snimek(Snimek(0, radky))
        p.odpoved(0, 80)
        p.snimek(Snimek(100, listOf(radky.last())))
        p.snimek(Snimek(200, radky))
        assertEquals(listOf("We have to find the way back to our home."), p.pozadavky.map { it.first })
        assertEquals(1, p.zmeny.size)
    }

    @Test fun noveZalomeniNevytvoriDalsiPozadavekAniProbliknuti() {
        val p = Prehravac()
        p.snimek(Snimek(0, listOf("We have to find", "the way back", "to our home.")))
        p.odpoved(0, 80)
        p.snimek(Snimek(200, listOf("We have to find the way", "back to our home.")))
        assertEquals(1, p.pozadavky.size)
        assertEquals(1, p.zmeny.size)
    }

    @Test fun pomalyPrekladPoPotvrzenemZmizeniNezobraziStaryText() {
        val p = Prehravac()
        p.snimek(Snimek(0, listOf("We have to leave now.")))
        p.snimek(Snimek(100, emptyList()))
        p.snimek(Snimek(200, emptyList()))
        p.odpoved(0, 2000)
        assertEquals(1, p.pozadavky.size)
        assertTrue(p.zmeny.isEmpty())
    }

    @Test fun pomalyPrekladNeprepiseDruhouRychlouRepliku() {
        val p = Prehravac()
        p.snimek(Snimek(0, listOf("Arthur: We should leave.")))
        p.snimek(Snimek(100, listOf("Dutch: Follow me!")))
        p.odpoved(1, 200)
        p.odpoved(0, 2000)
        assertEquals(listOf("Arthur: We should leave.", "Dutch: Follow me!"), p.pozadavky.map { it.first })
        assertEquals(listOf(200L to "Překlad: Dutch: Follow me!"), p.zmeny)
    }

    @Test fun dveRychleReplikySePriVcasnychOdpovedichObeZobrazi() {
        val p = Prehravac()
        p.snimek(Snimek(0, listOf("Arthur: We should leave.")))
        p.odpoved(0, 50)
        p.snimek(Snimek(100, listOf("Dutch: Follow me!")))
        p.odpoved(1, 150)
        assertEquals(2, p.pozadavky.size)
        assertEquals(2, p.zmeny.size)
        assertEquals(listOf(50L, 150L), p.zmeny.map { it.first })
    }
}
