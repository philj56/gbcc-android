package com.philj56.gbcc.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.EditTextPreference

class TurboPreference(context: Context, attrs: AttributeSet) :
    EditTextPreference(context, attrs) {
    init {
        summaryProvider = TurboSummaryProvider

        // This is currently unneeded, as it's hardcoded into
        // MaterialTurboPreferenceDialogFragmentCompat.
        // If that ever gets changed back to a less hacky solution, we'll want this again.
        // setOnBindEditTextListener { editText ->
        //     editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        //     editText.selectAll()
        // }
    }

    override fun setText(text: String?) {
        if (text?.toFloatOrNull() == null) {
            super.setText("0")
        } else {
            super.setText(text)
        }
    }

    private object TurboSummaryProvider : SummaryProvider<EditTextPreference> {
        override fun provideSummary(preference: EditTextPreference): CharSequence {
            val text = preference.text?.ifEmpty {
                "0"
            }
            return when (text?.toFloat()) {
                0F -> "0 (Unlimited)"
                else -> "$text×"
            }
        }
    }

}