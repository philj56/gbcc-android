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
import com.philj56.gbcc.databinding.ActivityLicenseBinding
import java.io.File

class LicenseActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLicenseBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.title = intent.extras?.getString("title") ?: ""
        val file = intent.extras?.getString("file") ?: ""

        binding.licenseText.text = assets.open(File("licenses", file).path).bufferedReader().use { it.readText() }
    }
}