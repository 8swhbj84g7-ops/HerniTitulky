package cz.titulky.tv.stick

import android.content.Context
import android.content.Intent

internal object StickPlatform {
    val onlineTranslation = false
    fun legacyScreen(context: Context): Intent? = null
    fun useLegacyHome(context: Context) = false
    fun loadChiaki() = System.loadLibrary("chiaki-hra")
}
