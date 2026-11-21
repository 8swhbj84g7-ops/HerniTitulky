package cz.titulky.tv.stick

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.metallic.chiaki.lib.ControllerState
import kotlin.math.abs
import kotlin.math.roundToInt

internal class StickPad(private val layout: Int) {
    var deviceId = -1
        private set
    var name = "čeká na vstup"
        private set
    val state = ControllerState()
    private var keys = 0u
    private var hat = 0u
    private var leftDigital = false
    private var rightDigital = false
    private var leftTrigger = 0f
    private var rightTrigger = 0f

    private fun accepts(d: InputDevice?): Boolean {
        if (d == null || (!d.supportsSource(InputDevice.SOURCE_GAMEPAD) &&
                    !d.supportsSource(InputDevice.SOURCE_JOYSTICK))) return false
        if (deviceId != -1 && deviceId != d.id) return false
        deviceId = d.id
        name = d.name
        return true
    }
    fun key(e: KeyEvent): Boolean {
        if (!accepts(e.device)) return false
        if (e.action != KeyEvent.ACTION_DOWN && e.action != KeyEvent.ACTION_UP) return false
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> leftDigital = down
            KeyEvent.KEYCODE_BUTTON_R2 -> rightDigital = down
            else -> {
                val flag = when (e.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_A -> ControllerState.BUTTON_CROSS
                    KeyEvent.KEYCODE_BUTTON_B -> ControllerState.BUTTON_MOON
                    KeyEvent.KEYCODE_BUTTON_X -> ControllerState.BUTTON_BOX
                    KeyEvent.KEYCODE_BUTTON_Y -> ControllerState.BUTTON_PYRAMID
                    KeyEvent.KEYCODE_BUTTON_L1 -> ControllerState.BUTTON_L1
                    KeyEvent.KEYCODE_BUTTON_R1 -> ControllerState.BUTTON_R1
                    KeyEvent.KEYCODE_BUTTON_THUMBL -> ControllerState.BUTTON_L3
                    KeyEvent.KEYCODE_BUTTON_THUMBR -> ControllerState.BUTTON_R3
                    KeyEvent.KEYCODE_BUTTON_START -> ControllerState.BUTTON_OPTIONS
                    KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK -> ControllerState.BUTTON_SHARE
                    KeyEvent.KEYCODE_BUTTON_MODE -> ControllerState.BUTTON_PS
                    KeyEvent.KEYCODE_BUTTON_1 -> ControllerState.BUTTON_TOUCHPAD
                    KeyEvent.KEYCODE_DPAD_LEFT -> ControllerState.BUTTON_DPAD_LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> ControllerState.BUTTON_DPAD_RIGHT
                    KeyEvent.KEYCODE_DPAD_UP -> ControllerState.BUTTON_DPAD_UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> ControllerState.BUTTON_DPAD_DOWN
                    else -> return false
                }
                keys = if (down) keys or flag else keys and flag.inv()
            }
        }
        merge()
        return true
    }
    fun move(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_MOVE || !e.isFromSource(InputDevice.SOURCE_JOYSTICK) || !accepts(e.device)) return false
        fun axis(id: Int) = e.getAxisValue(id)
        fun stick(id: Int): Short {
            val flat = e.device?.getMotionRange(id, e.source)?.flat ?: 0f
            return signedAxis(axis(id), maxOf(0.06f, flat))
        }
        val useZ = layout == 1 || (layout == 0 && e.device?.getMotionRange(MotionEvent.AXIS_Z, e.source) != null)
        state.leftX = stick(MotionEvent.AXIS_X)
        state.leftY = stick(MotionEvent.AXIS_Y)
        state.rightX = stick(if (useZ) MotionEvent.AXIS_Z else MotionEvent.AXIS_RX)
        state.rightY = stick(if (useZ) MotionEvent.AXIS_RZ else MotionEvent.AXIS_RY)
        leftTrigger = maxOf(axis(MotionEvent.AXIS_LTRIGGER), axis(MotionEvent.AXIS_BRAKE))
        rightTrigger = maxOf(axis(MotionEvent.AXIS_RTRIGGER), axis(MotionEvent.AXIS_GAS))
        hat = 0u
        val x = axis(MotionEvent.AXIS_HAT_X)
        val y = axis(MotionEvent.AXIS_HAT_Y)
        if (x < -0.5f) hat = hat or ControllerState.BUTTON_DPAD_LEFT
        if (x > 0.5f) hat = hat or ControllerState.BUTTON_DPAD_RIGHT
        if (y < -0.5f) hat = hat or ControllerState.BUTTON_DPAD_UP
        if (y > 0.5f) hat = hat or ControllerState.BUTTON_DPAD_DOWN
        merge()
        return true
    }
    private fun merge() {
        state.buttons = keys or hat
        state.l2State = trigger(leftTrigger, leftDigital)
        state.r2State = trigger(rightTrigger, rightDigital)
    }
    companion object {
        internal fun signedAxis(value: Float, flat: Float): Short =
            if (abs(value) <= flat) 0 else (value.coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
        internal fun trigger(value: Float, pressed: Boolean): UByte =
            (if (value > 0f) (value.coerceAtMost(1f) * 255).roundToInt() else if (pressed) 255 else 0).toUByte()
    }
}
