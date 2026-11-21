package cz.titulky.tv.stick

import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.Base64
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import cz.titulky.tv.Ps5Registration

class StickPair : StickPage() {
    private var busy = false

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        form()
    }

    private fun field(hintText: String, kind: Int) = EditText(this).apply {
        hint = hintText
        inputType = kind
        isSingleLine = true
        isSaveEnabled = false
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        column.addView(this, dp(380), -2)
    }

    private fun form() {
        page("Připojit PlayStation 5")
        label("Na profilu PS5 zapni Remote Play a zvol Propojit zařízení.")
        val account = field("PSN Account ID (Base64)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val ip = field("Místní IP adresa PS5", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val pin = field("Osmimístný kód z PS5", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        account.filters = arrayOf(InputFilter.LengthFilter(12))
        ip.filters = arrayOf(InputFilter.LengthFilter(15))
        pin.filters = arrayOf(InputFilter.LengthFilter(8))
        button("Spárovat") {
            if (busy) return@button
            val id = account.text.toString().trim()
            val host = ip.text.toString().trim()
            val code = pin.text.toString()
            val key = if (StickPairingInput.accountId(id)) {
                runCatching { Base64.decode(id, Base64.NO_WRAP) }.getOrNull()
            } else null
            if (key?.size != 8 || !StickPairingInput.localAddress(host) || !StickPairingInput.registrationCode(code)) {
                key?.fill(0)
                notice("Zkontroluj Account ID v Base64, místní IP a osmimístný kód.")
                return@button
            }
            val attempt = config.pairingAttempt()
            busy = true
            pin.text.clear()
            notice("Páruji konzoli…")
            try {
                Ps5Registration.sparuj(host, code.toInt(), key, {}, {
                    try { config.save(attempt, host, it.rpRegistKey, it.rpKey) }
                    finally { it.rpRegistKey.fill(0); it.rpKey.fill(0) }
                }) { ok, _ ->
                    runOnUiThread {
                        busy = false
                        if (!isDestroyed && !isFinishing) {
                            notice(if (ok) "PS5 je spárovaná." else "Párování selhalo. Zkontroluj údaje a platnost kódu.")
                            if (ok) finish()
                        }
                    }
                }
            } finally { key.fill(0) }
        }
    }
}
