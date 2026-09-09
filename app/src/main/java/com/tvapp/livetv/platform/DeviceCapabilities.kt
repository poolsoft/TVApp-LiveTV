package com.tvapp.livetv.platform

import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaCodecList
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
    val memoryClassMegabytes: Int,
    val hardwareVideoDecoderCount: Int,
    val maximumConcurrentVideoDecoders: Int,
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
        val activityManager = appContext.getSystemService(ActivityManager::class.java)
        val hasTvInputManager = appContext.getSystemService(TvInputManager::class.java) != null
        val tunerCount = if (hasTvInputManager) {
            runCatching { TifRepository(appContext).tunerInputs().size }.getOrDefault(0)
        } else {
            0
        }
        val decoderSummary = detectVideoDecoders()
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
            isLowRamDevice = activityManager?.isLowRamDevice == true,
            memoryClassMegabytes = activityManager?.memoryClass ?: 0,
            hardwareVideoDecoderCount = decoderSummary.first,
            maximumConcurrentVideoDecoders = decoderSummary.second,
        )
    }

    private fun detectVideoDecoders(): Pair<Int, Int> = runCatching {
        val decoders = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { info ->
            !info.isEncoder && info.isHardwareAccelerated &&
                info.supportedTypes.any { it.startsWith("video/", ignoreCase = true) }
        }
        val maximumInstances = decoders.maxOfOrNull { info ->
            info.supportedTypes.asSequence()
                .filter { it.startsWith("video/", ignoreCase = true) }
                .mapNotNull { type ->
                    runCatching { info.getCapabilitiesForType(type).maxSupportedInstances }
                        .getOrNull()
                }
                .maxOrNull() ?: 1
        } ?: 1
        decoders.size to maximumInstances.coerceAtLeast(1)
    }.getOrDefault(0 to 1)

    private companion object {
        const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
    }
}

object DeviceCapabilitiesSession {
    @Volatile
    private var cached: DeviceCapabilities? = null

    fun get(context: Context): DeviceCapabilities = cached ?: synchronized(this) {
        cached ?: DeviceCapabilitiesDetector(context).detect().also { cached = it }
    }
}

fun resolveExperienceMode(
    capabilities: DeviceCapabilities,
    mobileUiEnabled: Boolean,
    override: com.tvapp.livetv.settings.ExperienceModeOverride =
        com.tvapp.livetv.settings.ExperienceModeOverride.AUTOMATIC,
): ExperienceMode = when {
    mobileUiEnabled -> ExperienceMode.MOBILE_TEST
    override == com.tvapp.livetv.settings.ExperienceModeOverride.IPTV_ONLY ->
        ExperienceMode.IPTV_ONLY_TV
    override == com.tvapp.livetv.settings.ExperienceModeOverride.HYBRID &&
        capabilities.hasVendorTuner -> ExperienceMode.HYBRID_TV
    override == com.tvapp.livetv.settings.ExperienceModeOverride.HYBRID ->
        ExperienceMode.IPTV_ONLY_TV
    capabilities.hasVendorTuner -> ExperienceMode.HYBRID_TV
    else -> ExperienceMode.IPTV_ONLY_TV
}
