package com.tvapp.livetv.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView

data class IptvSubtitleAppearance(
    val custom: Boolean = false,
    val sizePercent: Int = 100,
    val color: Int = Color.WHITE,
    val background: Boolean = true,
    val outline: Boolean = true,
    val position: Int = 0,
    val monospace: Boolean = false,
)

class IptvSubtitleAppearanceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("iptv-subtitle-appearance", Context.MODE_PRIVATE)
    fun load() = IptvSubtitleAppearance(
        custom = preferences.getBoolean("custom", false),
        sizePercent = preferences.getInt("size", 100).coerceIn(50, 200),
        color = preferences.getInt("color", Color.WHITE),
        background = preferences.getBoolean("background", true),
        outline = preferences.getBoolean("outline", true),
        position = preferences.getInt("position", 0).coerceIn(0, 2),
        monospace = preferences.getBoolean("monospace", false),
    )
    fun save(value: IptvSubtitleAppearance) {
        preferences.edit().putBoolean("custom", value.custom).putInt("size", value.sizePercent)
            .putInt("color", value.color).putBoolean("background", value.background)
            .putBoolean("outline", value.outline).putInt("position", value.position)
            .putBoolean("monospace", value.monospace).apply()
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun apply(playerView: PlayerView) {
        val view = playerView.subtitleView ?: return
        val value = load()
        view.setApplyEmbeddedStyles(!value.custom)
        view.setApplyEmbeddedFontSizes(!value.custom)
        if (!value.custom) {
            view.setUserDefaultStyle()
            view.setUserDefaultTextSize()
            view.setBottomPaddingFraction(.08f)
            return
        }
        view.setStyle(CaptionStyleCompat(value.color, if (value.background) 0x80000000.toInt() else Color.TRANSPARENT,
            Color.TRANSPARENT, if (value.outline) CaptionStyleCompat.EDGE_TYPE_OUTLINE else CaptionStyleCompat.EDGE_TYPE_NONE,
            Color.BLACK, if (value.monospace) Typeface.MONOSPACE else Typeface.SANS_SERIF))
        view.setFractionalTextSize(.0533f * value.sizePercent / 100f)
        view.setBottomPaddingFraction(listOf(.08f, .45f, .85f)[value.position])
    }
}
