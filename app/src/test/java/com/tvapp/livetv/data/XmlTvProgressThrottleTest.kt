package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** XmlTvProgressThrottle: progress geri çağrısı sınırlamanın sözleşmesi. */
class XmlTvProgressThrottleTest {
    private class FakeClock {
        var now = 1_000L
    }

    @Test
    fun report_firstCallIsAlwaysDelivered() {
        val received = mutableListOf<XmlTvImportProgress>()
        val throttle = XmlTvProgressThrottle({ received += it }, 120) { 1_000L }
        throttle.bump()
        throttle.report(XmlTvImportPhase.PARSING)
        assertEquals(1, received.size)
        assertEquals(1, received.single().programsImported)
    }

    @Test
    fun report_rapidCountChangesCollapseIntoOneCallback() {
        val received = mutableListOf<XmlTvImportProgress>()
        var now = 1_000L
        val throttle = XmlTvProgressThrottle({ received += it }, 120) { now }
        throttle.bump()
        throttle.report(XmlTvImportPhase.PARSING)
        repeat(5_000) {
            throttle.bump()
            throttle.report(XmlTvImportPhase.PARSING)
        }
        assertEquals(1, received.size)
        now += 121
        throttle.bump()
        throttle.report(XmlTvImportPhase.PARSING)
        assertEquals(2, received.size)
        assertEquals(5_002, received.last().programsImported)
    }

    @Test
    fun report_phaseChangesAreNeverDelayed() {
        val received = mutableListOf<XmlTvImportProgress>()
        var now = 1_000L
        val throttle = XmlTvProgressThrottle({ received += it }, 120) { now }
        throttle.report(XmlTvImportPhase.READING)
        now += 10
        throttle.report(XmlTvImportPhase.PARSING)
        assertEquals(2, received.size)
        now += 10
        throttle.report(XmlTvImportPhase.SAVING)
        assertEquals(3, received.size)
    }

    @Test
    fun report_forcedCallAlwaysDeliversLatestState() {
        val received = mutableListOf<XmlTvImportProgress>()
        val throttle = XmlTvProgressThrottle({ received += it }, 120) { 1_000L }
        throttle.report(XmlTvImportPhase.READING)
        repeat(100) { throttle.bump() }
        throttle.report(XmlTvImportPhase.SAVING, force = true)
        assertEquals(2, received.size)
        assertEquals(100, received.last().programsImported)
    }

    @Test
    fun report_nullCallbackNeverDelivers() {
        val throttle = XmlTvProgressThrottle(null, 120) { 1_000L }
        throttle.bump()
        throttle.report(XmlTvImportPhase.PARSING, force = true)
        // Yalnızca istisna atmadığını doğruluyoruz; geri çağrı yok.
    }
}
