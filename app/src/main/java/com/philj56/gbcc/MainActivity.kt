package com.philj56.gbcc

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream

private const val IMPORT_REQUEST_CODE: Int = 42

class MainActivity : AppCompatActivity() {

    private lateinit var files: ArrayList<File>
    private lateinit var adapter: ArrayAdapter<File>
    private lateinit var currentDir: File
    private lateinit var baseDir: File

    override fun onCreate(savedInstanceState: Bundle?) {
        if (applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) {
            setTheme(R.style.AppThemeDark)
        } else {
            setTheme(R.style.AppTheme)
        }
        super.onCreate(savedInstanceState)
        Log.i("Internal", filesDir.toString())
        baseDir = getExternalFilesDir(null) ?: filesDir
        currentDir = baseDir
        setContentView(R.layout.activity_main)
        adapter = FileAdapter(this, R.layout.file_entry, R.id.fileEntry, files)
        fileList.adapter = adapter
        fileList.setOnItemClickListener{ _, _, position, _ ->
            val file = fileList.adapter.getItem(position) as File
            if (file.isDirectory) {
                currentDir = file
                updateFiles()
            } else {
                switchToGL(file.toString())
            }
        }
        val dpi = resources.displayMetrics.densityDpi
        Log.i("DPI", "$dpi")
    }

    override fun onContentChanged() {
        super.onContentChanged()
        updateFiles()
    }

    @SuppressLint("SetTextI18n")
    private fun updateFiles() {
        directoryTree.text = "Files" + currentDir.path.removePrefix(baseDir.path).replace("/", " / ")
        files = currentDir.listFiles { file ->
            file.name.matches(Regex(".*\\.gbc?")) or file.isDirectory
        }?.toCollection(ArrayList()) ?: ArrayList()
        files.sort()
        if (::adapter.isInitialized) {
            adapter.clear()
            adapter.addAll(files)
            adapter.notifyDataSetChanged()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        val id = item.itemId

        if (id == R.id.importItem) {
            performFileSearch()
        }

        return super.onOptionsItemSelected(item)
    }

    private fun switchToGL(filename: String) {
        val intent = Intent(this, GLActivity::class.java).apply {
            putExtra("file", filename)
        }
        startActivity(intent)
    }

    private fun performFileSearch() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }

    private fun getFileName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use null
            }

            val name = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val ret = cursor.getString(name)

            while (cursor.moveToNext()) {
                Log.i("GBCC", cursor.getString(name))
            }

            return ret
        }
    }

    private fun importFile(uri: Uri) {
        val iStream = contentResolver.openInputStream(uri)
        if (iStream == null) {
            Log.e("GBCC", "Failed to import $uri")
        } else {
            val data = iStream.use { it.readBytes() }
            val name = getFileName(uri) ?: "tmp.gbc"
            if (name == "tmp.gbc") {
                Log.w("GBCC", "Failed to retrieve real file name, using $name")
            }
            val file = File(getExternalFilesDir(null), name)
            FileOutputStream(file).use { it.write(data) }
            Log.i("Imported", file.toString())
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {

        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == IMPORT_REQUEST_CODE && resultCode == RESULT_OK && resultData != null) {
            val clipData = resultData.clipData
            if (clipData != null) {
                for (i in 0 until clipData.itemCount) {
                    importFile(clipData.getItemAt(i).uri)
                }
            } else {
                resultData.data?.also { importFile(it) }
            }
        }
        updateFiles()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentDir != getExternalFilesDir(null)) {
                currentDir = currentDir.parentFile ?: filesDir
                updateFiles()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

class FileAdapter(context: Context, resource: Int, textViewResourceId: Int, objects: List<File>)
    : ArrayAdapter<File>(context, resource, textViewResourceId, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.fileEntry)
        val imageView = view.findViewById<ImageView>(R.id.fileIcon)

        if (textView.text.endsWith(".gbc")) {
            imageView.setImageResource(R.drawable.ic_file_gbc)
        } else if (textView.text.endsWith(".gb")) {
            imageView.setImageResource(R.drawable.ic_file_dmg)
        } else {
            imageView.setImageResource(R.drawable.ic_folder_white_34dp)
        }

        textView.text = File(textView.text.toString()).nameWithoutExtension
        return view
    }
}