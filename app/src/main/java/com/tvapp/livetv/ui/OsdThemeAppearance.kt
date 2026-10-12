package com.tvapp.livetv.ui

import android.view.View
import androidx.annotation.DrawableRes
import com.tvapp.livetv.R
import com.tvapp.livetv.settings.OsdTheme

object OsdThemeAppearance {
    @DrawableRes
    fun rowBackground(theme: OsdTheme): Int = when (theme) {
        OsdTheme.DEFAULT -> R.drawable.bg_channel
        OsdTheme.GLASS -> R.drawable.bg_channel_glass
        OsdTheme.HIGH_CONTRAST -> R.drawable.bg_channel_high_contrast
    }

    fun apply(theme: OsdTheme, channelPanel: View, infoCard: View, panelOpacity: Int, infoOpacity: Int) {
        channelPanel.setBackgroundResource(when (theme) {
            OsdTheme.DEFAULT -> R.drawable.bg_glass_panel
            OsdTheme.GLASS -> R.drawable.bg_osd_glass
            OsdTheme.HIGH_CONTRAST -> R.drawable.bg_osd_high_contrast
        })
        infoCard.setBackgroundResource(when (theme) {
            OsdTheme.DEFAULT -> R.drawable.bg_info
            OsdTheme.GLASS -> R.drawable.bg_osd_glass
            OsdTheme.HIGH_CONTRAST -> R.drawable.bg_osd_high_contrast
        })
        channelPanel.background.mutate().alpha = if (theme == OsdTheme.HIGH_CONTRAST) 255
            else panelOpacity.coerceIn(30, 100) * 255 / 100
        // Default keeps the original info-card alpha; its parent opacity behavior is unchanged.
        infoCard.background.mutate().alpha = if (theme != OsdTheme.GLASS) 255
            else infoOpacity.coerceIn(30, 100) * 255 / 100
    }
}
