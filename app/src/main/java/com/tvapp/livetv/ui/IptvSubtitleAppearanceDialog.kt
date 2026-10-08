package com.tvapp.livetv.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.tvapp.livetv.R
import com.tvapp.livetv.settings.IptvSubtitleAppearanceStore

object IptvSubtitleAppearanceDialog {
    fun show(context: Context, onSaved: () -> Unit = {}) {
        val store = IptvSubtitleAppearanceStore(context)
        var value = store.load()
        val builder = AlertDialog.Builder(context, R.style.Theme_TVApp_Dialog)
        val themed = builder.context
        val density = context.resources.displayMetrics.density
        val content = LinearLayout(themed).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(themed).apply {
            addView(content)
        }
        val body = LinearLayout(themed).apply {
            setPadding((12 * density).toInt(), 0, (12 * density).toInt(), 0)
            addView(scroll, LinearLayout.LayoutParams(-1, (context.resources.displayMetrics.heightPixels * .5f).toInt()))
        }
        fun choice(title: Int, labels: List<String>, selected: () -> Int, change: (Int) -> Unit) {
            val row = TextView(themed).apply {
                layoutParams = LinearLayout.LayoutParams(-1, (48 * density).toInt()).apply { setMargins(0, 0, 0, (4 * density).toInt()) }
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), 0)
                setTextColor(context.getColor(R.color.text_primary))
                setBackgroundResource(R.drawable.bg_focusable)
                isFocusable = true
                isClickable = true
            }
            fun render() { row.text = "${context.getString(title)}: ${labels[selected().coerceIn(labels.indices)]}" }
            render()
            row.setOnClickListener {
                AlertDialog.Builder(themed, R.style.Theme_TVApp_Dialog).setTitle(title)
                    .setSingleChoiceItems(labels.toTypedArray(), selected()) { dialog, index ->
                        change(index); render(); dialog.dismiss(); row.requestFocus()
                    }.setNegativeButton(R.string.cancel, null).show()
            }
            content.addView(row)
        }
        val binary = listOf(context.getString(R.string.off), context.getString(R.string.on))
        choice(R.string.iptv_subtitle_custom, binary, { if (value.custom) 1 else 0 }) { value = value.copy(custom = it == 1) }
        val sizes = listOf(50, 75, 100, 125, 150, 175, 200)
        choice(R.string.iptv_subtitle_size, sizes.map { "$it%" }, { sizes.indexOf(value.sizePercent).coerceAtLeast(0) }) { value = value.copy(sizePercent = sizes[it]) }
        val colors = listOf(Color.WHITE, Color.YELLOW, Color.CYAN)
        choice(R.string.iptv_subtitle_color, listOf(R.string.iptv_subtitle_white, R.string.iptv_subtitle_yellow, R.string.iptv_subtitle_cyan).map(context::getString),
            { colors.indexOf(value.color).coerceAtLeast(0) }) { value = value.copy(color = colors[it]) }
        choice(R.string.iptv_subtitle_background, binary, { if (value.background) 1 else 0 }) { value = value.copy(background = it == 1) }
        choice(R.string.iptv_subtitle_outline, binary, { if (value.outline) 1 else 0 }) { value = value.copy(outline = it == 1) }
        choice(R.string.iptv_subtitle_position, listOf(R.string.iptv_subtitle_bottom, R.string.iptv_subtitle_center, R.string.iptv_subtitle_top).map(context::getString),
            { value.position }) { value = value.copy(position = it) }
        choice(R.string.iptv_subtitle_monospace, binary, { if (value.monospace) 1 else 0 }) { value = value.copy(monospace = it == 1) }
        val dialog = builder.setTitle(R.string.iptv_subtitle_appearance).setView(body)
            .setPositiveButton(R.string.save) { _, _ -> store.save(value); onSaved() }
            .setNegativeButton(R.string.cancel, null).create()
        dialog.setOnShowListener { content.post { if (dialog.isShowing) content.getChildAt(0).requestFocus() } }
        dialog.show()
    }
}
