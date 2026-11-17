package cz.titulky.tv

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Jediný opakovaný stav a odpovědi pouze od zvoleného přijímače; žádná další čtecí fronta. */
internal class PribehSpojeni(val ip: String, port: Int = 41989, private val cas: () -> Long) : AutoCloseable {
    @Volatile var zprava = ""
    @Volatile var odpoved: Pair<PribehProtokol.Odpoved, Long>? = null
        private set
    @Volatile private var bezi = true
    @Volatile private var zruseni: String? = null
    @Volatile var chyba: String? = null
        private set
    @Volatile var podporujePribeh = false
        private set
    init {
        Thread {
            var socket: DatagramSocket? = null
            val buffer = ByteArray(256)
            try {
                val sit = DatagramSocket()
                socket = sit
                sit.connect(InetAddress.getByName(ip), port)
                sit.soTimeout = 300
                while (bezi) {
                    val text = zprava
                    if (text.isEmpty()) { Thread.sleep(50); continue }
                    val data = text.toByteArray(Charsets.UTF_8)
                    runCatching { sit.send(DatagramPacket(data, data.size)) }
                    val incoming = DatagramPacket(buffer, buffer.size)
                    runCatching {
                        sit.receive(incoming)
                        val ack = PribehProtokol.odpoved(String(buffer, 0, incoming.length, Charsets.UTF_8))
                        if (ack != null && ack.id == zprava.split('\t').getOrNull(1)) {
                            odpoved = ack to cas()
                            podporujePribeh = true
                        }
                    }
                    // Odpověď nesmí změnit heartbeat na těsnou smyčku.
                    Thread.sleep(250)
                }
            } catch (e: Exception) {
                if (bezi) chyba = e.javaClass.simpleName
            } finally {
                zruseni?.let { text ->
                    val data = text.toByteArray(Charsets.UTF_8)
                    repeat(3) { runCatching { socket?.send(DatagramPacket(data, data.size)) } }
                }
                socket?.close()
            }
        }.apply { isDaemon = true; name = "PlayCaption-story"; start() }
    }
    fun zrus(packet: String) {
        zruseni = packet
        bezi = false
    }
    override fun close() { bezi = false }
}
