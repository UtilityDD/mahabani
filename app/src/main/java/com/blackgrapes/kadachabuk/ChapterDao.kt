package com.blackgrapes.kadachabuk// Create a 'db' package or similar

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.blackgrapes.kadachabuk.Chapter // Import your Chapter entity

@Dao
interface ChapterDao {

    @Upsert
    suspend fun upsertChapters(chapters: List<Chapter>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) // Replace if a chapter with the same primary key (or unique index conflict) exists
    suspend fun insertChapters(chapters: List<Chapter>)

    @Query("SELECT * FROM chapters WHERE languageCode = :languageCode ORDER BY serial ASC") // Or however you want to order
    suspend fun getChaptersByLanguage(languageCode: String): List<Chapter>

    @Query("DELETE FROM chapters WHERE languageCode = :languageCode")
    suspend fun deleteChaptersByLanguage(languageCode: String)

    // Helper to check if a language has any chapters (to decide if initial parse is needed)
    @Query("SELECT COUNT(*) FROM chapters WHERE languageCode = :languageCode")
    suspend fun getChapterCountForLanguage(languageCode: String): Int

    @Query("SELECT DISTINCT languageCode FROM chapters")
    suspend fun getDistinctLanguageCodes(): List<String>

    // Transaction to delete old and insert new chapters for a language atomically
    @Transaction
    suspend fun replaceChaptersForLanguage(languageCode: String, newChapters: List<Chapter>) {
        deleteChaptersByLanguage(languageCode)
        if (newChapters.isNotEmpty()) {
            insertChapters(newChapters)
        }
    }
}