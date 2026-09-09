package com.tvapp.livetv.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceResourcePolicyTest {
    @Test
    fun lowRamDeviceUsesBoundedLogoAndTwoStreamGrid() {
        val policy = resourcePolicyFor(capabilities(lowRam = true, decoderCapacity = 4))

        assertEquals(32, policy.maximumLogoDiskMegabytes)
        assertEquals(1, policy.logoPrefetchCount)
        assertEquals(2, policy.maximumGridStreams)
        assertFalse(policy.supportsMultiView)
    }

    @Test
    fun regularDeviceUsesDecoderCapacity() {
        val policy = resourcePolicyFor(capabilities(lowRam = false, decoderCapacity = 3))

        assertEquals(3, policy.maximumGridStreams)
        assertTrue(policy.supportsMultiView)
    }

    private fun capabilities(lowRam: Boolean, decoderCapacity: Int) = DeviceCapabilities(
        isLeanbackDevice = true,
        reportsLiveTvFeature = true,
        hasTvInputManager = true,
        vendorTunerInputCount = 1,
        hasTvListingsPermission = true,
        supportsPictureInPicture = true,
        isLowRamDevice = lowRam,
        memoryClassMegabytes = if (lowRam) 128 else 512,
        hardwareVideoDecoderCount = 2,
        maximumConcurrentVideoDecoders = decoderCapacity,
    )
}
