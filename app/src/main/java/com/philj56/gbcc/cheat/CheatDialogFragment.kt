package com.philj56.gbcc.cheat

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.InputFilter
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.CheatActivity
import com.philj56.gbcc.R
import com.philj56.gbcc.databinding.DialogEditCheatBinding

class CheatDialogFragment(private val index: Int) : DialogFragment() {
    private var descriptionFilled = false
    private var codeFilled = false

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity() as CheatActivity
        val view = activity.layoutInflater.inflate(R.layout.dialog_edit_cheat, null, false)
        val binding = DialogEditCheatBinding.bind(view)
        val cheatExists = (index >= 0)

        val title: Int
        if (!cheatExists) {
            title = R.string.cheat_add_description
        } else {
            title = R.string.cheat_edit_description
            val cheat = activity.getCheat(index)
            binding.cheatDescriptionInput.setText(cheat.description)
            binding.cheatCodeInput.setText(cheat.code)
            descriptionFilled = true
            codeFilled = true
        }

        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                activity.addCheat(
                    Cheat(
                        binding.cheatDescriptionInput.text.toString().trim(),
                        binding.cheatCodeInput.text.toString().uppercase(),
                        true
                    ),
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
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }

        binding.cheatDescriptionInput.filters = arrayOf(InputFilter { source, start, end, _, _, _ ->
            if (end <= start) {
                // Deletion, always accept
                return@InputFilter null
            }
            return@InputFilter source.subSequence(start, end).filterNot { it == '#' || it == ';' }
        })

        binding.cheatDescriptionInput.addTextChangedListener { editor ->
            descriptionFilled = editor?.isNotBlank() ?: false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                descriptionFilled && codeFilled
        }

        binding.cheatCodeInput.addTextChangedListener { editor ->
            codeFilled = editor?.length == 8 || editor?.length == 9
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
                descriptionFilled && codeFilled
        }

        return dialog
    }
}