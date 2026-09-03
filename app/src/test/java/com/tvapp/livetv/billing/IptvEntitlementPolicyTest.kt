package com.tvapp.livetv.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvEntitlementPolicyTest {
    @Test
    fun `free distribution remains open without a trial`() {
        val result = IptvEntitlementPolicy.evaluate(false, null, 0L, 100L)
        assertEquals(IptvAccessState.FREE_BUILD, result.state)
        assertTrue(result.accessGranted)
    }

    @Test
    fun `paid distribution requires starting the trial`() {
        val result = IptvEntitlementPolicy.evaluate(true, null, 0L, 100L)
        assertEquals(IptvAccessState.FIRST_INSTALL, result.state)
        assertFalse(result.accessGranted)
    }

    @Test
    fun `trial expires after seven days`() {
        val startedAt = 1_000L
        val active = IptvEntitlementPolicy.evaluate(true, null, startedAt, startedAt + 1_000L)
        val expired = IptvEntitlementPolicy.evaluate(
            true,
            null,
            startedAt,
            startedAt + IptvEntitlementManager.TRIAL_DURATION_MILLIS,
        )
        assertTrue(active.accessGranted)
        assertEquals(IptvAccessState.TRIAL_EXPIRED, expired.state)
        assertFalse(expired.accessGranted)
    }

    @Test
    fun `only completed purchase simulations grant paid access`() {
        assertFalse(simulated(IptvAccessState.PURCHASE_PENDING).accessGranted)
        assertTrue(simulated(IptvAccessState.PURCHASE_COMPLETED).accessGranted)
        assertTrue(simulated(IptvAccessState.PURCHASE_RESTORED).accessGranted)
    }

    private fun simulated(state: IptvAccessState) =
        IptvEntitlementPolicy.evaluate(false, state, 0L, 0L)
}
