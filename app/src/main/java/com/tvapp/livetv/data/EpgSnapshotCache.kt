package com.tvapp.livetv.data

import java.util.concurrent.atomic.AtomicLong

internal data class CurrentProgramLookup(
    val found: Boolean,
    val program: ProgramSummary?,
)

/**
 * Per-channel EPG snapshot cache (now/next + current program). Phase 1 OPT-1.7:
 * invalidation is selective — EPG data changes invalidate only the affected
 * channels instead of dropping the whole cache, so channel-list rows keep
 * their warm snapshots while a single channel's metadata or an EPG source
 * updates.
 */
internal object EpgSnapshotCache {
    internal const val NEGATIVE_CACHE_MS = 60_000L
    private const val MINIMUM_CACHE_MS = 5_000L
    private const val POSITIVE_CACHE_MAX_MS = 5 * 60_000L
    private const val MAX_ENTRIES = 256

    private data class Entry<T>(val value: T, val expiresAtMillis: Long)

    private val current = LinkedHashMap<String, Entry<ProgramSummary?>>(32, 0.75f, true)
    private val nowNext = LinkedHashMap<String, Entry<NowNextPrograms>>(32, 0.75f, true)

    /** Monotonic EPG-data version; bumped on any bulk EPG data change. */
    private val dataVersion = AtomicLong(0L)

    /** sourceKey -> data version the entry was computed against. */
    private val currentVersions = HashMap<String, Long>()
    private val nowNextVersions = HashMap<String, Long>()

    @Synchronized
    fun current(sourceKey: String, now: Long): CurrentProgramLookup {
        val entry = current[sourceKey]
        if (entry == null) return CurrentProgramLookup(false, null)
        if (entry.expiresAtMillis <= now) {
            removeEntry(current, sourceKey, currentVersions)
            return CurrentProgramLookup(false, null)
        }
        if (currentVersions[sourceKey] != dataVersion.get()) {
            removeEntry(current, sourceKey, currentVersions)
            return CurrentProgramLookup(false, null)
        }
        return CurrentProgramLookup(true, entry.value)
    }

    @Synchronized
    fun nowNext(sourceKey: String, now: Long): NowNextPrograms? {
        val entry = nowNext[sourceKey]
        if (entry == null) return null
        if (entry.expiresAtMillis <= now) {
            removeEntry(nowNext, sourceKey, nowNextVersions)
            return null
        }
        if (nowNextVersions[sourceKey] != dataVersion.get()) {
            removeEntry(nowNext, sourceKey, nowNextVersions)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun putCurrent(sourceKey: String, program: ProgramSummary?, now: Long) {
        stamp(current, sourceKey, currentVersions, Entry(program, expiry(program, now)))
        val existing = nowNext[sourceKey]
        if (existing != null && existing.expiresAtMillis > now &&
            nowNextVersions[sourceKey] == dataVersion.get()
        ) {
            val merged = existing.value.copy(current = program)
            stamp(nowNext, sourceKey, nowNextVersions, Entry(merged, nowNextExpiry(merged, now)))
        }
        trim(current)
    }

    @Synchronized
    fun putNowNext(sourceKey: String, programs: NowNextPrograms, now: Long) {
        stamp(nowNext, sourceKey, nowNextVersions, Entry(programs, nowNextExpiry(programs, now)))
        stamp(current, sourceKey, currentVersions, Entry(programs.current, expiry(programs.current, now)))
        trim(nowNext)
        trim(current)
    }

    /** Drops one channel's snapshots (e.g. EPG id override changed). */
    @Synchronized
    fun invalidate(sourceKey: String) {
        removeEntry(current, sourceKey, currentVersions)
        removeEntry(nowNext, sourceKey, nowNextVersions)
    }

    /**
     * Bulk invalidation for EPG data changes. Cache reads bump the entry's
     * recency (access-order maps); bumping the version once is enough — the
     * snapshot contents did not change, so every entry is stale until
     * recomputed. Very cheap: two map clears and a counter increment.
     */
    @Synchronized
    fun invalidateAll() {
        current.clear()
        nowNext.clear()
        currentVersions.clear()
        nowNextVersions.clear()
        dataVersion.incrementAndGet()
    }

    @Synchronized
    fun clear() {
        invalidateAll()
    }

    @Synchronized
    internal fun cachedSnapshotCount(): Int = current.size + nowNext.size

    private fun <T> stamp(
        cache: MutableMap<String, Entry<T>>,
        sourceKey: String,
        versions: MutableMap<String, Long>,
        entry: Entry<T>,
    ) {
        cache[sourceKey] = entry
        versions[sourceKey] = dataVersion.get()
    }

    private fun <T> removeEntry(
        cache: MutableMap<String, Entry<T>>,
        sourceKey: String,
        versions: MutableMap<String, Long>,
    ) {
        cache.remove(sourceKey)
        versions.remove(sourceKey)
    }

    private fun expiry(program: ProgramSummary?, now: Long): Long = program?.endTimeMillis
        ?.coerceAtMost(now + POSITIVE_CACHE_MAX_MS)
        ?.coerceAtLeast(now + MINIMUM_CACHE_MS)
        ?: now + NEGATIVE_CACHE_MS

    private fun nowNextExpiry(programs: NowNextPrograms, now: Long): Long {
        val boundary = sequenceOf(
            programs.current?.endTimeMillis,
            programs.next?.startTimeMillis,
        ).filterNotNull().filter { it > now }.minOrNull()
        return boundary
            ?.coerceAtMost(now + POSITIVE_CACHE_MAX_MS)
            ?.coerceAtLeast(now + MINIMUM_CACHE_MS)
            ?: now + NEGATIVE_CACHE_MS
    }

    private fun <T> trim(cache: LinkedHashMap<String, Entry<T>>) {
        while (cache.size > MAX_ENTRIES) cache.remove(cache.entries.first().key)
    }
}
