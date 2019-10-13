package com.philj56.gbcc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val READ_REQUEST_CODE: Int = 42

class GLActivity : Activity() {

    private lateinit var gLView: GLSurfaceView

    external fun loadRom(file: String)
    external fun quit()
    external fun press(x: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS, WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);

        // Create a GLSurfaceView instance and set it
        // as the ContentView for this Activity
        setContentView(R.layout.activity_gl)

        checkFiles()
        performFileSearch()
    }

    override fun onResume() {
        super.onResume()

        window.decorView.apply {
            systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        quit()
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

    private fun performFileSearch() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        startActivityForResult(intent, READ_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {

        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            resultData?.data?.also { uri ->
                Log.i("GBCC", "Uri: $uri")
                val iStream = contentResolver.openInputStream(uri)
                if (iStream == null) {
                    Log.e("GBCC", "Failed to read $uri")
                } else {
                    val data = iStream.use { it.readBytes() }
                    val file = File(getExternalFilesDir(null), "tmp.gbc")
                    FileOutputStream(file).use { it.write(data) }
                    Log.i("GBCC", file.toString())
                    loadRom(file.toString())
                }
            }
        }
    }

    fun pressA(view: View) {
        press(0)
    }

    fun pressB(view: View) {
        press(1)
    }
}

class MyGLSurfaceView : GLSurfaceView {
    private val renderer: MyGLRenderer

    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)

    init {
        // Create an OpenGL ES 3.0 context
        setEGLContextClientVersion(3)

        renderer = MyGLRenderer()

        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(renderer)
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

    external fun initWindow()
    external fun updateWindow()
    external fun resizeWindow(width: Int, height: Int)

    companion object {

        // Used to load the 'native-lib' library on application startup.
        init {
            System.loadLibrary("gbcc")
        }
    }
}
