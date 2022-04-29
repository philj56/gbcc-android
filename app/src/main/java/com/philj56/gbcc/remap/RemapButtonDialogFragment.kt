package com.philj56.gbcc.remap

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.RadioGroup
import androidx.core.content.edit
import androidx.fragment.app.DialogFragment
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.radiobutton.MaterialRadioButton
import com.philj56.gbcc.R

class RemapButtonDialogFragment(private val button: Button, private val key: String, private val analogue: Boolean) :
    DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val mapping = prefs.getString(key, "unmapped")
        val buttonNames: Array<String>
        val buttonValues: Array<String>
        if (analogue) {
            buttonNames = resources.getStringArray(R.array.button_map_analogue_names_array)
            buttonValues = resources.getStringArray(R.array.button_map_analogue_values_array)
        } else {
            buttonNames = resources.getStringArray(R.array.button_map_names_array)
            buttonValues = resources.getStringArray(R.array.button_map_values_array)
        }

        val layout = requireActivity().layoutInflater.inflate(R.layout.dialog_remap_button, null, false)
        val radioGroup = layout.findViewById<RadioGroup>(R.id.remapDialogRadioGroup)
        buttonNames.forEachIndexed { index, name ->
            val radio = MaterialRadioButton(radioGroup.context)
            radio.text = name
            radio.id = index
            radioGroup.addView(radio)

            if (mapping == buttonValues[index]) {
                radioGroup.check(index)
            }
        }

        val builder = MaterialAlertDialogBuilder(requireActivity())
        builder.setTitle(R.string.select_mapping)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.edit {
                    putString(key, buttonValues[radioGroup.checkedRadioButtonId])
                    apply()
                }
                button.text = buttonNames[radioGroup.checkedRadioButtonId]
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .setView(layout)

        return builder.create()
    }
}