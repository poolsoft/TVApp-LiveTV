package com.tvapp.livetv.playback

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class BufferedLiveInputStreamTest {
    private val capacity = 3 * 32 * 1024

    @Test fun `drains chunks in order across queue boundaries`() {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        BufferedLiveInputStream(ByteArrayInputStream(bytes), capacity).use { input ->
            assertArrayEquals(bytes, input.readBytes())
            assertEquals(-1, input.read())
            assertTrue(input.awaitTermination(2000))
        }
    }

    @Test fun `network error is delivered after queued video`() {
        val bytes = byteArrayOf(1, 2, 3)
        val source = object : InputStream() {
            var sent = false
            override fun read() = error("Use bulk read")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (sent) throw IOException("fixture outage")
                sent = true
                bytes.copyInto(b, off)
                return bytes.size
            }
        }
        BufferedLiveInputStream(source, capacity).use { input ->
            assertEquals(1, input.read()); assertEquals(2, input.read()); assertEquals(3, input.read())
            try { input.read(); fail("Expected outage") }
            catch (error: IOException) { assertEquals("fixture outage", error.message) }
        }
    }

    @Test fun `full queue blocks download and cancellation releases producer`() {
        val reads = AtomicInteger()
        val twoChunks = CountDownLatch(1)
        val source = object : InputStream() {
            override fun read() = error("Use bulk read")
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (reads.incrementAndGet() == 2) twoChunks.countDown()
                return len
            }
        }
        val input = BufferedLiveInputStream(source, capacity)
        try {
            assertTrue(twoChunks.await(2, TimeUnit.SECONDS))
            // One chunk occupies the queue and the second waits to be enqueued.
            assertEquals(2, reads.get())
            input.close()
            assertTrue(input.awaitTermination(2000))
            assertEquals(2, reads.get())
        } finally { input.close() }
    }

    @Test fun `closing download unblocks waiting consumer and network read`() {
        val networkRead = CountDownLatch(1)
        val networkClosed = CountDownLatch(1)
        val source = object : InputStream() {
            override fun read(): Int {
                networkRead.countDown()
                networkClosed.await()
                return -1
            }
            override fun close() { networkClosed.countDown() }
        }
        val executor = Executors.newSingleThreadExecutor()
        val input = BufferedLiveInputStream(source, capacity)
        try {
            assertTrue(networkRead.await(2, TimeUnit.SECONDS))
            val read = executor.submit<Boolean> {
                try { input.read(); false } catch (error: IOException) { true }
            }
            input.close()
            assertTrue(read.get(2, TimeUnit.SECONDS))
            assertTrue(input.awaitTermination(2000))
        } finally { input.close(); executor.shutdownNow() }
    }

    @Test fun `zero length reads and empty responses follow input contract`() {
        BufferedLiveInputStream(ByteArrayInputStream(byteArrayOf()), capacity).use { input ->
            assertEquals(0, input.read(ByteArray(1), 0, 0))
            assertEquals(-1, input.read())
        }
    }
}
