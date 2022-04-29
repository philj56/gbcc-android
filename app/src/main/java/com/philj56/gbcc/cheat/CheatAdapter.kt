package com.philj56.gbcc.cheat

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.philj56.gbcc.R

class CheatAdapter(
    context: Context,
    resource: Int,
    textViewResourceId: Int,
    private val objects: List<Cheat>
) : ArrayAdapter<Cheat>(context, resource, textViewResourceId, objects) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val textDescription = view.findViewById<TextView>(R.id.cheatDescription)
        val textCode = view.findViewById<TextView>(R.id.cheatCode)
        val switchActive = view.findViewById<SwitchCompat>(R.id.cheatActive)
        val (description, code, active) = objects[position]

        textDescription.text = description
        textCode.text = formatCode(code)
        switchActive.isChecked = active

        return view
    }

    private fun formatCode(code: String): String {
        if (code.length == 9) {
            return code.substring(0, 3) + "-" + code.substring(3, 6) + "-" + code.substring(6, 9)
        }
        return code
    }
}