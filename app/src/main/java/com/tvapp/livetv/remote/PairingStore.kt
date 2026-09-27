package com.tvapp.livetv.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * REMOTEEDIT-002: pairing and authorization state.
 *
 * The 6-digit code is single-use, valid for [CODE_TTL_MILLIS] and locks new
 * attempts for [LOCKOUT_MILLIS] after [MAX_ATTEMPTS] wrong entries. Devices
 * are stored as SHA-256 token hashes — the plain token is never persisted and
 * never logged. The current pairing code lives in EncryptedSharedPreferences
 * so it is not kept in a world-readable store.
 */
class PairingStore(context: Context) {

    data class Session(
        val deviceId: Long,
        val deviceName: String,
        val tokenPlain: String,
    )

    data class DeviceInfo(
        val id: Long,
        val deviceName: String,
        val pairedAt: Long,
        val lastSeenAt: Long,
    )

    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "remote_edit_pairing",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val random = SecureRandom()
    private val failedAttempts = AtomicInteger(0)

    @Synchronized
    fun currentCode(now: Long = System.currentTimeMillis()): String? {
        val code = preferences.getString(KEY_CODE, null) ?: return null
        val expiresAt = preferences.getLong(KEY_CODE_EXPIRES_AT, 0L)
        if (now >= expiresAt) {
            preferences.edit().remove(KEY_CODE).remove(KEY_CODE_EXPIRES_AT).apply()
            return null
        }
        return code
    }

    /** Generates a fresh 6-digit code, replacing any previous one. */
    @Synchronized
    fun generateCode(now: Long = System.currentTimeMillis()): String {
        val code = String.format(Locale.US, "%06d", random.nextInt(1_000_000))
        preferences.edit()
            .putString(KEY_CODE, code)
            .putLong(KEY_CODE_EXPIRES_AT, now + CODE_TTL_MILLIS)
            .apply()
        failedAttempts.set(0)
        return code
    }

    @Synchronized
    fun revokeCode() {
        preferences.edit().remove(KEY_CODE).remove(KEY_CODE_EXPIRES_AT).apply()
        failedAttempts.set(0)
    }

    @Synchronized
    fun lockedUntil(now: Long = System.currentTimeMillis()): Long {
        val until = preferences.getLong(KEY_LOCKED_UNTIL, 0L)
        return if (until > now) until else 0L
    }

    @Synchronized
    fun attemptPair(
        submittedCode: String,
        deviceName: String,
        now: Long = System.currentTimeMillis(),
    ): Session? {
        if (lockedUntil(now) > 0L) return null
        val expected = currentCode(now) ?: return null
        if (!constantTimeEquals(expected, submittedCode.trim())) {
            val failures = failedAttempts.incrementAndGet()
            if (failures >= MAX_ATTEMPTS) {
                preferences.edit().putLong(KEY_LOCKED_UNTIL, now + LOCKOUT_MILLIS).apply()
                revokeCode()
            }
            return null
        }
        failedAttempts.set(0)
        preferences.edit().remove(KEY_CODE).remove(KEY_CODE_EXPIRES_AT).apply()
        return registerDevice(deviceName, now)
    }

    @Synchronized
    fun registerDevice(deviceName: String, now: Long = System.currentTimeMillis()): Session {
        val token = generateToken()
        val id = preferences.getLong(KEY_NEXT_ID, 1L)
        preferences.edit()
            .putLong(KEY_NEXT_ID, id + 1)
            .putString(tokenHashKey(token), "$id|$deviceName|$now|$now")
            .apply()
        return Session(deviceId = id, deviceName = deviceName, tokenPlain = token)
    }

    @Synchronized
    fun devices(now: Long = System.currentTimeMillis()): List<DeviceInfo> = preferences.all.entries
        .filter { it.key.startsWith(HASH_PREFIX) }
        .mapNotNull { entry ->
            val parts = (entry.value as? String)?.split('|') ?: return@mapNotNull null
            if (parts.size < 4) return@mapNotNull null
            DeviceInfo(
                id = parts[0].toLongOrNull() ?: return@mapNotNull null,
                deviceName = parts[1],
                pairedAt = parts[2].toLongOrNull() ?: return@mapNotNull null,
                lastSeenAt = parts[3].toLongOrNull() ?: return@mapNotNull null,
            )
        }
        .sortedBy(DeviceInfo::id)

    @Synchronized
    fun removeDevice(deviceId: Long): Boolean {
        val key = preferences.all.entries.firstOrNull { entry ->
            entry.key.startsWith(HASH_PREFIX) &&
                (entry.value as? String)?.startsWith("$deviceId|") == true
        }?.key ?: return false
        preferences.edit().remove(key).apply()
        return true
    }

    /** Returns true and refreshes lastSeenAt when the bearer token is valid. */
    @Synchronized
    fun authorize(token: String, now: Long = System.currentTimeMillis()): Boolean {
        if (token.isBlank()) return false
        val hash = tokenHashKey(token)
        val stored = preferences.getString(hash, null) ?: return false
        val parts = stored.split('|')
        if (parts.size < 4) return false
        preferences.edit().putString(hash, "${parts[0]}|${parts[1]}|${parts[2]}|$now").apply()
        return true
    }

    private fun generateToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return bytes.joinToString(separator = "") { String.format(Locale.US, "%02x", it) }
    }

    private fun tokenHashKey(token: String): String =
        HASH_PREFIX + sha256Hex(token)

    private fun constantTimeEquals(expected: String, submitted: String): Boolean =
        MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            submitted.toByteArray(Charsets.UTF_8),
        )

    companion object {
        internal const val CODE_TTL_MILLIS = 5 * 60_000L
        internal const val LOCKOUT_MILLIS = 5 * 60_000L
        internal const val MAX_ATTEMPTS = 3
        private const val TOKEN_BYTES = 32
        private const val KEY_CODE = "pairing_code"
        private const val KEY_CODE_EXPIRES_AT = "pairing_code_expires_at"
        private const val KEY_LOCKED_UNTIL = "pairing_locked_until"
        private const val KEY_NEXT_ID = "next_device_id"
        private const val HASH_PREFIX = "token_hash_"

        internal fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { String.format(Locale.US, "%02x", it) }
    }
}
