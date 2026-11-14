package com.tvapp.livetv.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.PrintWriter

class LimitedCharWriterTest {
    @Test
    fun keepsShortOutputUntouched() {
        val writer = LimitedCharWriter(1_000)

        // print + explicit newline: println() uses the platform line separator, which is
        // CRLF on the desktop JVM but LF on Android.
        PrintWriter(writer).use { it.print("java.lang.IllegalStateException: bozuk satir\n") }

        assertEquals("java.lang.IllegalStateException: bozuk satir\n", writer.text())
    }

    @Test
    fun neverKeepsMoreThanTheLimitPlusTheTruncationNote() {
        val writer = LimitedCharWriter(2_000)

        PrintWriter(writer).use { printer ->
            repeat(50_000) { printer.println("  at com.tvapp.livetv.MainActivity.loop(Unknown Source)") }
        }

        val text = writer.text()
        assertTrue("kept ${text.length} characters", text.length <= 2_000 + 200)
        assertTrue(text, text.contains("iz kesildi"))
        assertTrue(text, text.contains("satir atlandi"))
    }

    @Test
    fun cutsOnLineBoundaryAndReportsDroppedAmount() {
        val writer = LimitedCharWriter(12)

        writer.write("0123456789\nabcdefgh\n", 0, 20)

        val text = writer.text()
        assertTrue(text, text.startsWith("0123456789\n"))
        assertTrue(text, text.contains("8 karakter / 1 satir atlandi"))
    }

    @Test
    fun keepsStackOverflowTraceFromEscapingTheLimit() {
        val error = runCatching { recurse(0) }.exceptionOrNull()
        assertTrue(error is StackOverflowError)

        val writer = LimitedCharWriter(4_000)
        PrintWriter(writer).use { error!!.printStackTrace(it) }

        val text = writer.text()
        assertTrue("kept ${text.length} characters", text.length <= 4_000 + 200)
        assertTrue(text, text.startsWith(error!!.javaClass.name))
    }

    @Test
    fun ignoresEmptyWrites() {
        val writer = LimitedCharWriter(4)

        writer.write(CharArray(0), 0, 0)

        assertEquals("", writer.text())
    }

    private fun recurse(depth: Int): Int = depth + recurse(depth + 1)
}
