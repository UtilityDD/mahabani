package com.blackgrapes.kadachabuk

import android.content.Context
import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class VideoListFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var scrollToTopButton: FloatingActionButton
    private lateinit var emptyFavoritesTextView: TextView
    private var videos: List<Video> = emptyList()
    private var playbackListener: VideoPlaybackListener? = null
    private var favoriteChangedListener: (() -> Unit)? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is VideoPlaybackListener) {
            playbackListener = context
        }
        if (context is OnFavoriteChangedListener) {
            favoriteChangedListener = { context.onFavoriteChanged() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            videos = it.getParcelableArrayList(ARG_VIDEOS) ?: emptyList()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_video_list, container, false)
        recyclerView = view.findViewById(R.id.video_list_recycler_view) // Corrected ID
        scrollToTopButton = view.findViewById(R.id.fab_scroll_to_top)
        emptyFavoritesTextView = view.findViewById(R.id.empty_favorites_text)

        recyclerView.layoutManager = LinearLayoutManager(context)
        playbackListener?.let { pbListener ->
            recyclerView.adapter = VideoAdapter(videos, pbListener, favoriteChangedListener ?: {})
        }

        scrollToTopButton.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Show the button if the user has scrolled down past the first two items
                if ((recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition() > 2) {
                    if (scrollToTopButton.visibility != View.VISIBLE) {
                        scrollToTopButton.animate().alpha(1f).setDuration(200).withStartAction { scrollToTopButton.visibility = View.VISIBLE }
                    }
                } else {
                    if (scrollToTopButton.visibility == View.VISIBLE) {
                        scrollToTopButton.animate().alpha(0f).setDuration(200).withEndAction { scrollToTopButton.visibility = View.GONE }
                    }
                }
            }
        })
        updateEmptyViewVisibility()
        return view
    }

    fun updateVideos(newVideos: List<Video>) {
        this.videos = newVideos
        updateEmptyViewVisibility()
        // Make sure the adapter is not null before trying to update it
        (recyclerView.adapter as? VideoAdapter)?.updateVideos(newVideos)
    }

    companion object {
        private const val ARG_VIDEOS = "videos"
        private const val ARG_IS_FAVORITES = "is_favorites"

        fun newInstance(videos: List<Video>, isFavoritesTab: Boolean = false) = VideoListFragment().apply {
            arguments = Bundle().apply {
                putParcelableArrayList(ARG_VIDEOS, ArrayList(videos))
                putBoolean(ARG_IS_FAVORITES, isFavoritesTab)
            }
        }
    }

    private fun updateEmptyViewVisibility() {
        val isFavoritesTab = arguments?.getBoolean(ARG_IS_FAVORITES) ?: false
        if (isFavoritesTab && videos.isEmpty()) {
            emptyFavoritesTextView.visibility = View.VISIBLE
        } else {
            emptyFavoritesTextView.visibility = View.GONE
        }
    }
}