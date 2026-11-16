package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class TextVRameckuTest {
    private val radky = listOf("The Shoebox can store your items. Alan",
        "can access stored items from any", "Shoebox in the Dark Place.")
    private val cely = radky.joinToString(" ")

    @Test fun opakovanyVypadekHornichRadkuNaStejnemObrazuNenahradiCelyText() {
        val t = TextVRamecku()
        assertEquals(cely, t.zpracuj(radky, true, 0, "snimek42"))
        for (cas in listOf(800L, 1600L, 2400L, 3200L)) {
            assertNull(t.zpracuj(listOf(radky.last()), true, cas, "snimek42"))
            assertEquals(cely, t.text)
        }
    }

    @Test fun skutecnaZmenaObrazuDovoliKratsiTextAPotvrzeneZmizeni() {
        val t = TextVRamecku()
        t.zpracuj(radky, true, 0, "snimek42")
        assertNull(t.zpracuj(listOf(radky.last()), true, 800, "snimek43"))
        assertEquals(radky.last(), t.zpracuj(listOf(radky.last()), true, 1600, "snimek43"))
        assertNull(t.zpracuj(emptyList(), true, 2000, "snimek44"))
        assertEquals("", t.zpracuj(emptyList(), true, 3600, "snimek44"))
    }

    @Test fun opakovanyVypadekProstrednihoRadkuAniPrazdneOcrNesmazouStejnyObraz() {
        val t = TextVRamecku()
        t.zpracuj(radky, true, 0, "stejny")
        for (cas in listOf(800L, 1600L, 2400L, 3200L)) {
            assertNull(t.zpracuj(listOf(radky.first(), radky.last()), true, cas, "stejny"))
        }
        assertNull(t.zpracuj(emptyList(), true, 4000, "stejny"))
        assertNull(t.zpracuj(emptyList(), true, 6000, "stejny"))
        assertEquals(cely, t.text)
    }

    @Test fun doplneniAOpravaOcrNaStejnemObrazuSePrijmou() {
        val t = TextVRamecku()
        assertEquals(radky.first(), t.zpracuj(radky.take(1), true, 0, "stejny"))
        assertEquals(cely, t.zpracuj(radky, true, 800, "stejny"))
        val oprava = radky.map { it.replace("Shoebox", "Shoe box") }
        assertEquals(oprava.joinToString(" "), t.zpracuj(oprava, true, 1600, "stejny"))
    }

    @Test fun noveSamostatneTextyAniResetSeNeprilepiKeStaremuOdstavci() {
        val t = TextVRamecku()
        t.zpracuj(radky, true, 0, "stary")
        assertEquals("Open the door.", t.zpracuj(listOf("Open the door."), true, 800, "novy"))
        assertEquals("", t.reset())
        assertEquals(radky.last(), t.zpracuj(listOf(radky.last()), true, 1600, "stary"))
    }

    @Test fun instrukceSePrekladaJakoJedenBlok() {
        assertEquals(cely, TextVRamecku().zpracuj(radky, true, 0))
    }

    @Test fun vypnutyRucniRezimNevytvoriPozadavek() {
        assertNull(TextVRamecku().zpracuj(radky, false, 0))
    }

    @Test fun dlouhoOtevrenaInstrukceSeNevyradi() {
        val t = TextVRamecku()
        assertEquals(cely, t.zpracuj(radky, true, 0))
        assertNull(t.zpracuj(radky, true, 16_000))
        assertNull(t.zpracuj(radky, true, 60_000))
        assertEquals(cely, t.text)
    }

    @Test fun zmenaZalomeniNevyzadaNovyPreklad() {
        val t = TextVRamecku()
        val pozadavky = listOfNotNull(t.zpracuj(radky, true, 0),
            t.zpracuj(listOf("The Shoebox can store your items.",
                "Alan can access stored items from any Shoebox in the Dark Place."), true, 100))
        assertEquals(listOf(cely), pozadavky)
    }

    @Test fun chybnyProstredniRadekNevytvoriProbliknuti() {
        val t = TextVRamecku()
        val vystup = listOfNotNull(t.zpracuj(radky, true, 0),
            t.zpracuj(listOf(radky.first(), radky.last()), true, 100),
            t.zpracuj(radky, true, 200))
        assertEquals(listOf(cely), vystup)
    }

    @Test fun postupneDopsanyOdstavecSeDoplni() {
        val t = TextVRamecku()
        assertEquals(radky.first(), t.zpracuj(radky.take(1), true, 0))
        assertEquals(cely, t.zpracuj(radky, true, 300))
    }

    @Test fun jedenPrazdnySnimekNeschovaTextAleZmizeniAno() {
        val t = TextVRamecku()
        t.zpracuj(radky, true, 0)
        assertNull(t.zpracuj(emptyList(), true, 100))
        assertNull(t.zpracuj(radky, true, 200))
        assertNull(t.zpracuj(emptyList(), true, 1000))
        assertEquals("", t.zpracuj(emptyList(), true, 2600))
        assertEquals("", t.text)
    }

    @Test fun vypnutiSkryjeAZnovuZapnutiPreloziStejnyText() {
        val t = TextVRamecku()
        t.zpracuj(radky, true, 0)
        assertEquals("", t.zpracuj(radky, false, 100))
        assertEquals(cely, t.zpracuj(radky, true, 200))
    }

    @Test fun spojeniRozdelenehoSlova() {
        assertEquals("The instruction is here.", TextVRamecku.spoj(listOf("The instruc-", "tion is here.")))
    }

    @Test fun textBezTeckySePoUstaleniTakeDoplni() {
        val t = TextVRamecku()
        assertEquals("We have to", t.zpracuj(listOf("We have to"), true, 0))
        assertNull(t.zpracuj(listOf("We have to find the key"), true, 100))
        assertEquals("We have to find the key", t.zpracuj(listOf("We have to find the key"), true, 200))
    }
}
