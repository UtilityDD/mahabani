package com.blackgrapes.kadachabuk

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.TypedValue
import android.app.Dialog
import android.content.DialogInterface
import android.text.TextUtils
import com.blackgrapes.kadachabuk.WindowUtils

private const val READER_THEME_PREFS = "ReaderThemePrefs"
private const val KEY_READER_THEME = "readerTheme"
private const val THEME_LIGHT = "light"
private const val THEME_SEPIA = "sepia"
private const val THEME_MIDNIGHT = "midnight"

class CoverActivity : AppCompatActivity() {

    private val bookViewModel: BookViewModel by viewModels()
    private lateinit var coverLayout: View
    private lateinit var coverImageView: ImageView
    private lateinit var bookshelfContainer: View
    private lateinit var kadachabukBook: ImageView

    private val animationScope = CoroutineScope(Dispatchers.Main)
    private var animationJob: Job? = null
    private var populationJob: Job? = null
    private var isAnimationSkippable = true

    private lateinit var shelfBooksContainer: android.widget.LinearLayout
    private var currentBookshelfLanguage: String? = null
    private var fetchedBooks: List<LibraryBook> = emptyList()

    private val bookAssetsMap = mapOf(
        "kada_chabuk" to Pair(R.drawable.spine_horizontal_the_echo, R.drawable.cover_kada_chabuk),
        "shaishab_kahini" to Pair(R.drawable.spine_horizontal_the_echo, R.drawable.cover_the_echo),
        "silent_hill" to Pair(R.drawable.spine_horizontal_silent_hill, R.drawable.cover_silent_hill),
        "lost_city" to Pair(R.drawable.spine_horizontal_lost_city, R.drawable.cover_lost_city),
        "red_sky" to Pair(R.drawable.spine_horizontal_red_sky, R.drawable.cover_red_sky),
        "ocean_deep" to Pair(R.drawable.spine_horizontal_ocean_deep, R.drawable.cover_ocean_deep)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySavedTheme()
        setContentView(R.layout.activity_cover)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowUtils.setStatusBarIconColor(window)

        coverLayout = findViewById(R.id.cover_layout)
        coverImageView = findViewById(R.id.cover_image)
        
        bookshelfContainer = findViewById(R.id.bookshelf_container)
        shelfBooksContainer = findViewById(R.id.shelf_books_container)

        startAnimationSequence()
        preloadChapters()
        observeViewModel()
        bookViewModel.fetchLibraryMetadata()

        coverLayout.setOnClickListener {
            if (isAnimationSkippable) {
                skipAnimation()
            }
        }

        val fabLanguage = findViewById<com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton>(R.id.fab_language)
        fabLanguage.setOnClickListener {
            showLanguageSelectionDialog(isCancelable = true)
        }
        
        val scrollView = findViewById<android.widget.ScrollView>(R.id.books_scroll_view)
        scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY) {
                fabLanguage.shrink()
            } else {
                fabLanguage.extend()
            }
        }
        
        setupSearch()
        handleWindowInsets()
    }

    private fun observeViewModel() {
        bookViewModel.libraryBooks.observe(this) { books ->
            val isFirstLoad = fetchedBooks.isEmpty()
            val hasDataChanged = fetchedBooks != books
            
            fetchedBooks = books
            
            if (bookshelfContainer.visibility == View.VISIBLE) {
                if (isFirstLoad || hasDataChanged) {
                    refreshShelf(currentBookshelfLanguage ?: "bn")
                }
            }
        }
    }

    private fun handleWindowInsets() {
        val searchCard = findViewById<View>(R.id.search_card) ?: return
        
        ViewCompat.setOnApplyWindowInsetsListener(searchCard) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as android.view.ViewGroup.MarginLayoutParams
            params.topMargin = systemBars.top + dpToPx(16f)
            view.layoutParams = params
            insets
        }
        
        val shelfContainer = findViewById<View>(R.id.shelf_books_container)
        ViewCompat.setOnApplyWindowInsetsListener(shelfContainer) { view, insets ->
            val systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, dpToPx(8f), view.paddingRight, systemBars.bottom + dpToPx(100f))
            insets
        }
    }
    
    private fun setupSearch() {
        val searchView = findViewById<androidx.appcompat.widget.SearchView>(R.id.bookshelf_search_view)
        
        // Style the search view to be "minimal and clean"
        val searchText = searchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
        searchText.setTextColor(android.graphics.Color.WHITE)
        searchText.setHintTextColor(android.graphics.Color.parseColor("#80FFFFFF"))
        
        val searchIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchIcon.setColorFilter(android.graphics.Color.WHITE)
        
        val closeIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeIcon.setColorFilter(android.graphics.Color.WHITE)

        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterBooks(newText ?: "")
                return true
            }
        })
    }
    
    private fun filterBooks(query: String) {
        val lang = currentBookshelfLanguage ?: "bn"
        val filteredList = if (query.isEmpty()) {
            fetchedBooks
        } else {
            fetchedBooks.filter { book ->
                val name = book.getLocalizedName(lang)
                name.contains(query, ignoreCase = true) || 
                book.sl.contains(query, ignoreCase = true) 
            }
        }
        
        populationJob?.cancel()
        populationJob = lifecycleScope.launch {
            shelfBooksContainer.removeAllViews()
            for (book in filteredList.sortedBy { it.sl }) {
                val progress = bookViewModel.getBookProgress(lang, book.bookId)
                val bookView = createBookView(book, lang, progress)
                shelfBooksContainer.addView(bookView)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if language has changed while we were away
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val selectedLang = sharedPreferences.getString("selected_language_code", null)
        
        if (selectedLang != null) {
            // Always refresh if we've already populated once to update progress bars
            if (bookshelfContainer.visibility == View.VISIBLE) {
                refreshShelf(selectedLang)
            }
        }
    }

    private fun refreshShelf(newLang: String) {
        currentBookshelfLanguage = newLang
        populationJob?.cancel()
        populationJob = animationScope.launch { 
            shelfBooksContainer.removeAllViews()
            populateBookshelf() 
        }
    }

    private fun startAnimationSequence() {
        animationJob = animationScope.launch {
            // Hide views initially
            coverImageView.alpha = 0f
            bookshelfContainer.alpha = 0f
            
            // Clear any existing books
            shelfBooksContainer.removeAllViews()

            delay(100)

            // 1. Bookshelf Reveal
            bookshelfContainer.visibility = View.VISIBLE
            bookshelfContainer.animate().alpha(1f).setDuration(800).start()
            
            // Populate books while shelf fades in
            populationJob?.cancel()
            populationJob = animationScope.launch { populateBookshelf() }
            populationJob?.join() // Wait for population to finish before staggering
            
            // Animate books entry (staggered)
            for (i in 0 until shelfBooksContainer.childCount) {
                val book = shelfBooksContainer.getChildAt(i)
                book.alpha = 0f
                book.translationY = 50f
                book.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 100L) // Removed large 400ms delay since we wait for population now
                    .setDuration(400)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }

            delay(1200)
            
            isAnimationSkippable = false
        }
    }

    private enum class BookState { SPINE, COVER }
    private val bookStates = mutableMapOf<String, BookState>()

    private suspend fun populateBookshelf() {
        // Track the language used for this population
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val lang = sharedPreferences.getString("selected_language_code", "bn") ?: "bn"
        currentBookshelfLanguage = lang

        val sortedBooks = fetchedBooks.sortedBy { it.sl }

        for (book in sortedBooks) {
            // Initialize state
            bookStates[book.bookId] = BookState.SPINE
            
            // Fetch progress
            val progress = bookViewModel.getBookProgress(lang, book.bookId)
            
            val bookView = createBookView(book, lang, progress)
            shelfBooksContainer.addView(bookView)
        }
    }

    private fun createBookView(book: LibraryBook, lang: String, progress: Int = 0): View {
        val (spineRes, coverRes) = bookAssetsMap[book.bookId] ?: Pair(R.drawable.spine_horizontal_the_echo, R.drawable.cover_the_echo)
        
        // Container for the book with shadow
        val cardView = android.widget.FrameLayout(this)
        val cardParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(80f) // Height of horizontal book
        )
        cardParams.setMargins(0, dpToPx(8f), 0, dpToPx(8f))
        cardView.layoutParams = cardParams
        cardView.elevation = dpToPx(4f).toFloat()
        
        // The book image
        val imageView = ImageView(this)
        val imageParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        imageView.layoutParams = imageParams
        imageView.setImageResource(spineRes)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.contentDescription = book.getLocalizedName(lang)
        imageView.tag = book // Store book data in tag
        
        cardView.addView(imageView)
        
        // Add library sticker (Serial Number)
        val stickerTextView = TextView(this)
        val size = dpToPx(38f)
        val stickerParams = android.widget.FrameLayout.LayoutParams(size, size)
        stickerParams.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        stickerParams.marginStart = dpToPx(16f)
        stickerTextView.layoutParams = stickerParams
        stickerTextView.background = ContextCompat.getDrawable(this, R.drawable.library_sticker)
        stickerTextView.text = book.sl
        stickerTextView.textSize = 12f
        stickerTextView.setTextColor(android.graphics.Color.parseColor("#5D4037"))
        stickerTextView.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        stickerTextView.gravity = android.view.Gravity.CENTER
        stickerTextView.setPadding(0, 0, 0, 0)
        stickerTextView.tag = "spine_text"
        
        cardView.addView(stickerTextView)

        // Title and Year Container
        val labelContainer = android.widget.LinearLayout(this)
        labelContainer.orientation = android.widget.LinearLayout.VERTICAL
        val labelParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        labelParams.gravity = android.view.Gravity.CENTER_VERTICAL
        labelParams.marginStart = dpToPx(72f) // Space for sticker
        labelParams.marginEnd = dpToPx(72f)   // Space to balance distance from serial and year
        labelContainer.layoutParams = labelParams
        labelContainer.gravity = android.view.Gravity.CENTER
        labelContainer.tag = "spine_text"

        // Main Title
        val textView = TextView(this)
        textView.text = book.getLocalizedName(lang)
        textView.setTextColor(android.graphics.Color.parseColor("#E0C090")) // Gold text
        textView.textSize = 16f
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textView.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        textView.gravity = android.view.Gravity.CENTER
        textView.maxLines = 2
        textView.ellipsize = android.text.TextUtils.TruncateAt.END
        labelContainer.addView(textView)

        // Subtitle (Year removed from here)
        val subText = book.getLocalizedSubName(lang)
        if (subText.isNotEmpty()) {
            val subTextView = TextView(this)
            subTextView.text = subText
            subTextView.setTextColor(android.graphics.Color.parseColor("#B0A080")) // Muted gold
            subTextView.textSize = 11f
            subTextView.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
            labelContainer.addView(subTextView)
        }
        
        cardView.addView(labelContainer)

        // Vertical Year at the end of spine
        val yearString = book.getLocalizedYear(lang)
        if (yearString.isNotEmpty()) {
            val yearSpineText = TextView(this)
            // Vertical arrangement: join characters with newlines
            yearSpineText.text = yearString.toCharArray().joinToString("\n")
            
            val yearParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            yearParams.gravity = android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL
            yearParams.marginEnd = dpToPx(16f)
            yearSpineText.layoutParams = yearParams
            
            yearSpineText.setTextColor(android.graphics.Color.parseColor("#C0B090"))
            yearSpineText.textSize = 10f
            yearSpineText.typeface = android.graphics.Typeface.DEFAULT_BOLD
            yearSpineText.gravity = android.view.Gravity.CENTER_HORIZONTAL
            yearSpineText.setLineSpacing(0f, 0.85f) // Tighter spacing for vertical stack
            
            cardView.addView(yearSpineText)
        }

        // Add reading progress line at the bottom
        if (progress > 0) {
            val progressContainer = android.widget.LinearLayout(this)
            val containerParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(3f)
            )
            containerParams.gravity = android.view.Gravity.BOTTOM
            containerParams.setMargins(dpToPx(16f), 0, dpToPx(16f), dpToPx(8f))
            progressContainer.layoutParams = containerParams
            progressContainer.orientation = android.widget.LinearLayout.HORIZONTAL
            progressContainer.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#33000000"))
            
            // The colored progress part
            val bar = android.view.View(this)
            val barParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, progress.toFloat())
            bar.layoutParams = barParams
            bar.setBackgroundColor(android.graphics.Color.parseColor("#E0C090")) // Gold
            
            // The remaining part of the track
            val remainder = android.view.View(this)
            val remainderParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, (100 - progress).toFloat())
            remainder.layoutParams = remainderParams
            
            progressContainer.addView(bar)
            progressContainer.addView(remainder)
            cardView.addView(progressContainer)
        }

        cardView.setOnClickListener {
            handleBookClick(imageView, cardView, book, lang, coverRes)
        }

        return cardView
    }

    private fun handleBookClick(imageView: ImageView, cardView: android.widget.FrameLayout, book: LibraryBook, lang: String, coverRes: Int) {
        showBookCoverOverlay(book, lang, coverRes)
    }

    private fun showBookCoverOverlay(book: LibraryBook, lang: String, coverRes: Int) {
        // Create fullscreen overlay
        val overlay = android.widget.FrameLayout(this)
        overlay.setBackgroundColor(android.graphics.Color.parseColor("#E0000000")) // Darker semi-transparent
        overlay.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        overlay.alpha = 0f
        overlay.isClickable = true
        
        // Create a container for cover and title
        val bookContainer = android.widget.FrameLayout(this)
        val containerParams = android.widget.FrameLayout.LayoutParams(
            dpToPx(280f),
            dpToPx(400f)
        )
        containerParams.gravity = android.view.Gravity.CENTER
        bookContainer.layoutParams = containerParams
        bookContainer.scaleX = 0.7f
        bookContainer.scaleY = 0.7f
        
        // Add cover image
        val coverImage = ImageView(this)
        val coverParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        coverImage.layoutParams = coverParams
        coverImage.setImageResource(coverRes)
        coverImage.scaleType = ImageView.ScaleType.FIT_XY
        coverImage.elevation = dpToPx(12f).toFloat()
        
        bookContainer.addView(coverImage)
        
        // Container for Title and Year to ensure they are stacked and centered together
        val textContainer = android.widget.LinearLayout(this)
        textContainer.orientation = android.widget.LinearLayout.VERTICAL
        textContainer.gravity = android.view.Gravity.CENTER
        val textContainerParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        textContainerParams.gravity = android.view.Gravity.CENTER
        textContainer.layoutParams = textContainerParams
        textContainer.translationZ = dpToPx(20f).toFloat()
        textContainer.elevation = dpToPx(15f).toFloat()

        // Add title text
        val titleText = TextView(this)
        titleText.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val displayTitle = book.getLocalizedName(lang)
        titleText.text = displayTitle
        titleText.textSize = 32f
        titleText.setTextColor(android.graphics.Color.parseColor("#F5F5DC")) // Beige/cream
        titleText.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        titleText.setShadowLayer(8f, 0f, 4f, android.graphics.Color.BLACK)
        titleText.gravity = android.view.Gravity.CENTER
        titleText.maxWidth = dpToPx(240f)
        textContainer.addView(titleText)

        // Add year text gently below
        val yearText = TextView(this)
        val yearParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        yearParams.topMargin = dpToPx(8f)
        yearText.layoutParams = yearParams
        
        val displayYear = book.getLocalizedYear(lang)
        if (displayYear.isNotEmpty()) {
            yearText.text = displayYear
            yearText.textSize = 16f
            yearText.setTextColor(android.graphics.Color.parseColor("#D0C0A0")) // Slightly darker/gold-ish beige
            yearText.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC)
            yearText.setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
            yearText.gravity = android.view.Gravity.CENTER
            textContainer.addView(yearText)
        }

        // Add arrival status message for unlinked books
        if (book.sheetId.isEmpty()) {
            val statusText = TextView(this)
            val statusParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            statusParams.topMargin = dpToPx(16f)
            statusText.layoutParams = statusParams
            
            val comingSoon = getString(R.string.status_coming_soon)
            val checkUpdates = getString(R.string.status_check_updates)
            statusText.text = "$comingSoon\n$checkUpdates"
            
            statusText.textSize = 14f
            statusText.setTextColor(android.graphics.Color.parseColor("#FFAB91")) // Soft coral
            statusText.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD_ITALIC)
            statusText.gravity = android.view.Gravity.CENTER
            statusText.setShadowLayer(4f, 0f, 2f, android.graphics.Color.BLACK)
            textContainer.addView(statusText)
        }

        bookContainer.addView(textContainer)
        overlay.addView(bookContainer)
        
        // Add to root view
        val rootView = window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
        rootView.addView(overlay)
        
        // Animate overlay entrance
        overlay.animate().alpha(1f).setDuration(400).start()
        bookContainer.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(500)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        
        // Handle overlay click
        overlay.setOnClickListener {
            if (book.sheetId.isNotEmpty()) {
                // Gentle transition to meditative images (automatically moves to index)
                startMeditativeTransition(rootView, overlay, book.bookId)
            } else {
                // Redirect back to book-list (close overlay) for unlinked books
                bookContainer.animate()
                    .scaleX(0.7f)
                    .scaleY(0.7f)
                    .alpha(0f)
                    .setDuration(350)
                    .start()
                overlay.animate()
                    .alpha(0f)
                    .setDuration(350)
                    .withEndAction {
                        rootView.removeView(overlay)
                    }
                    .start()
            }
        }
    }

    private fun startMeditativeTransition(rootView: android.view.ViewGroup, coverOverlay: View, bookId: String) {
        // Create full screen transition overlay
        val transitionOverlay = android.widget.FrameLayout(this)
        transitionOverlay.setBackgroundColor(android.graphics.Color.BLACK)
        transitionOverlay.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        transitionOverlay.alpha = 0f
        
        // Single image view for the random quote/photo
        val imageView = ImageView(this)
        val imgParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        imageView.layoutParams = imgParams
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.alpha = 0f
        
        transitionOverlay.addView(imageView)
        rootView.addView(transitionOverlay)
        
        // List of meditative images
        val images = listOf(
            R.drawable.card_photo_1, R.drawable.card_photo_2, R.drawable.card_photo_3,
            R.drawable.card_photo_4, R.drawable.card_photo_5, R.drawable.card_photo_6,
            R.drawable.card_photo_7, R.drawable.card_photo_8, R.drawable.card_photo_9,
            R.drawable.card_photo_10
        )
        
        // Pick exactly one random image
        val randomImage = images.random()
        imageView.setImageResource(randomImage)
        
        animationScope.launch {
            // 1. Smooth fade in for the black background and meditative image
            transitionOverlay.animate().alpha(1f).setDuration(800).start()
            imageView.animate().alpha(1f).setDuration(800).start()
            
            // CRITICAL: Hide bookshelf background immediately to prevent any flicker/bleeding
            bookshelfContainer.animate().alpha(0f).setDuration(400).start()
            
            // Wait for fade in to complete fully (opaque enough to hide background)
            delay(800)
            
            // Remove the cover overlay from background while fully obscured
            rootView.removeView(coverOverlay)
            
            // Complete the fade in wait
            delay(200)
            
            // 2. Display the image for a dedicated meditative moment
            delay(1200)
            
            // 3. Navigate directly to MainActivity (Transition takes 800ms)
            navigateToMain(bookId)
            
            // Cleanup the overlay AFTER the new activity is fully visible
            delay(1200)
            rootView.removeView(transitionOverlay)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun skipAnimation() {
        if (!isAnimationSkippable) return
        isAnimationSkippable = false
        animationJob?.cancel()

        animationScope.launch {
            bookshelfContainer.alpha = 1f
            bookshelfContainer.visibility = View.VISIBLE
            
            shelfBooksContainer.removeAllViews()
            populateBookshelf()
        }
    }

    private fun navigateToMain(bookId: String) {
        animationJob?.cancel()
        coverLayout.setOnClickListener(null)

        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("selected_book_id", bookId)
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun showLanguageSelectionDialog(isCancelable: Boolean) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedLangCode = sharedPreferences.getString("selected_language_code", null)
        val languageNamesArr = resources.getStringArray(R.array.language_names)
        val languageCodesArr = resources.getStringArray(R.array.language_codes)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_language_selector)
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.setCancelable(isCancelable)
        dialog.window?.let { WindowUtils.setStatusBarIconColor(it) }

        val rvLanguages = dialog.findViewById<RecyclerView>(R.id.rv_languages)
        rvLanguages.layoutManager = LinearLayoutManager(this)
        (rvLanguages.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        animationScope.launch {
            val languageAdapter = SimplifiedLanguageAdapter(
                languages = languageNamesArr.zip(languageCodesArr).toList(),
                currentSelectedCode = savedLangCode,
                onLanguageSelected = { langCode ->
                    saveLanguagePreference(langCode)
                    refreshShelf(langCode)
                    dialog.dismiss()
                }
            )
            rvLanguages.adapter = languageAdapter
        }
        dialog.show()
    }

    private fun saveLanguagePreference(languageCode: String) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putString("selected_language_code", languageCode)
            apply()
        }
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences(READER_THEME_PREFS, Context.MODE_PRIVATE)
        val themeStr = prefs.getString(KEY_READER_THEME, THEME_SEPIA) ?: THEME_SEPIA
        
        val targetMode = when (themeStr) {
            THEME_MIDNIGHT -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_SEPIA -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        
        if (AppCompatDelegate.getDefaultNightMode() != targetMode) {
            AppCompatDelegate.setDefaultNightMode(targetMode)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        animationJob?.cancel()
    }

    private fun preloadChapters() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedLangCode = sharedPreferences.getString("selected_language_code", null)

        if (savedLangCode != null) {
            val languageNames = resources.getStringArray(R.array.language_names)
            val languageCodes = resources.getStringArray(R.array.language_codes)
            val langIndex = languageCodes.indexOf(savedLangCode)

            if (langIndex != -1) {
                bookViewModel.fetchAndLoadChapters(savedLangCode, languageNames[langIndex], forceDownload = false, bookId = "kada_chabuk")
            }
        }
    }

    // --- Helper Extensions for LibraryBook Localization ---
    private fun LibraryBook.getLocalizedName(lang: String): String {
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

    private fun LibraryBook.getLocalizedSubName(lang: String): String {
        return when(lang) {
            "bn" -> bnSubName
            "hi" -> hiSubName
            "en" -> enSubName
            "as" -> asSubName
            "od" -> odSubName
            "tm" -> tmSubName
            else -> enSubName
        }.ifEmpty { enSubName }
    }

    private fun LibraryBook.getLocalizedYear(lang: String): String {
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