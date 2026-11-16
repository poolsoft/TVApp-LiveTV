package com.tvapp.livetv.browser

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Sayfa çerezlerini okumak için tek giriş noktası.
 *
 * [android.webkit.CookieManager] servisi Android tarafından ana thread'ten kullanılmak
 * istenir; sniffer ve çözümleyiciler ise WebKit'in ve `Dispatchers.IO`'nun arka plan
 * thread'lerinde çalışır (CRASH-001). Okuma bu yüzden ana thread'e taşınır: çağrı zaten
 * ana thread'teyse yerinde yapılır, değilse ana thread'e post edilir. Ana thread meşgulse
 * veya servis hazır değilse çerez "yok" sayılır; hiçbir çağrı yolunda çözücü kilitlenmez.
 */
internal object WebCookies {

    /** Ana thread'e geçiş bekleme üst sınırı (ms). */
    internal const val READ_TIMEOUT_MS = 2_000L

    private const val TAG = "WebCookies"

    /**
     * Ana thread zamanlayıcısı. İlk kullanımda ve hata yutularak kurulur: kurulamazsa
     * (veya ana thread döngüsü yoksa) okuma çerezsiz devam eder, çağırana istisna sızmaz.
     */
    private val mainHandler: Handler? by lazy {
        runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
    }

    /** [url] için çerez dizgisini; okunamıyorsa null döndürür. */
    fun read(url: String): String? = runCatching {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            readOnMain(url)
        } else {
            readViaMainThread(url)
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun readViaMainThread(url: String): String? {
        val handler = mainHandler ?: return null
        val latch = CountDownLatch(1)
        var cookie: String? = null
        val posted = handler.post {
            cookie = readOnMain(url)
            latch.countDown()
        }
        if (!posted) return null
        if (!latch.await(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            // Çerez alınamadı; akış başlıksız denenir. URL yazılmaz (kimlik bilgisi içerebilir).
            Log.w(TAG, "COOKIE_READ_TIMEOUT")
            return null
        }
        return cookie
    }

    private fun readOnMain(url: String): String? =
        runCatching { android.webkit.CookieManager.getInstance().getCookie(url) }.getOrNull()
}
