package com.tvapp.livetv.main

import android.content.Intent
import com.tvapp.livetv.R
import com.tvapp.livetv.data.local.VodMetadataEntity
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.vod.VodPageResolver
import com.tvapp.livetv.vod.WebVideoResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MainActivity'deki VOD deep-link/resolve orkestrasyonu (Faz 1).
 *
 * Davranis degisikligi yok: MainActivity'deki `handleVodSourceDeepLink` govdesi
 * buraya tasindi. Activity'ye bagimlilik [VodHostEnv] uzerinden tek yonludur
 * (MainActivity -> VodHost); Host baska bir Host'u cagirmaz.
 *
 * `Intent` sadece bir kez [handleDeepLink] girisinde okunur ve saf
 * [VodDeepLinkPayload]'a cevrilir; is mantigi Android'siz test edilir.
 *
 * Cozumleme isi (job) Host'a aittir; Activity yalnizca [cancelResolve] cagirir.
 */
class VodHost(
    private val scope: CoroutineScope,
    private val env: VodHostEnv,
) {

    private var resolveJob: Job? = null

    /** Devam eden cozumlemeyi iptal eder (mod degisimi / kapatma / yeni istek). */
    fun cancelResolve() {
        resolveJob?.cancel()
        resolveJob = null
    }

    /** Devam eden cozumlemenin bitmesini bekler (testler ve kapanis icin). */
    suspend fun awaitResolve() {
        resolveJob?.join()
    }

    /**
     * Deep-link girisini siniflandirir: LIVE_MODE / DIRECT_STREAM / DB_KEY / NONE.
     * Saf veri uzerinde calisir; Android Intent'e dokunmaz.
     */
    fun classifyDeepLink(payload: VodDeepLinkPayload?): VodDeepLink {
        if (payload == null) return VodDeepLink.None
        if (payload.liveMode) return VodDeepLink.LiveMode(payload.targetSource)
        val sourceKey = payload.sourceKey
            ?: if (!payload.streamUrl.isNullOrBlank()) "direct_vod_${System.currentTimeMillis()}" else null
            ?: return VodDeepLink.None
        return if (!payload.streamUrl.isNullOrBlank()) {
            VodDeepLink.DirectStream(sourceKey, payload.streamUrl)
        } else {
            VodDeepLink.LibraryKey(sourceKey)
        }
    }

    /** Dogrudan akis mi yoksa web cozumu mu gerektigini belirler. */
    fun needsWebResolve(streamUrl: String): Boolean = !WebVideoResolver.isDirectStream(streamUrl)

    /**
     * Web iceriginde tek guclu aday varsa onu, cok aday varsa ilk guclu adayi dondurur.
     * DB yolunda diyalog gosterilmez; secim MainActivity'deki mevcut kuralla aynidir.
     */
    fun pickDbCandidate(pendingChannel: LiveChannel, resolved: VodPageResolver.Resolved): LiveChannel {
        val chosen = resolved.single ?: resolved.candidates.firstOrNull()
        return if (chosen != null) resolved.applyTo(pendingChannel, chosen) else pendingChannel
    }

    /** Tek aday / cok aday / aday yok kararini uretir; diyalog karari caller'dadir. */
    fun decideCandidates(
        baseChannel: LiveChannel,
        resolved: VodPageResolver.Resolved,
    ): VodCandidateDecision = when {
        resolved.candidates.isEmpty() -> VodCandidateDecision.PlayBase(baseChannel)
        resolved.playable() != null -> VodCandidateDecision.PlayBase(
            resolved.applyTo(baseChannel, requireNotNull(resolved.playable())),
        )
        else -> VodCandidateDecision.ShowChoice(resolved, baseChannel)
    }

    /** VOD_SOURCE_KEY deep link'ini isler (cold start + warm onNewIntent). */
    fun handleDeepLink(intent: Intent?) {
        val payload = VodDeepLinkPayload.fromIntent(intent) ?: return
        handlePayload(payload)
    }

    /** Saf is mantigi: Android Intent'i olmadan da cagrilabilir. */
    fun handlePayload(payload: VodDeepLinkPayload) {
        if (payload.liveMode) {
            env.switchToLiveMode(payload.targetSource)
            return
        }
        val directStreamUrl = payload.streamUrl
        val sourceKey = payload.sourceKey
            ?: if (!directStreamUrl.isNullOrBlank()) "direct_vod_${System.currentTimeMillis()}" else null
            ?: return

        env.enterVodMode()
        env.showVodPlayerControls(autoHide = false)
        val requestGeneration = env.beginGeneration()
        if (payload.startOver) {
            env.saveResumePosition()
            env.stopIptvPlayback()
            env.clearResume(sourceKey)
        }

        if (!directStreamUrl.isNullOrBlank()) {
            handleDirectStream(payload, sourceKey, directStreamUrl, requestGeneration)
            return
        }

        if (env.currentChannelKey() == sourceKey && env.isTunedAndReady(sourceKey)) {
            env.resumeIptvPlayback()
            env.showVodPlayerControls(autoHide = true)
            return
        }

        // VOD items resolution directly from repository/DB
        env.stopIptvPlayback()
        env.showBufferingPreparing()

        cancelResolve()
        resolveJob = scope.launch {
            val channel = withContext(Dispatchers.IO) { buildLibraryChannel(sourceKey) }
            env.hideBuffering()
            if (!env.isVodMode() || requestGeneration != env.currentGeneration()) return@launch
            if (channel != null) {
                env.selectChannel(channel)
            } else {
                env.clearLastVodKey()
                env.showChannelNotFound()
            }
        }
    }

    private fun handleDirectStream(
        payload: VodDeepLinkPayload,
        sourceKey: String,
        directStreamUrl: String,
        requestGeneration: Int,
    ) {
        val mainTitle = payload.title ?: env.string(R.string.vod_archive_label)
        val subTitle = payload.episodeTitle
        env.setVodHasEpisodes(!subTitle.isNullOrBlank() || sourceKey.contains(":ep:"))

        env.stopIptvPlayback()
        val baseChannel = LiveChannel(
            id = sourceKey.hashCode().toLong(),
            sourceKey = sourceKey,
            inputId = "iptv:direct_vod",
            displayNumber = "VOD",
            displayName = mainTitle,
            source = LiveChannel.Source.IPTV,
            uri = directStreamUrl,
            logoUrl = payload.logoUrl,
            iptvContentType = "VOD",
            userAgent = payload.userAgent,
            referrer = payload.referrer,
            origin = payload.origin,
        ).apply {
            episodeTitle = subTitle
            cookie = payload.cookie
        }

        // Dogrudan medya akisi ise (mp4, m3u8, mkv vs.) web resolver'a girmeden direkt oynat
        if (WebVideoResolver.isDirectStream(directStreamUrl)) {
            env.selectChannel(baseChannel)
            return
        }

        // Web sayfasi / sniffer gerektiren URL ise cozumle (tek/cok adayli)
        env.showResolvingFeedback()
        cancelResolve()
        resolveJob = scope.launch {
            val resolved = withContext(Dispatchers.IO) { env.resolvePageWithContext(directStreamUrl) }
            env.hideResolvingFeedback()
            if (!env.isVodMode() || requestGeneration != env.currentGeneration()) return@launch
            when (val decision = decideCandidates(baseChannel, resolved)) {
                is VodCandidateDecision.PlayBase -> env.selectChannel(decision.channel)
                is VodCandidateDecision.ShowChoice -> env.showStreamChoice(decision.resolved, decision.baseChannel)
            }
        }
    }

    /**
     * Kutuphane/DB kaydindan oynatilabilir kanali kurar.
     * MainActivity'deki eski IO blogunun birebir karsiligidir.
     */
    private suspend fun buildLibraryChannel(sourceKey: String): LiveChannel? {
        val direct = env.findChannelInList(sourceKey)
            ?: runCatching { env.loadIptvChannel(sourceKey) }.getOrNull()

        if (direct != null) {
            val vodPending = direct.copy(iptvContentType = "VOD")
            return if (WebVideoResolver.isDirectStream(vodPending.uri)) {
                vodPending
            } else {
                // Web tabanli VOD icerigi: tek aday dogrudan oynar; cok adayda kilitlenmeyi
                // onlemek icin liste yolunda ilk guclu aday kullanilir (DB yolunda diyalog yok).
                VodLibraryResolver.applyDbResolve(
                    vodPending,
                    VodPageResolver.resolve(vodPending.uri, null),
                )
            }
        }

        var meta = runCatching { env.loadMetadata(sourceKey) }.getOrNull()
        var dynamicEpTitle: String? = null

        // JSON VOD dizi bolumu cozumlemesi (sourceKey format: parentKey:ep:index_hash)
        if (meta == null && sourceKey.contains(":ep:")) {
            val parentKey = sourceKey.substringBefore(":ep:")
            val parentMeta = runCatching { env.loadMetadata(parentKey) }.getOrNull()
            val dyn = runCatching {
                VodLibraryResolver.resolveDynamicEpisode(sourceKey, meta, parentMeta)
            }.getOrNull()
            if (dyn == null && parentMeta?.streamUrl?.startsWith("[") == true) {
                env.log("Parse dynamic JSON episode in resolve failed for $sourceKey")
            }
            if (dyn != null) {
                meta = dyn.meta
                dynamicEpTitle = dyn.dynamicEpTitle
            }
        }

        val resolvedMeta = meta ?: return null
        val streamUrl = resolvedMeta.streamUrl
        if (streamUrl.isNullOrBlank()) return null

        env.setVodHasEpisodes(VodLibraryResolver.hasEpisodes(resolvedMeta, sourceKey))
        val parentTitle = resolvedMeta.parentKey?.let { pKey ->
            runCatching { env.loadParentName(pKey) }.getOrNull()
        }
        val (mainTitle, subTitle) = VodLibraryResolver.titles(
            resolvedMeta,
            parentTitle,
            dynamicEpTitle,
        ) { season, episode -> env.seasonEpisodeLabel(season, episode) }

        val pendingChannel = VodLibraryResolver.buildChannel(resolvedMeta, mainTitle, subTitle)
        // Eger dogrudan medya akisi ise web resolver calistirma
        return if (WebVideoResolver.isDirectStream(streamUrl)) {
            pendingChannel
        } else {
            VodLibraryResolver.applyDbResolve(
                pendingChannel,
                VodPageResolver.resolve(streamUrl, null),
            )
        }
    }
}

/** VOD deep-link extra anahtarlari. */
object MainVodExtras {
    const val EXTRA_LIVE_MODE = "com.tvapp.livetv.extra.LIVE_MODE"
    const val EXTRA_LIVE_TARGET_SOURCE = "com.tvapp.livetv.extra.LIVE_TARGET_SOURCE"
    const val EXTRA_VOD_SOURCE_KEY = "com.tvapp.livetv.extra.VOD_SOURCE_KEY"
    const val EXTRA_VOD_STREAM_URL = "com.tvapp.livetv.extra.VOD_STREAM_URL"
    const val EXTRA_VOD_TITLE = "com.tvapp.livetv.extra.VOD_TITLE"
    const val EXTRA_VOD_EPISODE_TITLE = "com.tvapp.livetv.extra.VOD_EPISODE_TITLE"
    const val EXTRA_VOD_LOGO_URL = "com.tvapp.livetv.extra.VOD_LOGO_URL"
    const val EXTRA_VOD_START_OVER = "com.tvapp.livetv.extra.VOD_START_OVER"
    const val EXTRA_VOD_REFERRER = "com.tvapp.livetv.extra.VOD_REFERRER"
    const val EXTRA_VOD_ORIGIN = "com.tvapp.livetv.extra.VOD_ORIGIN"
    const val EXTRA_VOD_USER_AGENT = "com.tvapp.livetv.extra.VOD_USER_AGENT"
    const val EXTRA_VOD_COOKIE = "com.tvapp.livetv.extra.VOD_COOKIE"
}

/**
 * Intent'ten bir kez okunmus, saf deep-link verisi.
 *
 * MainActivity'deki sirali `removeExtra` cagrilari [fromIntent] icinde toplanir;
 * boylece onResume/focus tekrar tetiklemesi engellenir.
 */
data class VodDeepLinkPayload(
    val liveMode: Boolean = false,
    val targetSource: String? = null,
    val streamUrl: String? = null,
    val sourceKey: String? = null,
    val startOver: Boolean = false,
    val title: String? = null,
    val episodeTitle: String? = null,
    val logoUrl: String? = null,
    val referrer: String? = null,
    val origin: String? = null,
    val userAgent: String? = null,
    val cookie: String? = null,
) {
    companion object {
        /** Intent'i okur ve ayni anda extra'lari temizler. */
        fun fromIntent(intent: Intent?): VodDeepLinkPayload? {
            if (intent == null) return null
            val payload = VodDeepLinkPayload(
                liveMode = intent.getBooleanExtra(MainVodExtras.EXTRA_LIVE_MODE, false),
                targetSource = intent.getStringExtra(MainVodExtras.EXTRA_LIVE_TARGET_SOURCE),
                streamUrl = intent.getStringExtra(MainVodExtras.EXTRA_VOD_STREAM_URL),
                sourceKey = intent.getStringExtra(MainVodExtras.EXTRA_VOD_SOURCE_KEY),
                startOver = intent.getBooleanExtra(MainVodExtras.EXTRA_VOD_START_OVER, false),
                title = intent.getStringExtra(MainVodExtras.EXTRA_VOD_TITLE),
                episodeTitle = intent.getStringExtra(MainVodExtras.EXTRA_VOD_EPISODE_TITLE),
                logoUrl = intent.getStringExtra(MainVodExtras.EXTRA_VOD_LOGO_URL),
                referrer = intent.getStringExtra(MainVodExtras.EXTRA_VOD_REFERRER),
                origin = intent.getStringExtra(MainVodExtras.EXTRA_VOD_ORIGIN),
                userAgent = intent.getStringExtra(MainVodExtras.EXTRA_VOD_USER_AGENT),
                cookie = intent.getStringExtra(MainVodExtras.EXTRA_VOD_COOKIE),
            )
            // Consume immediately so re-entering onResume/focus cannot re-trigger the deep link.
            intent.removeExtra(MainVodExtras.EXTRA_LIVE_MODE)
            intent.removeExtra(MainVodExtras.EXTRA_LIVE_TARGET_SOURCE)
            intent.removeExtra(MainVodExtras.EXTRA_VOD_STREAM_URL)
            intent.removeExtra(MainVodExtras.EXTRA_VOD_SOURCE_KEY)
            intent.removeExtra(MainVodExtras.EXTRA_VOD_START_OVER)
            intent.removeExtra(MainVodExtras.EXTRA_VOD_TITLE)
            intent.removeExtra(MainVodExtras.EXTRA_VOD_EPISODE_TITLE)
            intent.removeExtra(MainVodExtras.EXTRA_VOD_LOGO_URL)
            intent.removeExtra(MainVodExtras.EXTRA_VOD_REFERRER)
            intent.removeExtra(MainVodExtras.EXTRA_VOD_ORIGIN)
            intent.removeExtra(MainVodExtras.EXTRA_VOD_USER_AGENT)
            intent.removeExtra(MainVodExtras.EXTRA_VOD_COOKIE)
            return payload
        }
    }
}

/** Deep-link siniflandirma sonucu. */
sealed interface VodDeepLink {
    data object None : VodDeepLink
    data class LiveMode(val targetSource: String?) : VodDeepLink
    data class DirectStream(val sourceKey: String, val streamUrl: String) : VodDeepLink
    data class LibraryKey(val sourceKey: String) : VodDeepLink
}

/** Aday karar sonucu. */
sealed interface VodCandidateDecision {
    data class PlayBase(val channel: LiveChannel) : VodCandidateDecision
    data class ShowChoice(
        val resolved: VodPageResolver.Resolved,
        val baseChannel: LiveChannel,
    ) : VodCandidateDecision
}

/**
 * VodHost'un Activity'den bekledigi dar arayuz.
 *
 * Tum UI/oynatma/saklama islemleri buradan gecer; Host dogrudan Activity
 * alanlarina dokunmaz. Boylece tasima sirasinda davranis degismez.
 */
interface VodHostEnv {
    fun isVodMode(): Boolean
    fun currentGeneration(): Int
    fun beginGeneration(): Int
    fun enterVodMode()
    fun switchToLiveMode(targetSource: String?)
    fun showVodPlayerControls(autoHide: Boolean)
    fun saveResumePosition()
    fun stopIptvPlayback()
    fun resumeIptvPlayback()
    fun clearResume(sourceKey: String)
    fun setVodHasEpisodes(hasEpisodes: Boolean)
    fun currentChannelKey(): String?
    fun isTunedAndReady(sourceKey: String): Boolean
    fun selectChannel(channel: LiveChannel)
    fun showResolvingFeedback()
    fun hideResolvingFeedback()
    fun showBufferingPreparing()
    fun hideBuffering()
    fun string(resId: Int): String
    fun seasonEpisodeLabel(season: Int, episode: Int): String
    fun showStreamChoice(resolved: VodPageResolver.Resolved, baseChannel: LiveChannel)
    fun showChannelNotFound()
    fun clearLastVodKey()
    fun log(message: String)
    fun findChannelInList(sourceKey: String): LiveChannel?
    suspend fun loadIptvChannel(sourceKey: String): LiveChannel?
    suspend fun loadMetadata(sourceKey: String): VodMetadataEntity?
    suspend fun loadParentName(parentKey: String): String?
    suspend fun resolvePageWithContext(url: String): VodPageResolver.Resolved
}
