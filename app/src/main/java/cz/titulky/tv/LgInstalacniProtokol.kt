package cz.titulky.tv

import org.json.JSONObject
import java.net.InetAddress

internal enum class LgChyba { ADRESA, BALICEK, KEY_SERVER, PASSPHRASE, IDENTITA, SSH, PRENOS, INSTALACE, OVERENI, CAS, ZRUSENO, RELACE }
internal class LgInstalacniChyba(val kod: LgChyba) : Exception(kod.name)
internal enum class LgFaze { BALICEK, KLIC, PRIPOJENI, PRENOS, INSTALACE, OVERENI }

internal object LgInstalacniProtokol {
    fun adresa(value: String): String {
        val ip = value.trim()
        val parts = ip.split('.')
        if (parts.size != 4 || parts.any { !it.matches(Regex("0|[1-9][0-9]{0,2}")) || it.toInt() > 255 }) throw LgInstalacniChyba(LgChyba.ADRESA)
        val a = InetAddress.getByAddress(parts.map { it.toInt().toByte() }.toByteArray())
        if ((!a.isSiteLocalAddress && !a.isLinkLocalAddress) || a.isLoopbackAddress || a.isMulticastAddress)
            throw LgInstalacniChyba(LgChyba.ADRESA)
        return ip
    }

    fun klicHttp(data: ByteArray): ByteArray {
        val text = data.toString(Charsets.US_ASCII)
        val split = text.indexOf("\r\n\r\n")
        if (split < 0 || split > 8192 || !text.lineSequence().first().matches(Regex("HTTP/1\\.[01] 200(?: .*)?"))) throw LgInstalacniChyba(LgChyba.KEY_SERVER)
        val headers = text.substring(0, split).lowercase()
        if ("transfer-encoding:" in headers) throw LgInstalacniChyba(LgChyba.KEY_SERVER)
        val body = data.copyOfRange(split + 4, data.size)
        val length = Regex("(?im)^content-length: *([0-9]+)\\r?$").find(text.substring(0, split))?.groupValues?.get(1)?.toLongOrNull()
        if (length != null && length != body.size.toLong()) throw LgInstalacniChyba(LgChyba.KEY_SERVER)
        if (!body.toString(Charsets.US_ASCII).contains("-----END RSA PRIVATE KEY-----")) throw LgInstalacniChyba(LgChyba.KEY_SERVER)
        if (!body.toString(Charsets.US_ASCII).startsWith("-----BEGIN RSA PRIVATE KEY-----") || body.size > 16384) throw LgInstalacniChyba(LgChyba.KEY_SERVER)
        return body
    }

    /** null = stále probíhá; true teprve pro koncový stav instalační služby. */
    fun instalace(json: JSONObject): Boolean? {
        if (json.has("returnValue") && !json.optBoolean("returnValue")) throw LgInstalacniChyba(LgChyba.INSTALACE)
        // LG subscription události po úvodním ACK nemají returnValue, ale nesou ID instalované aplikace.
        if (!json.has("returnValue") && (json.optString("id") != LG_APP_ID || json.optJSONObject("details") == null))
            throw LgInstalacniChyba(LgChyba.INSTALACE)
        val state = json.optJSONObject("details")?.optString("state").orEmpty().uppercase()
        if ("FAILED" in state || "ERROR" in state || state in listOf("CANCELLED", "CANCELED")) throw LgInstalacniChyba(LgChyba.INSTALACE)
        return if (state == "INSTALLED" || state == "SUCCESS" || state.startsWith("SUCCESS_")) true else null
    }

    fun verze(json: JSONObject, balicek: LgBalicek) {
        if (json.optString("id") != LG_APP_ID || json.optString("version") != balicek.verze) throw LgInstalacniChyba(LgChyba.OVERENI)
    }
}
