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
    /** Liste satır eylemleri kumanda tuşuyla anlatılır ("OK: Kanalları seç"). */
    RowActions,

    /**
     * UI-016: aynı satır eylemleri dokunma jestiyle anlatılır. Dokunma profilinde
     * "OK" ve "CH+/CH-" komutları karşılıksız kalıyor, oysa satıra dokunmak aynı
     * işlemleri açıyor.
     */
    RowActionsTouch,

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
 *  3. Satır eylemleri metni girdi profiline göre seçilir: tuş komutu ya da dokunma
 *     jesti (`UI-016`).
 */
object SourceListRules {

    fun emptyState(loadFinished: Boolean, itemCount: Int): SourceListEmptyState =
        if (loadFinished && itemCount <= 0) SourceListEmptyState.Empty else SourceListEmptyState.Hidden

    /**
     * UI-016: ipucu satırı, eylemin gerçekten nasıl tetiklendiğini söylemelidir.
     * `hasDpadInput = false` (dokunmatik telefon/tablet) iken "OK" ve "CH+/CH-"
     * komutları karşılıksızdır; satıra dokunmak aynı işlemleri açar. TV
     * profillerinde `hasDpadInput` her zaman true olduğu için metin değişmez.
     */
    fun hintFor(state: SourceListEmptyState, hasDpadInput: Boolean): SourceListHint = when {
        state == SourceListEmptyState.Empty -> SourceListHint.AddSource
        hasDpadInput -> SourceListHint.RowActions
        else -> SourceListHint.RowActionsTouch
    }
}
