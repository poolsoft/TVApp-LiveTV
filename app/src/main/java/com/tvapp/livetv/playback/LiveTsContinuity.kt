package com.tvapp.livetv.playback

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/** Joins overlapping responses at the last exact TS packet of each elementary stream.
 * No timestamp rewriting, codec parsing, or guessed time-based trimming. */
internal class LiveTsContinuity {
    private data class Tail(val start: ByteArray, val packet: ByteArray, var count: Int)
    private data class Anchor(val tail: Tail, var seen: Int = 0)
    private val tails = mutableMapOf<Int, Tail>()
    var droppedPackets = 0L
        private set

    fun open(source: InputStream): InputStream {
        val anchors = tails.mapValues { (_, tail) ->
            Anchor(Tail(tail.start.copyOf(), tail.packet.copyOf(), tail.count))
        }.toMutableMap()
        return object : InputStream() {
            private val input = BufferedInputStream(source, 32 * 1024)
            private val packet = ByteArray(188)
            private var position = packet.size
            private var eof = false

            override fun read(): Int {
                val one = ByteArray(1)
                return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 255
            }

            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if (offset < 0 || length < 0 || offset > bytes.size - length) throw IndexOutOfBoundsException()
                if (length == 0) return 0
                if (eof) return -1
                var written = 0
                while (written < length) {
                    while (position == packet.size) {
                        var count = 0
                        while (count < packet.size) {
                            val read = input.read(packet, count, packet.size - count)
                            if (read < 0) {
                                eof = true
                                if (anchors.isNotEmpty()) {
                                    throw LiveTsContinuityException("Live TS response has no complete continuity boundary")
                                }
                                // A response may end mid-packet. Its next overlapping response
                                // provides that packet in full; never expose the partial tail.
                                return if (written == 0) -1 else written
                            }
                            count += read
                        }
                        if (packet[0] != 0x47.toByte()) {
                            throw LiveTsContinuityException("Live TS response lost packet alignment")
                        }
                        val pid = ((packet[1].toInt() and 31) shl 8) or (packet[2].toInt() and 255)
                        val anchor = anchors[pid]
                        if (anchor != null) {
                            droppedPackets++
                            if (anchor.seen == 0) {
                                if (packet.contentEquals(anchor.tail.start)) anchor.seen = 1
                            } else {
                                anchor.seen++
                            }
                            if (anchor.seen == anchor.tail.count) {
                                if (!packet.contentEquals(anchor.tail.packet)) {
                                    throw LiveTsContinuityException("Live TS overlap changed within an elementary stream")
                                }
                                anchors.remove(pid)
                            }
                            continue
                        }
                        val control = (packet[3].toInt() ushr 4) and 3
                        val payload = if (control == 3) 5 + (packet[4].toInt() and 255) else 4
                        if (control == 1 || control == 3) {
                            if ((packet[1].toInt() and 64) != 0 && payload + 4 <= packet.size &&
                                packet[payload] == 0.toByte() && packet[payload + 1] == 0.toByte() &&
                                packet[payload + 2] == 1.toByte()) {
                                val streamId = packet[payload + 3].toInt() and 255
                                if (streamId in 0xc0..0xef || streamId == 0xbd) {
                                    tails[pid] = Tail(packet.copyOf(), packet.copyOf(), 0)
                                }
                            }
                        }
                        tails[pid]?.let { packet.copyInto(it.packet); it.count++ }
                        // PSI was already delivered on the first response. Do not repeatedly emit
                        // tables while searching an entirely duplicated response for its boundary.
                        if (anchors.isNotEmpty() && pid !in tails) { droppedPackets++; continue }
                        position = 0
                    }
                    val count = minOf(length - written, packet.size - position)
                    packet.copyInto(bytes, offset + written, position, position + count)
                    position += count
                    written += count
                }
                return written
            }

            override fun close() = input.close()
        }
    }
}

internal class LiveTsContinuityException(message: String) : IOException(message)

internal fun requiresFreshLiveSource(error: Throwable?): Boolean =
    generateSequence(error) { it.cause }.any { it is LiveTsContinuityException }
