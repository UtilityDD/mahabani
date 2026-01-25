package com.blackgrapes.kadachabuk

import android.app.Application
import android.util.Log
// import androidx.glance.layout.size // This import seems unused
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class BookViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BookRepository(application.applicationContext)

    private val _chapters = MutableLiveData<List<Chapter>>()
    val chapters: LiveData<List<Chapter>> = _chapters

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _loadingStatusMessage = MutableLiveData<String?>()
    val loadingStatusMessage: LiveData<String?> = _loadingStatusMessage

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _downloadingChaptersList = MutableLiveData<List<ChapterDownloadStatus>>()
    val downloadingChaptersList: LiveData<List<ChapterDownloadStatus>> = _downloadingChaptersList

    private val _showInitialLoadMessage = MutableLiveData<Boolean>()
    val showInitialLoadMessage: LiveData<Boolean> = _showInitialLoadMessage

    private val _aboutInfo = MutableLiveData<Result<String>>()
    val aboutInfo: LiveData<Result<String>> = _aboutInfo

    private val _contributors = MutableLiveData<Result<List<Contributor>>>()
    val contributors: LiveData<Result<List<Contributor>>> = _contributors

    var hasShownInitialAboutDialog = false
    val isFetchingAboutForDialog = MutableLiveData<Boolean>(false)
    // Add a corresponding flag for the Credits dialog
    val isFetchingCreditsForDialog = MutableLiveData<Boolean>(false)

    private var cachedContributors: List<Contributor>? = null

    fun fetchAndLoadChapters(
        languageCode: String,
        languageName: String,
        forceDownload: Boolean = false
    ) {
        viewModelScope.launch {
            // Try to load from DB first without showing a loading screen.
            val chaptersFromDb = repository.getChaptersFromDb(languageCode)
            val needsInitialLoadingScreen = chaptersFromDb.isNullOrEmpty() || forceDownload

            if (chaptersFromDb != null && !forceDownload) {
                _chapters.postValue(chaptersFromDb!!) // Safe because of the null check
                Log.d("BookViewModel", "Instantly loaded ${chaptersFromDb.size} chapters from DB for $languageCode.")
                // Now, check for updates silently in the background.
            } else {
                // Pre-warm the contributors cache in the background on first load.
                fetchContributors(forceRefresh = true, isSilent = true)
            }

            // Pre-warm the contributors cache in the background.
            fetchContributors(forceRefresh = false, isSilent = true)

            if (needsInitialLoadingScreen) {
                _isLoading.postValue(true)
                _showInitialLoadMessage.postValue(true) // Signal to show the message
                _downloadingChaptersList.postValue(emptyList()) // Clear previous list
                _loadingStatusMessage.postValue("Preparing download...")
                _error.postValue(null)
                Log.d("BookViewModel", "Showing loading screen for $languageName ($languageCode). Force refresh: $forceDownload")
            }

            // This will run for both initial load and silent background updates.
            viewModelScope.launch {
                val result = repository.getChaptersForLanguage(
                    languageCode = languageCode,
                    forceRefreshFromServer = forceDownload
                ) { progress: DownloadProgress ->
                    // This callback is now correctly executed for each parsed chapter
                    viewModelScope.launch(Dispatchers.Main) {
                        val newItem = ChapterDownloadStatus(heading = progress.chapter.heading, isDownloaded = true)
                        _downloadingChaptersList.value = (_downloadingChaptersList.value ?: emptyList()) + newItem
                    }
                }

                result.fold(
                    onSuccess = { loadedChapters ->
                        Log.i("BookViewModel", "Successfully loaded/updated ${loadedChapters.size} chapters for $languageCode from repository.")
                        if (loadedChapters.isNotEmpty()) {
                            _chapters.postValue(loadedChapters) // This will update the UI with new data if any
                            _loadingStatusMessage.postValue("Downlaoding chapters...")
                        } else {
                            _chapters.postValue(emptyList())
                            if (_error.value == null) {
                                _loadingStatusMessage.postValue("No chapters found for ${languageCode.uppercase()}.")
                            }
                        }
                    },
                    onFailure = { exception ->
                        Log.e("BookViewModel", "Failed to load chapters for $languageCode from repository", exception)
                        // Only show error if we didn't already load from DB.
                        if (needsInitialLoadingScreen) {
                            val errorMessage = getApplication<Application>().getString(R.string.default_error_message)
                            _error.postValue(errorMessage)
                            _chapters.postValue(emptyList())
                            _loadingStatusMessage.postValue(null)
                        }
                    }
                )
                // Only hide the loading screen if it was shown in the first place.
                if (needsInitialLoadingScreen) {
                    _isLoading.postValue(false)
                    _showInitialLoadMessage.postValue(false) // Signal to hide the message
                }
            }
        }
    }

    fun fetchAboutInfo(languageCode: String, forceRefresh: Boolean = false, isSilent: Boolean = false) {
        viewModelScope.launch {
            val result = repository.getAboutInfo(languageCode, forceRefresh)
            if (!isSilent) { // Only post the value if it's not a silent fetch
                _aboutInfo.postValue(result)
            }
        }
    }

    fun fetchContributors(forceRefresh: Boolean = false, isSilent: Boolean = false) {
        // This logic now mirrors fetchAboutInfo
        viewModelScope.launch {
            val result = repository.getContributors(forceRefresh)
            // Only post the value to trigger the observer if it's not a silent fetch.
            // The repository handles caching, so this will be fast if data exists.
            if (!isSilent) {
                _contributors.postValue(result)
            }
        }
    }

    suspend fun getDownloadedLanguageCodes(): Set<String> {
        return repository.getDownloadedLanguageCodes()
    }

    fun deleteChaptersForLanguage(languageCode: String) {
        viewModelScope.launch {
            repository.deleteChaptersForLanguage(languageCode)
            // If the deleted language is the currently displayed one, clear the list.
            if (_chapters.value?.any { it.languageCode == languageCode } == true) {
                _chapters.postValue(emptyList())
                // You might want to trigger the language selection dialog again here
                // or load a default language. For now, we'll just clear the screen.
                _loadingStatusMessage.postValue("Data for '$languageCode' has been deleted.")
            }
        }
    }

    fun onErrorShown() {
        _error.value = null
    }

    fun onStatusMessageShown() {
        _loadingStatusMessage.value = null
    }
}
