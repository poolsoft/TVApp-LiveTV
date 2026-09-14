package com.tvapp.livetv.ui

internal object VideoQuality {
    fun label(width: Int, height: Int, format: String): String? = resolutionLabel(width, height, format, isTif = false)

    fun resolutionLabel(width: Int, height: Int, format: String = "", isTif: Boolean = false): String? = when {
        width >= 3_840 || height >= 2_160 || "2160" in format || "4320" in format -> "4K"
        width >= 1_920 || height >= 1_080 || "1080" in format -> {
            if ("1080P" in format) "1080p"
            else if ("1080I" in format || isTif) "1080i"
            else "1080p"
        }
        width >= 1_280 || height >= 720 || "720" in format -> "720p"
        width >= 720 || height >= 576 || "576" in format -> {
            if ("576P" in format) "576p"
            else if ("576I" in format || isTif) "576i"
            else "576p"
        }
        width >= 640 || height >= 480 || "480" in format -> {
            if ("480P" in format) "480p"
            else if ("480I" in format || isTif) "480i"
            else "480p"
        }
        height > 0 -> "${height}p"
        listOf("240", "360").any(format::contains) -> "SD"
        else -> null
    }
}
