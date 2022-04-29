package com.philj56.gbcc.settings

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import com.philj56.gbcc.R
import com.philj56.gbcc.materialPreferences.MaterialSeekbarPreference

class UnitSeekbarPreference(context: Context, attrs: AttributeSet) :
    MaterialSeekbarPreference(context, attrs) {

    lateinit var textView: TextView
    val watcher = UnitSeekbarPreferenceWatcher()

    override fun onBindViewHolder(view: PreferenceViewHolder) {
        textView = view.findViewById(R.id.seekbar_value) as TextView
        textView.removeTextChangedListener(watcher)
        textView.addTextChangedListener(watcher)
        super.onBindViewHolder(view)
    }

    inner class UnitSeekbarPreferenceWatcher : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            textView.removeTextChangedListener(watcher)
            s?.insert(s.length, context.resources.getString(R.string.settings_bluetooth_latency_units))
            textView.addTextChangedListener(watcher)
        }

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        }
    }
}