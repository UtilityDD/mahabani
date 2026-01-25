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

class CoverActivity : AppCompatActivity() {

    private val bookViewModel: BookViewModel by viewModels()
    private lateinit var coverLayout: View
    private lateinit var coverImageView: ImageView
    private lateinit var bookshelfContainer: View
    private lateinit var kadachabukBook: ImageView

    private val animationScope = CoroutineScope(Dispatchers.Main)
    private var animationJob: Job? = null
    private var isAnimationSkippable = true

    private lateinit var shelfBooksContainer: android.widget.LinearLayout
    private var currentBookshelfLanguage: String? = null
    
    // Class-level book definitions for searching/filtering
    private val bookDefinitions = listOf(
        BookData("Kada Chabuk", R.drawable.spine_horizontal_kada_chabuk, R.drawable.cover_kada_chabuk, "01"),
        BookData("The Echo", R.drawable.spine_horizontal_the_echo, R.drawable.cover_the_echo, "02"),
        BookData("Silent Hill", R.drawable.spine_horizontal_silent_hill, R.drawable.cover_silent_hill, "03"),
        BookData("Lost City", R.drawable.spine_horizontal_lost_city, R.drawable.cover_lost_city, "04"),
        BookData("Red Sky", R.drawable.spine_horizontal_red_sky, R.drawable.cover_red_sky, "05"),
        BookData("Ocean Deep", R.drawable.spine_horizontal_ocean_deep, R.drawable.cover_ocean_deep, "06"),
        BookData("Mystic Tales", R.drawable.spine_horizontal_the_echo, R.drawable.cover_the_echo, "07"),
        BookData("Desert Winds", R.drawable.spine_horizontal_red_sky, R.drawable.cover_red_sky, "08"),
        BookData("Frozen Dreams", R.drawable.spine_horizontal_silent_hill, R.drawable.cover_silent_hill, "09"),
        BookData("Crimson Night", R.drawable.spine_horizontal_red_sky, R.drawable.cover_red_sky, "10"),
        BookData("Golden Dawn", R.drawable.spine_horizontal_kada_chabuk, R.drawable.cover_kada_chabuk, "11"),
        BookData("Shadow Walker", R.drawable.spine_horizontal_silent_hill, R.drawable.cover_silent_hill, "12"),
        BookData("Ancient Ruins", R.drawable.spine_horizontal_lost_city, R.drawable.cover_lost_city, "13"),
        BookData("Starlight Path", R.drawable.spine_horizontal_ocean_deep, R.drawable.cover_ocean_deep, "14"),
        BookData("Thunder Storm", R.drawable.spine_horizontal_silent_hill, R.drawable.cover_silent_hill, "15")
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

        coverLayout.setOnClickListener {
            if (isAnimationSkippable) {
                skipAnimation()
            }
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab_language).setOnClickListener {
            showLanguageSelectionDialog(isCancelable = true)
        }
        
        setupSearch()
        handleWindowInsets()
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
        shelfBooksContainer.removeAllViews()
        val filteredList = if (query.isEmpty()) {
            bookDefinitions
        } else {
            bookDefinitions.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.serial.contains(query, ignoreCase = true) 
            }
        }
        
        for (book in filteredList) {
            val bookView = createBookView(book)
            shelfBooksContainer.addView(bookView)
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if language has changed while we were away
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val selectedLang = sharedPreferences.getString("selected_language_code", null)
        
        if (selectedLang != null && selectedLang != currentBookshelfLanguage) {
            // Only refresh if we've already populated once (to avoid interfering with initial animation)
            if (bookshelfContainer.visibility == View.VISIBLE) {
                refreshShelf(selectedLang)
            }
        }
    }

    private fun refreshShelf(newLang: String) {
        currentBookshelfLanguage = newLang
        shelfBooksContainer.removeAllViews()
        populateBookshelf()
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
            populateBookshelf()
            
            // Animate books entry (staggered)
            for (i in 0 until shelfBooksContainer.childCount) {
                val book = shelfBooksContainer.getChildAt(i)
                book.alpha = 0f
                book.translationY = 50f
                book.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 100L + 400)
                    .setDuration(400)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }

            delay(1200)
            
            isAnimationSkippable = false
        }
    }

    // Data class for book configuration - easy to add more books
    // TODO: Add horizontal spine and cover images to drawable folder
    // Naming: spine_horizontal_[name].png and cover_[name].png
    data class BookData(
        val title: String, 
        val spineDrawable: Int,  // Horizontal spine image (book lying flat)
        val coverDrawable: Int,  // Front cover image
        val serial: String       // Library serial number
    )

    private enum class BookState { SPINE, COVER }
    private val bookStates = mutableMapOf<String, BookState>()

    private fun populateBookshelf() {
        // Track the language used for this population
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        currentBookshelfLanguage = sharedPreferences.getString("selected_language_code", "bn") ?: "bn"

        // Initialize all as showing spine
        bookDefinitions.forEach { bookStates[it.title] = BookState.SPINE }

        for (book in bookDefinitions) {
            val bookView = createBookView(book)
            shelfBooksContainer.addView(bookView)
        }
    }

    private fun createBookView(book: BookData): View {
        // Container for the book with shadow
        val cardView = android.widget.FrameLayout(this)
        val cardParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            dpToPx(80f) // Height of horizontal book
        )
        cardParams.setMargins(0, dpToPx(8f), 0, dpToPx(8f))
        cardView.layoutParams = cardParams
        cardView.elevation = dpToPx(4f).toFloat()
        
        // The book image (will switch between spine and cover)
        val imageView = ImageView(this)
        val imageParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        )
        imageView.layoutParams = imageParams
        imageView.setImageResource(book.spineDrawable)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.contentDescription = book.title
        imageView.tag = book // Store book data in tag
        
        cardView.addView(imageView)
        
        // Add library sticker (Serial Number) on the left side of spine
        val stickerTextView = TextView(this)
        val size = dpToPx(38f) // Square size for circular sticker
        val stickerParams = android.widget.FrameLayout.LayoutParams(size, size)
        stickerParams.gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        stickerParams.marginStart = dpToPx(16f)
        stickerTextView.layoutParams = stickerParams
        stickerTextView.background = ContextCompat.getDrawable(this, R.drawable.library_sticker)
        stickerTextView.text = book.serial
        stickerTextView.textSize = 12f
        stickerTextView.setTextColor(android.graphics.Color.parseColor("#5D4037")) // Warm brown-grey
        stickerTextView.typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        stickerTextView.gravity = android.view.Gravity.CENTER
        stickerTextView.setPadding(0, 0, 0, 0)
        stickerTextView.tag = "spine_text" // Ensure it hides when flipping or clicking
        
        cardView.addView(stickerTextView)

        // Add text overlay on spine
        val textView = TextView(this)
        val textParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        textParams.gravity = android.view.Gravity.CENTER
        textView.layoutParams = textParams
        
        // Localize the title on the spine if it's Kada Chabuk
        val displayName = if (book.title == "Kada Chabuk") getLocalizedBookName() else book.title
        textView.text = displayName
        
        textView.setTextColor(android.graphics.Color.parseColor("#E0C090")) // Gold text
        textView.textSize = 16f
        textView.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textView.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
        textView.id = android.view.View.generateViewId()
        textView.tag = "spine_text" // Tag to identify and hide when flipping
        
        cardView.addView(textView)

        cardView.setOnClickListener {
            handleBookClick(imageView, cardView, book)
        }

        return cardView
    }

    private fun handleBookClick(imageView: ImageView, cardView: android.widget.FrameLayout, book: BookData) {
        // Show full screen cover overlay
        showBookCoverOverlay(book)
    }

    private fun showBookCoverOverlay(book: BookData) {
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
        coverImage.setImageResource(book.coverDrawable)
        coverImage.scaleType = ImageView.ScaleType.FIT_XY
        coverImage.elevation = dpToPx(12f).toFloat()
        
        bookContainer.addView(coverImage)
        
        // Add title text ON the cover (centered vertically)
        val titleText = TextView(this)
        val titleParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        )
        titleParams.gravity = android.view.Gravity.CENTER
        titleText.layoutParams = titleParams
        
        // Localize the title if it's Kada Chabuk
        val displayTitle = if (book.title == "Kada Chabuk") getLocalizedBookName() else book.title
        titleText.text = displayTitle
        
        titleText.textSize = 32f
        titleText.setTextColor(
            if (book.title == "Kada Chabuk") 
                android.graphics.Color.parseColor("#FFD700") // Gold for Kada Chabuk
            else 
                android.graphics.Color.parseColor("#F5F5DC") // Beige/cream for others
        )
        titleText.typeface = android.graphics.Typeface.create(android.graphics.Typeface.SERIF, android.graphics.Typeface.BOLD)
        titleText.setShadowLayer(8f, 0f, 4f, android.graphics.Color.BLACK)
        titleText.gravity = android.view.Gravity.CENTER
        titleText.maxWidth = dpToPx(240f)
        
        // CRITICAL: Ensure title is ABOVE the cover by setting higher translationZ/elevation
        titleText.translationZ = dpToPx(20f).toFloat() 
        titleText.elevation = dpToPx(15f).toFloat()
        
        // Removed semi-transparent background for a cleaner, more minimal look
        
        bookContainer.addView(titleText)
        overlay.addView(bookContainer)
        
        // REMOVED "Tap to Open" text for a cleaner, more minimal look as requested
        
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
            if (book.title == "Kada Chabuk") {
                // Gentle transition to meditative images (automatically moves to index)
                startMeditativeTransition(rootView, overlay)
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

    private fun startMeditativeTransition(rootView: android.view.ViewGroup, coverOverlay: View) {
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
            // 1. Quick fade in for the black background and meditative image
            transitionOverlay.animate().alpha(1f).setDuration(400).start()
            imageView.animate().alpha(1f).setDuration(600).start()
            delay(600)
            
            // Remove the cover overlay from background while obscured by black
            rootView.removeView(coverOverlay)
            
            // 2. Display the image for a very short meditative moment
            delay(1000)
            
            // 3. Navigate directly to MainActivity (without fading out the overlay here)
            // The activity transition (fade_out) will handle the smooth transition to the next screen.
            navigateToMain()
            
            // Cleanup the overlay after some delay to ensure it's not visible during activity exit
            delay(800)
            rootView.removeView(transitionOverlay)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private suspend fun fadeIn(view: View, duration: Long) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate().alpha(1f).setDuration(duration).start()
        delay(duration)
    }

    private suspend fun fadeOut(view: View, duration: Long, waitForCompletion: Boolean = true) {
        view.animate().alpha(0f).setDuration(duration).start()
        if (waitForCompletion) {
            delay(duration)
            view.visibility = View.INVISIBLE
        }
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
    private fun navigateToMain() {
        // Stop the animation when we navigate away
        animationJob?.cancel()
        coverLayout.setOnClickListener(null) // Prevent double taps

        // Now it's safe to start the new activity and its transition.
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        // Don't call finish() - keep CoverActivity in back stack so user can return to bookshelf
    }

    private fun getLocalizedBookName(): String {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val langCode = sharedPreferences.getString("selected_language_code", "bn") ?: "bn"
        
        return when (langCode) {
            "bn" -> "কড়া চাবুক"
            "hi" -> "कड़ा चाबुक"
            "en" -> "Kada Chabuk"
            "tm" -> "கடா சாபுக்"
            "kn" -> "ಕಡಾ ಚಾಬುಕ್"
            "as" -> "কড়া চাবুক"
            "od" -> "କଡ଼ା ଚାବୁକ୍"
            else -> "Kada Chabuk"
        }
    }

    private fun showLanguageSelectionDialog(isCancelable: Boolean) {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedLangCode = sharedPreferences.getString("selected_language_code", null)
        val languageNamesArr = resources.getStringArray(R.array.language_names)
        val languageCodesArr = resources.getStringArray(R.array.language_codes)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_language_selector)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation
        dialog.setCancelable(isCancelable)
        dialog.window?.let { WindowUtils.setStatusBarIconColor(it) }

        val rvLanguages = dialog.findViewById<RecyclerView>(R.id.rv_languages)
        rvLanguages.layoutManager = LinearLayoutManager(this)
        (rvLanguages.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        animationScope.launch {
            val downloadedCodes = bookViewModel.getDownloadedLanguageCodes()
            val languageAdapter = LanguageAdapter(
                languages = languageNamesArr.zip(languageCodesArr).toList(),
                downloadedLanguageCodes = downloadedCodes,
                currentSelectedCode = savedLangCode,
                onLanguageSelected = { langCode, langName ->
                    if (downloadedCodes.contains(langCode)) {
                        saveLanguagePreference(langCode)
                        bookViewModel.fetchAndLoadChapters(langCode, langName, forceDownload = false)
                        refreshShelf(langCode)
                        dialog.dismiss()
                    } else {
                        showDownloadConfirmationDialog(langName) {
                            saveLanguagePreference(langCode)
                            bookViewModel.fetchAndLoadChapters(langCode, langName, forceDownload = false)
                            refreshShelf(langCode)
                            dialog.dismiss()
                        }
                    }
                },
                onLanguageDelete = { langCode, langName ->
                    showDeleteLanguageConfirmationDialog(langCode, langName) {
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
            .setTitle("Download in $langName?")
            .setMessage(HtmlCompat.fromHtml(
                "<i>Kada Chabuk</i> in '$langName' are not downloaded. Would you like to download them now?",
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
            theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
            val primaryColor = if (typedValue.resourceId != 0) {
                ContextCompat.getColor(this, typedValue.resourceId)
            } else typedValue.data
            positiveButton.setBackgroundColor(primaryColor)
            positiveButton.setTextColor(android.graphics.Color.WHITE)
        }

        dialog.show()
    }

    private fun showDeleteLanguageConfirmationDialog(langCode: String, langName: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Data for $langName?")
            .setMessage("This will remove all downloaded chapters for this language.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                bookViewModel.deleteChaptersForLanguage(langCode)
                onConfirm()
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

    private fun applySavedTheme() {
        val sharedPreferences = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val nightMode = sharedPreferences.getInt("NightMode", AppCompatDelegate.MODE_NIGHT_NO)
        AppCompatDelegate.setDefaultNightMode(nightMode)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel the coroutine job to prevent leaks
        animationJob?.cancel()
    }

    private fun preloadChapters() {
        val sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedLangCode = sharedPreferences.getString("selected_language_code", null)

        // If a language is selected, start fetching chapters.
        // If not, MainActivity will show the language selection dialog on first launch.
        if (savedLangCode != null) {
            val languageNames = resources.getStringArray(R.array.language_names)
            val languageCodes = resources.getStringArray(R.array.language_codes)
            val langIndex = languageCodes.indexOf(savedLangCode)

            if (langIndex != -1) {
                Log.d("CoverActivity", "Preloading chapters for $savedLangCode")
                bookViewModel.fetchAndLoadChapters(savedLangCode, languageNames[langIndex], forceDownload = false)
            }
        }
    }
}