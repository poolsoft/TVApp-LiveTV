package com.tvapp.livetv.playback

import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** One network producer and one reader. Full queues block the producer, never discard video. */
internal class BufferedLiveInputStream(
    private val source: InputStream,
    capacityBytes: Int,
) : InputStream() {
    init { require(capacityBytes >= CHUNK_BYTES * 3) }
    // Leave room for the producer's chunk and the consumer's current chunk.
    private val queue = ArrayBlockingQueue<ByteArray>(capacityBytes / CHUNK_BYTES - 2)
    @Volatile private var closed = false
    @Volatile private var finished = false
    @Volatile private var failure: IOException? = null
    private var current: ByteArray? = null
    private var position = 0
    private val worker = thread(name = "TVApp-live-prefetch", isDaemon = true) {
        try {
            while (!closed) {
                val chunk = ByteArray(CHUNK_BYTES)
                val count = source.read(chunk)
                if (count < 0) break
                if (count == 0) continue
                val data = if (count == chunk.size) chunk else chunk.copyOf(count)
                while (!closed && !queue.offer(data, WAIT_MS, TimeUnit.MILLISECONDS)) Unit
            }
        } catch (error: IOException) {
            if (!closed) failure = error
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            if (!closed) failure = InterruptedIOException("Live download interrupted")
        } catch (error: RuntimeException) {
            if (!closed) failure = IOException("Live download failed", error)
        } finally {
            try { source.close() }
            catch (error: IOException) { if (!closed && failure == null) failure = error }
            finally { if (closed) queue.clear(); finished = true }
        }
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (offset < 0 || length < 0 || offset > buffer.size - length) throw IndexOutOfBoundsException()
        if (length == 0) return 0
        while (true) {
            if (closed) throw IOException("Live buffer closed")
            val chunk = current ?: try { queue.poll(WAIT_MS, TimeUnit.MILLISECONDS) }
            catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Live read interrupted")
            }
            if (chunk == null) {
                if (finished && queue.isEmpty()) {
                    failure?.let { throw it }
                    return -1
                }
                continue
            }
            current = chunk
            val count = minOf(length, chunk.size - position)
            chunk.copyInto(buffer, offset, position, position + count)
            position += count
            if (position == chunk.size) { current = null; position = 0 }
            return count
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        queue.clear()
        worker.interrupt()
        source.close()
    }

    internal fun awaitTermination(timeoutMs: Long): Boolean {
        worker.join(timeoutMs)
        return !worker.isAlive
    }

    private companion object {
        const val CHUNK_BYTES = 32 * 1024
        const val WAIT_MS = 100L
    }
}
