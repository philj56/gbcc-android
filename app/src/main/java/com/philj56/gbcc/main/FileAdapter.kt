package com.philj56.gbcc.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.philj56.gbcc.R
import java.io.File

class FileAdapter(
    private val onClick: (File, View) -> Unit,
    private val onLongClick: (File, View) -> Unit
) :
    ListAdapter<File, FileAdapter.FileViewHolder>(FileDiffCallback) {

    val selected = HashSet<File>()

    class FileViewHolder(itemView: View, private val adapter: FileAdapter, val onClick: (File, View) -> Unit, val onLongClick: (File, View) -> Unit) :
        RecyclerView.ViewHolder(itemView) {
        private val textView = itemView.findViewById<TextView>(R.id.fileEntry)
        private val imageView = itemView.findViewById<ImageView>(R.id.fileIcon)
        private var currentFile: File? = null

        init {
            itemView.setOnClickListener {
                currentFile?.let {
                    onClick(it, itemView)
                }
            }
            itemView.setOnLongClickListener {
                currentFile?.let {
                    onLongClick(it, itemView)
                }
                return@setOnLongClickListener true
            }
        }

        fun bind(file: File) {
            currentFile = file
            itemView.isActivated = file in adapter.selected
            when (file.extension) {
                "gbc" -> {
                    imageView.setImageResource(R.drawable.ic_file_gbc)
                    imageView.clearColorFilter()
                }
                "gb" -> {
                    imageView.setImageResource(R.drawable.ic_file_dmg)
                    imageView.clearColorFilter()
                }
                else -> {
                    imageView.setImageResource(R.drawable.ic_folder_34dp)
                }
            }

            textView.text = file.nameWithoutExtension
        }
    }

    private object FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem == newItem
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.entry_file, parent, false)
        return FileViewHolder(view, this, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
}
