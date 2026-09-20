package com.tvapp.livetv.image

/**
 * Basarisiz olan logo isteklerini hafizada tutan, sinirli kapasiteye (LRU)
 * ve zaman asimina (TTL) sahip onbellek mekanizmasi.
 *
 * Gecici ag kopmalarinda logolarin sonsuza kadar engellenmesini onler.
 */
internal class FailedLogoTracker(
    private val maxEntries: Int = MAX_ENTRIES,
    private val ttlMillis: Long = TTL_MILLIS,
) {
    // accessOrder = true sayesinde en son erisilenler arkada, en eski kalanlar basta durur
    private val entries = LinkedHashMap<String, Long>(maxEntries, 0.75f, true)

    @Synchronized
    fun isFailed(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val failedAt = entries[key] ?: return false
        if (now - failedAt >= ttlMillis) {
            entries.remove(key)
            return false
        }
        return true
    }

    @Synchronized
    fun recordFailure(key: String, now: Long = System.currentTimeMillis()) {
        entries[key] = now
        trim()
    }

    @Synchronized
    fun recordSuccess(key: String) {
        entries.remove(key)
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }

    @Synchronized
    internal fun size(): Int = entries.size

    private fun trim() {
        while (entries.size > maxEntries) {
            val oldestKey = entries.entries.iterator().next().key
            entries.remove(oldestKey)
        }
    }

    companion object {
        const val MAX_ENTRIES = 200
        const val TTL_MILLIS = 5 * 60 * 1_000L // 5 dakika
    }
}
