package cz.titulky.tv

/** Jediná replika rozdělená podle skutečného boxu; frontu a pořadí překladů řídí telefon. */
internal class PribehPrehravac(private val konecStranky: (String, FloatArray) -> Int) {
    private var paket: PribehProtokol.Titulek? = null
    private val minuleRelace = ArrayDeque<String>()
    private var posledniId: String? = null
    private var zacatek = 0
    private var konec = 0
    private var doKdy = 0L
    private var doba = 0L
    private var cekaNaZobrazeni = false
    private var naposledy = 0L
    private var zbyva = 0L
    private var pauza = false
    private var hotovo = true
    var aktivni = false
        private set
    val stav get() = if (hotovo) "done" else "reading"
    val text get() = if (!aktivni || hotovo || pauza) "" else paket!!.text.substring(zacatek, konec)
    val rozvrh get() = paket?.rozvrh
    val barvy: IntArray get() {
        val p = paket ?: return intArrayOf()
        val radek = p.text.take(zacatek).count { it == '\n' }
        return p.barvy.drop(radek).toIntArray().also {
            if (it.isNotEmpty() && zacatek > 0 && p.text[zacatek - 1] != '\n') it[0] = 0
        }
    }

    /** Měření a přesun okna na pomalé TV nesmí spotřebovat čas dosud neukázané stránky. */
    fun zobrazeno(ted: Long) {
        if (cekaNaZobrazeni && !pauza && !hotovo) { doKdy = ted + doba; cekaNaZobrazeni = false }
    }

    fun prijmi(p: PribehProtokol.Titulek, ted: Long): Boolean {
        val stare = posledniId?.split('.')
        val nove = p.id.split('.')
        if (nove[0] in minuleRelace) return false
        if (stare != null && stare[0] == nove[0]) {
            for (i in 1..3) {
                if (nove[i].toLong() < stare[i].toLong()) return false
                if (nove[i].toLong() > stare[i].toLong()) break
            }
        }
        if (p.id != posledniId) {
            if (stare != null && stare[0] != nove[0]) {
                minuleRelace.addLast(stare[0]); if (minuleRelace.size > 8) minuleRelace.removeFirst()
            }
            paket = p; posledniId = p.id; zacatek = 0; konec = 0
            hotovo = p.text.isBlank(); aktivni = !hotovo; pauza = false
            if (aktivni) dalsi(ted)
        } else {
            val predchozi = paket!!
            if (predchozi.text != p.text) return false
            paket = p
            if (aktivni && !hotovo && !predchozi.rozvrh.contentEquals(p.rozvrh)) {
                konec = zacatek; dalsi(ted); zbyva = doKdy - ted
            }
        }
        naposledy = ted
        return true
    }

    fun krok(ted: Long, skryto: Boolean = false) {
        val p = paket ?: return
        val ticho = ted - naposledy > 5000
        val pozastavit = p.pauza || skryto || ticho
        if (pozastavit != pauza) {
            if (pozastavit) zbyva = if (cekaNaZobrazeni) doba
                else (doKdy - if (ticho) minOf(ted, naposledy + 5000) else ted).coerceAtLeast(0)
            else doKdy = ted + zbyva
            pauza = pozastavit
        }
        if (aktivni && !hotovo && !pauza && !cekaNaZobrazeni && ted >= doKdy) {
            if (konec < p.text.trimEnd().length) dalsi(ted) else hotovo = true
        }
    }

    private fun dalsi(ted: Long) {
        val p = paket!!
        zacatek = konec
        while (zacatek < p.text.length && p.text[zacatek].isWhitespace()) zacatek++
        val zbytek = p.text.substring(zacatek)
        konec = zacatek + konecStranky(zbytek, p.rozvrh).coerceIn(1, zbytek.length)
        val text = p.text.substring(zacatek, konec)
        doba = p.procenta?.let { CasovaniPribehu.dobaMs(text, it) }
            ?: maxOf(1800L, (text.codePointCount(0, text.length) * 1000L + 13) / 14)
        doKdy = ted + doba
        cekaNaZobrazeni = true
    }
}

internal object PribehStranky {
    fun konec(text: String, vejdeSe: (String) -> Boolean): Int {
        var konce = Regex("\\S+").findAll(text).map { it.range.last + 1 }.toList()
        if (konce.isEmpty()) return text.length
        if (!vejdeSe(text.take(konce.first()))) {
            konce = (1..text.codePointCount(0, konce.first())).map { text.offsetByCodePoints(0, it) }
        }
        var dolni = 0; var horni = konce.lastIndex; var konec = konce.first()
        while (dolni <= horni) {
            val stred = (dolni + horni) / 2
            if (vejdeSe(text.take(konce[stred]))) { konec = konce[stred]; dolni = stred + 1 } else horni = stred - 1
        }
        val veta = Regex("[.!?…][\"'”’»)]*(?=\\s|$)").findAll(text.take(konec)).lastOrNull()?.range?.last?.plus(1) ?: 0
        return if (veta * 2 >= konec) veta else konec
    }
}
