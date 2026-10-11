package com.tvapp.livetv.data

/** Caches Stalker portal handshake tokens per endpoint+MAC so every channel
 *  open (including queued live continuations) does not re-handshake the
 *  portal. Entries expire after TOKEN_TTL_MS; a rejected or stale token
 *  simply falls back to a fresh handshake in StalkerClient. The map is
 *  bounded by the number of configured portals, which is small.
 *  ponytail: single-process in-memory cache; add expiry eviction only if a
 *  user ever configures many portals. */
internal object StalkerSessionStore {
    internal const val TOKEN_TTL_MS = 3 * 60_000L

    private class Entry(val token: String, val expiresAt: Long)

    private val entries = HashMap<String, Entry>()

    internal var nowMillis: () -> Long = { System.currentTimeMillis() }

    internal fun reset() = synchronized(entries) { entries.clear() }

    fun token(endpoint: String, mac: String): String? = synchronized(entries) {
        entries[key(endpoint, mac)]?.takeIf { it.expiresAt > nowMillis() }?.token
    }

    fun put(endpoint: String, mac: String, token: String) {
        if (token.isBlank()) return
        synchronized(entries) {
            entries[key(endpoint, mac)] = Entry(token, nowMillis() + TOKEN_TTL_MS)
        }
    }

    private fun key(endpoint: String, mac: String) = "$endpoint|$mac"
}

