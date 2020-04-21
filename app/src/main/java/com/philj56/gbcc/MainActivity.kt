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

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.animation.addListener
import androidx.core.view.forEach
import androidx.core.view.forEachIndexed
import androidx.core.view.postDelayed
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import androidx.transition.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

private const val IMPORT_REQUEST_CODE: Int = 10
private const val EXPORT_REQUEST_CODE: Int = 11
private const val BACK_DELAY: Int = 2000

class MainActivity : AppCompatActivity() {

    enum class SelectionMode {
        NORMAL, MOVE, DELETE, SELECT
    }

    private var selectionMode = SelectionMode.NORMAL
    private val moveSelection = mutableListOf<File>()
    private lateinit var files: ArrayList<File>
    private lateinit var adapter: FileAdapter
    private lateinit var currentDir: File
    private lateinit var baseDir: File

    private var timeBackPressed: Long = 0

    private val toolbarTransition = CircularReveal()
        .setDuration(300)
        .setInterpolator(AccelerateDecelerateInterpolator())
    private val fileTransition = TransitionSet()
    private val deleteTransitionSet = TransitionSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        AppCompatDelegate.setDefaultNightMode(
            when (PreferenceManager.getDefaultSharedPreferences(this).getString("night_mode", null)) {
                "on" -> AppCompatDelegate.MODE_NIGHT_YES
                "off" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        super.onCreate(savedInstanceState)
        baseDir = getExternalFilesDir(null) ?: filesDir
        currentDir = baseDir
        setContentView(R.layout.activity_main)

        // Set up the toolbars
        mainToolbar.inflateMenu(R.menu.main_menu)
        mainToolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
        fileToolbar.inflateMenu(R.menu.file_menu)
        fileToolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
        fileToolbar.setNavigationOnClickListener { clearSelection() }
        moveToolbar.inflateMenu(R.menu.move_menu)
        moveToolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
        moveToolbar.setNavigationOnClickListener { clearSelection() }

        toolbarTransition.addTarget(mainToolbar)
        toolbarTransition.addTarget(fileToolbar)
        toolbarTransition.addTarget(moveToolbar)

        fileTransition.addTransition(SlideShrink().setDuration(300))
        fileTransition.ordering = TransitionSet.ORDERING_TOGETHER

        deleteTransitionSet.addListener(object: TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                adapter.selected.forEach { file ->
                    files.remove(file)
                    file.deleteRecursively()
                }
                clearSelection()
                updateFiles()
                fileList.forEach { view ->
                    view.clearAnimation()
                    view.layoutParams.height = 0
                    view.translationX = 0.0f
                    view.requestLayout()
                }
            }
        })

        deleteTransitionSet.addTransition(fileTransition)
        deleteTransitionSet.addTransition(toolbarTransition)
        deleteTransitionSet.ordering = TransitionSet.ORDERING_TOGETHER

        adapter = FileAdapter(this, R.layout.file_entry, R.id.fileEntry, files)
        fileList.adapter = adapter
        fileList.setOnItemClickListener{ _, _, position, _ ->
            val file = fileList.adapter.getItem(position) as File
            when (selectionMode) {
                SelectionMode.DELETE -> {}
                SelectionMode.MOVE -> {
                    if (file.isDirectory) {
                        currentDir = file
                        updateFiles()
                    }
                }
                SelectionMode.NORMAL -> {
                    if (file.isDirectory) {
                        currentDir = file
                        updateFiles()
                    } else {
                        switchToGL(file.toString())
                    }
                }
                SelectionMode.SELECT -> {
                    val item = fileList.getChildAt(position - fileList.firstVisiblePosition)
                    if (file in adapter.selected) {
                        adapter.selected.remove(file)
                        if (adapter.selected.isEmpty()) {
                            TransitionManager.beginDelayedTransition(mainLayout, toolbarTransition)
                            clearSelection()
                        }
                    } else {
                        adapter.selected.add(file)
                    }
                    item.isActivated = file in adapter.selected
                    item.invalidateDrawable(item.background)
                }
            }
        }
        fileList.setOnItemLongClickListener{ _, _, position, _ ->
            val file = fileList.adapter.getItem(position) as File
            if (adapter.selected.isEmpty()) {
                selectionMode = SelectionMode.SELECT
                val item = fileList.getChildAt(position - fileList.firstVisiblePosition)
                adapter.selected.add(file)
                item.isActivated = true
                item.invalidateDrawable(item.background)

                TransitionManager.beginDelayedTransition(mainLayout, toolbarTransition)

                fileToolbar.visibility = View.VISIBLE
                mainToolbar.visibility = View.GONE
            }
            return@setOnItemLongClickListener true
        }
    }

    override fun onPause() {
        supportFragmentManager.fragments.forEach {
            if (it is DialogFragment) {
                it.dismissAllowingStateLoss()
            }
        }
        clearSelection()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        delegate.applyDayNight()
    }

    override fun onContentChanged() {
        super.onContentChanged()
        updateFiles()
    }

    private fun clearSelection() {
        selectionMode = SelectionMode.NORMAL
        if (adapter.selected.isNotEmpty()) {
            adapter.selected.clear()
            fileList.forEach { view ->
                view.isActivated = false
                view.invalidateDrawable(view.background)
            }
        }
        moveSelection.clear()
        fileTransition.targets.clear()
        mainToolbar.visibility = View.VISIBLE
        fileToolbar.visibility = View.GONE
        moveToolbar.visibility = View.GONE
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
            R.id.exportItem -> selectExportDir()
            R.id.deleteItem -> {
                selectionMode = SelectionMode.DELETE
                val dialog = ConfirmDeleteDialogFragment(adapter.selected.count())
                dialog.show(supportFragmentManager, "")
            }
            R.id.moveItem -> {
                selectionMode = SelectionMode.MOVE
                moveSelection.addAll(0, files.filter { it in adapter.selected })
                moveToolbar.visibility = View.VISIBLE
                fileToolbar.visibility = View.GONE
                mainToolbar.visibility = View.GONE
            }
            R.id.confirmMoveItem -> {
                AsyncTask.execute {
                    moveSelection.forEach {file ->
                        val newFile = File(currentDir, file.name)
                        file.renameTo(newFile)
                    }
                    runOnUiThread {
                        clearSelection()
                        updateFiles()
                    }
                }
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

    fun performDelete() {
        fileList.forEachIndexed { index, view ->
            if (files[fileList.firstVisiblePosition + index] in adapter.selected) {
                fileTransition.addTarget(view)
            }
        }
        val scene = Scene(fileList)
        scene.setEnterAction {
            fileList.forEachIndexed { index, view ->
                if (files[fileList.firstVisiblePosition + index] in adapter.selected) {
                    view.layoutParams.height = 1
                }
            }
        }
        TransitionManager.go(scene, deleteTransitionSet)
        fileList.invalidate()
    }

    fun cancelDelete() {
        selectionMode = SelectionMode.SELECT
    }

    private fun performFileSearch() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
        }
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, IMPORT_REQUEST_CODE)
    }

    private fun selectExportDir() {
        val saveDir = filesDir.resolve("saves")
        if (!saveDir.isDirectory || saveDir.list()?.isEmpty() == true) {
            Toast.makeText(baseContext, getString(R.string.message_no_export), Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        intent.putExtra(Intent.EXTRA_TITLE, "saves.zip")
        startActivityForResult(intent, EXPORT_REQUEST_CODE)
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
            val dir = if (name.endsWith("sav")) {
                filesDir.resolve("saves")
            } else {
                currentDir
            }
            val file = File(dir, name)
            FileOutputStream(file).use { it.write(data) }
            Log.i("Imported", file.name)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {

        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode != RESULT_OK || resultData == null) {
            return
        }
        if (requestCode == IMPORT_REQUEST_CODE) {
            val clipData = resultData.clipData
            // Run the import in the background to avoid blocking the UI
            AsyncTask.execute {
                if (clipData != null) {
                    if (clipData.itemCount >= 10) {
                        // Give the user some notification that an import is occurring
                        runOnUiThread {
                            Toast.makeText(
                                baseContext,
                                resources.getQuantityString(R.plurals.message_importing, clipData.itemCount, clipData.itemCount),
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
        } else if (requestCode == EXPORT_REQUEST_CODE) {
            val data = resultData.data ?: return
            AsyncTask.execute {
                var count = 0
                filesDir.resolve("saves").walk().forEach { file ->
                    val zip = ZipOutputStream(contentResolver.openOutputStream(data))
                    if (file.extension == "sav") {
                        count += 1
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().copyTo(zip)
                        zip.closeEntry()
                    }
                }
                runOnUiThread {
                    Toast.makeText(
                        baseContext,
                        resources.getQuantityString(R.plurals.message_export_complete, count, count),
                        Toast.LENGTH_SHORT
                    ).show() }
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

class FileAdapter(context: Context, resource: Int, textViewResourceId: Int, private val objects: List<File>)
    : ArrayAdapter<File>(context, resource, textViewResourceId, objects) {

    val selected = HashSet<File>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.fileEntry)
        val imageView = view.findViewById<ImageView>(R.id.fileIcon)
        val file = objects[position]

        view.isActivated = file in selected
        when (file.extension) {
            "gbc" -> {
                imageView.setImageResource(R.drawable.ic_file_gbc)
                imageView.clearColorFilter()
            }
            "gb" -> {
                imageView.setImageResource(R.drawable.ic_file_dmg)
                imageView.clearColorFilter()
            }
            else -> {
                imageView.setImageResource(R.drawable.ic_folder_white_34dp)
            }
        }

        textView.text = file.nameWithoutExtension
        return view
    }
}

class ConfirmDeleteDialogFragment(private val count: Int) : AppCompatDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            val title = resources.getQuantityString(R.plurals.delete_confirmation, count, count)
            builder.setTitle(title)
                .setPositiveButton(R.string.delete) { _, _ ->
                    if (activity is MainActivity) {
                        (activity as MainActivity).performDelete()
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    if (activity is MainActivity) {
                        (activity as MainActivity).cancelDelete()
                    } }
                .create()
        } ?: throw IllegalStateException("Activity cannot be null")
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
            input?.setOnFocusChangeListener { v, hasFocus ->
                v.postDelayed(50) {
                    if (hasFocus) {
                        val imm =
                            context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(v, 0)
                    }
                }
            }
            input?.requestFocus()

            return dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}

class SlideShrink : Transition() {

    companion object {
        private const val PROPNAME_HEIGHT = "com.philj56.gbcc.slideshrink:SlideShrink:height"
        private const val PROPNAME_LAYOUT_HEIGHT = "com.philj56.gbcc.slideshrink:SlideShrink:layoutParams.height"
    }
    override fun captureStartValues(transitionValues: TransitionValues) {
        captureValues(transitionValues)
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        captureValues(transitionValues)
    }

    private fun captureValues(transitionValues: TransitionValues) {
        val view = transitionValues.view
        transitionValues.values[PROPNAME_HEIGHT] = view.height
        transitionValues.values[PROPNAME_LAYOUT_HEIGHT] = view.layoutParams.height
    }

    override fun createAnimator(
        sceneRoot: ViewGroup,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator? {
        if (startValues == null || endValues == null) {
            return null
        }
        val view = endValues.view
        val startHeight = startValues.values[PROPNAME_HEIGHT] as Int
        val endLayoutHeight = endValues.values[PROPNAME_LAYOUT_HEIGHT] as Int

        if (startHeight == endLayoutHeight) {
            return null
        }

        val animator = ValueAnimator.ofInt(startHeight, endLayoutHeight)
        animator.addUpdateListener {
            view.translationX = it.animatedFraction * view.width
            val offsetTime = min((1 - it.animatedFraction) * 2, 1.0f)
            view.layoutParams.height = max(offsetTime * startHeight, 1.0f).toInt()
            view.requestLayout()
        }

        return animator
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