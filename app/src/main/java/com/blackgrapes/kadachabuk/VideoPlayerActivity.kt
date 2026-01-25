package com.blackgrapes.kadachabuk

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.view.MenuItem
import android.widget.FrameLayout
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import android.view.Menu
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.net.URLEncoder

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var fullscreenContainer: FrameLayout
    private var customView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        webView = findViewById(R.id.video_webview)
        fullscreenContainer = findViewById(R.id.fullscreen_container)
        val video = intent.getParcelableExtra<Video>("video") ?: return

        setupWebView()

        when (video.source) {
            VideoSource.YOUTUBE -> loadYouTubeVideo(video)
            VideoSource.FACEBOOK -> loadFacebookVideo(video)
            else -> {
                // Handle unknown or unsupported video links, perhaps show an error
                finish() // Or show a toast/message
            }
        }
        title = video.remark
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient() // Ensures links open within the WebView
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                webView.visibility = View.GONE
                fullscreenContainer.visibility = View.VISIBLE
                fullscreenContainer.addView(customView)
                enterFullscreen()
            }

            override fun onHideCustomView() {
                super.onHideCustomView()
                if (customView == null) return

                webView.visibility = View.VISIBLE
                fullscreenContainer.visibility = View.GONE
                fullscreenContainer.removeView(customView)
                customView = null
                exitFullscreen()
            }
        }
    }

    override fun onBackPressed() {
        if (customView != null) {
            (webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadYouTubeVideo(video: Video) {
        val videoId = video.getYouTubeVideoId()
        if (videoId != null) {
            val videoUrl = "https://www.youtube.com/embed/$videoId?autoplay=1"
            val html = """
                <html><body style="margin:0;padding:0;overflow:hidden;background-color:black;">
                <iframe width="100%" height="100%" src="$videoUrl" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" allowfullscreen></iframe>
                </body></html>
            """.trimIndent()
            webView.loadData(html, "text/html", "utf-8")
        }
    }

    private fun loadFacebookVideo(video: Video) {
        // Facebook's embedded player requires the original video URL to be encoded.
        val encodedUrl = URLEncoder.encode(video.link, "UTF-8")
        val embedUrl = "https://www.facebook.com/plugins/video.php?href=$encodedUrl&show_text=false&autoplay=true"
        webView.loadUrl(embedUrl)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.video_player_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            R.id.action_fullscreen -> {
                // Manually trigger fullscreen mode if not already in it.
                // The web player's own button will call onHideCustomView to exit.
                if (customView == null) {
                    // This JS finds the video element and requests fullscreen.
                    webView.evaluateJavascript("document.querySelector('video').requestFullscreen()", null)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun enterFullscreen() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        supportActionBar?.hide()
    }

    private fun exitFullscreen() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        supportActionBar?.show()
    }
}