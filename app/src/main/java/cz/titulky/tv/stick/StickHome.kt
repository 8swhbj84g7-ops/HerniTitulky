package cz.titulky.tv.stick

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Gravity
import android.widget.*
import cz.titulky.tv.MainActivity
import cz.titulky.tv.Rezim
import cz.titulky.tv.Texty

abstract class StickPage : Activity() {
    internal val config by lazy { StickConfig(this) }
    protected lateinit var column: LinearLayout
    protected fun dp(n: Int) = (n * resources.displayMetrics.density + 0.5f).toInt()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        StickRuntime.prepare()
    }
    protected fun page(title: String) {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(17, 30, 40)) }
        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(20), dp(48), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scroll.addView(column)
        setContentView(scroll)
        label(title, 30f)
    }
    protected fun label(value: String, size: Float = 17f) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        setPadding(0, dp(4), 0, dp(6))
        column.addView(this)
    }
    protected fun button(value: String, press: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setOnClickListener { press() }
        column.addView(this, LinearLayout.LayoutParams(dp(380), -2))
    }
    protected fun notice(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

class StickHome : StickPage() {
    private var settings = false
    private var testing: StickPad? = null
    private var reading: TextView? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        if (Rezim.jeTelefon(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        home()
    }
    private fun home() {
        settings = false
        page("Herní titulky")
        button("Připojit PlayStation 5") { startActivity(Intent(this, StickPair::class.java)) }
        val play = button(if (config.language == "cs") "Spustit hraní s českými titulky" else "Spustit hraní s titulky") {
            startActivity(Intent(this, if (config.paired) StickGame::class.java else StickPair::class.java))
        }
        button("Nastavení") { options() }
        button("Diagnostika") {
            val text = config.prefs.getString("report", null) ?: "Zatím neproběhlo žádné měření při hraní."
            AlertDialog.Builder(this).setTitle("Poslední měření").setMessage(text)
                .setPositiveButton("Zavřít", null).show()
        }
        button("Původní režimy aplikace") { startActivity(Intent(this, MainActivity::class.java)) }
        if (config.paired) play.requestFocus()
    }
    private fun choice(title: String, values: List<String>, index: Int, save: (Int) -> Unit) {
        var selected = index
        val row = button("$title · ${values[index]}") {}
        row.setOnClickListener {
            AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(values.toTypedArray(), selected) { d, i ->
                save(i)
                row.text = "$title · ${values[i]}"
                selected = i
                d.dismiss()
            }.show()
        }
        selected = index
    }
    private fun options() {
        testing = null
        reading = null
        settings = true
        page("Nastavení")
        val p = config.prefs
        fun indexed(title: String, key: String, labels: List<String>, default: Int = 0) =
            choice(title, labels, p.getInt(key, default).coerceIn(labels.indices)) {
                p.edit().putInt(key, it).apply()
            }
        indexed("Kvalita streamu", "quality", Quality.values().map { it.label })
        val languages = Texty.JAZYKY
        choice("Jazyk překladu", languages.map { it.nazev }, languages.indexOfFirst { it.kod == config.language }.coerceAtLeast(0)) {
            p.edit().putString("lang", languages[it].kod).apply()
        }
        indexed("Velikost titulků", "size", listOf("Menší", "Střední", "Velké"), 1)
        indexed("Umístění", "position", listOf("Dole", "Nahoře"))
        indexed("Krytí pozadí", "opacity", listOf("0 %", "50 %", "80 %", "100 %"), 2)
        choice("Čtení", listOf("Standardní", "Příběhové"), if (config.story) 1 else 0) {
            p.edit().putBoolean("story", it == 1).apply()
        }
        indexed("Obnovení spojení", "retry", listOf("Automaticky (3 pokusy)", "Ručně"))
        indexed("Diagnostika ve hře", "stats", listOf("Skrýt", "Zobrazit"))
        indexed("Pravá páčka", "axes", listOf("Automaticky", "Osy Z / RZ", "Osy RX / RY"))
        button("Vyzkoušet tlačítka a páčky") {
            testing = StickPad(p.getInt("axes", 0))
            page("Zkouška ovladače")
            reading = label("Na DualSense stiskni tlačítko nebo pohni páčkou.")
            button("Zpět dálkovým ovladačem") { options() }
        }
        button("Připojit ovladač Bluetooth") {
            runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
        button("Zpět") { home() }
    }
    private fun showInput(pad: StickPad, event: String) {
        val s = pad.state
        reading?.text = "${pad.name} · $event\nLevá páčka ${s.leftX} / ${s.leftY} · pravá ${s.rightX} / ${s.rightY}\nL2 ${s.l2State} · R2 ${s.r2State}"
    }
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        testing?.let { if (it.key(e)) { showInput(it, KeyEvent.keyCodeToString(e.keyCode)); return true } }
        return super.dispatchKeyEvent(e)
    }
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        testing?.let { if (it.move(e)) { showInput(it, "pohyb"); return true } }
        return super.onGenericMotionEvent(e)
    }
    @Deprecated("Android TV back key")
    override fun onBackPressed() { if (testing != null) options() else if (settings) home() else super.onBackPressed() }
}
