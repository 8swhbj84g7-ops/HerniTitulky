package cz.titulky.tv

internal object PribehProtokol {
    data class Odpoved(val id: String, val stav: String)
    private val id = Regex("[a-f0-9]{32}\\.\\d{1,10}\\.\\d{1,10}\\.\\d{1,10}")
    fun odpoved(text: String): Odpoved? {
        val p = text.split('\t')
        if (p.size != 3 || p[0] != "PCA1" || !id.matches(p[1]) || p[2] !in listOf("ready", "reading", "done")) return null
        return Odpoved(p[1], p[2])
    }
    fun dotaz(token: String) = "PCQ1\t$token"
    fun titulek(token: String, text: String, pauza: Boolean, rozvrh: FloatArray, barvy: IntArray): String {
        require(id.matches(token) && rozvrh.size == 4 && rozvrh.all { it.isFinite() })
        val escaped = text.replace("\\", "\\\\").replace("\t", " ").replace("\n", "\\n")
        require(escaped.length <= 4096) { "Příběhový titulek přesahuje limit přijímače" }
        val packet = "PCS1\t$token\t${if (pauza) 1 else 0}\t$escaped\t${rozvrh.joinToString("\t")}\t${barvy.joinToString(",")}"
        require(packet.toByteArray(Charsets.UTF_8).size <= 8192) { "Příběhový titulek přesahuje limit UDP" }
        return packet
    }
}
