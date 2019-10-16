package com.philj56.gbcc

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.min

class GLActivity : Activity() {

    private lateinit var filename: String
    private var resume = false
    private var dpadState = 0

    private external fun loadRom(file: String)
    private external fun quit()
    private external fun press(button: Int, pressed: Boolean)
    private external fun saveState(state: Int)
    private external fun loadState(state: Int)

    private fun vibrate(milliseconds: Long) {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(milliseconds)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        setContentView(R.layout.activity_gl)

        checkFiles()
        val bundle = intent.extras
        filename = bundle?.getString("file") ?: ""
        if (filename == "") {
            Log.e("GBCC", "No rom provided.")
            finish()
        } else {
            loadRom(filename)
        }

        if (savedInstanceState != null) {
            if (savedInstanceState.getBoolean("resume")) {
                loadState(10)
            }
        }

        setButtonId(findViewById(R.id.buttonA), 0)
        setButtonId(findViewById(R.id.buttonB), 1)
        setButtonId(findViewById(R.id.buttonStart), 2)
        setButtonId(findViewById(R.id.buttonSelect), 3)

        with(findViewById<View>(R.id.dpad)) {
            this.setOnTouchListener( View.OnTouchListener { view, motionEvent ->
                if (view != this) {
                    return@OnTouchListener false
                }
                val up = Rect(0, 0, width, height / 3)
                val down = Rect(0, 2 * height / 3, width, height)
                val left = Rect(0, 0, width / 3, height)
                val right = Rect(2 * width / 3, 0, width, height)
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
    }

    override fun onStart() {
        super.onStart()
        Log.i("GBCC", "START")
    }

    override fun onResume() {
        super.onResume()
        Log.i("GBCC", "RESUME")
        window.decorView.apply {
            systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
        if (resume) {
            loadRom(filename)
            loadState(10)
            resume = false
        }
    }



    override fun onPause() {
        super.onPause()
        Log.i("GBCC", "PAUSE")
        saveState(10)
        quit()
        resume = true
    }

    override fun onStop() {
        super.onStop()
        Log.i("GBCC", "STOP")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("GBCC", "DESTROY")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("resume", true)
    }

    private fun checkFiles() {
        val filePath = getExternalFilesDir(null)
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
    private val renderer: MyGLRenderer

    constructor(context: Context) : super(context) {
        setMeasuredDimension(160, 144)
        layoutParams = ViewGroup.LayoutParams(160, 144)
    }

    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet) {
        setMeasuredDimension(160, 144)
        layoutParams = ViewGroup.LayoutParams(160, 144)
    }

    init {
        // Create an OpenGL ES 3.0 context
        setEGLContextClientVersion(3)

        renderer = MyGLRenderer()

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(renderer)
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

class MyGLRenderer : GLSurfaceView.Renderer {

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // Set the background frame colour
        GLES30.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
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

    companion object {
        init {
            System.loadLibrary("gbcc")
        }
    }
}