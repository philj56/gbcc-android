package com.philj56.gbcc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

abstract class BaseActivity : AppCompatActivity() {

    private var themeResource: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeResource = getThemeResource()
        setTheme(themeResource)
    }

    override fun onStart() {
        super.onStart()
        // If the app theme has changed out from under us
        // (e.g. when coming back to the main screen after changing the theme in settings),
        // we need to recreate the activity in order to apply the change
        if (getThemeResource() != themeResource) {
            recreate()
        }
    }

    private fun getThemeResource(): Int {
        return when (PreferenceManager.getDefaultSharedPreferences(this)
            .getString("theme", null)) {
            "Berry" -> R.style.Theme_GBCC_Berry
            "Dandelion" -> R.style.Theme_GBCC_Dandelion
            "Grape" -> R.style.Theme_GBCC_Grape
            "Kiwi" -> R.style.Theme_GBCC_Kiwi
            "Teal" -> R.style.Theme_GBCC_Teal
            else -> R.style.Theme_GBCC_Teal
        }
    }
}