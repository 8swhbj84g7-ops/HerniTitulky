package cz.titulky.tv

internal object CasovaniPribehu {
    const val MIN_PROCENT = 50
    const val MAX_PROCENT = 200
    const val VYCHOZI_PROCENTA = 100

    fun priZpozdeni(procenta: Int, cekajici: Int): Int {
        require(procenta in MIN_PROCENT..MAX_PROCENT && cekajici >= 0)
        if (cekajici < 3) return procenta
        val nasobek = maxOf(0.5, Math.pow(0.85, (cekajici - 2).coerceAtMost(5).toDouble()))
        return maxOf(MIN_PROCENT, Math.round(procenta * nasobek).toInt())
    }

    fun dobaMs(text: String, procenta: Int): Long {
        require(procenta in MIN_PROCENT..MAX_PROCENT)
        val nfc = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
        val zaklad = maxOf(1500L, (nfc.codePointCount(0, nfc.length) * 1000L + 17) / 18)
        return (zaklad * procenta * 80 + 9999) / 10000
    }
}
