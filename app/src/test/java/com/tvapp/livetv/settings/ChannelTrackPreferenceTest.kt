package com.tvapp.livetv.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelTrackPreferenceTest {

    @Test
    fun `global preferences are used when no channel preference exists`() {
        val resolved = resolveTrackPreferences(
            channel = null,
            globalAudioLanguage = "tur",
            globalSubtitlesEnabled = true,
            globalSubtitleLanguage = "eng",
        )
        assertEquals("tur", resolved.audioLanguage)
        assertTrue(resolved.subtitlesEnabled)
        assertEquals("eng", resolved.subtitleLanguage)
    }

    @Test
    fun `channel preference overrides global audio and subtitle language`() {
        val resolved = resolveTrackPreferences(
            channel = ChannelTrackPreference(audioLanguage = "ger", subtitleLanguage = "fra"),
            globalAudioLanguage = "tur",
            globalSubtitlesEnabled = true,
            globalSubtitleLanguage = "eng",
        )
        assertEquals("ger", resolved.audioLanguage)
        assertEquals("fra", resolved.subtitleLanguage)
    }

    @Test
    fun `channel can disable subtitles while global keeps them enabled`() {
        val resolved = resolveTrackPreferences(
            channel = ChannelTrackPreference(subtitlesEnabled = false),
            globalAudioLanguage = null,
            globalSubtitlesEnabled = true,
            globalSubtitleLanguage = "eng",
        )
        assertFalse(resolved.subtitlesEnabled)
    }

    @Test
    fun `channel can enable subtitles while global keeps them disabled`() {
        val resolved = resolveTrackPreferences(
            channel = ChannelTrackPreference(subtitlesEnabled = true),
            globalAudioLanguage = null,
            globalSubtitlesEnabled = false,
            globalSubtitleLanguage = null,
        )
        assertTrue(resolved.subtitlesEnabled)
    }

    @Test
    fun `unset channel fields fall back to global values`() {
        val resolved = resolveTrackPreferences(
            channel = ChannelTrackPreference(),
            globalAudioLanguage = "tur",
            globalSubtitlesEnabled = false,
            globalSubtitleLanguage = "eng",
        )
        assertEquals("tur", resolved.audioLanguage)
        assertFalse(resolved.subtitlesEnabled)
        assertEquals("eng", resolved.subtitleLanguage)
    }
}
