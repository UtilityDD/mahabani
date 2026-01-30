package com.blackgrapes.kadachabuk

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class SimplifiedLanguageAdapter(
    private val languages: List<Pair<String, String>>, // Pair of Name, Code
    private val currentSelectedCode: String?,
    private val onLanguageSelected: (String) -> Unit
) : RecyclerView.Adapter<SimplifiedLanguageAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view as MaterialCardView
        val nameTextView: TextView = view.findViewById(R.id.tv_lang_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_language_simple, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (name, code) = languages[position]
        holder.nameTextView.text = name

        val isSelected = code == currentSelectedCode
        
        // Visual indication of selected language
        if (isSelected) {
            val typedValue = android.util.TypedValue()
            holder.itemView.context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            holder.cardView.strokeColor = typedValue.data
            holder.cardView.strokeWidth = 4
        } else {
            holder.cardView.strokeWidth = 0
        }

        // Disabled Assamese (as) and Odiya (od) as per request
        if (code == "as" || code == "od") {
            holder.itemView.alpha = 0.4f
            holder.itemView.isEnabled = false
            holder.itemView.isClickable = false
            // Also disable the card stroke potentially to make it look really disabled if needed, 
            // but alpha is usually enough.
        } else {
            holder.itemView.alpha = 1.0f
            holder.itemView.isEnabled = true
            holder.itemView.isClickable = true
        }

        holder.itemView.setOnClickListener {
            // extra check just in case
            if (code != "as" && code != "od") {
                onLanguageSelected(code)
            }
        }
    }

    override fun getItemCount() = languages.size
}
