package com.blackgrapes.kadachabuk

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.content.SharedPreferences
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.squareup.picasso.Picasso

class VideoAdapter(
    private var videos: List<Video>,
    private val playbackListener: VideoPlaybackListener,
    private val onFavoriteChanged: () -> Unit
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private var currentlyPlayingPosition: Int = -1
    private lateinit var favoritePrefs: SharedPreferences

    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.videoThumbnail)
        val remark: TextView = view.findViewById(R.id.videoRemark)
        val webView: WebView = view.findViewById(R.id.video_webview)
        val favoriteButton: ImageButton = view.findViewById(R.id.favoriteButton)
        val progressBar: ProgressBar = view.findViewById(R.id.video_progress_bar)
        val playButton: FrameLayout = view.findViewById(R.id.play_button)
        val collapseButton: ImageButton = view.findViewById(R.id.collapse_button)

        fun releasePlayer() {
            webView.stopLoading()
            webView.loadUrl("about:blank")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        if (!::favoritePrefs.isInitialized) {
            favoritePrefs = parent.context.getSharedPreferences("VideoFavorites", Context.MODE_PRIVATE)
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_video, parent, false)
        return VideoViewHolder(view)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = videos[position]
        holder.remark.text = "${position + 1}. ${video.remark}"
        
        // --- THUMBNAIL LOGIC ---
        when (video.source) {
            VideoSource.YOUTUBE -> {
                val videoId = video.getYouTubeVideoId()
                if (videoId != null) {
                    val thumbnailUrl = "https://img.youtube.com/vi/$videoId/0.jpg"
                    Picasso.get().load(thumbnailUrl).placeholder(R.drawable.rounded_corner_background).into(holder.thumbnail)
                }
            }
            VideoSource.FACEBOOK -> {
                // Facebook doesn't have a simple thumbnail URL like YouTube.
                // Using a dedicated Facebook icon/placeholder.
                Picasso.get().load(R.drawable.ic_facebook).into(holder.thumbnail)
            }
            else -> {
                holder.thumbnail.setImageResource(R.drawable.rounded_corner_background)
            }
        }

        val isFavorite = favoritePrefs.getBoolean(video.getUniqueId(), false)
        holder.favoriteButton.setImageResource(if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)

        holder.favoriteButton.setOnClickListener { 
            val currentPosition = holder.adapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener
            val clickedVideo = videos[currentPosition]

            val growAnim = AnimationUtils.loadAnimation(it.context, R.anim.heart_grow)
            val shrinkAnim = AnimationUtils.loadAnimation(it.context, R.anim.heart_shrink)

            growAnim.setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}

                override fun onAnimationEnd(animation: Animation?) {
                    val currentIsFavorite = favoritePrefs.getBoolean(clickedVideo.getUniqueId(), false)
                    val newIsFavorite = !currentIsFavorite
                    with(favoritePrefs.edit()) {
                        putBoolean(clickedVideo.getUniqueId(), newIsFavorite)
                        apply()
                    }
                    holder.favoriteButton.setImageResource(if (newIsFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border)
                    holder.favoriteButton.startAnimation(shrinkAnim)
                    onFavoriteChanged()
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })

            holder.favoriteButton.isClickable = false
            holder.favoriteButton.startAnimation(growAnim)
            holder.favoriteButton.postDelayed({ holder.favoriteButton.isClickable = true }, 300)
         }

        if (position == currentlyPlayingPosition) {
            // PLAYING STATE
            playbackListener.onVideoPlaybackChanged(video.remark)
            holder.thumbnail.visibility = View.INVISIBLE
            holder.playButton.visibility = View.GONE
            holder.webView.visibility = View.VISIBLE
            holder.collapseButton.visibility = View.VISIBLE

            val htmlContent = when (video.source) {
                VideoSource.YOUTUBE -> {
                    val videoId = video.getYouTubeVideoId()
                    val packageName = holder.itemView.context.packageName
                    val origin = "https://$packageName"
                    """
                    <html><body style="margin:0;padding:0;background-color:black;">
                    <iframe width="100%" height="100%" 
                            src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1&origin=$origin&widget_referrer=$origin" 
                            frameborder="0" allow="autoplay; fullscreen" allowfullscreen
                            referrerpolicy="strict-origin-when-cross-origin"></iframe>
                    </body></html>
                    """.trimIndent()
                }
                VideoSource.FACEBOOK -> {
                    val encodedUrl = java.net.URLEncoder.encode(video.link, "UTF-8")
                    """
                    <html><body style="margin:0;padding:0;background-color:black;">
                    <iframe src="https://www.facebook.com/plugins/video.php?href=$encodedUrl&show_text=0&width=560&autoplay=1" width="100%" height="100%" style="border:none;overflow:hidden" scrolling="no" frameborder="0" allowfullscreen="true" allow="autoplay; clipboard-write; encrypted-media; picture-in-picture; web-share" allowFullScreen="true"></iframe>
                    </body></html>
                    """.trimIndent()
                }
                else -> {
                    """
                    <html><body style="margin:0;padding:0;background-color:black;display:flex;justify-content:center;align-items:center;">
                    <a href="${video.link}" style="color:white;text-decoration:none;font-family:sans-serif;">Open Video Stream</a>
                    </body></html>
                    """.trimIndent()
                }
            }

            holder.webView.settings.javaScriptEnabled = true
            holder.webView.settings.domStorageEnabled = true
            holder.webView.settings.mediaPlaybackRequiresUserGesture = false
            holder.webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress < 100) {
                        holder.progressBar.visibility = View.VISIBLE
                    } else {
                        holder.progressBar.visibility = View.GONE
                    }
                }
            }
            val baseUrl = "https://${holder.itemView.context.packageName}"
            holder.webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "utf-8", null)
        } else {
            // IDLE STATE
            holder.releasePlayer()
            holder.thumbnail.visibility = View.VISIBLE
            holder.playButton.visibility = View.VISIBLE
            holder.webView.visibility = View.GONE
            holder.progressBar.visibility = View.GONE
            holder.collapseButton.visibility = View.GONE
        }

        holder.playButton.setOnClickListener {
            val clickedPosition = holder.adapterPosition
            if(clickedPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            val previousPlayingPosition = currentlyPlayingPosition
            currentlyPlayingPosition = clickedPosition

            if (previousPlayingPosition != -1) {
                notifyItemChanged(previousPlayingPosition)
            }
            notifyItemChanged(currentlyPlayingPosition)
        }

        holder.collapseButton.setOnClickListener{
            val clickedPosition = holder.adapterPosition
            if(clickedPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            currentlyPlayingPosition = -1
            notifyItemChanged(clickedPosition)
            playbackListener.onVideoPlaybackChanged(null) // Reset title
        }
    }

    override fun onViewDetachedFromWindow(holder: VideoViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder.adapterPosition == currentlyPlayingPosition) {
            holder.releasePlayer()
            currentlyPlayingPosition = -1
            playbackListener.onVideoPlaybackChanged(null) 
        }
    }

    override fun onViewRecycled(holder: VideoViewHolder) {
        super.onViewRecycled(holder)
        holder.releasePlayer()
    }

    override fun getItemCount() = videos.size

    fun updateVideos(newVideos: List<Video>) {
        val diffCallback = VideoDiffCallback(this.videos, newVideos)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        this.videos = newVideos.toList()
        diffResult.dispatchUpdatesTo(this)
    }

    private class VideoDiffCallback(
        private val oldList: List<Video>,
        private val newList: List<Video>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].getUniqueId() == newList[newItemPosition].getUniqueId()
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}