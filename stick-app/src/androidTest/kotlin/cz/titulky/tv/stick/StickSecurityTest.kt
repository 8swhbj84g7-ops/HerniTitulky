package cz.titulky.tv.stick

import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class StickSecurityTest {
    @Test fun forgetRemovesKeyAndRejectsPreviousCiphertextAndLatePairing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = StickConfig(context)
        val ip = "192.168.1.90"
        val registration = ByteArray(16) { 3 }
        val session = ByteArray(16) { 4 }
        try {
            val interrupted = config.pairingAttempt()
            val oldAttempt = config.pairingAttempt()
            assertThrows(IllegalStateException::class.java) { config.save(interrupted, ip, registration, session) }
            config.save(oldAttempt, ip, registration, session)
            val record = requireNotNull(config.prefs.getString("console", null))
            assertFalse(record.contains(ip))
            assertArrayEquals(registration, config.connection().registKey)

            config.forget()
            assertFalse(StickConfig(context).paired)
            assertThrows(IllegalStateException::class.java) { config.save(oldAttempt, ip, registration, session) }
            assertTrue(config.prefs.edit().putString("console", record).commit())
            assertThrows(IllegalStateException::class.java) { config.connection() }
            config.save(config.pairingAttempt(), ip, registration, session)
            assertArrayEquals(session, config.connection().morning)

            // Recreating the alias during a new pairing does not recover the previous key.
            assertTrue(config.prefs.edit().putString("console", record).commit())
            assertThrows(java.security.GeneralSecurityException::class.java) { config.connection() }
        } finally { config.forget() }
    }

    @Test fun onlyLauncherIsExportedAndOldComponentsAreAbsent() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_SERVICES or PackageManager.GET_PROVIDERS
        val pkg = app.packageManager.getPackageInfo(app.packageName, flags)
        val components = pkg.activities.orEmpty().toList() + pkg.receivers.orEmpty() +
            pkg.services.orEmpty() + pkg.providers.orEmpty()
        assertEquals(listOf(StickHome::class.java.name), components.filter { it.exported }.map { it.name })
        assertEquals(setOf(StickHome::class.java.name, StickPair::class.java.name, StickGame::class.java.name),
            components.map { it.name }.filter { it.startsWith("cz.titulky.tv.") }.toSet())
        assertFalse(StickPlatform.onlineTranslation)
    }
}
