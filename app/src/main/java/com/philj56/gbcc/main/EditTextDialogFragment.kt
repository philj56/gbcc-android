package com.philj56.gbcc.main

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.core.view.postDelayed
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.R

class EditTextDialogFragment(
    private val title: Int,
    private val initialText: String,
    private val onConfirm: (String) -> Unit
) : DialogFragment() {
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