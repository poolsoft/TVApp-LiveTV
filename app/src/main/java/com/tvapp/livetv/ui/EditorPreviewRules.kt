package com.tvapp.livetv.ui

import com.tvapp.livetv.playback.IptvPlaybackProfile

/** Önizlemenin o an hangi yüzeyde çalıştığı. Hiçbir şey çalmıyorsa kurala `null` verilir. */
enum class PreviewSurface { TIF, IPTV }

/** Odak başka satıra giderken **çalışan** önizleme için yapılacak iş. */
enum class PreviewMoveAction {
    /** Hiçbir şey: kesilecek bir akış yok ya da yüzey yeni ayarlamaya bırakılır. */
    NOTHING,

    /** Yalnız IPTV akışı kesilir (indirme, çözme ve bekçi bırakılır); tuner yüzeyine dokunulmaz. */
    STOP_IPTV,

    /** İki yüzey de serbest bırakılır ve önizleme kutusu karartılır (kilit ya da kapalı önizleme). */
    RELEASE_ALL,
}

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
 *
 * **PERF-028 eki (gezinme gecikmesi):** ilk düzeltmede odak her değiştiğinde iki yüzey de
 * serbest bırakılıyordu. Odaklanma bir tuş basımıdır; uydu/DVB ayarlaması 1-2 sn sürdüğü için
 * gezinmenin tamamı tuner sürücüsüne oturum kapat/aç emirleri ve arayüz yeniden yerleşimi
 * yığıyordu — şikâyet geçmedi, çünkü tuş başına iş daha da arttı. Burada gezinme sırasında
 * yapılacak iş **sıfıra** indirilir: çalan bir akış yoksa hiçbir şeye dokunulmaz, IPTV akışı
 * kesilir (bedeli ucuzdur: indirme ve bekçi bırakılır), TIF yüzeyine ise dokunulmaz — yeni
 * ayarlama oturumu zaten kendisi değiştirir.
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
     * Odak başka bir satıra gittiğinde **çalışan** önizleme için karar.
     *
     * Yalnız gerçekten çalan bir akış kesilebilir: [activeSurface] `null` ise (gezinmenin
     * büyük kısmında durum budur) hiçbir binder çağrısı ve yeniden yerleşim üretilmez.
     *
     * - Kullanıcı önizlemeyi kapattıysa veya kanal kilitliyse kutu karartılır: ebeveyn kilidi
     *   içeriği açığa çıkarmaz, kapalı önizleme de arka planda kanal bırakmaz.
     * - Odak aynı satıra geri döndüyse (araç düğmesinden dönüş) oynatma kesilmez; o akış zaten
     *   bu kanal içindir.
     * - IPTV akışı hemen kesilir: indirme, çözme ve ana iş parçacığında çalışan bekçi
     *   gezinme boyunca listeyle yarışır.
     * - TIF yüzeyi olduğu gibi bırakılır. [android.widget.TvView.reset] çağırıp sonra yeniden
     *   görünür yapmak uydu tunerında oturumu kapatıp açar; tek kanallık sürücüde bu bedel
     *   (loglarda ölçülen 1-2 sn) her tuş basımına ödenir. Yeni ayarlama oturumu zaten
     *   devralır, kutu da bir sonraki kanala geçene kadar eski kareyi gösterir.
     */
    fun moveAction(
        previewEnabled: Boolean,
        locked: Boolean,
        activeSurface: PreviewSurface?,
        activeSourceKey: String?,
        nextSourceKey: String,
    ): PreviewMoveAction = when {
        activeSurface == null -> PreviewMoveAction.NOTHING
        !previewEnabled || locked -> PreviewMoveAction.RELEASE_ALL
        activeSourceKey == nextSourceKey -> PreviewMoveAction.NOTHING
        activeSurface == PreviewSurface.IPTV -> PreviewMoveAction.STOP_IPTV
        else -> PreviewMoveAction.NOTHING
    }

    /**
     * Yeni önizleme IPTV yüzeyinde açılacakken tuner yüzeyi serbest bırakılmalı mı?
     *
     * İki yüzey aynı anda görünür olamaz; ancak çalan bir TIF oturumu yoksa
     * [android.widget.TvView.reset] boşuna çağrılmaz.
     */
    fun releasesTunerSurface(
        targetSurface: PreviewSurface,
        activeSurface: PreviewSurface?,
    ): Boolean = targetSurface == PreviewSurface.IPTV && activeSurface == PreviewSurface.TIF

    /**
     * Yeni bir önizleme planlanmalı mı?
     *
     * - Önizleme kapalıysa oynatma yok.
     * - Kilitli kanalda oynatma yok: ebeveyn kilidi içeriği açığa çıkarmaz.
     * - Taşıma modunda oynatma yok: kullanıcı sırayı düzenlerken arka planda kanal değişmez.
     * - Kanal **zaten çalıyorsa** yeniden tune edilmez; karşılaştırma çalan kanala yapılır,
     *   planlanan kanala değil. Planlanan kanala yapılırsa kullanıcı erteleme süresi içinde bir
     *   satır aşağı inip geri döndüğünde plan iptal edilip yeniden kurulmaz ve önizleme hiç
     *   açılmaz.
     */
    fun schedulesPlayback(
        moveMode: Boolean,
        locked: Boolean,
        previewEnabled: Boolean,
        activeSourceKey: String?,
        nextSourceKey: String,
    ): Boolean = !locked && !moveMode && previewEnabled && activeSourceKey != nextSourceKey
}
