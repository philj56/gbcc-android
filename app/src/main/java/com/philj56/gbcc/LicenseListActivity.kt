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
import kotlinx.android.synthetic.main.activity_license_list.*

class LicenseListActivity : AppCompatActivity() {
    private lateinit var licenses: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license_list)
        adapter = ArrayAdapter<String>(this, R.layout.license_entry, R.id.licenseEntry, licenses)
        licenseList.adapter = adapter
        licenseList.setOnItemClickListener{ _, _, position, _ ->
            val name = licenseList.adapter.getItem(position) as String
            val intent = Intent(this, LicenseActivity::class.java).apply {
                putExtra("file", name)
            }
            startActivity(intent)
        }
    }

    override fun onContentChanged() {
        super.onContentChanged()
        updateLicenses()
    }

    private fun updateLicenses() {
        licenses = assets.list("licenses")?.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER, { it })
        )?.toCollection(ArrayList()) ?: ArrayList()
        if (::adapter.isInitialized) {
            adapter.clear()
            adapter.addAll(licenses)
            adapter.notifyDataSetChanged()
        }
    }
}