package cz.titulky.tv

/** Obsluha ImageReader musí odčerpávat snímky, dokud může starý dekodér ještě kreslit. */
internal class DokonceniVystupu(private val zavriVlakno: () -> Unit) {
    private var vystupu = 0
    private var uklizeno = false
    private var zavreno = false

    @Synchronized fun pridej() { check(!uklizeno); vystupu++ }
    @Synchronized fun uvolni() { check(vystupu > 0); vystupu--; zkusZavrit() }
    @Synchronized fun uklidHotov() { uklizeno = true; zkusZavrit() }

    private fun zkusZavrit() {
        if (uklizeno && vystupu == 0 && !zavreno) { zavreno = true; zavriVlakno() }
    }
}
