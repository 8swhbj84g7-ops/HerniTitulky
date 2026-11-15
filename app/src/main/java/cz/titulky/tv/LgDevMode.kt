package cz.titulky.tv

import android.content.Context
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

/** Kontrola relace u LG. Žádná operace zde Developer Mode neprodlužuje. */
internal object LgDevMode {
    private val pokusy = ConcurrentHashMap<String, Pair<String, Long>>()
    private val bezi = ConcurrentHashMap.newKeySet<String>()
    private val chyby = ConcurrentHashMap.newKeySet<String>()
    fun bezi(id: String) = id in bezi
    fun chyba(id: String) = id in chyby

    fun obnov(context: Context, id: String, rucne: Boolean = false) {
        val app = context.applicationContext
        val store = LgDevUloziste(app)
        val verze = store.verze(id) ?: return
        val now = System.currentTimeMillis()
        val posledni = pokusy[id]?.takeIf { it.first == verze }?.second ?: store.cas(id)?.overeno ?: 0
        if (!rucne && now >= posledni && now - posledni < 3600_000) return
        if (!bezi.add(id)) return
        pokusy[id] = verze to now
        Thread {
            try {
                val cas = nacti(store.token(id))
                if (store.cas(id, verze, cas)) chyby.remove(id)
            } catch (_: Exception) { if (store.verze(id) == verze) chyby.add(id) }
            finally { bezi.remove(id) }
        }.apply { isDaemon = true }.start()
    }

    private fun nacti(token: String): LgDevCas {
        require(token.matches(Regex("[A-Za-z0-9]{8,256}")))
        val conn = URL("https://developer.lge.com/secure/CheckDevModeSession.dev?sessionToken=$token")
            .openConnection() as HttpsURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            require(conn.responseCode == 200)
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(2048)
                val limit = System.nanoTime() + 16_000_000_000L
                while (true) {
                    require(System.nanoTime() < limit)
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= 16384)
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            return LgDevCas.zOdpovedi(bytes.toString(Charsets.UTF_8), System.currentTimeMillis())
        } finally { conn.disconnect() }
    }
}
