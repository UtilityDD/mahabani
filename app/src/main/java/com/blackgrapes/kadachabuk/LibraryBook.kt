package com.blackgrapes.kadachabuk

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "library_books")
data class LibraryBook(
    @PrimaryKey val bookId: String,
    val sl: String,
    val sheetId: String,
    val versionsGid: String,
    val aboutGid: String,
    val contributorsGid: String,
    // Store GID map as a simple flattened string or structured map if converter exists
    val bnGid: String = "",
    val hiGid: String = "",
    val enGid: String = "",
    val asGid: String = "",
    val odGid: String = "",
    val tmGid: String = "",
    
    // Store localized names for bookshelf
    val bnName: String = "",
    val hiName: String = "",
    val enName: String = "",
    val asName: String = "",
    val odName: String = "",
    val tmName: String = "",

    // Subnames
    val bnSubName: String = "",
    val hiSubName: String = "",
    val enSubName: String = "",
    val asSubName: String = "",
    val odSubName: String = "",
    val tmSubName: String = "",

    // Years
    val bnYear: String = "",
    val hiYear: String = "",
    val enYear: String = "",
    val asYear: String = "",
    val odYear: String = "",
    val tmYear: String = ""
) {
    // --- Helper Extensions for LibraryBook Localization ---
    fun getLocalizedName(lang: String): String {
        return when(lang) {
            "bn" -> bnName
            "hi" -> hiName
            "en" -> enName
            "as" -> asName
            "od" -> odName
            "tm" -> tmName
            else -> enName
        }.ifEmpty { enName }
    }

    fun getLocalizedSubName(lang: String): String {
        return when(lang) {
            "bn" -> bnSubName
            "hi" -> hiSubName
            "en" -> enSubName
            "as" -> asName // Use name as fallback for missing subname to avoid empty string on spine
            "od" -> odSubName
            "tm" -> tmSubName
            else -> enSubName
        }.ifEmpty { enSubName }
    }

    fun getLocalizedYear(lang: String): String {
        return when(lang) {
            "bn" -> bnYear
            "hi" -> hiYear
            "en" -> enYear
            "as" -> asYear
            "od" -> odYear
            "tm" -> tmYear
            else -> enYear
        }.ifEmpty { enYear }
    }
}
