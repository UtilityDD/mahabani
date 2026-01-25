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
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CoverActivity : AppCompatActivity() {

    private val bookViewModel: BookViewModel by viewModels()
    private lateinit var coverLayout: View
    private lateinit var titleTextView: TextView
    private lateinit var coverImageView: ImageView
    private lateinit var tapToOpenText: TextView

    private val animationScope = CoroutineScope(Dispatchers.Main)
    private var animationJob: Job? = null
    private var isAnimationSkippable = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySavedTheme() // Apply the theme first to prevent visual glitches
        setContentView(R.layout.activity_cover)

        // Allow the app to draw behind the system bars for a seamless UI
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Make the status bar transparent to show the background color underneath
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // Use the centralized utility to set the status bar icon color.
        WindowUtils.setStatusBarIconColor(window)

        coverLayout = findViewById(R.id.cover_layout)
        titleTextView = findViewById(R.id.title_text_view)
        coverImageView = findViewById(R.id.cover_image)
        tapToOpenText = findViewById(R.id.tap_to_open_text)

        // Start the animation sequence
        startAnimationSequence()

        // Pre-load chapters in the background
        preloadChapters()

        coverLayout.setOnClickListener {
            if (isAnimationSkippable) {
                // If the text animation is running, skip to the end of it.
                skipAnimation()
            }
        }
    }

    private fun startAnimationSequence() {
        animationJob = animationScope.launch {
            val languages = listOf(
                "কড়া চাবুক", // Bengali
                "कड़ा चाबुक", // Hindi
                "Kada Chabuk", // English
                "கடா சாபுக்", // Tamil
                "ಕಡಾ ಚಾಬುಕ್", // Kannada
                "কড়া চাবুক", // Assamese
                "કડા ચાબુક", // Gujarati
                "କଡ଼ା ଚାବୁକ", // Odia
                "ਕੜਾ ਚਾਬੁਕ", // Punjabi
                "കടാ ചാബുക്ക്", // Malayalam
                "కడా చాబుక్"  // Telugu
            )

            // Hide views initially
            titleTextView.alpha = 0f
            coverImageView.alpha = 0f
            coverImageView.scaleX = 0f
            coverImageView.scaleY = 0f
            tapToOpenText.alpha = 0f

            delay(50) // A brief initial delay

            // 1. Multilingual Text Sequence
            for (text in languages) {
                titleTextView.text = text
                fadeIn(titleTextView, 180)
                delay(250) // Time the text is fully visible
                // Start fading out, but don't wait for it to finish.
                fadeOut(titleTextView, 180, waitForCompletion = false)
                delay(100) // Wait for a portion of the fade-out before the next loop starts, creating a larger overlap.
            }

            // 2. PNG Reveal
            titleTextView.visibility = View.GONE
            coverImageView.visibility = View.VISIBLE
            coverImageView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(350)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()

            delay(200) // Wait a bit before showing the "tap to open" text

            // 3. "Touch to Open" text with pulse animation
            tapToOpenText.visibility = View.VISIBLE
            fadeIn(tapToOpenText, 300)
            val pulse = AnimationUtils.loadAnimation(this@CoverActivity, R.anim.pulse)
            tapToOpenText.startAnimation(pulse)

            // 4. Enable navigation
            coverLayout.setOnClickListener {
                navigateToMain()
            }
            // Once the main animation is done, it's no longer skippable.
            isAnimationSkippable = false
        }
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
        // Prevent multiple skips
        if (!isAnimationSkippable) return
        isAnimationSkippable = false

        // Cancel the ongoing text animation
        animationJob?.cancel()

        // Launch a new coroutine to perform the final part of the animation instantly
        animationJob = animationScope.launch {
            // 1. Clean up views from the text animation
            titleTextView.animate().cancel()
            titleTextView.visibility = View.GONE

            // 2. Immediately show the final state
            coverImageView.alpha = 1f
            coverImageView.scaleX = 1f
            coverImageView.scaleY = 1f
            coverImageView.visibility = View.VISIBLE

            tapToOpenText.alpha = 1f
            tapToOpenText.visibility = View.VISIBLE

            // 3. Start the pulse animation on the "tap to open" text
            val pulse = AnimationUtils.loadAnimation(this@CoverActivity, R.anim.pulse)
            tapToOpenText.startAnimation(pulse)

            // 4. Set the click listener to wait for the user's final tap to navigate
            coverLayout.setOnClickListener {
                navigateToMain()
            }
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
        finish()
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