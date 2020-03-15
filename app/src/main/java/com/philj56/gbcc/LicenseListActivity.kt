package com.philj56.gbcc

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_license_list.*
import java.io.File

class LicenseListActivity : AppCompatActivity() {
    private lateinit var licenses: ArrayList<String>
    private lateinit var adapter: ArrayAdapter<String>

    private var nightMode: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        nightMode = applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (nightMode) {
            setTheme(R.style.AppThemeDark)
        } else {
            setTheme(R.style.AppTheme)
        }
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

class LicenseAdapter(context: Context, resource: Int, textViewResourceId: Int, objects: List<String>)
    : ArrayAdapter<String>(context, resource, textViewResourceId, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textView = view.findViewById<TextView>(R.id.licenseEntry)
        textView.text = File(textView.text.toString()).nameWithoutExtension
        return view
    }
}