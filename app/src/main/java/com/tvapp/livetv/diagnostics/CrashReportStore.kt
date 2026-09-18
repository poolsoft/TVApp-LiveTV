package com.tvapp.livetv.diagnostics

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashReportStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun recordEditorEvent(event: String) {
        preferences.edit()
            .putString(KEY_LAST_EDITOR_EVENT, "${timestamp()} | $event")
            .commit()
        recordDebug("EDITOR_EVENT | $event")
    }

    fun recordDebug(event: String) {
        val line = "${timestamp()} | $event\n"
        logExecutor.execute {
            writeDebugLineInternal(line)
        }
    }

    @Synchronized
    private fun writeDebugLineInternal(line: String) {
        val today = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(Date())
        val lastDate = preferences.getString(KEY_DEBUG_LOG_DATE, null)
        val existingUri = preferences.getString(KEY_DEBUG_LOG_URI, null)?.let(Uri::parse)
        val currentBytes = preferences.getLong(KEY_DEBUG_LOG_BYTES, 0L)
        val lineBytes = line.toByteArray(Charsets.UTF_8).size.toLong()

        if (lastDate == today && existingUri != null && currentBytes < MAX_DEBUG_LOG_BYTES) {
            val appended = runCatching {
                checkNotNull(appContext.contentResolver.openOutputStream(existingUri, "wa"))
                    .bufferedWriter().use { it.write(line) }
            }.isSuccess
            if (appended) {
                preferences.edit().putLong(KEY_DEBUG_LOG_BYTES, currentBytes + lineBytes).apply()
                return
            }
        }

        val part = if (lastDate == today) preferences.getInt(KEY_DEBUG_LOG_PART, 0) + 1 else 0
        val fileName = if (part == 0) {
            "TVApp-debug-$today.log"
        } else {
            "TVApp-debug-$today-$part.log"
        }

        runCatching { createDebugLog(fileName, line) }
            .onSuccess { (uri, location) ->
                preferences.edit()
                    .putString(KEY_DEBUG_LOG_DATE, today)
                    .putInt(KEY_DEBUG_LOG_PART, part)
                    .putString(KEY_DEBUG_LOG_URI, uri.toString())
                    .putString(KEY_DEBUG_LOG_LOCATION, location)
                    .putLong(KEY_DEBUG_LOG_BYTES, lineBytes)
                    .apply()
            }

        checkAndPurgeExpiredLogs()
    }

    fun recordRecreation(detail: String) {
        recordDebug(
            "ACTIVITY_RECREATED | detail=$detail, lastEditorEvent=${lastEditorEvent()}",
        )
    }

    fun recordEditorExit(detail: String) {
        val report = buildString {
            appendLine("CHANNEL EDITOR FINISHED WITHOUT AN EXCEPTION")
            appendLine("Time: ${timestamp()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Last editor event: ${lastEditorEvent()}")
            appendLine("Detail: $detail")
            append("If a color key caused this, the vendor emitted a BACK/finish event.")
        }
        recordDebug("EDITOR_FINISHED\n$report")
        // Normal navigation belongs in the rolling debug log, not in a standalone error file.
    }

    fun recordCrash(thread: Thread, error: Throwable) {
        val trace = StringWriter().also { writer ->
            error.printStackTrace(PrintWriter(writer))
        }.toString()
        val report = buildString {
            appendLine("UNCAUGHT APPLICATION EXCEPTION")
            appendLine("Time: ${timestamp()}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Thread: ${thread.name}")
            appendLine("Last editor event: ${lastEditorEvent()}")
            appendLine()
            append(trace)
        }
        recordDebug("UNCAUGHT_EXCEPTION\n$report")
        saveReport(report, "uncaught-exception")
    }

    @Synchronized
    fun saveTifDiagnostics(report: String) {
        val fingerprint = report.hashCode()
        if (preferences.getInt(KEY_TIF_DIAGNOSTICS_HASH, 0) == fingerprint) return
        val location = runCatching { writeToDownloads(report, "tif-channels") }
            .getOrNull()
            ?: runCatching { writeToAppExternalFiles(report, "tif-channels") }.getOrNull()
            ?: return
        preferences.edit().putInt(KEY_TIF_DIAGNOSTICS_HASH, fingerprint).apply()
        recordDebug("TIF_DIAGNOSTICS_WRITTEN | $location")
    }

    fun pendingReport(): String? = preferences.getString(KEY_PENDING_REPORT, null)

    fun pendingLogLocation(): String? = preferences.getString(KEY_PENDING_LOG_LOCATION, null)

    fun clearPendingReport() {
        preferences.edit()
            .remove(KEY_PENDING_REPORT)
            .remove(KEY_PENDING_LOG_LOCATION)
            .apply()
    }

    private fun saveReport(report: String, category: String) {
        val location = runCatching { writeToDownloads(report, category) }
            .getOrNull()
            ?: runCatching { writeToAppExternalFiles(report, category) }.getOrNull()
            ?: "Log file could not be written; report remains in app preferences."
        preferences.edit()
            .putString(KEY_PENDING_REPORT, report)
            .putString(KEY_PENDING_LOG_LOCATION, location)
            .commit()
        checkAndPurgeExpiredLogs()
    }

    private fun writeToDownloads(report: String, category: String): String {
        val fileName = logFileName(category)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/TVApp",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = appContext.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(resolver.openOutputStream(uri, "w")).bufferedWriter().use { writer ->
                writer.write(report)
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        return "/storage/emulated/0/Download/TVApp/$fileName"
    }

    private fun createDebugLog(fileName: String, initialContent: String): Pair<Uri, String> {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/TVApp",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = appContext.contentResolver
        val uri = checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(resolver.openOutputStream(uri, "w")).bufferedWriter().use {
                it.write(initialContent)
            }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
        return uri to "/storage/emulated/0/Download/TVApp/$fileName"
    }

    private fun writeToAppExternalFiles(report: String, category: String): String {
        val directory = checkNotNull(appContext.getExternalFilesDir("logs"))
        val file = File(directory, logFileName(category))
        file.parentFile?.mkdirs()
        file.writeText(report)
        return file.absolutePath
    }

    private fun logFileName(category: String): String = "TVApp-$category-${SimpleDateFormat(
        "yyyyMMdd-HHmmss-SSS",
        Locale.ROOT,
    ).format(Date())}.log"

    private fun lastEditorEvent(): String =
        preferences.getString(KEY_LAST_EDITOR_EVENT, "none") ?: "none"

    private fun timestamp(): String = SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss.SSS",
        Locale.ROOT,
    ).format(Date())

    fun cleanExpiredLogs() {
        checkAndPurgeExpiredLogs(force = true)
    }

    private fun checkAndPurgeExpiredLogs(force: Boolean = false) {
        val now = System.currentTimeMillis()
        val lastPurge = preferences.getLong(KEY_LAST_PURGE_TIME, 0L)
        if (!force && now - lastPurge < PURGE_CHECK_INTERVAL_MS) return
        preferences.edit().putLong(KEY_LAST_PURGE_TIME, now).apply()

        val cutoffMillis = now - RETENTION_PERIOD_MS
        logExecutor.execute {
            purgeExternalFiles(cutoffMillis)
            purgeMediaStoreDownloads(cutoffMillis)
        }
    }

    private fun purgeExternalFiles(cutoffMillis: Long) {
        val directory = appContext.getExternalFilesDir("logs") ?: return
        val files = directory.listFiles() ?: return
        for (file in files) {
            val name = file.name
            if (name.startsWith("TVApp-") && isExpired(file.lastModified(), name, cutoffMillis)) {
                runCatching { file.delete() }
            }
        }
    }

    private fun purgeMediaStoreDownloads(cutoffMillis: Long) {
        val resolver = appContext.contentResolver
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATE_MODIFIED,
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%Download/TVApp%", "TVApp-%")
        runCatching {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                val toDelete = mutableListOf<Uri>()
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn) * 1000L

                    if (name.startsWith("TVApp-") && isExpired(dateModified, name, cutoffMillis)) {
                        toDelete.add(
                            android.content.ContentUris.withAppendedId(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                id,
                            ),
                        )
                    }
                }
                toDelete.forEach { uri ->
                    runCatching { resolver.delete(uri, null, null) }
                }
            }
        }
        runCatching {
            val downloadDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "TVApp",
            )
            if (downloadDir.exists() && downloadDir.isDirectory) {
                downloadDir.listFiles()?.forEach { file ->
                    val name = file.name
                    if (name.startsWith("TVApp-") && isExpired(file.lastModified(), name, cutoffMillis)) {
                        runCatching { file.delete() }
                    }
                }
            }
        }
    }

    private fun isExpired(lastModifiedMillis: Long, fileName: String, cutoffMillis: Long): Boolean {
        if (lastModifiedMillis in 1..cutoffMillis) return true
        val dateMatch = Regex("""\b(20\d{6})\b""").find(fileName)?.value
        if (dateMatch != null) {
            val parsed = runCatching {
                SimpleDateFormat("yyyyMMdd", Locale.ROOT).parse(dateMatch)?.time
            }.getOrNull()
            if (parsed != null && parsed < cutoffMillis) return true
        }
        return false
    }

    private companion object {
        val logExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "CrashReportLogWriter").apply { isDaemon = true }
        }
        const val MAX_DEBUG_LOG_BYTES = 2 * 1024 * 1024L
        const val RETENTION_PERIOD_MS = 7L * 24 * 60 * 60 * 1_000L
        const val PURGE_CHECK_INTERVAL_MS = 12 * 60 * 60 * 1_000L
        const val PREFERENCES_NAME = "diagnostic-reports"
        const val KEY_LAST_EDITOR_EVENT = "last-editor-event"
        const val KEY_PENDING_REPORT = "pending-report"
        const val KEY_PENDING_LOG_LOCATION = "pending-log-location"
        const val KEY_DEBUG_LOG_URI = "debug-log-uri"
        const val KEY_DEBUG_LOG_LOCATION = "debug-log-location"
        const val KEY_DEBUG_LOG_BYTES = "debug-log-bytes"
        const val KEY_DEBUG_LOG_DATE = "debug-log-date"
        const val KEY_DEBUG_LOG_PART = "debug-log-part"
        const val KEY_LAST_PURGE_TIME = "last-log-purge-time"
        const val KEY_TIF_DIAGNOSTICS_HASH = "tif-diagnostics-hash"
    }
}
