package cz.titulky.tv

/** Čtecí politika bez OCR, překladače a IO. Všechna volání patří jednomu vlastníku (např. UI vláknu). */
internal class CteciFronta(private val maxReplik: Int = 128, private val maxZnakuRepliky: Int = 8192) {
    data class Token(val relace: Long, val poradi: Long, val revize: Long)
    data class Stranka(
        val token: Token, val index: Int, val pocet: Int, val text: String,
        val puvodniCasMs: Long, val odMs: Long, val doMs: Long
    )
    enum class Prijeti { PRIJATO, OPAKOVANI, NEPLATNY_TOKEN, UZ_PRIPRAVENO, LIMIT_TEXTU }

    private class Replika(var token: Token, val puvodniCasMs: Long, var stranky: List<String>? = null)
    private val repliky = linkedMapOf<Long, Replika>()
    private var relace = 0L
    private var poradi = 0L
    private var aktualni: Stranka? = null
    private var pozastaveno = false
    private var zbyvaMs = 0L
    private var posledniCasMs = 0L
    private var casProcenta = CasovaniPribehu.VYCHOZI_PROCENTA
    fun nastavCasovani(procenta: Int) {
        require(procenta in CasovaniPribehu.MIN_PROCENT..CasovaniPribehu.MAX_PROCENT)
        casProcenta = procenta
    }
    var dokoncenych = 0
        private set
    var selhanych = 0
        private set
    val pocet get() = repliky.size
    val generace get() = relace
    fun platny(token: Token) = najdi(token) != null

    /** LG potvrzuje dočtení celé repliky; zde je pro externí výstup uložená jako jediný celek. */
    fun potvrzeno(token: Token): Boolean {
        if (aktualni?.token != token || aktualni?.pocet != 1) return false
        repliky.remove(token.poradi)
        aktualni = null
        dokoncenych++
        return true
    }
    fun chybaZobrazeni(token: Token): Boolean {
        if (aktualni?.token != token) return false
        repliky.remove(token.poradi); aktualni = null; selhanych++
        return true
    }

    init { require(maxReplik > 0 && maxZnakuRepliky > 0) }

    /** Volat jednou pro novou repliku, v pořadí zdroje a před spuštěním překladu. null = plná fronta. */
    fun zarad(puvodniCasMs: Long): Token? {
        require(puvodniCasMs >= 0)
        if (pocet >= maxReplik) return null
        val token = Token(relace, ++poradi, 0)
        repliky[token.poradi] = Replika(token, puvodniCasMs)
        return token
    }

    /** Oprava čekajícího obsahu zachová jeho pořadí. Rozečtenou repliku už nepřepisujeme. */
    fun oprav(token: Token): Token? {
        val replika = najdi(token) ?: return null
        if (aktualni?.token == token) return null
        replika.stranky = null
        replika.token = token.copy(revize = token.revize + 1)
        return replika.token
    }

    /** Finální české stránky; jejich zalomení musí dodat výstupní vrstva, ne odhad této fronty. */
    fun priprav(token: Token, stranky: List<String>): Prijeti {
        val replika = najdi(token) ?: return Prijeti.NEPLATNY_TOKEN
        if (replika.stranky != null)
            return if (replika.stranky == stranky) Prijeti.OPAKOVANI else Prijeti.UZ_PRIPRAVENO
        require(stranky.isNotEmpty() && stranky.all { it.isNotBlank() })
        var delka = 0L
        for (stranka in stranky) {
            delka += stranka.length
            // Samostatný limit zabrání, aby hotové B zaplnilo místo pro opožděné A.
            if (delka > maxZnakuRepliky) return Prijeti.LIMIT_TEXTU
        }
        replika.stranky = stranky.toList()
        return Prijeti.PRIJATO
    }

    /** Překladač ukončil čekající požadavek chybou. Platný připravený překlad chyba nesmaže. */
    fun chyba(token: Token): Boolean {
        val replika = najdi(token) ?: return false
        if (replika.stranky != null) return false
        repliky.remove(token.poradi)
        selhanych++
        return true
    }

    /** Vrací aktuální stránku. Pozdní tick nesmí přeskočit stránky, které hráč nikdy neviděl. */
    fun zobraz(tedMs: Long, externiDokonceni: Boolean = false): Stranka? {
        cas(tedMs)
        if (pozastaveno) return null
        val predchozi = aktualni
        if (predchozi != null) {
            if (externiDokonceni || tedMs < predchozi.doMs) return predchozi
            val replika = repliky.getValue(predchozi.token.poradi)
            if (predchozi.index + 1 < predchozi.pocet) return stranka(replika, predchozi.index + 1, tedMs)
            repliky.remove(predchozi.token.poradi)
            dokoncenych++
            aktualni = null
        }
        val prvni = repliky.values.firstOrNull() ?: return null
        if (prvni.stranky == null) return null
        return stranka(prvni, 0, tedMs)
    }

    /** Volby nebo odpojený přijímač zastaví čtení; po návratu zbývá stejný čas. */
    fun pozastav(ano: Boolean, tedMs: Long) {
        cas(tedMs)
        if (ano == pozastaveno) return
        if (ano) zbyvaMs = aktualni?.let { (it.doMs - tedMs).coerceAtLeast(0) } ?: 0
        else aktualni = aktualni?.copy(doMs = Math.addExact(tedMs, zbyvaMs))
        pozastaveno = ano
    }

    /** Změna režimu/jazyka/oblasti/konzole: staré tokeny už nemohou obnovit minulou relaci. */
    fun reset() {
        relace++
        repliky.clear()
        aktualni = null
        pozastaveno = false
        zbyvaMs = 0
        dokoncenych = 0
        selhanych = 0
    }

    private fun najdi(token: Token) = repliky[token.poradi]?.takeIf { it.token == token }

    private fun stranka(replika: Replika, index: Int, tedMs: Long): Stranka {
        val stranky = replika.stranky!!
        val text = stranky[index]
        val dobaMs = CasovaniPribehu.dobaMs(text, casProcenta)
        return Stranka(replika.token, index, stranky.size, text, replika.puvodniCasMs,
            tedMs, Math.addExact(tedMs, dobaMs)).also { aktualni = it }
    }

    private fun cas(tedMs: Long) {
        require(tedMs >= posledniCasMs) { "Čtecí fronta vyžaduje monotónní čas" }
        posledniCasMs = tedMs
    }
}
