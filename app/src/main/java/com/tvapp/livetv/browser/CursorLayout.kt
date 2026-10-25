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
    var playChip: View? = null
    var onChipClick: () -> Unit = {}
    var onModeChanged: ((CursorController.Mode) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E50914") }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.parseColor("#FFFFFF")
    }

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var okDown = false
    private var longPressed = false
    private val longPressRunnable = Runnable {
        longPressed = true
        cursor.toggleMode()
        onModeChanged?.invoke(cursor.mode)
    }

    init {
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun isTopBarFocused(): Boolean = topBar?.hasFocus() == true

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
        // Oncelikle URL cubuguna, yoksa geri butonuna odaklan
        val target = bar.findViewById<View>(R.id.edit_browser_url)
            ?: bar.findViewById<View>(R.id.btn_browser_nav_back)
            ?: bar
        target.requestFocus()
        invalidate()
    }

    fun leaveTopBarToCursor() {
        topBar?.clearFocus()
        this.requestFocus()
        val topLimit = getTopBarBottomLimit()
        cursor.moveTo(cursor.x, topLimit + 30f)
        cursor.stopMove()
        invalidate()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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

        // Ust barda odak varsa
        if (isTopBarFocused()) {
            // Alta basildiginda sanal fareye donus
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
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
                    val topLimit = getTopBarBottomLimit()
                    if (cursor.y <= topLimit + 15f) {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            enterTopBar()
                        }
                        return true
                    }
                    return handleDpad(event)
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
                    if (chipContains(cursor.x, cursor.y)) {
                        onChipClick()
                    } else if (cursor.mode == CursorController.Mode.CURSOR) {
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

    private fun chipContains(x: Float, y: Float): Boolean {
        val chip = playChip ?: return false
        if (chip.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        chip.getLocationOnScreen(loc)
        val layoutLoc = IntArray(2)
        getLocationOnScreen(layoutLoc)
        val relX = loc[0] - layoutLoc[0]
        val relY = loc[1] - layoutLoc[1]
        return x >= relX && x <= relX + chip.width && y >= relY && y <= relY + chip.height
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        // Ust barda odak yoksa ve sanal fare modu etkinse imleci ciz
        if (cursor.mode == CursorController.Mode.CURSOR && !isTopBarFocused()) {
            val r = 9f * density
            canvas.drawCircle(cursor.x, cursor.y, r, dotPaint)
            canvas.drawCircle(cursor.x, cursor.y, r, outlinePaint)
        }
    }

    companion object {
        const val LONG_PRESS_MS = 500L
    }
}
