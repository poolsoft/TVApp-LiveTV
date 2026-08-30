package com.tvapp.livetv.data

import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.settings.ChannelSourceFilterStore

class TifRepository(context: Context) {
    private val appContext = context.applicationContext
    private val inputManager = appContext.getSystemService(TvInputManager::class.java)
    private val sourceFilterStore = ChannelSourceFilterStore(appContext)
    private val debugLog = CrashReportStore(appContext)

    fun inputs(): List<TvInputInfo> = inputManager?.tvInputList.orEmpty()

    fun tunerInputs(): List<TvInputInfo> = inputs().filter { input ->
        input.type == TvInputInfo.TYPE_TUNER && "/HW" in input.id
    }

    fun channels(
        forceRefresh: Boolean = false,
        saveDiagnostics: Boolean = false,
    ): Result<List<LiveChannel>> = runCatching {
        val availableInputIds = tunerInputs().mapTo(mutableSetOf()) { it.id }
        val tunerInputIds = sourceFilterStore.enabledInputIds(availableInputIds)
        val cacheKey = tunerInputIds.sorted().joinToString("|")
        if (!forceRefresh && !saveDiagnostics && cachedInputKey == cacheKey) {
            cachedChannels?.let { return@runCatching it }
        }
        appContext.contentResolver.query(
            TvContract.Channels.CONTENT_URI,
            null,
            null,
            null,
            TvContract.Channels.COLUMN_DISPLAY_NUMBER,
        )?.use { cursor ->
            debugLog.recordDebug("TIF_CHANNEL_COLUMNS | ${cursor.columnNames.joinToString()}")
            val idIndex = cursor.getColumnIndexOrThrow(TvContract.Channels._ID)
            val inputIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_INPUT_ID)
            val numberIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_DISPLAY_NUMBER)
            val nameIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_DISPLAY_NAME)
            val videoFormatIndex = cursor.getColumnIndexOrThrow(
                TvContract.Channels.COLUMN_VIDEO_FORMAT,
            )
            val serviceTypeIndex = cursor.getColumnIndexOrThrow(
                TvContract.Channels.COLUMN_SERVICE_TYPE,
            )
            val lockedIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_LOCKED)
            val encryptedIndex = ENCRYPTED_COLUMN_CANDIDATES
                .firstNotNullOfOrNull { name -> cursor.getColumnIndex(name).takeIf { it >= 0 } }
            val providerDataIndex = cursor.getColumnIndex(
                TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA,
            )
            val browsableIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_BROWSABLE)
            val networkIdIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID)
            val streamIdIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID)
            val serviceIdIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_SERVICE_ID)
            val providerIdIndex = cursor.getColumnIndexOrThrow(TvContract.Channels.COLUMN_INTERNAL_PROVIDER_ID)

            val diagnosticRows = if (saveDiagnostics) mutableListOf<String>() else null
            val loadedChannels = buildList {
                while (cursor.moveToNext()) {
                    diagnosticRows?.add(cursor.diagnosticRow())
                    if (cursor.getInt(browsableIndex) != 1) continue
                    val inputId = cursor.getString(inputIndex)
                    if (inputId !in tunerInputIds) continue
                    val id = cursor.getLong(idIndex)
                    val networkId = cursor.getInt(networkIdIndex)
                    val streamId = cursor.getInt(streamIdIndex)
                    val serviceId = cursor.getInt(serviceIdIndex)
                    val providerId = cursor.getString(providerIdIndex).orEmpty()
                    add(
                        LiveChannel(
                            id = id,
                            sourceKey = stableSourceKey(
                                inputId = inputId,
                                channelId = id,
                                networkId = networkId,
                                streamId = streamId,
                                serviceId = serviceId,
                                providerId = providerId,
                            ),
                            inputId = inputId,
                            displayNumber = cursor.getString(numberIndex).orEmpty(),
                            displayName = cursor.getString(nameIndex).orEmpty().ifBlank { "Kanal $id" },
                            uri = TvContract.buildChannelUri(id).toString(),
                            videoFormat = cursor.getString(videoFormatIndex),
                            serviceType = cursor.getString(serviceTypeIndex),
                            locked = cursor.getInt(lockedIndex) == 1,
                            encrypted = cursor.isEncrypted(encryptedIndex, providerDataIndex),
                        ),
                    )
                }
            }
            if (diagnosticRows != null) {
                debugLog.saveTifDiagnostics(
                    buildString {
                        appendLine("TVAPP TIF CHANNEL DIAGNOSTICS")
                        appendLine("Columns (${cursor.columnCount}): ${cursor.columnNames.joinToString()}")
                        appendLine("Rows (${diagnosticRows.size}):")
                        diagnosticRows.forEach { appendLine(it) }
                    },
                )
            }
            cachedInputKey = cacheKey
            cachedChannels = loadedChannels
            loadedChannels
        }.orEmpty()
    }

    private fun stableSourceKey(
        inputId: String,
        channelId: Long,
        networkId: Int,
        streamId: Int,
        serviceId: Int,
        providerId: String,
    ): String = when {
        serviceId > 0 -> "tif:$inputId:dvb:$networkId:$streamId:$serviceId"
        providerId.isNotBlank() -> "tif:$inputId:provider:$providerId"
        else -> "tif:$inputId:channel:$channelId"
    }

    private fun Cursor.isEncrypted(explicitIndex: Int?, providerDataIndex: Int): Boolean {
        if (explicitIndex != null && !isNull(explicitIndex)) {
            val explicitValue = when (getType(explicitIndex)) {
                Cursor.FIELD_TYPE_INTEGER -> getLong(explicitIndex) != 0L
                Cursor.FIELD_TYPE_STRING -> getString(explicitIndex).isTruthyEncryptionValue()
                else -> false
            }
            if (explicitValue) return true
        }
        if (providerDataIndex < 0 || isNull(providerDataIndex)) return false
        val providerData = runCatching {
            when (getType(providerDataIndex)) {
                Cursor.FIELD_TYPE_BLOB -> String(getBlob(providerDataIndex), Charsets.UTF_8)
                Cursor.FIELD_TYPE_STRING -> getString(providerDataIndex)
                else -> ""
            }
        }.getOrDefault("")
        return ENCRYPTION_MARKER.containsMatchIn(providerData)
    }

    private fun Cursor.diagnosticRow(): String = columnNames.mapIndexed { index, name ->
        val value = runCatching {
            when (getType(index)) {
                Cursor.FIELD_TYPE_NULL -> "null"
                Cursor.FIELD_TYPE_INTEGER -> getLong(index).toString()
                Cursor.FIELD_TYPE_FLOAT -> getDouble(index).toString()
                Cursor.FIELD_TYPE_STRING -> getString(index).diagnosticValue()
                Cursor.FIELD_TYPE_BLOB -> {
                    val bytes = getBlob(index)
                    val hex = bytes.take(DIAGNOSTIC_BLOB_BYTES)
                        .joinToString("") { byte -> "%02x".format(byte) }
                    val text = String(bytes.take(DIAGNOSTIC_BLOB_BYTES).toByteArray(), Charsets.UTF_8)
                        .diagnosticValue()
                    "blob(${bytes.size}) hex=$hex text=$text"
                }
                else -> "unknown"
            }
        }.getOrElse { error -> "<${error.javaClass.simpleName}>" }
        "$name=$value"
    }.joinToString(" | ")

    private fun String.diagnosticValue(): String = replace('\n', ' ')
        .replace('\r', ' ')
        .take(DIAGNOSTIC_VALUE_LENGTH)

    private fun String.isTruthyEncryptionValue(): Boolean = trim().lowercase() in setOf(
        "1",
        "true",
        "yes",
        "scrambled",
        "encrypted",
    )

    private companion object {
        @Volatile
        var cachedInputKey: String? = null

        @Volatile
        var cachedChannels: List<LiveChannel>? = null

        val ENCRYPTED_COLUMN_CANDIDATES = listOf(
            "scrambled",
            "conditional_access",
            "is_scrambled",
            "is_encrypted",
            "encrypted",
            "free_ca_mode",
        )
        val ENCRYPTION_MARKER = Regex(
            "(?i)(scrambled|conditional[_ -]?access|free[_ -]?ca[_ -]?mode|encrypted)" +
                "[^a-z0-9]{0,12}(1|true|yes)",
        )
        const val DIAGNOSTIC_BLOB_BYTES = 128
        const val DIAGNOSTIC_VALUE_LENGTH = 256
    }
}
