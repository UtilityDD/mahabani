package com.blackgrapes.kadachabuk // Or your chosen package

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

// Import your data model for downloaded chapters, e.g.:
// import com.blackgrapes.kadachabuk.model.ChapterDownloadStatus

// Replace 'ChapterDownloadStatus' with the actual data class you'll use
// to represent the items in this list (e.g., just a String for the heading, or a more complex object).

class DownloadedChaptersAdapter(
    private val items: MutableList<ChapterDownloadStatus> // Use your specific data type
) : RecyclerView.Adapter<DownloadedChaptersAdapter.ViewHolder>() {

    // Example ViewHolder - customize this based on your item_downloaded_chapter_heading.xml
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val headingTextView: TextView = itemView.findViewById(R.id.tv_downloaded_chapter_title) // Example ID
        val statusIcon: View = itemView.findViewById(R.id.iv_chapter_download_status_icon)
        // Add other views from your item layout here
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // Create a layout file for your items, e.g., 'item_downloaded_chapter_heading.xml'
        // in app/src/main/res/layout/
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_downloaded_chapter_heading, parent, false) // Replace with your layout file
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.headingTextView.text = item.heading
        holder.statusIcon.isVisible = item.isDownloaded
        // Bind other data to your views here
    }

    override fun getItemCount(): Int = items.size

    // Function to update the list from the ViewModel
    fun updateList(newList: List<ChapterDownloadStatus>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged() // Consider using DiffUtil for better performance
    }

    fun addItem(item: ChapterDownloadStatus) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun clearItems() {
        items.clear()
        notifyDataSetChanged()
    }
}
