package com.tvapp.livetv.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.tvapp.livetv.databinding.ActivityMainBinding
import com.tvapp.livetv.settings.ChannelPanelSide
import com.tvapp.livetv.settings.DisplayPreferences
import com.tvapp.livetv.settings.InfoBarPosition

class TvOverlayLayoutHelper(
    private val context: Context,
    private val binding: ActivityMainBinding,
    private val getDisplayPreferences: () -> DisplayPreferences,
    private val isChannelPanelExpanded: () -> Boolean,
    private val showsVodTimeline: () -> Boolean,
    private val dp: (Int) -> Int,
) {
    companion object {
        const val COMPACT_PANEL_WIDTH_FRACTION = 0.25f
        const val EXPANDED_PANEL_FRACTION = 0.44f
        const val INFO_COMPACT_HEIGHT_FRACTION = 0.205f
        const val IPTV_INFO_EXTRA_HEIGHT_DP = 66
        const val OVERLAY_GAP_FRACTION = 0.008f
        const val VERTICAL_MARGIN_FRACTION = 0.026f
        const val INFO_HORIZONTAL_PADDING_FRACTION = 0.012f
        const val INFO_OUTER_MARGIN_FRACTION = 0.007f
        const val INFO_VERTICAL_PADDING_FRACTION = 0.012f
        const val CHANNEL_ACTIONS_HEIGHT_FRACTION = 0.12f
    }

    fun applyPanelGeometry() {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val verticalMargin = (screenHeight * VERTICAL_MARGIN_FRACTION).toInt()
        val overlayGap = (screenWidth * OVERLAY_GAP_FRACTION).toInt()
        val infoOuterMargin = (screenWidth * INFO_OUTER_MARGIN_FRACTION).toInt()
        val infoHeight = currentInfoBarHeight(screenHeight)
        val expanded = isChannelPanelExpanded()
        val displayPrefs = getDisplayPreferences()

        val panelWidth = if (expanded) {
            (screenWidth * EXPANDED_PANEL_FRACTION).toInt()
        } else {
            (screenWidth * COMPACT_PANEL_WIDTH_FRACTION).toInt()
        }

        binding.channelPanel.layoutParams =
            (binding.channelPanel.layoutParams as FrameLayout.LayoutParams).apply {
                width = panelWidth
                height = if (expanded) {
                    FrameLayout.LayoutParams.MATCH_PARENT
                } else {
                    screenHeight - (verticalMargin * 2)
                }
                gravity = (if (displayPrefs.channelPanelSide == ChannelPanelSide.LEFT) {
                    Gravity.START
                } else {
                    Gravity.END
                }) or Gravity.TOP
                val horizontal = 0
                val vertical = if (expanded) 0 else verticalMargin
                setMargins(horizontal, vertical, horizontal, vertical)
            }

        binding.channelActions.layoutParams = binding.channelActions.layoutParams.apply {
            height = (screenHeight * CHANNEL_ACTIONS_HEIGHT_FRACTION).toInt()
        }

        binding.infoBar.layoutParams =
            (binding.infoBar.layoutParams as FrameLayout.LayoutParams).apply {
                width = screenWidth - panelWidth - overlayGap - infoOuterMargin
                height = infoHeight
                gravity = (if (displayPrefs.channelPanelSide == ChannelPanelSide.LEFT) {
                    Gravity.END
                } else {
                    Gravity.START
                }) or if (displayPrefs.infoBarPosition == InfoBarPosition.TOP) {
                    Gravity.TOP
                } else {
                    Gravity.BOTTOM
                }
                if (displayPrefs.channelPanelSide == ChannelPanelSide.LEFT) {
                    setMargins(
                        0,
                        verticalMargin,
                        infoOuterMargin,
                        verticalMargin,
                    )
                } else {
                    setMargins(
                        infoOuterMargin,
                        verticalMargin,
                        0,
                        verticalMargin,
                    )
                }
            }

        val infoHorizontalPadding = (screenWidth * INFO_HORIZONTAL_PADDING_FRACTION).toInt()
        val infoVerticalPadding = (screenHeight * INFO_VERTICAL_PADDING_FRACTION).toInt()
        binding.infoBar.setPadding(
            infoHorizontalPadding,
            infoVerticalPadding,
            infoHorizontalPadding,
            infoVerticalPadding,
        )

        val diagGravity = (if (displayPrefs.channelPanelSide == ChannelPanelSide.LEFT) {
            Gravity.END
        } else {
            Gravity.START
        }) or if (displayPrefs.infoBarPosition == InfoBarPosition.TOP) {
            Gravity.BOTTOM
        } else {
            Gravity.TOP
        }

        (binding.diagnosticsOverlay.layoutParams as? FrameLayout.LayoutParams)?.apply {
            gravity = diagGravity
            setMargins(infoOuterMargin, verticalMargin, infoOuterMargin, verticalMargin)
            binding.diagnosticsOverlay.layoutParams = this
        }
    }

    fun currentInfoBarHeight(screenHeight: Int): Int =
        (screenHeight * INFO_COMPACT_HEIGHT_FRACTION).toInt() +
            if (binding.iptvPlaybackContainer.visibility == View.VISIBLE) {
                dp(if (showsVodTimeline()) IPTV_INFO_EXTRA_HEIGHT_DP else 37)
            } else {
                0
            }

    fun updateInfoBarHeight() {
        val screenHeight = binding.root.height.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels
        val targetHeight = currentInfoBarHeight(screenHeight)
        val lp = binding.infoBar.layoutParams
        if (lp != null && lp.height == targetHeight) return
        binding.infoBar.layoutParams = (lp ?: FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            targetHeight,
        )).apply {
            height = targetHeight
        }
    }
}
