package com.tvapp.livetv.main

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LongPressKeyGateTest {

    private fun scope() = CoroutineScope(Dispatchers.Unconfined + Job())

    @Test
    fun immediateTimeoutFiresLongPress() {
        val gate = LongPressKeyGate(scope(), longPressTimeoutMillis = { 0 })
        val events = mutableListOf<String>()
        gate.onDown(
            7,
            onLongPress = { events += "long:$it" },
            onShortPress = { events += "short:$it" },
        )
        gate.onUp()
        assertEquals(listOf("long:7"), events)
    }

    @Test
    fun releaseBeforeTimeoutFiresShortPress() {
        val gate = LongPressKeyGate(scope(), longPressTimeoutMillis = { 60_000 })
        val events = mutableListOf<String>()
        gate.onDown(
            9,
            onLongPress = { events += "long:$it" },
            onShortPress = { events += "short:$it" },
        )
        gate.onUp()
        assertEquals(listOf("short:9"), events)
    }

    @Test
    fun secondDownReplacesPendingCallbacks() {
        val gate = LongPressKeyGate(scope(), longPressTimeoutMillis = { 60_000 })
        val events = mutableListOf<String>()
        gate.onDown(
            9,
            onLongPress = { events += "long:$it" },
            onShortPress = { events += "short:$it" },
        )
        gate.onDown(
            9,
            onLongPress = { events += "long2:$it" },
            onShortPress = { events += "short2:$it" },
        )
        gate.onUp()
        // Ikinci DOWN onceki bekleyen kisa basimi gecersiz kilar.
        assertEquals(listOf("short2:9"), events)
    }

    @Test
    fun cancelPreventsAnyAction() {
        val gate = LongPressKeyGate(scope(), longPressTimeoutMillis = { 60_000 })
        val events = mutableListOf<String>()
        gate.onDown(
            9,
            onLongPress = { events += "long:$it" },
            onShortPress = { events += "short:$it" },
        )
        gate.cancel()
        gate.onUp()
        assertTrue(events.isEmpty())
    }

    @Test
    fun handledFlagResetsAfterUp() {
        val gate = LongPressKeyGate(scope(), longPressTimeoutMillis = { 0 })
        val events = mutableListOf<String>()
        val long: (Int) -> Unit = { events += "long:$it" }
        val short: (Int) -> Unit = { events += "short:$it" }

        gate.onDown(1, long, short)
        gate.onUp()
        gate.onDown(2, long, short)
        gate.onUp()

        assertEquals(listOf("long:1", "long:2"), events)
    }

    @Test
    fun secondPressWithoutLongPressStillGetsShortAction() {
        val gate = LongPressKeyGate(scope(), longPressTimeoutMillis = { 60_000 })
        val events = mutableListOf<String>()
        val long: (Int) -> Unit = { events += "long:$it" }
        val short: (Int) -> Unit = { events += "short:$it" }

        gate.onDown(1, long, short)
        gate.onUp()
        gate.onDown(2, long, short)
        gate.onUp()

        assertEquals(listOf("short:1", "short:2"), events)
    }
}
