package com.tvapp.livetv.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class IptvOperationGateTest {
    @Test fun idleServerDoesNotReserveOperations() {
        val gate = IptvOperationGate()
        assertFalse(gate.isBusy)
        assertTrue(gate.tryAcquire())
        assertTrue(gate.isBusy)
        assertFalse(gate.tryAcquire())
        gate.release()
        assertFalse(gate.isBusy)
        assertTrue(gate.tryAcquire())
    }

    @Test fun simultaneousTvAndBrowserCommandsHaveOnlyOneWinner() {
        val gate = IptvOperationGate()
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        try {
            val attempts = (1..2).map {
                executor.submit<Boolean> { start.await(); gate.tryAcquire() }
            }
            start.countDown()
            assertEquals(1, attempts.count { it.get(5, TimeUnit.SECONDS) })
            gate.release()
            assertTrue(gate.tryAcquire())
        } finally {
            executor.shutdownNow()
        }
    }
}
