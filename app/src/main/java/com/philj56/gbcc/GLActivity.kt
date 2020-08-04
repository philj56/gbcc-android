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
import android.content.Context
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
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.*
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_gl.*
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

class GLActivity : AppCompatActivity(), SensorEventListener, LifecycleOwner {
    private val handler = Handler()
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var gestureDetector : GestureDetector
    private lateinit var sensorManager : SensorManager
    private var accelerometer : Sensor? = null
    private lateinit var vibrator : Vibrator
    private lateinit var checkVibration : Runnable
    private lateinit var filename : String
    private var resume = false
    private var loadedSuccessfully = false
    private var cameraPermissionRefused = false
    private var dpadState = 0
    private lateinit var saveDir : String
    private var tempOptions : ByteArray? = null

    external fun toggleMenu(view: View)
    private external fun chdir(dirName: String)
    private external fun checkRom(file: String): Boolean
    private external fun loadRom(file: String, sampleRate: Int, samplesPerBuffer: Int, saveDir: String, prefs: SharedPreferences): Boolean
    private external fun getErrorMessage(): String
    private external fun quit()
    private external fun press(button: Int, pressed: Boolean)
    private external fun isPressed(button: Int) : Boolean
    private external fun toggleTurbo()
    private external fun saveState(state: Int)
    private external fun loadState(state: Int)
    private external fun getOptions(): ByteArray
    private external fun setOptions(options: ByteArray?)
    private external fun checkVibrationFun(): Boolean
    private external fun updateAccelerometer(x: Float, y: Float)
    private external fun updateCamera(array: ByteArray, width: Int, height: Int, rotation: Int, rowStride: Int)
    private external fun initialiseTileset(width: Int, height: Int, data: ByteArray)
    private external fun destroyTileset()
    private external fun setCameraImage(data: ByteArray)
    private external fun hasRumble(): Boolean
    private external fun hasAccelerometer(): Boolean
    private external fun isCamera(): Boolean


    init {
        checkVibration = Runnable {
            if (checkVibrationFun()) {
                vibrate(10)
            } else {
                vibrate(0)
            }
            handler.postDelayed(checkVibration, 10)
        }
    }

    private fun hapticVibrate() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val milliseconds = prefs.getInt("haptic_strength", 0).toLong()
        vibrate(milliseconds)
    }

    private fun vibrate(milliseconds: Long) {
        if (milliseconds == 0L) {
            vibrator.cancel()
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
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
                        hapticVibrate()
                    }
                    MotionEvent.ACTION_UP -> {
                        press(buttons[index], false)
                    }
                    MotionEvent.ACTION_MOVE -> run {
                        val x = motionEvent.rawX.toInt()
                        val y = motionEvent.rawY.toInt()
                        views.forEachIndexed { index2, view2 ->
                            if (view2 != view) {
                                val press = inBounds(view2, x, y)
                                if (press && !isPressed(buttons[index2])) {
                                    hapticVibrate()
                                }
                                press(buttons[index2], press)
                            }
                        }
                    }
                }
                return@OnTouchListener false
            })
        }
    }

    private fun updateLayout(gbc: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val bgColor = when (gbc) {
            true -> when (prefs.getString("color", "Teal")) {
                "Berry" -> R.color.gbcBerry
                "Dandelion" -> R.color.gbcDandelion
                "Grape" -> R.color.gbcGrape
                "Kiwi" -> R.color.gbcKiwi
                "Teal" -> R.color.gbcTeal
                else -> R.color.gbcTeal
            }
            false -> R.color.dmgBackground
        }
        window.setBackgroundDrawableResource(bgColor)

        if (!gbc) {
            val screenBorderColor = ContextCompat.getColor(this, R.color.dmgScreenBorder)
            val borders = arrayOf(
                screenBorderTop,
                screenBorderBottom,
                screenBorderLeft,
                screenBorderRight
            )

            borders.forEach {
                it.setColorFilter(
                    screenBorderColor,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }

            buttonA.setImageResource(R.drawable.ic_button_ab_dmg)
            buttonB.setImageResource(R.drawable.ic_button_ab_dmg)

            buttonStart.setImageResource(R.drawable.ic_button_startselect_dmg)
            buttonSelect.setImageResource(R.drawable.ic_button_startselect_dmg)

            buttonStart.rotation = -45f
            buttonSelect.rotation = -45f

            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                val bottomLeft = findViewById<ImageView>(R.id.bottomLeftCorner)
                val bottomRight = findViewById<ImageView>(R.id.bottomRightCorner)
                val px = (resources.displayMetrics.density + 0.5f).toInt()

                bottomLeft.layoutParams.apply {
                    width = 16 * px
                    height = width
                }

                bottomRight.layoutParams.apply {
                    width = 64 * px
                    height = width
                }
            }
        }

        buttonA.scaleX = prefs.getFloat(getString(R.string.a_scale_key), 1f)
        buttonA.scaleY = buttonA.scaleX
        buttonB.scaleX = prefs.getFloat(getString(R.string.b_scale_key), 1f)
        buttonB.scaleY = buttonB.scaleX
        buttonStart.scaleX = prefs.getFloat(getString(R.string.start_scale_key), 1f)
        buttonStart.scaleY = buttonStart.scaleX
        buttonSelect.scaleX = prefs.getFloat(getString(R.string.select_scale_key), 1f)
        buttonSelect.scaleY = buttonSelect.scaleX
        dpad.scaleX = prefs.getFloat(getString(R.string.dpad_scale_key), 1f)
        dpad.scaleY = dpad.scaleX

        buttonA.translationX = prefs.getFloat(getString(R.string.a_offset_x_key), 0f)
        buttonA.translationY = prefs.getFloat(getString(R.string.a_offset_y_key), 0f)
        buttonB.translationX = prefs.getFloat(getString(R.string.b_offset_x_key), 0f)
        buttonB.translationY = prefs.getFloat(getString(R.string.b_offset_y_key), 0f)
        buttonStart.translationX = prefs.getFloat(getString(R.string.start_offset_x_key), 0f)
        buttonStart.translationY = prefs.getFloat(getString(R.string.start_offset_y_key), 0f)
        buttonSelect.translationX = prefs.getFloat(getString(R.string.select_offset_x_key), 0f)
        buttonSelect.translationY = prefs.getFloat(getString(R.string.select_offset_y_key), 0f)
        dpad.translationX = prefs.getFloat(getString(R.string.dpad_offset_x_key), 0f)
        dpad.translationY = prefs.getFloat(getString(R.string.dpad_offset_y_key), 0f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chdir(filesDir.absolutePath)

        window.decorView.setOnSystemUiVisibilityChangeListener {
            hideNavigation()
        }

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        requestedOrientation = prefs.getString("orientation", "-1")?.toInt() ?: -1
        setContentView(R.layout.activity_gl)

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
            Toast.makeText(this,
                "Error loading ROM:\n" + getErrorMessage().trim(),
                Toast.LENGTH_SHORT
            ).show()
            finish()
        }

        updateLayout(
            when(prefs.getString("skin", "auto")) {
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

        setButtonIds(arrayOf(buttonA, buttonB), arrayOf(BUTTON_CODE_A, BUTTON_CODE_B))
        setButtonIds(arrayOf(buttonStart, buttonSelect), arrayOf(BUTTON_CODE_START, BUTTON_CODE_SELECT))

        placeholderTouchTarget.setOnTouchListener { v, _ ->
            // This shouldn't be needed, but Android
            // seems to act strangely when the root view is touched
            // and ignores any further touches.
            if (v != placeholderTouchTarget) {
                return@setOnTouchListener false
            }
            return@setOnTouchListener true
        }

        dpad.setOnTouchListener( View.OnTouchListener { view, motionEvent ->
            if (view != dpad) {
                return@OnTouchListener false
            }
            if (dpadState == 0) {
                if (gestureDetector.onTouchEvent(motionEvent)) {
                    toggleTurbo()
                    return@OnTouchListener true
                }
            }
            val up = Rect(0, 0, dpad.width, dpad.height / 3)
            val down = Rect(0, 2 * dpad.height / 3, dpad.width, dpad.height)
            val left = Rect(0, 0, dpad.width / 3, dpad.height)
            val right = Rect(2 * dpad.width / 3, 0, dpad.width, dpad.height)
            val lastState = dpadState
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    val x = motionEvent.x.toInt()
                    val y = motionEvent.y.toInt()
                    dpadState = 0
                    if (up.contains(x, y)) {
                        dpadState += 1
                    }
                    if (down.contains(x, y)) {
                        dpadState += 2
                    }
                    if (left.contains(x, y)) {
                        dpadState += 4
                    }
                    if (right.contains(x, y)) {
                        dpadState += 8
                    }

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

                    if (lastState != dpadState) {
                        hapticVibrate()
                    }
                }
                MotionEvent.ACTION_UP -> {
                    dpadState = 0
                    press(BUTTON_CODE_UP, false)
                    press(BUTTON_CODE_DOWN, false)
                    press(BUTTON_CODE_LEFT, false)
                    press(BUTTON_CODE_RIGHT, false)
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
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    override fun onResume() {
        super.onResume()
        screen.onResume()
        startGBCC()
    }

    private fun startGBCC() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

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

        if (tempOptions != null) {
            setOptions(tempOptions)
        }
        loadedSuccessfully = loadRom(filename, sampleRate, framesPerBuffer, saveDir, PreferenceManager.getDefaultSharedPreferences(this))
        if (!loadedSuccessfully) {
            Toast.makeText(this,
                "Error loading ROM:\n" + getErrorMessage().trim(),
                Toast.LENGTH_SHORT
            ).show()
            finish()
            return
        }
        if (resume) {
            loadState(autoSaveState)
            resume = false
        }
        if (hasRumble()) {
            handler.post(checkVibration)
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
            handler.removeCallbacks(checkVibration)
            saveState(autoSaveState)
            quit()
            resume = true
        }
    }


    override fun onPause() {
        stopGBCC()
        screen.onPause()
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
            when (windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_0 -> updateAccelerometer(event.values[0], event.values[1])
                Surface.ROTATION_90 -> updateAccelerometer(-event.values[1], event.values[0])
                Surface.ROTATION_180 -> updateAccelerometer(-event.values[0], -event.values[1])
                Surface.ROTATION_270 -> updateAccelerometer(event.values[1], -event.values[0])
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (checkCameraPermission()) {
                startCamera()
            } else {
                cameraPermissionRefused = true
            }
        }
    }

    private fun checkCameraPermission() : Boolean {
        val permissionStatus = ContextCompat.checkSelfPermission(baseContext, Manifest.permission.CAMERA)
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

        cameraProviderFuture.addListener(Runnable {
            val cameraProvider = cameraProviderFuture.get()

            // I assume that 320x240 is available on every camera out there
            // Though if it fails, the camera will still work
            val targetResolution = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Size(320, 240)
            } else {
                Size(240, 320)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(targetResolution)
                .build()

            imageAnalysis.setAnalyzer(executor, ImageAnalysis.Analyzer { image ->
                // Images are always in YUV_420_888 format, with Y as plane 0
                // with a pixel stride of 1, so we can just grab the greyscale from here
                val yplane = image.planes[0]
                val arr = ByteArray(yplane.buffer.remaining())
                yplane.buffer.get(arr)
                updateCamera(arr, image.width, image.height, image.imageInfo.rotationDegrees, yplane.rowStride)
                image.close()
            })

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD
            || event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) {
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
            || event.source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD) {
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
        if (event.source and InputDevice.SOURCE_DPAD != InputDevice.SOURCE_DPAD) {
            val x = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            val y = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
            press(BUTTON_CODE_UP, y.compareTo(-1.0f) == 0)
            press(BUTTON_CODE_DOWN, y.compareTo(1.0f) == 0)
            press(BUTTON_CODE_LEFT, x.compareTo(-1.0f) == 0)
            press(BUTTON_CODE_RIGHT, x.compareTo(1.0f) == 0)
        }
        if (event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
            && event.action == MotionEvent.ACTION_MOVE) {
            val x = getCenteredAxis(event, MotionEvent.AXIS_X)
            val y = getCenteredAxis(event, MotionEvent.AXIS_Y)
            if (x == 0.0f || y == 0.0f) {
                if (x != 0.0f) {
                    press(BUTTON_CODE_RIGHT, x > 0)
                    press(BUTTON_CODE_LEFT, x < 0)
                }
                if (y != 0.0f) {
                    press(BUTTON_CODE_UP, y < 0)
                    press(BUTTON_CODE_DOWN, y > 0)
                }
            } else {
                val sector = atan2(y, x) * 8 / PI.toFloat()  // Divide circle into sixteenths
                press(BUTTON_CODE_UP, -1 > sector && sector > -7)
                press(BUTTON_CODE_RIGHT, 3 > sector && sector > -3)
                press(BUTTON_CODE_DOWN, 7 > sector && sector > 1)
                press(BUTTON_CODE_LEFT, -5 > sector || sector > 5)
            }
        }
        return super.onGenericMotionEvent(event)
    }

    private fun gamepadPress(keyCode: Int, pressed: Boolean): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> press(BUTTON_CODE_B, pressed)
            KeyEvent.KEYCODE_BUTTON_B -> press(BUTTON_CODE_A, pressed)
            KeyEvent.KEYCODE_BUTTON_START -> press(BUTTON_CODE_START, pressed)
            KeyEvent.KEYCODE_BUTTON_SELECT -> press(BUTTON_CODE_SELECT, pressed)
            KeyEvent.KEYCODE_DPAD_UP -> press(BUTTON_CODE_UP, pressed)
            KeyEvent.KEYCODE_DPAD_DOWN -> press(BUTTON_CODE_DOWN, pressed)
            KeyEvent.KEYCODE_DPAD_LEFT -> press(BUTTON_CODE_LEFT, pressed)
            KeyEvent.KEYCODE_DPAD_RIGHT -> press(BUTTON_CODE_RIGHT, pressed)
            KeyEvent.KEYCODE_BUTTON_Y -> if (pressed) { toggleMenu(screen) }
            KeyEvent.KEYCODE_BUTTON_THUMBL -> if (pressed) { toggleTurbo() }
            KeyEvent.KEYCODE_BUTTON_L1 -> if (pressed) { onBackPressed() }
            else -> return false
        }
        return true
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
        setMeasuredDimension(160, 144)
        layoutParams = ViewGroup.LayoutParams(160, 144)
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
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
