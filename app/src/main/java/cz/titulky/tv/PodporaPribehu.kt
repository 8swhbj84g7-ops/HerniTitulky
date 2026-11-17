package cz.titulky.tv

/** Volba transportu pro jeden konkrétní výstup; nastavení režimu hráče nemění. */
internal object PodporaPribehu {
    fun pouzit(cilIp: String?, platforma: TvPlatforma, potvrzenaIp: String?): Boolean =
        cilIp == null || (dotazovat(platforma) && cilIp == potvrzenaIp)

    fun dotazovat(platforma: TvPlatforma): Boolean = platforma != TvPlatforma.ANDROID_TV
}
