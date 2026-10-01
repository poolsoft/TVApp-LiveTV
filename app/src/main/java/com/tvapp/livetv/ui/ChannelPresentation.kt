package com.tvapp.livetv.ui

import android.media.tv.TvContract
import com.tvapp.livetv.model.LiveChannel

internal fun LiveChannel.isRadioChannel(): Boolean =
    serviceType == TvContract.Channels.SERVICE_TYPE_AUDIO ||
        groupTitle.orEmpty().contains("radyo", ignoreCase = true) ||
        groupTitle.orEmpty().contains("radio", ignoreCase = true)

internal fun LiveChannel.qualityLabel(): String? {
    if (isRadioChannel()) return null
    return VideoQuality.resolutionLabel(0, 0, videoFormat.orEmpty())
}
