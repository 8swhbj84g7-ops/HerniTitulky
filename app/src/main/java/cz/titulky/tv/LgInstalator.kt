package cz.titulky.tv

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.UUID

internal data class LgInstalaceVysledek(val verze: String, val spusteno: Boolean)

internal class LgInstalator(
    private val zdroj: LgZdrojBalicku,
    private val znamyOtisk: (String) -> String?,
    private val ulozOtisk: (String, String) -> Unit
) {
    @Volatile private var zruseno = false
    @Volatile private var session: Session? = null
    @Volatile private var http: Socket? = null
    private var faze = LgFaze.BALICEK
    private var platnost = 0L

    fun zrus() {
        zruseno = true
        Thread { runCatching { http?.close() }; runCatching { session?.disconnect() } }.apply { isDaemon = true }.start()
    }

    private fun kontrola() {
        if (zruseno) throw LgInstalacniChyba(LgChyba.ZRUSENO)
        if (System.nanoTime() > platnost) throw LgInstalacniChyba(LgChyba.CAS)
    }

    fun nainstaluj(adresa: String, passphrase: CharArray, prubeh: (LgFaze) -> Unit): LgInstalaceVysledek {
        var key: ByteArray? = null
        val heslo = passphrase.concatToString().toByteArray(Charsets.UTF_8)
        passphrase.fill('\u0000')
        val jsch = JSch()
        platnost = System.nanoTime() + 120_000_000_000L
        val limit = java.util.Timer(true)
        limit.schedule(object : java.util.TimerTask() {
            override fun run() { runCatching { http?.close() }; runCatching { session?.disconnect() } }
        }, 120000)
        fun krok(f: LgFaze) { kontrola(); faze = f; prubeh(f) }
        try {
            val ip = LgInstalacniProtokol.adresa(adresa)
            if (heslo.size != 6) throw LgInstalacniChyba(LgChyba.PASSPHRASE)
            krok(LgFaze.BALICEK)
            val b = zdroj.nacti()
            krok(LgFaze.KLIC)
            key = nactiKlic(ip)
            overHeslo(jsch, key, heslo)
            jsch.addIdentity("playcaption-lg", key, null, heslo)
            krok(LgFaze.PRIPOJENI)
            val otisk = LgSshOtisk(znamyOtisk(ip))
            jsch.hostKeyRepository = otisk
            val ssh = jsch.getSession("prisoner", ip, 9922).also { session = it }
            nastavKompatibilitu(ssh)
            ssh.setConfig("PreferredAuthentications", "publickey")
            ssh.setConfig("StrictHostKeyChecking", "yes")
            ssh.timeout = 8000
            try { ssh.connect(8000) } catch (e: Exception) {
                if (otisk.zmeneny) throw LgInstalacniChyba(LgChyba.IDENTITA)
                throw e
            }
            // shortcut: první spárování důvěřuje domácí síti a klíči Key Serveru; pozdější změnu identity odmítáme.
            ulozOtisk(ip, otisk.novy ?: throw LgInstalacniChyba(LgChyba.SSH))
            krok(LgFaze.PRENOS)
            val cesta = "/media/developer/temp/playcaption-" + UUID.randomUUID().toString() + ".ipk"
            var pozadovanaInstalace = false
            var potvrzenaInstalace = false
            val sftp = ssh.openChannel("sftp") as ChannelSftp
            try {
                sftp.connect(8000)
                runCatching { sftp.mkdir("/media/developer/temp") }
                b.data.inputStream().use { sftp.put(it, cesta) }
                kontrola()
                val kopie = sftp.get(cesta).use { omezene(it, b.data.size + 1) }
                if (!kopie.contentEquals(b.data)) throw LgInstalacniChyba(LgChyba.PRENOS)
                krok(LgFaze.INSTALACE)
                val payload = JSONObject().put("id", LG_APP_ID).put("ipkUrl", cesta).put("subscribe", true)
                pozadovanaInstalace = true
                luna("luna://com.webos.appInstallService/dev/install", payload, true) { LgInstalacniProtokol.instalace(it) == true }
                potvrzenaInstalace = true
                krok(LgFaze.OVERENI)
                val info = sftp.get("/media/developer/apps/usr/palm/applications/$LG_APP_ID/appinfo.json").use { omezene(it, 16384) }
                LgInstalacniProtokol.verze(JSONObject(info.toString(Charsets.UTF_8)), b)
                kontrola()
                val spusteno = runCatching {
                    luna("luna://com.webos.applicationManager/launch", JSONObject().put("id", LG_APP_ID), false) {
                        it.optBoolean("returnValue", false)
                    }
                }.isSuccess
                kontrola()
                return LgInstalaceVysledek(b.verze, spusteno)
            } finally {
                // Pouze vlastní náhodně pojmenovaný dočasný balíček; nainstalovaná aplikace se nemaže.
                // Při přerušení může LG stále číst IPK; soubor jí v takovém případě ponecháme.
                if (!pozadovanaInstalace || potvrzenaInstalace) runCatching { sftp.rm(cesta) }
                sftp.disconnect()
            }
        } catch (e: Exception) {
            if (zruseno) throw LgInstalacniChyba(LgChyba.ZRUSENO)
            if (e is LgInstalacniChyba) throw e
            if (e is SocketTimeoutException || System.nanoTime() > platnost) throw LgInstalacniChyba(LgChyba.CAS)
            throw LgInstalacniChyba(when (faze) {
                LgFaze.BALICEK -> LgChyba.BALICEK
                LgFaze.KLIC -> LgChyba.KEY_SERVER
                LgFaze.PRIPOJENI -> LgChyba.SSH
                LgFaze.PRENOS -> LgChyba.PRENOS
                LgFaze.INSTALACE -> LgChyba.INSTALACE
                LgFaze.OVERENI -> LgChyba.OVERENI
            })
        } finally {
            limit.cancel()
            heslo.fill(0)
            key?.fill(0)
            jsch.removeAllIdentity()
            runCatching { http?.close() }
            http = null
            session?.disconnect()
            session = null
        }
    }

    private fun nactiKlic(ip: String): ByteArray {
        val socket = Socket().also { http = it }
        socket.use {
            it.soTimeout = 5000
            it.connect(InetSocketAddress(ip, 9991), 5000)
            kontrola()
            // LG Key Server je lokální HTTP služba; nezapínáme cleartext pro celou Android aplikaci.
            it.getOutputStream().write("GET /webos_rsa HTTP/1.0\r\nHost: $ip:9991\r\nConnection: close\r\n\r\n".toByteArray(Charsets.US_ASCII))
            return LgInstalacniProtokol.klicHttp(omezene(it.getInputStream(), 24576))
        }
    }

    private fun luna(uri: String, payload: JSONObject, subscribe: Boolean, hotovo: (JSONObject) -> Boolean) {
        val channel = session!!.openChannel("exec") as ChannelExec
        // URI i JSON obsahují pouze pevné ID a námi vytvořenou cestu; žádný shellový vstup uživatele.
        channel.setCommand("/usr/bin/luna-send-pub ${if (subscribe) "-i" else "-n 1"} '$uri' '$payload'")
        // -i drží subscription do EOF na stdin; zavřeme ji až po koncovém výsledku.
        val stdin = channel.outputStream
        val vstup = channel.inputStream
        try {
            channel.connect(8000)
            val pending = StringBuilder()
            var bytes = 0
            while (true) {
                kontrola()
                if (vstup.available() == 0) {
                    if (channel.isClosed) throw LgInstalacniChyba(LgChyba.INSTALACE)
                    Thread.sleep(40)
                    continue
                }
                val value = vstup.read()
                if (value < 0) throw LgInstalacniChyba(LgChyba.INSTALACE)
                if (++bytes > 65536) throw LgInstalacniChyba(LgChyba.INSTALACE)
                pending.append(value.toChar())
                if (value == 10) {
                    val json = try { JSONObject(pending.toString()) } catch (_: JSONException) { continue }
                    pending.setLength(0)
                    if (!json.optBoolean("returnValue", true)) throw LgInstalacniChyba(LgChyba.INSTALACE)
                    if (hotovo(json)) return
                }
            }
        } finally { runCatching { stdin.close() }; channel.disconnect() }
    }

    private fun omezene(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            kontrola()
            val n = input.read(buffer)
            if (n < 0) return out.toByteArray()
            if (out.size() + n > max) throw LgInstalacniChyba(if (faze == LgFaze.KLIC) LgChyba.KEY_SERVER else LgChyba.PRENOS)
            out.write(buffer, 0, n)
        }
    }

    companion object {
        internal fun nastavKompatibilitu(ssh: Session) {
            // Developer Mode na C5 nabízí pouze ssh-rsa; povolujeme jej jen tomuto spojení s kontrolou otisku.
            for (key in listOf("server_host_key", "PubkeyAcceptedAlgorithms"))
                ssh.setConfig(key, JSch.getConfig(key) + ",ssh-rsa")
        }

        internal fun overHeslo(jsch: JSch, key: ByteArray, passphrase: ByteArray) {
            val pair = KeyPair.load(jsch, key, null)
            try {
                if (!pair.isEncrypted) throw LgInstalacniChyba(LgChyba.KEY_SERVER)
                if (!pair.decrypt(passphrase)) throw LgInstalacniChyba(LgChyba.PASSPHRASE)
            } finally { pair.dispose() }
        }
    }
}
