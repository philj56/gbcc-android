package com.philj56.gbcc

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

abstract class BaseActivity : AppCompatActivity() {

    val inNightMode get() = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private var themeResource: Int = 0
    private var oledNightMode: Boolean = false
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        themeResource = getThemeResource()
        oledNightMode = getOledNightMode()
        setTheme(themeResource)
        if (inNightMode && oledNightMode) {
            theme.applyStyle(R.style.OledDark, true)
        }
    }

    override fun onStart() {
        super.onStart()
        // If the app theme has changed out from under us
        // (e.g. when coming back to the main screen after changing the theme in settings),
        // we need to recreate the activity in order to apply the change
        if (getThemeResource() != themeResource) {
            recreate()
        } else if (inNightMode && getOledNightMode() != oledNightMode) {
            recreate()
        }
    }

    private fun getThemeResource(): Int {
        return when (prefs.getString("theme", null)) {
            "Berry" -> R.style.Theme_GBCC_Berry
            "Dandelion" -> R.style.Theme_GBCC_Dandelion
            "Grape" -> R.style.Theme_GBCC_Grape
            "Kiwi" -> R.style.Theme_GBCC_Kiwi
            "Teal" -> R.style.Theme_GBCC_Teal
            else -> R.style.Theme_GBCC_Teal
        }
    }

    private fun getOledNightMode(): Boolean {
        return prefs.getBoolean("oled_night_mode", false)
    }
}