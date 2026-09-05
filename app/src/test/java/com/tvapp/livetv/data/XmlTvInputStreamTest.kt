package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class XmlTvInputStreamTest {
    @Test
    fun leavesPlainXmlReadable() {
        val xml = "<?xml version=\"1.0\"?><tv/>"

        val decoded = ByteArrayInputStream(xml.toByteArray()).openXmlTvContent()

        assertEquals(xml, decoded.bufferedReader().readText())
    }

    @Test
    fun detectsGzipFromMagicBytesWithoutHttpHeaders() {
        val xml = "<?xml version=\"1.0\"?><tv><channel id=\"one\"/></tv>"
        val compressed = ByteArrayOutputStream().also { output ->
            GZIPOutputStream(output).use { it.write(xml.toByteArray()) }
        }.toByteArray()

        val decoded = ByteArrayInputStream(compressed).openXmlTvContent()

        assertEquals(xml, decoded.bufferedReader().readText())
    }
}
