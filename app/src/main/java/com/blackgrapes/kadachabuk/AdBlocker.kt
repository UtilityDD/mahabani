package com.blackgrapes.kadachabuk

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.util.Locale

object AdBlocker {
    private val AD_DOMAINS = hashSetOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "moatads.com",
        "adservice.google.com",
        "google-analytics.com",
        "ads.youtube.com",
        "adsystem.com",
        "adtarget.me",
        "amazon-adsystem.com",
        "adnxs.com",
        "openx.net",
        "pubmatic.com",
        "rubiconproject.com",
        "scorecardresearch.com"
    )

    fun isAd(url: String): Boolean {
        val lowerUrl = url.lowercase(Locale.ROOT)
        return AD_DOMAINS.any { domain -> 
            lowerUrl.contains(domain)
        }
    }

    fun createEmptyResource(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
    }
}
