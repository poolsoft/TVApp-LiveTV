package com.tvapp.livetv.ui

/**
 * OSD metin punto kurali (UI-005).
 *
 * TV kumanda mesafesinden (10-foot) okunabilirliyin alt siniri tek yerde
 * tutulur. Onceki degerler okunmuyordu: renk aksiyon/durum ipuclari ve ayrac
 * `MainActivity` icinde 9f/11f, `TvUiComponents.colorAction` 11f, kanal satiri
 * kalite rozeti 8sp, kaynak rozetleri 9sp, saat altindaki tarih autoSize alti
 * 7sp idi. Programatik OSD metinler artik bu kuraldan gecer; layout degerleri
 * de ayni alt sinira gore verildi (rozetler satir yuksekligini degistirmez,
 * cunku satiri 15-16sp kanal adi belirler).
 */
object OsdTextScale {

    /** OSD'de kullanilabilir en kucuk punto. */
    const val MIN_TEXT_SP = 12f

    /** Renk aksiyon, durum ipucu ve ayraclarin punto degeri. */
    val HINT_TEXT_SP: Float = MIN_TEXT_SP

    /** Kanal listesi/kalite/kaynak rozetlerinin punto degeri (layout ile ayni). */
    val BADGE_TEXT_SP: Float = MIN_TEXT_SP

    /** Istenen punto alt sinirin altindaysa sinira cekilir, uzerine dokunulmaz. */
    fun textSp(preferredSp: Float): Float =
        if (preferredSp < MIN_TEXT_SP) MIN_TEXT_SP else preferredSp
}
