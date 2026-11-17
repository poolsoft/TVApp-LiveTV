package com.tvapp.livetv.ui

import com.tvapp.livetv.playback.IptvPlaybackProfile

/**
 * PERF-028: kanal editörünün önizleme kararları.
 *
 * Editörde ok tuşuyla gezinirken her duruş bir önizleme başlatıyordu ve o önizleme tam ekran
 * oynatma ile **aynı** profilde açılıyordu: sınırsız çözünürlük/bit hızı, kullanıcının tampon
 * tercihi (varsayılan 20 sn) ve 8 MB canlı ön-indirme. Üstelik yeni önizleme planlanırken
 * çalışan akış kesilmediği için, kullanıcı satırlar arasında gezindiği sürece eski akış
 * indirmeye ve sağlık bekçisi (watchdog) ana iş parçacığında çalışmaya devam ediyordu —
 * "editörde hantallık var, sanki önizleme için bir ağırlık oluşuyor" şikâyetinin kaynağı.
 *
 * Kararlar oynatıcıdan bağımsız ve saf tutulur: hangi durumda hiç oynatma planlanmayacağı,
 * ne zaman çalışan akışın kesileceği ve önizlemenin hangi kalite tavanıyla açılacağı burada
 * birim testiyle kilitlenir.
 */
object EditorPreviewRules {

    /**
     * Önizleme kutusu 260dp'lik bir yan panel; Multi-View'ın iki hücrede (düşük güçlü SoC
     * sınırı için) kullandığı tavanlı profilden fazlasına ihtiyacı yok.
     * [IptvPlaybackProfile.PRIMARY] tam ekran oynatmanın konfigürasyonudur: çözünürlük ve bit
     * hızı sınırı yok, tampon kullanıcının ayarına göre 20 sn'e kadar çıkıyor, canlı ön-indirme
     * 8 MB. Tavanlı profil tamponu 10 sn'ye, ön-indirmeyi 2 MB'ye indirir ve başlama tamponunu
     * 800 ms'ye sabitler.
     */
    val previewProfile: IptvPlaybackProfile = IptvPlaybackProfile.SECONDARY

    /**
     * Odak başka bir satıra gittiğinde **çalışan** önizleme hemen kesilmeli mi?
     *
     * Bekçi (watchdog) ve indirme yalnız oynatma sürerken çalıştığı için, akış kesilmeden yeni
     * satıra geçilirse gezinme boyunca tam nitelikli bir akış arka planda çalışmaya devam
     * eder. Odak aynı satıra geri döndüyse (araç düğmesinden dönüş) kesilmez; oynatma zaten o
     * kanal içindir. Hiçbir şey çalmıyorken de kesme yapılmaz.
     */
    fun stopsRunningPlayback(previousSourceKey: String?, nextSourceKey: String): Boolean =
        previousSourceKey != null && previousSourceKey != nextSourceKey

    /**
     * Yeni bir önizleme planlanmalı mı?
     *
     * - Kilitli kanalda oynatma yok: ebeveyn kilidi içeriği açığa çıkarmaz.
     * - Taşıma modunda oynatma yok: kullanıcı sırayı düzenlerken arka planda kanal değişmez.
     * - Aynı kanal zaten önizleniyorsa yeniden tune edilmez; aksi halde her odak hareketi
     *   aynı kanalı baştan açardı.
     */
    fun schedulesPlayback(
        moveMode: Boolean,
        locked: Boolean,
        previousSourceKey: String?,
        nextSourceKey: String,
    ): Boolean = !locked && !moveMode && previousSourceKey != nextSourceKey
}
