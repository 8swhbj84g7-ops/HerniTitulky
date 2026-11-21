package cz.titulky.tv

import android.content.Context
import android.util.Base64
import com.metallic.chiaki.lib.RegistInfo

/**
 * Uložené údaje pro Remote Play (PSN Account ID, IP konzole, klíče z párování).
 * Klíče z párování jsou citlivé – zůstávají jen v televizi, v soukromém úložišti aplikace.
 */
class RemotePlayUdaje(context: Context) {
    private val prefs = context.getSharedPreferences("remote_play", Context.MODE_PRIVATE)

    var accountId: String?
        get() = prefs.getString("account_id", null)
        set(v) = prefs.edit().putString("account_id", v?.trim()).apply()

    /** Přihlášení potvrdit až po zápisu na disk; volat mimo hlavní vlákno. */
    fun ulozAccountId(id: String): Boolean = prefs.edit().putString("account_id", id).commit()

    var ipKonzole: String?
        get() = prefs.getString("ip", null)
        set(v) = prefs.edit().putString("ip", v).apply()

    var registKey: ByteArray?
        get() = prefs.getString("regist_key", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        set(v) = prefs.edit().putString("regist_key", v?.let { Base64.encodeToString(it, Base64.NO_WRAP) }).apply()

    var rpKey: ByteArray?
        get() = prefs.getString("rp_key", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        set(v) = prefs.edit().putString("rp_key", v?.let { Base64.encodeToString(it, Base64.NO_WRAP) }).apply()

    val sparovano get() = ipKonzole != null && registKey != null && rpKey != null

    /** Account ID je 8 bajtů zakódovaných v base64 (např. "AbCdEfGhIjk="). */
    fun accountIdBajty(): ByteArray? = accountId?.let {
        runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull()
            ?.takeIf { b -> b.size == RegistInfo.ACCOUNT_ID_SIZE }
    }
}

/** Spárování televize s PS5 pomocí 8místného PIN kódu z PS5. */
object Parovani {

    fun sparuj(
        context: Context,
        ip: String,
        pin: Int,
        log: (String) -> Unit,
        hotovo: (uspech: Boolean, zprava: String) -> Unit
    ) {
        val udaje = RemotePlayUdaje(context)
        val accountId = udaje.accountIdBajty()
        if (accountId == null) {
            hotovo(false, "Chybí PSN Account ID – nejdřív spusť v počítači skript psn-account-id.ps1.")
            return
        }
        Ps5Registration.sparuj(ip, pin, accountId, log, { host ->
            udaje.ipKonzole = ip
            udaje.registKey = host.rpRegistKey
            udaje.rpKey = host.rpKey
        }, hotovo)
    }
}
