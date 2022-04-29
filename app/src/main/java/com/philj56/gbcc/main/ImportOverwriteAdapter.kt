package com.philj56.gbcc.main

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import com.philj56.gbcc.R
import java.io.File
import java.util.HashSet

class ImportOverwriteAdapter(
    context: Context,
    resource: Int,
    textViewResourceId: Int,
    private val objects: List<File>
) : ArrayAdapter<File>(context, resource, textViewResourceId, objects) {

    val selected = HashSet<File>()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view: View = super.getView(position, convertView, parent)
        val textView: CheckedTextView = view.findViewById(R.id.importOverwriteText)
        val file: File = objects[position]

        textView.isChecked = file in selected

        textView.text = file.nameWithoutExtension
        return view
    }
}