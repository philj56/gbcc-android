package com.philj56.gbcc

import android.content.SharedPreferences
import android.os.Bundle
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philj56.gbcc.additionalMappings.AdditionalMapping
import com.philj56.gbcc.additionalMappings.AdditionalMappingAdapter
import com.philj56.gbcc.databinding.ActivityAdditionalMappingsBinding

class AdditionalMappingsActivity : BaseActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var binding: ActivityAdditionalMappingsBinding
    private lateinit var mappings: Array<AdditionalMapping>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdditionalMappingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        prefs = PreferenceManager.getDefaultSharedPreferences(baseContext)

        mappings = arrayOf(
            AdditionalMapping(getString(R.string.key_description_a), getString(R.string.additional_map_a_key)),
            AdditionalMapping(getString(R.string.key_description_b), getString(R.string.additional_map_b_key)),
            AdditionalMapping(getString(R.string.key_description_start), getString(R.string.additional_map_start_key)),
            AdditionalMapping(getString(R.string.key_description_select), getString(R.string.additional_map_select_key)),
            AdditionalMapping(getString(R.string.key_description_up), getString(R.string.additional_map_up_key)),
            AdditionalMapping(getString(R.string.key_description_down), getString(R.string.additional_map_down_key)),
            AdditionalMapping(getString(R.string.key_description_left), getString(R.string.additional_map_left_key)),
            AdditionalMapping(getString(R.string.key_description_right), getString(R.string.additional_map_right_key)),
            AdditionalMapping(getString(R.string.key_description_turbo), getString(R.string.additional_map_turbo_key)),
            AdditionalMapping(getString(R.string.key_description_menu), getString(R.string.additional_map_menu_key)),
            AdditionalMapping(getString(R.string.key_description_back), getString(R.string.additional_map_back_key))
        )

        val adapter = AdditionalMappingAdapter(this, R.layout.entry_additional_mapping, R.id.buttonDescription, mappings)
        binding.listView.adapter = adapter
        binding.listView.setOnItemClickListener { _, _, position, _ ->
            val mapping = mappings[position]
            val dialog = MaterialAlertDialogBuilder(this)
                .setTitle(mapping.description)
                .setView(R.layout.dialog_additional_mapping)
                .setNegativeButton(android.R.string.cancel) { _, _ -> }
                .setNeutralButton(R.string.additional_mappings_unset) { _, _ ->
                    prefs.edit {
                        remove(mapping.mapKey)
                        commit()
                    }
                }
                .setOnDismissListener { adapter.notifyDataSetChanged() }
                .create()

            dialog.setOnKeyListener { it, keyCode, keyEvent ->
                if (keyEvent.isSystem) {
                    return@setOnKeyListener false
                }
                prefs.edit {
                    putInt(mapping.mapKey, keyCode)
                    commit()
                }
                it.dismiss()
                return@setOnKeyListener true
            }
            dialog.show()
        }
    }
}

