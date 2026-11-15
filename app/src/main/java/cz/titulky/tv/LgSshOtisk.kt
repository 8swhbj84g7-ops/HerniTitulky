package cz.titulky.tv

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.security.MessageDigest
import java.util.Base64

internal class LgSshOtisk(private val znamy: String?) : HostKeyRepository {
    var novy: String? = null
        private set
    var zmeneny = false
        private set
    override fun check(host: String, key: ByteArray): Int {
        val hash = "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
        novy = hash
        if (znamy != null && hash != znamy) { zmeneny = true; return HostKeyRepository.CHANGED }
        return HostKeyRepository.OK
    }
    override fun add(hostkey: HostKey?, ui: UserInfo?) {}
    override fun remove(host: String?, type: String?) {}
    override fun remove(host: String?, type: String?, key: ByteArray?) {}
    override fun getKnownHostsRepositoryID() = "PlayCaption LG"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}
