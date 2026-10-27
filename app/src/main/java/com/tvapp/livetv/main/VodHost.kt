package com.tvapp.livetv.main

import android.content.Intent
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.vod.VodPageResolver
import com.tvapp.livetv.vod.WebVideoResolver

/**
 * MainActivity'deki VOD deep-link/resolve orkestrasyonu (Faz 1).
 *
 * Strangler deseni: once saf karar mantigi buraya tasinir, MainActivity ince
 * delege olarak kalir. Davranis degisikligi yok.
 *
 * `Host` dogrudan Activity field'larina dokunmaz; ihtiyac duydugu her seyi
 * [VodHostCallbacks] uzerinden alir/verir. Boylece MainActivity -> Host bagimlilik
 * yonu tek yonlu kalir, Host'lar birbirini cagirmaz.
 */
class VodHost(private val callbacks: VodHostCallbacks) {

    /**
     * Deep-link girisini siniflandirir: LIVE_MODE / DIRECT_STREAM / DB_KEY / NONE.
     *
     * Android Intent'e dokunmaz; MainActivity Intent'i [DeepLinkInput]'e cevirir.
     * Boylece bu karar saf JVM testinde dogrulanir.
     */
    fun classifyDeepLink(input: DeepLinkInput?): VodDeepLink {
        if (input == null) return VodDeepLink.None
        if (input.liveMode) return VodDeepLink.LiveMode(input.targetSource)
        val sourceKey = input.sourceKey
            ?: if (!input.directStreamUrl.isNullOrBlank()) "direct_vod_${System.currentTimeMillis()}" else null
            ?: return VodDeepLink.None
        return if (!input.directStreamUrl.isNullOrBlank()) {
            VodDeepLink.DirectStream(sourceKey, input.directStreamUrl)
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
}

/** MainActivity'deki EXTRA anahtarlarinin Host'tan erisilebilen gorunumu. */
object MainVodExtras {
    const val EXTRA_LIVE_MODE = "com.tvapp.livetv.extra.LIVE_MODE"
    const val EXTRA_LIVE_TARGET_SOURCE = "com.tvapp.livetv.extra.LIVE_TARGET_SOURCE"
    const val EXTRA_VOD_SOURCE_KEY = "com.tvapp.livetv.extra.VOD_SOURCE_KEY"
    const val EXTRA_VOD_STREAM_URL = "com.tvapp.livetv.extra.VOD_STREAM_URL"

    /** Activity tarafinda Intent -> [DeepLinkInput] cevirisi icin yardimci. */
    fun toInput(intent: Intent?): DeepLinkInput? {
        if (intent == null) return null
        return DeepLinkInput(
            liveMode = intent.getBooleanExtra(EXTRA_LIVE_MODE, false),
            targetSource = intent.getStringExtra(EXTRA_LIVE_TARGET_SOURCE),
            directStreamUrl = intent.getStringExtra(EXTRA_VOD_STREAM_URL),
            sourceKey = intent.getStringExtra(EXTRA_VOD_SOURCE_KEY),
        )
    }
}

/** Intent'ten cikarilmis saf deep-link girisi (Android bagimliligi yok). */
data class DeepLinkInput(
    val liveMode: Boolean = false,
    val targetSource: String? = null,
    val directStreamUrl: String? = null,
    val sourceKey: String? = null,
)

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

/** VodHost'un MainActivity'den bekledigi dar arayuz (henuz baglanmadi; Faz 1 adim 2). */
interface VodHostCallbacks {
    fun isVodMode(): Boolean
    fun currentGeneration(): Int
}
