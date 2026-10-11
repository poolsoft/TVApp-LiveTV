package com.tvapp.livetv.playback

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class LiveTsContinuityTest {
    private fun pes(pid: Int, value: Int) = ByteArray(188) { value.toByte() }.apply {
        this[0] = 0x47; this[1] = ((pid shr 8) or 0x40).toByte(); this[2] = pid.toByte(); this[3] = 0x10
        this[4] = 0; this[5] = 0; this[6] = 1; this[7] = if (pid == 256) 0xe0.toByte() else 0xc0.toByte()
    }
    private fun continuation(pid: Int, value: Int) = pes(pid, value).apply {
        this[1] = (pid shr 8).toByte()
    }
    private fun read(joiner: LiveTsContinuity, vararg packets: ByteArray) =
        joiner.open(ByteArrayInputStream(packets.reduce(ByteArray::plus))).use { it.readBytes() }

    @Test fun `independently removes audio and video overlap`() {
        val joiner = LiveTsContinuity()
        val v1 = pes(256, 1); val a1 = pes(257, 2)
        val v2 = pes(256, 3); val a2 = pes(257, 4)
        assertArrayEquals(v1 + a1, read(joiner, v1, a1))
        assertArrayEquals(v2 + a2, read(joiner, a1, v1, v2, a2))
        val v3 = pes(256, 5); val a3 = pes(257, 6)
        assertArrayEquals(v3 + a3, read(joiner, v1, a1, v2, a2, v3, a3))
    }

    @Test fun `restores packet cut by previous HTTP response`() {
        val joiner = LiveTsContinuity()
        val start = pes(256, 1); val next = continuation(256, 2)
        assertArrayEquals(start, read(joiner, start, next.copyOf(33)))
        assertArrayEquals(next, read(joiner, start, next))
    }

    @Test fun `continuation resumes mid PES without dropping remaining packets`() {
        val joiner = LiveTsContinuity()
        val start = pes(256, 1); val tail = continuation(256, 2); val next = continuation(256, 3)
        read(joiner, start, tail)
        assertArrayEquals(next, read(joiner, start, tail, next))
    }

    @Test fun `identical filler within a PES does not match the wrong boundary`() {
        val joiner = LiveTsContinuity()
        val start = pes(256, 1); val filler = continuation(256, 2); val next = continuation(256, 3)
        read(joiner, start, filler, filler)
        assertArrayEquals(next, read(joiner, start, filler, filler, next))
    }

    @Test fun `missing overlap fails instead of silently joining unknown timelines`() {
        val joiner = LiveTsContinuity()
        read(joiner, pes(256, 1))
        assertThrows(LiveTsContinuityException::class.java) { read(joiner, pes(256, 2)) }
    }

    @Test fun `changed bytes at expected overlap boundary fail safely`() {
        val joiner = LiveTsContinuity()
        val start = pes(256, 1)
        read(joiner, start, continuation(256, 2))
        assertThrows(LiveTsContinuityException::class.java) { read(joiner, start, continuation(256, 3)) }
    }

    @Test fun `small reads preserve bytes and zero length read returns zero`() {
        val bytes = pes(256, 1) + continuation(256, 2)
        LiveTsContinuity().open(ByteArrayInputStream(bytes)).use { input ->
            assertEquals(0, input.read(ByteArray(0), 0, 0))
            assertArrayEquals(bytes, ByteArray(bytes.size) { input.read().toByte() })
            assertEquals(-1, input.read())
        }
    }

    @Test fun `PES with adaptation field tracks its payload boundary`() {
        val joiner = LiveTsContinuity()
        val start = pes(256, 7).apply {
            copyInto(this, 7, 4, 185)
            this[3] = 0x30; this[4] = 2; this[5] = 0; this[6] = 0
        }
        val next = continuation(256, 8)
        assertArrayEquals(start, read(joiner, start))
        assertArrayEquals(next, read(joiner, start, next))
    }

    @Test fun `unaligned data is not delivered as TS packets`() {
        assertThrows(LiveTsContinuityException::class.java) {
            read(LiveTsContinuity(), ByteArray(188))
        }
    }

    @Test fun `only continuity failures require a fresh extractor`() {
        assertTrue(requiresFreshLiveSource(java.io.IOException("wrapped", LiveTsContinuityException("boundary"))))
        assertFalse(requiresFreshLiveSource(java.io.IOException("ordinary network failure")))
        assertFalse(requiresFreshLiveSource(null))
    }
}
