package com.tvapp.livetv.ui

import android.media.tv.TvContract
import com.tvapp.livetv.model.LiveChannel
import java.util.Locale

internal fun LiveChannel.isRadioChannel(): Boolean =
    serviceType == TvContract.Channels.SERVICE_TYPE_AUDIO ||
        groupTitle.orEmpty().contains("radyo", ignoreCase = true) ||
        groupTitle.orEmpty().contains("radio", ignoreCase = true)

internal fun LiveChannel.qualityLabel(): String? {
    if (isRadioChannel()) return null
    val format = videoFormat.orEmpty().uppercase(Locale.ROOT)
    val name = displayName.uppercase(Locale.ROOT)
    return when {
        "4320" in format || "2160" in format || "4K" in name || "UHD" in name -> "4K"
        "1080" in format || "720" in format || "HD" in name -> "HD"
        format.isNotBlank() || "SD" in name -> "SD"
        else -> null
    }
}
