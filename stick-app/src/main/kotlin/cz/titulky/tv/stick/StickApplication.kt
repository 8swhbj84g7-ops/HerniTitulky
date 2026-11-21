package cz.titulky.tv.stick

import android.app.Application
import android.webkit.WebView

class StickApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WebView.setWebContentsDebuggingEnabled(false)
    }
}
