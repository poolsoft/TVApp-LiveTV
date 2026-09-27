package com.tvapp.livetv.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * REMOTEEDIT-003: phone-side client state. The paired token and the server
 * address live in EncryptedSharedPreferences; edits made while offline are
 * kept in a small JSON queue file and replayed on sync. The queue lives in
 * [queueFile] which is injectable for unit tests.
 */
class RemoteEditClientStore(
    context: Context,
    private val queueFile: File = File(context.applicationContext.filesDir, QUEUE_FILE_NAME),
) {
    data class PendingOp(
        val sourceKey: String,
        val revision: Long,
        val patch: JSONObject,
    )

    private val preferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        "remote_edit_client",
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Volatile
    private var queueCache: List<PendingOp>? = null

    fun address(): String = preferences.getString(KEY_ADDRESS, null).orEmpty()

    fun setAddress(address: String) {
        preferences.edit().putString(KEY_ADDRESS, address.trim()).apply()
    }

    fun token(): String = preferences.getString(KEY_TOKEN, null).orEmpty()

    fun setToken(token: String) {
        preferences.edit().putString(KEY_TOKEN, token.trim()).apply()
    }

    fun clearSession() {
        preferences.edit().remove(KEY_TOKEN).apply()
    }

    @Synchronized
    fun pendingOps(): List<PendingOp> {
        if (queueCache != null) return queueCache!!
        if (!queueFile.exists()) return emptyList()
        val ops = runCatching {
            val array = JSONArray(queueFile.readText())
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val patch = item.optJSONObject("patch") ?: return@mapNotNull null
                PendingOp(
                    sourceKey = item.optString("sourceKey"),
                    revision = item.optLong("revision"),
                    patch = patch,
                )
            }
        }.getOrDefault(emptyList())
        queueCache = ops
        return ops
    }

    @Synchronized
    fun enqueue(op: PendingOp) {
        val ops = pendingOps() + op
        persistQueue(ops)
    }

    /** Removes successfully applied ops; keeps conflicts for user review. */
    @Synchronized
    fun retainFailed(ops: List<PendingOp>) {
        persistQueue(ops)
    }

    @Synchronized
    fun clearQueue() {
        persistQueue(emptyList())
    }

    private fun persistQueue(ops: List<PendingOp>) {
        queueCache = ops
        val array = JSONArray()
        ops.forEach { op ->
            array.put(
                JSONObject()
                    .put("sourceKey", op.sourceKey)
                    .put("revision", op.revision)
                    .put("patch", op.patch),
            )
        }
        queueFile.writeText(array.toString())
    }

    companion object {
        private const val QUEUE_FILE_NAME = "remote_edit_queue.json"
        private const val KEY_ADDRESS = "server_address"
        private const val KEY_TOKEN = "server_token"
    }
}
