package com.tvapp.livetv.platform

import com.tvapp.livetv.model.LiveChannel

/**
 * "Canlı TV'ye dön" isteğinden çıkan hedef.
 *
 * [token] kaynak TÜRU hedefidir (`TIF` / `IPTV`), [sourceKey] ise derin bağlantıda
 * (`tvapp://channel/open?sourceKey=...`) taşınan belirli kanal anahtarıdır.
 */
data class LiveLaunchTarget(
    val token: String? = null,
    val sourceKey: String? = null,
) {
    val isEmpty: Boolean get() = token == null && sourceKey == null
}

/**
 * UI-014: canlıya dönüş isteğini oynatılacak kanala çeviren saf kural.
 *
 * Token semantiği TV kabuğundan alınmıştır (`MainActivity.switchToLiveMode`): hedef bir IPTV
 * listesi anahtarı değil kaynak TÜRÜ adadır (`"TIF"` / `"IPTV"`); başka bir değer ya da boş
 * hedef "mevcut oynatmayı koru" anlamına gelir. VOD içerikli satırlar canlı sayılmaz.
 *
 * Bilinçli sınır: kural asla "ilgili bir kanal uydurmaz". Eşleşme yoksa `null` döner ve
 * çağıran taraf mevcut oynatmasına devam eder. Mobil kabuk yalnız IPTV kanallarıyla çalıştığı
 * için (`channels(includeTif = false)`) `TIF` isteği orada karşılıksızdır.
 */
object LiveLaunchRules {

    const val TARGET_TIF = "TIF"
    const val TARGET_IPTV = "IPTV"

    /** Önce token, sonra belirli kaynak anahtarı; ikisi de tutmazsa null. */
    fun resolve(channels: List<LiveChannel>, target: LiveLaunchTarget?): LiveChannel? {
        if (target == null || target.isEmpty) return null
        val byToken = when (target.token) {
            TARGET_TIF -> channels.firstOrNull { it.source == LiveChannel.Source.TIF && !it.isVodContent() }
            TARGET_IPTV -> channels.firstOrNull {
                it.source == LiveChannel.Source.IPTV && !it.isVodContent()
            }
            else -> null
        }
        if (byToken != null) return byToken
        val key = target.sourceKey ?: return null
        return channels.firstOrNull { it.sourceKey == key }
    }
}
