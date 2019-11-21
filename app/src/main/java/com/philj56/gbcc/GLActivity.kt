package com.philj56.gbcc

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.*
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import kotlinx.android.synthetic.main.activity_gl.*
import java.io.File
import java.io.FileOutputStream
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min


private const val autoSaveState: Int = 10

class GLActivity : Activity(), SensorEventListener {

    private val handler = Handler()
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private lateinit var vibrator: Vibrator
    private lateinit var checkVibration: Runnable
    private lateinit var filename: String
    private var resume = false
    private var dpadState = 0

    private external fun loadRom(file: String, prefs: SharedPreferences)
    private external fun quit()
    private external fun press(button: Int, pressed: Boolean)
    private external fun saveState(state: Int)
    private external fun loadState(state: Int)
    private external fun checkVibrationFun(): Boolean
    private external fun updateAccelerometer(x: Float, y: Float)
    private external fun hasRumble(): Boolean
    private external fun hasAccelerometer(): Boolean


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


    private fun vibrate(milliseconds: Long) {
        if (milliseconds == 0L) {
            vibrator.cancel()
            return
        }
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(milliseconds)
        }
    }

    private fun setButtonId(view: View, button: Int) {
        view.setOnTouchListener(View.OnTouchListener { touchView, motionEvent ->
            if (touchView != view) {
                return@OnTouchListener false
            }
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    press(button, true)
                    vibrate(10)
                }
                MotionEvent.ACTION_UP -> {
                    press(button, false)
                }
            }
            return@OnTouchListener false
        })
    }

    private fun updateLayout(gbc: Boolean) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)

        val bgColor = when (gbc) {
            true -> Color.parseColor(prefs.getString("color", getString(R.string.gbcTeal)))
            false -> ContextCompat.getColor(this, R.color.dmgBackground)
        }
        findViewById<View>(R.id.layout).setBackgroundColor(bgColor)

        if (!gbc) {
            val screenBorderColor = ContextCompat.getColor(this, R.color.dmgScreenBorder)
            val borders = intArrayOf(
                R.id.screenBorderTop,
                R.id.screenBorderBottom,
                R.id.screenBorderLeft,
                R.id.screenBorderRight
            )

            borders.forEach { border ->
                findViewById<ImageView>(border).setColorFilter(
                    screenBorderColor,
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
            }

            findViewById<ImageButton>(R.id.buttonA).setImageResource(R.drawable.ic_button_ab_dmg)
            findViewById<ImageButton>(R.id.buttonB).setImageResource(R.drawable.ic_button_ab_dmg)

            val buttonStart = findViewById<ImageButton>(R.id.buttonStart)
            val buttonSelect = findViewById<ImageButton>(R.id.buttonSelect)

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        requestedOrientation = prefs.getString("orientation", "-1")?.toInt() ?: -1
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        setContentView(R.layout.activity_gl)

        val bundle = intent.extras
        filename = bundle?.getString("file") ?: ""
        if (filename == "") {
            Log.e("GBCC", "No rom provided.")
            finish()
        }

        updateLayout(
            when(prefs.getString("skin", "auto")) {
                "dmg" -> false
                "gbc" -> true
                else -> filename.endsWith("gbc")
            }
        )

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        checkFiles()

        if (savedInstanceState != null) {
            resume = resume || savedInstanceState.getBoolean("resume")
        }

        setButtonId(findViewById(R.id.buttonA), 0)
        setButtonId(findViewById(R.id.buttonB), 1)
        setButtonId(findViewById(R.id.buttonStart), 2)
        setButtonId(findViewById(R.id.buttonSelect), 3)

        dpad.setOnTouchListener( View.OnTouchListener { view, motionEvent ->
            if (view != dpad) {
                return@OnTouchListener false
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
                    press(4, up.contains(x, y))
                    press(5, down.contains(x, y))
                    press(6, left.contains(x, y))
                    press(7, right.contains(x, y))
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

                    if (lastState != dpadState) {
                        vibrate(10)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    dpadState = 0
                    press(4, false)
                    press(5, false)
                    press(6, false)
                    press(7, false)
                }
            }

            return@OnTouchListener true
        })
    }

    override fun onResume() {
        super.onResume()

        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.decorView.apply {
            systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
        loadRom(filename, PreferenceManager.getDefaultSharedPreferences(this))
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
    }


    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(checkVibration)
        saveState(autoSaveState)
        quit()
        resume = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("resume", true)
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

    private fun checkFiles() {
        val filePath = filesDir
        if (File("$filePath/shaders").exists()) {
            //return
        }
        assets.open("tileset.png").use { iStream ->
            val file = File("$filePath/tileset.png")
            FileOutputStream(file).use { it.write(iStream.readBytes()) }
        }
        assets.list("shaders")?.forEach { shader ->
            File("$filePath/shaders").mkdirs()
            assets.open("shaders/$shader").use { iStream ->
                val file = File("$filePath/shaders/$shader")
                FileOutputStream(file).use { it.write(iStream.readBytes()) }
            }
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

            renderer = MyGLRenderer(context)

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
}

class MyGLRenderer(_context: Context) : GLSurfaceView.Renderer {

    private val context: Context = _context

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // Set the background frame colour
        GLES30.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
        initWindow(PreferenceManager.getDefaultSharedPreferences(context))
    }

    override fun onDrawFrame(unused: GL10) {
        updateWindow()
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        resizeWindow(width, height)
    }

    private external fun initWindow(prefs: SharedPreferences)
    private external fun updateWindow()
    private external fun resizeWindow(width: Int, height: Int)

    companion object {
        init {
            System.loadLibrary("gbcc")
        }
    }
}