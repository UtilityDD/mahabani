package com.blackgrapes.kadachabuk

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

class LanguageAdapter(
    private val languages: List<Pair<String, String>>,
    private val downloadedLanguageCodes: Set<String>,
    private val currentSelectedCode: String?,
    private val onLanguageSelected: (String, String) -> Unit,
    private val onLanguageDelete: (String, String) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LanguageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_language, parent, false)
        return LanguageViewHolder(view)
    }
 
    override fun onBindViewHolder(holder: LanguageViewHolder, position: Int) {
        val (name, code) = languages[position]
        holder.bind(name, code, downloadedLanguageCodes.contains(code))
    }

    override fun getItemCount(): Int = languages.size

    inner class LanguageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val languageNameTextView: TextView = itemView.findViewById(R.id.language_name)
        private val downloadStatusIcon: ImageView = itemView.findViewById(R.id.iv_download_status)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.button_delete_language)

        init {
            itemView.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val (name, code) = languages[adapterPosition]
                    onLanguageSelected(code, name)
                }
            }
            deleteButton.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val (name, code) = languages[adapterPosition]
                    onLanguageDelete(code, name)
                }
            }
        }

        fun bind(name: String, code: String, isDownloaded: Boolean) {
            languageNameTextView.text = name
            itemView.isSelected = (code == currentSelectedCode)
            downloadStatusIcon.isVisible = isDownloaded
            deleteButton.isVisible = isDownloaded
        }
    }
}