package com.tvapp.livetv.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceResourcePolicyTest {
    @Test
    fun lowRamDeviceUsesBoundedLogoAndTwoStreamGrid() {
        val policy = resourcePolicyFor(capabilities(lowRam = true, decoderCapacity = 1))

        assertEquals(32, policy.maximumLogoDiskMegabytes)
        assertEquals(1, policy.logoPrefetchCount)
        assertEquals(2, policy.maximumGridStreams)
        assertTrue(policy.supportsMultiView)
    }

    @Test
    fun regularDeviceWithFourH264DecodersUsesFourStreamGrid() {
        val policy = resourcePolicyFor(capabilities(lowRam = false, decoderCapacity = 4))

        assertEquals(4, policy.maximumGridStreams)
        assertTrue(policy.supportsMultiView)
    }

    @Test
    fun forceFourStreamsOverridesCodecAndMemoryLimits() {
        val policy = resourcePolicyFor(
            capabilities(
                lowRam = true,
                decoderCapacity = 1,
                h264Instances = 0,
            ),
            forceFourGridStreams = true,
        )

        assertEquals(4, policy.maximumGridStreams)
    }

    @Test
    fun fourH264InstancesYieldFourGridStreams() {
        val policy = resourcePolicyFor(
            capabilities(lowRam = false, decoderCapacity = 4, h264Instances = 4),
        )

        assertEquals(4, policy.maximumGridStreams)
    }

    @Test
    fun twoH264InstancesYieldTwoGridStreams() {
        val policy = resourcePolicyFor(
            capabilities(lowRam = false, decoderCapacity = 2, h264Instances = 2),
        )

        assertEquals(2, policy.maximumGridStreams)
    }

    @Test
    fun singleH264InstanceStillKeepsMinimumTwoStreams() {
        val policy = resourcePolicyFor(
            capabilities(lowRam = false, decoderCapacity = 1, h264Instances = 1),
        )

        assertEquals(2, policy.maximumGridStreams)
    }

    @Test
    fun hevcOnlyDeviceWithStrongGenericCapacityKeepsFourStreams() {
        val policy = resourcePolicyFor(
            capabilities(lowRam = false, decoderCapacity = 8, h264Instances = 0, h265Instances = 4),
        )

        assertEquals(4, policy.maximumGridStreams)
    }

    @Test
    fun hevcOnlyDeviceWithWeakGenericCapacityIsCapped() {
        val policy = resourcePolicyFor(
            capabilities(lowRam = false, decoderCapacity = 1, h264Instances = 0, h265Instances = 1),
        )

        assertEquals(2, policy.maximumGridStreams)
    }

    @Test
    fun unknownCodecReportKeepsMinimumTwoStreams() {
        val policy = resourcePolicyFor(
            capabilities(lowRam = false, decoderCapacity = 0, h264Instances = 0, h265Instances = 0),
        )

        assertEquals(2, policy.maximumGridStreams)
    }

    private fun capabilities(
        lowRam: Boolean,
        decoderCapacity: Int,
        h264Instances: Int = decoderCapacity,
        h265Instances: Int = 0,
    ) = DeviceCapabilities(
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
        maximumConcurrentH264Decoders = h264Instances,
        maximumConcurrentH265Decoders = h265Instances,
    )
}
