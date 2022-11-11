package com.philj56.gbcc.romConfig

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.R
import com.philj56.gbcc.RomConfigActivity
import com.philj56.gbcc.materialPreferences.MaterialListPreferenceDialogFragmentCompat
import com.philj56.gbcc.materialPreferences.MaterialTurboPreferenceDialogFragmentCompat
import com.philj56.gbcc.settings.TurboPreference

class RomConfigFragment : PreferenceFragmentCompat() {
    private val DIALOG_FRAGMENT_TAG = "com.philj56.gbcc.preference.DIALOG"
    private lateinit var dataStore: IniDataStore
    private var save = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        dataStore = IniDataStore((requireActivity() as RomConfigActivity).configFile)
        preferenceManager.preferenceDataStore = dataStore
        setPreferencesFromResource(R.xml.rom_config, rootKey)
    }

    override fun onDestroy() {
        if (save) {
            dataStore.saveFile()
        }
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference.key == "delete") {
            val context = requireContext()
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_config_confirmation)
                .setPositiveButton(R.string.delete) { _, _ ->
                    save = false
                    (activity as RomConfigActivity).clearConfig()
                }.setNegativeButton(android.R.string.cancel) { _, _ -> }
                .show()
            return true
        }
        return super.onPreferenceTreeClick(preference)
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