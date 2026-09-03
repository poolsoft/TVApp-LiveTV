package com.tvapp.livetv.data

import android.net.Uri
import java.util.Base64

object StalkerStreamUri {
    private const val SCHEME = "tvapp-stalker"

    fun create(endpoint: String, macAddress: String, command: String): String = Uri.Builder()
        .scheme(SCHEME)
        .authority("stream")
        .appendQueryParameter("endpoint", endpoint)
        .appendQueryParameter("mac", macAddress)
        .appendQueryParameter(
            "cmd",
            Base64.getUrlEncoder().withoutPadding().encodeToString(command.toByteArray(Charsets.UTF_8)),
        )
        .build()
        .toString()

    fun resolve(value: Uri): String? {
        if (value.scheme != SCHEME) return null
        val endpoint = value.getQueryParameter("endpoint") ?: return null
        val mac = value.getQueryParameter("mac") ?: return null
        val encoded = value.getQueryParameter("cmd") ?: return null
        val command = String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        return StalkerClient(endpoint, mac).resolve(command)
    }
}
