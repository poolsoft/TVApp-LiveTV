package com.tvapp.livetv.ui

internal object VideoQuality {
    fun label(width: Int, height: Int, format: String): String? = when {
        width >= 3_840 || height >= 2_160 || "2160" in format || "4320" in format -> "4K"
        width >= 1_920 || height >= 1_080 || "1080" in format -> "FHD"
        width >= 1_280 || height >= 720 || "720" in format -> "HD"
        width > 0 || height > 0 || listOf("240", "360", "480", "576").any(format::contains) -> "SD"
        else -> null
    }
}
