package com.tvapp.livetv.ui

import java.util.Locale

internal object VideoQuality {
    fun label(width: Int, height: Int, format: String): String? = resolutionLabel(width, height, format)

    fun resolutionLabel(width: Int, height: Int, format: String = ""): String? {
        val normalized = format.uppercase(Locale.ROOT)
        return when {
            width >= 3_840 || height >= 2_160 || "2160" in normalized || "4320" in normalized -> "4K"
            "1080P" in normalized -> "1080p"
            "1080I" in normalized -> "1080i"
            width >= 1_920 || height >= 1_080 -> "FHD"
            "720P" in normalized -> "720p"
            "720I" in normalized -> "720i"
            width >= 1_280 || height >= 720 -> "HD"
            "576P" in normalized -> "576p"
            "576I" in normalized -> "576i"
            "480P" in normalized -> "480p"
            "480I" in normalized -> "480i"
            width > 0 || height > 0 || listOf("240", "360", "480", "576").any(normalized::contains) -> "SD"
            else -> null
        }
    }
}
