/*
 * Copyright (C) 2019-2020 Philip Jones
 *
 * Licensed under the MIT License.
 * See either the LICENSE file, or:
 *
 * https://opensource.org/licenses/MIT
 *
 */

package com.philj56.gbcc

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.*
import android.net.Uri
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.*
import android.util.AttributeSet
import android.util.Log
import android.util.Property
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.databinding.ActivityGlBinding
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

private const val autoSaveState: Int = 10
private const val REQUEST_CODE_PERMISSIONS = 10
private const val PRINTER_TRANSITION_MILLIS = 800L
private const val PRINTER_CLEAR_MILLIS = 800L
private const val PRINTER_SCROLL_MILLIS = 300L
private const val PRINTER_UPDATE_SAMPLES = 17000

private const val BUTTON_CODE_A = 0
private const val BUTTON_CODE_B = 1
private const val BUTTON_CODE_START = 2
private const val BUTTON_CODE_SELECT = 3
private const val BUTTON_CODE_UP = 4
private const val BUTTON_CODE_DOWN = 5
private const val BUTTON_CODE_LEFT = 6
private const val BUTTON_CODE_RIGHT = 7

private val KEYCODE_TO_STRING_MAP = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to "button_map_a",
        KeyEvent.KEYCODE_BUTTON_B to "button_map_b",
        KeyEvent.KEYCODE_BUTTON_X to "button_map_x",
        KeyEvent.KEYCODE_BUTTON_Y to "button_map_y",
        KeyEvent.KEYCODE_DPAD_UP to "button_map_up",
        KeyEvent.KEYCODE_DPAD_DOWN to "button_map_down",
        KeyEvent.KEYCODE_DPAD_LEFT to "button_map_left",
        KeyEvent.KEYCODE_DPAD_RIGHT to "button_map_right",
        KeyEvent.KEYCODE_BUTTON_START to "button_map_start",
        KeyEvent.KEYCODE_BUTTON_SELECT to "button_map_select",
        KeyEvent.KEYCODE_BUTTON_L1 to "button_map_l1",
        KeyEvent.KEYCODE_BUTTON_L2 to "button_map_l2",
        KeyEvent.KEYCODE_BUTTON_R1 to "button_map_r1",
        KeyEvent.KEYCODE_BUTTON_R2 to "button_map_r2",
        KeyEvent.KEYCODE_BUTTON_THUMBL to "button_map_thumbl",
        KeyEvent.KEYCODE_BUTTON_THUMBR to "button_map_thumbr",
)

private val ACTION_TO_KEY_MAP = mapOf(
    "a" to 0,
    "b" to 1,
    "start" to 2,
    "select" to 3,
    "up" to 4,
    "down" to 5,
    "left" to 6,
    "right" to 7,
    "turbo" to 8,
    "pause" to 9,
    "printer" to 10,
    "fps" to 11,
    "frame_blending" to 12,
    "vsync" to 13,
    "link_cable" to 14,
    "autosave" to 15,
    "menu" to 16,
    "interlace" to 17,
    "shader" to 18,
    "back" to -1,
    "unmapped" to -1
)

class GLActivity : BaseActivity(), SensorEventListener, LifecycleOwner {

    private data class ButtonInfo(val view: View, val id: Int) {
        var isNormalPressed: Boolean = false
        var isMotionPressed: Boolean = false
        val isPressed: Boolean get() = isNormalPressed or isMotionPressed
    }

    private enum class Screen {
        GAMEBOY, PRINTER
    }

    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var gestureDetector : GestureDetector
    private lateinit var sensorManager : SensorManager
    private var accelerometer : Sensor? = null
    private lateinit var vibrator : Vibrator
    private lateinit var checkEmulatorState : Runnable
    private lateinit var filename : String
    private lateinit var printerAudio : AudioTrack
    private lateinit var printerPaperTearSound : SoundPool
    private var printerAudioLength = 0
    private var resume = false
    private var resumePrinting = false
    private var reboot = false
    private var loadedSuccessfully = false
    private var cameraPermissionRefused = false
    private var animateButtons = true
    private var dpadState = 0
    private var lastHatUp = false
    private var lastHatDown = false
    private var lastHatLeft = false
    private var lastHatRight = false
    private var lastLeftTrigger = false
    private var lastRightTrigger = false
    private var disableAccelerometer = false
    private lateinit var saveDir : String
    private var tempOptions : ByteArray? = null
    private var printerByteArray : ByteArray = ByteArray(0)
    private val additionalMappings = mutableMapOf<Int, String>()
    private val transitionToPrinter = AnimatorSet()
    private val transitionToGameboy = AnimatorSet()
    private lateinit var printerScrollAnimation : ObjectAnimator
    private var currentScreen = Screen.GAMEBOY

    private lateinit var binding: ActivityGlBinding

    private external fun chdir(dirName: String)
    private external fun checkRom(file: String): Boolean
    private external fun loadRom(
        file: String,
        sampleRate: Int,
        samplesPerBuffer: Int,
        saveDir: String,
        configFile: String?,
        cheatFile: String?,
        prefs: SharedPreferences
    ): Boolean
    private external fun getErrorMessage(): String
    private external fun quit()
    private external fun press(button: Int, pressed: Boolean)
    private external fun isPressed(button: Int) : Boolean
    private external fun toggleTurbo(): Boolean
    private external fun toggleMenu()
    private external fun saveState(state: Int)
    private external fun loadState(state: Int)
    private external fun getOptions(): ByteArray
    private external fun setOptions(options: ByteArray)
    private external fun checkVibrationFun(): Boolean
    private external fun checkErrorFun(): Boolean
    private external fun checkTurboFun(): Boolean
    private external fun flushLogs()
    private external fun updateAccelerometer(x: Float, y: Float)
    private external fun updateCamera(
        array: ByteArray,
        width: Int,
        height: Int,
        rotation: Int,
        rowStride: Int
    )
    private external fun initialiseTileset(width: Int, height: Int, data: ByteArray)
    private external fun destroyTileset()
    private external fun setCameraImage(data: ByteArray)
    private external fun hasRumble(): Boolean
    private external fun hasAccelerometer(): Boolean
    private external fun isCamera(): Boolean
    private external fun printerConnected(): Boolean
    private external fun shouldStartPrinting(): Boolean
    private external fun isPrinting(): Boolean
    private external fun updatePrinter(): Boolean
    private external fun getPrinterStrip(): ByteArray
    private external fun resetPrinter()


    init {
        checkEmulatorState = Runnable {
            if (printerConnected()) {
                binding.printerTransitionButton.visibility = View.VISIBLE
            } else {
                binding.printerTransitionButton.visibility = View.GONE
            }
            if (shouldStartPrinting() || (isPrinting() && (printerAudio.playState != AudioTrack.PLAYSTATE_PLAYING))) {
                if (printerConnected()) {
                    print()
                } else {
                    // Something went wrong, reset the printer to be safe
                    resetPrinter()
                }
            }
            if (checkVibrationFun()) {
                rumblePakVibrate(prefs.getInt("rumble_strength", 255))
            }
            val turbo = checkTurboFun()
            if (binding.turboToggle.isChecked != turbo) {
                binding.turboToggle.isChecked = turbo
            }
            if (checkErrorFun()) {
                flushLogs()
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.invalid_opcode_title)
                    .setMessage(R.string.invalid_opcode_description)
                    .setPositiveButton(R.string.button_send_feedback) { _, _ ->
                        val intent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(
                                Intent.EXTRA_EMAIL,
                                arrayOf("gbcc.emu+invalid_opcode@gmail.com")
                            )
                            putExtra(Intent.EXTRA_SUBJECT, "Bug report")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Crash log:\n----------\n" + filesDir.resolve("gbcc.log").readText()
                            )
                        }
                        try {
                            startActivity(intent)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                this,
                                R.string.message_no_email_app,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        finish()
                    }.setNegativeButton(R.string.button_quit) { _, _ ->
                        finish()
                    }.setCancelable(false)
                    .create()
                    .show()
            } else {
                handler.postDelayed(checkEmulatorState, 10)
            }
        }
    }

    private fun hapticVibrate(view: View, pressed: Boolean) {
        if (pressed && !prefs.getBoolean("haptic_key_press", true)) {
            return
        }
        if (!pressed && !prefs.getBoolean("haptic_key_release", false)) {
            return
        }
        val effect = if (pressed) {
            HapticFeedbackConstants.VIRTUAL_KEY
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                HapticFeedbackConstants.VIRTUAL_KEY_RELEASE
            } else {
                HapticFeedbackConstants.VIRTUAL_KEY
            }
        }
        view.performHapticFeedback(effect)
    }

    private fun rumblePakVibrate(strength: Int) {
        if (!vibrator.hasVibrator()) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    10,
                    strength
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setButtonIds(buttons: Array<ButtonInfo>) {
        fun inBounds(view: View, x: Int, y: Int): Boolean {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            val rect = Rect(
                location[0],
                location[1],
                location[0] + ceil(view.width * view.scaleX).toInt(),
                location[1] + ceil(view.height * view.scaleY).toInt()
            )
            return rect.contains(x, y)
        }
        buttons.forEach { button ->
            button.view.setOnTouchListener(View.OnTouchListener { touchView, motionEvent ->
                if (touchView != button.view) {
                    return@OnTouchListener false
                }
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!button.isPressed) {
                            press(button.id, true)
                        }
                        button.isNormalPressed = true
                        button.view.isPressed = true && animateButtons
                        hapticVibrate(button.view, true)
                    }
                    MotionEvent.ACTION_UP -> {
                        buttons.forEach { button2 ->
                            val lastPressed = button2.isPressed
                            if (button2 == button) {
                                button2.isNormalPressed = false
                            } else {
                                button2.isMotionPressed = false
                            }
                            if (lastPressed && !button2.isPressed) {
                                press(button2.id, false)
                                button2.view.isPressed = false
                                hapticVibrate(button2.view, false)
                            }
                        }
                    }
                    MotionEvent.ACTION_MOVE -> run {
                        val x = motionEvent.rawX.toInt()
                        val y = motionEvent.rawY.toInt()
                        buttons.forEach { button2 ->
                            if (button2 != button) {
                                val lastPressed = button2.isPressed
                                button2.isMotionPressed = inBounds(button2.view, x, y)
                                val pressed = button2.isPressed
                                if (pressed != lastPressed) {
                                    hapticVibrate(button2.view, pressed)
                                    press(button2.id, pressed)
                                    button2.view.isPressed = pressed && animateButtons
                                }
                            }
                        }
                    }
                }
                return@OnTouchListener true
            })
        }
    }

    private fun updateLayout(gbc: Boolean) {
        val bgColor = when (gbc) {
            true -> when (prefs.getString("color", "Teal")) {
                "Berry" -> R.color.gbcBerry
                "Dandelion" -> R.color.gbcDandelion
                "Grape" -> R.color.gbcGrape
                "Kiwi" -> R.color.gbcKiwi
                "Teal" -> R.color.gbcTeal
                else -> R.color.gbcTeal
            }
            false -> when (prefs.getString("dmg_color", "Light")) {
                "Light" -> R.color.dmgLightBackground
                "Dark" -> R.color.dmgDarkBackground
                else -> R.color.dmgLightBackground
            }
        }
        window.setBackgroundDrawableResource(bgColor)

        binding.screen.setOnClickListener { toggleMenu() }
        binding.turboToggle.setOnClickListener { toggleTurbo() }

        if (!gbc) {
            val screenBorderColor: Int
            val theme = prefs.getString("dmg_color", "Light")
            if (theme == "Dark") {
                screenBorderColor = ContextCompat.getColor(this, R.color.dmgDarkScreenBorder)
                binding.dpad.dpadBackground.setColorFilter(
                    ContextCompat.getColor(this, R.color.dmgDarkDpad),
                    PorterDuff.Mode.SRC_IN
                )

                binding.buttonA.setImageResource(R.drawable.ic_button_ab_dmg_dark_selector)
                binding.buttonB.setImageResource(R.drawable.ic_button_ab_dmg_dark_selector)

                binding.buttonStart.setImageResource(R.drawable.ic_button_startselect_dmg_dark_selector)
                binding.buttonSelect.setImageResource(R.drawable.ic_button_startselect_dmg_dark_selector)

                binding.turboToggle.thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dmgDarkToggleThumb))
                binding.turboToggle.trackTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.dmgDarkToggleTrack))
            } else {
                screenBorderColor = ContextCompat.getColor(this, R.color.dmgLightScreenBorder)
                binding.buttonA.setImageResource(R.drawable.ic_button_ab_dmg_selector)
                binding.buttonB.setImageResource(R.drawable.ic_button_ab_dmg_selector)

                binding.buttonStart.setImageResource(R.drawable.ic_button_startselect_dmg_selector)
                binding.buttonSelect.setImageResource(R.drawable.ic_button_startselect_dmg_selector)
            }


            binding.buttonStart.rotation = -45f
            binding.buttonSelect.rotation = -45f

            val borders = arrayOf(
                binding.screenBorderTop,
                binding.screenBorderBottom,
                binding.screenBorderLeft,
                binding.screenBorderRight
            )

            borders.forEach {
                it.setColorFilter(
                    screenBorderColor,
                    PorterDuff.Mode.SRC_IN
                )
            }

            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                val px = (resources.displayMetrics.density + 0.5f).toInt()

                binding.bottomLeftCorner.layoutParams.apply {
                    width = 16 * px
                    height = width
                }

                binding.bottomRightCorner.layoutParams.apply {
                    width = 64 * px
                    height = width
                }
            }
        }

        binding.buttonA.scaleX = prefs.getFloat(getString(R.string.a_scale_key), 1f)
        binding.buttonA.scaleY = binding.buttonA.scaleX
        binding.buttonB.scaleX = prefs.getFloat(getString(R.string.b_scale_key), 1f)
        binding.buttonB.scaleY = binding.buttonB.scaleX
        binding.buttonStart.scaleX = prefs.getFloat(getString(R.string.start_scale_key), 1f)
        binding.buttonStart.scaleY = binding.buttonStart.scaleX
        binding.buttonSelect.scaleX = prefs.getFloat(getString(R.string.select_scale_key), 1f)
        binding.buttonSelect.scaleY = binding.buttonSelect.scaleX
        binding.dpad.root.scaleX = prefs.getFloat(getString(R.string.dpad_scale_key), 1f)
        binding.dpad.root.scaleY = binding.dpad.root.scaleX
        binding.turboToggle.scaleX = prefs.getFloat(getString(R.string.turbo_scale_key), 1f)
        binding.turboToggle.scaleY = binding.turboToggle.scaleX

        binding.buttonA.translationX = prefs.getFloat(getString(R.string.a_offset_x_key), 0f)
        binding.buttonA.translationY = prefs.getFloat(getString(R.string.a_offset_y_key), 0f)
        binding.buttonB.translationX = prefs.getFloat(getString(R.string.b_offset_x_key), 0f)
        binding.buttonB.translationY = prefs.getFloat(getString(R.string.b_offset_y_key), 0f)
        binding.buttonStart.translationX = prefs.getFloat(getString(R.string.start_offset_x_key), 0f)
        binding.buttonStart.translationY = prefs.getFloat(getString(R.string.start_offset_y_key), 0f)
        binding.buttonSelect.translationX = prefs.getFloat(getString(R.string.select_offset_x_key), 0f)
        binding.buttonSelect.translationY = prefs.getFloat(getString(R.string.select_offset_y_key), 0f)
        binding.dpad.root.translationX = prefs.getFloat(getString(R.string.dpad_offset_x_key), 0f)
        binding.dpad.root.translationY = prefs.getFloat(getString(R.string.dpad_offset_y_key), 0f)
        binding.turboToggle.translationX = prefs.getFloat(getString(R.string.turbo_offset_x_key), 0f)
        binding.turboToggle.translationY = prefs.getFloat(getString(R.string.turbo_offset_y_key), 0f)

        if (!prefs.getBoolean("show_turbo", false)) {
            binding.turboToggle.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).let {
            // Hide system bars
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback { showBackPrompt() }
        chdir(filesDir.absolutePath)

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        requestedOrientation = prefs.getString("orientation", "-1")?.toInt() ?: -1
        binding = ActivityGlBinding.inflate(layoutInflater)
        setContentView(binding.root)
        hideNavigation()

        val save = filesDir.resolve("saves")
        save.mkdirs()
        saveDir = save.absolutePath
        val bundle = intent.extras
        filename = bundle?.getString("file") ?: ""
        if (filename == "") {
            Log.e("GBCC", "No rom provided.")
            finish()
        }
        if (!checkRom(filename)) {
            Toast.makeText(
                this,
                "Error loading ROM:\n" + getErrorMessage().trim(),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }

        updateLayout(
            when (prefs.getString("skin", "auto")) {
                "dmg" -> false
                "gbc" -> true
                else -> filename.endsWith("gbc")
            }
        )

        gestureDetector = GestureDetector(this, DpadListener())
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        checkFiles()

        if (savedInstanceState != null) {
            resume = resume || savedInstanceState.getBoolean("resume")
            resumePrinting = resumePrinting || savedInstanceState.getBoolean("resumePrinting")
            if (tempOptions == null) {
                tempOptions = savedInstanceState.getByteArray("options")
            }
            if (printerByteArray.isEmpty()) {
                savedInstanceState.getByteArray("printerByteArray")?.let {
                    printerByteArray = it
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getSerializable("currentScreen", Screen.GAMEBOY.declaringClass)
                    ?.let { currentScreen = it }
            } else {
                @Suppress("DEPRECATION")
                (savedInstanceState.getSerializable("currentScreen") as Screen?)
                    ?.let { currentScreen = it }
            }
        }

        animateButtons = prefs.getBoolean("animate_buttons", true)
        setButtonIds(
            arrayOf(
                ButtonInfo(binding.buttonA, BUTTON_CODE_A),
                ButtonInfo(binding.buttonB, BUTTON_CODE_B)
            )
        )
        setButtonIds(
            arrayOf(
                ButtonInfo(binding.buttonStart, BUTTON_CODE_START),
                ButtonInfo(binding.buttonSelect, BUTTON_CODE_SELECT)
            )
        )

        binding.placeholderTouchTarget.setOnTouchListener { v, _ ->
            // This shouldn't be needed, but Android
            // seems to act strangely when the root view is touched
            // and ignores any further touches.
            if (v != binding.placeholderTouchTarget) {
                return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }

        binding.dpad.root.setOnTouchListener(View.OnTouchListener { view, motionEvent ->
            if (view != binding.dpad.root) {
                return@OnTouchListener false
            }
            if (dpadState == 0) {
                if (gestureDetector.onTouchEvent(motionEvent)) {
                    if (prefs.getBoolean("dpad_turbo", false)) {
                        toggleTurboWrapper()
                    }
                    return@OnTouchListener true
                }
            }
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val up = Rect(0, 0, binding.dpad.root.width, binding.dpad.root.height / 3)
                    val down = Rect(0, 2 * binding.dpad.root.height / 3, binding.dpad.root.width, binding.dpad.root.height)
                    val left = Rect(0, 0, binding.dpad.root.width / 3, binding.dpad.root.height)
                    val right = Rect(2 * binding.dpad.root.width / 3, 0, binding.dpad.root.width, binding.dpad.root.height)

                    val x = motionEvent.x.toInt()
                    val y = motionEvent.y.toInt()

                    var state = 0
                    if (up.contains(x, y)) {
                        state += 1
                    }
                    if (down.contains(x, y)) {
                        state += 2
                    }
                    if (left.contains(x, y)) {
                        state += 4
                    }
                    if (right.contains(x, y)) {
                        state += 8
                    }

                    val changed = updateDpad(state)

                    if (changed) {
                        hapticVibrate(view, true)
                        if (animateButtons) {
                            binding.dpad.dpadHighlight.setImageResource(
                                when (state) {
                                    1 -> R.drawable.ic_button_dpad_highlight_pressed_up
                                    2 -> R.drawable.ic_button_dpad_highlight_pressed_down
                                    4 -> R.drawable.ic_button_dpad_highlight_pressed_left
                                    5 -> R.drawable.ic_button_dpad_highlight_pressed_up_left
                                    6 -> R.drawable.ic_button_dpad_highlight_pressed_down_left
                                    8 -> R.drawable.ic_button_dpad_highlight_pressed_right
                                    9 -> R.drawable.ic_button_dpad_highlight_pressed_up_right
                                    10 -> R.drawable.ic_button_dpad_highlight_pressed_down_right
                                    else -> R.drawable.ic_button_dpad_highlight
                                }
                            )
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    updateDpad(0)
                    hapticVibrate(view, false)
                    if (animateButtons) {
                        binding.dpad.dpadHighlight.setImageResource(R.drawable.ic_button_dpad_highlight)
                    }
                }
            }

            return@OnTouchListener true
        })

        additionalMappings[prefs.getInt(getString(R.string.additional_map_a_key), -1)] = "a"
        additionalMappings[prefs.getInt(getString(R.string.additional_map_b_key), -1)] = "b"
        additionalMappings[prefs.getInt(getString(R.string.additional_map_start_key), -1)] = "start"
        additionalMappings[prefs.getInt(getString(R.string.additional_map_select_key), -1)] = "select"
        additionalMappings[prefs.getInt(getString(R.string.additional_map_up_key), -1)] = "up"
        additionalMappings[prefs.getInt(getString(R.string.additional_map_down_key), -1)] = "down"
        additionalMappings[prefs.getInt(getString(R.string.additional_map_left_key), -1)] = "left"
        additionalMappings[prefs.getInt(getString(R.string.additional_map_right_key), -1)] = "right"
        additionalMappings[prefs.getInt(getString(R.string.additional_map_turbo_key), -1)] = "turbo"
        additionalMappings[prefs.getInt(getString(R.string.additional_map_menu_key), -1)] = "menu"
        additionalMappings[prefs.getInt(getString(R.string.additional_map_back_key), -1)] = "back"

        initialisePrinter()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideNavigation()
        }
    }

    private fun hideNavigation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars()
                    or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("Deprecation")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.screen.onResume()
        startGBCC()
    }

    private fun startGBCC() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val sampleRate: Int
        val framesPerBuffer: Int
        if (!bluetoothAudio()) {
            val lowLatency = prefs.getBoolean("audio_low_latency", true)
            if (lowLatency) {
                sampleRate = am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.let { str ->
                    Integer.parseInt(str).takeUnless { it == 0 }
                } ?: 44100
                framesPerBuffer =
                    am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.let { str ->
                        Integer.parseInt(str).takeUnless { it == 0 }
                    } ?: 256
            } else {
                val ms = prefs.getInt("audio_latency_ms", 40)
                sampleRate = 44100
                framesPerBuffer = ms * 44100 / (4 * 1000)
            }
        } else {
            val ms = prefs.getInt("bluetooth_latency_ms", 40)
            sampleRate = 44100
            framesPerBuffer = ms * 44100 / (4 * 1000)
        }

        Log.d("GBCC", "Using $sampleRate Hz audio, $framesPerBuffer samples per buffer")

        val configFile = filesDir.resolve("config/" + File(filename).nameWithoutExtension + ".cfg").let {
            if (it.exists()) it else null
        }
        val cheatFile = filesDir.resolve("config/" + File(filename).nameWithoutExtension + ".cheats").let {
            if (it.exists()) it else null
        }
        tempOptions?.let { setOptions(it) }
        loadedSuccessfully = loadRom(
            filename,
            sampleRate,
            framesPerBuffer,
            saveDir,
            configFile?.absolutePath,
            cheatFile?.absolutePath,
            PreferenceManager.getDefaultSharedPreferences(
                this
            )
        )
        if (!loadedSuccessfully) {
            Toast.makeText(
                this,
                "Error loading ROM:\n" + getErrorMessage().trim(),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }

        if (!prefs.getBoolean("audio_background_music", false)) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
                    setAudioAttributes(AudioAttributes.Builder().run {
                        setUsage(AudioAttributes.USAGE_GAME)
                        setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        build()
                    })
                    build()
                }
                audioManager.requestAudioFocus(focusRequest)
            } else {
                @Suppress("Deprecation")
                audioManager.requestAudioFocus(
                    {},
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
        handler.post(checkEmulatorState)
        if (!reboot && (resume || prefs.getBoolean("auto_resume", false))) {
            loadState(autoSaveState)
            binding.turboToggle.isChecked = false
            resume = false
        }
        if (resumePrinting) {
            print()
            resumePrinting = false
        }
        reboot = false
        if (hasAccelerometer()) {
            sensorManager.registerListener(this, accelerometer, 10000)
        }
        if (isCamera()) {
            if (checkCameraPermission()) {
                startCamera()
            } else if (!cameraPermissionRefused) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.CAMERA), REQUEST_CODE_PERMISSIONS
                )
            }
        }
    }

    private fun stopGBCC() {
        if (loadedSuccessfully) {
            resumePrinting = (printerAudio.playState == AudioTrack.PLAYSTATE_PLAYING)
            stopPrinting()
            tempOptions = getOptions()
            sensorManager.unregisterListener(this)
            handler.removeCallbacks(checkEmulatorState)
            saveState(autoSaveState)
            quit()
            resume = true
        }
    }


    override fun onPause() {
        stopGBCC()
        binding.screen.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        destroyTileset()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("resume", true)
        outState.putBoolean("resumePrinting", resumePrinting)
        outState.putByteArray("options", tempOptions)
        outState.putByteArray("printerByteArray", printerByteArray)
        outState.putSerializable("currentScreen", currentScreen)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            if (disableAccelerometer) {
                return
            }
            @Suppress("Deprecation")
            val rotation =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    display?.rotation ?: Surface.ROTATION_0
                } else {
                    windowManager.defaultDisplay.rotation
                }
            when (rotation) {
                Surface.ROTATION_0 -> updateAccelerometer(event.values[0], event.values[1])
                Surface.ROTATION_90 -> updateAccelerometer(-event.values[1], event.values[0])
                Surface.ROTATION_180 -> updateAccelerometer(-event.values[0], -event.values[1])
                Surface.ROTATION_270 -> updateAccelerometer(event.values[1], -event.values[0])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (checkCameraPermission()) {
                startCamera()
            } else {
                cameraPermissionRefused = true
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun checkCameraPermission() : Boolean {
        val permissionStatus = ContextCompat.checkSelfPermission(
            baseContext,
            Manifest.permission.CAMERA
        )
        return permissionStatus == PackageManager.PERMISSION_GRANTED
    }

    private fun checkFiles() {
        assets.open("tileset.png").use {
            val bitmap = BitmapFactory.decodeStream(it)
            val buf = ByteBuffer.allocate(bitmap.allocationByteCount)
            bitmap.copyPixelsToBuffer(buf)
            initialiseTileset(bitmap.width, bitmap.height, buf.array())
        }
        assets.open("camera.png").use {
            val bitmap = BitmapFactory.decodeStream(it)
            val buf = ByteBuffer.allocate(bitmap.allocationByteCount)
            bitmap.copyPixelsToBuffer(buf)
            setCameraImage(buf.array())
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // I assume that 320x240 is available on every camera out there
            // Though if it fails, the camera will still work
            val targetResolution =
                if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    Size(320, 240)
                } else {
                    Size(240, 320)
                }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(targetResolution)
                .build()

            imageAnalysis.setAnalyzer(executor) { image ->
                // Images are always in YUV_420_888 format, with Y as plane 0
                // with a pixel stride of 1, so we can just grab the greyscale from here
                val yplane = image.planes[0]
                val arr = ByteArray(yplane.buffer.remaining())
                yplane.buffer.get(arr)
                updateCamera(
                    arr,
                    image.width,
                    image.height,
                    image.imageInfo.rotationDegrees,
                    yplane.rowStride
                )
                image.close()
            }

            val cameraSelector = when (prefs.getString("camera", "back")) {
                "front" -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
            } catch (e: IllegalArgumentException) {
                Toast.makeText(
                    this,
                    getString(R.string.message_failed_camera, e.message),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            val pressed = when(keyCode) {
                KeyEvent.KEYCODE_BUTTON_L2 -> { val r = !lastLeftTrigger; lastLeftTrigger = true; r }
                KeyEvent.KEYCODE_BUTTON_R2 -> { val r = !lastRightTrigger; lastRightTrigger = true; r }
                else -> true
            }
            if (pressed && gamepadPress(keyCode, true)) {
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.repeatCount == 0) {
            val pressed = when(keyCode) {
                KeyEvent.KEYCODE_BUTTON_L2 -> { val r = lastLeftTrigger; lastLeftTrigger = false; r }
                KeyEvent.KEYCODE_BUTTON_R2 -> { val r = lastRightTrigger; lastRightTrigger = false; r }
                else -> true
            }
            if (pressed && gamepadPress(keyCode, false)) {
                return true
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
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            && event.action == MotionEvent.ACTION_MOVE) {
            if (event.source and InputDevice.SOURCE_DPAD != InputDevice.SOURCE_DPAD) {
                val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

                var hatUp = false
                var hatDown = false
                var hatLeft = false
                var hatRight = false
                if (y == -1.0f) {
                    hatUp = true
                } else if (y == 1.0f) {
                    hatDown = true
                }
                if (x == -1.0f) {
                    hatLeft = true
                } else if (x == 1.0f) {
                    hatRight = true
                }

                if (hatUp && !lastHatUp) {
                    gamepadPress(KeyEvent.KEYCODE_DPAD_UP, true)
                } else if (!hatUp && lastHatUp) {
                    gamepadPress(KeyEvent.KEYCODE_DPAD_UP, false)
                }
                if (hatDown && !lastHatDown) {
                    gamepadPress(KeyEvent.KEYCODE_DPAD_DOWN, true)
                } else if (!hatDown && lastHatDown) {
                    gamepadPress(KeyEvent.KEYCODE_DPAD_DOWN, false)
                }
                if (hatLeft && !lastHatLeft) {
                    gamepadPress(KeyEvent.KEYCODE_DPAD_LEFT, true)
                } else if (!hatLeft && lastHatLeft) {
                    gamepadPress(KeyEvent.KEYCODE_DPAD_LEFT, false)
                }
                if (hatRight && !lastHatRight) {
                    gamepadPress(KeyEvent.KEYCODE_DPAD_RIGHT, true)
                } else if (!hatRight && lastHatRight) {
                    gamepadPress(KeyEvent.KEYCODE_DPAD_RIGHT, false)
                }

                lastHatUp = hatUp
                lastHatDown = hatDown
                lastHatLeft = hatLeft
                lastHatRight = hatRight
            }
            var state = 0
            fun dpadSector(x: Float, y: Float) {
                if (x == 0.0f || y == 0.0f) {
                    if (y < 0) {
                        state = state or 1
                    } else if (y > 0) {
                        state = state or 2
                    }
                    if (x < 0) {
                        state = state or 4
                    } else if (x > 0) {
                        state = state or 8
                    }
                } else {
                    val sector = atan2(y, x) * 8 / PI.toFloat()  // Divide circle into sixteenths
                    if (-1 > sector && sector > -7) {
                        state = state or 1
                    } else if (7 > sector && sector > 1) {
                        state = state or 2
                    }
                    if (-5 > sector || sector > 5) {
                        state = state or 4
                    } else if (3 > sector && sector > -3) {
                        state = state or 8
                    }
                }
            }

            fun tiltValue(x: Float, y: Float) {
                val g = 9.81f
                updateAccelerometer(-g * x, g * y)
                disableAccelerometer = true
            }

            val lx = getCenteredAxis(event, MotionEvent.AXIS_X)
            val ly = getCenteredAxis(event, MotionEvent.AXIS_Y)
            val rx = getCenteredAxis(event, MotionEvent.AXIS_Z)
            val ry = getCenteredAxis(event, MotionEvent.AXIS_RZ)

            when (prefs.getString("button_map_analogue_left", "dpad")) {
                "dpad" -> dpadSector(lx, ly)
                "tilt" -> tiltValue(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y))
            }
            when (prefs.getString("button_map_analogue_right", "dpad")) {
                "dpad" -> dpadSector(rx, ry)
                "tilt" -> tiltValue(event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ))
            }
            updateDpad(state)

            val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER) > 0f
            val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER) > 0f
            gamepadPress(KeyEvent.KEYCODE_BUTTON_L2, lt && !lastLeftTrigger)
            gamepadPress(KeyEvent.KEYCODE_BUTTON_R2, rt && !lastRightTrigger)
            lastLeftTrigger = lt
            lastRightTrigger = rt
        }
        return super.onGenericMotionEvent(event)
    }

    private fun showBackPrompt() {
        if (prefs.getBoolean("back_prompt", false)) {
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.quit_confirmation)
                .setPositiveButton(R.string.button_quit) { _, _ -> finish() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setNeutralButton(R.string.button_reboot) { _, _ ->
                    stopGBCC()
                    reboot = true
                    startGBCC()
                }
                .create()
            dialog.window?.let {
                WindowCompat.getInsetsController(it, it.decorView).let { controller ->
                    // Hide system bars
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            dialog.show()
        } else {
            finish()
        }
    }

    private fun gamepadPress(keyCode: Int, pressed: Boolean): Boolean {
        val action = when(keyCode) {
            in KEYCODE_TO_STRING_MAP -> {
                prefs.getString(KEYCODE_TO_STRING_MAP[keyCode], null) ?: "unmapped"
            }
            in additionalMappings -> {
                additionalMappings[keyCode] ?: "unmapped"
            }
            else -> {
                return false
            }
        }
        val button = ACTION_TO_KEY_MAP[action] ?: -1
        when (action) {
            "back" -> onBackPressedDispatcher.onBackPressed()
            "turbo" -> if (pressed) toggleTurboWrapper()
            else -> press(button, pressed)
        }
        return true
    }

    private fun updateDpad(state: Int) : Boolean {
        val lastState = dpadState
        dpadState = state
        val toggledOn = (lastState and dpadState) xor dpadState
        val toggledOff = (lastState or dpadState) xor dpadState

        if (toggledOn and 1 > 0) {
            press(BUTTON_CODE_UP, true)
        } else if (toggledOff and 1 > 0) {
            press(BUTTON_CODE_UP, false)
        }
        if (toggledOn and 2 > 0) {
            press(BUTTON_CODE_DOWN, true)
        } else if (toggledOff and 2 > 0) {
            press(BUTTON_CODE_DOWN, false)
        }
        if (toggledOn and 4 > 0) {
            press(BUTTON_CODE_LEFT, true)
        } else if (toggledOff and 4 > 0) {
            press(BUTTON_CODE_LEFT, false)
        }
        if (toggledOn and 8 > 0) {
            press(BUTTON_CODE_RIGHT, true)
        } else if (toggledOff and 8 > 0) {
            press(BUTTON_CODE_RIGHT, false)
        }

        return (toggledOn or toggledOff) > 0
    }

    private fun bluetoothAudio() : Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val mr = getSystemService(Context.MEDIA_ROUTER_SERVICE) as MediaRouter
            val deviceType = mr.getSelectedRoute(MediaRouter.ROUTE_TYPE_LIVE_AUDIO).deviceType
            return deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH
        }
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetooth = outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        val wired = outputs.any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                    || it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }

        return bluetooth && !wired
    }

    private fun toggleTurboWrapper() {
        val turbo = toggleTurbo()
        binding.turboToggle.isChecked = turbo
    }

    private fun initialisePrinter() {
        initialisePrinterAudio()

        val dimension: Float
        val property: Property<View, Float>

        if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            dimension = resources.displayMetrics.widthPixels.toFloat()
            property = View.TRANSLATION_X
            if (currentScreen == Screen.GAMEBOY) {
                binding.printerLayout.translationX = dimension
            } else {
                binding.gameboyLayout.translationX = -dimension
                binding.printerLayout.visibility = View.VISIBLE
            }
            printerScrollAnimation = ObjectAnimator.ofFloat(binding.printerPaperLayout, View.TRANSLATION_Y, 0f).apply {
                duration = PRINTER_SCROLL_MILLIS
            }
        } else {
            dimension = -resources.displayMetrics.heightPixels.toFloat()
            property = View.TRANSLATION_Y
            if (currentScreen == Screen.GAMEBOY) {
                binding.printerLayout.translationY = dimension
            } else {
                binding.gameboyLayout.translationY = -dimension
                binding.printerLayout.visibility = View.VISIBLE
            }
            printerScrollAnimation = ObjectAnimator.ofFloat(binding.printerPaperLayout, View.TRANSLATION_X, 0f).apply {
                duration = PRINTER_SCROLL_MILLIS
            }
        }

        updatePrinterImage(false)

        transitionToPrinter.play(
            ObjectAnimator.ofFloat(binding.printerLayout, property, 0f).apply {
                duration = PRINTER_TRANSITION_MILLIS
            }
        ).with(
            ObjectAnimator.ofFloat(binding.gameboyLayout, property, -dimension).apply {
                duration = PRINTER_TRANSITION_MILLIS
            }
        )
        transitionToPrinter.doOnStart {
            binding.printerLayout.visibility = View.VISIBLE
            currentScreen = Screen.PRINTER
            updatePrinterImage(false)
        }


        transitionToGameboy.play(
            ObjectAnimator.ofFloat(binding.printerLayout, property, dimension).apply {
                duration = PRINTER_TRANSITION_MILLIS
            }
        ).with(
            ObjectAnimator.ofFloat(binding.gameboyLayout, property, 0f).apply {
                duration = PRINTER_TRANSITION_MILLIS
            }
        )
        transitionToGameboy.doOnEnd {
            binding.printerLayout.visibility = View.GONE
            currentScreen = Screen.GAMEBOY
        }

        binding.printerTransitionButton.setOnClickListener {
            if (transitionToGameboy.isRunning || transitionToPrinter.isRunning || currentScreen == Screen.PRINTER) {
                return@setOnClickListener
            }
            transitionToPrinter.start()
        }

        binding.gameboyTransitionButton.setOnClickListener {
            if (transitionToGameboy.isRunning || transitionToPrinter.isRunning || currentScreen == Screen.GAMEBOY) {
                return@setOnClickListener
            }
            transitionToGameboy.start()
        }

        val printerExport = registerForActivityResult(CreatePrinterImage()) { uri : Uri? ->
            if (uri == null) {
                return@registerForActivityResult
            }
            Thread {
                printerByteArray.let {
                    if (it.isEmpty()) {
                        return@Thread
                    }
                    val width = 160
                    val height = it.size / 160
                    val bitmap = Bitmap.createBitmap(
                        width,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    // Android fails to export ALPHA_8 bitmaps, so we have to use ARGB_8888
                    // and set each pixel manually.
                    for (x in 0 until width) {
                        for (y in 0 until height) {
                            val px = 255 - it[y * width + x].toUByte().toInt()
                            bitmap.setPixel(x, y, Color.argb(255, px, px, px))
                        }
                    }
                    contentResolver.openOutputStream(uri).use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                }
            }.start()
        }
        binding.printerSaveButton.setOnClickListener {
            val date = SimpleDateFormat("yyyy-MM-dd'T'HHmmss").format(Date())
            printerExport.launch("gbcc_printer_$date.png")
        }
        printerPaperTearSound = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            )
            .build()
        printerPaperTearSound.load(this, R.raw.printer_paper_tear, 1)
        binding.printerClearButton.setOnClickListener {
            binding.printerClearButton.isEnabled = false
            binding.printerSaveButton.isEnabled = false

            val portrait = (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
            // Make a duplicate of the printer paper
            binding.printerPaperTearLayout.visibility = View.VISIBLE
            updatePrinterPaperTear()

            // Reset the actual paper
            printerScrollAnimation.cancel()
            printerByteArray = ByteArray(0)
            updatePrinterImage(false)

            // Animate the torn paper
            val screenWidth = resources.displayMetrics.widthPixels.toFloat()
            val screenHeight = resources.displayMetrics.heightPixels.toFloat()
            val paperWidth = binding.printerPaperLayout.width.toFloat()
            val paperHeight = binding.printerPaperLayout.height.toFloat()
            val curX = binding.printerPaperTearLayout.translationX
            val curY = binding.printerPaperTearLayout.translationY
            val anim = AnimatorSet()
            val path = Path().apply {
                if (portrait) {
                    moveTo(0f, curY)
                    lineTo(screenWidth + paperHeight, curY - paperWidth)
                    Log.d("Printer", "Target: (${screenWidth + paperHeight}, ${curY - paperWidth})")
                } else {
                    moveTo(curX, 0f)
                    lineTo(curX - paperHeight, -(screenHeight + paperWidth))
                }
            }
            anim.play(
                ObjectAnimator.ofFloat(binding.printerPaperTearLayout, View.TRANSLATION_X, View.TRANSLATION_Y, path).apply {
                    duration = PRINTER_CLEAR_MILLIS
                }
            ).with(
                ObjectAnimator.ofFloat(binding.printerPaperTearLayout, View.ROTATION, 0f, 90f).apply {
                    duration = PRINTER_CLEAR_MILLIS
                }
            )
            val volume = prefs.getInt("printer_volume", 100) / 100f
            anim.doOnStart {
                printerPaperTearSound.play(1, 0.5f * volume, 0.5f * volume, 0, 0, 1f)
            }
            anim.doOnEnd {
                binding.printerPaperTearLayout.visibility = View.GONE
            }
            anim.interpolator = FastOutSlowInInterpolator()
            anim.start()
        }

    }

    private fun initialisePrinterAudio() {
        val headerLength = 44L
        val data: ByteArray
        resources.openRawResourceFd(R.raw.print).use { fd ->
            data = ByteArray((fd.length - headerLength).toInt())
            fd.createInputStream().use {
                it.skip(headerLength)
                it.read(data)
            }
        }
        printerAudio = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_8BIT)
                .setSampleRate(22050)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            data.size,
            AudioTrack.MODE_STATIC,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        printerAudio.setVolume(prefs.getInt("printer_volume", 100) / 100f)
        printerAudio.write(data, 0, data.size)
        printerAudioLength = data.size
        printerAudio.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            private var stop = false

            override fun onMarkerReached(track: AudioTrack?) {
                if (stop) {
                    stop = false
                    printerAudio.stop()
                    printerAudio.reloadStaticData()
                    return
                }
                val finished = updatePrinter()
                val strip = getPrinterStrip()
                if (strip.isEmpty()) {
                    stop = true
                    return
                }
                val oldSize = printerByteArray.size
                printerByteArray = printerByteArray.copyOf(oldSize + strip.size)
                strip.copyInto(printerByteArray, oldSize)
                updatePrinterImage(true)

                printerAudio.notificationMarkerPosition += data.size
                if (finished) {
                    stop = true
                    printerAudio.notificationMarkerPosition -= PRINTER_UPDATE_SAMPLES
                }
            }

            override fun onPeriodicNotification(track: AudioTrack?) {
                // This method is required
            }
        })
    }

    private fun updatePrinterImage(animate: Boolean) {
        val portrait = (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)

        val printoutWidthPX = 160  // Image width in Game boy Pixels
        val printoutHeightPX = printerByteArray.size / printoutWidthPX
        val printoutWidthDP = 128f // Width of printout on paper
        val paperWidthDP = 192f  // Width of paper in printer drawable
        val printerWidthDP = 360f  // Width of printer drawable
        val printerHeightDP = 640f
        val bladesHeightDP = 520f // Y-Position of blades in printer drawable

        // We have to use screen size rather than e.g. binding.printer.width, as it
        // may not have been laid out fully when we call this function
        val printerWidthPX = if (portrait) {
            resources.displayMetrics.widthPixels.toFloat()
        } else {
            resources.displayMetrics.heightPixels.toFloat()
        }
        val printerHeightPX = printerWidthPX * printerHeightDP / printerWidthDP

        val dpPerPX = printoutWidthDP / printoutWidthPX

        val paper = binding.printerPaper
        if (printoutHeightPX > 0) {
            paper.setImageDrawable(PrinterDrawable(printerByteArray, !portrait))
            binding.printerClearButton.isEnabled = true
            binding.printerSaveButton.isEnabled = true
        } else {
            binding.printerClearButton.isEnabled = false
            binding.printerSaveButton.isEnabled = false
        }
        // Scale paper width to match printer image
        val paperWidth : Int
        if (portrait) {
            paperWidth = (printerWidthPX * paperWidthDP / printerWidthDP).toInt()
            paper.layoutParams.width = paperWidth
        } else {
            paperWidth = (printerWidthPX * paperWidthDP / printerWidthDP).toInt()
            paper.layoutParams.height = paperWidth
        }

        // Scale height by same factor
        if (portrait) {
            paper.layoutParams.height =
                (printoutHeightPX * dpPerPX * paperWidth / paperWidthDP).toInt()
        } else {
            paper.layoutParams.width =
                (printoutHeightPX * dpPerPX * paperWidth / paperWidthDP).toInt()
        }
        paper.requestLayout()
        if (portrait) {
            binding.printerPaperTop.layoutParams.width = paperWidth
            binding.printerPaperBottom.layoutParams.width = paperWidth
        } else {
            binding.printerPaperTop.layoutParams.height = paperWidth
            binding.printerPaperBottom.layoutParams.height = paperWidth
        }
        binding.printerPaperTop.requestLayout()
        binding.printerPaperBottom.requestLayout()

        if (portrait) {
            if (binding.printerPaperLayout.translationY == 0f) {
                binding.printerPaperLayout.translationY =
                    printerHeightPX * bladesHeightDP / printerHeightDP
            }
            val target = printerHeightPX * bladesHeightDP / printerHeightDP - binding.printerPaper.layoutParams.height
            if (animate) {
                printerScrollAnimation.setFloatValues(target)
                printerScrollAnimation.start()
            } else {
                binding.printerPaperLayout.translationY = target
            }
        } else {
            if (binding.printerPaperLayout.translationX == 0f) {
                binding.printerPaperLayout.translationX =
                    printerHeightPX * bladesHeightDP / printerHeightDP
            }
            val target = printerHeightPX * bladesHeightDP / printerHeightDP - binding.printerPaper.layoutParams.width
            if (animate) {
                printerScrollAnimation.setFloatValues(target)
                printerScrollAnimation.start()
            } else {
                binding.printerPaperLayout.translationX = target
            }
        }
    }

    // Make the torn paper look just like the normal paper
    private fun updatePrinterPaperTear() {
        val portrait = (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
        val paper = binding.printerPaperTear
        if (printerByteArray.isNotEmpty()) {
            paper.setImageDrawable(PrinterDrawable(printerByteArray, !portrait))
        }

        paper.layoutParams.width = binding.printerPaper.layoutParams.width
        paper.layoutParams.height = binding.printerPaper.layoutParams.height

        if (portrait) {
            binding.printerPaperTearTop.layoutParams.width = binding.printerPaper.layoutParams.width
            binding.printerPaperTearBottom.layoutParams.width = binding.printerPaper.layoutParams.width
        } else {
            binding.printerPaperTearTop.layoutParams.height = binding.printerPaper.layoutParams.height
            binding.printerPaperTearBottom.layoutParams.height = binding.printerPaper.layoutParams.height
        }

        paper.requestLayout()
        binding.printerPaperTop.requestLayout()
        binding.printerPaperBottom.requestLayout()

        val layout = binding.printerPaperTearLayout
        if (portrait) {
            layout.translationY = binding.printerPaperLayout.translationY
        } else {
            layout.translationX = binding.printerPaperLayout.translationX
        }
        layout.rotation = 0f
    }

    private fun print() {
        if (currentScreen != Screen.PRINTER) {
            binding.printerTransitionButton.performClick()
        }
        if (prefs.getBoolean("animate_printer", true)) {
            printerAudio.setLoopPoints(0, printerAudioLength, -1)
            printerAudio.notificationMarkerPosition = PRINTER_UPDATE_SAMPLES
            printerAudio.play()
        } else {
            var finished = updatePrinter()
            var strip = getPrinterStrip()
            while (strip.isNotEmpty()) {
                val oldSize = printerByteArray.size
                printerByteArray = printerByteArray.copyOf(oldSize + strip.size)
                strip.copyInto(printerByteArray, oldSize)
                updatePrinterImage(false)

                if (finished) {
                    break
                } else {
                    finished = updatePrinter()
                    strip = getPrinterStrip()
                }
            }
        }
    }

    private fun stopPrinting() {
        printerAudio.stop()
    }

    class DpadListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            return true
        }
    }

    companion object {
        init {
            System.loadLibrary("gbcc")
        }
    }
}

class MyGLSurfaceView : GLSurfaceView {
    private var renderer: MyGLRenderer? = null

    constructor(context: Context) : super(context) {
        setup()
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        setup()
    }

    private fun setup() {
        setMeasuredDimension(160, 144)
        layoutParams = ViewGroup.LayoutParams(160, 144)
    }

    init {
        if (!isInEditMode) {
            // Create an OpenGL ES 3.0 context
            setEGLContextClientVersion(3)

            renderer = MyGLRenderer()

            // Set the Renderer for drawing on the GLSurfaceView
            setRenderer(renderer)
        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        super.surfaceCreated(holder)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Limit frame rate on devices with >60Hz screens
            holder.surface.setFrameRate(59.7275f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        var width = 0
        var height = 0
        val scaleX = widthSize / 160
        val scaleY = heightSize / 144

        when(widthMode) {
            MeasureSpec.EXACTLY -> width = widthSize
            MeasureSpec.AT_MOST -> Unit
            MeasureSpec.UNSPECIFIED -> width = 160
        }

        when(heightMode) {
            MeasureSpec.EXACTLY -> height = heightSize
            MeasureSpec.AT_MOST -> Unit
            MeasureSpec.UNSPECIFIED -> height = 144
        }

        if (width == 0 && height == 0) {
            val scale = min(scaleX, scaleY)
            width = 160 * scale
            height = 144 * scale
        } else if (width == 0) {
            width = (height * 160) / 144
        } else if (height == 0) {
            height = (width * 144) / 160
        }

        setMeasuredDimension(width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        destroyWindow()
        super.surfaceDestroyed(holder)
    }

    private external fun destroyWindow()
}

class MyGLRenderer : GLSurfaceView.Renderer {

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        initWindow()
    }

    override fun onDrawFrame(unused: GL10) {
        updateWindow()
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        resizeWindow(width, height)
    }

    private external fun initWindow()
    private external fun updateWindow()
    private external fun resizeWindow(width: Int, height: Int)
}

class PrinterDrawable(byteArray: ByteArray, private val landscape: Boolean) : Drawable() {
    private val bitmap : Bitmap
    private val whitePaint: Paint = Paint().apply { setARGB(255, 255, 255, 255) }
    private val blackPaint: Paint = Paint().apply {
        setARGB(255, 0, 0, 0)
        isFilterBitmap = true
    }

    init {
        bitmap = Bitmap.createBitmap(
            160,
            byteArray.size / 160,
            Bitmap.Config.ALPHA_8
        )
        bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(byteArray))
    }

    override fun draw(canvas: Canvas) {
        // Get the drawable's bounds
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val rect = RectF(width / 6f, 0f, 5f * width / 6f, height)

        if (landscape) {
            canvas.rotate(-90f, width / 2, height / 2)
            canvas.scale(height / width, width / height, width / 2, height / 2)
        }
        canvas.drawRect(0f, 0f, width, height, whitePaint)
        canvas.drawBitmap(bitmap, null, rect, blackPaint)
    }

    override fun setAlpha(alpha: Int) {
        // This method is required
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // This method is required
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity() =
        // Must be PixelFormat.UNKNOWN, TRANSLUCENT, TRANSPARENT, or OPAQUE
        PixelFormat.OPAQUE
}

class CreatePrinterImage : ActivityResultContracts.CreateDocument("image/png") {
    @SuppressLint("SimpleDateFormat")
    override fun createIntent(context: Context, input: String): Intent {
        val intent = super.createIntent(context, input)
        intent.apply {
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        return intent
    }
}
