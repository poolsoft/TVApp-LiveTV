package com.tvapp.livetv.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DEBUGLOG-001: ayiklama gunlugunun kullanici tercihi ile kapatilabilmesi, istisna
 * kayitlarinin ise hicbir zaman kapatilamamasi.
 */
class DebugLogPolicyTest {

    @Test
    fun `event lines are skipped when the debug log is off`() {
        assertFalse(
            "Gunluk kapaliyken olay satiri yazilmamali",
            DebugLogPolicy.writesDebugLine(debugLogEnabled = false, kind = DiagnosticEntryKind.EVENT),
        )
    }

    @Test
    fun `event lines are written when the debug log is on`() {
        assertTrue(
            DebugLogPolicy.writesDebugLine(debugLogEnabled = true, kind = DiagnosticEntryKind.EVENT),
        )
    }

    @Test
    fun `exception lines are written even when the debug log is off`() {
        assertTrue(
            "Kullanici gereksinimi: acilis dahil hicbir istisna kacirilmamali",
            DebugLogPolicy.writesDebugLine(
                debugLogEnabled = false,
                kind = DiagnosticEntryKind.EXCEPTION,
            ),
        )
    }

    @Test
    fun `exception lines are written when the debug log is on`() {
        assertTrue(
            DebugLogPolicy.writesDebugLine(
                debugLogEnabled = true,
                kind = DiagnosticEntryKind.EXCEPTION,
            ),
        )
    }
}
