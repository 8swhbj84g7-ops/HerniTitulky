package cz.titulky.tv

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

internal const val LG_APP_ID = "cz.playcaption.overlaytest"
internal fun lgSha256(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

internal data class LgBalicek(val verze: String, val sha256: String, val data: ByteArray) {
    companion object {
        fun over(manifest: String, data: ByteArray): LgBalicek {
            val m = JSONObject(manifest)
            val v = m.getString("version")
            require(m.getInt("schema") == 1 && m.getString("platform") == "webos" && m.getString("id") == LG_APP_ID)
            require(v.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
            require(m.getString("file") == "${LG_APP_ID}_${v}_all.ipk")
            require(data.size.toLong() == m.getLong("size") && data.size > 8)
            require(data.take(8).toByteArray().contentEquals("!<arch>\n".toByteArray()))
            val hash = lgSha256(data)
            require(m.getString("sha256") == hash)
            return LgBalicek(v, hash, data)
        }
    }
}

/** Instalátor dostává balíček; nemusí vědět, odkud pochází. MVP používá pouze assets. */
internal fun interface LgZdrojBalicku { fun nacti(): LgBalicek }

internal class LgAssetBalicek(context: Context) : LgZdrojBalicku {
    private val assets = context.applicationContext.assets
    override fun nacti(): LgBalicek {
        val manifest = assets.open("lg/receiver-manifest.json").bufferedReader().use { it.readText() }
        val data = assets.open("lg/receiver.ipk").use { it.readBytes() }
        return LgBalicek.over(manifest, data)
    }
}
