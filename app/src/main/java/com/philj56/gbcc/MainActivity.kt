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
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.core.animation.addListener
import androidx.core.view.forEach
import androidx.core.view.forEachIndexed
import androidx.core.view.postDelayed
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import androidx.transition.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.databinding.ActivityMainBinding
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.IllegalArgumentException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.collections.ArrayList
import kotlin.collections.HashSet
import kotlin.math.max
import kotlin.math.min

private const val IMPORT_REQUEST_CODE: Int = 10
private const val EXPORT_REQUEST_CODE: Int = 11
private const val BACK_DELAY: Int = 2000
private const val SAVE_DIR: String = "saves"
private const val IMPORTED_SAVE_SUBDIR: String = "imported"

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
    private lateinit var binding: ActivityMainBinding

    private var timeBackPressed: Long = 0

    private val toolbarTransition = CircularReveal()
        .setDuration(300)
        .setInterpolator(AccelerateDecelerateInterpolator())
    private val fileTransition = TransitionSet()
    private val deleteTransitionSet = TransitionSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        AppCompatDelegate.setDefaultNightMode(
            when (PreferenceManager.getDefaultSharedPreferences(this)
                .getString("night_mode", null)) {
                "on" -> AppCompatDelegate.MODE_NIGHT_YES
                "off" -> AppCompatDelegate.MODE_NIGHT_NO
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
        super.onCreate(savedInstanceState)
        baseDir = getExternalFilesDir(null) ?: filesDir
        currentDir = baseDir
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if the version has changed since last launch, and perform some setup if so
        Thread {
            val lastLaunchVersion = filesDir.resolve("lastLaunchVersion")
            if (!lastLaunchVersion.exists()
                || !lastLaunchVersion.readText().startsWith("${BuildConfig.VERSION_CODE}")
            ) {
                lastLaunchVersion.createNewFile()
                lastLaunchVersion.writeText("${BuildConfig.VERSION_CODE}\n")

                getExternalFilesDir(null)?.resolve("Tutorial.gbc")?.outputStream()?.use {
                    assets.open("Tutorial.gbc").copyTo(it)
                }
                assets.open("print.wav").use { iStream ->
                    val file = File("$filesDir/print.wav")
                    FileOutputStream(file).use { iStream.copyTo(it) }
                }
                assets.list("shaders")?.forEach { shader ->
                    File("$filesDir/shaders").mkdirs()
                    assets.open("shaders/$shader").use { iStream ->
                        val file = File("$filesDir/shaders/$shader")
                        FileOutputStream(file).use { iStream.copyTo(it) }
                    }
                }
                runOnUiThread { updateFiles() }
            }
        }.start()

        // Set up the toolbars
        binding.mainToolbar.inflateMenu(R.menu.main_menu)
        binding.mainToolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
        binding.fileToolbar.inflateMenu(R.menu.file_menu)
        binding.fileToolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
        binding.fileToolbar.setNavigationOnClickListener { clearSelection() }
        binding.moveToolbar.inflateMenu(R.menu.move_menu)
        binding.moveToolbar.setOnMenuItemClickListener { item -> onMenuItemClick(item) }
        binding.moveToolbar.setNavigationOnClickListener { clearSelection() }

        toolbarTransition.addTarget(binding.mainToolbar)
        toolbarTransition.addTarget(binding.fileToolbar)
        toolbarTransition.addTarget(binding.moveToolbar)

        fileTransition.addTransition(SlideShrink().setDuration(300))
        fileTransition.ordering = TransitionSet.ORDERING_TOGETHER

        deleteTransitionSet.addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                adapter.selected.forEach { file ->
                    files.remove(file)
                    file.deleteRecursively()
                }
                clearSelection()
                updateFiles()
                binding.fileList.forEach { view ->
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

        adapter = FileAdapter(this, R.layout.entry_file, R.id.fileEntry, files)
        binding.fileList.adapter = adapter
        binding.fileList.setOnItemClickListener { _, _, position, _ ->
            val file = binding.fileList.adapter.getItem(position) as File
            when (selectionMode) {
                SelectionMode.DELETE -> {
                }
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
                    val item = binding.fileList.getChildAt(position - binding.fileList.firstVisiblePosition)
                    if (file in adapter.selected) {
                        adapter.selected.remove(file)
                        if (adapter.selected.isEmpty()) {
                            TransitionManager.beginDelayedTransition(binding.mainLayout, toolbarTransition)
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
        binding.fileList.setOnItemLongClickListener { _, _, position, _ ->
            val file = binding.fileList.adapter.getItem(position) as File
            if (adapter.selected.isEmpty()) {
                selectionMode = SelectionMode.SELECT
                val item = binding.fileList.getChildAt(position - binding.fileList.firstVisiblePosition)
                adapter.selected.add(file)
                item.isActivated = true
                item.invalidateDrawable(item.background)

                if (file.isDirectory) {
                    showDirectoryActionsDialog(file)
                } else {
                    showRomActionsDialog(file)
                }
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

    private fun beginSelection() {
        TransitionManager.beginDelayedTransition(binding.mainLayout, toolbarTransition)
        binding.fileToolbar.visibility = View.VISIBLE
        binding.mainToolbar.visibility = View.GONE
    }

    fun clearSelection() {
        selectionMode = SelectionMode.NORMAL
        if (adapter.selected.isNotEmpty()) {
            adapter.selected.clear()
            binding.fileList.forEach { view ->
                view.isActivated = false
                view.invalidateDrawable(view.background)
            }
        }
        moveSelection.clear()
        fileTransition.targets.clear()
        binding.mainToolbar.visibility = View.VISIBLE
        binding.fileToolbar.visibility = View.GONE
        binding.moveToolbar.visibility = View.GONE
    }

    private fun updateFiles() {
        val curPath = currentDir.path.removePrefix(baseDir.path).replace("/", " / ")
        binding.directoryTree.text = getString(R.string.directory_tree, curPath)
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
                val dialog = EditTextDialogFragment(R.string.create_folder, "") { name ->
                    createFolder(name)
                }
                dialog.show(supportFragmentManager, "")
            }
            R.id.exportItem -> selectExportDir()
            R.id.deleteItem -> {
                selectionMode = SelectionMode.DELETE
                val count = adapter.selected.count()
                MaterialAlertDialogBuilder(this)
                    .setMessage(resources.getQuantityString(R.plurals.delete_confirmation, count, count))
                    .setPositiveButton(R.string.delete) { _, _ -> performDelete() }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> clearSelection() }
                    .setOnCancelListener { clearSelection() }
                    .show()
            }
            R.id.moveItem -> {
                selectionMode = SelectionMode.MOVE
                moveSelection.addAll(0, files.filter { it in adapter.selected })
                binding.moveToolbar.visibility = View.VISIBLE
                binding.fileToolbar.visibility = View.GONE
                binding.mainToolbar.visibility = View.GONE
            }
            R.id.confirmMoveItem -> {
                Thread {
                    moveSelection.forEach { file ->
                        val newFile = File(currentDir, file.name)
                        file.renameTo(newFile)
                    }
                    runOnUiThread {
                        clearSelection()
                        updateFiles()
                    }
                }.start()
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

    private fun createFolder(name: String) {
        if (currentDir.resolve(name).mkdirs()) {
            updateFiles()
        } else {
            Toast.makeText(
                baseContext,
                getString(R.string.message_failed_create_folder, name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun performDelete() {
        binding.fileList.forEachIndexed { index, view ->
            if (files[binding.fileList.firstVisiblePosition + index] in adapter.selected) {
                fileTransition.addTarget(view)
            }
        }
        val scene = Scene(binding.fileList)
        scene.setEnterAction {
            binding.fileList.forEachIndexed { index, view ->
                if (files[binding.fileList.firstVisiblePosition + index] in adapter.selected) {
                    view.layoutParams.height = 1
                }
            }
        }
        TransitionManager.go(scene, deleteTransitionSet)
        binding.fileList.invalidate()
    }

    private fun showDirectoryActionsDialog(file: File) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(R.layout.dialog_directory_actions)
            .setOnCancelListener { clearSelection() }
            .show()

        dialog.findViewById<Button>(R.id.buttonSelectItems)?.setOnClickListener {
            beginSelection()
            dialog.dismiss()
        }
        dialog.findViewById<Button>(R.id.buttonRenameDirectory)?.setOnClickListener {
            showRenameDialog(file)
            dialog.dismiss()
        }
    }

    private fun showRomActionsDialog(file: File) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(R.layout.dialog_rom_actions)
            .setOnCancelListener { clearSelection() }
            .show()

        dialog.findViewById<Button>(R.id.buttonSelectItems)?.setOnClickListener {
            beginSelection()
            dialog.dismiss()
        }
        dialog.findViewById<Button>(R.id.buttonRenameRom)?.setOnClickListener {
            showRenameDialog(file)
            dialog.dismiss()
        }
        dialog.findViewById<Button>(R.id.buttonDeleteSave)?.setOnClickListener {
            showSaveDeleteDialog()
            dialog.dismiss()
        }
        dialog.findViewById<Button>(R.id.buttonEditCheats)?.setOnClickListener {
            val intent = Intent(this, CheatActivity::class.java).apply {
                putExtra("file", file.name)
            }
            startActivity(intent)
            dialog.dismiss()
        }
        dialog.findViewById<Button>(R.id.buttonEditConfig)?.setOnClickListener {
            val intent = Intent(this, RomConfigActivity::class.java).apply {
                putExtra("file", file.name)
            }
            startActivity(intent)
            dialog.dismiss()
        }
    }

    private fun showRenameDialog(file: File) {
        selectionMode = SelectionMode.NORMAL
        val dialog = EditTextDialogFragment(R.string.rename_rom, file.nameWithoutExtension) { name ->
            if (file.isDirectory) {
                file.renameTo(File("${file.parent}/$name"))
                updateFiles()
                return@EditTextDialogFragment
            }
            val configFile = filesDir.resolve("config/${file.nameWithoutExtension}.cfg")
            val cheatFile = filesDir.resolve("config/${file.nameWithoutExtension}.cheats")
            val savFile = filesDir.resolve("saves/${file.nameWithoutExtension}.sav")
            if (configFile.exists()) {
                configFile.renameTo(filesDir.resolve("config/$name.cfg"))
            }
            if (cheatFile.exists()) {
                cheatFile.renameTo(filesDir.resolve("config/$name.cheats"))
            }
            if (savFile.exists()) {
                savFile.renameTo(filesDir.resolve("saves/$name.sav"))
            }
            for (n in 0..10) {
                val state = filesDir.resolve("saves/${file.nameWithoutExtension}.s$n")
                if (state.exists()) {
                    state.renameTo(filesDir.resolve("saves/$name.s$n"))
                }
            }
            file.renameTo(File("${file.parent}/$name.${file.extension}"))
            updateFiles()
        }
        dialog.setOnDismissListener { clearSelection() }
        dialog.show(supportFragmentManager, "")
    }

    private fun showSaveDeleteDialog() {
        selectionMode = SelectionMode.DELETE
        val count = adapter.selected.count()
        MaterialAlertDialogBuilder(this)
            .setMessage(resources.getQuantityString(R.plurals.delete_save_confirmation, count, count))
            .setPositiveButton(R.string.delete) { _, _ -> performSaveDelete() }
            .setNegativeButton(android.R.string.cancel) { _, _ -> clearSelection() }
            .setOnCancelListener { clearSelection() }
            .show()
    }

    private fun performSaveDelete() {
        val saveDir = filesDir.resolve(SAVE_DIR)
        adapter.selected.forEach { file ->
            saveDir.listFiles { _, name ->
                name.startsWith(file.nameWithoutExtension)
            }?.forEach { save ->
                save.delete()
            }
        }
        clearSelection()
    }

    private fun performFileSearch() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try {
            startActivityForResult(intent, IMPORT_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                baseContext,
                getString(R.string.message_no_files_app),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    @SuppressLint("SimpleDateFormat")
    private fun selectExportDir() {
        val saveDir = filesDir.resolve(SAVE_DIR)
        if (!saveDir.isDirectory || (saveDir.list()?.none { it.endsWith(".sav") } == true)) {
            Toast.makeText(baseContext, getString(R.string.message_no_export), Toast.LENGTH_SHORT)
                .show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        val date = SimpleDateFormat("yyyyMMdd").format(Date())
        intent.putExtra(Intent.EXTRA_TITLE, "gbcc_saves_$date.zip")
        try {
            startActivityForResult(intent, EXPORT_REQUEST_CODE)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                baseContext,
                getString(R.string.message_no_files_app),
                Toast.LENGTH_SHORT
            ).show()
        }
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
        fun doCopy(input: InputStream, name: String) {
            val dir = if (name.endsWith("sav")) {
                filesDir.resolve(SAVE_DIR).resolve(IMPORTED_SAVE_SUBDIR)
            } else {
                currentDir
            }
            val file = dir.resolve(name)
            file.parentFile?.mkdirs()
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        fun importZipWithCharset(iStream: InputStream, charset: Charset) : Exception? {
            try {
                if (Build.VERSION.SDK_INT >= 24) {
                    ZipInputStream(iStream, charset)
                } else {
                    ZipInputStream(iStream)
                }.use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name.matches(Regex(".*\\.(gbc?|sav|s[0-9])"))) {
                            doCopy(zip, entry.name)
                        }
                        entry = zip.nextEntry
                    }
                }
            } catch (e: IllegalArgumentException) {
                return e
            } catch (e: ZipException) {
                return e
            }
            return null
        }

        fun showImportFailToast() {
            runOnUiThread {
                Toast.makeText(
                    baseContext,
                    getString(R.string.message_failed_import, uri),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val name = getFileName(uri)
        when {
            name == null -> {
                runOnUiThread {
                    Toast.makeText(
                        baseContext,
                        getString(R.string.message_failed_name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            name.matches(Regex(".*\\.(gbc?|sav|s[0-9])")) -> {
                val iStream = contentResolver.openInputStream(uri)
                if (iStream == null) {
                    showImportFailToast()
                    return
                }
                doCopy(iStream, name)
                iStream.close()
            }
            contentResolver.getType(uri).equals("application/zip")
                    or contentResolver.getType(uri).equals("application/x-zip-compressed")
                    or name.endsWith("zip") -> {
                val iStream = contentResolver.openInputStream(uri)
                if (iStream == null) {
                    showImportFailToast()
                    return
                }
                // We have no way of knowing what encoding was used to create the zip,
                // so just try utf-8, then cp437, then give up
                var e = importZipWithCharset(iStream, StandardCharsets.UTF_8)
                if (e != null) {
                    // importZipWithCharset closes the input stream, so we have to open a new one
                    val iStream2 = contentResolver.openInputStream(uri)
                    if (iStream2 == null) {
                        showImportFailToast()
                        return
                    }
                    e = importZipWithCharset(iStream2, Charset.forName("Cp437"))
                    if (e != null) {
                        runOnUiThread {
                            MaterialAlertDialogBuilder(this).run {
                                setTitle(
                                    resources.getString(
                                        R.string.error_zip_title,
                                        e.message
                                    )
                                )
                                setMessage(R.string.error_zip_body)
                                setPositiveButton(android.R.string.ok, null)
                                show()
                            }
                        }
                    }
                }
            }
            else -> {
                runOnUiThread {
                    Toast.makeText(
                        baseContext,
                        getString(R.string.message_failed_import, name),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
            Thread {
                if (clipData != null) {
                    if (clipData.itemCount >= 10) {
                        // Give the user some notification that an import is occurring
                        runOnUiThread {
                            Toast.makeText(
                                baseContext,
                                resources.getQuantityString(
                                    R.plurals.message_importing,
                                    clipData.itemCount,
                                    clipData.itemCount
                                ),
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

                // Go through the imported saves and prompt to overwrite if they already exist
                val existing = ArrayList<File>()
                val saveDir = filesDir.resolve(SAVE_DIR)
                val importDir = saveDir.resolve(IMPORTED_SAVE_SUBDIR)
                for (file in importDir.walk()) {
                    if (file == importDir) {
                        continue
                    }
                    val dest = saveDir.resolve(file.name)
                    if (dest.exists()) {
                        existing.add(file)
                    } else {
                        file.renameTo(dest)
                    }
                }
                if (existing.size > 0) {
                    existing.sort()
                    val dialog = ImportOverwriteDialogFragment(existing)
                    dialog.show(supportFragmentManager, "")
                } else {
                    importDir.delete()
                }
            }.start()
        } else if (requestCode == EXPORT_REQUEST_CODE) {
            val data = resultData.data ?: return
            Thread {
                val saveDir = filesDir.resolve(SAVE_DIR)
                val saves = saveDir.listFiles { path -> path.extension == "sav" }
                    ?: return@Thread
                contentResolver.openOutputStream(data).use { outputStream ->
                    ZipOutputStream(outputStream).use { zip ->
                        saves.forEach { file ->
                            zip.putNextEntry(ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
                runOnUiThread {
                    Toast.makeText(
                        baseContext,
                        resources.getQuantityString(
                            R.plurals.message_export_complete,
                            saves.size,
                            saves.size
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.start()
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

        Toast.makeText(baseContext, getString(R.string.message_repeat_back), Toast.LENGTH_SHORT)
            .show()
    }
}

class FileAdapter(
    context: Context,
    resource: Int,
    textViewResourceId: Int,
    private val objects: List<File>
) : ArrayAdapter<File>(context, resource, textViewResourceId, objects) {

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

class ImportOverwriteAdapter(
    context: Context,
    resource: Int,
    textViewResourceId: Int,
    private val objects: List<File>
) : ArrayAdapter<File>(context, resource, textViewResourceId, objects) {

    val selected = HashSet<File>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = super.getView(position, convertView, parent)
        val textView: CheckedTextView = view.findViewById(R.id.importOverwriteText)
        val file: File = objects[position]

        textView.isChecked = file in selected

        textView.text = file.nameWithoutExtension
        return view
    }
}

class EditTextDialogFragment(private val title: Int, private val initialText: String, private val onConfirm: (String) -> Unit) : DialogFragment() {
    private var onDismissListener: (() -> Unit)? = null

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val textView = it.layoutInflater.inflate(R.layout.dialog_create_folder, null, false)
            val input = textView?.findViewById<EditText>(R.id.createFolderInput)
            input?.setText(initialText)

            val builder = MaterialAlertDialogBuilder(it)
            builder.setTitle(title)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    onConfirm(input?.text.toString())
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setView(textView)

            val dialog = builder.create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            }

            input?.addTextChangedListener { editor ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                    editor?.isNotBlank() ?: false
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

    override fun onDismiss(dialog: DialogInterface) {
        onDismissListener?.invoke()
        super.onDismiss(dialog)
    }

    fun setOnDismissListener(listener: () -> Unit) {
        onDismissListener = listener
    }
}

class ImportOverwriteDialogFragment(private val files: ArrayList<File>) : DialogFragment() {
    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val adapter = ImportOverwriteAdapter(requireContext(), R.layout.entry_import_overwrite, R.id.importOverwriteText, files)
            adapter.selected.addAll(files)
            val view = it.layoutInflater.inflate(R.layout.dialog_import_overwrite, null, false)
            val listView = view.findViewById<ListView>(R.id.listView)
            listView.adapter = adapter
            listView.setOnItemClickListener { _, _, position, _ ->
                val file = listView.adapter.getItem(position) as File
                val item = listView.getChildAt(position - listView.firstVisiblePosition)
                if (file in adapter.selected) {
                    adapter.selected.remove(file)
                } else {
                    adapter.selected.add(file)
                }
                item.findViewById<CheckedTextView>(R.id.importOverwriteText).isChecked = file in adapter.selected
            }

            val saveDir = requireContext().filesDir.resolve("saves")
            fun deleteFiles() {
                saveDir.resolve(IMPORTED_SAVE_SUBDIR).deleteRecursively()
            }
            val builder = MaterialAlertDialogBuilder(it)
            builder.setTitle(resources.getString(R.string.overwrite_confirmation))
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    Thread {
                        adapter.selected.forEach { file ->
                            val dest = saveDir.resolve(file.name)
                            dest.delete()
                            file.renameTo(dest)
                        }
                        activity?.runOnUiThread {
                            Toast.makeText(
                                context,
                                resources.getQuantityString(
                                    R.plurals.message_imported,
                                    adapter.selected.size,
                                    adapter.selected.size
                                ),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        deleteFiles()
                    }.start()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> deleteFiles() }
                .setView(view)
                .setOnDismissListener { deleteFiles() }
            return builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}

class SlideShrink : Transition() {

    companion object {
        private const val PROPNAME_HEIGHT = "com.philj56.gbcc.slideshrink:SlideShrink:height"
        private const val PROPNAME_LAYOUT_HEIGHT =
            "com.philj56.gbcc.slideshrink:SlideShrink:layoutParams.height"
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
            max(view.width, view.height).toFloat()
        )
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
            0.0f
        )
    }
}
