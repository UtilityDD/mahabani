package com.blackgrapes.kadachabuk

import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Quote(
    val text: String,
    val source: String
)

class QuoteRepository {

    // Direct export link for the Google Sheet
    private val SHEET_URL = "https://docs.google.com/spreadsheets/d/1wZSxXRZHkgbTG3oPDJn_JbKy4m3BWELah67XcgBz6BA/export?format=csv&gid=1002193481"

    suspend fun fetchRandomQuote(): Quote? = withContext(Dispatchers.IO) {
        try {
            val csvContent = URL(SHEET_URL).readText()
            val quotes = parseCsvContent(csvContent)
            
            if (quotes.isNotEmpty()) {
                quotes.random()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseCsvContent(content: String): List<Quote> {
        val quotes = mutableListOf<Quote>()
        val currentField = StringBuilder()
        val currentRow = mutableListOf<String>()
        var inQuotes = false
        var i = 0
        
        while (i < content.length) {
            val char = content[i]
            
            when {
                char == '"' -> {
                    // Handle escaped quotes ("") if needed, though simple toggle works for most basic CSVs
                    if (i + 1 < content.length && content[i + 1] == '"') {
                        currentField.append('"')
                        i++ // Skip next quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == ',' && !inQuotes -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                }
                (char == '\n' || char == '\r') && !inQuotes -> {
                    // End of row
                    currentRow.add(currentField.toString())
                    currentField.clear()
                    
                    // Process row if it has enough data (Quote + Source)
                    if (currentRow.size >= 2 && currentRow[0].isNotBlank()) {
                        // Sanitize source: replace newlines and multiple spaces with single space
                        val rawSource = currentRow[1]
                        val sanitizedSource = rawSource.replace(Regex("\\s+"), " ").trim()
                        quotes.add(Quote(currentRow[0].trim(), sanitizedSource))
                    }
                    currentRow.clear()
                    
                    // Skip following newline characters (handle \r\n or \n\r sequences)
                    if (i + 1 < content.length && (content[i + 1] == '\n' || content[i + 1] == '\r') && content[i + 1] != char) {
                        i++
                    }
                }
                else -> currentField.append(char)
            }
            i++
        }
        
        // Handle last row if no newline at end
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.size >= 2 && currentRow[0].isNotBlank()) {
                val rawSource = currentRow[1]
                val sanitizedSource = rawSource.replace(Regex("\\s+"), " ").trim()
                quotes.add(Quote(currentRow[0].trim(), sanitizedSource))
            }
        }
        
        // Remove header row if it exists (assuming header "Quote" is first col)
        if (quotes.isNotEmpty() && (quotes[0].text.equals("Quote", ignoreCase = true) || quotes[0].text.equals("Quotes", ignoreCase = true))) {
            return quotes.drop(1)
        }
        
        return quotes
    }
}
