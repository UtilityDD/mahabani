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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<Chapter>)

    @Query("SELECT * FROM chapters WHERE languageCode = :languageCode AND bookId = :bookId ORDER BY CAST(serial AS INTEGER) ASC")
    suspend fun getChaptersByLanguageAndBook(languageCode: String, bookId: String): List<Chapter>

    @Query("DELETE FROM chapters WHERE languageCode = :languageCode AND bookId = :bookId")
    suspend fun deleteChaptersByLanguageAndBook(languageCode: String, bookId: String)

    @Query("SELECT COUNT(*) FROM chapters WHERE languageCode = :languageCode AND bookId = :bookId")
    suspend fun getChapterCount(languageCode: String, bookId: String): Int

    @Query("SELECT DISTINCT languageCode FROM chapters WHERE bookId = :bookId")
    suspend fun getDownloadedLanguageCodes(bookId: String): List<String>

    @Query("UPDATE chapters SET isRead = :isRead WHERE languageCode = :languageCode AND bookId = :bookId AND serial = :serial")
    suspend fun updateReadStatus(languageCode: String, bookId: String, serial: String, isRead: Boolean)

    @Query("SELECT COUNT(*) FROM chapters WHERE languageCode = :languageCode AND bookId = :bookId AND isRead = 1")
    suspend fun getReadChaptersCount(languageCode: String, bookId: String): Int

    @Transaction
    suspend fun replaceChapters(languageCode: String, bookId: String, newChapters: List<Chapter>) {
        deleteChaptersByLanguageAndBook(languageCode, bookId)
        if (newChapters.isNotEmpty()) {
            insertChapters(newChapters)
        }
    }
}