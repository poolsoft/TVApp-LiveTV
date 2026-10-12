package com.tvapp.livetv.settings

enum class OsdTheme {
    DEFAULT, GLASS, HIGH_CONTRAST;

    companion object {
        fun fromName(value: String?): OsdTheme = entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
