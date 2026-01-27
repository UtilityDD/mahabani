package com.blackgrapes.kadachabuk

import android.content.DialogInterface
import android.content.Intent
import com.blackgrapes.kadachabuk.VideoActivity
import android.app.Dialog
import android.database.MatrixCursor
import android.animation.ValueAnimator
import android.provider.BaseColumns
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.graphics.Color
import android.view.View
import android.widget.ScrollView
import android.graphics.drawable.ColorDrawable
import android.view.Menu
import android.widget.ImageView
import android.view.MenuItem
import android.widget.Button
import androidx.activity.result.IntentSenderRequest
import androidx.core.graphics.ColorUtils
import android.widget.CheckBox
import java.util.concurrent.TimeUnit
import android.graphics.PorterDuff
import android.widget.ProgressBar
import android.view.animation.AnimationUtils
import android.view.ViewGroup
import androidx.cursoradapter.widget.SimpleCursorAdapter
import androidx.recyclerview.widget.LinearSmoothScroller
import android.view.Window
import android.util.TypedValue
import android.widget.ImageButton
import androidx.core.content.res.ResourcesCompat
import android.text.TextUtils
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.AppBarLayout
import androidx.core.content.ContextCompat
import androidx.constraintlayout.widget.Group
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.core.text.HtmlCompat
import androidx.core.app.ShareCompat
import com.airbnb.lottie.LottieAnimationView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.CoroutineScope
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SEARCH_HISTORY_PREFS = "SearchHistoryPrefs"
private const val KEY_SEARCH_HISTORY = "search_history"
private const val MAX_SEARCH_HISTORY = 10
private const val ABOUT_PREFS = "AboutPrefs"

private const val LAST_READ_PREFS = "LastReadPrefs"
private const val KEY_LAST_READ_SERIAL = "lastReadSerial"
private const val KEY_LAST_READ_LANG = "lastReadLang"

private const val READER_THEME_PREFS = "ReaderThemePrefs"
private const val KEY_READER_THEME = "readerTheme"
private const val THEME_LIGHT = "light"
private const val THEME_SEPIA = "sepia"
private const val THEME_MIDNIGHT = "midnight"

class MainActivity : AppCompatActivity() {

    private val bookViewModel: BookViewModel by viewModels()

    private lateinit var chapterAdapter: ChapterAdapter
    private lateinit var searchResultAdapter: SearchResultAdapter
    private lateinit var recyclerViewChapters: RecyclerView

    private lateinit var loadingGroup: Group
    private lateinit var tvLoadingStatus: TextView
    private lateinit var lottieAnimationView: LottieAnimationView
    private lateinit var tvLoadingTimeNotice: TextView
    private lateinit var rvDownloadedChapterHeadings: RecyclerView
    private lateinit var searchSummaryTextView: TextView
    private lateinit var errorGroup: Group
    private lateinit var errorMessageTextView: TextView
    private lateinit var noResultsGroup: ViewGroup // Changed from TextView
    private var originalChapters: List<Chapter> = emptyList()
    private var pristineOriginalChapters: List<Chapter> = emptyList() // Holds the clean, sorted list
    private lateinit var retryButton: Button
    private lateinit var downloadedHeadingsAdapter: DownloadedChaptersAdapter
    private lateinit var toolbarTitle: TextView

    private lateinit var fabBookmarks: FloatingActionButton
    private var isShowingBookmarks = false
    private lateinit var languageCodes: Array<String>
    private lateinit var languageNames: Array<String>

    private var currentBookId: String = "kada_chabuk"

    private var optionsMenu: Menu? = null

    private var searchJob: Job? = null
    private val uiScope = CoroutineScope(Dispatchers.Main)

    // --- In-App Update Properties ---
    private lateinit var appUpdateManager: AppUpdateManager
    private val updateLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.e("MainActivity", "In-App Update flow failed with result code: ${result.resultCode}")
        }
    }
    private val installStateUpdatedListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            // After the update is downloaded, show a notification
            // and request user confirmation to restart the app.
            showUpdateDownloadedSnackbar()
        }
    }
    // Define a string resource for the default loading message if not already present
    // For example, in res/values/strings.xml:
    // <string name="loading_status_default">Loading...</string>
    // <string name="loading_status_processing">Processing chapters...</string>
    // <string name="loading_status_preparing">Preparing data...</string>
    // <string name="loading_status_about">Loading about info...</string>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get bookId from Intent
        currentBookId = intent.getStringExtra("selected_book_id") ?: "kada_chabuk"
        bookViewModel.currentBookId = currentBookId

        // applySavedTheme() removed - handled by applyReaderTheme
        setContentView(R.layout.activity_main)

        // Allow the app to draw behind the system bars for a seamless UI
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Make the status bar transparent to show the AppBarLayout's color underneath
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        initializeViews()
        setupRetryButton() // Ensure retry button is initialized

        // Immediate Toolbar Visibility Logic
        val toolbarIcon: ImageView = findViewById(R.id.toolbar_icon)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        if (currentBookId == "kada_chabuk") {
            toolbarIcon.visibility = View.VISIBLE
            toolbarTitle.visibility = View.GONE
        } else {
            toolbarIcon.visibility = View.GONE
            toolbarTitle.visibility = View.VISIBLE
        }
        
        // Handle window insets to prevent overlap with the status bar
        handleWindowInsets()

        loadLanguageArrays()
        setupAdaptersAndRecyclerViews()
        checkIfLanguageNotSet()
        setupFab()
        observeViewModel()

        // Dynamic Toolbar Title Logic
        bookViewModel.libraryBooks.observe(this) { books ->
            val book = books.find { it.bookId == currentBookId } ?: return@observe
            val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val lang = sharedPreferences.getString("selected_language_code", "bn") ?: "bn"
            
            val toolbarIcon: ImageView = findViewById(R.id.toolbar_icon)
                if (currentBookId == "kada_chabuk") {
                    toolbarIcon.visibility = View.VISIBLE
                    toolbarTitle.visibility = View.GONE
                    supportActionBar?.setDisplayShowTitleEnabled(false)
                } else {
                    toolbarIcon.visibility = View.GONE
                    val bookName = book.getLocalizedName(lang)
                    if (bookName.isNotEmpty()) {
                        toolbarTitle.text = bookName
                        toolbarTitle.visibility = View.VISIBLE
                        supportActionBar?.setDisplayShowTitleEnabled(false)
                        
                        // Force the Galada font programmatically to ensure it's applied
                        try {
                            val typeface = ResourcesCompat.getFont(this@MainActivity, R.font.galada)
                            toolbarTitle.typeface = typeface
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Error loading Galada font", e)
                        }

                        // Match title color to the icon tint (?attr/colorOnPrimary)
                        val typedValue = TypedValue()
                        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                        val titleColor = typedValue.data
                        toolbarTitle.setTextColor(titleColor)
                    }
                }
        }
        
        // Ensure we actually fetch the metadata (from DB first, then network)
        bookViewModel.fetchLibraryMetadata()

        // Initialize and check for app updates
        appUpdateManager = AppUpdateManagerFactory.create(this)
        checkForAppUpdate()
        applyReaderTheme()
    }

    override fun onResume() {
        super.onResume()
        // Re-apply the status bar icon color every time the activity resumes.
        // This is crucial for when returning from dialogs or other activities.
        setStatusBarIconColor()

        // Check for updates that were downloaded in the background.
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                showUpdateDownloadedSnackbar()
            }
        }

        appUpdateManager.registerListener(installStateUpdatedListener)

        // This block ensures that when the user returns from reading a chapter,
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // This is another crucial part to fix the status bar icon color flicker
            // when returning from dialogs. This re-asserts the activity's desired icon color.
            setStatusBarIconColor()
        }
        // the list is immediately updated to pin the last-read chapter at the top.
        // We only perform this logic if the original list of chapters has been loaded.
        if (originalChapters.isNotEmpty()) {
            // Always reorder from the pristine, serially-sorted list.
            // This ensures previously pinned items go back to their correct place.
            val reorderedChapters = reorderChaptersWithLastRead(pristineOriginalChapters)
            // Update the currently displayed list.
            originalChapters = reorderedChapters

            val searchItem = optionsMenu?.findItem(R.id.action_search)
            val searchView = searchItem?.actionView as? SearchView
            val isSearching = searchView != null && !searchView.isIconified && !searchView.query.isNullOrEmpty()

            // Only update the visible list if the user is not in the middle of a search.
            if (!isSearching) {
                if (isShowingBookmarks) {
                    // If the user was viewing bookmarks, re-apply the filter.
                    filterBookmarkedChapters()
                } else {
                    // Otherwise, just update the main list.
                    chapterAdapter.updateChapters(originalChapters)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister the listener to avoid leaks.
        appUpdateManager.unregisterListener(installStateUpdatedListener)
    }

    /**
     * Sets the status bar icon color (light/dark) to match the AppBar's icon color.
     * This is done by checking if the app is currently in night mode.
     */
    private fun setStatusBarIconColor() {
        WindowUtils.setStatusBarIconColor(window)
    }

    private fun handleWindowInsets() {
        // We apply the listener to the toolbar, not the AppBarLayout.
        // This way, the AppBarLayout's background draws behind the status bar,
        // and we only add padding to the toolbar itself to prevent content overlap.
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Apply the top inset as padding to push the toolbar's content down
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)

            // Increase the toolbar's height to accommodate the new padding
            // Get the default toolbar height from the theme attribute for robustness
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
            val actionBarSize = TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)

            view.layoutParams.height = actionBarSize + insets.top

            // We've handled the insets, so consume them
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun initializeViews() {
        recyclerViewChapters = findViewById(R.id.recyclerViewChapters)
        loadingGroup = findViewById(R.id.loading_group)
        tvLoadingStatus = findViewById(R.id.tv_loading_status)
        lottieAnimationView = findViewById(R.id.lottie_animation_view)
        tvLoadingTimeNotice = findViewById(R.id.tv_loading_time_notice)
        rvDownloadedChapterHeadings = findViewById(R.id.rv_downloaded_chapter_headings)
        errorGroup = findViewById(R.id.error_group)
        errorMessageTextView = findViewById(R.id.error_message)
        searchSummaryTextView = findViewById(R.id.tv_search_summary)
        noResultsGroup = findViewById(R.id.no_results_group) // Changed to the new group ID
        retryButton = findViewById(R.id.retry_button)
        fabBookmarks = findViewById(R.id.fab_bookmarks)
        toolbarTitle = findViewById(R.id.toolbar_title)
    }

    private fun loadLanguageArrays() {
        languageNames = resources.getStringArray(R.array.language_names)
        languageCodes = resources.getStringArray(R.array.language_codes)
    }

    private fun setupAdaptersAndRecyclerViews() {
        chapterAdapter = ChapterAdapter(emptyList())
        searchResultAdapter = SearchResultAdapter(emptyList())
        recyclerViewChapters.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chapterAdapter
        }

        try {
            downloadedHeadingsAdapter = DownloadedChaptersAdapter(mutableListOf())
            // Use a custom LayoutManager to control the scroll speed.
            val speedyLayoutManager = SpeedyLinearLayoutManager(this@MainActivity)

            rvDownloadedChapterHeadings.apply {
                layoutManager = speedyLayoutManager
                adapter = downloadedHeadingsAdapter
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error setting up DownloadedChaptersAdapter.", e)
            Toast.makeText(this, "Error initializing download progress display.", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupRetryButton() {
        retryButton.setOnClickListener {
            val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val savedLangCode = sharedPreferences.getString("selected_language_code", null)
            if (savedLangCode != null) {
                val langIndex = languageCodes.indexOf(savedLangCode)
                bookViewModel.fetchAndLoadChapters(savedLangCode, languageNames[langIndex], forceDownload = true, bookId = currentBookId)
            }
        }
    }

    private fun setupFab() {
        fabBookmarks.setOnClickListener {
            isShowingBookmarks = !isShowingBookmarks
            if (isShowingBookmarks) {
                filterBookmarkedChapters()
                fabBookmarks.setImageResource(R.drawable.ic_bookmark_filled)
            } else {
                chapterAdapter.updateChapters(originalChapters)
                hideNoResultsView() // Hide "no results" when returning to the full list
                fabBookmarks.setImageResource(R.drawable.ic_bookmark_border)
                // If a search query is active, re-apply it
                val searchItem = optionsMenu?.findItem(R.id.action_search)
                val searchView = searchItem?.actionView as? SearchView
                if (searchView != null && !searchView.isIconified && !searchView.query.isNullOrEmpty()) {
                    filter(searchView.query.toString())
                }
            }
        }
    }

    private fun filterBookmarkedChapters() {
        val bookmarkPrefs = getSharedPreferences("BookmarkPrefs", Context.MODE_PRIVATE)
        val bookmarkedChapters = originalChapters.filter { chapter ->
            val key = "bookmark_${chapter.bookId}_${chapter.languageCode}_${chapter.serial}"
            bookmarkPrefs.getBoolean(key, false)
        }
        chapterAdapter.updateChapters(bookmarkedChapters)

        if (bookmarkedChapters.isEmpty()) {
            showNoResultsView("No bookmarks added yet", R.drawable.ic_bookmark_border)
        } else {
            hideNoResultsView()
        }
    }

    private fun checkIfLanguageNotSet() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedLangCode = sharedPreferences.getString("selected_language_code", null)

        if (savedLangCode == null) {
            // If no language is set, show the selection dialog.
            // This will only happen on the very first app launch.
            showLanguageSelectionDialog(isCancelable = false)
        } else if (bookViewModel.chapters.value.isNullOrEmpty() && bookViewModel.isLoading.value != true) {
            // If a language is saved but no chapters are loaded (e.g., app was closed during initial load),
            // automatically resume fetching the chapters without showing a dialog.
            val langIndex = languageCodes.indexOf(savedLangCode)
            if (langIndex != -1) bookViewModel.fetchAndLoadChapters(savedLangCode, languageNames[langIndex], forceDownload = false, bookId = currentBookId)
        }
    }

    private fun showLanguageSelectionDialog(isCancelable: Boolean) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedLangCode = sharedPreferences.getString("selected_language_code", null)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_language_selector)
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.setCancelable(isCancelable)
        // Use the centralized utility to set the status bar icon color for the dialog.
        dialog.window?.let { WindowUtils.setStatusBarIconColor(it) }

        val rvLanguages = dialog.findViewById<RecyclerView>(R.id.rv_languages)
        rvLanguages.layoutManager = LinearLayoutManager(this)
        (rvLanguages.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        // We need to know which languages are downloaded to show the icons.
        uiScope.launch {
            val downloadedCodes = bookViewModel.getDownloadedLanguageCodes(currentBookId)
            val languageAdapter = LanguageAdapter(
                languages = languageNames.zip(languageCodes).toList(),
                downloadedLanguageCodes = downloadedCodes,
                currentSelectedCode = savedLangCode,
                onLanguageSelected = { langCode, langName ->
                    // If the language is already downloaded, switch to it immediately.
                    if (downloadedCodes.contains(langCode)) {
                        saveLanguagePreference(langCode)
                        bookViewModel.fetchAndLoadChapters(langCode, langName, forceDownload = false, bookId = currentBookId)
                        dialog.dismiss()
                    } else {
                        // If not downloaded, show a confirmation dialog before proceeding.
                        showDownloadConfirmationDialog(langName) {
                            saveLanguagePreference(langCode)
                            bookViewModel.fetchAndLoadChapters(langCode, langName, forceDownload = false, bookId = currentBookId)
                            dialog.dismiss()
                        }
                    }
                },
                onLanguageDelete = { langCode, langName ->
                    showDeleteLanguageConfirmationDialog(langCode, langName) {
                        // This block will be executed on confirmation.
                        // We dismiss the language selection dialog and then delete.
                        dialog.dismiss()
                    }
                }
            )
            rvLanguages.adapter = languageAdapter
        }
        dialog.show()
    }

    private fun showDownloadConfirmationDialog(langName: String, onConfirm: () -> Unit) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Download in $langName?") // Corrected typo from "Dowmload"
            .setMessage(HtmlCompat.fromHtml(
                "<i>Kada Chabuk</i> in '$langName' are not downloaded. Would you like to download them now? This may take a few moments depending on your network speed.",
                HtmlCompat.FROM_HTML_MODE_LEGACY
            ))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Download") { _, _ ->
                onConfirm()
            }
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
            val typedValue = TypedValue()
            // Resolve the theme's primary color
            theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            val primaryColor = if (typedValue.resourceId != 0) {
                ContextCompat.getColor(this, typedValue.resourceId)
            } else typedValue.data

            // Apply the colors to make it a contained button
            positiveButton.setBackgroundColor(primaryColor)
            positiveButton.setTextColor(Color.WHITE)

            // Reduce padding to make the button's "box" smaller
            val horizontalPadding = (16 * resources.displayMetrics.density).toInt() // 16dp
            val verticalPadding = (8 * resources.displayMetrics.density).toInt()   // 8dp
            positiveButton.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

        }

        dialog.show()
    }

    private fun showDeleteLanguageConfirmationDialog(langCode: String, langName: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Data for $langName?")
            .setMessage("This will remove all downloaded chapters for this language to free up space. You can download them again later.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                bookViewModel.deleteChaptersForLanguage(langCode, currentBookId)
                onConfirm() // Execute the callback to dismiss the other dialog
            }
            .show()
    }

    private fun saveLanguagePreference(languageCode: String) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("selected_language_code", languageCode)
            apply()
        }
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        optionsMenu = menu
        val searchItem = menu.findItem(R.id.action_search)

        // Style the main search icon (magnifying glass) on the toolbar
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        searchItem.icon?.setColorFilter(if (isNightMode) Color.DKGRAY else Color.WHITE, PorterDuff.Mode.SRC_ATOP)

        val searchView = searchItem.actionView as SearchView
        styleSearchView(searchView) // Apply custom styling
        setupSearchSuggestions(searchView)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchJob?.cancel()
                saveSearchQuery(query ?: "")
                filter(query)
                // Hide keyboard on submit
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchJob?.cancel()
                searchJob = uiScope.launch {
                    kotlinx.coroutines.delay(500L) // 500ms debounce delay
                    populateSearchSuggestions(newText, searchView)
                    filter(newText)
                }
                return true
            }
        })

        // Add a badge to the "Credits" menu item to draw attention
        addBadgeToCreditsMenuItem()

        // Theme icon is set in main_menu.xml
        return true
    }

    private fun setupSearchSuggestions(searchView: SearchView) {
        val from = arrayOf("query")
        val to = intArrayOf(R.id.suggestion_text)
        val cursorAdapter = SimpleCursorAdapter(
            this,
            R.layout.item_search_suggestion,
            null,
            from,
            to,
            SimpleCursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
        )

        searchView.suggestionsAdapter = cursorAdapter

        searchView.setOnSuggestionListener(object : SearchView.OnSuggestionListener {
            override fun onSuggestionSelect(position: Int): Boolean {
                return false
            }

            override fun onSuggestionClick(position: Int): Boolean {
                val cursor = searchView.suggestionsAdapter.cursor
                if (cursor.moveToPosition(position)) {
                    val query = cursor.getString(cursor.getColumnIndexOrThrow("query"))
                    searchView.setQuery(query, true)
                }
                return true
            }
        })
    }

    /**
     * Applies consistent styling to the SearchView to match the toolbar's icon colors.
     * This ensures the search icon, text, hint text, and close button have the correct
     * color in both light and dark themes.
     */
    private fun styleSearchView(searchView: SearchView) {
        // Resolve theme attributes for consistent coloring
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        val iconAndTextColor = typedValue.data

        val hintColor = ColorUtils.setAlphaComponent(iconAndTextColor, 180) // ~70% opacity for hint

        // Style the search icon
        val searchIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchIcon.setColorFilter(iconAndTextColor, PorterDuff.Mode.SRC_IN)

        // Style the text, hint, and cursor
        val searchText = searchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
        searchText.setTextColor(iconAndTextColor)
        searchText.setHintTextColor(hintColor)
        // Set the cursor color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Create a simple drawable for the cursor and set its color
            searchText.textCursorDrawable = ColorDrawable(iconAndTextColor)
        }

        // Style the close button
        val closeButton = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeButton.setColorFilter(iconAndTextColor, PorterDuff.Mode.SRC_IN)

        // Style the back arrow (up button) that appears when search is active
        val backButton = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_button)
        backButton.setColorFilter(iconAndTextColor, PorterDuff.Mode.SRC_IN)

        // Style the underline (optional, can be hidden with transparent color)
        val underline = searchView.findViewById<View>(androidx.appcompat.R.id.search_plate)
        underline.setBackgroundColor(Color.TRANSPARENT)
    }

    /**
     * Creates and attaches a BadgeDrawable to the "Credits" menu item.
     * This is used to highlight the item in the overflow menu.
     */
    private fun addBadgeToCreditsMenuItem() {
        // The badge needs to be attached to the Toolbar, not the menu item directly.
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        val creditsBadge = BadgeDrawable.create(this).apply {
            // Using a dot without a number.
            backgroundColor = ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_light)
            isVisible = true
        }
        com.google.android.material.badge.BadgeUtils.attachBadgeDrawable(creditsBadge, toolbar, R.id.action_credits)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel any running jobs to avoid memory leaks
        searchJob?.cancel()
    }

    private fun checkForAppUpdate() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                // An update is available and flexible updates are allowed.
                // Start the update flow.
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                )
            } else {
                Log.d("MainActivity", "No new update available.")
            }
        }
    }

    private fun showUpdateDownloadedSnackbar() {
        Snackbar.make(
            findViewById(R.id.main),
            "A new version has been downloaded.",
            Snackbar.LENGTH_INDEFINITE
        ).apply {
            setAction("RESTART") {
                // Trigger the final installation of the update.
                appUpdateManager.completeUpdate()
            }
            show()
        }
    }

    private fun saveSearchQuery(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return

        val prefs = getSharedPreferences(SEARCH_HISTORY_PREFS, Context.MODE_PRIVATE)
        val history = getSearchHistory().toMutableList()

        history.remove(trimmedQuery) // Remove if it already exists to move it to the top
        history.add(0, trimmedQuery) // Add to the top (most recent)

        val trimmedHistory = history.take(MAX_SEARCH_HISTORY)

        with(prefs.edit()) {
            putStringSet(KEY_SEARCH_HISTORY, trimmedHistory.toSet())
            apply()
        }
    }

    private fun getSearchHistory(): List<String> {
        val prefs = getSharedPreferences(SEARCH_HISTORY_PREFS, Context.MODE_PRIVATE)
        // The set is unordered, so we can't rely on its iteration order.
        // For simplicity here, we'll just sort it alphabetically. A more complex
        // implementation might store timestamps.
        return prefs.getStringSet(KEY_SEARCH_HISTORY, emptySet())?.sorted() ?: emptyList()
    }

    private fun populateSearchSuggestions(query: String?, searchView: SearchView) {
        val history = getSearchHistory().filter { it.contains(query ?: "", ignoreCase = true) }
        val cursor = MatrixCursor(arrayOf(BaseColumns._ID, "query"))
        history.forEachIndexed { index, suggestion ->
            cursor.addRow(arrayOf(index, suggestion))
        }
        searchView.suggestionsAdapter.changeCursor(cursor)
    }

    private fun filter(text: String?) {
        val query = text?.lowercase()?.trim()

        // Disable the bookmark FAB during a search to prevent conflicting states.
        val isSearching = !query.isNullOrEmpty()
        fabBookmarks.isEnabled = !isSearching
        fabBookmarks.alpha = if (isSearching) 0.5f else 1.0f

        uiScope.launch {
            // If the search query is empty, restore the original adapter and hide search UI.
            if (query.isNullOrEmpty()) {
                recyclerViewChapters.adapter = chapterAdapter
                chapterAdapter.updateChapters(originalChapters)
                hideNoResultsView()
                searchSummaryTextView.visibility = View.GONE
                return@launch
            }

            // Perform the heavy filtering and counting on a background thread.
            val (searchResults, totalOccurrences) = withContext(Dispatchers.IO) {
                val results = mutableListOf<SearchResult>()
                var occurrences = 0

                originalChapters.forEach { chapter ->
                    val totalMatchesInChapter = countOccurrences(chapter.heading, query) +
                            countOccurrences(chapter.serial, query) +
                            countOccurrences(chapter.writer, query) +
                            countOccurrences(chapter.dataText, query)

                    if (totalMatchesInChapter > 0) {
                        results.add(SearchResult(chapter, totalMatchesInChapter))
                        occurrences += totalMatchesInChapter
                    }
                }
                Pair(results, occurrences)
            }

            // Switch to the search adapter on the main thread
            if (recyclerViewChapters.adapter !is SearchResultAdapter) {
                recyclerViewChapters.adapter = searchResultAdapter
            }

            // When filtering, exit the "bookmarks only" view for a better user experience.
            if (isShowingBookmarks) {
                isShowingBookmarks = false
                fabBookmarks.setImageResource(R.drawable.ic_bookmark_border)
            }

            searchResultAdapter.updateResults(searchResults, query)

            if (searchResults.isEmpty()) {
                searchSummaryTextView.visibility = View.GONE 
                // When search has no results, show the search icon
                showNoResultsView("No results found for your search", R.drawable.ic_search)
            } else {
                val summary = "\"$text\" found in ${searchResults.size} chapters, $totalOccurrences times total."
                searchSummaryTextView.text = summary
                searchSummaryTextView.visibility = View.VISIBLE
                hideNoResultsView()
            }
        }
    }

    // A more performant way to count occurrences, ignoring case.
    private fun countOccurrences(text: String, query: String): Int {
        if (query.isEmpty()) return 0
        var count = 0
        var index = text.indexOf(query, 0, ignoreCase = true)
        while (index != -1) {
            count++
            index = text.indexOf(query, index + query.length, ignoreCase = true)
        }
        return count
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_theme_toggle -> {
                showReaderThemeDialog()
                return true
            }
            R.id.action_language_change -> {
                showLanguageSelectionDialog(isCancelable = true)
                return true
            }
            R.id.action_reading_history -> {
                showReadingHistoryOptionsDialog()
                return true
            }
            R.id.action_share_app -> {
                shareApp()
                return true
            }
            R.id.action_my_notes -> {
                startActivity(Intent(this, MyNotesActivity::class.java))
                return true
            }
            R.id.action_videos -> {
                startActivity(Intent(this, VideoActivity::class.java))
                return true
            }
            R.id.action_about -> {
                showAboutDialog()
                return true
            }
            R.id.action_credits -> {
                // Set the flag and fetch from cache. The observer will handle showing the dialog.
                bookViewModel.isFetchingCreditsForDialog.value = true
                bookViewModel.fetchContributors(forceRefresh = false)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showReadingHistoryOptionsDialog() {
        val historyPrefs = getSharedPreferences("ReadingHistoryPrefs", Context.MODE_PRIVATE)
        val isHistoryVisible = historyPrefs.getBoolean("is_history_visible", true)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_history_options, null)
        dialog.setContentView(dialogView)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        // Use the centralized utility to set the status bar icon color for the dialog.
        dialog.window?.let { WindowUtils.setStatusBarIconColor(it) }

        val resetOption = dialogView.findViewById<TextView>(R.id.option_reset_history)
        val toggleSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.option_toggle_history)
        val totalReadsTextView = dialogView.findViewById<TextView>(R.id.habit_total_reads)
        val totalTimeTextView = dialogView.findViewById<TextView>(R.id.habit_total_time)
        val habitLayout = dialogView.findViewById<View>(R.id.layout_reading_habit)

        // --- Style the Reset button to match the language delete button ---
        // 1. Get the theme's error color
        val typedValue = TypedValue()
        theme.resolveAttribute(android.R.attr.colorError, typedValue, true)
        val errorColor = typedValue.data

        // 2. Apply the color to the TextView's text and its drawable icon
        resetOption.setTextColor(errorColor)
        val deleteIcon = ContextCompat.getDrawable(this, R.drawable.ic_delete_sweep)?.mutate()
        deleteIcon?.setTint(errorColor)
        resetOption.setCompoundDrawablesWithIntrinsicBounds(deleteIcon, null, null, null)

        toggleSwitch.text = "Show Reading History"
        toggleSwitch.isChecked = isHistoryVisible

        resetOption.setOnClickListener {
            showResetHistoryConfirmationDialog()
            dialog.dismiss()
        }

        toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            historyPrefs.edit().putBoolean("is_history_visible", isChecked).apply()
            // Refresh the list to apply the change
            chapterAdapter.notifyDataSetChanged()
            // The toast is a bit redundant since the switch state is clear.
            // dialog.dismiss() // Keep dialog open to change other settings
        }

        // Calculate and display reading habits
        var totalReadCount = 0
        var totalReadTimeMs = 0L
        val allEntries: Map<String, *> = historyPrefs.all

        for ((key, value) in allEntries) {
            if (key.startsWith("count_") && value is Int) {
                totalReadCount += value
            } else if (key.startsWith("time_") && value is Long) {
                totalReadTimeMs += value
            }
        }

        if (totalReadCount > 0) {
            val formattedTotalTime = TimeUtils.formatDuration(totalReadTimeMs)

            totalReadsTextView.text = "• Total $totalReadCount times read"
            totalTimeTextView.text = "• Total $formattedTotalTime reading time"
            habitLayout.visibility = View.VISIBLE
        } else {
            habitLayout.visibility = View.GONE
        }

        dialog.show()
    }

    private fun showResetHistoryConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Reading History")
            .setMessage("Are you sure you want to reset all reading history? This action cannot be undone.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Reset") { _, _ ->
                resetReadingHistory()
            }
            .show()
    }

    private fun resetReadingHistory() {
        // Clear reading history (counts and times)
        val historyPrefs = getSharedPreferences("ReadingHistoryPrefs", Context.MODE_PRIVATE)
        historyPrefs.edit().clear().apply()

        // Clear the last read chapter indicator
        val lastReadPrefs = getSharedPreferences("LastReadPrefs", Context.MODE_PRIVATE)
        lastReadPrefs.edit().clear().apply()

        // Also reset the visibility preference to its default (visible)
        historyPrefs.edit().putBoolean("is_history_visible", true).apply()

        // Show a confirmation toast
        Toast.makeText(this, "Reading history has been reset.", Toast.LENGTH_SHORT).show()

        // Refresh the UI by re-binding the adapter with the original, pristine chapter list
        // and no last-read chapter.
        chapterAdapter.updateChapters(pristineOriginalChapters, null)
    }

    
    private fun shareApp() {
        val appPackageName = packageName // Get the package name of your app
        val appLink = "https://play.google.com/store/apps/details?id=$appPackageName"

        val shareIntent = ShareCompat.IntentBuilder(this)
            .setType("text/plain")
            .setText("Check out this app: $appLink")
            .setSubject("Share App")
            .setChooserTitle("Share App via")
            .intent

        startActivity(shareIntent)
    }

    private fun reorderChaptersWithLastRead(chapters: List<Chapter>): List<Chapter> {
        val prefs = getSharedPreferences(LAST_READ_PREFS, Context.MODE_PRIVATE)
        val lastReadSerial = prefs.getString("${KEY_LAST_READ_SERIAL}_$currentBookId", null)
        val lastReadLang = prefs.getString("${KEY_LAST_READ_LANG}_$currentBookId", null)

        if (lastReadSerial == null || lastReadLang == null) {
            return chapters // No last read chapter, return original order
        }

        val lastReadChapter = chapters.find { it.serial == lastReadSerial && it.languageCode == lastReadLang }

        return if (lastReadChapter != null) {
            // Create a new list with the last read chapter at the top
            val mutableChapters = chapters.toMutableList()
            mutableChapters.remove(lastReadChapter)
            mutableChapters.add(0, lastReadChapter)
            mutableChapters.toList()
        } else {
            chapters // Last read chapter not in the current list, return original order
        }
    }

    private fun observeViewModel() {
        bookViewModel.chapters.observe(this) { chapters ->
            Log.d("MainActivity", "Chapters LiveData updated. Count: ${chapters?.size ?: 0}")
            chapters?.let {
                if (it.isNotEmpty()) {
                    // This is the first time we are displaying chapters in this session.
                    // This is the perfect place to show the initial "About" dialog.
                    if (!bookViewModel.hasShownInitialAboutDialog) {
                        val aboutPrefs = getSharedPreferences(ABOUT_PREFS, Context.MODE_PRIVATE)
                        val showAbout = aboutPrefs.getBoolean("show_about_on_startup", true)
                        if (showAbout) {
                            val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                            val savedLangCode = sharedPreferences.getString("selected_language_code", null)
                            savedLangCode?.let { langCode -> bookViewModel.fetchAboutInfo(langCode, forceRefresh = false, bookId = currentBookId) }
                        }
                        bookViewModel.hasShownInitialAboutDialog = true

                        // Silently pre-fetch the "About" info in the background for the next time.
                        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                        val savedLangCode = sharedPreferences.getString("selected_language_code", null)
                        savedLangCode?.let { langCode -> bookViewModel.fetchAboutInfo(langCode, forceRefresh = true, isSilent = true, bookId = currentBookId) }

                        // Also, silently pre-fetch the "Credits" info in the background.
                        bookViewModel.fetchContributors(forceRefresh = true, isSilent = true, bookId = currentBookId)

                    }

                    val reorderedChapters = reorderChaptersWithLastRead(it)
                    val prefs = getSharedPreferences(LAST_READ_PREFS, Context.MODE_PRIVATE)
                    val lastReadSerial = prefs.getString("${KEY_LAST_READ_SERIAL}_$currentBookId", null)

                    chapterAdapter.updateChapters(reorderedChapters, lastReadSerial)
                    hideNoResultsView()
                    // Store both the pristine and the reordered list
                    pristineOriginalChapters = it // This is the clean, serially sorted list.
                    originalChapters = reorderedChapters // This is the list for display.
                    recyclerViewChapters.visibility = View.VISIBLE
                } else { // This block runs when the observed chapter list is empty.
                    // Don't hide the RecyclerView if there are no chapters, just show an empty state.
                    // This prevents the UI from "jumping" if the user switches to a language with no content.
                    chapterAdapter.updateChapters(emptyList())
                    originalChapters = emptyList()
                    // Only show the "no results" message if we are certain loading is finished and there's no error.
                    pristineOriginalChapters = emptyList()
                    if (bookViewModel.isLoading.value == false && errorGroup.visibility == View.GONE) {
                        // Also check that we are not in the middle of a search that has no results
                        val searchItem = optionsMenu?.findItem(R.id.action_search)
                        val searchView = searchItem?.actionView as? SearchView
                        val isSearching = searchView != null && !searchView.isIconified && !searchView.query.isNullOrEmpty()
                        if (!isSearching) showNoResultsView(getString(R.string.no_chapters_found))
                    }
                }
            }
        }

        bookViewModel.isLoading.observe(this) { isLoading ->
            Log.d("MainActivity", "isLoading LiveData updated: $isLoading")
            optionsMenu?.findItem(R.id.action_search)?.isEnabled = !isLoading
            optionsMenu?.findItem(R.id.action_theme_toggle)?.isEnabled = !isLoading
            optionsMenu?.findItem(R.id.action_overflow)?.isEnabled = !isLoading
            val alpha = if (isLoading) 128 else 255 // 50% transparent when disabled
            optionsMenu?.findItem(R.id.action_search)?.icon?.alpha = alpha
            optionsMenu?.findItem(R.id.action_theme_toggle)?.icon?.alpha = alpha
            // The search icon is part of the SearchView, so we can't just set its icon alpha. Disabling the item is sufficient.
            optionsMenu?.findItem(R.id.action_overflow)?.icon?.alpha = alpha

            // Disable the bookmark FAB during loading to prevent interaction.
            fabBookmarks.isEnabled = !isLoading
            fabBookmarks.alpha = if (isLoading) 0.5f else 1.0f

            if (isLoading) {
                // When loading starts, ensure the progress bar is indeterminate (spinning)
                // and the percentage text is hidden.
                lottieAnimationView.playAnimation()
                loadingGroup.visibility = View.VISIBLE
                rvDownloadedChapterHeadings.visibility = View.VISIBLE // Show progress list
                recyclerViewChapters.visibility = View.GONE 
                hideNoResultsView()
            } else {
                lottieAnimationView.cancelAnimation()
                loadingGroup.visibility = View.GONE
                rvDownloadedChapterHeadings.visibility = View.GONE // Hide progress list
                // When loading is finished, ensure the main content or error is visible.
                if (bookViewModel.error.value == null && chapterAdapter.itemCount > 0) {
                    // Only animate if the view is not already visible
                    if (recyclerViewChapters.visibility != View.VISIBLE) {
                        val fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in)
                        recyclerViewChapters.startAnimation(fadeIn)
                        recyclerViewChapters.visibility = View.VISIBLE
                    }
                }
            }
        }
        setupRetryButton()

        bookViewModel.loadingStatusMessage.observe(this) { statusMessage ->
            // This observer is now the primary driver for tvLoadingStatus text when loading.
            if (bookViewModel.isLoading.value == true) { // Only update if loading is active
                if (!statusMessage.isNullOrEmpty()) {
                    tvLoadingStatus.text = statusMessage
                    Log.d("MainActivity", "Loading Status Message Update: $statusMessage")
                } else {
                    // Fallback to default loading text if message is cleared but still loading
                    tvLoadingStatus.text = getString(R.string.loading_status_default)
                }
            }
            // If isLoading is false, loadingGroup is hidden, so this text won't be visible.
        }

        bookViewModel.error.observe(this) { errorMessage ->
            errorMessage?.let {
                errorMessageTextView.text = it
                errorGroup.visibility = View.VISIBLE
                recyclerViewChapters.visibility = View.GONE
                hideNoResultsView()
                loadingGroup.visibility = View.GONE
                Log.e("MainActivity", "Error observed: $it. Showing error screen.")
            } ?: run {
                errorGroup.visibility = View.GONE
                Log.d("MainActivity", "Error cleared. Hiding error screen.")
            }
        }

        bookViewModel.showInitialLoadMessage.observe(this) { showMessage ->
            tvLoadingTimeNotice.visibility = if (showMessage) View.VISIBLE else View.GONE
        }

        bookViewModel.downloadingChaptersList.observe(this) { downloadingList ->
            if (bookViewModel.isLoading.value == true) {
                downloadedHeadingsAdapter.updateList(downloadingList)
                if (downloadingList.isNotEmpty()) {
                    rvDownloadedChapterHeadings.smoothScrollToPosition(downloadedHeadingsAdapter.itemCount - 1)
                    tvLoadingStatus.text = "Preparing (${downloadingList.last().heading})"
                }
            }
        }

        bookViewModel.aboutInfo.observe(this) { result ->
            result.onSuccess { aboutText ->
                // Only show the dialog if the ViewModel has flagged that it's fetching for this purpose.
                // This prevents the dialog from showing unexpectedly if the LiveData updates for another reason.
                if (bookViewModel.isFetchingAboutForDialog.value == true) showAboutDialog(content = aboutText)
            }.onFailure {
                // Optionally handle error, e.g., show a toast
                if (bookViewModel.isFetchingAboutForDialog.value == true) {
                    Toast.makeText(this, "Could not load 'About' info.", Toast.LENGTH_SHORT).show()
                    bookViewModel.isFetchingAboutForDialog.value = false
                }
                Log.e("MainActivity", "Failed to get 'About' info", it)
            }
        }

        // Observe the contributors LiveData
        bookViewModel.contributors.observe(this) { result ->
            if (bookViewModel.isFetchingCreditsForDialog.value == true) {
                result.onSuccess { contributorList ->
                    showContributorsDialog(contributorList)
                }.onFailure {
                    Toast.makeText(this, "Could not load credits.", Toast.LENGTH_SHORT).show()
                    Log.e("MainActivity", "Failed to get contributors", it)
                }
                // Reset the flag after attempting to show the dialog
                bookViewModel.isFetchingCreditsForDialog.value = false
            }
        }
    }

    private fun showAboutDialog(content: String? = null) {
        // If content is not provided, it means we need to fetch it first.
        if (content == null) {
            // Set a flag in the ViewModel indicating our intent to show the dialog once data arrives.
            bookViewModel.isFetchingAboutForDialog.value = true // This flag is still needed to trigger the observer
            val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val savedLangCode = sharedPreferences.getString("selected_language_code", null)
            // Always use the cache for instant dialog display. The background fetch will keep it fresh.
            savedLangCode?.let { bookViewModel.fetchAboutInfo(it, forceRefresh = false) }
            return // Exit the function; the observer will call this function again with content.
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        val titleTextView = dialogView.findViewById<TextView>(R.id.about_title)
        val aboutContentTextView = dialogView.findViewById<TextView>(R.id.about_content)
        val dontShowAgainCheckbox = dialogView.findViewById<CheckBox>(R.id.checkbox_dont_show_again)
        val closeButton = dialogView.findViewById<Button>(R.id.button_close)

        // Use Markwon to render the content as Markdown, enabling clickable links.
        val markwon = Markwon.builder(this)
            .usePlugin(LinkifyPlugin.create())
            .build()
        markwon.setMarkdown(aboutContentTextView, content ?: "")

        // Apply custom styling to the title
        titleTextView.setTypeface(null, Typeface.ITALIC)
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        titleTextView.setTextColor(typedValue.data)


        // Show the "Don't show again" checkbox only on the initial startup dialog.
        // The hasShownInitialAboutDialog flag from the ViewModel is the reliable source of truth here.
        dontShowAgainCheckbox.visibility = if (bookViewModel.hasShownInitialAboutDialog) View.GONE else View.VISIBLE

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        val scrollView = dialogView.findViewById<ScrollView>(R.id.about_scroll_view)

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            (it as? Dialog)?.window?.attributes?.windowAnimations = R.style.DialogAnimation

            // Post a runnable to check for scrollability after the layout is drawn
            scrollView.post {
                val canScroll = scrollView.getChildAt(0).height > scrollView.height
                if (canScroll) {
                    // Use a coroutine to add a small delay before starting the animation
                    uiScope.launch {
                        delay(500) // Wait half a second
                        val scrollDistance = (50 * resources.displayMetrics.density).toInt() // 50dp
                        ValueAnimator.ofInt(0, scrollDistance, 0).apply {
                            duration = 1500
                            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                            addUpdateListener { animation ->
                                scrollView.scrollTo(0, animation.animatedValue as Int)
                            }
                            start()
                        }
                    }
                }
            }
        }
        dialog.setOnDismissListener {
            val aboutPrefs = getSharedPreferences(ABOUT_PREFS, Context.MODE_PRIVATE)
            if (dontShowAgainCheckbox.visibility == View.VISIBLE) {
                aboutPrefs.edit().putBoolean("show_about_on_startup", !dontShowAgainCheckbox.isChecked).apply()
            }
            bookViewModel.isFetchingAboutForDialog.value = false
        }
        dialog.show()
    }

    private fun showNoResultsView(message: String, iconResId: Int = R.drawable.ic_search) {
        val noResultsTextView = noResultsGroup.findViewById<TextView>(R.id.tv_no_results_text)
        val noResultsImageView = noResultsGroup.findViewById<ImageView>(R.id.iv_no_results_icon)

        noResultsImageView.setImageResource(iconResId)
        noResultsTextView.text = message

        if (noResultsGroup.visibility == View.VISIBLE) return

        val animation = AnimationUtils.loadAnimation(this, R.anim.fade_in_slide_up)
        noResultsGroup.startAnimation(animation)
        noResultsGroup.visibility = View.VISIBLE
    }

    private fun hideNoResultsView() {
        if (noResultsGroup.visibility == View.GONE) return // Do nothing if already hidden

        // You could add a fade-out animation here if desired, but for now, just hide it.
        noResultsGroup.clearAnimation() // Clear any running animation
        noResultsGroup.visibility = View.GONE
    }

    private fun showContributorsDialog(contributors: List<Contributor>) {
        // Reuse the 'About' dialog layout for a consistent UI.
        val dialogView = layoutInflater.inflate(R.layout.dialog_about, null)
        val titleTextView = dialogView.findViewById<TextView>(R.id.about_title)
        val contentTextView = dialogView.findViewById<TextView>(R.id.about_content)
        val closeButton = dialogView.findViewById<Button>(R.id.button_close)
        val dontShowAgainCheckbox = dialogView.findViewById<CheckBox>(R.id.checkbox_dont_show_again)

        // Set the correct title for the Credits dialog.
        titleTextView.text = "Credits"

        // Apply custom styling to the title to match the About dialog
        titleTextView.setTypeface(null, Typeface.ITALIC)
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        titleTextView.setTextColor(typedValue.data)

        // The "Don't show again" checkbox is only for the initial 'About' dialog.
        dontShowAgainCheckbox.visibility = View.GONE

        // Always show the introductory text at the top.
        val introText = "We are grateful to everyone who has supported this project. If you'd like to contribute by suggesting corrections, citing mistakes, or in any other way, please contact us at **kadachabuk@gmail.com**."

        val markdownContent = if (contributors.isNotEmpty()) {
            // If contributors exist, append them below the intro text with a separator.
            val contributorsListString = contributors.joinToString(separator = "\n\n") {
                "**${it.name}**\n*${it.address}*" // Making the address italic for better visual style.
            }
            "$introText\n\n---\n\n$contributorsListString"
        } else {
            introText // If no contributors, just show the intro text.
        }

        // Use Markwon to render the Markdown content.
        val markwon = Markwon.builder(this).build()
        markwon.setMarkdown(contentTextView, markdownContent)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showReaderThemeDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_reading_theme)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.let { WindowUtils.setStatusBarIconColor(it) }

        // Theme selection
        val themeLight = dialog.findViewById<View>(R.id.theme_system) // Keeping ID for now, will update XML later
        val themeSepia = dialog.findViewById<View>(R.id.theme_sepia)
        val themeMidnight = dialog.findViewById<View>(R.id.theme_midnight)
        
        // Update background to light theme selector
        themeLight.setBackgroundResource(R.drawable.theme_selector_light)

        val prefs = getSharedPreferences(READER_THEME_PREFS, Context.MODE_PRIVATE)
        val currentTheme = prefs.getString(KEY_READER_THEME, THEME_SEPIA) ?: THEME_SEPIA
        
        themeLight.isSelected = currentTheme == THEME_LIGHT
        themeSepia.isSelected = currentTheme == THEME_SEPIA
        themeMidnight.isSelected = currentTheme == THEME_MIDNIGHT
        
        themeLight.setOnClickListener {
            themeLight.isSelected = true
            themeSepia.isSelected = false
            themeMidnight.isSelected = false
            prefs.edit().putString(KEY_READER_THEME, THEME_LIGHT).apply()
            applyReaderTheme()
        }
        
        themeSepia.setOnClickListener {
            themeLight.isSelected = false
            themeSepia.isSelected = true
            themeMidnight.isSelected = false
            prefs.edit().putString(KEY_READER_THEME, THEME_SEPIA).apply()
            applyReaderTheme()
        }
        
        themeMidnight.setOnClickListener {
            themeLight.isSelected = false
            themeSepia.isSelected = false
            themeMidnight.isSelected = true
            prefs.edit().putString(KEY_READER_THEME, THEME_MIDNIGHT).apply()
            applyReaderTheme()
        }

        dialog.show()
    }

    private fun applyReaderTheme() {
        val prefs = getSharedPreferences(READER_THEME_PREFS, Context.MODE_PRIVATE)
        val themeStr = prefs.getString(KEY_READER_THEME, THEME_SEPIA) ?: THEME_SEPIA
        
        // Sync system mode for app-wide consistency
        val targetMode = when (themeStr) {
            THEME_MIDNIGHT -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_SEPIA -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }

        val mainLayout = findViewById<View>(R.id.main) ?: return
        
        when (themeStr) {
            THEME_SEPIA -> {
                mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.reader_sepia_bg))
            }
            THEME_MIDNIGHT -> {
                mainLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.reader_midnight_bg))
            }
            else -> {
                // THEME_SYSTEM - use default app theme colors
                val typedValue = TypedValue()
                
                // Use colorSurface for background instead of colorBackground for better compatibility
                if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
                    mainLayout.setBackgroundColor(typedValue.data)
                } else {
                    // Fallback to a safe default
                    mainLayout.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light))
                }
            }
        }
    }

    /**
     * A custom LinearLayoutManager that scrolls faster than the default.
     * This is used for the list of downloading chapters to make the animation quicker.
     */
    private class SpeedyLinearLayoutManager(context: Context) : LinearLayoutManager(context) {

        override fun smoothScrollToPosition(recyclerView: RecyclerView, state: RecyclerView.State, position: Int) {
            val linearSmoothScroller = object : LinearSmoothScroller(recyclerView.context) {
                // The default value is 25f. A lower value results in a faster scroll.
                // We'll use 6.25f to make the scroll animation four times as fast.
                private val MILLISECONDS_PER_INCH = 6.25f

                override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                    return MILLISECONDS_PER_INCH / displayMetrics.densityDpi
                }
            }
            linearSmoothScroller.targetPosition = position
            startSmoothScroll(linearSmoothScroller)
        }
    }
}
