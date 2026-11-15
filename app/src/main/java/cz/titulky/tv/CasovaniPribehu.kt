package cz.titulky.tv

internal object CasovaniPribehu {
    const val MIN_PROCENT = 50
    const val MAX_PROCENT = 200
    const val VYCHOZI_PROCENTA = 100

    fun dobaMs(text: String, procenta: Int): Long {
        require(procenta in MIN_PROCENT..MAX_PROCENT)
        val nfc = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC)
        val zaklad = maxOf(1500L, (nfc.codePointCount(0, nfc.length) * 1000L + 17) / 18)
        return (zaklad * procenta + 99) / 100
    }
}
