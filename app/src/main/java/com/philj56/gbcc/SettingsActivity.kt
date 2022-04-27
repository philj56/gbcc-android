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

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import com.philj56.gbcc.databinding.ActivitySettingsBinding
import com.philj56.gbcc.preference.MaterialTurboPreferenceDialogFragmentCompat
import com.philj56.gbcc.preference.MaterialListPreferenceDialogFragmentCompat
import com.philj56.gbcc.preference.SliderPreference

class SettingsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    private val DIALOG_FRAGMENT_TAG = "com.philj56.gbcc.preference.DIALOG"
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val nightMode = preferenceManager.findPreference<SummaryListPreference>("night_mode")
        nightMode?.setOnPreferenceChangeListener { _, newValue ->
            AppCompatDelegate.setDefaultNightMode(
                when (newValue) {
                    "auto" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    "on" -> AppCompatDelegate.MODE_NIGHT_YES
                    "off" -> AppCompatDelegate.MODE_NIGHT_NO
                    else -> return@setOnPreferenceChangeListener false
                }
            )
            return@setOnPreferenceChangeListener true
        }

        val theme = preferenceManager.findPreference<SummaryListPreference>("theme")
        theme?.setOnPreferenceChangeListener { _, _ ->
            activity?.recreate()
            return@setOnPreferenceChangeListener true
        }
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        when (preference) {
            is ListPreference -> {
                val f = MaterialListPreferenceDialogFragmentCompat.newInstance(preference.key)
                f.setTargetFragment(this, 0)
                f.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
            }
            is TurboPreference -> {
                val f = MaterialTurboPreferenceDialogFragmentCompat.newInstance(preference.key)
                f.setTargetFragment(this, 0)
                f.show(parentFragmentManager, DIALOG_FRAGMENT_TAG)
            }
            else -> super.onDisplayPreferenceDialog(preference)
        }
    }
}

class SummaryListPreference(context: Context, attrs: AttributeSet) :
    ListPreference(context, attrs) {
    init {
        summaryProvider = SimpleSummaryProvider.getInstance()
    }
}

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
}

object TurboSummaryProvider : Preference.SummaryProvider<EditTextPreference> {
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

class UnitSeekbarPreference(context: Context, attrs: AttributeSet) :
    SliderPreference(context, attrs) {

    lateinit var textView: TextView
    val watcher = UnitSeekbarPreferenceWatcher()

    override fun onBindViewHolder(view: PreferenceViewHolder) {
        textView = view.findViewById(R.id.seekbar_value) as TextView
        textView.removeTextChangedListener(watcher)
        textView.addTextChangedListener(watcher)
        super.onBindViewHolder(view)
    }

    inner class UnitSeekbarPreferenceWatcher : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            textView.removeTextChangedListener(watcher)
            s?.insert(s.length, context.resources.getString(R.string.settings_bluetooth_latency_units))
            textView.addTextChangedListener(watcher)
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }
    }
}
