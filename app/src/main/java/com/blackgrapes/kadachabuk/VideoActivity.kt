package com.blackgrapes.kadachabuk

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.util.TypedValue
import android.view.View
import android.view.MenuItem
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import androidx.activity.viewModels
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
 
interface VideoPlaybackListener {
    fun onVideoPlaybackChanged(videoTitle: String?)
}
interface OnFavoriteChangedListener {
    fun onFavoriteChanged()
}
class VideoActivity : AppCompatActivity(), VideoPlaybackListener, OnFavoriteChangedListener {

    private lateinit var progressBar: ProgressBar
    private lateinit var toolbar: MaterialToolbar
    private lateinit var errorGroup: Group
    private lateinit var errorMessageTextView: TextView
    private lateinit var retryButton: Button
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    private var originalTitle: String = "Video Links"
    private var currentViewPagerPosition = 0
    private var allVideos: List<Video> = emptyList()

    private val bookViewModel: BookViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        // Allow content to draw behind the system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Use the centralized utility to set the status bar icon color.
        WindowUtils.setStatusBarIconColor(window)
        
        toolbar = findViewById(R.id.toolbar) // Initialize once
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the top inset as padding
            view.setPadding(view.paddingLeft, insets.top, view.paddingRight, view.paddingBottom)

            // Increase the toolbar's height to accommodate the new padding
            val typedValue = TypedValue()
            theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)
            val actionBarSize = TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
            view.layoutParams.height = actionBarSize + insets.top

            // Consume the insets
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.title = "Video Links"

        progressBar = findViewById(R.id.progressBar)
        errorGroup = findViewById(R.id.error_group)
        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.view_pager)
        errorMessageTextView = findViewById(R.id.error_message)
        retryButton = findViewById(R.id.retry_button)

        retryButton.setOnClickListener {
            bookViewModel.fetchVideos(forceRefresh = true)
        }

        observeViewModel()
        
        // This will hit the cache instantly if pre-loaded in MainActivity
        bookViewModel.fetchVideos(forceRefresh = false)
    }

    private fun observeViewModel() {
        bookViewModel.videos.observe(this) { result ->
            result.onSuccess { videoList ->
                allVideos = videoList
                
                progressBar.visibility = View.GONE
                if (videoList.isEmpty()) {
                    errorGroup.visibility = View.VISIBLE
                    errorMessageTextView.text = "No videos found in the sheet."
                    tabLayout.visibility = View.GONE
                    viewPager.visibility = View.GONE
                    return@onSuccess
                }

                errorGroup.visibility = View.GONE
                tabLayout.visibility = View.VISIBLE
                viewPager.visibility = View.VISIBLE

                val favoritePrefs = getSharedPreferences("VideoFavorites", Context.MODE_PRIVATE)
                val favoriteVideos = videoList.filter { favoritePrefs.getBoolean(it.getUniqueId(), false) }

                val videoMap = videoList.groupBy { it.category }.toMutableMap()
                videoMap["Favorites"] = favoriteVideos

                val categories = listOf("Favorites", "Speech", "Mahanam", "Vedic Song")
                val fragments = categories.mapIndexed { index, category ->
                    VideoListFragment.newInstance(videoMap[category] ?: emptyList(), index == 0)
                }

                viewPager.adapter = VideoPagerAdapter(this@VideoActivity, fragments)

                TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                    val category = categories[position]
                    val count = videoMap[category]?.size ?: 0

                    if (position == 0) { // This is the "Favorites" tab
                        tab.setIcon(R.drawable.ic_favorite_filled)
                        tab.contentDescription = "Favorites ($count)"

                        val badge = tab.orCreateBadge
                        badge.number = count
                        badge.isVisible = count > 0
                    } else {
                        tab.text = "$category ($count)"
                    }
                }.attach()

                viewPager.setCurrentItem(currentViewPagerPosition, false)
                originalTitle = "Video Links (${videoList.size})"
                toolbar.title = originalTitle
            }.onFailure { error ->
                progressBar.visibility = View.GONE
                errorGroup.visibility = View.VISIBLE
                errorMessageTextView.text = "Error: ${error.message ?: "Failed to load videos"}"
                tabLayout.visibility = View.GONE
                viewPager.visibility = View.GONE
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.video_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            R.id.action_refresh_videos -> {
                bookViewModel.fetchVideos(forceRefresh = true)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }


    override fun onVideoPlaybackChanged(videoTitle: String?) {
        if (videoTitle != null) {
            toolbar.title = videoTitle
        } else {
            toolbar.title = originalTitle
        }
    }

    override fun onFavoriteChanged() {
        // This is a more efficient update that doesn't cause the list to scroll.
        // It avoids calling fetchVideoData() and recreating all the fragments.

        val favoritePrefs = getSharedPreferences("VideoFavorites", Context.MODE_PRIVATE)
        val favoriteVideos = allVideos.filter { favoritePrefs.getBoolean(it.getUniqueId(), false) }

        // 1. Update the list inside the Favorites fragment
        val pagerAdapter = viewPager.adapter as? VideoPagerAdapter
        val favoritesFragment = pagerAdapter?.getFragment(0) as? VideoListFragment
        favoritesFragment?.updateVideos(favoriteVideos)

        // 2. Update the badge on the Favorites tab
        val favoritesTab = tabLayout.getTabAt(0)
        if (favoritesTab != null) {
            val count = favoriteVideos.size
            favoritesTab.contentDescription = "Favorites ($count)"
            val badge = favoritesTab.orCreateBadge
            badge.number = count
            badge.isVisible = count > 0
        }
    }
}