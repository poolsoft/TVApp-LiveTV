package com.tvapp.livetv.platform

import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.media.tv.TvInputManager
import androidx.core.content.ContextCompat
import com.tvapp.livetv.data.TifRepository

data class DeviceCapabilities(
    val isLeanbackDevice: Boolean,
    val reportsLiveTvFeature: Boolean,
    val hasTvInputManager: Boolean,
    val vendorTunerInputCount: Int,
    val hasTvListingsPermission: Boolean,
    val supportsPictureInPicture: Boolean,
    val isLowRamDevice: Boolean,
) {
    val hasVendorTuner: Boolean
        get() = hasTvInputManager && vendorTunerInputCount > 0
}

enum class ExperienceMode {
    HYBRID_TV,
    IPTV_ONLY_TV,
    MOBILE_TEST,
}

class DeviceCapabilitiesDetector(context: Context) {
    private val appContext = context.applicationContext

    fun detect(): DeviceCapabilities {
        val packageManager = appContext.packageManager
        val hasTvInputManager = appContext.getSystemService(TvInputManager::class.java) != null
        val tunerCount = if (hasTvInputManager) {
            runCatching { TifRepository(appContext).tunerInputs().size }.getOrDefault(0)
        } else {
            0
        }
        return DeviceCapabilities(
            isLeanbackDevice = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            reportsLiveTvFeature = packageManager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV),
            hasTvInputManager = hasTvInputManager,
            vendorTunerInputCount = tunerCount,
            hasTvListingsPermission = ContextCompat.checkSelfPermission(
                appContext,
                READ_TV_LISTINGS,
            ) == PackageManager.PERMISSION_GRANTED,
            supportsPictureInPicture = packageManager.hasSystemFeature(
                PackageManager.FEATURE_PICTURE_IN_PICTURE,
            ) && runCatching { PictureInPictureParams.Builder().build() }.isSuccess,
            isLowRamDevice = appContext.getSystemService(ActivityManager::class.java)
                ?.isLowRamDevice == true,
        )
    }

    private companion object {
        const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
    }
}

fun resolveExperienceMode(
    capabilities: DeviceCapabilities,
    mobileUiEnabled: Boolean,
): ExperienceMode = when {
    mobileUiEnabled -> ExperienceMode.MOBILE_TEST
    capabilities.hasVendorTuner -> ExperienceMode.HYBRID_TV
    else -> ExperienceMode.IPTV_ONLY_TV
}

