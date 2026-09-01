package com.tvapp.livetv.image

import android.content.Context
import android.widget.ImageView
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.dispose
import coil.disk.DiskCache
import coil.load
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.tvapp.livetv.settings.LogoCachePreferencesStore

object ChannelLogoLoader {
    private var holder: LoaderHolder? = null

    fun load(imageView: ImageView, url: String?, fallbackRes: Int) {
        if (url.isNullOrBlank()) {
            imageView.dispose()
            imageView.setImageResource(fallbackRes)
            return
        }
        val current = loader(imageView.context)
        imageView.load(url, current.loader) {
            placeholder(fallbackRes)
            error(fallbackRes)
            fallback(fallbackRes)
            diskCachePolicy(if (current.diskEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED)
        }
    }

    @Synchronized
    fun invalidate() {
        holder?.loader?.shutdown()
        holder = null
    }

    @OptIn(ExperimentalCoilApi::class)
    @Synchronized
    fun clear(context: Context) {
        loader(context).loader.memoryCache?.clear()
        loader(context).loader.diskCache?.clear()
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
        holder?.takeIf {
            it.diskEnabled == preferences.enabled &&
                it.maximumMegabytes == preferences.maximumMegabytes
        }?.let { return it }
        holder?.loader?.shutdown()
        val loader = ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.12)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(logoCacheDirectory(appContext))
                    .maxSizeBytes(preferences.maximumMegabytes * 1_024L * 1_024L)
                    .build()
            }
            .build()
        return LoaderHolder(loader, preferences.enabled, preferences.maximumMegabytes)
            .also { holder = it }
    }

    private fun logoCacheDirectory(context: Context) = context.cacheDir.resolve("channel-logos")

    private data class LoaderHolder(
        val loader: ImageLoader,
        val diskEnabled: Boolean,
        val maximumMegabytes: Int,
    )
}
