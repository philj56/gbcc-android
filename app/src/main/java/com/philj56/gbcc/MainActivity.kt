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

import android.animation.*
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.animation.addListener
import androidx.core.content.ContextCompat
import androidx.core.view.forEach
import androidx.core.view.forEachIndexed
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import androidx.transition.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max


private const val IMPORT_REQUEST_CODE: Int = 10
private const val BACK_DELAY: Int = 2000

class MainActivity : AppCompatActivity() {

    private lateinit var files: ArrayList<File>
    private lateinit var adapter: FileAdapter
    private lateinit var currentDir: File
    private lateinit var baseDir: File

    private var nightMode: Boolean = false
    private var timeBackPressed: Long = 0

    private val toolbarTransition = CircularReveal()
        .setDuration(300)
        .setInterpolator(AccelerateDecelerateInterpolator())
    private val fileTransition = Slide(Gravity.END).setDuration(300)
    private val showTransition = ChangeBounds()
    private val deleteTransitionSet = TransitionSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        nightMode = applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (nightMode) {
            setTheme(R.style.AppThemeDark_NoActionBar)
        } else {
            setTheme(R.style.AppTheme_NoActionBar)
        }
        super.onCreate(savedInstanceState)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        baseDir = getExternalFilesDir(null) ?: filesDir
        currentDir = baseDir
        setContentView(R.layout.activity_main)

        // Setup the toolbars
        mainToolbar.setTitle(R.string.app_name)
        mainToolbar.inflateMenu(R.menu.main_menu)
        mainToolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
        fileToolbar.setTitle(R.string.create_folder)
        fileToolbar.inflateMenu(R.menu.file_menu)
        fileToolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
        fileToolbar.visibility = View.GONE

        toolbarTransition.addTarget(mainToolbar)
        toolbarTransition.addTarget(fileToolbar)

        val scene = Scene(fileList)
        scene.setEnterAction {
            fileList.forEach { view ->
                view.visibility = View.VISIBLE
            }
            updateFiles()
        }

        deleteTransitionSet.addListener(object: TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                Log.d("Selected", adapter.selected.toString())
                adapter.selected.forEach { position ->
                    val file = adapter.getItem(position)
                    files.remove(file)
                    file?.deleteRecursively()
                }
                clearSelection()
                fileList.forEach { view ->
                    showTransition.addTarget(view)
                }
                TransitionManager.go(scene, showTransition)
            }
        })

        deleteTransitionSet.addTransition(fileTransition)
        deleteTransitionSet.addTransition(toolbarTransition)
        deleteTransitionSet.ordering = TransitionSet.ORDERING_TOGETHER

        adapter = FileAdapter(this, R.layout.file_entry, R.id.fileEntry, files)
        fileList.adapter = adapter
        fileList.setOnItemClickListener{ _, _, position, _ ->
            if (adapter.selected.isEmpty()) {
                val file = fileList.adapter.getItem(position) as File
                if (file.isDirectory) {
                    currentDir = file
                    updateFiles()
                } else {
                    switchToGL(file.toString())
                }
            } else {
                val item = fileList.getChildAt(position - fileList.firstVisiblePosition)
                if (position in adapter.selected) {
                    adapter.selected.remove(position)
                    item.background.clearColorFilter()
                    if (adapter.selected.isEmpty()) {
                        TransitionManager.beginDelayedTransition(mainLayout, toolbarTransition)
                        clearSelection()
                    }
                } else {
                    adapter.selected.add(position)
                    item.background.colorFilter = PorterDuffColorFilter(
                        ContextCompat.getColor(this, R.color.fileSelected),
                        PorterDuff.Mode.SRC)
                }
                item.invalidateDrawable(item.background)
            }
        }
        fileList.setOnItemLongClickListener{ _, _, position, _ ->
            if (adapter.selected.isEmpty()) {
                val item = fileList.getChildAt(position - fileList.firstVisiblePosition)
                adapter.selected.add(position)
                item.background.colorFilter = PorterDuffColorFilter(
                    ContextCompat.getColor(this, R.color.fileSelected),
                    PorterDuff.Mode.SRC)
                item.invalidateDrawable(item.background)

                TransitionManager.beginDelayedTransition(mainLayout, toolbarTransition)

                fileToolbar.visibility = View.VISIBLE
                mainToolbar.visibility = View.GONE
            }
            return@setOnItemLongClickListener true
        }
    }

    override fun onPause() {
        clearSelection()
        super.onPause()
    }

    override fun onContentChanged() {
        super.onContentChanged()
        updateFiles()
    }

    private fun clearSelection() {
        if (adapter.selected.isNotEmpty()) {
            adapter.selected.clear()
            fileList.forEach { view ->
                view.background.clearColorFilter()
                view.invalidateDrawable(view.background)
            }
        }
        mainToolbar.visibility = View.VISIBLE
        fileToolbar.visibility = View.GONE
    }

    private fun updateFiles() {
        val curPath = currentDir.path.removePrefix(baseDir.path).replace("/", " / ")
        directoryTree.text = String.format(getString(R.string.directory_tree), curPath)
        val unsorted = currentDir.listFiles { file ->
            file.name.matches(Regex(".*\\.gbc?")) or file.isDirectory
        }
        files = unsorted?.sortedWith(
            compareBy(
                { !it.isDirectory },
                { it.name }
            )
        )?.toCollection(ArrayList()) ?: ArrayList()
        if (::adapter.isInitialized) {
            adapter.clear()
            adapter.addAll(files)
            adapter.notifyDataSetChanged()
        }
    }

    private fun onMenuItemClick(item: MenuItem): Boolean {

        when (item.itemId) {
            R.id.importItem -> performFileSearch()
            R.id.settingsItem -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.folderItem -> {
                val dialog = CreateFolderDialogFragment()
                dialog.show(supportFragmentManager, "")
            }
            R.id.cancelItem -> clearSelection()
            R.id.deleteItem -> {
                fileList.forEachIndexed { index, view ->
                    if (fileList.firstVisiblePosition + index in adapter.selected) {
                        fileTransition.addTarget(view)
                    }
                }
                val scene = Scene(fileList)
                scene.setEnterAction {
                    fileList.forEachIndexed { index, view ->
                        if (fileList.firstVisiblePosition + index in adapter.selected) {
                            view.visibility = View.GONE
                        }
                    }
                }
                TransitionManager.go(scene, deleteTransitionSet)
                //TransitionManager.beginDelayedTransition(mainLayout, deleteTransitionSet)
            }
        }
        return true
    }

    private fun switchToGL(filename: String) {
        val intent = Intent(this, GLActivity::class.java).apply {
            putExtra("file", filename)
        }
        startActivity(intent)
    }

    fun createFolder(name: String) {
        if (currentDir.resolve(name).mkdirs()) {
            updateFiles()
        } else {
            Toast.makeText(
                baseContext,
                getString(R.string.message_failed_create_folder).format(name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun performFileSearch() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
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
        Log.d("File", uri.toString())
        val iStream = contentResolver.openInputStream(uri)
        if (iStream == null) {
            runOnUiThread {
                Toast.makeText(
                    baseContext,
                    getString(R.string.message_failed_import).format(uri),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            val name = getFileName(uri)
            if (name == null) {
                runOnUiThread {
                    Toast.makeText(
                        baseContext,
                        getString(R.string.message_failed_name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
            if (!name.matches(Regex(".*\\.(gbc?|sav|s[0-9])"))) {
                runOnUiThread {
                    Toast.makeText(
                        baseContext,
                        getString(R.string.message_failed_import).format(name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
            val data = iStream.use { it.readBytes() }
            val file = File(getExternalFilesDir(null), name)
            FileOutputStream(file).use { it.write(data) }
            Log.i("Imported", file.name)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {

        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == IMPORT_REQUEST_CODE && resultCode == RESULT_OK && resultData != null) {
            val clipData = resultData.clipData
            // Run the import in the background to avoid blocking the UI
            AsyncTask.execute {
                if (clipData != null) {
                    if (clipData.itemCount >= 10) {
                        // Give the user some notification that an import is occurring
                        runOnUiThread {
                            Toast.makeText(
                                baseContext,
                                getString(R.string.message_importing).format(clipData.itemCount),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    for (i in 0 until clipData.itemCount) {
                        importFile(clipData.getItemAt(i).uri)
                    }
                } else {
                    resultData.data?.also { importFile(it) }
                }
                // updateFiles needs to be run on the main thread, however
                runOnUiThread { updateFiles() }
            }
        }
    }

    override fun onBackPressed() {
        if (currentDir != getExternalFilesDir(null)) {
            currentDir = currentDir.parentFile ?: filesDir
            updateFiles()
            return
        }

        if (timeBackPressed + BACK_DELAY > System.currentTimeMillis()) {
            super.onBackPressed()
            return
        }

        timeBackPressed = System.currentTimeMillis()

        Toast.makeText(baseContext, getString(R.string.message_repeat_back), Toast.LENGTH_SHORT).show()
    }
}

class FileAdapter(context: Context, resource: Int, textViewResourceId: Int, objects: List<File>)
    : ArrayAdapter<File>(context, resource, textViewResourceId, objects) {

    val selected = HashSet<Int>()
    private val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.fileEntry)
        val imageView = view.findViewById<ImageView>(R.id.fileIcon)

        if (position in selected) {
            view.background.colorFilter = PorterDuffColorFilter(
                ContextCompat.getColor(context, R.color.fileSelected),
                PorterDuff.Mode.SRC)
        } else {
            view.background.clearColorFilter()
        }
        if (textView.text.endsWith(".gbc")) {
            imageView.setImageResource(R.drawable.ic_file_gbc)
            imageView.clearColorFilter()
        } else if (textView.text.endsWith(".gb")) {
            imageView.setImageResource(R.drawable.ic_file_dmg)
            imageView.clearColorFilter()
        } else {
            imageView.setImageResource(R.drawable.ic_folder_white_34dp)
            if (nightMode) {
                imageView.setColorFilter(
                    Color.argb(179, 255, 255, 255),
                    PorterDuff.Mode.SRC_IN
                )
            } else {
                imageView.setColorFilter(
                    Color.argb(138, 0, 0, 0),
                    PorterDuff.Mode.SRC_IN
                )
            }
        }

        textView.text = File(textView.text.toString()).nameWithoutExtension
        return view
    }
}

class CreateFolderDialogFragment : DialogFragment() {
    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val textView = it.layoutInflater.inflate(R.layout.create_folder, null, false)
            val input = textView?.findViewById<EditText>(R.id.createFolderInput)

            val builder = AlertDialog.Builder(it)
            builder.setTitle(R.string.create_folder)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    if (activity is MainActivity) {
                        (activity as MainActivity).createFolder(input?.text.toString())
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setView(textView)

            val dialog = builder.create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            }

            input?.addTextChangedListener { editor ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = editor?.isNotBlank() ?: false
            }
            return dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}

class CircularReveal : Visibility() {
    override fun onAppear(
        sceneRoot: ViewGroup?,
        view: View?,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        if (view == null) {
            return super.onAppear(sceneRoot, view, startValues, endValues)
        }
        val animator = ViewAnimationUtils.createCircularReveal(
            view,
            view.width / 2,
            view.height / 2,
            0.0f,
            max(view.width, view.height).toFloat())
        view.alpha = 0.0f
        animator.addListener(
            onStart = { view.alpha = 1.0f }
        )
        return animator
    }

    override fun onDisappear(
        sceneRoot: ViewGroup?,
        view: View?,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator {
        if (view == null) {
            return super.onDisappear(sceneRoot, view, startValues, endValues)
        }
        return ViewAnimationUtils.createCircularReveal(
            view,
            view.width / 2,
            view.height / 2,
            max(view.width, view.height).toFloat(),
            0.0f)
    }
}