package cz.titulky.tv

import com.metallic.chiaki.lib.RegistInfo
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.Executor

/** Jeden pokus sdílený aplikací a přihlašovací stránkou; kód ani token se nezapisují do záznamu. */
internal class PsnPokus(
    private val vymen: (String) -> String,
    private val uloz: (String) -> Boolean,
    private val spoustec: Executor = Executor { Thread(it).apply { isDaemon = true }.start() },
    private val priZmene: () -> Unit = {}
) {
    enum class Stav { CEKA, OVERUJE, HOTOVO, CHYBA }
    data class Vysledek(val stav: Stav, val chyba: String = "", val http: Int? = null) {
        fun popisChyby(jazyk: String) = Texty.t(jazyk, chyba) + (http?.let { " (HTTP $it)" } ?: "")
    }
    @Volatile var vysledek = Vysledek(Stav.CEKA)
        private set
    val stav get() = vysledek.stav
    val chyba get() = vysledek.chyba
    val http get() = vysledek.http
    private var aktivni = true
    private var posledniKod: String? = null

    fun prihlas(kod: String) {
        synchronized(this) {
            if (!aktivni || stav == Stav.OVERUJE || kod == posledniKod) return
            posledniKod = kod
            vysledek = Vysledek(Stav.OVERUJE)
        }
        priZmene()
        spoustec.execute {
            synchronized(this) { if (!aktivni) return@execute }
            val odpoved = runCatching {
                vymen(kod).also { require(Base64.getDecoder().decode(it).size == RegistInfo.ACCOUNT_ID_SIZE) }
            }
            synchronized(this) {
                if (!aktivni) return@execute
                val e = odpoved.exceptionOrNull()
                vysledek = if (e != null) {
                    val duvod = when {
                        e is PsnHttpChyba && e.status == 400 && e.krok == "token" -> "psnKod"
                        e is PsnHttpChyba && e.status == 429 -> "psnLimit"
                        e is PsnHttpChyba -> "psnSony"
                        e is SocketTimeoutException -> "psnCas"
                        e is IOException -> "psnSit"
                        else -> "psnOdpoved"
                    }
                    Vysledek(Stav.CHYBA, duvod, (e as? PsnHttpChyba)?.status)
                } else if (!runCatching { uloz(odpoved.getOrThrow()) }.getOrDefault(false)) {
                    Vysledek(Stav.CHYBA, "psnUlozeni")
                } else Vysledek(Stav.HOTOVO)
            }
            priZmene()
        }
    }

    @Synchronized fun zrus() { aktivni = false }

    fun chybaStranky() {
        synchronized(this) {
            if (!aktivni) return
            aktivni = false
            vysledek = Vysledek(Stav.CHYBA, "psnStranka")
        }
        priZmene()
    }

    fun lzeParovat(platnyUcet: Boolean) = platnyUcet && when (stav) {
        Stav.CEKA, Stav.HOTOVO -> true
        else -> false
    }

    fun popisChyby(jazyk: String) = vysledek.popisChyby(jazyk)
}

internal class PsnHttpChyba(val krok: String, val status: Int) : IOException("PSN $krok HTTP $status")
