package com.tvapp.livetv.image

import android.content.Context
import android.widget.ImageView
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.dispose
import coil.disk.DiskCache
import coil.load
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.tvapp.livetv.platform.DeviceCapabilities
import com.tvapp.livetv.platform.DeviceCapabilitiesSession
import com.tvapp.livetv.platform.DeviceResourcePolicy
import com.tvapp.livetv.platform.resourcePolicyFor
import com.tvapp.livetv.settings.LogoCachePreferencesStore

object ChannelLogoLoader {
    private var holder: LoaderHolder? = null
    private var configuredPolicy: DeviceResourcePolicy? = null
    private val failedRequests = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun load(imageView: ImageView, data: Any?, fallbackRes: Int) {
        if (data == null || data is String && data.isBlank()) {
            imageView.dispose()
            imageView.setImageResource(fallbackRes)
            return
        }
        val requestKey = data.toString()
        if (requestKey in failedRequests) {
            imageView.dispose()
            imageView.setImageResource(fallbackRes)
            return
        }
        val current = loader(imageView.context)
        imageView.load(data, current.loader) {
            placeholder(fallbackRes)
            error(fallbackRes)
            fallback(fallbackRes)
            listener(
                onSuccess = { _, _ -> failedRequests.remove(requestKey) },
                onError = { _, _ -> failedRequests.add(requestKey) },
            )
            diskCachePolicy(if (current.diskEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED)
        }
    }

    @Synchronized
    fun configure(context: Context, capabilities: DeviceCapabilities) {
        val policy = resourcePolicyFor(capabilities)
        if (configuredPolicy == policy) return
        configuredPolicy = policy
        invalidate()
        loader(context)
    }

    fun prefetch(context: Context, items: Sequence<Any?>) {
        val current = loader(context)
        items
            .filterNotNull()
            .filterNot { it is String && it.isBlank() }
            .distinctBy(Any::toString)
            .take(current.prefetchCount)
            .forEach { data ->
                current.loader.enqueue(
                    ImageRequest.Builder(context.applicationContext)
                        .data(data)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(
                            if (current.diskEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED,
                        )
                        .build(),
                )
            }
    }

    fun trimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            holder?.loader?.memoryCache?.clear()
            failedRequests.clear()
        }
    }

    @Synchronized
    fun invalidate() {
        holder?.loader?.shutdown()
        holder = null
        failedRequests.clear()
    }

    @OptIn(ExperimentalCoilApi::class)
    @Synchronized
    fun clear(context: Context) {
        loader(context).loader.memoryCache?.clear()
        loader(context).loader.diskCache?.clear()
        failedRequests.clear()
    }

    fun cacheSizeBytes(context: Context): Long = runCatching {
        logoCacheDirectory(context).walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }.getOrDefault(0L)

    @Synchronized
    private fun loader(context: Context): LoaderHolder {
        val appContext = context.applicationContext
        val preferences = LogoCachePreferencesStore(appContext).load()
        val policy = configuredPolicy ?: resourcePolicyFor(DeviceCapabilitiesSession.get(appContext))
            .also { configuredPolicy = it }
        val effectiveMaximumMegabytes = minOf(
            preferences.maximumMegabytes,
            policy.maximumLogoDiskMegabytes,
        )
        holder?.takeIf {
            it.diskEnabled == preferences.enabled &&
                it.maximumMegabytes == effectiveMaximumMegabytes &&
                it.memoryPercent == policy.logoMemoryPercent &&
                it.prefetchCount == policy.logoPrefetchCount
        }?.let { return it }
        holder?.loader?.shutdown()
        val loader = ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(policy.logoMemoryPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(logoCacheDirectory(appContext))
                    .maxSizeBytes(effectiveMaximumMegabytes * 1_024L * 1_024L)
                    .build()
            }
            .build()
        return LoaderHolder(
            loader,
            preferences.enabled,
            effectiveMaximumMegabytes,
            policy.logoMemoryPercent,
            policy.logoPrefetchCount,
        )
            .also { holder = it }
    }

    private fun logoCacheDirectory(context: Context) = context.cacheDir.resolve("channel-logos")

    private data class LoaderHolder(
        val loader: ImageLoader,
        val diskEnabled: Boolean,
        val maximumMegabytes: Int,
        val memoryPercent: Double,
        val prefetchCount: Int,
    )
}
