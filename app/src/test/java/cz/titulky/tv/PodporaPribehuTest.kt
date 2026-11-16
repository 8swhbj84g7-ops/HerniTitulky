package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class PodporaPribehuTest {
    private val ip = "192.0.2.10"

    @Test fun mistniTelefonStoryZachovaBezSitovehoPotvrzeni() {
        assertTrue(PodporaPribehu.pouzit(null, TvPlatforma.NEZNAMA, null))
    }

    @Test fun androidTvNeprepneNaKolizniStoryProtokol() {
        assertFalse(PodporaPribehu.dotazovat(TvPlatforma.ANDROID_TV))
        assertFalse(PodporaPribehu.pouzit(ip, TvPlatforma.ANDROID_TV, null))
        assertFalse(PodporaPribehu.pouzit(ip, TvPlatforma.ANDROID_TV, ip))
    }

    @Test fun lgASamsungSePotvrzujiOdpovediNikoliOdhadem() {
        for (platforma in listOf(TvPlatforma.WEBOS, TvPlatforma.SAMSUNG)) {
            assertTrue(PodporaPribehu.dotazovat(platforma))
            assertFalse(PodporaPribehu.pouzit(ip, platforma, null))
            assertTrue(PodporaPribehu.pouzit(ip, platforma, ip))
        }
    }

    @Test fun neznamaTvBezPotvrzeniZustaneVeStandardnim() {
        assertTrue(PodporaPribehu.dotazovat(TvPlatforma.NEZNAMA))
        assertFalse(PodporaPribehu.pouzit(ip, TvPlatforma.NEZNAMA, null))
        assertTrue(PodporaPribehu.pouzit(ip, TvPlatforma.NEZNAMA, ip))
    }

    @Test fun potvrzeniStareAdresyNezapneStoryNaNovemCili() {
        assertFalse(PodporaPribehu.pouzit("192.0.2.20", TvPlatforma.WEBOS, ip))
        assertFalse(PodporaPribehu.pouzit("192.0.2.20", TvPlatforma.SAMSUNG, ip))
    }
}
