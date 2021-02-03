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
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter
import android.net.Uri
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.*
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.databinding.ActivityGlBinding
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

private const val autoSaveState: Int = 10
private const val REQUEST_CODE_PERMISSIONS = 10

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

class GLActivity : AppCompatActivity(), SensorEventListener, LifecycleOwner {
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var gestureDetector : GestureDetector
    private lateinit var sensorManager : SensorManager
    private var accelerometer : Sensor? = null
    private lateinit var vibrator : Vibrator
    private lateinit var checkEmulatorState : Runnable
    private lateinit var filename : String
    private var resume = false
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
    private external fun setOptions(options: ByteArray?)
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


    init {
        checkEmulatorState = Runnable {
            if (checkVibrationFun()) {
                vibrate(10)
            }
            val turbo = checkTurboFun()
            if (binding.turboToggle.isChecked != turbo) {
                binding.turboToggle.isChecked = turbo
            }
            if (binding.turboToggleDark.isChecked != turbo) {
                binding.turboToggleDark.isChecked = turbo
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
                    }.setNegativeButton(R.string.invalid_opcode_button_quit) { _, _ ->
                        finish()
                    }.setCancelable(false)
                    .create()
                    .show()
            } else {
                handler.postDelayed(checkEmulatorState, 10)
            }
        }
    }

    private fun hapticVibrate() {
        val milliseconds = prefs.getInt("haptic_strength", 0).toLong()
        vibrate(milliseconds)
    }

    private fun vibrate(milliseconds: Long) {
        if (milliseconds == 0L) {
            vibrator.cancel()
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    milliseconds,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(milliseconds)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setButtonIds(views: Array<View>, buttons: Array<Int>) {
        fun inBounds(view: View, x: Int, y: Int): Boolean {
            val rect = Rect()
            val location = IntArray(2)
            view.getDrawingRect(rect)
            view.getLocationOnScreen(location)
            rect.offset(location[0], location[1])
            return rect.contains(x, y)
        }
        views.forEachIndexed { index, view ->
            view.setOnTouchListener(View.OnTouchListener { touchView, motionEvent ->
                if (touchView != view) {
                    return@OnTouchListener false
                }
                when (motionEvent.action) {
                    MotionEvent.ACTION_DOWN -> {
                        press(buttons[index], true)
                        view.isPressed = true && animateButtons
                        hapticVibrate()
                    }
                    MotionEvent.ACTION_UP -> {
                        views.forEachIndexed { index2, view2 ->
                            press(buttons[index2], false)
                            view2.isPressed = false && animateButtons
                        }
                    }
                    MotionEvent.ACTION_MOVE -> run {
                        val x = motionEvent.rawX.toInt()
                        val y = motionEvent.rawY.toInt()
                        views.forEachIndexed { index2, view2 ->
                            if (view2 != view) {
                                val pressed = inBounds(view2, x, y)
                                if (pressed && !isPressed(buttons[index2])) {
                                    hapticVibrate()
                                }
                                press(buttons[index2], pressed)
                                view2.isPressed = pressed && animateButtons
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
        binding.turboToggleDark.setOnClickListener { toggleTurbo() }

        if (!gbc) {
            val screenBorderColor: Int
            val theme = prefs.getString("dmg_color", "Light")
            if (theme == "Dark") {
                screenBorderColor = ContextCompat.getColor(this, R.color.dmgDarkScreenBorder)
                binding.dpad.dpadBackground.setColorFilter(
                    ContextCompat.getColor(this, R.color.dmgDarkDpad),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )

                binding.buttonA.setImageResource(R.drawable.ic_button_ab_dmg_dark_selector)
                binding.buttonB.setImageResource(R.drawable.ic_button_ab_dmg_dark_selector)

                binding.buttonStart.setImageResource(R.drawable.ic_button_startselect_dmg_dark_selector)
                binding.buttonSelect.setImageResource(R.drawable.ic_button_startselect_dmg_dark_selector)

                binding.turboToggle.visibility = View.INVISIBLE
                binding.turboToggleDark.visibility = View.VISIBLE
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
                    android.graphics.PorterDuff.Mode.SRC_IN
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
        binding.turboToggleDark.scaleX = binding.turboToggle.scaleX
        binding.turboToggleDark.scaleY = binding.turboToggle.scaleX

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
        binding.turboToggleDark.translationX = prefs.getFloat(getString(R.string.turbo_offset_x_key), 0f)
        binding.turboToggleDark.translationY = prefs.getFloat(getString(R.string.turbo_offset_y_key), 0f)

        if (!prefs.getBoolean("show_turbo", false)) {
            binding.turboToggle.visibility = View.GONE
            binding.turboToggleDark.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        checkFiles()

        if (savedInstanceState != null) {
            resume = resume || savedInstanceState.getBoolean("resume")
            if (tempOptions == null) {
                tempOptions = savedInstanceState.getByteArray("options")
            }
        }

        animateButtons = prefs.getBoolean("animate_buttons", true)
        setButtonIds(arrayOf(binding.buttonA, binding.buttonB), arrayOf(BUTTON_CODE_A, BUTTON_CODE_B))
        setButtonIds(
            arrayOf(binding.buttonStart, binding.buttonSelect), arrayOf(
                BUTTON_CODE_START,
                BUTTON_CODE_SELECT
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
                    if (!prefs.getBoolean("show_turbo", false)) {
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
                        hapticVibrate()
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
                    if (animateButtons) {
                        binding.dpad.dpadHighlight.setImageResource(R.drawable.ic_button_dpad_highlight)
                    }
                }
            }

            return@OnTouchListener true
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideNavigation()
        }
    }

    private fun hideNavigation() {
        @Suppress("Deprecation")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars()
                    or WindowInsets.Type.navigationBars())
        } else {
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
        if (tempOptions != null) {
            setOptions(tempOptions)
        }
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
        handler.post(checkEmulatorState)
        if (resume) {
            loadState(autoSaveState)
            binding.turboToggle.isChecked = false
            binding.turboToggleDark.isChecked = false
            resume = false
        }
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
        outState.putByteArray("options", tempOptions)
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

            imageAnalysis.setAnalyzer(executor, { image ->
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
            })

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
            || event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) {
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
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
            || event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) {
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

    private fun gamepadPress(keyCode: Int, pressed: Boolean): Boolean {
        if (KEYCODE_TO_STRING_MAP.containsKey(keyCode)){
            val action = prefs.getString(KEYCODE_TO_STRING_MAP[keyCode], null) ?: "unmapped"
            val button = ACTION_TO_KEY_MAP[action] ?: -1
            when (action) {
                "back" -> onBackPressed()
                "turbo" -> if (pressed) toggleTurboWrapper()
                else -> press(button, pressed)
            }
            return true
        }
        return false
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
        binding.turboToggleDark.isChecked = turbo
    }

    class DpadListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent?): Boolean {
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
