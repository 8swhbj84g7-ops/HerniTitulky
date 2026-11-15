package cz.titulky.tv

/** Napojení finálních překladů na jedinou čtecí frontu. Volat na jednom vlákně. */
internal class PribehovaRelace(
    private val preloz: (List<String>, (String) -> Unit, () -> Unit) -> Unit,
    private val hlaseni: (String) -> Unit
) {
    private val fronta = CteciFronta()
    private var posledni: CteciFronta.Token? = null
    private val barvy = linkedMapOf<CteciFronta.Token, IntArray>()
    val generace get() = fronta.generace
    val pocet get() = fronta.pocet
    fun nastavCasovani(procenta: Int) = fronta.nastavCasovani(procenta)

    fun prijmi(text: List<String>, jmena: IntArray, oprava: Boolean, ted: Long) {
        val token = if (oprava && posledni != null) {
            val stary = posledni!!
            fronta.oprav(stary)?.also { barvy.remove(stary) } ?: return
        } else fronta.zarad(ted) ?: run { hlaseni("Příběhová fronta je plná — přepni do Standardního režimu."); return }
        posledni = token
        barvy[token] = jmena.copyOf()
        preloz(text.toList(), { cesky ->
            if (cesky.isBlank()) chyba(token)
            else if (fronta.priprav(token, listOf(cesky)) == CteciFronta.Prijeti.LIMIT_TEXTU) chyba(token)
        }, { chyba(token) })
    }
    private fun chyba(token: CteciFronta.Token) {
        if (fronta.chyba(token)) { barvy.remove(token); hlaseni("Jednu repliku se nepodařilo přeložit.") }
    }
    fun krok(ted: Long, externi: Boolean, pauza: Boolean): CteciFronta.Stranka? {
        fronta.pozastav(pauza, ted)
        val p = fronta.zobraz(ted, externi)
        barvy.keys.retainAll(barvy.keys.filter { fronta.platny(it) }.toSet())
        return p
    }
    fun barvy(token: CteciFronta.Token) = barvy[token]?.copyOf() ?: intArrayOf()
    fun potvrzeno(token: CteciFronta.Token) = fronta.potvrzeno(token)
    fun chybaVystupu(token: CteciFronta.Token) {
        if (fronta.chybaZobrazeni(token)) { barvy.remove(token); hlaseni("Replika se nevešla do přenosu na TV.") }
    }
    fun reset() { fronta.reset(); barvy.clear(); posledni = null }
}
