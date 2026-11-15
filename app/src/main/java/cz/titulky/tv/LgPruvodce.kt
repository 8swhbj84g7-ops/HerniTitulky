package cz.titulky.tv

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Navigace je pouze rozpracovaný návod, nikoliv potvrzení instalace nebo spojení. */
internal class LgPruvodceStav(ulozeny: Int) {
    var krok = ulozeny.coerceIn(0, POSLEDNI)
        private set
    fun dalsi(): Boolean {
        if (krok == POSLEDNI) return false
        krok++
        return true
    }
    fun zpet(): Boolean {
        if (krok == 0) return false
        krok--
        return true
    }
    fun prijimacMam() { krok = POSLEDNI }
    companion object { const val POSLEDNI = 4 }
}

/** Samostatné LG instrukce; běžný Android TV průvodce zůstává beze změny. */
class LgPruvodce(private val activity: Activity, private val vyhledej: () -> Unit) {
    private val prefs = activity.getSharedPreferences("lg_pruvodce", Activity.MODE_PRIVATE)
    private val stav = LgPruvodceStav(prefs.getInt("krok", 0))
    private val dialog = object : Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar) {
        @Deprecated("Deprecated in Java")
        override fun onBackPressed() {
            if (stav.zpet()) vykresli() else @Suppress("DEPRECATION") super.onBackPressed()
        }
    }
    private fun t(klic: String) = Texty.t(activity, klic)
    private fun dp(v: Int) = (v * activity.resources.displayMetrics.density).toInt()

    fun ukaz() {
        if (activity.isFinishing || activity.isDestroyed) return
        dialog.setOwnerActivity(activity)
        dialog.setOnCancelListener { uloz() }
        vykresli()
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun uloz() { prefs.edit().putInt("krok", stav.krok).apply() }

    private fun otevri(url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(activity, t("lgBezProhlizece") + "\n" + url, Toast.LENGTH_LONG).show() }
    }

    private fun text(popis: String, velikost: Float, barva: Int = Color.WHITE) = TextView(activity).apply {
        text = popis
        textSize = velikost
        setTextColor(barva)
        setLineSpacing(dp(3).toFloat(), 1.1f)
    }

    private fun tlacitko(popis: String, hlavni: Boolean = false, akce: () -> Unit) = text(popis, 17f).apply {
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        minimumHeight = dp(52)
        setPadding(dp(18), dp(12), dp(18), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.parseColor(if (hlavni) "#E5091A" else "#33343E"))
            cornerRadius = dp(14).toFloat()
        }
        isFocusable = true
        setOnClickListener { akce() }
    }

    private fun vykresli() {
        uloz()
        val koren = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B0E"))
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }
        val hlavicka = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        hlavicka.addView(text("PlayCaption · LG webOS", 22f).apply { typeface = Typeface.DEFAULT_BOLD },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hlavicka.addView(tlacitko(t("lgZavrit")) { dialog.dismiss() })
        koren.addView(hlavicka)
        koren.addView(text(t("lgKrok").replace("{n}", (stav.krok + 1).toString()), 14f, Color.parseColor("#B2B3BF")).apply {
            setPadding(0, dp(18), 0, dp(12))
        })
        val obsah = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(20)) }
        obsah.addView(text(t("lgNadpis" + stav.krok), 27f).apply { typeface = Typeface.DEFAULT_BOLD })
        obsah.addView(text(t("lgText" + stav.krok), 18f, Color.parseColor("#E2E2E8")).apply { setPadding(0, dp(18), 0, dp(14)) })
        fun odkaz(popis: String, url: String) {
            obsah.addView(tlacitko(t(popis) + "  ↗") { otevri(url) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })
        }
        when (stav.krok) {
            0 -> obsah.addView(tlacitko(t("lgUzMam")) { stav.prijimacMam(); vykresli() })
            1 -> odkaz("lgUcetOdkaz", "https://webostv.developer.lge.com/")
            2 -> odkaz("lgOficialniNavod", "https://webostv.developer.lge.com/develop/getting-started/developer-mode-app")
            3 -> {
                odkaz("lgBalicek", "https://github.com/AdamRuzick4/PlayCaption-app/releases")
                odkaz("lgSpravce", "https://github.com/webosbrew/dev-manager-desktop/releases/latest")
                odkaz("lgCelyNavod", "https://github.com/AdamRuzick4/PlayCaption-app/blob/master/webos/NAVOD-LG.md")
            }
            4 -> odkaz("lgOficialniNavod", "https://webostv.developer.lge.com/develop/getting-started/developer-mode-app")
        }
        koren.addView(ScrollView(activity).apply { isFillViewport = true; addView(obsah) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val navigace = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        navigace.addView(tlacitko(t("zpet")) {
            if (stav.zpet()) vykresli() else dialog.dismiss()
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(12) })
        navigace.addView(tlacitko(t(if (stav.krok == LgPruvodceStav.POSLEDNI) "lgHledat" else "lgDalsi"), hlavni = true) {
            if (stav.dalsi()) vykresli() else {
                dialog.dismiss()
                vyhledej()
            }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        koren.addView(navigace)
        dialog.setContentView(koren)
    }
}
