package com.tvapp.livetv.tifinput

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

object IptvInputResolver {
    fun findInput(context: Context): TvInputInfo? =
        context.getSystemService(TvInputManager::class.java).tvInputList.firstOrNull(::isOwnInput)

    fun findInputId(context: Context): String? = findInput(context)?.id

    fun isOwnInput(input: TvInputInfo): Boolean =
        input.serviceInfo.packageName == IptvInputChannelMetadata.OWNER &&
            input.serviceInfo.name == IptvTvInputService::class.java.name
}
