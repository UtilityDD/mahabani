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
import java.util.Locale
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import androidx.core.content.ContextCompat // <-- IMPORT THIS
import androidx.core.content.res.ResourcesCompat
import androidx.core.app.ShareCompat
import android.widget.Toast
import android.util.Log
import java.text.BreakIterator
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.FileOutputStream

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
import com.google.android.material.floatingactionbutton.FloatingActionButton // Add this
import androidx.core.app.ActivityCompat // Add this
import android.content.pm.PackageManager // Add this
import android.Manifest // Add this
import androidx.annotation.OptIn
import androidx.cardview.widget.CardView // Add this
import androidx.media3.common.util.UnstableApi
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import kotlin.random.Random // <-- IMPORT THIS
import java.util.concurrent.TimeUnit

private const val FONT_PREFS = "FontPrefs"
private const val KEY_FONT_SIZE = "fontSize"
private const val DEFAULT_FONT_SIZE = 18f
private const val KEY_LINE_SPACING = "lineSpacing"
private const val DEFAULT_LINE_SPACING = 1.8f
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
    private lateinit var buttonDownload: ImageButton
    private lateinit var chapterSerial: String
    private lateinit var languageCode: String
    private lateinit var scrollToTopButton: ImageButton
    private lateinit var searchNavigationLayout: LinearLayout
    private lateinit var previousMatchButton: ImageButton
    private lateinit var nextMatchButton: ImageButton
    private lateinit var matchCountTextView: TextView
    private lateinit var bookId: String
    private lateinit var markwon: Markwon
    private lateinit var breathingOrb: BreathingOrbView // Custom visualizer
    private var exoPlayer: ExoPlayer? = null
    private var audioLink: String? = null
    private var isHumanAudioPlaying = false
    private var isAudioAvailable = false
    private var isAudioDownloaded = false

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

    // --- Compact Audio Player Variables ---
    private var compactPlayer: View? = null
    private var compactVisualizer: AudioVisualizerView? = null
    private var btnCompactPlayPause: FloatingActionButton? = null
    private var btnCompactStop: ImageButton? = null
    private val RECORD_AUDIO_PERMISSION_CODE = 101

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
        audioLink = intent.getStringExtra("EXTRA_AUDIO_LINK")

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
            buttonDownload.updatePadding(top = systemBars.top)
            backButton.updatePadding(top = systemBars.top)
            insets
        }

        scrollView = findViewById(R.id.scrollView)
        imageViewHeader = findViewById(R.id.imageViewHeader)
        
        buttonTts = findViewById(R.id.button_tts)
        buttonDownload = findViewById(R.id.button_download)
        breathingOrb = findViewById(R.id.breathingOrb) // Initialize orb

        checkAudioAvailability()

        buttonDownload.setOnClickListener {
            if (isAudioAvailable && !isAudioDownloaded) {
                downloadAudio()
            } else if (isAudioDownloaded) {
                Toast.makeText(this, "Audio already downloaded", Toast.LENGTH_SHORT).show()
            }
        }

        buttonTts.setOnClickListener {
            if (!isAudioAvailable) {
                Toast.makeText(this, "Audio not available for this chapter", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Handle audio playback
            if (isHumanAudioPlaying) {
                pauseHumanAudio()
            } else {
                checkPermissionAndShowOverlay()
            }
        }
        
        // Initialize Gesture Detector for Swipe Navigation
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 150 // Increased to prevent accidental swipes while clicking
            private val SWIPE_VELOCITY_THRESHOLD = 150

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

        // 1. Create a Markwon instance with standardized Image handling
        markwon = Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(io.noties.markwon.SoftBreakAddsNewLinePlugin.create())
            .usePlugin(io.noties.markwon.html.HtmlPlugin.create { plugin ->
                plugin.addHandler(object : io.noties.markwon.html.TagHandler() {
                    override fun supportedTags() = setOf("book-image")
                    override fun handle(visitor: io.noties.markwon.MarkwonVisitor, renderer: io.noties.markwon.html.MarkwonHtmlRenderer, tag: io.noties.markwon.html.HtmlTag) {
                        visitor.builder().setSpan(
                            object : android.text.style.LineHeightSpan {
                                override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fm: android.graphics.Paint.FontMetricsInt) {
                                    // Counteract the lineSpacingMultiplier to prevent huge gaps above/below images
                                    val multiplier = textViewData.lineSpacingMultiplier
                                    if (multiplier > 1.0f) {
                                        val originalHeight = fm.descent - fm.ascent
                                        // We want the final rendered height to be roughly 'originalHeight'.
                                        // Since TextView renders as (height * multiplier), we must shrink the input height.
                                        val targetHeight = (originalHeight / multiplier).toInt()
                                        val center = (fm.descent + fm.ascent) / 2
                                        val halfHeight = targetHeight / 2
                                        
                                        fm.ascent = center - halfHeight
                                        fm.descent = center + halfHeight
                                        fm.top = fm.ascent
                                        fm.bottom = fm.descent
                                    }
                                }
                            },
                            tag.start(),
                            tag.end(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                })

                plugin.addHandler(object : io.noties.markwon.html.TagHandler() {
                    override fun supportedTags() = setOf("book-center")
                    override fun handle(visitor: io.noties.markwon.MarkwonVisitor, renderer: io.noties.markwon.html.MarkwonHtmlRenderer, tag: io.noties.markwon.html.HtmlTag) {
                        visitor.builder().setSpan(
                            android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),
                            tag.start(),
                            tag.end(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                })

                plugin.addHandler(object : io.noties.markwon.html.TagHandler() {
                    override fun handle(visitor: io.noties.markwon.MarkwonVisitor, renderer: io.noties.markwon.html.MarkwonHtmlRenderer, tag: io.noties.markwon.html.HtmlTag) {
                         val spans = arrayOf(
                             android.text.style.AlignmentSpan.Standard(android.text.Layout.Alignment.ALIGN_CENTER),
                             android.text.style.RelativeSizeSpan(0.8f), // Slightly larger for better readability
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

                plugin.addHandler(object : io.noties.markwon.html.TagHandler() {
                    override fun handle(visitor: io.noties.markwon.MarkwonVisitor, renderer: io.noties.markwon.html.MarkwonHtmlRenderer, tag: io.noties.markwon.html.HtmlTag) {
                        val galada = try {
                            ResourcesCompat.getFont(this@DetailActivity, R.font.galada)
                        } catch (e: Exception) {
                            null
                        }

                        val typedValue = TypedValue()
                        theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)
                        val color = typedValue.data

                        visitor.builder().setSpan(object : android.text.style.ReplacementSpan() {
                            private var mWidth = 0

                            override fun getSize(paint: android.graphics.Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int {
                                val originalSize = paint.textSize
                                val originalFm = paint.fontMetricsInt // Capture original line metrics
                                
                                paint.textSize = originalSize * 2.0f // 2x Drop Cap
                                if (galada != null) paint.typeface = galada
                                mWidth = paint.measureText(text, start, end).toInt()
                                
                                if (fm != null) {
                                    // Full neutralization: Force the drop cap to use the original line's metrics.
                                    // This prevents the line height from expanding, keeping line 1 and 2 spacing perfect.
                                    fm.ascent = originalFm.ascent
                                    fm.top = originalFm.top
                                    fm.descent = originalFm.descent
                                    fm.bottom = originalFm.bottom
                                }
                                paint.textSize = originalSize
                                return mWidth
                            }

                            override fun draw(canvas: android.graphics.Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: android.graphics.Paint) {
                                val originalSize = paint.textSize
                                val originalColor = paint.color
                                val originalTypeface = paint.typeface
                                
                                paint.textSize = originalSize * 2.0f
                                paint.color = color
                                if (galada != null) paint.typeface = galada
                                
                                // Draw text at the baseline (y). Since we constrained descent in getSize,
                                // the large font will naturally extend upwards due to large negative ascent.
                                canvas.drawText(text!!, start, end, x, y.toFloat(), paint)
                                
                                paint.textSize = originalSize
                                paint.color = originalColor
                                paint.typeface = originalTypeface
                            }
                        }, tag.start(), tag.end(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }

                    override fun supportedTags(): Collection<String> {
                        return setOf("drop-cap")
                    }
                })
            })
            // Correctly initialize ImagesPlugin
            .usePlugin(io.noties.markwon.image.ImagesPlugin.create { plugin ->
                plugin.addSchemeHandler(object : io.noties.markwon.image.SchemeHandler() {
                    override fun handle(raw: String, uri: Uri): io.noties.markwon.image.ImageItem {
                        val transparentDrawable = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
                        return try {
                            // Standardize on using path segments for resource names (res:///resource_name)
                            val resourceName = uri.lastPathSegment ?: uri.authority ?: ""
                            if (resourceName.isNotEmpty()) {
                                // Sanitization: ensure lowercase and underscores (safety check)
                                val sanitizedName = resourceName.lowercase().replace("-", "_")
                                val resId = resources.getIdentifier(sanitizedName, "drawable", packageName)
                                
                                if (resId != 0) {
                                    val drawable = ContextCompat.getDrawable(this@DetailActivity, resId)
                                    io.noties.markwon.image.ImageItem.withResult(drawable ?: transparentDrawable)
                                } else {
                                    Log.e("MarkwonImage", "Asset not found: $sanitizedName")
                                    io.noties.markwon.image.ImageItem.withResult(transparentDrawable)
                                }
                            } else {
                                io.noties.markwon.image.ImageItem.withResult(transparentDrawable)
                            }
                        } catch (e: Exception) {
                            Log.e("MarkwonImage", "Error loading asset: ${e.message}")
                            io.noties.markwon.image.ImageItem.withResult(transparentDrawable)
                        }
                    }

                    override fun supportedSchemes(): Collection<String> {
                        return setOf("res")
                    }
                })
            })
            .build()

        // 2. Set the processed markdown content to the TextView
        // Using setMarkdown ensures both images and custom HTML tags (drop-cap) render correctly
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
        initializePlayer()
        highlightSearchTerm()
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
        pauseHumanAudio()
        collapseAudioPlayer() // Collapse player when leaving
        saveReadingTime()
        saveScrollPosition()
        saveLastReadChapter()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        compactVisualizer?.release()
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

        scrollToOffset(charIndex, verticalBias = 0.33f)
    }

    private fun scrollToOffset(offset: Int, verticalBias: Float = 0.33f) {
        textViewData.post {
            val layout = textViewData.layout
            if (layout != null) {
                val line = layout.getLineForOffset(offset)
                // Calculate y position to scroll to, with some offset to not be at the very top
                val y = layout.getLineTop(line) - (scrollView.height * verticalBias).toInt()
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

        val lineSpacingSlider = dialog.findViewById<Slider>(R.id.line_spacing_slider)
        val lineSpacingPrefs = getSharedPreferences(FONT_PREFS, Context.MODE_PRIVATE)
        lineSpacingSlider.value = lineSpacingPrefs.getFloat(KEY_LINE_SPACING, DEFAULT_LINE_SPACING)

        lineSpacingSlider.addOnChangeListener { _, value, _ ->
            textViewData.setLineSpacing(0f, value)
        }

        dialog.setOnDismissListener {
            val finalFontSize = textViewData.textSize / resources.displayMetrics.scaledDensity
            val finalLineSpacing = lineSpacingSlider.value
            saveReaderSettings(finalFontSize, finalLineSpacing)
        }

        dialog.show()
    }

    private fun saveReaderSettings(fontSize: Float, lineSpacing: Float) {
        val sharedPreferences = getSharedPreferences(FONT_PREFS, Context.MODE_PRIVATE)
        with(sharedPreferences.edit()) {
            putFloat(KEY_FONT_SIZE, fontSize)
            putFloat(KEY_LINE_SPACING, lineSpacing)
            apply()
        }
    }

    private fun loadAndApplyFontSize() {
        val sharedPreferences = getSharedPreferences(FONT_PREFS, Context.MODE_PRIVATE)
        val fontSize = sharedPreferences.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)
        val lineSpacing = sharedPreferences.getFloat(KEY_LINE_SPACING, DEFAULT_LINE_SPACING)
        textViewData.textSize = fontSize
        textViewData.setLineSpacing(0f, lineSpacing)

        // Programmatically enforce Tiro Bangla font
        try {
            val typeface = ResourcesCompat.getFont(this, R.font.tiro_bangla_regular)
            textViewData.typeface = typeface
        } catch (e: Exception) {
            Log.e("DetailActivity", "Error setting font: ${e.message}")
        }
    }


    private fun applyReaderTheme() {
        val bindingShadow: View? = findViewById(R.id.binding_shadow)
        val textViewHeading: TextView = findViewById(R.id.textViewHeading)
        val textViewDate: TextView = findViewById(R.id.textViewDate)
        val textViewWriter: TextView = findViewById(R.id.textViewWriter)
        
        val galada = try {
            ResourcesCompat.getFont(this, R.font.galada)
        } catch (e: Exception) {
            null
        }
        
        // Ensure Galada is applied to heading
        if (galada != null) {
            textViewHeading.typeface = galada
        }
        
        // Metadata styling
        textViewDate.setTypeface(textViewDate.typeface, android.graphics.Typeface.ITALIC)
        textViewWriter.setTypeface(textViewWriter.typeface, android.graphics.Typeface.ITALIC)

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
                scrollView.background = ContextCompat.getDrawable(this, R.drawable.paper_texture_background_light)
                textViewData.setTextColor(ContextCompat.getColor(this, R.color.reader_sepia_text))
                textViewHeading.setTextColor(ContextCompat.getColor(this, R.color.reader_sepia_text))
                val metaColor = ContextCompat.getColor(this, R.color.reader_sepia_text)
                textViewDate.setTextColor(metaColor)
                textViewWriter.setTextColor(metaColor)
                bindingShadow?.visibility = View.VISIBLE
                bindingShadow?.visibility = View.VISIBLE
                bindingShadow?.alpha = 1.0f
                breathingOrb.setBaseColor(android.graphics.Color.parseColor("#FFD700")) // Gold for Sepia
            }
            THEME_MIDNIGHT -> {
                scrollView.setBackgroundColor(ContextCompat.getColor(this, R.color.reader_midnight_bg))
                textViewData.setTextColor(ContextCompat.getColor(this, R.color.reader_midnight_text))
                textViewHeading.setTextColor(ContextCompat.getColor(this, R.color.reader_midnight_text))
                val metaColor = ContextCompat.getColor(this, R.color.reader_midnight_text)
                textViewDate.setTextColor(metaColor)
                textViewWriter.setTextColor(metaColor)
                bindingShadow?.visibility = View.GONE
                breathingOrb.setBaseColor(android.graphics.Color.parseColor("#00FFFF")) // Cyan for Midnight
            }
            else -> {
                // THEME_LIGHT/DEFAULT
                scrollView.setBackgroundColor(android.graphics.Color.WHITE)
                bindingShadow?.visibility = View.VISIBLE
                bindingShadow?.alpha = 0.5f
                
                val typedValue = TypedValue()
                val textColor = if (theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, typedValue, true)) {
                    typedValue.data
                } else {
                    android.graphics.Color.BLACK
                }
                
                textViewHeading.setTextColor(textColor)
                
                val onSurfaceColor = if (theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)) {
                    typedValue.data
                } else {
                    android.graphics.Color.BLACK
                }
                
                textViewData.setTextColor(onSurfaceColor)
                
                // Muted metadata for light theme
                textViewDate.setTextColor(onSurfaceColor)
                textViewDate.alpha = 0.6f
                textViewWriter.setTextColor(onSurfaceColor)
                textViewWriter.alpha = 0.6f
                breathingOrb.setBaseColor(android.graphics.Color.parseColor("#9C27B0")) // Purple for Light
            }
        }

        // Re-set the markwon to ensure all spans (especially drop-caps and images) 
        // correctly pick up any theme-dependent color changes.
        val rawData = intent.getStringExtra("EXTRA_DATA")
        if (::markwon.isInitialized && rawData != null) {
            val processed = processCustomImageTags(rawData)
            markwon.setMarkdown(textViewData, processed)
        }
    }

    private fun processCustomImageTags(content: String): String {
        // SMART DROP CAP DETECTION
        // We must find the first "real" text character, skipping over:
        // 1. HTML tags: <...>
        // 2. Custom handlebars: {{...}}
        // 3. HTML entities: &...;
        
        var firstValidCharIndex = -1
        var i = 0
        val len = content.length
        
        while (i < len) {
            val c = content[i]
            
            // Check for start of skipped blocks
            if (c == '<') {
                // Skip until '>'
                val endTag = content.indexOf('>', i)
                if (endTag != -1) {
                    i = endTag + 1
                    continue
                }
            }
            
            if (c == '{' && i + 1 < len && content[i+1] == '{') {
                // Skip until '}}'
                val endCurly = content.indexOf("}}", i)
                if (endCurly != -1) {
                    i = endCurly + 2
                    continue
                }
            }
            
            if (c == '&') {
                // Skip until ';' (assuming generic entity)
                val endEntity = content.indexOf(';', i)
                if (endEntity != -1 && endEntity - i < 10) { // Safety limit for entity length
                    i = endEntity + 1
                    continue
                }
            }
            
            // If we are here, we are outside known tags. 
            // Check if it's a valid Drop Cap candidate.
            if (c.isLetter() || c.isDigit()) {
                firstValidCharIndex = i
                break
            }
            i++
        }

        var textWithDropCap = content
        if (firstValidCharIndex != -1) {
            val iterator = BreakIterator.getCharacterInstance()
            iterator.setText(content)
            val clusterEnd = iterator.following(firstValidCharIndex)
            val end = if (clusterEnd != BreakIterator.DONE) clusterEnd else firstValidCharIndex + 1
            
            val dropCapChar = content.substring(firstValidCharIndex, end)
            textWithDropCap = content.substring(0, firstValidCharIndex) + 
                             "<drop-cap>$dropCapChar</drop-cap>" + 
                             content.substring(end)
        }

    // Robust pattern to catch: {{image:name.jpg|caption:text}} OR {{image:name.jpg|text}}
    // Consumes surrounding whitespace AND <br> tags.
    // Wraps image in <book-image> tag to handle line spacing compensation.
    val regexWithCaption = Regex("(?:[\\s\\r\\n]|<br\\s*/?>)*\\{\\{image:(.*?)(?:\\|caption:|\\s*\\|\\s*)(.*?)\\}\\}", RegexOption.DOT_MATCHES_ALL)
    var processed = textWithDropCap.replace(regexWithCaption) { match ->
        val rawImageName = match.groupValues[1].trim()
        val caption = match.groupValues[2].trim()
        val resourceName = rawImageName.substringBeforeLast(".").replace("-", "_").lowercase()
        // Wraps in <book-image> and uses \n\n to isolate as a block
        "\n\n<book-image>![image](res:///$resourceName)</book-image>\n\n<book-caption>$caption</book-caption>\n\n"
    }

    // Ensure <center> tags are surrounded by newlines for proper block parsing
    // Handle both unescaped and escaped variations and normalize to custom <book-center> tag
    processed = processed.replace("<center>", "\n\n<book-center>\n")
                        .replace("&lt;center&gt;", "\n\n<book-center>\n")
                        .replace("</center>", "\n</book-center>\n\n")
                        .replace("&lt;/center&gt;", "\n</book-center>\n\n")


    val regexNoCaption = Regex("(?:[\\s\\r\\n]|<br\\s*/?>)*\\{\\{image:(.*?)\\}\\}")
    processed = processed.replace(regexNoCaption) { match ->
        val raw = match.value
        if (raw.contains("|")) return@replace raw
        val rawImageName = match.groupValues[1].trim()
        val resourceName = rawImageName.substringBeforeLast(".").replace("-", "_").lowercase()
        "\n\n<book-image>![image](res:///$resourceName)</book-image>\n\n"
    }

        return processed
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
        navigateToNextChapter()
    }

    private fun onSwipeRight() {
        // Swipe Right -> Go to Previous Chapter
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
            putExtra("EXTRA_AUDIO_LINK", chapter.audioLink)
        }
        
        startActivity(intent)
        
        if (isNext) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        } else {
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
        
        finish() // Close current chapter to keep back stack clean
    }














    private fun getAutoGuessedAudioUrl(): String {
        // Normalize serial: remove leading zeros (e.g., "01" -> "1")
        val cleanSerial = chapterSerial.toIntOrNull()?.toString() ?: chapterSerial
        // Canonical Raw format is safest for ExoPlayer
        return "https://raw.githubusercontent.com/UtilityDD/mahabani_audio/main/$bookId/$languageCode/$cleanSerial.wav"
    }

    private suspend fun verifyUrlExists(urlString: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val responseCode = connection.responseCode
                connection.disconnect()
                responseCode == HttpURLConnection.HTTP_OK
            } catch (e: Exception) {
                Log.e("AudioVerify", "Error verifying URL: $urlString", e)
                false
            }
        }
    }

    private fun checkAudioAvailability() {
        // Initial state: disabled/loading look
        isAudioAvailable = false
        isAudioDownloaded = false
        buttonTts.alpha = 0.4f
        buttonTts.isEnabled = true // Keep enabled to show toast on click
        
        buttonDownload.visibility = View.GONE
        buttonDownload.alpha = 1.0f
        buttonDownload.setImageResource(R.drawable.ic_download)

        // Use auto-guessed URL if audioLink is null/blank
        val urlToVerify = if (!audioLink.isNullOrBlank()) audioLink!! else getAutoGuessedAudioUrl()
        
        // 1. Check local storage first
        val localFile = getLocalAudioFile()
        if (localFile.exists()) {
            isAudioAvailable = true
            isAudioDownloaded = true
            runOnUiThread {
                buttonTts.alpha = 1.0f
                buttonDownload.visibility = View.VISIBLE
                buttonDownload.setImageResource(R.drawable.ic_checkmark)
                Log.d("AudioVerify", "Local audio found: ${localFile.absolutePath}")
            }
            return
        }

        // 2. If not local, check remote reachability
        CoroutineScope(Dispatchers.Main).launch {
            val exists = verifyUrlExists(urlToVerify)
            isAudioAvailable = exists
            if (exists) {
                buttonTts.alpha = 1.0f
                buttonDownload.visibility = View.VISIBLE
                Log.d("AudioVerify", "Remote audio available: $urlToVerify")
            } else {
                buttonTts.alpha = 0.4f
                buttonDownload.visibility = View.GONE
                Log.d("AudioVerify", "Audio NOT available: $urlToVerify")
            }
        }
    }

    private fun getLocalAudioFile(): File {
        val directory = File(filesDir, "audio_downloads")
        if (!directory.exists()) directory.mkdirs()
        
        // Use a unique name based on bookId, languageCode, and serial
        val fileName = "${bookId}_${languageCode}_${chapterSerial}.wav"
        return File(directory, fileName)
    }

    private fun downloadAudio() {
        val urlToDownload = if (!audioLink.isNullOrBlank()) audioLink!! else getAutoGuessedAudioUrl()
        val localFile = getLocalAudioFile()

        Toast.makeText(this, "Starting download...", Toast.LENGTH_SHORT).show()
        
        // Visual feedback: partial alpha during download
        buttonDownload.animate().alpha(0.5f).setDuration(500).start()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(urlToDownload)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { input ->
                        FileOutputStream(localFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        isAudioDownloaded = true
                        buttonDownload.setImageResource(R.drawable.ic_checkmark)
                        buttonDownload.animate().alpha(1.0f).setDuration(300).start()
                        Toast.makeText(this@DetailActivity, "Download complete", Toast.LENGTH_SHORT).show()
                        Log.d("AudioDownload", "Successfully downloaded to: ${localFile.absolutePath}")
                    }
                } else {
                    throw Exception("Server returned code ${connection.responseCode}")
                }
            } catch (e: Exception) {
                Log.e("AudioDownload", "Error downloading audio", e)
                withContext(Dispatchers.Main) {
                    buttonDownload.animate().alpha(1.0f).setDuration(300).start()
                    Toast.makeText(this@DetailActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    if (localFile.exists()) localFile.delete()
                }
            }
        }
    }



    private fun initializePlayer() {
        if (exoPlayer != null) return
        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                isHumanAudioPlaying = isPlaying
                runOnUiThread {
                    if (isPlaying) {
                        buttonTts.setImageResource(R.drawable.ic_pause)
                        breathingOrb.setTalking(true)
                        // Sync compact player button if visible
                        btnCompactPlayPause?.setImageResource(R.drawable.ic_pause)
                    } else {
                        buttonTts.setImageResource(R.drawable.ic_speaker)
                        breathingOrb.setTalking(false)
                        // Sync compact player button if visible
                        btnCompactPlayPause?.setImageResource(R.drawable.ic_play_arrow)
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    isHumanAudioPlaying = false
                    breathingOrb.setTalking(false)
                    buttonTts.setImageResource(R.drawable.ic_speaker)
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("AudioPlayer", "Error playing human audio: ${error.message}")
                Toast.makeText(this@DetailActivity, "Audio unavailable for this chapter.", Toast.LENGTH_SHORT).show()
                audioLink = null 
                isHumanAudioPlaying = false
                breathingOrb.setTalking(false)
                buttonTts.setImageResource(R.drawable.ic_speaker)
            }
        })
    }

    private fun playHumanAudio() {
        if (!isAudioAvailable) {
            Toast.makeText(this, "Audio not available for this chapter", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if we're resuming playback (player already has content)
        val isResuming = exoPlayer?.currentMediaItem != null && exoPlayer?.duration ?: 0 > 0
        
        if (isResuming) {
            // Resume from 2 seconds back for context
            val currentPos = exoPlayer?.currentPosition ?: 0L
            val seekPos = (currentPos - 2000L).coerceAtLeast(0L) // 2 seconds back, minimum 0
            exoPlayer?.seekTo(seekPos)
            Log.d("AudioPlayer", "Resuming from position: $seekPos ms (2s back from $currentPos)")
        } else {
            // Starting fresh
            val url = if (isAudioDownloaded) {
                Uri.fromFile(getLocalAudioFile()).toString()
            } else {
                audioLink ?: getAutoGuessedAudioUrl()
            }
            Log.d("AudioPlayer", "Starting new audio: $url")
            

            
            val mediaItem = MediaItem.fromUri(url)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.prepare()
        }
        
        exoPlayer?.play()
        
        // Start visualizer animation
        compactVisualizer?.startAnimation()
    }

    private fun pauseHumanAudio() {
        exoPlayer?.pause()
        // Stop visualizer animation when paused
        compactVisualizer?.stopAnimation()
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    // --- Audio Overlay & Visualizer Logic ---

    private fun checkPermissionAndShowOverlay() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
        } else {
            expandAudioPlayer()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                expandAudioPlayer()
            } else {
                Toast.makeText(this, "Permission denied. Visualizer will not work, but audio will play.", Toast.LENGTH_SHORT).show()
                // Still play audio without visualizer
                playHumanAudio() 
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun expandAudioPlayer() {
        // Get references to views
        if (compactPlayer == null) {
            compactPlayer = findViewById(R.id.compact_audio_player)
            compactVisualizer = compactPlayer?.findViewById(R.id.compact_visualizer_view)
            btnCompactPlayPause = compactPlayer?.findViewById(R.id.btn_compact_play_pause)
            btnCompactStop = compactPlayer?.findViewById(R.id.btn_compact_stop)
            
            // Set Listeners
            btnCompactPlayPause?.setOnClickListener {
                if (isHumanAudioPlaying) pauseHumanAudio() else playHumanAudio()
            }
            
            btnCompactStop?.setOnClickListener {
                pauseHumanAudio()
                exoPlayer?.seekTo(0)
                isHumanAudioPlaying = false
                btnCompactPlayPause?.setImageResource(R.drawable.ic_play_arrow)
                collapseAudioPlayer()
            }
        }

        // Ensure play is triggered if not already playing
        if (!isHumanAudioPlaying) {
            playHumanAudio()
        }
        
        // Link visualizer AFTER playback starts (with a small delay to ensure player is ready)
        compactPlayer?.postDelayed({
            exoPlayer?.let { player ->
                val sessionId = player.audioSessionId
                android.util.Log.d("AudioVisualizer", "Linking to audio session: $sessionId")
                if (sessionId != 0) {
                    compactVisualizer?.linkTo(sessionId)
                    compactVisualizer?.startAnimation()
                } else {
                    android.util.Log.e("AudioVisualizer", "Invalid audio session ID!")
                }
            }
        }, 300) // 300ms delay to ensure player is initialized

        // Update button state
        btnCompactPlayPause?.setImageResource(R.drawable.ic_pause)

        // Smoothly hide bookmark and font buttons, show the compact player
        bookmarkButton.animate().alpha(0f).setDuration(200).withEndAction {
            bookmarkButton.visibility = View.GONE
        }.start()
        
        fontSettingsButton.animate().alpha(0f).setDuration(200).withEndAction {
            fontSettingsButton.visibility = View.GONE
        }.start()
        
        buttonTts.animate().alpha(0f).setDuration(200).withEndAction {
            buttonTts.visibility = View.GONE
            buttonDownload.visibility = View.GONE
            // Now show the compact player
            compactPlayer?.visibility = View.VISIBLE
            compactPlayer?.alpha = 0f
            compactPlayer?.animate()?.alpha(1f)?.setDuration(300)?.start()
        }.start()

        buttonDownload.animate().alpha(0f).setDuration(200).start()
    }

    private fun collapseAudioPlayer() {
        // Smoothly hide compact player, restore original buttons
        compactPlayer?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            compactPlayer?.visibility = View.GONE
            compactVisualizer?.release()
            
            // Show original buttons
            buttonTts.visibility = View.VISIBLE
            buttonTts.alpha = 0f
            val targetAlpha = if (isAudioAvailable) 1.0f else 0.4f
            buttonTts.animate().alpha(targetAlpha).setDuration(300).start()
            
            if (isAudioAvailable) {
                buttonDownload.visibility = View.VISIBLE
                buttonDownload.alpha = 0f
                buttonDownload.animate().alpha(1.0f).setDuration(300).start()
            }
            
            bookmarkButton.visibility = View.VISIBLE
            bookmarkButton.alpha = 0f
            bookmarkButton.animate().alpha(1f).setDuration(300).start()
            
            fontSettingsButton.visibility = View.VISIBLE
            fontSettingsButton.alpha = 0f
            fontSettingsButton.animate().alpha(1f).setDuration(300).start()
        }?.start()
    }

    private fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, resources.displayMetrics).toInt()
    }
}
