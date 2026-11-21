package cz.titulky.tv

import com.metallic.chiaki.lib.ChiakiLog
import com.metallic.chiaki.lib.CreateError
import com.metallic.chiaki.lib.Regist
import com.metallic.chiaki.lib.RegistEvent
import com.metallic.chiaki.lib.RegistEventCanceled
import com.metallic.chiaki.lib.RegistEventFailed
import com.metallic.chiaki.lib.RegistEventSuccess
import com.metallic.chiaki.lib.RegistInfo
import com.metallic.chiaki.lib.Target

internal object Ps5Registration {
    fun sparuj(ip: String, pin: Int, accountId: ByteArray, log: (String) -> Unit,
                        uloz: (com.metallic.chiaki.lib.RegistHost) -> Unit,
                        hotovo: (Boolean, String) -> Unit) {
        val info = RegistInfo(
            target = Target.PS5_1,
            host = ip,
            broadcast = false,
            psnOnlineId = null,
            psnAccountId = accountId,
            pin = pin
        )
        val chiakiLog = ChiakiLog(ChiakiLog.Level.ALL.value and ChiakiLog.Level.VERBOSE.value.inv()) { _, text -> log(text) }
        val created = java.util.concurrent.CompletableFuture<Regist>()
        try {
            val regist = Regist(info, chiakiLog) { udalost: RegistEvent ->
                when (udalost) {
                    is RegistEventSuccess -> {
                        runCatching { uloz(udalost.host) }.fold(
                            { hotovo(true, "Spárováno s ${udalost.host.serverNickname} ✔") },
                            { hotovo(false, "Párovací údaje se nepodařilo uložit.") }
                        )
                    }
                    is RegistEventFailed -> hotovo(false, "Párování selhalo ✘ (špatný PIN, nebo PIN vypršel?)")
                    is RegistEventCanceled -> hotovo(false, "Párování zrušeno.")
                }
                // uvolnění nativního objektu mimo jeho vlastní callback
                created.thenAcceptAsync { it.dispose() }
            }
            created.complete(regist)
        } catch (e: CreateError) {
            created.completeExceptionally(e)
            hotovo(false, "Párování nešlo spustit ✘ (${e.errorCode})")
        }
    }
}
