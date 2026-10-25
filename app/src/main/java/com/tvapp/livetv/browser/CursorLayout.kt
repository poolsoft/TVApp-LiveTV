package com.tvapp.livetv.browser

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout

/**
 * Sanal fare imlecini cizen, D-pad hareketlerini yoneten ve ust bar bilesenlerine tiklamayi saglayan FrameLayout.
 */
class CursorLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    lateinit var webView: WebView
    lateinit var cursor: CursorController

    var onBack: () -> Boolean = { false }
    var onExitPage: () -> Unit = {}
    var playChip: View? = null
    var onChipClick: () -> Unit = {}

    private val density = resources.displayMetrics.density
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E50914") }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.parseColor("#FFFFFF")
    }

    private var okDown = false

    init {
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                val focused = findFocus()
                if (focused is EditText) {
                    focused.clearFocus()
                    this.requestFocus()
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

        // Eger EditText acik ve kullanici yazi yaziyorsa normal tus akisina izin ver
        val focused = findFocus()
        if (focused is EditText) {
            if (event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && event.action == KeyEvent.ACTION_DOWN) {
                focused.clearFocus()
                this.requestFocus()
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> return handleOk(event)
            KeyEvent.KEYCODE_DPAD_UP,
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
                okDown = true
            }
            KeyEvent.ACTION_UP -> {
                if (okDown) {
                    okDown = false
                    val hit = findClickableChildAt(this, cursor.x, cursor.y)
                    if (hit != null && hit !== webView) {
                        if (hit is EditText) {
                            hit.requestFocus()
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showSoftInput(hit, InputMethodManager.SHOW_IMPLICIT)
                        } else {
                            hit.performClick()
                        }
                    } else if (chipContains(cursor.x, cursor.y)) {
                        onChipClick()
                    } else {
                        cursor.tap()
                    }
                }
            }
        }
        return true
    }

    private fun findClickableChildAt(view: View, x: Float, y: Float): View? {
        if (view.visibility != View.VISIBLE) return null
        if (view is ViewGroup && view !== webView) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                val hit = findClickableChildAt(child, x, y)
                if (hit != null) return hit
            }
        }
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val l = loc[0]
        val t = loc[1]
        val r = l + view.width
        val b = t + view.height
        val rootLoc = IntArray(2)
        getLocationOnScreen(rootLoc)
        val screenX = x + rootLoc[0]
        val screenY = y + rootLoc[1]
        val inside = screenX >= l && screenX <= r && screenY >= t && screenY <= b
        return if (inside && (view.isClickable || view is EditText)) {
            view
        } else null
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
        if (cursor.mode == CursorController.Mode.CURSOR) {
            val r = 9f * density
            canvas.drawCircle(cursor.x, cursor.y, r, dotPaint)
            canvas.drawCircle(cursor.x, cursor.y, r, outlinePaint)
        }
    }
}
