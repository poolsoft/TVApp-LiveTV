package com.tvapp.livetv.data

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** Gece yenileme penceresi hesabı: saf java.time, Android çatısına bağlı değil.
 *  JobInfo kurulumu (minimumLatency + overrideDeadline) bu değere dayanır. */
internal object NightlyRefreshWindows {
    /** `nowMillis`'ten sonraki `startHour`:00 yerel penceresine kalan süre.
     *  Pencere tam o anda ise bir sonraki güne kayar; sonuç en az bir dakikadır. */
    fun nextWindowDelayMillis(
        nowMillis: Long,
        startHour: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone)
        var next = now.toLocalDate().atTime(startHour, 0).atZone(zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now.toInstant(), next.toInstant()).toMillis()
            .coerceAtLeast(60_000L)
    }
}
