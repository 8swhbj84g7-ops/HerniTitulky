package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

/** Správné anglické vstupy a virtuální čas; kvalitu překladače tyto testy nepředstírají. */
class SkladaniVetTest {
    private class Scena {
        val vystup = mutableListOf<Pair<Long, SkladaniVet.Veta>>()
        var ted = 0L
        val buffer = SkladaniVet { vystup += ted to it }
        fun vstup(id: Long, text: String, cas: Long = ted, revize: Int = 0, barva: Int = 0, cekat: Boolean = true) {
            ted = cas; buffer.prijmi(SkladaniVet.Replika(id, revize, text, barva), ted, cekat)
        }
        fun krok(cas: Long) { ted = cas; buffer.krok(ted) }
        fun prerus(cas: Long) { ted = cas; buffer.prerus(ted) }
        fun texty() = vystup.map { it.second.text }
        fun bezDuplicit() {
            val znaky = vystup.flatMap { it.second.zdroje }.flatMap { u ->
                (u.od until u.doZnaku).map { Triple(u.id, u.revize, it) }
            }
            assertEquals("Zdrojový úsek nesmí být spotřebovaný dvakrát", znaky.size, znaky.distinct().size)
        }
    }

    @Test fun uplnaVetaSTeckouJdeIhned() {
        val s = Scena(); s.vstup(1, "I'm going home.")
        assertEquals(listOf("I'm going home."), s.texty()); assertEquals(0L, s.vystup.single().first)
    }
    @Test fun uplneVetyBezTeckyNecekaji() {
        for (text in listOf("Get out of here", "What are you doing", "I don't know", "Come with me")) {
            val s = Scena(); s.vstup(1, text); assertEquals(listOf(text), s.texty())
            assertEquals(0L, s.vystup.single().first)
        }
    }
    @Test fun rozdelenaVetaSePreloziSpolecne() {
        val s = Scena(); s.vstup(1, "I'm drunk and you are such"); assertTrue(s.vystup.isEmpty())
        s.vstup(2, "a downer.", 1400)
        assertEquals(listOf("I'm drunk and you are such a downer."), s.texty())
        assertEquals(listOf(1L, 2L), s.vystup.single().second.zdroje.map { it.id }); s.bezDuplicit()
    }
    @Test fun triCastiSeNeprepisujiPouzePosledniDvojici() {
        val s = Scena(); s.vstup(1, "Trevor: We need to", barva = -65536)
        s.vstup(2, "Trevor: get to the", 700, barva = -65536)
        assertTrue(s.vystup.isEmpty()); s.vstup(3, "Trevor: safe house.", 1300, barva = -65536)
        assertEquals(listOf("Trevor: We need to get to the safe house."), s.texty())
        assertEquals(-65536, s.vystup.single().second.barva); s.bezDuplicit()
    }
    @Test fun blokUKonciStarouVetuAZacneNovou() {
        val s = Scena(); s.vstup(1, "I'm drunk and you are such")
        s.vstup(2, "a downer. I'm going home.", 1000)
        assertEquals(listOf("I'm drunk and you are such a downer.", "I'm going home."), s.texty())
        assertEquals(listOf(1L, 2L), s.vystup[0].second.zdroje.map { it.id })
        assertEquals(listOf(2L), s.vystup[1].second.zdroje.map { it.id }); s.bezDuplicit()
    }
    @Test fun rychleSamostatneVetySeNeslepi() {
        val s = Scena(); s.vstup(1, "I don't know"); s.vstup(2, "Come with me", 100)
        assertEquals(listOf("I don't know", "Come with me"), s.texty()); s.bezDuplicit()
    }
    @Test fun zmenaMluvcihoZabraniSpojeniAUvolniCekajiciText() {
        val s = Scena(); s.vstup(1, "Trevor: You are such")
        s.vstup(2, "Michael: a downer.", 100)
        assertEquals(listOf("Trevor: You are such", "Michael: a downer."), s.texty()); s.bezDuplicit()
    }
    @Test fun znamePreruseniScenyZabraniSpojeni() {
        val s = Scena(); s.vstup(1, "I'm drunk and you are such"); s.prerus(500)
        s.vstup(2, "a downer.", 600)
        assertEquals(listOf("I'm drunk and you are such", "a downer."), s.texty()); s.bezDuplicit()
    }
    @Test fun ocrRevizeNeniPokracovani() {
        val s = Scena(); s.vstup(1, "I'm drunk and you are such")
        s.vstup(1, "I'm tipsy and you are such", 100, revize = 1)
        s.vstup(2, "a downer.", 200)
        assertEquals(listOf("I'm tipsy and you are such a downer."), s.texty())
        assertEquals(1, s.vystup.single().second.zdroje.first().revize); s.bezDuplicit()
        val oprava = Scena(); oprava.vstup(1, "I don't knovv"); oprava.vstup(1, "I don't know", 100, revize = 1)
        assertEquals(listOf("I don't knovv"), oprava.texty()) // Uvolněný překlad už zásobník nepřepisuje.
    }
    @Test fun dopisovaniStejnehoIdNenatahujeDeadlineAniNedublujeObsah() {
        val s = Scena(); s.vstup(1, "We are such")
        s.vstup(1, "We are such a", 1800, revize = 1); s.krok(2500)
        assertEquals(listOf("We are such a"), s.texty()); assertEquals(2500L, s.vystup.single().first)
        s.krok(5000); assertEquals(1, s.vystup.size); s.bezDuplicit()
    }
    @Test fun nenavazujiciTitulekUvolniPredchoziBezSlepeni() {
        val s = Scena(); s.vstup(1, "I'm drunk and you are such")
        s.vstup(2, "I don't know.", 500)
        assertEquals(listOf("I'm drunk and you are such", "I don't know."), s.texty()); s.bezDuplicit()
    }
    @Test fun timeoutAZmizeniJsouOmezeneANeprodluziJeHeartbeat() {
        val s = Scena(); s.vstup(1, "I'm drunk and you are such"); s.krok(2499)
        assertTrue(s.vystup.isEmpty()); s.krok(2500); assertEquals(1, s.vystup.size)
        val mezera = Scena(); mezera.vstup(1, "I'm drunk and you are such")
        mezera.buffer.zmizelo(500); mezera.buffer.zmizelo(900); mezera.krok(1099)
        assertTrue(mezera.vystup.isEmpty()); mezera.krok(1100); assertEquals(1, mezera.vystup.size)
    }
    @Test fun interaktivniVolbaNecekaNaDokonceniAniNespojujePresVolbu() {
        val s = Scena(); s.vstup(1, "I'm drunk and you are such"); s.prerus(100)
        s.vstup(2, "a downer.", 200, cekat = false)
        assertEquals(listOf("I'm drunk and you are such", "a downer."), s.texty()); s.bezDuplicit()
    }
    @Test fun pokracovaniBehemPrekladuNesmiOpakovatUzOdeslanyZacatek() {
        val s = Scena(); s.vstup(1, "I'm drunk and you are such"); s.krok(2500)
        // Překladač drží callback. Uvolnění vstupu je už závazné, nezávisle na jeho rychlosti.
        s.vstup(2, "a downer.", 2600)
        assertEquals(listOf("I'm drunk and you are such", "a downer."), s.texty()); s.bezDuplicit()
    }
    @Test fun pokracovaniPoZobrazeniNevytvoriDruhouKopiiPrekladu() {
        val s = Scena(); s.vstup(1, "I'm drunk and you are such"); s.krok(2500)
        s.vstup(2, "a downer.", 6000)
        assertEquals(listOf("I'm drunk and you are such", "a downer."), s.texty()); s.bezDuplicit()
    }
    @Test fun opakovaneStareOcrIdSeZahodiAleSkutecneNoveStejneSlovoZustane() {
        val s = Scena(); s.vstup(1, "Yes."); s.vstup(2, "Yes.", 100)
        s.vstup(1, "Yes.", 200); s.vstup(2, "Yes.", 300)
        assertEquals(listOf("Yes.", "Yes."), s.texty()); s.bezDuplicit()
    }
    @Test fun slabsiSignalyBezJmenaNespojujiSamostatneDialogy() {
        val s = Scena(); s.vstup(1, "We need to"); s.vstup(2, "go home.", 100)
        assertEquals(listOf("We need to", "go home."), s.texty())
        val b = Scena(); b.vstup(1, "Trevor: We need to"); b.vstup(2, "go home.", 100)
        assertEquals(listOf("Trevor: We need to", "go home."), b.texty())
    }
    @Test fun zkratkyADesetinnaCislaNejsouSamostatneVety() {
        val s = Scena(); s.vstup(1, "Mr. Smith paid 3.14 dollars. I'm leaving.")
        assertEquals(listOf("Mr. Smith paid 3.14 dollars.", "I'm leaving."), s.texty()); s.bezDuplicit()
    }
    @Test fun resetZneplatniStavAUmozniNovouRelaci() {
        val s = Scena(); s.vstup(10, "I'm drunk and you are such"); s.buffer.reset()
        s.vstup(1, "New session.", 100)
        assertEquals(listOf("New session."), s.texty())
    }
    @Test fun revizePoUvolneniPrvniVetyNesmiZnovuVydatSpotrebovaneUseky() {
        val s = Scena(); s.vstup(1, "Hello. We are such")
        s.vstup(1, "Hello there. We are such a", 100, revize = 1); s.krok(2500)
        assertEquals(listOf("Hello.", "We are such"), s.texty()); s.bezDuplicit()
    }
    @Test fun negativniKorpusMalychPismenVycekAniZnamehoJmenaNeniDukazSpojeni() {
        val scenare = listOf(
            "I don't know" to "and I don't care.",
            "Get out of here" to "go home.",
            "What are you doing" to "nothing.",
            "Wait..." to "another car is coming.",
            "Trevor: I'm going home" to "Trevor: good night.",
            "Trevor: We need to" to "Michael: go home.",
            "I'm drunk and you are such" to "the car is here.",
            "I'm drunk and you are such" to "A downer.",
            "We are such" to "a car has stopped."
        )
        for ((a, b) in scenare) {
            val s = Scena(); s.vstup(1, a); s.vstup(2, b, 500); s.krok(3000)
            assertEquals("Nesmí spojit $a / $b", listOf(a, b), s.texty()); s.bezDuplicit()
        }
    }
    @Test fun zmenaBarvyZnamehoMluvcihoANeznameJmenoSpojeniBlokuji() {
        val s = Scena(); s.vstup(1, "Trevor: We need to", barva = -65536)
        s.vstup(2, "Trevor: go home.", 500, barva = -16711936)
        assertEquals(listOf("Trevor: We need to", "Trevor: go home."), s.texty())
    }
    @Test fun limitCastiUvolniVseBezNekonecnehoRetezce() {
        val s = Scena(); s.vstup(1, "Trevor: We need to")
        s.vstup(2, "Trevor: get to the", 100); s.vstup(3, "Trevor: end of the", 200)
        s.vstup(4, "Trevor: road.", 300)
        assertEquals(listOf("Trevor: We need to get to the end of the", "Trevor: road."), s.texty()); s.bezDuplicit()
    }
    @Test fun celyZdrojVcetneZbytkuBlokuJeSpotrebovanyJednou() {
        val s = Scena(); val a = "I'm drunk and you are such"; val b = "a downer. I'm going home."
        s.vstup(1, a); s.vstup(2, b, 1000)
        for ((id, text) in listOf(1L to a, 2L to b)) {
            val spotrebovano = s.vystup.flatMap { it.second.zdroje }.filter { it.id == id }
                .flatMap { (it.od until it.doZnaku).toList() }
            assertEquals(text.indices.filter { !text[it].isWhitespace() }, spotrebovano.filter { !text[it].isWhitespace() })
        }
        s.bezDuplicit()
    }
    @Test fun metrikyPridaneLatenceAPresnostiNaOznacenemKorpusu() {
        var spravne = 0
        val prodlevy = mutableListOf<Long>()
        for ((a, b, spojeny) in listOf(
            Triple("I'm drunk and you are such", "a downer.", "I'm drunk and you are such a downer."),
            Triple("Trevor: We need to", "Trevor: go home.", "Trevor: We need to go home."),
            Triple("Michael: This is such a", "Michael: bad idea.", "Michael: This is such a bad idea.")
        )) {
            val s = Scena(); s.vstup(1, a); s.vstup(2, b, 700)
            assertEquals(listOf(spojeny), s.texty()); s.bezDuplicit()
            spravne++; prodlevy += s.vystup.single().first
        }
        assertEquals(3, spravne); assertEquals(listOf(700L, 700L, 700L), prodlevy)
        // Hotové samostatné věty mají nulové čekání; chybějící pokračování pevný strop.
        val hotove = Scena(); hotove.vstup(1, "Come with me"); assertEquals(0L, hotove.vystup.single().first)
        val timeout = Scena(); timeout.vstup(1, "We are such"); timeout.krok(2500)
        assertEquals(2500L, timeout.vystup.single().first)
    }

}
