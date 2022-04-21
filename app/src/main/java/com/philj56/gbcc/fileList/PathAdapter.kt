package com.philj56.gbcc.fileList

import android.annotation.SuppressLint
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philj56.gbcc.R
import java.io.File

class PathAdapter(private val onClick: (File) -> Unit) :
    ListAdapter<File, PathAdapter.PathViewHolder>(PathDiffCallback) {

    class PathViewHolder(itemView: View, private val adapter: PathAdapter, val onClick: (File) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val button = itemView.findViewById<Button>(R.id.pathButton)
        private var currentFile: File? = null

        init {
            button.setOnClickListener {
                currentFile?.let {
                    onClick(it)
                }
            }
        }

        @SuppressLint("SetTextI18n")
        fun bind(file: File) {
            currentFile = file
            button.isActivated = (file == adapter.getItem(adapter.itemCount - 1))
            if (file.name == "") {
                button.text = itemView.context.getString(R.string.base_directory_name)
            } else {
                button.text = "/  ${file.name}"
            }
        }
    }

    private object PathDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PathViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.entry_path, parent, false)
        return PathViewHolder(view, this, onClick)
    }

    override fun onBindViewHolder(holder: PathViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
