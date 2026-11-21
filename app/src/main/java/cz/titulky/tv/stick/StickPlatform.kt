package cz.titulky.tv.stick

import android.content.Context
import android.content.Intent
import cz.titulky.tv.MainActivity
import cz.titulky.tv.PametObraz
import cz.titulky.tv.Rezim

internal object StickPlatform {
    val onlineTranslation = true
    fun legacyScreen(context: Context): Intent = Intent(context, MainActivity::class.java)
    fun useLegacyHome(context: Context) = Rezim.jeTelefon(context)

    fun loadChiaki() {
        if (StickRuntime.gameProcess) {
            System.loadLibrary("chiaki-hra")
            return
        }
        val memory = Rezim.ulozeny() == true && runCatching { System.loadLibrary("chiaki-pc") }.isSuccess
        if (!memory) System.loadLibrary("chiaki-jni")
        PametObraz.knihovnaPc = memory
    }
}
