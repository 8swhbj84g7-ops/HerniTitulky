package cz.titulky.tv.stick

internal object StickPairingInput {
    private val base64Id = Regex("[A-Za-z0-9+/]{10}[AEIMQUYcgkosw048]=")
    private val code = Regex("[0-9]{8}")
    private val octet = Regex("0|[1-9][0-9]{0,2}")

    fun accountId(value: String) = base64Id.matches(value)
    fun registrationCode(value: String) = code.matches(value)

    fun localAddress(value: String): Boolean {
        val octets = value.split('.')
        if (octets.size != 4 || octets.any { !octet.matches(it) || it.toInt() > 255 }) return false
        val first = octets[0].toInt()
        val second = octets[1].toInt()
        // A literal RFC 1918 address keeps both discovery and the TCP handshake on the LAN.
        return first == 10 || first == 172 && second in 16..31 || first == 192 && second == 168
    }
}
