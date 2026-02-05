package com.blackgrapes.kadachabuk

import android.os.Parcelable
import java.util.regex.Pattern
import kotlinx.parcelize.Parcelize

enum class VideoSource { YOUTUBE, FACEBOOK, UNKNOWN }

@Parcelize
data class Video(
    val sl: String,
    private val originalLink: String,
    val remark: String,
    val category: String
) : Parcelable {

    // This computed property ensures that we always use a compatible link format.
    val link: String
        get() {
            // Convert new "/share/r/" links to the embeddable "/reel/" format.
            if (originalLink.contains("/share/r/")) {
                return originalLink.replace("/share/r/", "/reel/")
            }
            return originalLink
        }

    val source: VideoSource
        get() {
            // Use the originalLink for source detection to be safe.
            val linkForDetection = originalLink
            return when {
                link.contains("youtube.com") || link.contains("youtu.be") -> VideoSource.YOUTUBE
                link.contains("facebook.com") || link.contains("fb.watch") || link.contains("fb.gg") -> VideoSource.FACEBOOK
                else -> VideoSource.UNKNOWN
            }
        }
    fun getYouTubeVideoId(): String? {
        val pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#\\&\\?\\n]*"
        val compiledPattern = Pattern.compile(pattern)
        val matcher = compiledPattern.matcher(link)
        return if (matcher.find()) matcher.group() else null
    }

    /**
     * Provides a unique identifier for the video, suitable for use as a key in SharedPreferences.
     * It defaults to the YouTube video ID, falling back to the original link if the ID can't be extracted.
     */
    fun getUniqueId(): String = getYouTubeVideoId() ?: originalLink

}