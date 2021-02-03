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

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.philj56.gbcc.databinding.ActivityLicenseListBinding

class LicenseListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLicenseListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val licenseNames = resources.getStringArray(R.array.license_names_array)
        val licenseFiles = resources.getStringArray(R.array.license_values_array)
        val adapter = ArrayAdapter(this, R.layout.entry_license, R.id.licenseEntry, licenseNames)
        binding.listView.adapter = adapter
        binding.listView.setOnItemClickListener { _, _, position, _ ->
            val name = licenseNames[position]
            val file = licenseFiles[position]
            val intent = Intent(this, LicenseActivity::class.java).apply {
                putExtra("title", name)
                putExtra("file", file)
            }
            startActivity(intent)
        }
    }
}
