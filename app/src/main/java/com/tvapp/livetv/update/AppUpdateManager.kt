package com.tvapp.livetv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.tvapp.livetv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val mandatory: Boolean,
)

class AppUpdateManager(private val context: Context) {
    suspend fun check(): AppUpdate? = withContext(Dispatchers.IO) {
        check(BuildConfig.SELF_UPDATE_ENABLED) { "External updates are disabled for this build" }
        val json = readUrl(BuildConfig.UPDATE_MANIFEST_URL, MAX_MANIFEST_BYTES)
        val root = JSONObject(json)
        val update = AppUpdate(
            versionCode = root.getInt("versionCode"),
            versionName = root.getString("versionName"),
            apkUrl = root.getString("apkUrl"),
            sha256 = root.getString("sha256").lowercase(),
            mandatory = root.optBoolean("mandatory", false),
        )
        val installedApkHash = sha256(File(context.applicationInfo.sourceDir))
        update.takeIf {
            it.versionCode > BuildConfig.VERSION_CODE ||
                (it.versionCode == BuildConfig.VERSION_CODE && it.sha256 != installedApkHash)
        }
    }

    suspend fun download(update: AppUpdate): Uri = withContext(Dispatchers.IO) {
        check(BuildConfig.SELF_UPDATE_ENABLED) { "External updates are disabled for this build" }
        require(update.apkUrl.startsWith("https://")) { "Only HTTPS update URLs are accepted" }
        val directory = File(context.filesDir, "updates").apply { mkdirs() }
        val target = File(directory, "TVApp-${update.versionCode}.apk")
        val temporary = File(directory, "${target.name}.download")
        temporary.delete()
        openConnection(update.apkUrl).useConnection { connection ->
            connection.inputStream.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val actualHash = sha256(temporary)
        check(actualHash.equals(update.sha256, ignoreCase = true)) {
            "Downloaded APK checksum does not match"
        }
        target.delete()
        check(temporary.renameTo(target)) { "Downloaded APK could not be finalized" }
        FileProvider.getUriForFile(context, "${context.packageName}.updates", target)
    }

    fun installerIntent(apkUri: Uri): Intent {
        check(BuildConfig.SELF_UPDATE_ENABLED) { "External updates are disabled for this build" }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun readUrl(url: String, maximumBytes: Int): String {
        require(url.startsWith("https://")) { "Only HTTPS update manifests are accepted" }
        return openConnection(url).useConnection { connection ->
            connection.inputStream.bufferedReader().use { reader ->
                val result = reader.readText()
                check(result.toByteArray().size <= maximumBytes) { "Update manifest is too large" }
                result
            }
        }
    }

    private fun openConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, application/octet-stream")
            setRequestProperty("User-Agent", "TVApp/${BuildConfig.VERSION_NAME}")
            connect()
            check(responseCode in 200..299) { "Update server returned HTTP $responseCode" }
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_MANIFEST_BYTES = 64 * 1024
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
