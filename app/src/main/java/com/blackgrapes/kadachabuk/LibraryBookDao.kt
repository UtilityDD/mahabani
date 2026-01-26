package com.blackgrapes.kadachabuk

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LibraryBookDao {
    @Upsert
    suspend fun upsertBooks(books: List<LibraryBook>)

    @Query("SELECT * FROM library_books ORDER BY sl ASC")
    suspend fun getAllBooks(): List<LibraryBook>

    @Query("SELECT * FROM library_books WHERE bookId = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): LibraryBook?

    @Query("DELETE FROM library_books")
    suspend fun deleteAllBooks()
}
