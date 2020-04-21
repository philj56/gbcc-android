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
import android.text.InputType
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat

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