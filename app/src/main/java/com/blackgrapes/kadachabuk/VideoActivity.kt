package com.blackgrapes.kadachabuk

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import androidx.appcompat.widget.SearchView
import android.util.TypedValue
import android.view.View
import android.view.MenuItem
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageView
import android.graphics.PorterDuff
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.core.graphics.ColorUtils
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
    private var currentViewPagerPosition = 2
    private var allVideos: List<Video> = emptyList()
    private var refreshItem: MenuItem? = null
    private var currentSearchQuery: String = ""
    private var categories = listOf("Favorites", "Speech", "Mahanam", "Vedic Song")
    private var categoryFragments: List<VideoListFragment> = emptyList()

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
                
                stopRefreshAnimation()
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

                // Use the class-level 'categories' property
                val fragments = categories.mapIndexed { index, category ->
                    VideoListFragment.newInstance(videoMap[category] ?: emptyList(), index == 0)
                }

                viewPager.adapter = VideoPagerAdapter(this@VideoActivity, fragments)
                categoryFragments = fragments

                TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                    updateTabAppearance(tab, position)
                }.attach()

                viewPager.setCurrentItem(currentViewPagerPosition, false)
                originalTitle = "Video Links (${videoList.size})"
                toolbar.title = originalTitle
            }.onFailure { error ->
                stopRefreshAnimation()
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
        refreshItem = menu?.findItem(R.id.action_refresh_videos)
        
        val searchItem = menu?.findItem(R.id.action_search)
        val searchView = searchItem?.actionView as? SearchView
        searchView?.apply {
            queryHint = "Search video title..."
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    currentSearchQuery = newText ?: ""
                    filterAndDisplayVideos()
                    return true
                }
            })
            styleSearchView(this)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            R.id.action_refresh_videos -> {
                startRefreshAnimation()
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

    private fun filterAndDisplayVideos() {
        val favoritePrefs = getSharedPreferences("VideoFavorites", Context.MODE_PRIVATE)
        val videoMap = allVideos.filter {
            it.remark.contains(currentSearchQuery, ignoreCase = true)
        }.groupBy { it.category }.toMutableMap()
        
        val favoriteVideos = allVideos.filter { 
            favoritePrefs.getBoolean(it.getUniqueId(), false) && 
            it.remark.contains(currentSearchQuery, ignoreCase = true)
        }
        videoMap["Favorites"] = favoriteVideos

        categoryFragments.forEachIndexed { index, fragment ->
            val category = categories[index]
            fragment.updateVideos(videoMap[category] ?: emptyList())
            
            val tab = tabLayout.getTabAt(index)
            if (tab != null) {
                updateTabAppearance(tab, index, videoMap[category]?.size ?: 0)
            }
        }
    }

    private fun updateTabAppearance(tab: TabLayout.Tab, position: Int, overrideCount: Int? = null) {
        val category = categories[position]
        val favoritePrefs = getSharedPreferences("VideoFavorites", Context.MODE_PRIVATE)
        
        val count = overrideCount ?: if (position == 0) {
            allVideos.filter { favoritePrefs.getBoolean(it.getUniqueId(), false) && it.remark.contains(currentSearchQuery, ignoreCase = true) }.size
        } else {
            allVideos.filter { it.category == category && it.remark.contains(currentSearchQuery, ignoreCase = true) }.size
        }

        if (position == 0) {
            tab.setIcon(R.drawable.ic_favorite_filled)
            tab.contentDescription = "Favorites ($count)"
            val badge = tab.orCreateBadge
            badge.number = count
            badge.isVisible = count > 0
        } else {
            tab.text = "$category ($count)"
        }
    }

    private fun startRefreshAnimation() {
        refreshItem?.let { item ->
            val iv = ImageView(this).apply {
                setImageResource(R.drawable.ic_refresh)
                // Set the size to match the standard toolbar menu item size (usually 48dp)
                val size = (48 * resources.displayMetrics.density).toInt()
                layoutParams = ViewGroup.LayoutParams(size, size)
                scaleType = ImageView.ScaleType.CENTER
                val typedValue = TypedValue()
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
                setColorFilter(typedValue.data)
            }
            val rotation = AnimationUtils.loadAnimation(this, R.anim.rotate_refresh)
            iv.startAnimation(rotation)
            item.actionView = iv
        }
    }

    private fun styleSearchView(searchView: SearchView) {
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)
        val iconAndTextColor = typedValue.data

        val hintColor = ColorUtils.setAlphaComponent(iconAndTextColor, 180)

        val searchIcon = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_mag_icon)
        searchIcon.setColorFilter(iconAndTextColor, PorterDuff.Mode.SRC_IN)

        val searchText = searchView.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
        searchText.setTextColor(iconAndTextColor)
        searchText.setHintTextColor(hintColor)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            searchText.textCursorDrawable = ColorDrawable(iconAndTextColor)
        }

        val closeButton = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeButton.setColorFilter(iconAndTextColor, PorterDuff.Mode.SRC_IN)

        val backButton = searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_button)
        backButton?.setColorFilter(iconAndTextColor, PorterDuff.Mode.SRC_IN)

        val underline = searchView.findViewById<View>(androidx.appcompat.R.id.search_plate)
        underline.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun stopRefreshAnimation() {
        refreshItem?.actionView?.clearAnimation()
        refreshItem?.actionView = null
    }
}