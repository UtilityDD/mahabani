package com.blackgrapes.kadachabuk

import android.content.ClipData
import android.content.ClipboardManager
import android.app.Dialog
import android.animation.ValueAnimator
import android.net.Uri
import android.content.Intent
import android.view.LayoutInflater
import android.content.Context
import android.os.Build
import android.graphics.Color // <-- IMPORT THIS
import android.view.MotionEvent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.view.ActionMode
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.view.WindowInsetsController
import android.view.Menu
import android.view.MenuItem
import android.view.GestureDetector
import android.speech.tts.TextToSpeech
import java.util.Locale
import android.widget.ImageButton
import android.widget.ImageView // <-- IMPORT THIS
import android.util.TypedValue
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import androidx.core.content.ContextCompat // <-- IMPORT THIS
import androidx.core.app.ShareCompat
import android.widget.Toast
import android.util.Log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.core.view.updatePadding
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlin.random.Random // <-- IMPORT THIS
import java.util.concurrent.TimeUnit

private const val FONT_PREFS = "FontPrefs"
private const val KEY_FONT_SIZE = "fontSize"
private const val DEFAULT_FONT_SIZE = 18f
private const val BOOKMARK_PREFS = "BookmarkPrefs"
private const val READER_THEME_PREFS = "ReaderThemePrefs"
private const val KEY_READER_THEME = "readerTheme"
private const val THEME_LIGHT = "light"
private const val THEME_SEPIA = "sepia"
private const val THEME_MIDNIGHT = "midnight"
private const val SCROLL_PREFS = "ScrollPositions"
private const val NOTES_PREFS = "MyNotesPrefs"
private const val HISTORY_PREFS = "ReadingHistoryPrefs"
private const val KEY_NOTES = "notes"
private const val LAST_READ_PREFS = "LastReadPrefs"
private const val KEY_LAST_READ_SERIAL = "lastReadSerial"
private const val KEY_LAST_READ_LANG = "lastReadLang"

class DetailActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private lateinit var imageViewHeader: ImageView // <-- DECLARE ImageView
    private var isFullScreen = false
    private lateinit var textViewData: TextView
    private lateinit var fontSettingsButton: ImageButton
    private lateinit var backButton: ImageButton
    private lateinit var bookmarkButton: ImageButton
    private lateinit var buttonTts: ImageButton
    private var textToSpeech: TextToSpeech? = null
    private var isTtsPlaying = false
    private var isTtsInitialized = false
    private var currentTtsOffset = 0
    private lateinit var chapterSerial: String
    private lateinit var languageCode: String
    private lateinit var scrollToTopButton: ImageButton
    private lateinit var searchNavigationLayout: LinearLayout
    private lateinit var previousMatchButton: ImageButton
    private lateinit var nextMatchButton: ImageButton
    private lateinit var matchCountTextView: TextView
    private lateinit var bookId: String

    private var chapterHeading: String? = null
    private var chapterDate: String? = null
    private var customActionMenu: View? = null
    private val matchIndices = mutableListOf<Int>()
    private var previousMatchIndex = -1
    private var sessionStartTime: Long = 0
    private var currentMatchIndex = -1

    private var scrollAnimator: ValueAnimator? = null
    // Array of your drawable resource IDs
    private val headerImageDrawables = intArrayOf(
        R.drawable.thakur1, // Replace with your actual drawable names
        R.drawable.thakur2,
        R.drawable.thakur3,
        R.drawable.thakur4,
        R.drawable.thakur5,
        R.drawable.thakur6,
        R.drawable.thakur7,
        R.drawable.thakur8,
        R.drawable.thakur9
        // Add all your header image drawables here
    )

    private lateinit var gestureDetector: GestureDetector
    private lateinit var bookRepository: BookRepository // Access repository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- CRITICAL: Extract Intent Data First ---
        chapterHeading = intent.getStringExtra("EXTRA_HEADING")
        chapterDate = intent.getStringExtra("EXTRA_DATE")
        val dataContent = intent.getStringExtra("EXTRA_DATA")
        val writer = intent.getStringExtra("EXTRA_WRITER")
        chapterSerial = intent.getStringExtra("EXTRA_SERIAL") ?: ""
        languageCode = intent.getStringExtra("EXTRA_LANGUAGE_CODE") ?: ""
        bookId = intent.getStringExtra("EXTRA_BOOK_ID") ?: "kada_chabuk"

        setContentView(R.layout.activity_detail)
        // Enable the action bar menu
        enableActionBarMenu()
        
        bookRepository = BookRepository(this) // Initialize repo

        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            fontSettingsButton.updatePadding(top = systemBars.top)
            bookmarkButton.updatePadding(top = systemBars.top)
            buttonTts.updatePadding(top = systemBars.top)
            backButton.updatePadding(top = systemBars.top)
            insets
        }

        scrollView = findViewById(R.id.scrollView)
        imageViewHeader = findViewById(R.id.imageViewHeader)
        
        buttonTts = findViewById(R.id.button_tts)

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = when (languageCode) {
                    "bn" -> Locale("bn", "IN")
                    "hi" -> Locale("hi", "IN")
                    else -> Locale.US
                }
                textToSpeech?.setLanguage(locale)
                textToSpeech?.setSpeechRate(0.9f) // More natural, slightly slower pace
                
                // Try to find a male voice for the current locale
                val voices = textToSpeech?.voices
                if (voices != null) {
                    val maleVoice = voices.find { voice ->
                        voice.locale.language == locale.language && 
                        voice.locale.country == locale.country &&
                        (voice.name.lowercase().contains("male") || 
                         voice.name.lowercase().contains("-x-") && voice.name.lowercase().contains("-a-") || // Many Google male voices have 'a' or 'd'
                         voice.name.lowercase().contains("-d-")) 
                    }
                    if (maleVoice != null) {
                        textToSpeech?.voice = maleVoice
                        Log.d("TTS", "Selected male voice: ${maleVoice.name}")
                    }
                }
                isTtsInitialized = true
                Log.d("TTS", "Initialization success for $languageCode")
            } else {
                Log.e("TTS", "Initialization failed")
            }
        }

        buttonTts.setOnClickListener {
            if (!isTtsInitialized) {
                Toast.makeText(this, "Voice engine is warming up, please wait...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isTtsPlaying) {
                pauseTts()
            } else {
                startTts()
            }
        }
        
        // Initialize Gesture Detector for Swipe Navigation
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 80 // Reduced slightly for better sensitivity
            private val SWIPE_VELOCITY_THRESHOLD = 80

            override fun onDown(e: MotionEvent): Boolean {
                return true // CRITICAL: Must return true to detect further gestures
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                Log.d("DetailActivity", "onFling detected: diffX=$diffX, diffY=$diffY")
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            Log.d("DetailActivity", "Swipe Right -> Previous")
                            onSwipeRight()
                        } else {
                            Log.d("DetailActivity", "Swipe Left -> Next")
                            onSwipeLeft()
                        }
                        return true
                    }
                }
                return false
            }
        })

        // Attach touch listener to ScrollView to detect swipes
        scrollView.setOnTouchListener { v, event ->
            if (gestureDetector.onTouchEvent(event)) {
                return@setOnTouchListener true
            }
            // Add a touch listener to cancel animations on user interaction (moved from separate listener)
            if (event.action == MotionEvent.ACTION_DOWN) {
                scrollAnimator?.cancel()
            }
            // Return false to allow ScrollView to handle scrolling
            false 
        }

        val textViewHeading: TextView = findViewById(R.id.textViewHeading)
        val textViewDate: TextView = findViewById(R.id.textViewDate)
        val textViewWriter: TextView = findViewById(R.id.textViewWriter)
        textViewData = findViewById(R.id.textViewData)
        fontSettingsButton = findViewById(R.id.button_font_settings)
        backButton = findViewById(R.id.button_back)
        bookmarkButton = findViewById(R.id.button_bookmark)
        searchNavigationLayout = findViewById(R.id.search_navigation_layout)
        previousMatchButton = findViewById(R.id.button_previous_match)
        scrollToTopButton = findViewById(R.id.button_scroll_to_top)
        nextMatchButton = findViewById(R.id.button_next_match)
        matchCountTextView = findViewById(R.id.text_view_match_count)
        customActionMenu = findViewById(R.id.custom_action_menu)


        // Intent data extraction was moved to the very top of onCreate to fix TTS race condition

        // Update read status in background
        if (chapterSerial.isNotEmpty() && languageCode.isNotEmpty()) {
            val repository = BookRepository(this)
            CoroutineScope(Dispatchers.IO).launch {
                repository.markChapterAsRead(languageCode, bookId, chapterSerial)
            }
        }

        textViewHeading.text = chapterHeading
        val formattedDate = chapterDate?.removeSurrounding("(", ")") ?: ""
        textViewDate.text = if (formattedDate.trim().equals("N/A", ignoreCase = true)) "" else formattedDate

        // --- MARKDOWN RENDERING ---
        
        // Pre-process content to handle custom image tags
        val processedContent = processCustomImageTags(dataContent ?: "")

        // Resolve current theme for caption color contrast
        val themePrefs = getSharedPreferences(READER_THEME_PREFS, Context.MODE_PRIVATE)
        val currentReaderTheme = themePrefs.getString(KEY_READER_THEME, THEME_SEPIA) ?: THEME_SEPIA
        val captionColor = if (currentReaderTheme == THEME_MIDNIGHT) {
            android.graphics.Color.LTGRAY
        } else {
            android.graphics.Color.DKGRAY
        }

        // 1. Create a Markwon instance (with table, link, image, and html support)
        val markwon = Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(io.noties.markwon.html.HtmlPlugin.create { plugin ->
                plugin.addHandler(object : io.noties.markwon.html.TagHandler() {
                    override fun handle(visitor: io.noties.markwon.MarkwonVisitor, renderer: io.noties.markwon.html.MarkwonHtmlRenderer, tag: io.noties.markwon.html.HtmlTag) {
                         val spans = arrayOf(
                             android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),
                             android.text.style.RelativeSizeSpan(0.75f),
                             android.text.style.StyleSpan(android.graphics.Typeface.ITALIC),
                             android.text.style.ForegroundColorSpan(captionColor)
                         )
                         for (span in spans) {
                             visitor.builder().setSpan(span, tag.start(), tag.end(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                         }
                    }

                    override fun supportedTags(): Collection<String> {
                        return setOf("book-caption")
                    }
                })
            }) // Enable HTML with custom caption styling
            .usePlugin(io.noties.markwon.image.ImagesPlugin.create { plugin ->
                plugin.addSchemeHandler(object : io.noties.markwon.image.SchemeHandler() {
                    override fun handle(raw: String, uri: Uri): io.noties.markwon.image.ImageItem {
                        val transparentDrawable = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
                        return try {
                            val resourceName = uri.authority
                            if (resourceName != null) {
                                val resId = resources.getIdentifier(resourceName, "drawable", packageName)
                                if (resId != 0) {
                                    val drawable = ContextCompat.getDrawable(this@DetailActivity, resId)
                                    io.noties.markwon.image.ImageItem.withResult(drawable ?: transparentDrawable)
                                } else {
                                    io.noties.markwon.image.ImageItem.withResult(transparentDrawable) 
                                }
                            } else {
                                io.noties.markwon.image.ImageItem.withResult(transparentDrawable)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            io.noties.markwon.image.ImageItem.withResult(transparentDrawable)
                        }
                    }

                    override fun supportedSchemes(): Collection<String> {
                        return setOf("drawable")
                    }
                })
            })
            .build()

        // 2. Set the processed markdown content to the TextView
        markwon.setMarkdown(textViewData, processedContent)

        title = chapterHeading ?: "Details"

        loadAndApplyFontSize()
        applyReaderTheme()
        textViewWriter.text = writer
        setupFontSettingsButton()
        setupBookmarkButton()
        setupSearchNavigation()

        // Request focus for the TextView
        textViewData.requestFocus()

        // Enable text selection
        textViewData.setTextIsSelectable(true)

        // Set custom action mode callback for text selection
        textViewData.customSelectionActionModeCallback = customActionModeCallback
        
        backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Add a touch listener to cancel animations on user interaction.
        scrollView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                scrollAnimator?.cancel()
            }
            false // Return false to not consume the event, allowing normal scroll.
        }

        scrollToTopButton.setOnClickListener {
            // Cancel any ongoing scroll animation before starting a new one.
            scrollAnimator?.cancel()

            // Animate the scroll to the top with an ease-out effect
            val currentScrollY = scrollView.scrollY
            if (currentScrollY > 0) {
                scrollAnimator = ValueAnimator.ofInt(currentScrollY, 0).apply {
                    duration = 1200L // Increased duration for a more pronounced slow-down
                    interpolator = DecelerateInterpolator(3.0f) // Higher factor for a more dramatic ease-in at the end
                    addUpdateListener { animation ->
                        val animatedValue = animation.animatedValue as Int
                        scrollView.scrollTo(0, animatedValue)
                    }
                    start()
                }
            }
        }

        val searchQuery = intent.getStringExtra("EXTRA_SEARCH_QUERY")
        // Check for a saved scroll position, but only if not coming from a search result.
        if (searchQuery.isNullOrEmpty()) {
            checkForSavedScrollPosition()
        }

        // Enter full screen as soon as the activity is created
        enterFullScreen()

        // Set a random header image
        setRandomHeaderImage()

        // Handle reading history tracking and display
        incrementReadCount()

        // Add a scroll listener to fade out the header image on scroll.
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = scrollView.scrollY

            // Re-apply the status bar icon color on scroll. This is a robust way to prevent
            // the system from reverting it, especially with complex layouts.
            WindowUtils.setStatusBarIconColor(window)

            val imageHeight = imageViewHeader.height.toFloat()

            // Calculate alpha: 1.0 (fully visible) at scrollY 0, to 0.0 (fully transparent)
            // as the user scrolls past the image's height.
            val topIconsAlpha = (1.0f - (scrollY / imageHeight)).coerceIn(0f, 1f)

            // Calculate alpha for the "scroll to top" button. It fades in as the top icons fade out.
            // It starts appearing after scrolling past a fraction of the image height.
            val scrollToTopAlpha = (scrollY / imageHeight - 0.5f).coerceIn(0f, 1f)

            // Apply the same fade effect to the header image and the top icons.
            imageViewHeader.alpha = topIconsAlpha
            backButton.alpha = topIconsAlpha
            bookmarkButton.alpha = topIconsAlpha
            fontSettingsButton.alpha = topIconsAlpha

            // Apply the fade effect to the scroll-to-top button.
            scrollToTopButton.visibility = if (scrollToTopAlpha > 0) View.VISIBLE else View.GONE
            scrollToTopButton.alpha = scrollToTopAlpha
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // When the window gains focus (e.g., after a dialog closes), re-apply our desired icon color.
            WindowUtils.setStatusBarIconColor(window)
        }
    }

    private val customActionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // Calculate the position to show the menu
            val layout = textViewData.layout
            if (layout != null) {
                val startSelection = textViewData.selectionStart
                val line = layout.getLineForOffset(startSelection)
                // Y position is the top of the line of text, minus the scroll position, plus the TextView's top margin.
                val yPos = layout.getLineTop(line) - scrollView.scrollY + textViewData.top

                customActionMenu?.let {
                    // Position the menu above the selected text line.
                    val finalY = (yPos - it.height - 16).toFloat() // 16px margin

                    // Set initial state for animation: slightly lower and invisible
                    it.translationY = finalY + 20f // Start 20px lower
                    it.alpha = 0f
                    it.visibility = View.VISIBLE

                    // Animate to final state: fade in and slide up
                    it.animate()
                        .translationY(finalY)
                        .alpha(1f)
                        .setDuration(150) // A short, subtle duration
                        .start()
                }
            } else {
                customActionMenu?.visibility = View.VISIBLE // Fallback to top if layout is null
            }
            setupCustomMenuClickListeners(mode)

            // Do not tint any icons to preserve their original colors.

            // Prevent the default menu from showing
            menu?.clear()
            return true // We've handled it
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            // Again, prevent the default menu
            menu?.clear()
            return true // IMPORTANT: Return true to keep the action mode alive.
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            // This will not be called because we are not using the default menu items
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            // Animate the menu out (fade out)
            customActionMenu?.animate()
                ?.alpha(0f)
                ?.setDuration(150)
                ?.withEndAction {
                    // After animation, hide the view and clear tints
                    customActionMenu?.visibility = View.GONE
                    // No need to clear filters as none are applied.
                }
                ?.start()
        }
    }

    private fun setupCustomMenuClickListeners(mode: ActionMode?) {
        customActionMenu?.findViewById<ImageButton>(R.id.action_copy)?.setOnClickListener {
            val rawSelectedText = getSelectedText()
            if (rawSelectedText.isNotEmpty()) {
                val textToShare = getFormattedTextForAction(rawSelectedText)
                copyToClipboard(textToShare)
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
            mode?.finish()
        }

        customActionMenu?.findViewById<ImageButton>(R.id.action_share_whatsapp)?.setOnClickListener {
            val rawSelectedText = getSelectedText()
            if (rawSelectedText.isNotEmpty()) {
                val textToShare = getFormattedTextForAction(rawSelectedText)
                shareToApp(textToShare, "com.whatsapp")
            }
            mode?.finish()
        }

        customActionMenu?.findViewById<ImageButton>(R.id.action_share_facebook)?.setOnClickListener {
            val rawSelectedText = getSelectedText()
            if (rawSelectedText.isNotEmpty()) {
                val textToShare = getFormattedTextForAction(rawSelectedText)
                shareToApp(textToShare, "com.facebook.katana")
            }
            mode?.finish()
        }

        customActionMenu?.findViewById<ImageButton>(R.id.action_keep_notes)?.setOnClickListener {
            val rawSelectedText = getSelectedText()
            if (rawSelectedText.isNotEmpty()) {
                val textToSave = getFormattedTextForAction(rawSelectedText)
                saveNote(textToSave)
                Toast.makeText(this, "Note saved", Toast.LENGTH_SHORT).show()
            }
            mode?.finish()
        }

        customActionMenu?.findViewById<ImageButton>(R.id.action_report_error)?.setOnClickListener {
            val rawSelectedText = getSelectedText()
            if (rawSelectedText.isNotEmpty()) {
                val textToReport = getFormattedTextForAction(rawSelectedText)
                val subject = "I found an error!"
                val body = "Please rectify the errors in spelling etc. in the following lines:\n\n\"$textToReport\"\n\n[Corrected lines are: write corrected lines here]"

                // For ACTION_SENDTO, subject and body must be part of the mailto URI.
                val uriText = "mailto:kadachabuk@gmail.com" +
                        "?subject=" + Uri.encode(subject) +
                        "&body=" + Uri.encode(body)
                val mailtoUri = Uri.parse(uriText)

                val emailIntent = Intent(Intent.ACTION_SENDTO, mailtoUri)
                try {
                    startActivity(emailIntent)
                } catch (ex: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, "There are no email clients installed.", Toast.LENGTH_SHORT).show()
                }
            }
            mode?.finish()
        }
    }

    private fun saveNote(note: String) {
        val prefs = getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)
        val existingNotes = prefs.getStringSet(KEY_NOTES, emptySet())?.toMutableSet()
            ?: mutableSetOf()

        // Create a JSON object for the new note
        val noteObject = JSONObject()
        noteObject.put("text", note)
        noteObject.put("timestamp", System.currentTimeMillis())

        existingNotes.add(noteObject.toString())
        with(prefs.edit()) {
            putStringSet(KEY_NOTES, existingNotes)
            apply()
        }
    }

    private fun getFormattedTextForAction(selectedText: String): String {
        val header = chapterHeading ?: ""
        val date = chapterDate?.removeSurrounding("(", ")") ?: ""
        val attribution = "\n\n[${header}, ${date}]"
        return "$selectedText$attribution"
    }

    private fun getSelectedText(): String {
        val startIndex = textViewData.selectionStart
        val endIndex = textViewData.selectionEnd
        return if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
            textViewData.text.substring(startIndex, endIndex)
        } else {
            ""
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("KadaChabuk_Copy", text)
        clipboard.setPrimaryClip(clip)
    }

    override fun onResume() {
        super.onResume()
        sessionStartTime = System.currentTimeMillis()
        highlightSearchTerm()
    }

    private fun setupBookmarkButton() {
        checkAndSetBookmarkState()

        bookmarkButton.setOnClickListener {
            val prefs = getSharedPreferences(BOOKMARK_PREFS, Context.MODE_PRIVATE)
            val key = getBookmarkKey() ?: return@setOnClickListener
            val isBookmarked = prefs.getBoolean(key, false)

            with(prefs.edit()) {
                putBoolean(key, !isBookmarked)
                apply()
            }

            if (!isBookmarked) {
                bookmarkButton.setImageResource(R.drawable.ic_bookmark_filled)
                Toast.makeText(this, "Bookmark added", Toast.LENGTH_SHORT).show()
            } else {
                bookmarkButton.setImageResource(R.drawable.ic_bookmark_border)
                Toast.makeText(this, "Bookmark removed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkAndSetBookmarkState() {
        val key = getBookmarkKey() ?: return
        val prefs = getSharedPreferences(BOOKMARK_PREFS, Context.MODE_PRIVATE)
        val isBookmarked = prefs.getBoolean(key, false)
        if (isBookmarked) {
            bookmarkButton.setImageResource(R.drawable.ic_bookmark_filled)
        } else {
            bookmarkButton.setImageResource(R.drawable.ic_bookmark_border)
        }
    }

    private fun getScrollPositionKey(): String? {
        return if (::chapterSerial.isInitialized && ::languageCode.isInitialized && chapterSerial.isNotEmpty() && languageCode.isNotEmpty()) {
            "scroll_pos_${bookId}_${languageCode}_${chapterSerial}"
        } else {
            null
        }
    }

    private fun getBookmarkKey(): String? {
        return if (::chapterSerial.isInitialized && ::languageCode.isInitialized && chapterSerial.isNotEmpty() && languageCode.isNotEmpty()) {
            "bookmark_${bookId}_${languageCode}_${chapterSerial}"
        } else {
            null
        }
    }

    private fun saveScrollPosition() {
        val scrollKey = getScrollPositionKey()
        val timeKey = getTimestampKey()

        if (scrollKey != null && timeKey != null) {
            val sharedPreferences = getSharedPreferences(SCROLL_PREFS, Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putInt(scrollKey, scrollView.scrollY)
                putLong(timeKey, System.currentTimeMillis()) // Also save the current time
                apply()
            }
        }
    }

    private fun getTimestampKey(): String? {
        return if (::chapterSerial.isInitialized && ::languageCode.isInitialized && chapterSerial.isNotEmpty() && languageCode.isNotEmpty()) {
            "scroll_time_${bookId}_${languageCode}_${chapterSerial}"
        } else {
            null
        }
    }

    private fun checkForSavedScrollPosition() {
        val scrollKey = getScrollPositionKey()
        val timeKey = getTimestampKey()

        if (scrollKey != null && timeKey != null) {
            val sharedPreferences = getSharedPreferences(SCROLL_PREFS, Context.MODE_PRIVATE)
            val savedScrollY = sharedPreferences.getInt(scrollKey, 0)
            val savedTimestamp = sharedPreferences.getLong(timeKey, 0)

            if (savedScrollY > 100 && savedTimestamp > 0) { // Only prompt if they've scrolled a bit.
                val customView = LayoutInflater.from(this).inflate(R.layout.dialog_resume_reading, null)
                MaterialAlertDialogBuilder(this)
                    .setView(customView)
                    .setPositiveButton("Yes") { dialog, _ ->
                        // Cancel any other ongoing scroll animation before starting this one.
                        scrollAnimator?.cancel()

                        scrollView.post {
                            // Cancel any ongoing scroll animation.
                            scrollAnimator?.cancel()

                            // Use a ValueAnimator for a smoother, decelerating scroll.
                            scrollAnimator = ValueAnimator.ofInt(scrollView.scrollY, savedScrollY).apply {
                                duration = 1200L // A longer duration for a graceful scroll
                                interpolator = DecelerateInterpolator(3.0f) // Slows down significantly at the end
                                addUpdateListener { animation ->
                                    val animatedValue = animation.animatedValue as Int
                                    scrollView.scrollTo(0, animatedValue)
                                }
                                start()
                            }
                            highlightLineAt(savedScrollY) // Add the highlight animation
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun incrementReadCount() {
        val historyKeyBase = getHistoryKeyBase() ?: return
        val prefs = getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)

        // Increment read count for this session
        val countKey = "count_$historyKeyBase"
        val currentCount = prefs.getInt(countKey, 0)
        val newCount = currentCount + 1
        prefs.edit().putInt(countKey, newCount).apply()
    }

    private fun saveReadingTime() {
        val historyKeyBase = getHistoryKeyBase() ?: return
        if (sessionStartTime == 0L) return

        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        // Only save if the session was longer than 10 seconds to avoid counting brief views
        if (sessionDuration < 10000) return

        val prefs = getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
        val timeKey = "time_$historyKeyBase"

        val existingTime = prefs.getLong(timeKey, 0)
        val newTotalTime = existingTime + sessionDuration

        prefs.edit().putLong(timeKey, newTotalTime).apply()
    }

    private fun getHistoryKeyBase(): String? {
        return if (::chapterSerial.isInitialized && ::languageCode.isInitialized && chapterSerial.isNotEmpty() && languageCode.isNotEmpty()) {
            "${bookId}_${languageCode}_${chapterSerial}"
        } else {
            null
        }
    }

    private fun highlightLineAt(scrollY: Int) {
        val layout = textViewData.layout ?: return

        // Find the line number at the given scroll Y position.
        val line = layout.getLineForVertical(scrollY)

        // Get the start and end character indices for that line.
        val lineStart = layout.getLineStart(line)
        val lineEnd = layout.getLineEnd(line)

        if (lineStart >= lineEnd) return // Nothing to highlight

        val originalText = textViewData.text
        val spannable = SpannableStringBuilder(originalText)

        // Create a ValueAnimator to fade the highlight color
        val animator = ValueAnimator.ofArgb(
            ContextCompat.getColor(this, R.color.highlight_color),
            ContextCompat.getColor(this, android.R.color.transparent)
        )
        animator.duration = 2000 // 2 seconds
        animator.addUpdateListener { animation ->
            val color = animation.animatedValue as Int
            val highlightSpan = BackgroundColorSpan(color)
            spannable.setSpan(highlightSpan, lineStart, lineEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            textViewData.text = spannable
        }
        animator.start()
    }

    override fun onPause() {
        super.onPause()
        saveReadingTime()
        saveScrollPosition()
        saveLastReadChapter()
    }

    private fun saveLastReadChapter() {
        if (::chapterSerial.isInitialized && ::languageCode.isInitialized && chapterSerial.isNotEmpty() && languageCode.isNotEmpty()) {
            val prefs = getSharedPreferences(LAST_READ_PREFS, Context.MODE_PRIVATE)
            with(prefs.edit()) {
                putString("${KEY_LAST_READ_SERIAL}_$bookId", chapterSerial)
                putString("${KEY_LAST_READ_LANG}_$bookId", languageCode)
                apply()
            }
        }
    }

    private fun setRandomHeaderImage() {
        if (headerImageDrawables.isNotEmpty()) {
            val randomIndex = Random.nextInt(headerImageDrawables.size)
            val randomImageResId = headerImageDrawables[randomIndex]
            // imageViewHeader.setImageResource(randomImageResId) // Simple way
            // For potentially smoother loading with large images or more control:
            imageViewHeader.setImageDrawable(ContextCompat.getDrawable(this, randomImageResId))
        } else {
            // Optional: Hide ImageView or set a default placeholder if no images are available
            imageViewHeader.visibility = View.GONE
        }
    }

    private fun highlightSearchTerm() {
        val searchQuery = intent.getStringExtra("EXTRA_SEARCH_QUERY")
        if (searchQuery.isNullOrEmpty()) {
            return // No query to highlight
        }

        // Work with the existing Spannable text to preserve Markdown formatting
        val spannable = SpannableStringBuilder(textViewData.text)
        val fullText = spannable.toString()
        val highlightColor = ContextCompat.getColor(this, R.color.highlight_color) // Make sure to define this color

        matchIndices.clear()
        var index = fullText.indexOf(searchQuery, 0, ignoreCase = true)
        while (index >= 0) {
            matchIndices.add(index)
            val span = BackgroundColorSpan(highlightColor)
            spannable.setSpan(span, index, index + searchQuery.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = fullText.indexOf(searchQuery, index + searchQuery.length, ignoreCase = true)
        }

        textViewData.text = spannable
        
        if (matchIndices.isNotEmpty()) {
            currentMatchIndex = 0
            scrollToMatch(currentMatchIndex)
            updateNavigationState()
            searchNavigationLayout.visibility = View.VISIBLE
        } else {
            searchNavigationLayout.visibility = View.GONE
        }
    }

    private fun setupSearchNavigation() {
        previousMatchButton.setOnClickListener {
            if (matchIndices.isNotEmpty()) {
                if (currentMatchIndex > 0) {
                    currentMatchIndex--
                    scrollToMatch(currentMatchIndex)
                    updateNavigationState()
                }
            }
        }

        nextMatchButton.setOnClickListener {
            if (matchIndices.isNotEmpty()) {
                if (currentMatchIndex < matchIndices.size - 1) {
                    currentMatchIndex++
                    scrollToMatch(currentMatchIndex)
                    updateNavigationState()
                }
            }
        }
    }

    private fun updateNavigationState() {
        if (matchIndices.isNotEmpty()) {
            matchCountTextView.text = "${currentMatchIndex + 1} of ${matchIndices.size}"
            previousMatchButton.isEnabled = currentMatchIndex > 0
            nextMatchButton.isEnabled = currentMatchIndex < matchIndices.size - 1
        }
    }

    private fun scrollToMatch(matchIndex: Int) {
        if (matchIndex < 0 || matchIndex >= matchIndices.size) return

        val charIndex = matchIndices[matchIndex]
        val searchQueryLength = intent.getStringExtra("EXTRA_SEARCH_QUERY")?.length ?: 0
        if (searchQueryLength == 0) return

        val spannable = SpannableStringBuilder(textViewData.text)

        if (previousMatchIndex != -1 && previousMatchIndex != matchIndex) {
            val prevCharIndex = matchIndices[previousMatchIndex]
            spannable.setSpan(
                BackgroundColorSpan(ContextCompat.getColor(this, R.color.highlight_color)),
                prevCharIndex,
                prevCharIndex + searchQueryLength,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        spannable.setSpan(
            BackgroundColorSpan(ContextCompat.getColor(this, R.color.current_match_highlight)),
            charIndex,
            charIndex + searchQueryLength,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        // 3. Update the TextView with the new spannable text.
        textViewData.text = spannable

        // 4. Update the previous match index to the current one for the next navigation.
        previousMatchIndex = matchIndex

        scrollView.post {
            val layout = textViewData.layout
            if (layout != null) {
                val line = layout.getLineForOffset(charIndex)
                // Calculate y position to scroll to, with some offset to not be at the very top
                val y = layout.getLineTop(line) - (scrollView.height / 3)
                scrollView.smoothScrollTo(0, y.coerceAtLeast(0))
            }
        }
    }
    
    private fun setupFontSettingsButton() {
        fontSettingsButton.setOnClickListener {
            showFontSettingsDialog()
        }
    }

    private fun showFontSettingsDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_font_settings)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.let { WindowUtils.setStatusBarIconColor(it) }
        
        val slider = dialog.findViewById<Slider>(R.id.font_size_slider)
        slider.value = textViewData.textSize / resources.displayMetrics.scaledDensity

        slider.addOnChangeListener { _, value, _ ->
            textViewData.textSize = value
        }

        // Theme selection
        val themeLight = dialog.findViewById<View>(R.id.theme_system)
        val themeSepia = dialog.findViewById<View>(R.id.theme_sepia)
        val themeMidnight = dialog.findViewById<View>(R.id.theme_midnight)
        
        // Update background to light theme selector
        themeLight.setBackgroundResource(R.drawable.theme_selector_light)

        val prefs = getSharedPreferences(READER_THEME_PREFS, Context.MODE_PRIVATE)
        val currentTheme = prefs.getString(KEY_READER_THEME, THEME_SEPIA) ?: THEME_SEPIA
        
        // Set initial selected state
        themeLight.isSelected = currentTheme == THEME_LIGHT
        themeSepia.isSelected = currentTheme == THEME_SEPIA
        themeMidnight.isSelected = currentTheme == THEME_MIDNIGHT
        
        themeLight.setOnClickListener {
            themeLight.isSelected = true
            themeSepia.isSelected = false
            themeMidnight.isSelected = false
            with(prefs.edit()) {
                putString(KEY_READER_THEME, THEME_LIGHT)
                apply()
            }
            applyReaderTheme()
        }
        
        themeSepia.setOnClickListener {
            themeLight.isSelected = false
            themeSepia.isSelected = true
            themeMidnight.isSelected = false
            with(prefs.edit()) {
                putString(KEY_READER_THEME, THEME_SEPIA)
                apply()
            }
            applyReaderTheme()
        }
        
        themeMidnight.setOnClickListener {
            themeLight.isSelected = false
            themeSepia.isSelected = false
            themeMidnight.isSelected = true
            with(prefs.edit()) {
                putString(KEY_READER_THEME, THEME_MIDNIGHT)
                apply()
            }
            applyReaderTheme()
        }

        dialog.setOnDismissListener {
            saveFontSize(textViewData.textSize / resources.displayMetrics.scaledDensity)
        }

        dialog.show()
    }

    private fun saveFontSize(size: Float) {
        val sharedPreferences = getSharedPreferences(FONT_PREFS, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putFloat(KEY_FONT_SIZE, size)
            apply()
        }
    }

    private fun loadAndApplyFontSize() {
        val sharedPreferences = getSharedPreferences(FONT_PREFS, Context.MODE_PRIVATE)
        val fontSize = sharedPreferences.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        textViewData.textSize = fontSize
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
        
        when (themeStr) {
            THEME_SEPIA -> {
                scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.reader_sepia_bg))
                textViewData.setTextColor(ContextCompat.getColor(this, R.color.reader_sepia_text))
            }
            THEME_MIDNIGHT -> {
                scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.reader_midnight_bg))
                textViewData.setTextColor(ContextCompat.getColor(this, R.color.reader_midnight_text))
            }
            else -> {
                // THEME_LIGHT/DEFAULT - use default app theme colors (Light Mode)
                val typedValue = TypedValue()
                
                // Use colorSurface for background instead of colorBackground for better compatibility
                if (theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)) {
                    scrollView.setBackgroundColor(typedValue.data)
                } else {
                    // Fallback to a safe light default
                    scrollView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.background_light))
                }
                
                if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                    textViewData.setTextColor(typedValue.data)
                } else {
                    // Fallback to ensure text is visible (Black on white)
                    textViewData.setTextColor(ContextCompat.getColor(this, android.R.color.black))
                }
            }
        }
    }

    private fun enterFullScreen() {
        if (isFullScreen) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
        supportActionBar?.hide()
        isFullScreen = true
        // After entering fullscreen, we want the ScrollView to not have extra top padding from insets.
        // Requesting insets again will trigger the listener, which now adjusts padding based on isFullScreen.
        ViewCompat.requestApplyInsets(scrollView)
        // We don't need the insets listener anymore, but we can manually adjust padding
        // to avoid the content jumping under the status bar area.
        val insets = ViewCompat.getRootWindowInsets(window.decorView)
        val systemBarInsets = insets?.getInsets(WindowInsetsCompat.Type.systemBars())
        scrollView.setPadding(systemBarInsets?.left ?: 0, 0, systemBarInsets?.right ?: 0, 0)
    }

    private fun processCustomImageTags(text: String): String {
        // Pattern: {{image:filename.ext | caption}}
        // Captures: 1=filename with ext, 2=caption
        val regex = Regex("\\{\\{image:(.*?) \\| (.*?)\\}\\}")
        return regex.replace(text) { matchResult ->
            val filenameWithExt = matchResult.groupValues[1].trim()
            val caption = matchResult.groupValues[2].trim()
            // Remove extension to get resource name (e.g., "image.webp" -> "image")
            // Also sanitize: replace hyphens with underscores and ensure lowercase
            val resourceName = filenameWithExt.substringBeforeLast(".")
                .replace("-", "_")
                .lowercase()
            
            // Convert to Markdown image syntax using custom 'drawable' scheme
            // Structure: Image + Newline + Custom Styled Caption Tag (with inner newlines for alignment)
            "\n\n![${caption}](drawable://${resourceName})\n<book-caption>\n${caption}\n</book-caption>\n\n"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.detail_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                shareText()
                true
            }
            R.id.action_share_app -> {
                shareApp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun enableActionBarMenu() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun shareText() {
            val textToShare = textViewData.text.toString()

        if (textToShare.isNotEmpty()) {
            try {


                ShareCompat.IntentBuilder(this)
                    .setType("text/plain")
                    .setText(textToShare)
                    .setChooserTitle("Share via")
                    .`startChooser`()
            } catch (e: Exception) {
                Toast.makeText(this, "Sharing failed", Toast.LENGTH_SHORT).show()
                Log.e("ShareError", "Error sharing text", e)
            }
        } else {
            Toast.makeText(this, "No text to share", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareToApp(text: String, packageName: String) {
        try {
            val shareIntent = ShareCompat.IntentBuilder(this)
                .setType("text/plain")
                .setText(text)
                .intent
            shareIntent.setPackage(packageName)
            startActivity(shareIntent)
        } catch (e: Exception) {
            Log.e("ShareError", "Could not share to $packageName", e)
            Toast.makeText(this, "App not installed or unable to share.", Toast.LENGTH_SHORT).show()
            // Fallback to a general share chooser if the specific app fails
            ShareCompat.IntentBuilder(this).setType("text/plain").setText(text).startChooser()
        }
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

        if (shareIntent.resolveActivity(packageManager) != null) {
            startActivity(shareIntent)
        }
    }

    private fun onSwipeLeft() {
        // Swipe Left -> Go to Next Chapter
        stopTts() // Ensure TTS stops and resets on navigation
        navigateToNextChapter()
    }

    private fun onSwipeRight() {
        // Swipe Right -> Go to Previous Chapter
        stopTts() // Ensure TTS stops and resets on navigation
        navigateToPreviousChapter()
    }

    private fun navigateToNextChapter() {
        if (!::chapterSerial.isInitialized) return
        
        CoroutineScope(Dispatchers.Main).launch {
            val nextChapter = withContext(Dispatchers.IO) {
                bookRepository.getNextChapter(languageCode, bookId, chapterSerial)
            }
            
            if (nextChapter != null) {
                openChapter(nextChapter, true)
            } else {
                Toast.makeText(this@DetailActivity, "No more chapters", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToPreviousChapter() {
        if (!::chapterSerial.isInitialized) return

        CoroutineScope(Dispatchers.Main).launch {
            val prevChapter = withContext(Dispatchers.IO) {
                bookRepository.getPreviousChapter(languageCode, bookId, chapterSerial)
            }
            
            if (prevChapter != null) {
                openChapter(prevChapter, false)
            } else {
                Toast.makeText(this@DetailActivity, "This is the first chapter", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openChapter(chapter: Chapter, isNext: Boolean) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra("EXTRA_HEADING", chapter.heading)
            putExtra("EXTRA_DATE", chapter.date)
            putExtra("EXTRA_DATA", chapter.dataText)
            putExtra("EXTRA_WRITER", chapter.writer)
            putExtra("EXTRA_SERIAL", chapter.serial)
            putExtra("EXTRA_LANGUAGE_CODE", languageCode)
            putExtra("EXTRA_BOOK_ID", bookId)
        }
        
        startActivity(intent)
        
        if (isNext) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        
        finish() // Close current chapter to keep back stack clean
    }

    private fun startTts() {
        // Clean up markdown and custom tags
        val rawContent = intent.getStringExtra("EXTRA_DATA") ?: ""
        val cleanContent = rawContent
            .replace(Regex("\\{\\{image:.*?\\}\\}"), "") // Remove custom image tags
            .replace(Regex("<.*?>"), "") // Remove HTML tags
            .replace(Regex("[#*`_~]"), " ") // Replace markdown chars with space for natural pauses
            .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1") // Simplify markdown links to just text
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .trim()

        if (cleanContent.isEmpty()) return

        val result = textToSpeech?.speak(cleanContent.substring(currentTtsOffset), TextToSpeech.QUEUE_FLUSH, null, "ChapterContent")
        if (result == TextToSpeech.SUCCESS) {
            isTtsPlaying = true
            buttonTts.setImageResource(R.drawable.ic_pause)
            
            // Set listener to reset icon when done
            textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        isTtsPlaying = false
                        currentTtsOffset = 0 // Reset on completion
                        buttonTts.setImageResource(R.drawable.ic_speaker)
                    }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        isTtsPlaying = false
                        buttonTts.setImageResource(R.drawable.ic_speaker)
                    }
                }
                override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                    // Update offset as it reads so we can resume from the same spot
                    currentTtsOffset += start
                }
            })
        }
    }

    private fun pauseTts() {
        textToSpeech?.stop()
        isTtsPlaying = false
        buttonTts.setImageResource(R.drawable.ic_speaker)
    }

    private fun stopTts() {
        textToSpeech?.stop()
        isTtsPlaying = false
        currentTtsOffset = 0
        buttonTts.setImageResource(R.drawable.ic_speaker)
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }
}
