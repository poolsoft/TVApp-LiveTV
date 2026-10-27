package com.tvapp.livetv.main

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Faz 4: ayni tusun kisa / uzun basim ayrimi.
 *
 * MainActivity'de iki yerde (ayarlar tusu ve son kanal tusu) birebir kopyalanan
 * durum makinesinin tek hali. Davranis aynidir:
 *
 * - DOWN (tekrar yok): isleyici sifirlanir, onceki zamanlayici iptal edilir,
 *   uzun basim zamanlayicisi kurulur.
 * - Zamanlayici dolarsa uzun basim islenir ve tus birakilana kadar kisa basim
 *   bastirilir.
 * - UP: zamanlayici iptal edilir; uzun basim islenmediyse kisa basim calisir.
 *
 * Tus kodu geri cagrilara tasinir; boylece tani loglari eskisiyle ayni kalir.
 */
class LongPressKeyGate(
    private val scope: CoroutineScope,
    private val longPressTimeoutMillis: () -> Long,
) {

    private var job: Job? = null
    private var handled = false
    private var pendingKeyCode: Int = 0
    private var shortPress: ((Int) -> Unit)? = null

    /** Tus basildi (repeat olmayan DOWN). */
    fun onDown(keyCode: Int, onLongPress: (Int) -> Unit, onShortPress: (Int) -> Unit) {
        handled = false
        pendingKeyCode = keyCode
        shortPress = onShortPress
        job?.cancel()
        job = scope.launch {
            delay(longPressTimeoutMillis())
            handled = true
            onLongPress(keyCode)
        }
    }

    /** Tus birakildi. */
    fun onUp() {
        job?.cancel()
        job = null
        if (!handled) shortPress?.invoke(pendingKeyCode)
        handled = false
        shortPress = null
    }

    /** Bekleyen zamanlayiciyi iptal eder (Activity kapanisi). */
    fun cancel() {
        job?.cancel()
        job = null
        handled = false
        shortPress = null
    }
}
