package cz.titulky.tv

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Odpovědi jdou ze stejného soketu na zdrojový port dotazu, ne na port titulků telefonu. */
internal class PribehPrijem(
    private val zobraz: ((PribehProtokol.Titulek, (String) -> Unit) -> Unit)?,
    private val cas: () -> Long
) {
    private var odesilatel: InetAddress? = null
    private var naposledy = 0L
    private val odpovedi = java.util.concurrent.ConcurrentLinkedQueue<DatagramPacket>()

    fun zpracuj(packet: DatagramPacket): Boolean {
        val zprava = PribehProtokol.prijmi(String(packet.data, packet.offset, packet.length, Charsets.UTF_8)) ?: return false
        val prijmi = zobraz ?: return true
        val adresa = packet.address
        val port = packet.port
        val ted = cas()
        if (odesilatel != null && odesilatel != adresa && ted - naposledy < 5000) return true
        fun odpovez(stav: String) {
            if (stav !in listOf("ready", "reading", "done")) return
            val data = "PCA1\t${zprava.id}\t$stav".toByteArray(Charsets.UTF_8)
            odpovedi.add(DatagramPacket(data, data.size, adresa, port))
        }
        val titulek = zprava.titulek
        if (titulek == null) odpovez("ready") else {
            odesilatel = adresa; naposledy = ted
            prijmi(titulek) { stav -> odpovez(stav) }
        }
        return true
    }

    /** Překryv jen zařadí ACK; soket obsluhuje výhradně přijímací vlákno. */
    fun odesliOdpovedi(socket: DatagramSocket) {
        while (true) {
            val odpoved = odpovedi.poll() ?: return
            runCatching { socket.send(odpoved) }
        }
    }
}
