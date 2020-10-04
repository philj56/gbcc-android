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

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.android.synthetic.main.activity_cheat_list.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

data class Cheat(var description: String, var code: String, var active: Boolean)

class CheatActivity : AppCompatActivity() {
    private lateinit var configFile: File
    private lateinit var adapter: CheatAdapter
    private var cheats = ArrayList<Cheat>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cheat_list)

        val configDir = filesDir.resolve("config")
        configDir.mkdirs()
        val bundle = intent.extras
        val filename = File(bundle?.getString("file") ?: "").nameWithoutExtension
        configFile = configDir.resolve("$filename.cheats")

        if (configFile.exists()) {
            loadFile()
        } else {
            configFile.createNewFile()
        }

        adapter = CheatAdapter(this, R.layout.entry_cheat, R.id.cheatDescription, cheats)
        cheatList.adapter = adapter

        cheatList.setOnItemClickListener { _, view, position, _ ->
            cheats[position].active = !cheats[position].active
            view.findViewById<SwitchCompat>(R.id.cheatActive).isChecked = cheats[position].active
        }

        cheatList.setOnItemLongClickListener { _, _, position, _ ->
            val dialog = CheatDialogFragment(position)
            dialog.isCancelable = false
            dialog.show(supportFragmentManager, "")
            return@setOnItemLongClickListener true
        }
    }

    override fun onDestroy() {
        saveFile()
        super.onDestroy()
    }

    fun clearConfig() {
        configFile.delete()
        finish()
    }

    @Suppress("UNUSED_PARAMETER")
    fun showCheatDialog(view: View?) {
        val dialog = CheatDialogFragment(-1)
        dialog.isCancelable = false
        dialog.show(supportFragmentManager, "")
    }

    fun addCheat(cheat: Cheat, index: Int) {
        if (index >= 0) {
            cheats[index] = cheat
        } else {
            cheats.add(cheat)
        }
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    fun removeCheat(index: Int) {
        cheats.removeAt(index)
        if (::adapter.isInitialized) {
            adapter.notifyDataSetChanged()
        }
    }

    fun getCheat(index: Int): Cheat {
        return cheats[index]
    }

    private fun saveFile() {
        configFile.outputStream().use {
            cheats.forEach { (description, code, active) ->
                if (!active) {
                    it.write("#".toByteArray())
                }
                it.write("cheat=$code#$description\n".toByteArray())
            }
        }
    }

    private fun loadFile() {
        configFile.forEachLine { line ->
            val active = !line.startsWith("#")
            val (_, code, description) =
                if (!active) { line.substring(1) } else { line }
                    .split("=", "#")
            cheats.add(Cheat(description, code, active))
        }
    }
}

class CheatAdapter(
    context: Context,
    resource: Int,
    textViewResourceId: Int,
    private val objects: List<Cheat>
) : ArrayAdapter<Cheat>(context, resource, textViewResourceId, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textDescription = view.findViewById<TextView>(R.id.cheatDescription)
        val textCode = view.findViewById<TextView>(R.id.cheatCode)
        val switchActive = view.findViewById<SwitchCompat>(R.id.cheatActive)
        val (description, code, active) = objects[position]

        textDescription.text = description
        textCode.text = formatCode(code)
        switchActive.isChecked = active

        return view
    }

    private fun formatCode(code: String): String {
        if (code.length == 9) {
            return code.substring(0, 3) + "-" + code.substring(3, 6) + "-" + code.substring(6, 9)
        }
        return code
    }
}

class CheatDialogFragment(private val index: Int) : DialogFragment() {
    private var descriptionFilled = false
    private var codeFilled = false

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity() as CheatActivity
        val view = activity.layoutInflater.inflate(R.layout.dialog_edit_cheat, null, false)
        val descriptionInput = view.findViewById<EditText>(R.id.cheatDescriptionInput)
        val codeInput = view.findViewById<EditText>(R.id.cheatCodeInput)
        val cheatExists = (index >= 0)

        val title: Int
        if (!cheatExists) {
            title = R.string.cheat_add_description
        } else {
            title = R.string.cheat_edit_description
            val cheat = activity.getCheat(index)
            descriptionInput.setText(cheat.description)
            codeInput.setText(cheat.code)
            descriptionFilled = true
            codeFilled = true
        }

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                activity.addCheat(
                    Cheat(
                        descriptionInput.text.toString().trim(),
                        codeInput.text.toString().toUpperCase(Locale.getDefault()),
                        true),
                    index
                )
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setView(view)

        if (cheatExists) {
            builder.setNeutralButton(R.string.delete) { _, _ -> activity.removeCheat(index) }
        }

        val dialog = builder.create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !cheatExists
        }

        descriptionInput.filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
            if (end <= start) {
                // Deletion, always accept
                return@InputFilter null
            }
            return@InputFilter source.subSequence(start, end).filterNot { it == '#' || it == ';' }
        })

        descriptionInput.addTextChangedListener { editor ->
            descriptionFilled = editor?.isNotBlank() ?: false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                descriptionFilled && codeFilled
        }

        codeInput.addTextChangedListener { editor ->
            codeFilled = editor?.length == 8 || editor?.length == 9
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                descriptionFilled && codeFilled
        }

        return dialog
    }
}
