package com.tvapp.livetv.main

import com.tvapp.livetv.data.IptvPageDirection
import com.tvapp.livetv.model.LiveChannel

/**
 * Faz 3: IPTV kutuphanesi anahtar-kumesi (keyset) penceresinin saf sayim
 * kurallari.
 *
 * 15.000+ kayitli kataloglarda pencere basi, onceki/sonraki bayraklari ve
 * gorunen numaralandirma tek yerde tutulur. Veritabani okuma isi
 * [com.tvapp.livetv.data.IptvRepository] tarafinda, UI isi MainActivity'de kalir.
 */
object IptvLibraryPagingRules {

    /** Pencerenin konumu ve gezinme bayraklari. */
    data class Window(
        val windowStart: Int,
        val hasPrevious: Boolean,
        val hasNext: Boolean,
    )

    /**
     * Yeni pencerenin baslangic indeksi.
     *
     * @param currentWindowStart onceki pencerenin baslangici
     * @param previousWindowSize onceki pencerede yuklenmis kayit sayisi
     * @param loadedSize yeni pencerede yuklenen kayit sayisi
     */
    fun windowStart(
        direction: IptvPageDirection,
        currentWindowStart: Int,
        previousWindowSize: Int,
        loadedSize: Int,
        totalCount: Int,
        targetIndex: Int,
    ): Int = when (direction) {
        IptvPageDirection.FIRST -> 0
        IptvPageDirection.NEXT -> currentWindowStart + previousWindowSize
        IptvPageDirection.PREVIOUS ->
            (currentWindowStart - loadedSize).coerceAtLeast(0)
        IptvPageDirection.LAST ->
            (totalCount - loadedSize).coerceAtLeast(0)
        IptvPageDirection.AT_INDEX -> targetIndex.coerceIn(
            0,
            (totalCount - loadedSize).coerceAtLeast(0),
        )
    }

    /** Pencere konumu + gezinme bayraklari birlikte. */
    fun window(
        direction: IptvPageDirection,
        currentWindowStart: Int,
        previousWindowSize: Int,
        loadedSize: Int,
        totalCount: Int,
        targetIndex: Int,
    ): Window {
        val start = windowStart(
            direction = direction,
            currentWindowStart = currentWindowStart,
            previousWindowSize = previousWindowSize,
            loadedSize = loadedSize,
            totalCount = totalCount,
            targetIndex = targetIndex,
        )
        return Window(
            windowStart = start,
            hasPrevious = start > 0,
            hasNext = start + loadedSize < totalCount,
        )
    }

    /**
     * Yukleme sonrasi odaklanacak satir: geriye/sona gidislerde pencerenin sonu,
     * diger yonlerde basi.
     */
    fun focusTarget(direction: IptvPageDirection, loadedCount: Int): Int =
        when (direction) {
            IptvPageDirection.PREVIOUS, IptvPageDirection.LAST ->
                (loadedCount - 1).coerceAtLeast(0)
            else -> 0
        }

    /** Pencere icindeki kanallari katalog sirasina gore numaralandirir. */
    fun renumber(channels: List<LiveChannel>, windowStart: Int): List<LiveChannel> =
        channels.mapIndexed { index, channel ->
            channel.copy(displayNumber = (windowStart + index + 1).toString())
        }
}
