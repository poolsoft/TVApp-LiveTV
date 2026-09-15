package com.tvapp.livetv.platform

data class DeviceResourcePolicy(
    val logoMemoryPercent: Double,
    val maximumLogoDiskMegabytes: Int,
    val logoPrefetchCount: Int,
    val maximumGridStreams: Int,
    val supportsMultiView: Boolean,
)

fun resourcePolicyFor(capabilities: DeviceCapabilities): DeviceResourcePolicy {
    val lowMemory = capabilities.isLowRamDevice || capabilities.memoryClassMegabytes <= 192
    return DeviceResourcePolicy(
        logoMemoryPercent = if (lowMemory) 0.05 else 0.10,
        maximumLogoDiskMegabytes = if (lowMemory) 32 else 128,
        logoPrefetchCount = if (lowMemory) 1 else 4,
        maximumGridStreams = if (lowMemory) 2 else 4,
        supportsMultiView = true,
    )
}
