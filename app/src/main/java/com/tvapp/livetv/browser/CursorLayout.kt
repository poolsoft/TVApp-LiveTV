package com.tvapp.livetv.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import com.tvapp.livetv.R

/**
 * Sanal fare imlecini cizen, D-pad hareketlerini yoneten ve ust bar bilesenleri arasinda gecisi saglayan FrameLayout.
 */
class CursorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    lateinit var webView: WebView
    lateinit var cursor: CursorController

    var topBar: View? = null
    var onBack: () -> Boolean = { false }
    var onExitPage: () -> Unit = {}
    var onModeChanged: ((CursorController.Mode) -> Unit)? = null
    var dashboard: View? = null

    private val density = resources.displayMetrics.density
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#00E5FF") }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        color = Color.parseColor("#E6000000")
    }
    private val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFFFF") }

    private var cursorAlpha = 1.0f
    private val idleHideHandler = Handler(Looper.getMainLooper())
    private val idleHideRunnable = Runnable {
        cursorAlpha = 0f
        invalidate()
    }

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var okDown = false
    private var longPressed = false
    private val longPressRunnable = Runnable {
        longPressed = true
        cursor.toggleMode()
        onModeChanged?.invoke(cursor.mode)
    }

    var onTopBarRequested: (() -> Unit)? = null

    init {
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun isTopBarFocused(): Boolean = topBar?.hasFocus() == true

    fun wakeCursor() {
        if (cursorAlpha != 1.0f) {
            cursorAlpha = 1.0f
            invalidate()
        }
        idleHideHandler.removeCallbacks(idleHideRunnable)
        idleHideHandler.postDelayed(idleHideRunnable, 2500L)
    }

    fun getTopBarBottomLimit(): Float {
        val bar = topBar ?: return 0f
        val loc = IntArray(2)
        bar.getLocationOnScreen(loc)
        val rootLoc = IntArray(2)
        getLocationOnScreen(rootLoc)
        val relY = (loc[1] - rootLoc[1]) + bar.height
        return relY.toFloat().coerceAtLeast(0f)
    }

    fun enterTopBar() {
        cursor.stopMove()
        val bar = topBar ?: return
        onTopBarRequested?.invoke()
        val target = bar.findViewById<View>(R.id.edit_browser_url)
            ?: bar.findViewById<View>(R.id.btn_browser_nav_back)
            ?: bar
        target.requestFocus()
        invalidate()
    }

    fun leaveTopBarToCursor() {
        topBar?.findViewById<View>(R.id.browser_url_action_bar)?.visibility = View.GONE
        topBar?.clearFocus()
        val dash = dashboard
        if (dash != null && dash.visibility == View.VISIBLE) {
            dash.requestFocus()
        } else if (cursor.mode == CursorController.Mode.FOCUS) {
            webView.requestFocus()
        } else {
            this.requestFocus()
            cursor.moveTo(cursor.x, 30f)
            cursor.stopMove()
            wakeCursor()
        }
        invalidate()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        wakeCursor()

        // Geri tusu mantigi
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (isTopBarFocused()) {
                    leaveTopBarToCursor()
                    return true
                }
                if (onBack()) return true
                if (webView.canGoBack()) {
                    webView.goBack()
                    return true
                }
                onExitPage()
                return true
            }
            return true
        }

        // Kumanda Menu veya Info tusu ile dogrudan ust bara gecis
        if (event.keyCode == KeyEvent.KEYCODE_MENU ||
            event.keyCode == KeyEvent.KEYCODE_INFO ||
            event.keyCode == KeyEvent.KEYCODE_SETTINGS
        ) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (isTopBarFocused()) {
                    leaveTopBarToCursor()
                } else {
                    enterTopBar()
                }
            }
            return true
        }

        // Ust barda odak varsa
        if (isTopBarFocused()) {
            // Alta basildiginda
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                val urlEdit = topBar?.findViewById<View>(R.id.edit_browser_url)
                val actionBar = topBar?.findViewById<View>(R.id.browser_url_action_bar)
                val actionClear = topBar?.findViewById<View>(R.id.btn_action_clear)
                // Eger adres cubugu odaktaysa, alt eylem barini ac ve Temizle butonuna gec
                if (urlEdit?.hasFocus() == true && actionClear != null) {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        actionBar?.visibility = View.VISIBLE
                        actionClear.requestFocus()
                    }
                    return true
                }
                // Eger alt eylem barindaysa veya diger butonlardaysa sayfaya/imlece don
                if (event.action == KeyEvent.ACTION_DOWN) {
                    leaveTopBarToCursor()
                }
                return true
            }
            // Diger tuslar (Sol, Sag, OK, yazi yazma) ust bar bilesenleri tarafindan kullanilir
            return super.dispatchKeyEvent(event)
        }

        // Sayfa uzerindeyken tus yonetimi
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> return handleOk(event)

            KeyEvent.KEYCODE_DPAD_UP -> {
                if (cursor.mode == CursorController.Mode.CURSOR) {
                    if (cursor.y <= 15f) {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            enterTopBar()
                        }
                        return true
                    }
                    return handleDpad(event)
                } else {
                    // FOCUS modunda sayfanin en ustundeyken UP tusuna basildiginda ust bara odaklan
                    if (webView.scrollY <= 10) {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            enterTopBar()
                        }
                        return true
                    }
                }
            }

            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (cursor.mode == CursorController.Mode.CURSOR) {
                    return handleDpad(event)
                }
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun handleDpad(event: KeyEvent): Boolean {
        val (dx, dy) = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> 0 to -1
            KeyEvent.KEYCODE_DPAD_DOWN -> 0 to 1
            KeyEvent.KEYCODE_DPAD_LEFT -> -1 to 0
            else -> 1 to 0
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> cursor.startMove(dx, dy)
            KeyEvent.ACTION_UP -> cursor.stopMove()
        }
        return true
    }

    private fun handleOk(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (!okDown) {
                    okDown = true
                    longPressed = false
                    longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                }
            }
            KeyEvent.ACTION_UP -> {
                okDown = false
                longPressHandler.removeCallbacks(longPressRunnable)
                if (!longPressed) {
                    if (cursor.mode == CursorController.Mode.CURSOR) {
                        cursor.tap()
                    } else {
                        // FOCUS modunda web sayfasindaki secili baglantiya tikla
                        super.dispatchKeyEvent(event)
                    }
                }
            }
        }
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // Ust barda odak yoksa ve sanal fare modu etkinse imleci ciz
        if (cursor.mode == CursorController.Mode.CURSOR && !isTopBarFocused() && cursorAlpha > 0.01f) {
            val r = 9f * density
            dotPaint.alpha = (cursorAlpha * 255).toInt()
            shadowPaint.alpha = (cursorAlpha * 230).toInt()
            innerPaint.alpha = (cursorAlpha * 255).toInt()
            canvas.drawCircle(cursor.x, cursor.y, r, dotPaint)
            canvas.drawCircle(cursor.x, cursor.y, r, shadowPaint)
            canvas.drawCircle(cursor.x, cursor.y, 3.5f * density, innerPaint)
        }
    }

    companion object {
        const val LONG_PRESS_MS = 500L
    }
}
