package cz.titulky.tv

/** Story se zapne až po platném potvrzení konkrétního výstupu, v telefonu rovnou. */
internal object PodporaPribehu {
    fun pouzit(cilIp: String?, potvrzenaIp: String?): Boolean = cilIp == null || cilIp == potvrzenaIp
}
