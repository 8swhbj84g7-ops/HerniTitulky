package cz.titulky.tv.stick

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickPairingInputTest {
    @Test fun acceptsOnlyPrivateLiteralIpv4() {
        for (ip in listOf("10.0.0.8", "172.16.4.5", "172.31.4.5", "192.168.1.99")) {
            assertTrue(ip, StickPairingInput.localAddress(ip))
        }
        for (ip in listOf("172.15.4.5", "172.32.4.5", "8.8.8.8", "127.0.0.1", "localhost",
            "192.168.1.256", "192.168.1.2:80", "192.168.001.2", "192.168.1", "192.168.1.2.",
            "192.168.1.-1", "::ffff:192.168.1.4", "192.168.1.2/path")) {
            assertFalse(ip, StickPairingInput.localAddress(ip))
        }
    }

    @Test fun accountIdAndPinRequireExactRepresentations() {
        assertTrue(StickPairingInput.accountId("AQIDBAUGBwg="))
        assertFalse(StickPairingInput.accountId("AQIDBAUGBwh="))
        assertFalse(StickPairingInput.accountId("AQIDBAUGBwg"))
        assertFalse(StickPairingInput.accountId("AQIDBAUGBwg=\n"))
        assertFalse(StickPairingInput.accountId("AQIDBAUGBwc_"))
        assertTrue(StickPairingInput.registrationCode("01234567"))
        assertFalse(StickPairingInput.registrationCode("1234567"))
        assertFalse(StickPairingInput.registrationCode("123456789"))
        assertFalse(StickPairingInput.registrationCode("１２３４５６７８"))
    }
}
