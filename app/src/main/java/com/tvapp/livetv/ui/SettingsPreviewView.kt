package com.tvapp.livetv.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.tvapp.livetv.R
import com.tvapp.livetv.settings.ChannelPanelSide
import com.tvapp.livetv.settings.DisplayPreferences
import com.tvapp.livetv.settings.InfoBarPosition

class SettingsPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var preferences: DisplayPreferences = DisplayPreferences()
        set(value) {
            field = value
            invalidate()
        }

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = context.getColor(R.color.accent)
        alpha = 130
    }
    private val primaryText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_primary)
        textSize = 12f * resources.displayMetrics.scaledDensity
        isFakeBoldText = true
    }
    private val secondaryText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text_secondary)
        textSize = 9f * resources.displayMetrics.scaledDensity
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = width * 0.035f
        val top = height * 0.10f
        val bottom = height * 0.90f
        val panelWidth = width * 0.29f
        val gap = width * 0.018f
        val channelLeft = if (preferences.channelPanelSide == ChannelPanelSide.LEFT) inset else width - inset - panelWidth
        val channelRect = RectF(channelLeft, top, channelLeft + panelWidth, bottom)
        drawPanel(canvas, channelRect, preferences.channelPanelOpacityPercent)
        canvas.drawText(context.getString(R.string.channel_list), channelRect.left + 10f, channelRect.top + 22f, primaryText)
        repeat(3) { index ->
            val y = channelRect.top + 43f + index * 24f
            canvas.drawText("${index + 1}", channelRect.left + 10f, y, secondaryText)
            canvas.drawText(
                if (index == 0) context.getString(R.string.settings_preview_channel) else "TV ${index + 1}",
                channelRect.left + 30f,
                y,
                secondaryText,
            )
        }

        val infoLeft = if (preferences.channelPanelSide == ChannelPanelSide.LEFT) channelRect.right + gap else inset
        val infoRight = if (preferences.channelPanelSide == ChannelPanelSide.LEFT) width - inset else channelRect.left - gap
        val infoHeight = height * 0.32f
        val infoTop = if (preferences.infoBarPosition == InfoBarPosition.TOP) top else bottom - infoHeight
        val infoRect = RectF(infoLeft, infoTop, infoRight, infoTop + infoHeight)
        drawPanel(canvas, infoRect, preferences.infoBarOpacityPercent)
        canvas.drawText("1", infoRect.left + 12f, infoRect.top + 23f, primaryText)
        canvas.drawText(context.getString(R.string.settings_preview_channel), infoRect.left + 42f, infoRect.top + 23f, primaryText)
        canvas.drawText(context.getString(R.string.settings_preview_program), infoRect.left + 42f, infoRect.top + 43f, secondaryText)
    }

    private fun drawPanel(canvas: Canvas, rect: RectF, opacityPercent: Int) {
        panelPaint.color = context.getColor(R.color.panel)
        panelPaint.alpha = (opacityPercent.coerceIn(0, 100) * 255 / 100)
        canvas.drawRoundRect(rect, 6f, 6f, panelPaint)
        canvas.drawRoundRect(rect, 6f, 6f, borderPaint)
    }
}
