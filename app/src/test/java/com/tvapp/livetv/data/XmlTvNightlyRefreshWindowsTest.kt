package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Gece yenileme penceresi gecikmesi sözleşmesi. */
class XmlTvNightlyRefreshWindowsTest {
    private val zone = ZoneId.of("Europe/Istanbul")

    private fun at(hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 9, 29, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    @Test
    fun delayFromEveningLandsAtNextMorningWindow() {
        val delay = NightlyRefreshWindows.nextWindowDelayMillis(at(22, 0), 4, zone)
        // 22:00 → ertesi gün 04:00 = 6 saat
        assertEquals(6 * 60 * 60 * 1_000L, delay)
    }

    @Test
    fun delayFromEarlyMorningLandsAtSameDayWindow() {
        val delay = NightlyRefreshWindows.nextWindowDelayMillis(at(1, 30), 4, zone)
        // 01:30 → aynı gün 04:00 = 2,5 saat
        assertEquals((2 * 60 + 30) * 60 * 1_000L, delay)
    }

    @Test
    fun exactWindowTimeRollsToNextDay() {
        val delay = NightlyRefreshWindows.nextWindowDelayMillis(at(4, 0), 4, zone)
        assertEquals(24 * 60 * 60 * 1_000L, delay)
    }

    @Test
    fun delayNeverBelowOneMinute() {
        val delay = NightlyRefreshWindows.nextWindowDelayMillis(at(3, 59), 4, zone)
        assertEquals(60_000L, delay)
    }

    @Test
    fun daylightSavingGapStillProducesPositiveDelay() {
        // Yaz saati uygulaması geçişi olan bir bölgede pencere yine de pozitiftir
        // ve bir sonraki 04:00'e işaret eder (bugün 04:00 geçmişse yarın).
        val zoneWithDst = ZoneId.of("Europe/Berlin")
        val summer = ZonedDateTime.of(2026, 7, 15, 5, 0, 0, 0, zoneWithDst)
            .toInstant().toEpochMilli()
        val delay = NightlyRefreshWindows.nextWindowDelayMillis(summer, 4, zoneWithDst)
        val target = Instant.ofEpochMilli(summer).plusMillis(delay)
            .atZone(zoneWithDst)
        assertEquals(4, target.hour)
        assertTrue(delay > 0)
    }
}
