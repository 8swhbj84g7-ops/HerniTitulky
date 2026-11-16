package cz.titulky.tv

/** Stav nativní PS5 cesty v procesu; nový Service nesmí zamaskovat tři prázdná spojení. */
internal class HlidaniPs5 {
    private var relace: Any? = null
    private var spojeno = false
    private var obraz = false
    private var zapocitano = false
    private var prazdnych = 0
    @Volatile var obsazenych = 0
        private set
    @Volatile var restartNutny = false
        private set

    @Synchronized fun nova(s: Any) { relace = s; spojeno = false; obraz = false; zapocitano = false }
    @Synchronized fun aktualni(s: Any?) = s != null && relace === s
    @Synchronized fun pripojeno(s: Any?): Boolean {
        if (!aktualni(s)) return false
        spojeno = true; obsazenych = 0
        return true
    }
    @Synchronized fun obsazeno(s: Any?): Boolean {
        if (!aktualni(s)) return false
        obsazenych++
        return true
    }
    @Synchronized fun snimek(s: Any?): Boolean {
        if (!aktualni(s) || obraz) return false
        obraz = true; prazdnych = 0; obsazenych = 0
        return true
    }
    @Synchronized fun bezObrazu(s: Any?): Boolean {
        if (!aktualni(s) || !spojeno || obraz) return false
        if (!zapocitano) { zapocitano = true; prazdnych++; if (prazdnych >= 3) restartNutny = true }
        return restartNutny
    }
    @Synchronized fun ukonci(s: Any?) {
        if (aktualni(s)) { relace = null; spojeno = false; obraz = false }
    }
}
