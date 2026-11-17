package cz.titulky.tv

/** LG potřebuje telefon jako zdroj OCR; převzetí spojení Android TV se nemění. */
internal object TelefonPozadi {
    fun pokracovatNaLg(aktivni: Boolean, cilIp: String?, platforma: TvPlatforma?): Boolean =
        aktivni && !cilIp.isNullOrBlank() && platforma == TvPlatforma.WEBOS
}
