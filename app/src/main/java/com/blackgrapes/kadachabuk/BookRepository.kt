package com.blackgrapes.kadachabuk

import android.content.Context
import android.util.Log
import com.blackgrapes.kadachabuk.AppDatabase // Ensure this path is correct
// import com.blackgrapes.kadachabuk.ChapterDao // DAO is accessed via AppDatabase instance
// import com.blackgrapes.kadachabuk.Chapter // Your Chapter entity
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader // For parsing CSV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileReader
import java.io.InputStream
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.net.URL

data class DownloadProgress(
    val chapter: Chapter)

private const val GOOGLE_SHEET_URL_SUFFIX = "&format=csv"
private const val CONTRIBUTORS_PREFS = "ContributorsPrefs"
private const val ABOUT_INFO_PREFS = "AboutInfoPrefs"
private const val VERSION_INFO_PREFS = "VersionInfoPrefs"
private const val VIDEO_LINKS_PREFS = "VideoLinksPrefs"

private const val VIDEO_SHEET_ID = "1wZSxXRZHkgbTG3oPDJn_JbKy4m3BWELah67XcgBz6BA"
private const val VIDEO_GID = "1681780330"

private const val LIBRARY_METADATA_URL = "https://docs.google.com/spreadsheets/d/1wZSxXRZHkgbTG3oPDJn_JbKy4m3BWELah67XcgBz6BA/export?gid=0$GOOGLE_SHEET_URL_SUFFIX"

class BookRepository(private val context: Context) {

    // Get an instance of the DAO from the AppDatabase
    private val database = AppDatabase.getDatabase(context)
    private val chapterDao = database.chapterDao()
    private val libraryBookDao = database.libraryBookDao()

    // --- Temporary CSV File handling for download and parse ---
    private fun getTemporaryCsvFile(languageCode: String): File {
        val dir = File(context.cacheDir, "csv_temp_downloads") // Use cacheDir for temp files
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "chapters_temp_${languageCode.lowercase()}.csv")
    }

    private suspend fun getCsvUrlForLanguage(bookId: String, languageCode: String): URL? {
        val metadata = libraryBookDao.getBookById(bookId.lowercase()) ?: return null
        val gid = when(languageCode.lowercase()) {
            "bn" -> metadata.bnGid
            "hi" -> metadata.hiGid
            "en" -> metadata.enGid
            "as" -> metadata.asGid
            "od" -> metadata.odGid
            "tm" -> metadata.tmGid
            else -> ""
        }
        if (gid.isEmpty()) {
            Log.e("BookRepository", "No GID found for book $bookId and language $languageCode")
            return null
        }
        val urlString = "https://docs.google.com/spreadsheets/d/${metadata.sheetId}/export?gid=$gid$GOOGLE_SHEET_URL_SUFFIX"
        return try {
            URL(urlString)
        } catch (e: Exception) {
            Log.e("BookRepository", "Malformed URL for $bookId / $languageCode: $urlString", e)
            null
        }
    }

    private suspend fun getVersionsSheetUrl(bookId: String): URL? {
        val metadata = libraryBookDao.getBookById(bookId.lowercase()) ?: return null
        val urlString = "https://docs.google.com/spreadsheets/d/${metadata.sheetId}/export?gid=${metadata.versionsGid}$GOOGLE_SHEET_URL_SUFFIX"
        return try {
            URL(urlString)
        } catch (e: Exception) {
            Log.e("BookRepository", "Malformed URL for $bookId versions: $urlString", e)
            null
        }
    }

    private suspend fun getAboutSheetUrl(bookId: String): URL? {
        val metadata = libraryBookDao.getBookById(bookId.lowercase()) ?: return null
        val urlString = "https://docs.google.com/spreadsheets/d/${metadata.sheetId}/export?gid=${metadata.aboutGid}$GOOGLE_SHEET_URL_SUFFIX"
        return try {
            URL(urlString)
        } catch (e: Exception) {
            Log.e("BookRepository", "Malformed URL for $bookId about: $urlString", e)
            null
        }
    }

    private suspend fun getContributorsSheetUrl(bookId: String): URL? {
        val metadata = libraryBookDao.getBookById(bookId.lowercase()) ?: return null
        val urlString = "https://docs.google.com/spreadsheets/d/${metadata.sheetId}/export?gid=${metadata.contributorsGid}$GOOGLE_SHEET_URL_SUFFIX"
        return try {
            URL(urlString)
        } catch (e: Exception) {
            Log.e("BookRepository", "Malformed URL for $bookId contributors: $urlString", e)
            null
        }
    }

    /**
     * Quickly fetches chapters for a language directly from the database without any network calls.
     * Returns null if no chapters are found.
     */
    suspend fun getChaptersFromDb(bookId: String, languageCode: String): List<Chapter>? {
        return withContext(Dispatchers.IO) {
            if (chapterDao.getChapterCount(languageCode, bookId) > 0) {
                Log.i("BookRepository", "DB QUICK FETCH for $bookId/$languageCode. Loading from database.")
                val chaptersFromDb = chapterDao.getChaptersByLanguageAndBook(languageCode, bookId)
                chaptersFromDb.sortedBy { it.serial.toIntOrNull() ?: Int.MAX_VALUE }
            } else {
                null
            }
        }
    }

    /**
     * Fetches chapters for the given language.
     * Checks the local Room database first. If data is found and forceRefresh is false,
     * it returns the cached data. Otherwise, it downloads the CSV, parses it,
     * updates the database, and then returns the new data.
     */
    suspend fun getChaptersForLanguage(
        bookId: String,
        languageCode: String,
        forceRefreshFromServer: Boolean = false,
        onProgress: (DownloadProgress) -> Unit = {} // Callback for progress
    ): Result<List<Chapter>> {
        return withContext(Dispatchers.IO) {
            try {
                val versionPrefs = context.getSharedPreferences(VERSION_INFO_PREFS, Context.MODE_PRIVATE)
                val localVersion = versionPrefs.getString("version_${bookId}_$languageCode", "0.0") ?: "0.0"
                val remoteVersionResult = getRemoteMasterVersion(bookId, languageCode)
                val remoteVersion = remoteVersionResult.getOrNull()

                // Decide if a download is needed
                val needsDownload = if (forceRefreshFromServer) {
                    Log.i("BookRepository", "Force refresh triggered for $languageCode.")
                    true
                } else if (remoteVersion == null) {
                    Log.w("BookRepository", "Could not fetch remote version for $languageCode. Will rely on local DB if available.")
                    false // Cannot compare, so don't download.
                } else {
                    (remoteVersion.toFloatOrNull() ?: 0.0f) > (localVersion.toFloatOrNull() ?: 0.0f)
                }

                if (!needsDownload && chapterDao.getChapterCount(languageCode, bookId) > 0) {
                    Log.i("BookRepository", "DB CACHE HIT for $bookId/$languageCode (v$localVersion). Loading from database.")
                    val chaptersFromDb = chapterDao.getChaptersByLanguageAndBook(languageCode, bookId)
                    val sortedChapters = chaptersFromDb.sortedBy { it.serial.toIntOrNull() ?: Int.MAX_VALUE }                    
                    return@withContext Result.success(sortedChapters)
                } else {
                    if (forceRefreshFromServer) {
                        Log.i("BookRepository", "FORCE REFRESH requested for $languageCode. Fetching from server.")
                    } else {
                        Log.i("BookRepository", "DB CACHE MISS for $languageCode. Fetching from server.")
                    }

                    if (needsDownload && remoteVersion != null) {
                        Log.i("BookRepository", "New version available for $languageCode. Remote: v$remoteVersion, Local: v$localVersion. Downloading...")
                    }

                    // Download the CSV to a temporary file first
                    val downloadResult = downloadCsvToTempFile(bookId, languageCode)

                    downloadResult.fold(
                        onSuccess = { tempCsvFile ->
                            Log.d("BookRepository", "CSV downloaded to temp file for $languageCode. Parsing and updating DB.")

                            // Parse the CSV stream from the temporary file
                            val parseResult = parseCsvStreamInternal(
                                bookId = bookId,
                                languageCodeForLog = languageCode,
                                csvInputStream = FileInputStream(tempCsvFile),
                                onProgress = onProgress
                            )

                            tempCsvFile.delete() // Clean up the temp file

                            parseResult.fold(
                                onSuccess = { parsedChaptersWithoutLang ->
                            // The languageCode is now part of the Chapter object from parsing.
                            val chaptersToStoreInDb = parsedChaptersWithoutLang

                            // --- SMART UPDATE LOGIC ---
                            // Get all existing chapters for this language/book from the DB to compare versions.
                            val existingChapters = chapterDao.getChaptersByLanguageAndBook(languageCode, bookId)
                            val existingChapterMap = existingChapters.associateBy { it.serial }

                            val chaptersToUpdate = chaptersToStoreInDb.filter { newChapter ->
                                val existingChapter = existingChapterMap[newChapter.serial]
                                // Update if the chapter is new (not in the map) or if the version is different.
                                existingChapter == null || existingChapter.version != newChapter.version
                            }

                            if (chaptersToUpdate.isNotEmpty()) {
                                Log.i("BookRepository", "Smart Update: Found ${chaptersToUpdate.size} new/updated chapters for $languageCode. Updating database.")
                                // CORRECTED LOGIC: Instead of replacing all, we now "upsert" (update or insert) only the changed chapters.
                                // This leaves the unchanged chapters in the database untouched.
                                chapterDao.upsertChapters(chaptersToUpdate)
                            } else {
                                Log.i("BookRepository", "Smart Update: No new or changed chapters found for $languageCode. Database is already up-to-date.")
                            }

                            // On successful update, save the new master version
                            if (remoteVersion != null) {
                                versionPrefs.edit().putString("version_${bookId}_$languageCode", remoteVersion).apply()
                            }
                            // Return the full, sorted list of chapters for the UI.
                            Result.success(chapterDao.getChaptersByLanguageAndBook(languageCode, bookId).sortedBy { it.serial.toIntOrNull() ?: Int.MAX_VALUE })
                                },
                                onFailure = { parsingException ->
                                    Log.e("BookRepository", "Failed to parse CSV for $languageCode after download.", parsingException)
                                    Result.failure(parsingException)
                                }
                            )
                        },
                        onFailure = { downloadException ->
                            Log.e("BookRepository", "Failed to download or parse CSV for $bookId/$languageCode.", downloadException)
                            // If download fails, but we have old data, return the old data to prevent a blank screen.
                            val chaptersFromDb = chapterDao.getChaptersByLanguageAndBook(languageCode, bookId)
                            if (chaptersFromDb.isNotEmpty()) {
                                Log.w("BookRepository", "Download failed, but returning stale data from DB for $bookId/$languageCode.")
                                return@fold Result.success(chaptersFromDb.sortedBy { it.serial.toIntOrNull() ?: Int.MAX_VALUE })
                            }
                            Result.failure(downloadException)
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Error in getChaptersForLanguage for $languageCode", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Fetches the master version string for a given language from the 'versions' sheet.
     */
    private suspend fun getRemoteMasterVersion(bookId: String, languageCode: String): Result<String> {
        return withContext(Dispatchers.IO) {
            val url = getVersionsSheetUrl(bookId) ?: return@withContext Result.failure(Exception("Could not create URL for versions sheet of $bookId"))
            var connection: HttpURLConnection? = null
            try {
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000 
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val version = csvReader { skipEmptyLine = true }.open(connection.inputStream) {
                        readAllAsSequence()
                            .drop(1) // Skip header
                            .firstOrNull { row -> row.size >= 2 && row[0].trim().equals(languageCode, ignoreCase = true) }
                            ?.get(1)?.trim()
                    }
                    if (version != null) {
                        Result.success(version)
                    } else {
                        Result.failure(Exception("Version not found for language '$languageCode' in versions sheet."))
                    }
                } else {
                    Result.failure(Exception("Failed to download versions sheet: ${connection.responseCode}"))
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Error fetching remote master version", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Downloads the CSV for the given language to a temporary local file.
     * Returns a Result containing the File object.
     * The caller is responsible for deleting the temp file.
     */
    private suspend fun downloadCsvToTempFile(bookId: String, languageCode: String): Result<File> {
        val downloadUrl = getCsvUrlForLanguage(bookId, languageCode)
            ?: return Result.failure(IllegalArgumentException("Could not construct URL for book: $bookId, language: $languageCode"))

        Log.d("BookRepository", "Downloading CSV for $languageCode from: $downloadUrl to a temporary file.")
        val tempFile = getTemporaryCsvFile(languageCode)
        
        val maxRetries = 3
        var currentAttempt = 0
        var lastException: Exception? = null

        while (currentAttempt < maxRetries) {
            currentAttempt++
            var connection: HttpURLConnection? = null
            try {
                if (currentAttempt > 1) {
                    val backoff = 2000L
                    Log.d("BookRepository", "Retry attempt $currentAttempt for download in ${backoff}ms...")
                    kotlinx.coroutines.delay(backoff)
                }

                connection = downloadUrl.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.connect()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    var totalBytesRead = 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                totalBytesRead += bytesRead
                            }
                            output.flush()
                        }
                    }
                    if (totalBytesRead > 0) {
                        Log.i("BookRepository", "CSV downloaded ($totalBytesRead bytes) to temp file: ${tempFile.absolutePath} (Attempt $currentAttempt)")
                        return Result.success(tempFile)
                    } else {
                        Log.w("BookRepository", "Downloaded CSV for $languageCode was empty (Attempt $currentAttempt).")
                        // Treat empty file as a failure to trigger retry
                        throw Exception("Downloaded CSV for $languageCode was empty.")
                    }
                } else {
                    Log.e("BookRepository", "Download failed: $responseCode ${connection.responseMessage} (Attempt $currentAttempt)")
                    throw Exception("Download failed: $responseCode ${connection.responseMessage}")
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Exception during CSV download attempt $currentAttempt", e)
                lastException = e
            } finally {
                connection?.disconnect()
            }
        }
        
        // If we exhausted all retries
        if (tempFile.exists()) tempFile.delete()
        return Result.failure(lastException ?: Exception("Unknown download failure after $maxRetries attempts"))
    }

    /**
     * Internal CSV parsing logic.
     * Parses the given InputStream and returns a list of Chapter objects (without languageCode set).
     */
    private suspend fun parseCsvStreamInternal(
        bookId: String,
        languageCodeForLog: String,
        csvInputStream: InputStream,
        onProgress: (DownloadProgress) -> Unit
    ): Result<List<Chapter>> {
        return try {
            Log.d("BookRepository", "Starting internal CSV parsing for $languageCodeForLog.")
                val chapterList = mutableListOf<Chapter>()
                val isHeaderRow = { row: List<String> ->
                    row.any {
                        it.trim().equals("heading", ignoreCase = true) ||
                                it.trim().equals("date", ignoreCase = true) ||
                                it.trim().equals("writer", ignoreCase = true) ||
                                it.trim().equals("serial", ignoreCase = true)
                    }
                }

                var header: List<String>? = null
                
                csvReader { skipEmptyLine = true }.open(csvInputStream) { // csvInputStream will be closed by this block
                    readAllAsSequence().forEachIndexed forEach@{ index, row ->
                        if (header == null) {
                            if (isHeaderRow(row)) {
                                header = row.map { it.trim().lowercase() }
                                return@forEach
                            } else {
                                // Default header if missing
                                header = listOf("heading", "date", "writer", "data", "serial", "version", "audiolink")
                            }
                        }
                        
                        try {
                            val h = header!!
                            val headingIdx = h.indexOf("heading").let { if (it == -1) 0 else it }
                            val dateIdx = h.indexOf("date").let { if (it == -1) 1 else it }
                            val writerIdx = h.indexOf("writer").let { if (it == -1) 2 else it }
                            val dataIdx = h.indexOf("data").let { if (it == -1) 3 else it }
                            val serialIdx = h.indexOf("serial").let { if (it == -1) 4 else it }
                            val versionIdx = h.indexOf("version").let { if (it == -1) 5 else it }
                            val audioIdx = h.indexOf("audiolink").let { if (it == -1) 6 else it }

                            if (row.size >= 5) { 
                                val chapter = Chapter(
                                    languageCode = languageCodeForLog,
                                    bookId = bookId,
                                    heading = row.getOrNull(headingIdx)?.trim() ?: "Unknown Heading",
                                    date = row.getOrNull(dateIdx)?.trim()?.let { if (it.isNotEmpty()) it else null },
                                    writer = row.getOrNull(writerIdx)?.trim() ?: "Unknown Writer",
                                    dataText = row.getOrNull(dataIdx)?.trim() ?: "No Data",
                                    serial = row.getOrNull(serialIdx)?.trim() ?: "N/A",
                                    version = row.getOrNull(versionIdx)?.trim() ?: "N/A",
                                    audioLink = row.getOrNull(audioIdx)?.trim()?.let { if (it.isEmpty()) null else it }
                                )
                                chapterList.add(chapter)
                                val progress = DownloadProgress(chapter = chapter)
                                onProgress(progress)
                            }
                        } catch (e: Exception) {
                            Log.e("BookRepository", "Error parsing chapter row for $languageCodeForLog at index $index: ${e.message}", e)
                        }
                    }
                }
            Log.i("BookRepository", "Internal CSV parsing complete for $languageCodeForLog. Found ${chapterList.size} chapters.")
            Result.success(chapterList)
        } catch (e: Exception) {
            Log.e("BookRepository", "Error during internal CSV parsing for $languageCodeForLog", e)
            Result.failure(e)
        }
    }

    suspend fun getAboutInfo(bookId: String, languageCode: String, forceRefresh: Boolean = false): Result<String> {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(ABOUT_INFO_PREFS, Context.MODE_PRIVATE)
            val cacheKey = "about_${bookId}_$languageCode"

            if (!forceRefresh) {
                val cachedInfo = prefs.getString(cacheKey, null)
                if (cachedInfo != null) {
                    Log.d("BookRepository", "CACHE HIT for about info: $languageCode")
                    return@withContext Result.success(cachedInfo)
                }
            }

            Log.d("BookRepository", "CACHE MISS for about info: $bookId/$languageCode. Fetching from network.")
            try {
                val url = getAboutSheetUrl(bookId) ?: return@withContext Result.failure(Exception("Could not create URL for about sheet of $bookId"))
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val aboutText = parseAboutCsv(connection.inputStream, languageCode)
                    if (aboutText != null) {
                        // Save to cache before returning
                        prefs.edit().putString(cacheKey, aboutText).apply()
                        Log.d("BookRepository", "Saved about info for $languageCode to cache.")
                        Result.success(aboutText)
                    } else {
                        Result.failure(Exception("About info not found for language: $languageCode"))
                    }
                } else {
                    Result.failure(Exception("Failed to download about info: ${connection.responseCode}"))
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Error fetching about info", e)
                Result.failure(e)
            }
        }
    }

    private fun parseAboutCsv(inputStream: InputStream, languageCode: String): String? {
        return csvReader { skipEmptyLine = true }.open(inputStream) {
            readAllAsSequence()
                .drop(1) // Skip header row
                .firstOrNull { row ->
                    // Find the first row where the language code matches
                    row.size >= 2 && row[0].trim().equals(languageCode, ignoreCase = true)
                }
                ?.get(1) // If a row is found, get the second column (the "about" text)
                ?.trim() // And trim it
        }
    }

    // The old getChapterCsvInputStream and downloadAndSaveCsv can now be removed
    // if getChaptersForLanguage is the sole entry point for fetching chapter data.
    // If you need to keep them for other purposes, you can, but they are not used
    // by the getChaptersForLanguage method above.
    suspend fun getContributors(bookId: String, forceRefresh: Boolean = false): Result<List<Contributor>> {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(CONTRIBUTORS_PREFS, Context.MODE_PRIVATE)
            val cacheKey = "contributors_${bookId}_list"

            if (!forceRefresh) {
                val cachedJson = prefs.getString(cacheKey, null)
                if (cachedJson != null) {
                    Log.d("BookRepository", "CACHE HIT for contributors list.")
                    return@withContext Result.success(parseContributorsJson(cachedJson))
                }
            }

            Log.d("BookRepository", "CACHE MISS for contributors list. Fetching from network for $bookId.")
            try {
                val url = getContributorsSheetUrl(bookId) ?: return@withContext Result.failure(Exception("Could not create URL for contributors sheet of $bookId"))
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val contributors = parseContributorsCsv(connection.inputStream)
                    // Cache the result
                    val jsonToCache = contributorsToJson(contributors)
                    prefs.edit().putString(cacheKey, jsonToCache).apply()
                    Log.d("BookRepository", "Saved contributors list to cache.")
                    Result.success(contributors)
                } else {
                    Result.failure(Exception("Failed to download contributors sheet: ${connection.responseCode}"))
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Error fetching contributors", e)
                // If network fails, try to return from cache as a last resort
                val cachedJson = prefs.getString(cacheKey, null)
                if (cachedJson != null) {
                    Result.success(parseContributorsJson(cachedJson))
                } else {
                    Result.failure(e)
                }
            }
        }
    }
    
    private fun parseContributorsCsv(inputStream: InputStream): List<Contributor> {
        val contributors = mutableListOf<Contributor>()
        csvReader { skipEmptyLine = true }.open(inputStream) {
            readAllAsSequence().drop(1).forEach { row ->
                if (row.size >= 2) {
                    contributors.add(
                        Contributor(
                            name = row[0].trim(),
                            address = row[1].trim()
                        )
                    )
                }
            }
        }
        return contributors
    }

    private fun contributorsToJson(contributors: List<Contributor>): String {
        val jsonArray = org.json.JSONArray()
        contributors.forEach {
            val jsonObject = org.json.JSONObject()
            jsonObject.put("name", it.name)
            jsonObject.put("address", it.address)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    private fun parseContributorsJson(jsonString: String): List<Contributor> {
        val contributors = mutableListOf<Contributor>()
        val jsonArray = org.json.JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            contributors.add(Contributor(jsonObject.getString("name"), jsonObject.getString("address")))
        }
        return contributors
    }

    /**
     * Deletes all chapters and associated preferences for a given language.
     */
    suspend fun deleteChaptersForLanguage(bookId: String, languageCode: String) {
        withContext(Dispatchers.IO) {
            // Delete from Room database
            chapterDao.deleteChaptersByLanguageAndBook(languageCode, bookId)

            // Delete version info from SharedPreferences
            val versionPrefs = context.getSharedPreferences(VERSION_INFO_PREFS, Context.MODE_PRIVATE)
            versionPrefs.edit().remove("version_${bookId}_$languageCode").apply()

            Log.i("BookRepository", "Deleted all data for $bookId language: $languageCode")
        }
    }

    /**
     * Returns a set of language codes for which chapters exist in the database.
     */
    suspend fun getDownloadedLanguageCodes(bookId: String): Set<String> {
        return withContext(Dispatchers.IO) {
            chapterDao.getDownloadedLanguageCodes(bookId).toSet()
        }
    }

    suspend fun markChapterAsRead(languageCode: String, bookId: String, serial: String) {
        val normalizedSerial = if (serial.isEmpty()) "" else try {
            serial.toInt().toString().padStart(2, '0')
        } catch (e: Exception) {
            serial
        }
        chapterDao.updateReadStatus(languageCode, bookId, normalizedSerial, true)
    }
    
    suspend fun getNextChapter(languageCode: String, bookId: String, currentSerial: String): Chapter? {
        val normalizedSerial = if (currentSerial.isEmpty()) "00" else try {
            currentSerial.toInt().toString().padStart(2, '0')
        } catch (e: Exception) {
            currentSerial
        }
        return chapterDao.getNextChapter(languageCode, bookId, normalizedSerial)
    }

    suspend fun getPreviousChapter(languageCode: String, bookId: String, currentSerial: String): Chapter? {
        val normalizedSerial = if (currentSerial.isEmpty()) "00" else try {
            currentSerial.toInt().toString().padStart(2, '0')
        } catch (e: Exception) {
            currentSerial
        }
        return chapterDao.getPreviousChapter(languageCode, bookId, normalizedSerial)
    }

    suspend fun getBookProgress(languageCode: String, bookId: String): Int {
        return withContext(Dispatchers.IO) {
            val total = chapterDao.getChapterCount(languageCode, bookId)
            if (total == 0) return@withContext 0
            val read = chapterDao.getReadChaptersCount(languageCode, bookId)
            (read * 100) / total
        }
    }

    /**
     * Fetches the dynamic library metadata from the remote spreadsheet.
     */
    suspend fun getLibraryMetadata(): Result<List<LibraryBook>> {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(LIBRARY_METADATA_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val books = parseLibraryCsv(connection.inputStream).sortedBy { it.sl }
                    libraryBookDao.upsertBooks(books)
                    Result.success(books)
                } else {
                    Result.failure(Exception("Failed to download library metadata: ${connection.responseCode}"))
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Error fetching library metadata", e)
                Result.failure(e)
            }
        }
    }

    suspend fun getLibraryBooksFromDb(): List<LibraryBook> {
        return libraryBookDao.getAllBooks()
    }

    private fun parseLibraryCsv(inputStream: InputStream): List<LibraryBook> {
        val books = mutableListOf<LibraryBook>()
        val langCodes = listOf("bn", "hi", "en", "as", "od", "tm")
        
        csvReader { skipEmptyLine = true }.open(inputStream) {
            val rows = readAllAsSequence().toList()
            if (rows.isEmpty()) return@open
            
            val header = rows[0].map { it.trim().lowercase() }
            
            rows.drop(1).forEach { row ->
                if (row.isEmpty()) return@forEach
                
                try {
                    val slRaw = row.getOrNull(header.indexOf("sl"))?.trim() ?: ""
                    if (slRaw.isEmpty()) return@forEach
                    
                    val sl = if (slRaw.length == 1 && slRaw[0].isDigit()) "0$slRaw" else slRaw
                    
                    val bookId = when(sl) {
                        "01" -> "kada_chabuk"
                        "02" -> "shaishab_kahini"
                        else -> "book_$sl"
                    }

                    books.add(LibraryBook(
                        bookId = bookId,
                        sl = sl,
                        sheetId = row.getOrNull(header.indexOf("sheet_id"))?.trim() ?: "",
                        versionsGid = row.getOrNull(header.indexOf("versions_gid"))?.trim() ?: "",
                        aboutGid = row.getOrNull(header.indexOf("about_gid"))?.trim() ?: "1925993700", // Kept original default
                        contributorsGid = row.getOrNull(header.indexOf("contributors_gid"))?.trim() ?: "1786621690", // Kept original default
                        bnGid = row.getOrNull(header.indexOf("bn_gid"))?.trim() ?: "",
                        hiGid = row.getOrNull(header.indexOf("hi_gid"))?.trim() ?: "",
                        enGid = row.getOrNull(header.indexOf("en_gid"))?.trim() ?: "",
                        asGid = row.getOrNull(header.indexOf("as_gid"))?.trim() ?: "",
                        odGid = row.getOrNull(header.indexOf("od_gid"))?.trim() ?: "",
                        tmGid = row.getOrNull(header.indexOf("tm_gid"))?.trim() ?: "",
                        bnName = row.getOrNull(header.indexOf("bn_name"))?.trim() ?: "",
                        hiName = row.getOrNull(header.indexOf("hi_name"))?.trim() ?: "",
                        enName = row.getOrNull(header.indexOf("en_name"))?.trim() ?: "",
                        asName = row.getOrNull(header.indexOf("as_name"))?.trim() ?: "",
                        odName = row.getOrNull(header.indexOf("od_name"))?.trim() ?: "",
                        tmName = row.getOrNull(header.indexOf("tm_name"))?.trim() ?: "",
                        bnSubName = row.getOrNull(header.indexOf("bn_subname"))?.trim() ?: "",
                        hiSubName = row.getOrNull(header.indexOf("hi_subname"))?.trim() ?: "",
                        enSubName = row.getOrNull(header.indexOf("en_subname"))?.trim() ?: "",
                        asSubName = row.getOrNull(header.indexOf("as_subname"))?.trim() ?: "",
                        odSubName = row.getOrNull(header.indexOf("od_subname"))?.trim() ?: "",
                        tmSubName = row.getOrNull(header.indexOf("tm_subname"))?.trim() ?: "",
                        bnYear = row.getOrNull(header.indexOf("bn_year"))?.trim() ?: "",
                        hiYear = row.getOrNull(header.indexOf("hi_year"))?.trim() ?: "",
                        enYear = row.getOrNull(header.indexOf("en_year"))?.trim() ?: "",
                        asYear = row.getOrNull(header.indexOf("as_year"))?.trim() ?: "",
                        odYear = row.getOrNull(header.indexOf("od_year"))?.trim() ?: "",
                        tmYear = row.getOrNull(header.indexOf("tm_year"))?.trim() ?: "",
                        audioLink = row.getOrNull(header.indexOf("audiolink"))?.trim() ?: ""
                    ))
                } catch (e: Exception) {
                    Log.e("BookRepository", "Error parsing library row: ${e.message}", e)
                }
            }
        }
        return books
    }

    suspend fun getVideos(forceRefresh: Boolean = false): Result<List<Video>> {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(VIDEO_LINKS_PREFS, Context.MODE_PRIVATE)
            val cacheKey = "video_links_csv"
            
            // 1. Try Cache First
            if (!forceRefresh) {
                val cachedCsv = prefs.getString(cacheKey, null)
                if (!cachedCsv.isNullOrEmpty()) {
                    Log.d("BookRepository", "Video Cache Hit. Parsing cached CSV.")
                    val cachedVideos = parseVideoCsv(cachedCsv)
                    if (cachedVideos.isNotEmpty()) {
                        return@withContext Result.success(cachedVideos)
                    }
                }
            }

            // 2. Fetch from Network
            Log.d("BookRepository", "Video Cache Miss or Force Refresh. Fetching from network.")
            try {
                val urlString = "https://docs.google.com/spreadsheets/d/$VIDEO_SHEET_ID/export?gid=$VIDEO_GID$GOOGLE_SHEET_URL_SUFFIX"
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val csvData = connection.inputStream.bufferedReader().use { it.readText() }
                    if (csvData.isNotEmpty() && !csvData.startsWith("PK")) { // PK check for ZIP/Excel
                        // Save to cache
                        prefs.edit().putString(cacheKey, csvData).apply()
                        Log.d("BookRepository", "Video CSV saved to cache.")
                        
                        val videos = parseVideoCsv(csvData)
                        Result.success(videos)
                    } else {
                        Result.failure(Exception("Downloaded CSV layout is invalid or binary."))
                    }
                } else {
                    Result.failure(Exception("Failed to download videos: ${connection.responseCode}"))
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Error fetching videos", e)
                // Fallback to cache if network fails
                val cachedCsv = prefs.getString(cacheKey, null)
                if (!cachedCsv.isNullOrEmpty()) {
                    Result.success(parseVideoCsv(cachedCsv))
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    private fun parseVideoCsv(csvData: String): List<Video> {
        val videos = mutableListOf<Video>()
        try {
            csvReader { skipEmptyLine = true }.readAllWithHeader(csvData).forEach { row ->
                // Columns: sl, link, remark, category
                val sl = row["sl"]?.trim() ?: ""
                val rawLink = row["link"]?.trim() ?: ""
                val remark = row["remark"]?.trim() ?: ""
                val category = row["category"]?.trim() ?: ""

                val link = extractVideoUrl(rawLink)
                if (link.isNotEmpty()) {
                    videos.add(Video(sl, link, remark, category))
                }
            }
        } catch (e: Exception) {
            Log.e("BookRepository", "Error parsing video CSV", e)
        }
        return videos
    }

    private fun extractVideoUrl(input: String): String {
        // 1. YouTube Iframe
        if (input.contains("<iframe") && input.contains("youtube.com/embed/")) {
            val srcPattern = """src="([^"]*youtube\.com/embed/[^"]*)"""".toRegex()
            val match = srcPattern.find(input)
            if (match != null) {
                val embedUrl = match.groupValues[1]
                val videoId = embedUrl.substringAfter("youtube.com/embed/").substringBefore("?")
                return "https://www.youtube.com/watch?v=$videoId"
            }
        }
        
        // 2. Facebook Iframe
        if (input.contains("<iframe") && input.contains("facebook.com/")) {
            val srcPattern = """src="([^"]*facebook\.com/[^"]*)"""".toRegex()
            val match = srcPattern.find(input)
            if (match != null) {
                val embedUrl = match.groupValues[1]
                if (embedUrl.contains("href=")) {
                    val actualUrl = embedUrl.substringAfter("href=").substringBefore("&").replace("%3A", ":").replace("%2F", "/")
                    return actualUrl
                }
                return embedUrl
            }
        }

        // 3. Direct YouTube Link
        if (input.contains("youtube.com") || input.contains("youtu.be")) {
            return input
        }

        // 4. Direct Facebook Link
        if (input.contains("facebook.com") || input.contains("fb.watch") || input.contains("fb.gg")) {
            return input
        }
        
        // Return original if it looks like a URL but not an iframe
        if (!input.contains("<") && (input.startsWith("http") || input.contains("."))) {
            return input
        }
        
        return ""
    }
}
