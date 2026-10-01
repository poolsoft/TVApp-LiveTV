package com.tvapp.livetv.settings

data class IptvSourcePlaybackOptions(
    val liveBufferSeconds: Int? = null,
    val vodBufferSeconds: Int? = null,
    val maximumVideoHeight: Int? = null,
    val automaticRecovery: Boolean? = null,
) {
    fun resolve(global: IptvPlaybackPreferences, isVod: Boolean): EffectiveIptvPlaybackOptions =
        EffectiveIptvPlaybackOptions(
            bufferSeconds = (if (isVod) vodBufferSeconds else liveBufferSeconds)
                ?.takeIf { it in IptvPlaybackPreferences.BUFFER_OPTIONS } ?: global.targetBufferSeconds,
            maximumVideoHeight = maximumVideoHeight
                ?.takeIf { it in IptvPlaybackPreferences.QUALITY_HEIGHT_OPTIONS } ?: global.maximumVideoHeight,
            automaticRecovery = automaticRecovery ?: global.automaticRecovery,
        )
}

data class EffectiveIptvPlaybackOptions(
    val bufferSeconds: Int,
    val maximumVideoHeight: Int,
    val automaticRecovery: Boolean,
)
