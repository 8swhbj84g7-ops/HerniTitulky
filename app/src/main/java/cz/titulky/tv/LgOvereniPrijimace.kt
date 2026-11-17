package cz.titulky.tv

/** Jednorázový důkaz síťové dostupnosti; nic neříká o vykreslení ani o překladu. */
internal class LgOvereniPrijimace {
    class Pokus(val cil: Propojeni.Televize)
    data class Vysledek(val cil: Propojeni.Televize, val nalezen: Boolean, val overenoKdy: Long)
    private var pokus: Pokus? = null
    val bezi get() = pokus != null
    var vysledek: Vysledek? = null
        private set

    fun zacni(cil: Propojeni.Televize): Pokus {
        vysledek = null
        return Pokus(cil).also { pokus = it }
    }

    fun dokonci(aktualni: Pokus, nalezene: List<Propojeni.Televize>, ted: Long): Boolean {
        if (pokus !== aktualni) return false
        pokus = null
        val cil = aktualni.cil
        vysledek = Vysledek(cil, nalezene.any { it.id == cil.id && it.ip == cil.ip }, ted)
        return true
    }

    fun zrus() { pokus = null; vysledek = null }

    companion object {
        fun cil(kontrola: LgKontrola?, overenaIp: String, zadanaIp: String,
            profily: List<TvProfil>, posledniId: String?): Propojeni.Televize? {
            kontrola?.takeIf { overenaIp == zadanaIp }?.let { p ->
                return if (p.verze == null) null else Propojeni.Televize(p.id, "LG PlayCaption", overenaIp, false)
            }
            val vhodne = profily.filter { it.platforma == TvPlatforma.WEBOS && (zadanaIp.isBlank() || it.ip == zadanaIp) }
            val p = vhodne.firstOrNull { it.id == posledniId } ?: vhodne.singleOrNull()
            return p?.let { Propojeni.Televize(it.id, it.jmeno, it.ip, false) }
        }
    }
}
