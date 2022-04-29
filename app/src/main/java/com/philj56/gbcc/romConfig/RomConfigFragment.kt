package com.philj56.gbcc.romConfig

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.R
import com.philj56.gbcc.RomConfigActivity

class RomConfigFragment : PreferenceFragmentCompat() {
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
}