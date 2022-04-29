package com.philj56.gbcc.settings

import android.content.Context
import android.util.AttributeSet
import androidx.preference.ListPreference

class SummaryListPreference(context: Context, attrs: AttributeSet) :
    ListPreference(context, attrs) {
    init {
        summaryProvider = SimpleSummaryProvider.getInstance()
    }
}