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
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
 
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
            fetchVideoData()
        }

        fetchVideoData()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.video_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                // This is the ID for the "up" button.
                onBackPressedDispatcher.onBackPressed()
                return true
            }
            R.id.action_refresh_videos -> {
                // Call fetchVideoData when refresh is clicked
                fetchVideoData()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    private fun fetchVideoData() {
        progressBar.visibility = View.VISIBLE
        errorGroup.visibility = View.GONE
        tabLayout.visibility = View.GONE
        viewPager.visibility = View.GONE
        // Standard Google Sheets CSV export URL
        val sheetId = "1wZSxXRZHkgbTG3oPDJn_JbKy4m3BWELah67XcgBz6BA"
        val gid = "1681780330"
        
        // This is the most reliable URL for public Google Sheets CSV export
        val url = "https://docs.google.com/spreadsheets/d/$sheetId/export?gid=$gid&format=csv"

        android.util.Log.d("VideoActivity", "Fetching from URL: $url")

        val stringRequest = StringRequest(Request.Method.GET, url,
            { response ->
                android.util.Log.d("VideoActivity", "Response received, length: ${response.length}")
                
                // Safety check: if response starts with "PK", it's an Excel/ZIP file, not CSV
                if (response.startsWith("PK")) {
                    android.util.Log.e("VideoActivity", "Response is BINARY/ZIP (Excel format), not CSV!")
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        errorGroup.visibility = View.VISIBLE
                        errorMessageTextView.text = "Error: Sheet is in Excel format. Please 'Publish to Web' as 'CSV'."
                    }
                    return@StringRequest
                }

                android.util.Log.d("VideoActivity", "First 100 chars: ${response.take(100).replace("\n", "[NL]")}")
                
                // Launch a coroutine to handle parsing in the background
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        val videoList = withContext(Dispatchers.IO) {
                            parseCsv(response)
                        }
                        allVideos = videoList 

                        android.util.Log.d("VideoActivity", "Parsed ${videoList.size} videos")

                        if (videoList.isEmpty()) {
                            progressBar.visibility = View.GONE
                            errorGroup.visibility = View.VISIBLE
                            errorMessageTextView.text = "No valid videos found in the sheet."
                            return@launch
                        }

                        // Update UI
                        progressBar.visibility = View.GONE
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
                            if (position == 0) {
                                tab.setIcon(R.drawable.ic_favorite_filled)
                                tab.orCreateBadge.apply {
                                    number = count
                                    isVisible = count > 0
                                }
                            } else {
                                tab.text = "$category ($count)"
                            }
                        }.attach()

                        viewPager.setCurrentItem(currentViewPagerPosition, false)
                        toolbar.title = "Video Links (${videoList.size})"
                        
                    } catch (e: Exception) {
                        android.util.Log.e("VideoActivity", "Error parsing CSV", e)
                        progressBar.visibility = View.GONE
                        errorGroup.visibility = View.VISIBLE
                        errorMessageTextView.text = "Parsing error: ${e.message}"
                    }
                }
            },
            { error ->
                android.util.Log.e("VideoActivity", "Network error: ${error.message}")
                progressBar.visibility = View.GONE
                errorGroup.visibility = View.VISIBLE
                errorMessageTextView.text = "Network error. Please check your internet."
            })

        Volley.newRequestQueue(this).add(stringRequest)
    }

    private fun parseCsv(csvData: String): List<Video> {
        val videos = mutableListOf<Video>()
        val lines = csvData.lines().filter { it.isNotBlank() }
        if (lines.size < 2) {
            android.util.Log.w("VideoActivity", "CSV has only ${lines.size} lines, no data found.")
            return emptyList()
        }
        
        // Detect delimiter from header (e.g. sl,link,remark,category)
        val header = lines[0]
        val delimiter = if (header.contains("\t")) "\t" else ","
        android.util.Log.d("VideoActivity", "Header: $header | Delimiter: '$delimiter'")

        lines.drop(1).forEach { line ->
            val parts = splitCsvLine(line, delimiter)
            if (parts.size >= 4) {
                val sl = parts[0].trim().removeSurrounding("\"")
                val rawLink = parts[1].trim().removeSurrounding("\"")
                val remark = parts[2].trim().removeSurrounding("\"")
                val category = parts[3].trim().removeSurrounding("\"")
                
                val link = extractVideoUrl(rawLink)
                if (link.isNotEmpty()) {
                    videos.add(Video(sl, link, remark, category))
                    android.util.Log.d("VideoActivity", "Parsed: $remark | Category: $category")
                }
            } else {
                android.util.Log.w("VideoActivity", "Skipping malformed line: $line")
            }
        }
        return videos
    }

    private fun splitCsvLine(line: String, delimiter: String): List<String> {
        // Simple splitter, could be improved for quoted commas but usually enough for Sheets
        return if (delimiter == "\t") {
            line.split("\t")
        } else {
            // Improved comma split that handles some quoted values
            val result = mutableListOf<String>()
            var start = 0
            var inQuotes = false
            for (i in line.indices) {
                if (line[i] == '\"') inQuotes = !inQuotes
                else if (line[i] == ',' && !inQuotes) {
                    result.add(line.substring(start, i))
                    start = i + 1
                }
            }
            result.add(line.substring(start))
            result
        }
    }
    
    private fun extractVideoUrl(input: String): String {
        android.util.Log.d("VideoActivity", "Extracting from input: $input")
        
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
                // If it's already an embed URL, we might want to extract the actual video URL
                // but usually the Facebook plugin URL contains the href parameter.
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