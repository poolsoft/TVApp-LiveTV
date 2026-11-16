package com.tvapp.livetv.vod

/**
 * VOD ana ekranının ölçüye bağlı geometrisi (`UI-006`).
 *
 * `VodHomeActivity` manifestte landscape'e sabitlidir ve telefon ile aynı düzeni
 * (`activity_vod_home.xml`) kullanır. Düzen TV için sabit ölçülerle yazıldığından
 * (Devam Et rayı 175dp, afiş 124dp, ızgara 5 sütun) telefonda gövde yüksekliği
 * ~260-360dp'ye düşer; ray sabit kaldığı için afiş ızgarası ya yarım kalır ya da
 * hiç görünmez. Aynı şekilde 5 sütun zorlandığında kartlar ~40dp'ye sıkışır.
 *
 * Karar burada, View'lardan bağımsız ve saf verilir: böylece "kırpma yok" hesabı
 * birim testle kanıtlanır. Gövde [TV_BODY_HEIGHT_DP] ve üzerinde XML'deki sabitler
 * aynen döner, yani TV/tablet görünümü bu değişimden etkilenmez.
 */
object VodLayoutRules {

    /** Bu ve üzeri gövde yüksekliğinde XML sabitleri korunur (TV/tablet düzeni). */
    const val TV_BODY_HEIGHT_DP = 380

    /** `activity_vod_home.xml` içindeki Devam Et rayının sabit yüksekliği. */
    const val TV_RAIL_HEIGHT_DP = 175

    /** `item_vod_poster_card.xml` içindeki afiş çerçevesinin sabit yüksekliği. */
    const val TV_POSTER_HEIGHT_DP = 124

    /** Ray içindeki RecyclerView'ın üst + alt dolgusu (6dp + 6dp). */
    const val RAIL_CONTENT_PADDING_DP = 12

    /** Ray başlığı ("İzlemeye Devam Et") + 4dp aralığın tuttuğu dikey alan. */
    const val RAIL_LABEL_BLOCK_DP = 25

    /** Afiş ızgarasının başlık/sayı satırının tuttuğu dikey alan. */
    const val GRID_HEADER_BLOCK_DP = 26

    /**
     * Afiş dışında kartın tuttuğu dikey alan: kök dolgu (10dp) + başlık satırı
     * (4dp aralık + ~17dp). Yıl/puan satırı kompakt modda gizlendiği için sayılmaz.
     */
    const val CARD_CHROME_DP = 31

    /** Yıl/puan satırının açıkken eklediği yükseklik (2dp aralık + ~13dp). */
    const val CARD_META_ROW_DP = 15

    /** Rayda afişin inebileceği en küçük yükseklik. */
    const val MIN_RAIL_POSTER_HEIGHT_DP = 52

    /**
     * Kısa ekranda ray için ayrılan en küçük yükseklik. Afiş tabanı + kart başlık
     * payı + ray dolgusundan türetilir: ray bu tabana inse bile kendi kartlarını
     * kırpmez, kırpılan dikey pay listeye kalır.
     */
    const val MIN_RAIL_HEIGHT_DP = MIN_RAIL_POSTER_HEIGHT_DP + CARD_CHROME_DP + RAIL_CONTENT_PADDING_DP

    /** Izgarada afişin inebileceği en küçük yükseklik (tek satır okunur kalsın). */
    const val MIN_GRID_POSTER_HEIGHT_DP = 72

    /** Izgaraya bırakılan en küçük dikey pay: bir tam kart satırı + nefes payı. */
    const val MIN_GRID_VIEWPORT_DP = MIN_GRID_POSTER_HEIGHT_DP + CARD_CHROME_DP + 46

    /** Bir afiş sütunu için gereken en küçük genişlik (kart 106dp + yatay paylar). */
    const val MIN_CARD_WIDTH_DP = 110

    /** Izgarada izin verilen en çok sütun; TV'de mevcut davranış budur. */
    const val MAX_GRID_SPAN = 5

    /**
     * Kısa ekranda (telefon yatay) uygulanan geometri. [metaRowVisible] kapandığında
     * yıl/puan satırı başlık alanına yer açar; [railLabelVisible] yalnız TV ölçeğinde
     * açıktır.
     */
    data class VodGeometry(
        val railHeightDp: Int,
        val railPosterHeightDp: Int,
        val gridPosterHeightDp: Int,
        val railLabelVisible: Boolean,
        val metaRowVisible: Boolean,
    )

    /**
     * Gövde yüksekliğine (üst bar ile alt ipucu barı arasında kalan alan) göre
     * ray yüksekliğini ve afiş yüksekliklerini hesaplar.
     *
     * Sıra kasıtlı: önce ızgaraya [MIN_GRID_VIEWPORT_DP] kadar garanti pay verilir,
     * kalan ray'a gider. Böylece kısa ekranda kaydırılabilir liste her durumda en az
     * bir tam kart satırı görür; kırpılma riski varsa ray kırpılır, liste değil.
     */
    fun geometry(bodyHeightDp: Int): VodGeometry {
        if (bodyHeightDp <= 0 || bodyHeightDp >= TV_BODY_HEIGHT_DP) return standard()
        val railBudget = bodyHeightDp - GRID_HEADER_BLOCK_DP - MIN_GRID_VIEWPORT_DP
        val railHeight = TV_RAIL_HEIGHT_DP.coerceAtMost(railBudget.coerceAtLeast(MIN_RAIL_HEIGHT_DP))
        val railPoster = (railHeight - RAIL_CONTENT_PADDING_DP - CARD_CHROME_DP)
            .coerceIn(MIN_RAIL_POSTER_HEIGHT_DP, TV_POSTER_HEIGHT_DP)
        val gridViewport = bodyHeightDp - railHeight - GRID_HEADER_BLOCK_DP
        val gridPoster = (gridViewport - CARD_CHROME_DP)
            .coerceIn(MIN_GRID_POSTER_HEIGHT_DP, TV_POSTER_HEIGHT_DP)
        return VodGeometry(
            railHeightDp = railHeight,
            railPosterHeightDp = railPoster,
            gridPosterHeightDp = gridPoster,
            railLabelVisible = false,
            metaRowVisible = false,
        )
    }

    /** XML sabitleriyle birebir aynı görünüm (TV/tablet ve ölçü yokken güvenli varsayılan). */
    fun standard(): VodGeometry = VodGeometry(
        railHeightDp = TV_RAIL_HEIGHT_DP,
        railPosterHeightDp = TV_POSTER_HEIGHT_DP,
        gridPosterHeightDp = TV_POSTER_HEIGHT_DP,
        railLabelVisible = true,
        metaRowVisible = true,
    )

    /**
     * Afiş ızgarası sütun sayısı: mevcut genişliğe sığan en çok [MIN_CARD_WIDTH_DP]
     * genişliğinde kart, en çok [MAX_GRID_SPAN]. TV genişliğinde sonuç 5'tir, yani
     * mevcut davranış değişmez; telefonda kartlar 40dp'ye ezilmez.
     */
    fun gridSpan(availableWidthPx: Int, minCardWidthPx: Int, maxSpan: Int = MAX_GRID_SPAN): Int {
        if (availableWidthPx <= 0 || minCardWidthPx <= 0) return 1
        return (availableWidthPx / minCardWidthPx).coerceIn(1, maxSpan.coerceAtLeast(1))
    }
}
