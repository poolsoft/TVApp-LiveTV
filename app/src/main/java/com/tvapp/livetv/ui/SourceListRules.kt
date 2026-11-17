package com.tvapp.livetv.ui

/** Kaynak yönetim listelerinin (IPTV listeleri, XMLTV kaynakları) boşluk durumu. */
enum class SourceListEmptyState {
    /** Liste dolu ya da ilk okuma henüz bitmedi: yönlendirme kartı gizli. */
    Hidden,

    /** İlk okuma bitti ve listede tek satır yok: kart görünür. */
    Empty,
}

/** Liste alanının altındaki kalıcı ipucu satırının içerik seçimi. */
enum class SourceListHint {
    /** Liste satır eylemleri ("OK: Kanalları seç", "OK: Kaynak işlemleri"). */
    RowActions,

    /** Eklenecek tek satır yokken "nasıl eklenir" yönlendirmesi. */
    AddSource,
}

/**
 * UI-003: kaynak yönetim ekranları boş olduğunda panel bomboş kalıyor, alttaki ipucu
 * satırı ise yok bir listede "OK: Kanalları seç" gibi geçersiz bir komutu anlatmaya
 * devam ediyordu.
 *
 * İki karar burada saf fonksiyon olarak tutulur:
 *  1. Kart yalnızca ilk okuma bittikten SONRA ve satır sayısı sıfır ise görünür.
 *     Böylece yükleme sırasında ekranda yanlış bir "kaynak yok" iddiası durmaz;
 *     gerçek durum uydurulmaz.
 *  2. Kart görünürken alt ipucu satırı, satır eylemlerinin yerine "nasıl eklenir"
 *     yönlendirmesine geçer. Ekleme eylemi bu ekranlarda zaten panel düğmeleriyle
 *     yapıldığı için karta ikinci bir eylem düğmesi eklenmez (odak durak sayısı
 *     artmaz, AGENTS.md "yerleşik etkileşim varsa düğme ekleme" kuralı).
 */
object SourceListRules {

    fun emptyState(loadFinished: Boolean, itemCount: Int): SourceListEmptyState =
        if (loadFinished && itemCount <= 0) SourceListEmptyState.Empty else SourceListEmptyState.Hidden

    fun hintFor(state: SourceListEmptyState): SourceListHint =
        if (state == SourceListEmptyState.Empty) SourceListHint.AddSource else SourceListHint.RowActions
}
