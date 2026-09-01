package com.tvapp.livetv.settings

import android.content.Context

enum class InfoBarPosition { TOP, BOTTOM }
enum class ChannelPanelSide { LEFT, RIGHT }

data class DisplayPreferences(
    val infoBarPosition: InfoBarPosition = InfoBarPosition.TOP,
    val showCurrentProgram: Boolean = true,
    val showNextProgram: Boolean = true,
    val showChannelLogo: Boolean = true,
    val showChannelProgram: Boolean = true,
    val showChannelProgress: Boolean = true,
    val showChannelSourceBadge: Boolean = false,
    val channelPanelSide: ChannelPanelSide = ChannelPanelSide.LEFT,
    val infoBarOpacityPercent: Int = 90,
    val channelPanelOpacityPercent: Int = 90,
    val infoBarDurationSeconds: Int = 6,
    val channelFocusAutoTune: Boolean = true,
    val channelFocusTuneDelayMillis: Int = 1_500,
    val subtitlesEnabled: Boolean = false,
    val launchOnBoot: Boolean = false,
    val preferredAudioLanguage: String? = null,
    val preferredSubtitleLanguage: String? = null,
)

class DisplayPreferencesStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "display-preferences",
        Context.MODE_PRIVATE,
    )

    fun load(): DisplayPreferences = DisplayPreferences(
        infoBarPosition = enumValueOrDefault(
            preferences.getString(KEY_INFO_POSITION, null),
            InfoBarPosition.TOP,
        ),
        showCurrentProgram = preferences.getBoolean(KEY_SHOW_CURRENT, true),
        showNextProgram = preferences.getBoolean(KEY_SHOW_NEXT, true),
        showChannelLogo = preferences.getBoolean(KEY_SHOW_CHANNEL_LOGO, true),
        showChannelProgram = preferences.getBoolean(KEY_SHOW_CHANNEL_PROGRAM, true),
        showChannelProgress = preferences.getBoolean(KEY_SHOW_CHANNEL_PROGRESS, true),
        showChannelSourceBadge = preferences.getBoolean(KEY_SHOW_SOURCE_BADGE, false),
        channelPanelSide = enumValueOrDefault(
            preferences.getString(KEY_PANEL_SIDE, null),
            ChannelPanelSide.LEFT,
        ),
        infoBarOpacityPercent = preferences.getInt(
            KEY_INFO_OPACITY,
            preferences.getInt(KEY_LEGACY_OPACITY, 90),
        ).coerceIn(30, 100),
        channelPanelOpacityPercent = preferences.getInt(
            KEY_PANEL_OPACITY,
            preferences.getInt(KEY_LEGACY_OPACITY, 90),
        ).coerceIn(30, 100),
        infoBarDurationSeconds = preferences.getInt(KEY_DURATION, 6).coerceIn(0, 15),
        channelFocusAutoTune = preferences.getBoolean(KEY_CHANNEL_FOCUS_AUTO_TUNE, true),
        channelFocusTuneDelayMillis = preferences.getInt(
            KEY_CHANNEL_FOCUS_TUNE_DELAY,
            1_500,
        ).coerceIn(500, 5_000),
        subtitlesEnabled = preferences.getBoolean(KEY_SUBTITLES_ENABLED, false),
        launchOnBoot = preferences.getBoolean(KEY_LAUNCH_ON_BOOT, false),
        preferredAudioLanguage = preferences.getString(KEY_AUDIO_LANGUAGE, null),
        preferredSubtitleLanguage = preferences.getString(KEY_SUBTITLE_LANGUAGE, null),
    )

    fun save(displayPreferences: DisplayPreferences) {
        preferences.edit()
            .putString(KEY_INFO_POSITION, displayPreferences.infoBarPosition.name)
            .putBoolean(KEY_SHOW_CURRENT, displayPreferences.showCurrentProgram)
            .putBoolean(KEY_SHOW_NEXT, displayPreferences.showNextProgram)
            .putBoolean(KEY_SHOW_CHANNEL_LOGO, displayPreferences.showChannelLogo)
            .putBoolean(KEY_SHOW_CHANNEL_PROGRAM, displayPreferences.showChannelProgram)
            .putBoolean(KEY_SHOW_CHANNEL_PROGRESS, displayPreferences.showChannelProgress)
            .putBoolean(KEY_SHOW_SOURCE_BADGE, displayPreferences.showChannelSourceBadge)
            .putString(KEY_PANEL_SIDE, displayPreferences.channelPanelSide.name)
            .putInt(KEY_INFO_OPACITY, displayPreferences.infoBarOpacityPercent.coerceIn(30, 100))
            .putInt(
                KEY_PANEL_OPACITY,
                displayPreferences.channelPanelOpacityPercent.coerceIn(30, 100),
            )
            .putInt(KEY_DURATION, displayPreferences.infoBarDurationSeconds.coerceIn(0, 15))
            .putBoolean(KEY_CHANNEL_FOCUS_AUTO_TUNE, displayPreferences.channelFocusAutoTune)
            .putInt(
                KEY_CHANNEL_FOCUS_TUNE_DELAY,
                displayPreferences.channelFocusTuneDelayMillis.coerceIn(500, 5_000),
            )
            .putBoolean(KEY_SUBTITLES_ENABLED, displayPreferences.subtitlesEnabled)
            .putBoolean(KEY_LAUNCH_ON_BOOT, displayPreferences.launchOnBoot)
            .putString(KEY_AUDIO_LANGUAGE, displayPreferences.preferredAudioLanguage)
            .putString(KEY_SUBTITLE_LANGUAGE, displayPreferences.preferredSubtitleLanguage)
            .apply()
    }

    fun updateTrackPreferences(
        subtitlesEnabled: Boolean? = null,
        audioLanguage: String? = null,
        subtitleLanguage: String? = null,
    ) {
        preferences.edit().apply {
            subtitlesEnabled?.let { putBoolean(KEY_SUBTITLES_ENABLED, it) }
            audioLanguage?.let { putString(KEY_AUDIO_LANGUAGE, it) }
            subtitleLanguage?.let { putString(KEY_SUBTITLE_LANGUAGE, it) }
        }.apply()
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private companion object {
        const val KEY_INFO_POSITION = "info-position"
        const val KEY_SHOW_CURRENT = "show-current-program"
        const val KEY_SHOW_NEXT = "show-next-program"
        const val KEY_SHOW_CHANNEL_LOGO = "show-channel-logo"
        const val KEY_SHOW_CHANNEL_PROGRAM = "show-channel-program"
        const val KEY_SHOW_CHANNEL_PROGRESS = "show-channel-progress"
        const val KEY_SHOW_SOURCE_BADGE = "show-channel-source-badge"
        const val KEY_PANEL_SIDE = "channel-panel-side"
        const val KEY_INFO_OPACITY = "info-opacity"
        const val KEY_PANEL_OPACITY = "panel-opacity"
        const val KEY_LEGACY_OPACITY = "overlay-opacity"
        const val KEY_DURATION = "info-duration"
        const val KEY_CHANNEL_FOCUS_AUTO_TUNE = "channel-focus-auto-tune"
        const val KEY_CHANNEL_FOCUS_TUNE_DELAY = "channel-focus-tune-delay"
        const val KEY_SUBTITLES_ENABLED = "subtitles-enabled"
        const val KEY_LAUNCH_ON_BOOT = "launch-on-boot"
        const val KEY_AUDIO_LANGUAGE = "audio-language"
        const val KEY_SUBTITLE_LANGUAGE = "subtitle-language"
    }
}
