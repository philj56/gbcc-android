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
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class RomConfigActivity : AppCompatActivity() {
    private lateinit var configFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rom_config)

        val configDir = filesDir.resolve("config")
        configDir.mkdirs()
        val bundle = intent.extras
        val filename = File(bundle?.getString("file") ?: "").nameWithoutExtension
        configFile = configDir.resolve("$filename.cfg")
        val fragment = RomConfigFragment(configFile)

        supportFragmentManager.beginTransaction()
            .add(R.id.romConfigFragment, fragment)
            .commit()
    }

    fun clearConfig() {
        configFile.delete()
        finish()
    }
}

@Suppress("unused")
class RomConfigFragment(private val file: File) : PreferenceFragmentCompat() {
    private lateinit var dataStore: IniDataStore
    private var save = true

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        dataStore = IniDataStore(file)
        preferenceManager.preferenceDataStore = dataStore
        setPreferencesFromResource(R.xml.rom_config, rootKey)
    }

    override fun onDestroy() {
        if (save) {
            dataStore.saveFile()
        }
        super.onDestroy()
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        if (preference != null && preference.key == "delete") {
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

