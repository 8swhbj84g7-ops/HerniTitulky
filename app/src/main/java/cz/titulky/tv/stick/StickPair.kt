package cz.titulky.tv.stick

import android.net.Uri
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Base64
import android.util.Patterns
import android.view.WindowManager
import android.webkit.*
import android.widget.EditText
import cz.titulky.tv.HledacPS
import cz.titulky.tv.Parovani
import cz.titulky.tv.PsnPrihlaseni

class StickPair : StickPage() {
    private var browser: WebView? = null
    private var account = ""
    private var address = ""
    private var busy = false
    private var attempt = 0

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        form()
    }
    private fun field(hintText: String, kind: Int, initial: String = "") = EditText(this).apply {
        hint = hintText
        inputType = kind
        isSingleLine = true
        isSaveEnabled = false
        setText(initial)
        column.addView(this, dp(380), -2)
    }
    private fun form() {
        page("Připojit PlayStation 5")
        label("Na PS5 zapni Remote Play a zvol Propojit zařízení u účtu, na kterém chceš hrát.")
        val signin = button("Přihlásit se u Sony na Sticku") {}
        val id = field("PSN Account ID (Base64)", InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, account)
        val ip = field("IP adresa PS5", InputType.TYPE_CLASS_TEXT, address)
        signin.setOnClickListener { address = ip.text.toString(); login() }
        button("Najít PS5 v domácí síti") {
            Thread {
                val found = runCatching { HledacPS.hledej().filter { it.typ == "PS5" } }.getOrDefault(emptyList())
                runOnUiThread {
                    if (!ip.isAttachedToWindow) return@runOnUiThread
                    when (found.size) {
                        1 -> ip.setText(found.first().ip)
                        0 -> notice("PS5 neodpověděla. Zadej její IP adresu.")
                        else -> android.app.AlertDialog.Builder(this).setTitle("Vyber konzoli")
                            .setItems(found.map { it.jmeno + " · " + it.ip }.toTypedArray()) { _, i -> ip.setText(found[i].ip) }.show()
                    }
                }
            }.start()
        }
        val pin = field("Osmimístný kód z PS5", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        pin.filters = arrayOf(InputFilter.LengthFilter(8))
        button("Spárovat") {
            if (busy) return@button
            val key = runCatching { Base64.decode(id.text.toString().trim(), Base64.DEFAULT) }.getOrNull()
            val host = ip.text.toString().trim()
            val code = pin.text.toString()
            if (key?.size != 8 || !Patterns.IP_ADDRESS.matcher(host).matches() || !Regex("[0-9]{8}").matches(code)) {
                notice("Zkontroluj Account ID, IP adresu a osmimístný kód.")
                return@button
            }
            busy = true
            notice("Páruji konzoli…")
            Parovani.sparuj(host, code.toInt(), key, {}, { config.save(host, it.rpRegistKey, it.rpKey) }) { ok, message ->
                runOnUiThread {
                    busy = false
                    if (!isDestroyed && !isFinishing) { notice(message); if (ok) finish() }
                }
            }
        }
    }

    private fun login() {
        if (busy) return
        val token = ++attempt
        val web = runCatching { WebView(this) }.getOrElse {
            notice("Prohlížeč není dostupný. Zadej PSN Account ID ručně.")
            return
        }
        browser = web
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        var exchanging = false
        fun navigate(uri: Uri): Boolean {
            if (uri.scheme != "https") return true
            if (uri.host == "remoteplay.dl.playstation.net" && uri.path == "/remoteplay/redirect") {
                val code = uri.getQueryParameter("code")
                if (!exchanging && !code.isNullOrEmpty()) {
                    exchanging = true
                    Thread {
                        val result = runCatching { PsnPrihlaseni.accountId(code) }
                        runOnUiThread {
                            if (token != attempt || isDestroyed || isFinishing) return@runOnUiThread
                            result.onSuccess { account = it }.onFailure { notice("Sony přihlášení nepotvrdilo. Lze zadat Account ID ručně.") }
                            closeBrowser()
                        }
                    }.start()
                }
                return true
            }
            val host = uri.host ?: return true
            return listOf("sony.com", "sonyentertainmentnetwork.com", "playstation.com", "sony.com.cn")
                .none { host == it || host.endsWith("." + it) }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) =
                r.isForMainFrame && navigate(r.url)
            override fun onPageStarted(v: WebView, url: String, icon: android.graphics.Bitmap?) {
                if (navigate(Uri.parse(url))) v.stopLoading()
            }
        }
        setContentView(web)
        web.loadUrl(PsnPrihlaseni.ADRESA_PRIHLASENI)
    }
    private fun closeBrowser() {
        val old = browser
        browser = null
        attempt++
        form()
        old?.destroy()
    }
    @Deprecated("Android TV back key")
    override fun onBackPressed() {
        if (browser != null) closeBrowser() else super.onBackPressed()
    }
    override fun onDestroy() {
        attempt++
        browser?.let { (it.parent as? android.view.ViewGroup)?.removeView(it); it.destroy() }
        super.onDestroy()
    }
}
