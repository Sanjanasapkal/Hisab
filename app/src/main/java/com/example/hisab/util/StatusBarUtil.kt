package com.example.hisab.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Utility for handling Edge-to-Edge system window insets.
 *
 * Ensures headers and toolbars extend behind the status bar with their brand background,
 * while automatically sliding down all interactive elements (back button, titles, action icons)
 * below the status bar, camera punch-hole, and display cutouts.
 */
object StatusBarUtil {

    /**
     * Applies status bar and display cutout insets as top padding to the target view.
     */
    fun applyStatusBarPadding(view: View) {
        val basePaddingTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(top = basePaddingTop + insets.top)
            windowInsets
        }
        ViewCompat.requestApplyInsets(view)
    }
}
