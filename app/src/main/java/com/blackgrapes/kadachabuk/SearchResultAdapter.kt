package com.blackgrapes.kadachabuk

import android.content.Intent
import android.view.LayoutInflater
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.text.BreakIterator
import java.util.Locale

data class SearchResult(val chapter: Chapter, val matchCount: Int)

class SearchResultAdapter(private var searchResults: List<SearchResult>) :
    RecyclerView.Adapter<SearchResultAdapter.SearchResultViewHolder>() {

    private var currentQuery: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result_card, parent, false)
        return SearchResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: SearchResultViewHolder, position: Int) {
        val searchResult = searchResults[position]
        holder.bind(searchResult, currentQuery)
    }

    override fun getItemCount(): Int = searchResults.size

    fun updateResults(newResults: List<SearchResult>, query: String) {
        searchResults = newResults
        this.currentQuery = query
        notifyDataSetChanged() // Consider using DiffUtil for better performance
    }

    class SearchResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val headingTextView: TextView = itemView.findViewById(R.id.textViewHeading)
        private val serialTextView: TextView = itemView.findViewById(R.id.textViewSerial)
        private val dateTextView: TextView = itemView.findViewById(R.id.textViewDate)

        fun bind(searchResult: SearchResult, query: String?) {
            val chapter = searchResult.chapter
            if (query.isNullOrEmpty()) {
                headingTextView.text = chapter.heading
            } else {
                headingTextView.text = highlightText(chapter.heading, query, chapter.languageCode)
            }
            serialTextView.text = chapter.serial // Serial numbers are usually short and don't need highlighting.
            dateTextView.text = "${searchResult.matchCount} matches"

            itemView.setOnClickListener {
                val context = it.context
                val intent = Intent(context, DetailActivity::class.java).apply {
                    putExtra("EXTRA_HEADING", chapter.heading)
                    putExtra("EXTRA_DATE", chapter.date ?: "")
                    putExtra("EXTRA_DATA", chapter.dataText)
                    putExtra("EXTRA_WRITER", chapter.writer)
                    putExtra("EXTRA_SERIAL", chapter.serial)
                    putExtra("EXTRA_LANGUAGE_CODE", chapter.languageCode)
                    putExtra("EXTRA_BOOK_ID", chapter.bookId) // Pass bookId
                    putExtra("EXTRA_SEARCH_QUERY", query) // Pass the search query
                }
                context.startActivity(intent)
            }
        }

        private fun highlightText(fullText: String, query: String, languageCode: String): SpannableString {
            val spannableString = SpannableString(fullText)
            if (query.isEmpty()) {
                return spannableString
            }

            val highlightColor = ContextCompat.getColor(itemView.context, R.color.highlight_color) // Make sure this color is defined for light/dark themes
            // Create a locale from the language code to ensure correct word boundary detection
            val locale = if (languageCode.isNotEmpty()) {
                Locale(languageCode)
            } else {
                Locale.getDefault()
            }
            val boundary = BreakIterator.getWordInstance(locale)
            boundary.setText(fullText)
            var fromIndex = 0

            while (fromIndex < fullText.length) {
                val matchIndex = fullText.indexOf(query, fromIndex, ignoreCase = true)
                if (matchIndex == -1) break

                // Use BreakIterator to find the word boundaries around the match.
                // By checking from the middle of the match, we ensure we get the boundaries
                // of the containing word, even if the match itself is a valid word.
                val wordStart = boundary.preceding(matchIndex + 1)
                val wordEnd = boundary.following(matchIndex)
                
                spannableString.setSpan(BackgroundColorSpan(highlightColor), wordStart, wordEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                fromIndex = wordEnd
            }
            return spannableString
        }
    }
}