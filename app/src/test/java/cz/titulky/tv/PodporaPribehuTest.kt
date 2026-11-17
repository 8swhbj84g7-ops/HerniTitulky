package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class PodporaPribehuTest {
    private val ip = "192.0.2.10"
    @Test fun mistniTelefonStoryZachovaBezSitovehoPotvrzeni() {
        assertTrue(PodporaPribehu.pouzit(null, null))
    }
    @Test fun kazdaTvVcetneAndroiduVyžadujePotvrzeni() {
        assertFalse(PodporaPribehu.pouzit(ip, null))
        assertTrue(PodporaPribehu.pouzit(ip, ip))
    }
    @Test fun potvrzeniStareAdresyNezapneStoryNaNovemCili() {
        assertFalse(PodporaPribehu.pouzit("192.0.2.20", ip))
    }
}
