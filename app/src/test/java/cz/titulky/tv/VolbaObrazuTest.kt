package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class VolbaObrazuTest {
    @Test fun prvniSpusteniZacinaYuvAPokusONahraduSeNeuklada() {
        val v = VolbaObrazu(false, false, false)
        assertFalse(v.gl)
        assertTrue(v.prepnoutNaGl())
        assertTrue(v.gl)
        assertFalse(v.ulozitGl)
    }

    @Test fun driveFungujiciYuvSePriVypadkuNemenizGl() {
        val v = VolbaObrazu(false, false, false)
        assertTrue(v.snimek(false))
        assertTrue(v.yuvOvereno)
        assertFalse(v.prepnoutNaGl())
        assertFalse(v.gl)
        assertFalse(v.ulozitGl)
    }

    @Test fun uspechYuvPlatiIPoRestartu() {
        val v = VolbaObrazu(true, true, true)
        assertFalse(v.gl)
        assertFalse(v.prepnoutNaGl())
    }

    @Test fun stareAutomatickeGlBezDukazuSnimekZnovuOveri() {
        val v = VolbaObrazu(true, false, false)
        assertTrue(v.gl)
        assertFalse(v.ulozitGl)
        assertTrue(v.snimek(true))
        assertTrue(v.ulozitGl)
        assertTrue(VolbaObrazu(v.ulozitGl, v.glOvereno, v.yuvOvereno).gl)
    }

    @Test fun glBezSnimekSePriStavajicimWatchdoguVratiNaYuv() {
        val v = VolbaObrazu(true, false, false)
        assertTrue(v.vratitNeovereneGl())
        assertFalse(v.gl)
        assertFalse(v.ulozitGl)
    }

    @Test fun overeneGlSePriSitovemVypadkuNezahazuje() {
        val v = VolbaObrazu(true, true, false)
        assertTrue(v.gl)
        assertFalse(v.vratitNeovereneGl())
        assertTrue(v.ulozitGl)
    }

    @Test fun starySnimekJineCestyNeniDukazFunkcnostiNove() {
        val v = VolbaObrazu(false, false, false)
        v.prepnoutNaGl()
        assertFalse(v.snimek(false))
        assertFalse(v.glOvereno)
        assertFalse(v.yuvOvereno)
    }

    @Test fun dalsiSnimekNevyzadujeDalsiZapisNastaveni() {
        val v = VolbaObrazu(false, false, false)
        assertTrue(v.snimek(false))
        repeat(100) { assertFalse(v.snimek(false)) }
    }
}
