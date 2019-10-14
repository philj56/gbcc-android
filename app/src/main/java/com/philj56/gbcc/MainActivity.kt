package com.philj56.gbcc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import java.io.File
import java.io.FileOutputStream
import java.net.URI

private const val READ_REQUEST_CODE: Int = 42

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    private fun switchToGL(filename: String) {
        val intent = Intent(this, GLActivity::class.java).apply {
            putExtra("file", filename)
        }
        startActivity(intent)
    }

    fun performFileSearch(view: View) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        startActivityForResult(intent, READ_REQUEST_CODE)
    }

    private fun getFileName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }

            val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            return cursor.getString(name)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {

        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            resultData?.data?.also { uri ->
                val iStream = contentResolver.openInputStream(uri)
                if (iStream == null) {
                    Log.e("GBCC", "Failed to read $uri")
                } else {
                    val data = iStream.use { it.readBytes() }
                    val name = getFileName(uri) ?: "tmp.gbc"
                    if (name == "tmp.gbc") {
                        Log.w("GBCC", "Failed to retrieve real file name, using $name")
                    }
                    val file = File(getExternalFilesDir(null), name)
                    FileOutputStream(file).use { it.write(data) }
                    Log.i("GBCC", file.toString())
                    switchToGL(file.toString())
                }
            }
        }
    }
}
