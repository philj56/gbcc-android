package com.philj56.gbcc

import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Button
import androidx.preference.PreferenceManager
import com.philj56.gbcc.databinding.ActivityRemapControllerBinding
import com.philj56.gbcc.remap.RemapButtonDialogFragment
import kotlin.math.abs

class RemapActivity : BaseActivity() {

    data class ButtonInfo(val keyCode: Int, val mapKey: String, val button: Button, val drawable: Drawable, val analogue: Boolean)

    private lateinit var prefs: SharedPreferences
    private lateinit var layers: LayerDrawable
    private lateinit var buttonInfoArray: Array<ButtonInfo>
    private lateinit var binding: ActivityRemapControllerBinding

    private fun Boolean.toAlpha() = if (this) 255 else 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRemapControllerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager.getDefaultSharedPreferences(baseContext)
        layers = binding.controllerLayers.drawable as LayerDrawable

        // Set controller outline to follow night mode theme
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
        layers.findDrawableByLayerId(R.id.controllerOutline).setTint(typedValue.data)

        buttonInfoArray = arrayOf(
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_A, "button_map_a", binding.buttonRemapA, layers.findDrawableByLayerId(R.id.buttonRemapABackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_B, "button_map_b", binding.buttonRemapB, layers.findDrawableByLayerId(R.id.buttonRemapBBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_X, "button_map_x", binding.buttonRemapX, layers.findDrawableByLayerId(R.id.buttonRemapXBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_Y, "button_map_y", binding.buttonRemapY, layers.findDrawableByLayerId(R.id.buttonRemapYBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_DPAD_UP, "button_map_up", binding.buttonRemapDpadUp, layers.findDrawableByLayerId(R.id.buttonRemapDpadUpBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_DPAD_DOWN, "button_map_down", binding.buttonRemapDpadDown, layers.findDrawableByLayerId(R.id.buttonRemapDpadDownBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_DPAD_LEFT, "button_map_left", binding.buttonRemapDpadLeft, layers.findDrawableByLayerId(R.id.buttonRemapDpadLeftBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_DPAD_RIGHT, "button_map_right", binding.buttonRemapDpadRight, layers.findDrawableByLayerId(R.id.buttonRemapDpadRightBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_START, "button_map_start", binding.buttonRemapStart, layers.findDrawableByLayerId(R.id.buttonRemapStartBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_SELECT, "button_map_select", binding.buttonRemapSelect, layers.findDrawableByLayerId(R.id.buttonRemapSelectBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_L1, "button_map_l1", binding.buttonRemapLeftShoulder, layers.findDrawableByLayerId(R.id.buttonRemapLeftShoulderBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_L2, "button_map_l2", binding.buttonRemapLeftTrigger, layers.findDrawableByLayerId(R.id.buttonRemapLeftTriggerBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_R1, "button_map_r1", binding.buttonRemapRightShoulder, layers.findDrawableByLayerId(R.id.buttonRemapRightShoulderBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_R2, "button_map_r2", binding.buttonRemapRightTrigger, layers.findDrawableByLayerId(R.id.buttonRemapRightTriggerBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_THUMBL, "button_map_thumbl", binding.buttonRemapLeftStick, layers.findDrawableByLayerId(R.id.buttonRemapLeftStickBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_BUTTON_THUMBR, "button_map_thumbr", binding.buttonRemapRightStick, layers.findDrawableByLayerId(R.id.buttonRemapRightStickBackground), false),
            ButtonInfo(KeyEvent.KEYCODE_SHIFT_LEFT, "button_map_analogue_left", binding.buttonRemapLeftStickMove, layers.findDrawableByLayerId(R.id.buttonRemapLeftStickMoveBackground), true),
            ButtonInfo(KeyEvent.KEYCODE_SHIFT_RIGHT, "button_map_analogue_right", binding.buttonRemapRightStickMove, layers.findDrawableByLayerId(R.id.buttonRemapRightStickMoveBackground), true),
        )

        val buttonNames = resources.getStringArray(R.array.button_map_names_array)
        val buttonValues = resources.getStringArray(R.array.button_map_values_array)
        val analogueNames = resources.getStringArray(R.array.button_map_analogue_names_array)
        val analogueValues = resources.getStringArray(R.array.button_map_analogue_values_array)
        buttonInfoArray.forEach { buttonInfo ->
            val key = prefs.getString(buttonInfo.mapKey, null) ?: "unmapped"

            buttonInfo.drawable.alpha = 0
            if (buttonInfo.analogue) {
                buttonInfo.button.text = analogueNames[analogueValues.indexOf(key)]
            } else {
                buttonInfo.button.text = buttonNames[buttonValues.indexOf(key)]
            }
            buttonInfo.button.setOnClickListener {
                RemapButtonDialogFragment(
                    buttonInfo.button,
                    buttonInfo.mapKey,
                    buttonInfo.analogue
                ).show(supportFragmentManager, "")
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
            || event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        ) {
            if (event.repeatCount == 0) {
                if (gamepadPress(keyCode, true)) {
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
            || event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD
        ) {
            if (event.repeatCount == 0) {
                if (gamepadPress(keyCode, false)) {
                    return true
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        fun getCenteredAxis(event: MotionEvent, axis: Int): Float {
            event.device.getMotionRange(axis, event.source)?.apply {
                val value = event.getAxisValue(axis)
                if (abs(value) > 0.5f) {
                    return value
                }
            }
            return 0f
        }

        var handled = false

        if (event.source and InputDevice.SOURCE_DPAD != InputDevice.SOURCE_DPAD) {
            val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            val up = (y == -1.0f)
            val down = (y == 1.0f)
            val left = (x == -1.0f)
            val right = (x == 1.0f)

            gamepadPress(KeyEvent.KEYCODE_DPAD_UP, up)
            gamepadPress(KeyEvent.KEYCODE_DPAD_DOWN, down)
            gamepadPress(KeyEvent.KEYCODE_DPAD_LEFT, left)
            gamepadPress(KeyEvent.KEYCODE_DPAD_RIGHT, right)

            if (up || down || left || right) {
                handled = true
            }
        }
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            && event.action == MotionEvent.ACTION_MOVE
        ) {
            val x = getCenteredAxis(event, MotionEvent.AXIS_X)
            val y = getCenteredAxis(event, MotionEvent.AXIS_Y)
            val ls = x != 0.0f || y != 0.0f
            gamepadPress(KeyEvent.KEYCODE_SHIFT_LEFT, ls)

            val rx = getCenteredAxis(event, MotionEvent.AXIS_Z)
            val ry = getCenteredAxis(event, MotionEvent.AXIS_RZ)
            val rs = rx != 0.0f || ry != 0.0f
            gamepadPress(KeyEvent.KEYCODE_SHIFT_RIGHT, rs)

            val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > 0.0f
            val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > 0.0f
            gamepadPress(KeyEvent.KEYCODE_BUTTON_L2, lt)
            gamepadPress(KeyEvent.KEYCODE_BUTTON_R2, rt)

            handled = true
        }
        if (handled) {
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun highlight(button: Button, pressed: Boolean) {
        val color = if (pressed) {
            val typedValue = TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryInverse, typedValue, true)
            typedValue.data
        } else {
            val typedValue = TypedValue()
            val theme = button.context.theme
            theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
            typedValue.data
        }
        button.setBackgroundColor(color)
    }

    private fun gamepadPress(keyCode: Int, pressed: Boolean): Boolean {
        val button = buttonInfoArray.firstOrNull { keyCode == it.keyCode }
        return if (button != null) {
            highlight(button.button, pressed)
            button.drawable.alpha = pressed.toAlpha()
            true
        } else {
            false
        }
    }
}

