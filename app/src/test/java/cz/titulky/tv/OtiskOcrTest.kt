package cz.titulky.tv

import org.junit.Assert.*
import org.junit.Test

class OtiskOcrTest {
    @Test fun stejnePixelyAStejnaVelikostDajiStejnyOtisk() {
        val pixely = IntArray(1200) { it }
        assertEquals(OtiskOcr.barvy(pixely, 40, 30), OtiskOcr.barvy(pixely.copyOf(), 40, 30))
    }

    @Test fun zmenaMimoRychlyVzorekIPosledniPixelSePozna() {
        val puvodni = IntArray(1200)
        for (index in listOf(1, 1023, 1024, 1199)) {
            val zmena = puvodni.copyOf().also { it[index] = 1 }
            assertNotEquals(OtiskOcr.barvy(puvodni, 40, 30), OtiskOcr.barvy(zmena, 40, 30))
        }
    }

    @Test fun zmenaRozmeruNeniStejnyObraz() {
        val pixely = IntArray(1200)
        assertNotEquals(OtiskOcr.barvy(pixely, 40, 30), OtiskOcr.barvy(pixely, 30, 40))
        assertNotEquals(OtiskOcr.sedy(ByteArray(1200), 40, 30), OtiskOcr.sedy(ByteArray(1200), 30, 40))
    }

    @Test fun sedyOtiskCteKazdyPixelNejenVzorek() {
        val pixely = ByteArray(1200)
        assertEquals(OtiskOcr.sedy(pixely, 40, 30), OtiskOcr.sedy(pixely.copyOf(), 40, 30))
        val zmena = pixely.copyOf().also { it[1199] = 1 }
        assertNotEquals(OtiskOcr.sedy(pixely, 40, 30), OtiskOcr.sedy(zmena, 40, 30))
    }
}
