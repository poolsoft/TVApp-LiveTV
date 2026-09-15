package com.tvapp.livetv.platform

data class DeviceResourcePolicy(
    val logoMemoryPercent: Double,
    val maximumLogoDiskMegabytes: Int,
    val logoPrefetchCount: Int,
    val maximumGridStreams: Int,
    val supportsMultiView: Boolean,
)

/**
 * Resolves how many simultaneous grid streams the device should attempt.
 *
 * The limit is derived from the codec the grid streams are most likely to use
 * (H.264 dominates IPTV catalogs). HEVC-only devices are assumed to keep at
 * least two concurrent decoders because their H.264 path falls back to the
 * software/extension decoder. The result never drops below [MINIMUM_GRID_STREAMS]
 * so a partial codec report cannot lock Multi-View to a single stream again.
 */
fun maximumGridStreamsFor(capabilities: DeviceCapabilities): Int {
    val h264 = capabilities.maximumConcurrentH264Decoders
    val anyHardware = capabilities.maximumConcurrentVideoDecoders
    val capabilityBased = when {
        h264 >= FOUR_STREAM_DECODER_INSTANCES -> FOUR_GRID_STREAMS
        h264 >= THREE_STREAM_DECODER_INSTANCES -> h264
        h264 > 0 -> h264.coerceAtLeast(MINIMUM_GRID_STREAMS)
        // No usable H.264 report: trust the generic hardware decoder capacity.
        anyHardware >= FOUR_STREAM_DECODER_INSTANCES -> FOUR_GRID_STREAMS
        anyHardware > 0 -> anyHardware.coerceIn(MINIMUM_GRID_STREAMS, THREE_GRID_STREAMS)
        // Unknown platform: stay conservative but never lock to one stream.
        else -> MINIMUM_GRID_STREAMS
    }
    return capabilityBased.coerceIn(MINIMUM_GRID_STREAMS, FOUR_GRID_STREAMS)
}

fun resourcePolicyFor(
    capabilities: DeviceCapabilities,
    forceFourGridStreams: Boolean = false,
): DeviceResourcePolicy {
    val lowMemory = capabilities.isLowRamDevice || capabilities.memoryClassMegabytes <= 192
    val codecGridStreams = maximumGridStreamsFor(capabilities)
    val gridStreams = when {
        forceFourGridStreams -> FOUR_GRID_STREAMS
        lowMemory -> MINIMUM_GRID_STREAMS
        else -> codecGridStreams
    }
    return DeviceResourcePolicy(
        logoMemoryPercent = if (lowMemory) 0.05 else 0.10,
        maximumLogoDiskMegabytes = if (lowMemory) 32 else 128,
        logoPrefetchCount = if (lowMemory) 1 else 4,
        maximumGridStreams = gridStreams,
        supportsMultiView = true,
    )
}

private const val MINIMUM_GRID_STREAMS = 2
private const val THREE_GRID_STREAMS = 3
private const val FOUR_GRID_STREAMS = 4
private const val THREE_STREAM_DECODER_INSTANCES = 3
private const val FOUR_STREAM_DECODER_INSTANCES = 4
