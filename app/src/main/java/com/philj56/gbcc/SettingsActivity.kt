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
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }
}

@Suppress("unused")
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val turbo = preferenceManager.findPreference<EditTextPreference>("turbo_speed")
        turbo?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            editText.selectAll()
        }

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
    }
}

object TurboSummaryProvider : Preference.SummaryProvider<EditTextPreference> {
    override fun provideSummary(preference: EditTextPreference?): CharSequence {
        if (preference == null) {
            return "Not set"
        }
        val text = if (preference.text.isEmpty()) {
            "0"
        } else {
            preference.text
        }
        return when (text.toFloat()) {
            0F -> "0 (Unlimited)"
            else -> "$text×"
        }
    }
}

class UnitSeekbarPreference(context: Context, attrs: AttributeSet) :
    SeekBarPreference(context, attrs) {

    var textView: TextView? = null
    val watcher = UnitSeekbarPreferenceWatcher()

    override fun onBindViewHolder(view: PreferenceViewHolder?) {
        textView = view?.findViewById(R.id.seekbar_value) as TextView?
        textView?.removeTextChangedListener(watcher)
        textView?.addTextChangedListener(watcher)
        super.onBindViewHolder(view)
    }

    inner class UnitSeekbarPreferenceWatcher : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            textView?.removeTextChangedListener(watcher)
            s?.insert(s.length, context.resources.getString(R.string.settings_bluetooth_latency_units))
            textView?.addTextChangedListener(watcher)
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }
    }
}
