package com.philj56.gbcc.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.*

class CreateSaveExportZip : ActivityResultContracts.CreateDocument() {
    @SuppressLint("SimpleDateFormat")
    override fun createIntent(context: Context, input: String): Intent {
        val intent = super.createIntent(context, input)
        intent.apply {
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val date = SimpleDateFormat("yyyyMMdd").format(Date())
        intent.putExtra(Intent.EXTRA_TITLE, "gbcc_saves_$date.zip")
        return intent
    }
}