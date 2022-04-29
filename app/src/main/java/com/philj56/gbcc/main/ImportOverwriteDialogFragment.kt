package com.philj56.gbcc.main

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.widget.CheckedTextView
import android.widget.ListView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.IMPORTED_SAVE_SUBDIR
import com.philj56.gbcc.R
import java.io.File

class ImportOverwriteDialogFragment(private val files: ArrayList<File>) : DialogFragment() {
    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val adapter = ImportOverwriteAdapter(
                requireContext(),
                R.layout.entry_import_overwrite,
                R.id.importOverwriteText,
                files
            )
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