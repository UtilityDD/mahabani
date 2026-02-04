package com.blackgrapes.kadachabuk

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.ContextCompat
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

        // Retrieve the last read serial from SharedPreferences for this specific book
        val prefs = context.getSharedPreferences("LastReadPrefs", Context.MODE_PRIVATE)
        val lastReadSerial = prefs.getString("lastReadSerial_${chapter.bookId}", null)

        holder.serialTextView.text = chapter.serial
        // Pass whether this is the last read chapter to the bind method
        holder.bind(chapter, chapter.serial == lastReadSerial)

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
        private val historyTextView: TextView = itemView.findViewById(R.id.textViewHistory)
        private val lastReadTextView: TextView = itemView.findViewById(R.id.textViewLastRead)
        val serialTextView: TextView = itemView.findViewById(R.id.textViewSerial)

        fun bind(chapter: Chapter, isLastRead: Boolean) {
            headingTextView.text = chapter.heading
            // Remove parentheses from the date string, or show blank if missing
            val displayDate = chapter.date?.removeSurrounding("(", ")") ?: ""
            dateTextView.text = displayDate
            dateTextView.visibility = if (displayDate.isEmpty()) View.GONE else View.VISIBLE

            // --- Reading History Display Logic ---
            // Cancel any existing animations on this view before starting a new one.
            // This is crucial for RecyclerView to prevent animations from repeating on recycled views.
            historyTextView.animate().cancel()
            historyTextView.visibility = View.GONE

            val historyPrefs = itemView.context.getSharedPreferences("ReadingHistoryPrefs", Context.MODE_PRIVATE)
            val isHistoryVisible = historyPrefs.getBoolean("is_history_visible", true)

            if (isHistoryVisible) {
                val historyKeyBase = "${chapter.bookId}_${chapter.languageCode}_${chapter.serial}"
                val count = historyPrefs.getInt("count_$historyKeyBase", 0)
                val totalTimeMs = historyPrefs.getLong("time_$historyKeyBase", 0)

                if (count > 0) {
                    val formattedTime = TimeUtils.formatDuration(totalTimeMs)
                    val finalHistoryText = "$count / $formattedTime"

                    // Set initial text and make it visible
                    historyTextView.alpha = 1f
                    historyTextView.translationX = 0f
                    historyTextView.rotationY = 0f
                    // Set the icon from the start but make it transparent to reserve its space.
                    val historyIcon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_history)?.mutate()
                    historyIcon?.alpha = 0 // Make icon transparent
                    historyTextView.setCompoundDrawablesWithIntrinsicBounds(historyIcon, null, null, null)
                    historyTextView.text = "Reading history" // Initial text
                    historyTextView.visibility = View.VISIBLE


                    // Animate to the actual data after a delay
                    historyTextView.postDelayed({
                        // Animate the initial text out (flip away)
                        historyTextView.animate().rotationY(90f).alpha(0f).setDuration(250).withEndAction {
                            // At the halfway point of the flip:
                            historyTextView.text = finalHistoryText
                            // Fade the icon in.
                            historyTextView.compoundDrawables[0]?.alpha = 255
                            historyTextView.rotationY = -90f
                            historyTextView.animate().rotationY(0f).alpha(1f).setDuration(250).start()
                        }.start()
                    }, 800) // 0.8-second delay
                }
            } // --- End of History Logic ---

            // Visually distinguish the last read chapter
            lastReadTextView.visibility = if (isLastRead) View.VISIBLE else View.GONE
            if (isLastRead) {
                // Example: Change stroke color and width
                cardView.strokeWidth = 4 // Set a noticeable stroke width
                // Use the theme's primary color for the stroke.
                // R.color.design_default_color_primary is a private resource.
                val typedValue = android.util.TypedValue()
                cardView.context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                cardView.strokeColor = typedValue.data

            } else {
                // Reset to default for other items
                cardView.strokeWidth = 0
            }
        }
    }
}
