package com.tvapp.livetv.data

import java.nio.charset.Charset

internal object TifProviderDataInspector {
    private val vendorKeys = listOf(
        "frequency",
        "symbolRate",
        "polarization",
        "satelliteId",
        "transponderId",
        "frontendId",
        "lnbId",
        "serviceKey",
    )

    fun inspect(bytes: ByteArray): List<Pair<String, String>> {
        if (bytes.isEmpty()) return listOf("internal_provider_data.size" to "0")
        val utf8 = bytes.decode(Charsets.UTF_8)
        val utf16 = bytes.decode(Charset.forName("UTF-16LE"))
        val searchable = sequenceOf(utf8, utf16).joinToString("\n")
        val fields = vendorKeys.mapNotNull { key ->
            extractValue(searchable, key)?.let { "internal_provider_data.$key" to it }
        }
        val preview = bytes.take(MAX_HEX_BYTES)
            .joinToString("") { byte -> "%02x".format(byte) }
        val printable = PRINTABLE_RUN.findAll(searchable)
            .map { it.value.trim() }
            .filter { it.length >= MIN_PRINTABLE_LENGTH }
            .distinct()
            .take(MAX_PRINTABLE_RUNS)
            .joinToString(" | ")

        return buildList {
            add("internal_provider_data.size" to bytes.size.toString())
            add("internal_provider_data.hex" to preview)
            add("internal_provider_data.utf8" to utf8.clean())
            add("internal_provider_data.utf16le" to utf16.clean())
            if (printable.isNotBlank()) add("internal_provider_data.printable" to printable.clean())
            addAll(fields)
        }
    }

    private fun extractValue(text: String, key: String): String? {
        val pattern = Regex(
            "(?i)(?:^|[^a-z0-9])${Regex.escape(key)}[\\s\\u0000]*[=:][\\s\\u0000]*[\\\"']?" +
                "([^\\u0000\\r\\n,;}&\\\"']{1,96})",
        )
        return pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf(String::isNotBlank)
    }

    private fun ByteArray.decode(charset: Charset): String = runCatching {
        String(this, charset)
    }.getOrDefault("")

    private fun String.clean(): String = replace('\u0000', ' ')
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(MAX_TEXT_LENGTH)

    private val PRINTABLE_RUN = Regex("[\\p{L}\\p{N} _./:=+\\-]{4,}")
    private const val MAX_HEX_BYTES = 512
    private const val MAX_TEXT_LENGTH = 1_024
    private const val MIN_PRINTABLE_LENGTH = 4
    private const val MAX_PRINTABLE_RUNS = 32
}
