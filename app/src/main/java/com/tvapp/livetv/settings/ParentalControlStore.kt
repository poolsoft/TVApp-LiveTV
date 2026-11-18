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

    private var lockedKeysJson: String? = null
    private var lockedKeysCache: Set<String> = emptySet()

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

    private fun lockedKeys(): Set<String> {
        // PERF-028 eki: bu fonksiyon kanal listesindeki her satır bağlanırken ve her odak
        // hareketinde cagriliyor (ChannelEditorAdapter.updateState -> isParentalLocked). Kayitli
        // JSON dizisini her seferinde yeniden cozmek arayuz is parcasinda gereksiz ayrilastirma
        // ve tahsis uretiyordu; cozum yalnizca kayitli metin degistiginde yeniden yapilir.
        //SharedPreferences ayni dosya icin surec icinde tek ornek dondurur ve apply() bile
        //bellekteki degeri hemen gunceller, boylece kullanim yerleri eskisiyle ayni sonuclar
        //gorur.
        val stored = preferences.getString(KEY_LOCKED, "[]")
        if (stored != lockedKeysJson) {
            lockedKeysJson = stored
            lockedKeysCache = runCatching {
                val array = JSONArray(stored)
                (0 until array.length()).mapTo(linkedSetOf()) { array.getString(it) }
            }.getOrDefault(emptySet())
        }
        return lockedKeysCache
    }

    private fun hash(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_PIN_HASH = "pin-hash"
        const val KEY_LOCKED = "locked-source-keys"
    }
}
