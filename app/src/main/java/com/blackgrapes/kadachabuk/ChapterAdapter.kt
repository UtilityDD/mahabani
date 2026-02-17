package com.blackgrapes.kadachabuk

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit
import com.google.android.material.card.MaterialCardView

class ChapterAdapter(private var chapters: List<Chapter>) :
    RecyclerView.Adapter<ChapterAdapter.ChapterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChapterViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chapter_card, parent, false)
        return ChapterViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChapterViewHolder, position: Int) {
        val chapter = chapters[position]
        val context = holder.itemView.context

        // Retrieve the last read info from SharedPreferences
        val prefs = context.getSharedPreferences("LastReadPrefs", Context.MODE_PRIVATE)
        val lastReadSerial = prefs.getString("lastReadSerial_${chapter.bookId}", null)
        val lastReadLang = prefs.getString("lastReadLang_${chapter.bookId}", null)
        val isLastRead = chapter.serial == lastReadSerial && chapter.languageCode == lastReadLang
        
        holder.serialTextView.text = chapter.serial
        
        // Fetch the last read timestamp if this is the last read chapter
        val lastReadTimestamp = if (isLastRead) {
            prefs.getLong("lastReadTimestamp_${chapter.bookId}_${chapter.serial}_${chapter.languageCode}", 0L)
        } else {
            0L
        }
        
        // Pass to the bind method with timestamp
        holder.bind(chapter, isLastRead, lastReadTimestamp)

        // Handle click → open DetailActivity
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, DetailActivity::class.java).apply {
                // Note: EXTRA_WRITER is still passed for DetailActivity, but not displayed on the card.
                putExtra("EXTRA_HEADING", chapter.heading)
                putExtra("EXTRA_DATE", chapter.date ?: "")
                putExtra("EXTRA_WRITER", chapter.writer)
                putExtra("EXTRA_DATA", chapter.dataText) // ✅ use dataText instead of data
                putExtra("EXTRA_SERIAL", chapter.serial)
                putExtra("EXTRA_LANGUAGE_CODE", chapter.languageCode)
                putExtra("EXTRA_BOOK_ID", chapter.bookId) // Pass bookId
                putExtra("EXTRA_AUDIO_LINK", chapter.audioLink)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = chapters.size

    fun updateChapters(newChapters: List<Chapter>, lastReadSerial: String? = null) {
        chapters = newChapters
        notifyDataSetChanged()
    }

    class ChapterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val headingTextView: TextView = itemView.findViewById(R.id.textViewHeading)
        private val dateTextView: TextView = itemView.findViewById(R.id.textViewDate)
        private val historyIndicatorDot: View = itemView.findViewById(R.id.historyIndicatorDot)
        private val lastReadTextView: TextView = itemView.findViewById(R.id.textViewLastRead)
        val serialTextView: TextView = itemView.findViewById(R.id.textViewSerial)


        fun bind(chapter: Chapter, isLastRead: Boolean, lastReadTimestamp: Long) {
            headingTextView.text = chapter.heading
            // Remove parentheses from the date string, or show blank if missing
            val displayDate = chapter.date?.removeSurrounding("(", ")") ?: ""
            dateTextView.text = displayDate
            dateTextView.visibility = if (displayDate.isEmpty()) View.GONE else View.VISIBLE

            // --- Reading History Display Logic (Serial Number Click) ---
            historyIndicatorDot.visibility = View.GONE
            serialTextView.setOnClickListener(null) // Clear previous listener

            val historyPrefs = itemView.context.getSharedPreferences("ReadingHistoryPrefs", Context.MODE_PRIVATE)
            val isHistoryVisible = historyPrefs.getBoolean("is_history_visible", true)

            if (isHistoryVisible) {
                val historyKeyBase = "${chapter.bookId}_${chapter.languageCode}_${chapter.serial}"
                val count = historyPrefs.getInt("count_$historyKeyBase", 0)
                val totalTimeMs = historyPrefs.getLong("time_$historyKeyBase", 0)

                if (count > 0) {
                    // Show subtle indicator dot on serial badge
                    historyIndicatorDot.visibility = View.VISIBLE
                    
                    // Make serial number clickable to show reading history
                    serialTextView.setOnClickListener {
                        val formattedTime = TimeUtils.formatDuration(totalTimeMs)
                        val message = "📖 Read $count time${if (count > 1) "s" else ""}\n⏱️ Total: $formattedTime"
                        android.widget.Toast.makeText(itemView.context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } // --- End of History Logic ---

            // Display last read with relative time
            if (isLastRead && lastReadTimestamp > 0) {
                val relativeTime = getRelativeTime(lastReadTimestamp)
                lastReadTextView.text = relativeTime
                lastReadTextView.visibility = View.VISIBLE
            } else if (isLastRead) {
                // Fallback if timestamp is not available
                lastReadTextView.text = "Last read"
                lastReadTextView.visibility = View.VISIBLE
            } else {
                lastReadTextView.visibility = View.GONE
            }

            // Visually distinguish the last read chapter
            if (isLastRead) {
                // Apply a clean, animated highlight (no border)
                cardView.strokeWidth = 0
                
                val typedValue = android.util.TypedValue()
                itemView.context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                val primaryColor = typedValue.data
                
                // Use a more visible but still subtle background tint
                val tintColor = ColorUtils.setAlphaComponent(primaryColor, 35)
                
                // Animate the background tint with a smooth fade-in
                cardView.alpha = 0.7f
                cardView.setCardBackgroundColor(tintColor)
                cardView.animate()
                    .alpha(1f)
                    .setDuration(400)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                
                // Subtle elevation boost for depth
                cardView.cardElevation = 6f
            } else {
                // Reset to default
                cardView.strokeWidth = 0
                cardView.alpha = 1f
                val typedValue = android.util.TypedValue()
                // Use colorSurfaceContainer to match item_chapter_card.xml default
                itemView.context.theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
                cardView.setCardBackgroundColor(typedValue.data)
                cardView.cardElevation = 4f // Default elevation from XML
            }
        }
        
        private fun getRelativeTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp
            
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24
            
            return when {
                seconds < 60 -> "Just now"
                minutes < 60 -> "${minutes}m ago"
                hours < 24 -> "${hours}h ago"
                days == 1L -> "Yesterday"
                days < 7 -> "${days}d ago"
                days < 30 -> "${days / 7}w ago"
                else -> "${days / 30}mo ago"
            }
        }
    }
}
