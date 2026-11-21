package cz.titulky.tv.stick

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.metallic.chiaki.lib.Codec
import com.metallic.chiaki.lib.ConnectInfo
import com.metallic.chiaki.lib.ConnectVideoProfile
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal object StickRuntime {
    // Set on entry to each activity in the dedicated :stick process, before using Chiaki.
    var gameProcess = false
        private set

    fun prepare() {
        if (gameProcess) return
        if (android.os.Build.VERSION.SDK_INT >= 28 && android.app.Application.getProcessName().endsWith(":stick"))
            android.webkit.WebView.setDataDirectorySuffix("stick")
        gameProcess = true
    }
}

internal enum class Quality(val label: String, val width: Int, val height: Int, val fps: Int, val bitrate: Int) {
    HD("720p · 30 FPS", 1280, 720, 30, 6000),
    HD60("720p · 60 FPS", 1280, 720, 60, 10000),
    FULL("1080p · 30 FPS", 1920, 1080, 30, 12000);
    fun profile() = ConnectVideoProfile(width, height, fps, bitrate, Codec.CODEC_H264)
}

internal class StickConfig(context: Context) {
    val prefs = context.getSharedPreferences("stick", Context.MODE_PRIVATE)
    val quality get() = Quality.values().getOrElse(prefs.getInt("quality", 0)) { Quality.HD }
    val paired get() = prefs.contains("console")
    val language get() = prefs.getString("lang", "cs") ?: "cs"
    val story get() = prefs.getBoolean("story", false)

    private fun key(): SecretKey {
        val alias = "stick-pairing-v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    fun save(ip: String, registration: ByteArray, session: ByteArray) {
        val data = JSONObject().put("ip", ip).put("reg", encode(registration)).put("key", encode(session))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = encode(cipher.iv) + "." + encode(cipher.doFinal(data.toString().toByteArray()))
        check(prefs.edit().putString("console", encrypted).commit())
    }

    fun connection(): ConnectInfo {
        val parts = requireNotNull(prefs.getString("console", null)).split('.')
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(parts[0])))
        val data = JSONObject(String(cipher.doFinal(decode(parts[1])), Charsets.UTF_8))
        return ConnectInfo(true, data.getString("ip"), decode(data.getString("reg")),
            decode(data.getString("key")), quality.profile())
    }
}
