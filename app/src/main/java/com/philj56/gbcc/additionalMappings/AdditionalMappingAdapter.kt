package com.philj56.gbcc.additionalMappings

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceManager
import com.philj56.gbcc.R

class AdditionalMappingAdapter(
    context: Context,
    resource: Int,
    textViewResourceId: Int,
    private val objects: Array<AdditionalMapping>
) : ArrayAdapter<AdditionalMapping>(context, resource, textViewResourceId, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textDescription = view.findViewById<TextView>(R.id.buttonDescription)
        val textMapping = view.findViewById<TextView>(R.id.buttonMapping)
        val (description, mapKey) = objects[position]
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val keyName = keyCodeNames[prefs.getInt(mapKey, -1)] ?: "Unknown"

        textDescription.text = description
        textMapping.text = context.getString(R.string.additional_mappings_map_description, keyName)

        return view
    }
}