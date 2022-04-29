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

import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.preference.*
import com.philj56.gbcc.databinding.ActivitySettingsBinding
import com.philj56.gbcc.materialPreferences.MaterialListPreferenceDialogFragmentCompat
import com.philj56.gbcc.materialPreferences.MaterialTurboPreferenceDialogFragmentCompat
import com.philj56.gbcc.settings.SummaryListPreference
import com.philj56.gbcc.settings.TurboPreference

abstract class BaseSettingsActivity : BaseActivity() {
    abstract val preferenceResource : Int

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        if (savedInstanceState == null) {
            val bundle = bundleOf("preferenceResource" to preferenceResource)
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<SettingsFragment>(R.id.settingsFragment, args = bundle)
            }
        }
    }
}

class SettingsActivity : BaseSettingsActivity() {
    override val preferenceResource = R.xml.preferences
}

class AudioSettingsActivity : BaseSettingsActivity() {
    override val preferenceResource = R.xml.preferences_audio
}

class BehaviourSettingsActivity : BaseSettingsActivity() {
    override val preferenceResource = R.xml.preferences_behaviour
}

class DisplaySettingsActivity : BaseSettingsActivity() {
    override val preferenceResource = R.xml.preferences_display
}

class GraphicsSettingsActivity : BaseSettingsActivity() {
    override val preferenceResource = R.xml.preferences_graphics
}

class MiscellaneousSettingsActivity : BaseSettingsActivity() {
    override val preferenceResource = R.xml.preferences_miscellaneous
}

class SettingsFragment : PreferenceFragmentCompat() {
    private val DIALOG_FRAGMENT_TAG = "com.philj56.gbcc.preference.DIALOG"
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val preferenceResource = requireArguments().getInt("preferenceResource")
        setPreferencesFromResource(preferenceResource, rootKey)

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

        val oledNightMode = preferenceManager.findPreference<SwitchPreferenceCompat>("oled_night_mode")
        oledNightMode?.setOnPreferenceChangeListener { _, _ ->
            val activity = requireActivity() as BaseActivity
            if (activity.inNightMode) {
                activity.recreate()
            }
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

