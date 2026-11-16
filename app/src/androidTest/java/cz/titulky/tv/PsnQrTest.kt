package cz.titulky.tv

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PsnQrTest {
    @Test fun qrKodAndroidTvLzePrecistAZachovaCelouAdresu() {
        val adresa = "http://192.0.2.10:${PrihlaseniTelefonem.PORT}"
        val bitmapa = PrihlaseniTelefonem.qr(adresa, 512)
        try {
            val pixely = IntArray(bitmapa.width * bitmapa.height)
            bitmapa.getPixels(pixely, 0, bitmapa.width, 0, 0, bitmapa.width, bitmapa.height)
            val zdroj = RGBLuminanceSource(bitmapa.width, bitmapa.height, pixely)
            val vysledek = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(zdroj)))
            assertEquals(adresa, vysledek.text)
        } finally { bitmapa.recycle() }
    }
}
