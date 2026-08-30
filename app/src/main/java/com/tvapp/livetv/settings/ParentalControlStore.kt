package com.tvapp.livetv.settings

import android.content.Context
import org.json.JSONArray
import java.security.MessageDigest

data class ParentalControlSnapshot(
    val pinHash: String?,
    val lockedSourceKeys: Set<String>,
)

class ParentalControlStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "parental-control",
        Context.MODE_PRIVATE,
    )

    fun hasPin(): Boolean = !preferences.getString(KEY_PIN_HASH, null).isNullOrBlank()

    fun setPin(pin: String) {
        require(pin.length == 4 && pin.all(Char::isDigit))
        preferences.edit().putString(KEY_PIN_HASH, hash(pin)).apply()
    }

    fun verify(pin: String): Boolean = preferences.getString(KEY_PIN_HASH, null) == hash(pin)

    fun isLocked(sourceKey: String): Boolean = sourceKey in lockedKeys()

    fun setLocked(sourceKey: String, locked: Boolean) {
        val updated = lockedKeys().toMutableSet().apply {
            if (locked) add(sourceKey) else remove(sourceKey)
        }
        preferences.edit().putString(KEY_LOCKED, JSONArray(updated.toList()).toString()).apply()
    }

    fun snapshot() = ParentalControlSnapshot(
        pinHash = preferences.getString(KEY_PIN_HASH, null),
        lockedSourceKeys = lockedKeys(),
    )

    fun restore(snapshot: ParentalControlSnapshot) {
        preferences.edit()
            .putString(KEY_PIN_HASH, snapshot.pinHash)
            .putString(KEY_LOCKED, JSONArray(snapshot.lockedSourceKeys.toList()).toString())
            .apply()
    }

    private fun lockedKeys(): Set<String> = runCatching {
        val array = JSONArray(preferences.getString(KEY_LOCKED, "[]"))
        (0 until array.length()).mapTo(linkedSetOf()) { array.getString(it) }
    }.getOrDefault(emptySet())

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_PIN_HASH = "pin-hash"
        const val KEY_LOCKED = "locked-source-keys"
    }
}
