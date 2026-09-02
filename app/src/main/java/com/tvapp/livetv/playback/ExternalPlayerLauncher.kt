package com.tvapp.livetv.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.settings.ExternalPlayerPreference

object ExternalPlayerLauncher {
    fun launch(
        context: Context,
        channel: LiveChannel,
        preference: ExternalPlayerPreference,
    ): Boolean {
        val base = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(channel.uri), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_TITLE, channel.displayName)
            putExtra("title", channel.displayName)
            val headers = buildList {
                channel.userAgent?.let { add("User-Agent"); add(it) }
                channel.referrer?.let { add("Referer"); add(it) }
            }.toTypedArray()
            if (headers.isNotEmpty()) putExtra("headers", headers)
        }
        val candidates = when (preference) {
            ExternalPlayerPreference.SYSTEM -> listOf<String?>(null)
            ExternalPlayerPreference.VLC -> listOf(VLC_PACKAGE, null)
            ExternalPlayerPreference.MX_PLAYER -> listOf(MX_PRO_PACKAGE, MX_FREE_PACKAGE, null)
        }
        candidates.forEach { packageName ->
            val intent = Intent(base).apply {
                if (packageName != null) setPackage(packageName)
                channel.subtitleUrl?.let { subtitleUrl ->
                    putExtra("subtitles_location", subtitleUrl)
                    val subtitleUri = Uri.parse(subtitleUrl)
                    putExtra("subs", arrayOf(subtitleUri))
                    putExtra("subs.name", arrayOf(channel.displayName))
                    putExtra("subs.filename", arrayOf(subtitleUri.lastPathSegment.orEmpty()))
                    putExtra("subs.enable", arrayOf(subtitleUri))
                }
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        }
        return false
    }

    private const val VLC_PACKAGE = "org.videolan.vlc"
    private const val MX_FREE_PACKAGE = "com.mxtech.videoplayer.ad"
    private const val MX_PRO_PACKAGE = "com.mxtech.videoplayer.pro"
}
