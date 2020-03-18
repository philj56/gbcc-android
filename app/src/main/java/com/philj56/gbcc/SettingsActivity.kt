package com.philj56.gbcc

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputType
import android.util.AttributeSet
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val nightMode = (applicationContext.resources.configuration.uiMode
                        and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
        if (nightMode) {
            setTheme(R.style.SettingsThemeDark)
        } else {
            setTheme(R.style.SettingsTheme)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }
}

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        val turbo = preferenceManager.findPreference<EditTextPreference>("turbo_speed")
        turbo?.setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            editText.selectAll();
        }
    }
}

class SummaryListPreference : ListPreference {
    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int): super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int): super(context, attrs, defStyleAttr, defStyleRes)

    init {
        summaryProvider = SimpleSummaryProvider.getInstance()
    }
}