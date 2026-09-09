package com.tvapp.livetv.platform

data class DeviceResourcePolicy(
    val logoMemoryPercent: Double,
    val maximumLogoDiskMegabytes: Int,
    val logoPrefetchCount: Int,
    val maximumGridStreams: Int,
    val supportsMultiView: Boolean,
)

fun resourcePolicyFor(capabilities: DeviceCapabilities): DeviceResourcePolicy {
    val decoderCapacity = capabilities.maximumConcurrentVideoDecoders.coerceAtLeast(1)
    val lowMemory = capabilities.isLowRamDevice || capabilities.memoryClassMegabytes <= 192
    return DeviceResourcePolicy(
        logoMemoryPercent = if (lowMemory) 0.05 else 0.10,
        maximumLogoDiskMegabytes = if (lowMemory) 32 else 128,
        logoPrefetchCount = if (lowMemory) 1 else 4,
        maximumGridStreams = minOf(if (lowMemory) 2 else 4, decoderCapacity),
        supportsMultiView = !lowMemory && decoderCapacity >= 2,
    )
}
