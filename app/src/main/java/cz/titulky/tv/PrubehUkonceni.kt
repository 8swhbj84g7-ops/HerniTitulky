package cz.titulky.tv

import java.util.concurrent.atomic.AtomicBoolean

/** Pouze měří původní volání; limit nikdy nepřerušuje nativní práci ani neuvolňuje její paměť. */
internal class PrubehUkonceni(
    private val id: String,
    private val ted: () -> Long,
    private val zapis: (String) -> Unit
) {
    private class Krok(val nazev: String, val od: Long, val vlakno: Thread) {
        val varovano = AtomicBoolean(false)
    }
    private val od = ted()
    @Volatile private var krok: Krok? = null
    @Volatile private var hotovo = false

    init { zapis("[PS5 konec #$id] začátek") }

    fun proved(nazev: String, akce: () -> Unit) {
        val k = Krok(nazev, ted(), Thread.currentThread())
        krok = k
        zapis("[PS5 konec #$id] $nazev začátek")
        try {
            akce()
        } catch (e: Throwable) {
            zapis("[PS5 konec #$id] $nazev chyba ${e.javaClass.simpleName}")
        } finally {
            krok = null
            zapis("[PS5 konec #$id] $nazev konec ${ted() - k.od} ms")
        }
    }

    /** Vrací, zda se má diagnostická kontrola zopakovat. */
    fun kontrola(limitMs: Long): Boolean {
        val k = krok
        if (!hotovo && k != null && ted() - k.od >= limitMs && k.varovano.compareAndSet(false, true)) {
            // Stack obsahuje pouze názvy metod a souborů, ne argumenty relace.
            val stack = runCatching { k.vlakno.stackTrace.take(20) }.getOrDefault(emptyList())
            if (krok === k && !hotovo) {
                zapis("[PS5 konec #$id] $limitMs ms překročeno: ${k.nazev}, trvá ${ted() - k.od} ms, vlákno ${k.vlakno.name}")
                stack.forEach { zapis("[PS5 stack #$id] $it") }
            }
        }
        return !hotovo
    }

    fun dokonci() {
        hotovo = true
        zapis("[PS5 konec #$id] dokončeno ${ted() - od} ms")
    }
}
