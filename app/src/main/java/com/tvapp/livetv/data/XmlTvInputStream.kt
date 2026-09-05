package com.tvapp.livetv.data

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

internal fun InputStream.openXmlTvContent(): InputStream {
    val buffered = this as? BufferedInputStream ?: BufferedInputStream(this)
    buffered.mark(GZIP_HEADER_SIZE)
    val first = buffered.read()
    val second = buffered.read()
    buffered.reset()
    return if (first == GZIP_MAGIC_FIRST && second == GZIP_MAGIC_SECOND) {
        GZIPInputStream(buffered)
    } else {
        buffered
    }
}

private const val GZIP_HEADER_SIZE = 2
private const val GZIP_MAGIC_FIRST = 0x1f
private const val GZIP_MAGIC_SECOND = 0x8b
