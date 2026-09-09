package com.tvapp.livetv.platform

import com.tvapp.livetv.settings.ExperienceModeOverride
import org.junit.Assert.assertEquals
import org.junit.Test

class ExperienceModeResolverTest {
    @Test
    fun vendorTunerUsesHybridModeEvenWhenLiveTvFeatureIsMissing() {
        val capabilities = capabilities(
            reportsLiveTvFeature = false,
            hasTvInputManager = true,
            vendorTunerInputCount = 1,
        )

        assertEquals(
            ExperienceMode.HYBRID_TV,
            resolveExperienceMode(capabilities, mobileUiEnabled = false),
        )
    }

    @Test
    fun liveTvFeatureWithoutUsableTunerUsesIptvOnlyMode() {
        val capabilities = capabilities(
            reportsLiveTvFeature = true,
            hasTvInputManager = true,
            vendorTunerInputCount = 0,
        )

        assertEquals(
            ExperienceMode.IPTV_ONLY_TV,
            resolveExperienceMode(capabilities, mobileUiEnabled = false),
        )
    }

    @Test
    fun missingTvInputManagerUsesIptvOnlyMode() {
        val capabilities = capabilities(
            hasTvInputManager = false,
            vendorTunerInputCount = 0,
        )

        assertEquals(
            ExperienceMode.IPTV_ONLY_TV,
            resolveExperienceMode(capabilities, mobileUiEnabled = false),
        )
    }

    @Test
    fun mobileVariantAlwaysUsesMobileTestMode() {
        val capabilities = capabilities(
            hasTvInputManager = true,
            vendorTunerInputCount = 2,
        )

        assertEquals(
            ExperienceMode.MOBILE_TEST,
            resolveExperienceMode(capabilities, mobileUiEnabled = true),
        )
    }

    @Test
    fun iptvOnlyOverrideDisablesAvailableTuner() {
        val capabilities = capabilities(hasTvInputManager = true, vendorTunerInputCount = 1)

        assertEquals(
            ExperienceMode.IPTV_ONLY_TV,
            resolveExperienceMode(
                capabilities,
                mobileUiEnabled = false,
                override = ExperienceModeOverride.IPTV_ONLY,
            ),
        )
    }

    @Test
    fun unavailableHybridOverrideFallsBackSafely() {
        assertEquals(
            ExperienceMode.IPTV_ONLY_TV,
            resolveExperienceMode(
                capabilities(),
                mobileUiEnabled = false,
                override = ExperienceModeOverride.HYBRID,
            ),
        )
    }

    private fun capabilities(
        reportsLiveTvFeature: Boolean = false,
        hasTvInputManager: Boolean = false,
        vendorTunerInputCount: Int = 0,
    ) = DeviceCapabilities(
        isLeanbackDevice = true,
        reportsLiveTvFeature = reportsLiveTvFeature,
        hasTvInputManager = hasTvInputManager,
        vendorTunerInputCount = vendorTunerInputCount,
        hasTvListingsPermission = false,
        supportsPictureInPicture = false,
        isLowRamDevice = false,
        memoryClassMegabytes = 256,
        hardwareVideoDecoderCount = 2,
        maximumConcurrentVideoDecoders = 4,
    )
}
