package com.blackgrapes.kadachabuk

import android.content.res.Configuration
import android.view.Window
import androidx.core.view.ViewCompat

/**
 * A utility object for handling window-related configurations, such as status bar icon colors.
 */
object WindowUtils {

    /**
     * Sets the system status bar icon colors based on the current theme.
     * This enforces the app's specific style: black icons in dark mode, and white icons in light mode.
     *
     * @param window The window whose status bar icons need to be controlled.
     */
    fun setStatusBarIconColor(window: Window) {
        ViewCompat.getWindowInsetsController(window.decorView)?.let { controller ->
            val isNightMode = (window.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            // For dark icons in dark mode (isNightMode=true), isAppearanceLightStatusBars must be true.
            // For light icons in light mode (isNightMode=false), isAppearanceLightStatusBars must be false.
            // Therefore, the value should be the same as isNightMode.
            controller.isAppearanceLightStatusBars = isNightMode
        }
    }
}