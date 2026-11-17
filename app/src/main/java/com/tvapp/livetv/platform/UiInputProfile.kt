package com.tvapp.livetv.platform

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import com.tvapp.livetv.BuildConfig

/**
 * UI-015: kullanıcı ekrana nasıl işaret ediyor?
 *
 * TV UX'i ile dokunma UX'i aynı dosyaları paylaşıyor; kararın tek bir yerde ve cihaz
 * gerçeklerine bağlı olması gerekir. Aksi hâlde `BuildConfig.MOBILE_UI_ENABLED` satır içi
 * koşulları "bu flavor dokunmatik kabuk mu?" sorusunu "kullanıcı dokunuyor mu?" sorusunun
 * yerine kullanır — mobil APK dokunmatik olmayan bir cihaza kurulduğunda (side-load edilen
 * TV kutusu) arayüz dokunma için tasarlanmış ama dokunan hiçbir şey kalmaz.
 *
 * Odak çerçevesi [hasDpadInput] ile çizilir; anlık dokunma modu (`View.isInTouchMode`) ile
 * değil. Android odak çerçevesini dokunma modunda zaten kendisi bastırır, bu yüzden profil
 * ekran boyunca sabit kalır ve çerçeve titremesi üretmez.
 */
enum class UiInputProfile(
    /** Renk tuşu rozetleri/aksiyonlar dokunulabilir mi; dokunma hedefleri 48dp+ mı. */
    val usesTouchTargets: Boolean,
    /** D-pad ile gezinme var mı: odak çerçevesi ve yön tuşu ipuçları buna bağlıdır. */
    val hasDpadInput: Boolean,
    /** Kırmızı/yeşil/sarı/mavi tuş ipucu anlamlı mı (yoksa eylem dokunulabilir olmalı). */
    val hasColorKeys: Boolean,
) {
    /** TV kumandası: tek işaret biçimi D-pad. */
    D_PAD(usesTouchTargets = false, hasDpadInput = true, hasColorKeys = true),

    /** Telefon/tablet: dokunma, D-pad yeteneği yok. */
    TOUCH(usesTouchTargets = true, hasDpadInput = false, hasColorKeys = false),

    /** Tablet + D-pad'li kumanda/klavye: ikisi de. */
    HYBRID(usesTouchTargets = true, hasDpadInput = true, hasColorKeys = false),
}

/** [UiInputProfile] kararı — cihazdan bağımsız, birim testli saf karar. */
object UiInputRules {

    /**
     * TV yapılandırmalarında (`mobileUiEnabled = false`) sonuç **her zaman** [D_PAD]'dir:
     * donanım dokunmatik olsa bile TV arayüzü taşınmaz. Bu, dokunma işlerinin TV UX'ini
     * bozamamasının mekanik garantisidir.
     *
     * Mobil flavor'da dokunmatik olmayan bir cihaz yine [D_PAD] alır; dokunmatik + D-pad
     * yeteneği [HYBRID], sadece dokunmatik [TOUCH]'tur.
     */
    fun resolve(
        mobileUiEnabled: Boolean,
        hasTouchScreen: Boolean,
        hasDpadInput: Boolean,
    ): UiInputProfile = when {
        !mobileUiEnabled -> UiInputProfile.D_PAD
        !hasTouchScreen -> UiInputProfile.D_PAD
        hasDpadInput -> UiInputProfile.HYBRID
        else -> UiInputProfile.TOUCH
    }
}

/** Cihaz sinyallerini okuyup profil çözen tek nokta. */
object UiInputProfileSource {

    /**
     * TV yapılandırmalarında cihaz sinyalleri **hiç okunmaz**: derleme zamanı sabiti
     * `MOBILE_UI_ENABLED` false olduğu için hemen [UiInputProfile.D_PAD] döner.
     */
    fun from(context: Context): UiInputProfile {
        if (!BuildConfig.MOBILE_UI_ENABLED) return UiInputProfile.D_PAD
        val configuration = context.resources.configuration
        return UiInputRules.resolve(
            mobileUiEnabled = BuildConfig.MOBILE_UI_ENABLED,
            hasTouchScreen = context.packageManager
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
            hasDpadInput = configuration.navigation == Configuration.NAVIGATION_DPAD,
        )
    }
}
