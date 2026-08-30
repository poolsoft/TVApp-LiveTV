package com.tvapp.livetv.data

import com.tvapp.livetv.data.local.UserChannelEntity
import com.tvapp.livetv.model.LiveChannel

object ChannelMerger {
    fun merge(
        sourceChannels: List<LiveChannel>,
        preferences: List<UserChannelEntity>,
        includeHidden: Boolean = false,
    ): List<LiveChannel> {
        val preferencesByKey = preferences.associateBy { it.sourceKey }
        val ordered = sourceChannels.map { channel ->
            val preference = preferencesByKey[channel.sourceKey] ?: return@map channel
            channel.copy(
                displayName = preference.customName ?: channel.displayName,
                favorite = preference.favorite,
                hidden = preference.hidden,
                groupId = preference.groupId,
            )
        }.sortedWith(
            compareBy<LiveChannel> { preferencesByKey[it.sourceKey]?.sortOrder ?: Int.MAX_VALUE }
                .thenBy { it.displayNumber.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.displayNumber },
        )
            .mapIndexed { index, channel -> channel.copy(displayNumber = (index + 1).toString()) }

        return if (includeHidden) ordered else ordered.filterNot { it.hidden }
    }
}
