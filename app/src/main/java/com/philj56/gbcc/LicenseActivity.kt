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

import android.content.res.Configuration
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File


class LicenseActivity : AppCompatActivity() {
    private var nightMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        nightMode =
            applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (nightMode) {
            setTheme(R.style.AppThemeDark)
        } else {
            setTheme(R.style.AppTheme)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)
        val name = intent.extras?.getString("file") ?: ""
        title = name

        assets.open(File("licenses", name).path).use{
            findViewById<TextView>(R.id.licenseText).text = String(it.readBytes())
        }
    }
}