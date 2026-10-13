package com.tvapp.livetv.web

internal class WebSessionTimeout(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private var lastActivityMillis = nowMillis()

    @Synchronized
    fun touch() {
        lastActivityMillis = nowMillis()
    }

    @Synchronized
    fun isExpired(operationBusy: Boolean): Boolean {
        val now = nowMillis()
        if (operationBusy) lastActivityMillis = now
        return now - lastActivityMillis >= IDLE_TIMEOUT_MILLIS
    }

    companion object {
        const val IDLE_TIMEOUT_MILLIS = 15 * 60_000L
    }
}
