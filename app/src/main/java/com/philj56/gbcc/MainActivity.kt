package com.philj56.gbcc

import android.app.ListActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import java.io.File
import java.io.FileOutputStream

private const val IMPORT_REQUEST_CODE: Int = 42

class MainActivity : ListActivity() {

    private lateinit var files : ArrayList<File>
    private lateinit var adapter: ArrayAdapter<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        adapter = ArrayAdapter(this, R.layout.file_entry_layout, R.id.fileEntry, files)
        listView.adapter = adapter
        listView.setOnItemClickListener{ _, _, position, _ ->
            switchToGL(listView.adapter.getItem(position).toString())
        }
    }

    override fun onContentChanged() {
        super.onContentChanged()
        updateFiles()
    }

    private fun updateFiles() {
        files = getExternalFilesDir(null)?.listFiles { file ->
            file.name.matches(Regex(".*\\.gbc?")) or file.isDirectory
        }?.toCollection(ArrayList()) ?: ArrayList()
        if (::adapter.isInitialized) {
            adapter.clear()
            adapter.addAll(files.toMutableSet())
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
}
