package com.philj56.gbcc.romConfig

import androidx.preference.PreferenceDataStore
import java.io.File
import java.util.*

class IniDataStore(private val file: File) : PreferenceDataStore() {

    private val props = Properties()

    init {
        if (file.exists()) {
            file.inputStream().use { props.load(it) }
        } else {
            file.createNewFile()
        }
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        return when (props[key]) {
            "true" -> true
            "false" -> false
            else -> defValue
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        props.setProperty(key, if (value) "true" else "false")
    }

    override fun getString(key: String?, defValue: String?): String? {
        return props.getProperty(key) ?: defValue
    }

    override fun putString(key: String?, value: String?) {
        props.setProperty(key, value)
    }

    fun saveFile() {
        file.outputStream().use { props.store(it, null) }
    }
}