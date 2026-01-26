package com.blackgrapes.kadachabuk

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.blackgrapes.kadachabuk.Chapter

@Database(entities = [Chapter::class, LibraryBook::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun chapterDao(): ChapterDao
    abstract fun libraryBookDao(): LibraryBookDao

    companion object {
        @Volatile // Ensures visibility of this instance to all threads
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) { // synchronized to make it thread-safe
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kada_chabuk_database" // Name of your database file
                )
                    // .addMigrations(MIGRATION_1_2, ...) // Add migrations if you change schema later
                    .fallbackToDestructiveMigration() // For development: if schema changes, recreates DB (data lost!)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}