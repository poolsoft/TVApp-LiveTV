package com.tvapp.livetv.data

internal data class CurrentProgramLookup(
    val found: Boolean,
    val program: ProgramSummary?,
)

internal object EpgSnapshotCache {
    private const val NEGATIVE_CACHE_MS = 2_000L
    private const val MINIMUM_CACHE_MS = 5_000L
    private const val POSITIVE_CACHE_MAX_MS = 5 * 60_000L
    private const val MAX_ENTRIES = 256

    private data class Entry<T>(val value: T, val expiresAtMillis: Long)

    private val current = LinkedHashMap<String, Entry<ProgramSummary?>>(32, 0.75f, true)
    private val nowNext = LinkedHashMap<String, Entry<NowNextPrograms>>(32, 0.75f, true)

    @Synchronized
    fun current(sourceKey: String, now: Long): CurrentProgramLookup {
        val entry = current[sourceKey]
        if (entry == null) return CurrentProgramLookup(false, null)
        if (entry.expiresAtMillis <= now) {
            current.remove(sourceKey)
            return CurrentProgramLookup(false, null)
        }
        return CurrentProgramLookup(true, entry.value)
    }

    @Synchronized
    fun nowNext(sourceKey: String, now: Long): NowNextPrograms? {
        val entry = nowNext[sourceKey] ?: return null
        if (entry.expiresAtMillis <= now) {
            nowNext.remove(sourceKey)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun putCurrent(sourceKey: String, program: ProgramSummary?, now: Long) {
        current[sourceKey] = Entry(program, expiry(program, now))
        val existing = nowNext[sourceKey]
        if (existing != null && existing.expiresAtMillis > now) {
            val merged = existing.value.copy(current = program)
            nowNext[sourceKey] = Entry(merged, nowNextExpiry(merged, now))
        }
        trim(current)
    }

    @Synchronized
    fun putNowNext(sourceKey: String, programs: NowNextPrograms, now: Long) {
        nowNext[sourceKey] = Entry(programs, nowNextExpiry(programs, now))
        current[sourceKey] = Entry(programs.current, expiry(programs.current, now))
        trim(nowNext)
        trim(current)
    }

    @Synchronized
    fun invalidate(sourceKey: String) {
        current.remove(sourceKey)
        nowNext.remove(sourceKey)
    }

    @Synchronized
    fun clear() {
        current.clear()
        nowNext.clear()
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
