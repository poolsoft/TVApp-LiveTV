package com.tvapp.livetv.main

import com.tvapp.livetv.data.local.VodMetadataEntity
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.vod.VodPageResolver
import com.tvapp.livetv.vod.VodUiSupport

/**
 * MainActivity'deki VOD kutuphane/DB cozumleme blogunun saf karsiligi (Faz 1).
 *
 * Kurallar MainActivity ile birebir aynidir; sadece IO (DAO) disarida birakilir:
 * caller DAO'dan `meta`/`parentMeta` okur, karari burada alir.
 */
object VodLibraryResolver {

    data class ResolvedMeta(
        val meta: VodMetadataEntity,
        val dynamicEpTitle: String?,
        val hasEpisodes: Boolean,
    )

    /**
     * `:ep:` dinamik bolum yolunu cozumler.
     * MainActivity'deki `sourceKey:ep:index_hash` blogunun aynisidir.
     */
    fun resolveDynamicEpisode(
        sourceKey: String,
        meta: VodMetadataEntity?,
        parentMeta: VodMetadataEntity?,
    ): ResolvedMeta? {
        if (meta != null || !sourceKey.contains(":ep:") || parentMeta == null) return null
        val parentStream = parentMeta.streamUrl
        if (parentStream.isNullOrBlank() || !parentStream.startsWith("[")) return null
        return try {
            val epArray = org.json.JSONArray(parentStream)
            val epPart = sourceKey.substringAfter(":ep:")
            val epIdx = epPart.substringBefore('_').toIntOrNull() ?: 0
            if (epIdx !in 0 until epArray.length()) return null
            val parsed = VodUiSupport.dynamicEpisode(epArray.optJSONObject(epIdx), epIdx) ?: return null
            ResolvedMeta(
                meta = parentMeta.copy(sourceKey = sourceKey, streamUrl = parsed.url),
                dynamicEpTitle = parsed.title,
                hasEpisodes = true,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** `currentVodHasEpisodes` bayraginin MainActivity'deki kurali. */
    fun hasEpisodes(meta: VodMetadataEntity?, sourceKey: String): Boolean {
        if (meta == null) return false
        return meta.parentKey != null || meta.kind == "SERIES" ||
            (!meta.streamUrl.isNullOrBlank() && meta.streamUrl.startsWith("[")) ||
            sourceKey.contains(":ep:")
    }

    /**
     * Baslik kurali: parent varsa `parentAdi` + bolum alt-basligi, yoksa meta adi.
     * Alt-baslik icin once dinamik bolum basligi, sonra `vod_season_episode_fmt` kullanilir.
     */
    fun titles(
        meta: VodMetadataEntity,
        parentName: String?,
        dynamicEpTitle: String?,
        seasonEpisodeFmt: (season: Int, episode: Int) -> String,
    ): Pair<String, String?> {
        val mainTitle = parentName ?: meta.name
        val subTitle = dynamicEpTitle ?: if (parentName != null) {
            val sNum = meta.seasonNumber ?: 1
            val epNum = meta.episodeNumber
            if (epNum != null) seasonEpisodeFmt(sNum, epNum) else meta.name
        } else {
            null
        }
        return mainTitle to subTitle
    }

    /** Meta kaydindan calinabilir kanal kurar (MainActivity'deki pendingChannel ile ayni). */
    fun buildChannel(
        meta: VodMetadataEntity,
        mainTitle: String,
        subTitle: String?,
    ): LiveChannel = LiveChannel(
        id = meta.sourceKey.hashCode().toLong(),
        sourceKey = meta.sourceKey,
        inputId = "iptv:${meta.sourceId}",
        displayNumber = "VOD",
        displayName = mainTitle,
        source = LiveChannel.Source.IPTV,
        uri = meta.streamUrl.orEmpty(),
        logoUrl = meta.logoUrl,
        iptvContentType = "VOD",
    ).apply {
        episodeTitle = subTitle
    }

    /** DB yolunda web cozumu sonrasi kanal secsimi (MainActivity ile ayni: ilk guclu aday). */
    fun applyDbResolve(pendingChannel: LiveChannel, resolved: VodPageResolver.Resolved): LiveChannel {
        val chosen = resolved.single ?: resolved.candidates.firstOrNull() ?: return pendingChannel
        return resolved.applyTo(pendingChannel, chosen)
    }
}
