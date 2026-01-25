package com.blackgrapes.kadachabuk

import androidx.room.Entity
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a chapter in the application.
 * This class is an Entity for the Room database, meaning it defines a table named "chapters".
 */
@Entity(
    tableName = "chapters",
    // A composite primary key ensures that each chapter is uniquely identified
    // by the combination of its language and serial number. This is crucial for
    // the 'upsert' (update or insert) operation to work correctly.
    primaryKeys = ["languageCode", "serial"]
)
@Parcelize
data class Chapter(
    /**
     * The language code for this chapter (e.g., "en", "bn", "hi").
     * Part of the composite primary key.
     */
    val languageCode: String,

    /**
     * The main heading or title of the chapter.
     */
    val heading: String,
    /**
     * The publication date or relevant date for the chapter.
     */
    val date: String?,
    /**
     * The author or writer of the chapter.
     */
    val writer: String,
    /**
     * The main content or detailed text of the chapter.
     */
    val dataText: String,
    /**
     * A serial number or unique identifier for the chapter within its language.
     */
    val serial: String,
    /**
     * The version of this chapter's content.
     */
    val version: String
) : Parcelable
