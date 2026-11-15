package cz.titulky.tv

import org.json.JSONObject

internal data class LgDevCas(val sekundy: Long, val overeno: Long) {
    fun cerstvy(ted: Long) = ted >= overeno && ted - overeno <= 24 * 3600_000L
    fun zbyva(ted: Long) = (sekundy - (ted - overeno).coerceAtLeast(0) / 1000).coerceAtLeast(0)
    fun brzy(ted: Long) = cerstvy(ted) && zbyva(ted) <= 72 * 3600

    companion object {
        fun zOdpovedi(text: String, ted: Long): LgDevCas {
            val json = JSONObject(text)
            require(json.optString("result") == "success")
            val casti = Regex("([0-9]{1,5}):([0-5][0-9]):([0-5][0-9])")
                .matchEntire(json.optString("errorMsg"))?.groupValues ?: error("Unknown duration")
            val hodiny = casti[1].toLong()
            require(hodiny <= 87600 && ted > 0)
            return LgDevCas(hodiny * 3600 + casti[2].toLong() * 60 + casti[3].toLong(), ted)
        }
    }
}
