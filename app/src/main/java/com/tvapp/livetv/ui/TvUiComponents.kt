package com.tvapp.livetv.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import com.tvapp.livetv.R

object TvUiMetrics {
    const val SETTINGS_PANEL_WIDTH_FRACTION = 0.40f
    const val SETTINGS_PANEL_HEIGHT_FRACTION = 0.94f
    const val SAFE_EDGE_FRACTION = 0.012f
    const val ICON_SLOT_DP = 20
    const val COLOR_KEY_DP = 17
}

object TvUiComponents {
    fun applyFocusableRow(view: View) {
        view.background = AppCompatResources.getDrawable(view.context, R.drawable.bg_settings_item)
        view.isFocusable = true
        view.isClickable = true
        view.setOnFocusChangeListener { target, focused ->
            target.animate()
                .scaleX(if (focused) 1.015f else 1f)
                .scaleY(if (focused) 1.015f else 1f)
                .setDuration(90L)
                .start()
        }
    }

    fun colorAction(
        context: Context,
        colorDrawable: Int,
        label: String,
        interactive: Boolean,
        clicked: (() -> Unit)? = null,
    ): LinearLayout = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        orientation = LinearLayout.HORIZONTAL
        isClickable = interactive
        isFocusable = interactive
        if (interactive) {
            background = AppCompatResources.getDrawable(context, R.drawable.bg_focusable)
            minimumHeight = context.dp(44)
            setPadding(context.dp(9), 0, context.dp(9), 0)
            setOnClickListener { clicked?.invoke() }
        }
        addView(ImageView(context).apply {
            setImageResource(colorDrawable)
            contentDescription = label
        }, LinearLayout.LayoutParams(context.dp(TvUiMetrics.COLOR_KEY_DP), context.dp(TvUiMetrics.COLOR_KEY_DP)))
        addView(TextView(context).apply {
            text = label
            setTextColor(ContextCompat.getColor(context, if (interactive) R.color.text_primary else R.color.text_secondary))
            textSize = if (interactive) 12f else 11f
            setPadding(context.dp(5), 0, 0, 0)
        })
    }

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
