package com.tvapp.livetv.browser

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView

/**
 * TV kumandasi D-pad yon tuslarini sanal fare imlecine ve WebView MotionEvent hareketlerine donusturur.
 */
class CursorController(
    private val webView: WebView,
    private val speedMultiplier: () -> Float = { 1f },
    private val invalidate: () -> Unit
) {
    enum class Mode { CURSOR, FOCUS }

    var mode = Mode.CURSOR
        private set

    var x = 0f
        private set
    var y = 0f
        private set

    /** Fare imlecinin cikamayacagi ust bar siniri */
    var minY: Float = 0f

    /** Dashboard ScrollView referansi (fare kaydirma ve tiklama icin) */
    var dashboard: View? = null

    /** Fare en ust sinira ulastiginda cagrilan callback */
    var onReachedTop: (() -> Unit)? = null

    /** Fare her hareket ettiginde cagrilan callback (auto-hide sifirlama icin) */
    var onCursorMove: ((Float, Float) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var dirX = 0
    private var dirY = 0
    private var holdStart = 0L
    private val edgeZonePx get() = webView.resources.displayMetrics.density * 48f
    private val sideZonePx get() = webView.resources.displayMetrics.density * 120f

    fun isEdgeZone(checkX: Float = x): Boolean =
        checkX < sideZonePx || checkX > (webView.width - sideZonePx).coerceAtLeast(0f)

    private val mover = object : Runnable {
        override fun run() {
            if (dirX == 0 && dirY == 0) return
            val held = SystemClock.uptimeMillis() - holdStart
            val speed = CursorGeometry.speedForHoldMs(held, speedMultiplier())
            val effectiveMinY = if (isEdgeZone(x)) 0f else minY
            val p = CursorGeometry.step(
                CursorGeometry.Point(x, y), dirX, dirY, speed, webView.width, webView.height, effectiveMinY
            )
            val reachedTop = dirY < 0 && p.y <= effectiveMinY + 4f && y <= effectiveMinY + 4f
            x = p.x
            y = p.y
            onCursorMove?.invoke(x, y)
            val ticks = CursorGeometry.wheelStep(
                dirY, y, webView.height, edgeZonePx, WHEEL_TICKS_PER_FRAME, effectiveMinY
            )
            if (ticks != 0f) {
                wheel(ticks)
            }
            invalidate()
            if (reachedTop) {
                stopMove()
                onReachedTop?.invoke()
                return
            }
            handler.postDelayed(this, FRAME_MS)
        }
    }

    fun center(width: Int, height: Int) {
        x = width / 2f
        y = ((height - minY) / 2f + minY).coerceAtLeast(minY + 20f)
        onCursorMove?.invoke(x, y)
        invalidate()
    }

    fun moveTo(newX: Float, newY: Float) {
        x = newX
        val effectiveMinY = if (isEdgeZone(newX)) 0f else minY
        y = newY.coerceAtLeast(effectiveMinY)
        onCursorMove?.invoke(x, y)
        invalidate()
    }

    fun startMove(dx: Int, dy: Int) {
        val effectiveMinY = if (isEdgeZone(x)) 0f else minY
        if (dy < 0 && y <= effectiveMinY + 4f) {
            onReachedTop?.invoke()
            return
        }
        if (dirX == dx && dirY == dy) return
        dirX = dx
        dirY = dy
        holdStart = SystemClock.uptimeMillis()
        handler.removeCallbacks(mover)
        handler.post(mover)
    }

    fun stopMove() {
        dirX = 0
        dirY = 0
        handler.removeCallbacks(mover)
    }

    /**
     * Sanal fare tiklamasi gonderir (hover move -> action down -> action up).
     */
    fun tap() {
        val t = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_HOVER_MOVE, t, t)
        dispatch(MotionEvent.ACTION_DOWN, t, t)
        dispatch(MotionEvent.ACTION_UP, t, t + 1)
    }

    fun toggleMode() {
        mode = if (mode == Mode.CURSOR) Mode.FOCUS else Mode.CURSOR
        stopMove()
        invalidate()
    }

    /**
     * Sayfayi fare tekerlegi gibi kaydirir.
     */
    private fun wheel(ticks: Float) {
        val dash = dashboard
        if (dash != null && dash.visibility == View.VISIBLE) {
            val scrollDelta = -(ticks * 160f).toInt()
            dash.scrollBy(0, scrollDelta)
            return
        }
        val t = SystemClock.uptimeMillis()
        dispatch(MotionEvent.ACTION_HOVER_MOVE, t, t)
        val props = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = this@CursorController.x
            this.y = this@CursorController.y
            setAxisValue(MotionEvent.AXIS_VSCROLL, ticks)
        })
        val e = MotionEvent.obtain(
            t, t, MotionEvent.ACTION_SCROLL, 1, props, coords,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0
        )
        webView.dispatchGenericMotionEvent(e)
        e.recycle()
    }

    private fun dispatch(action: Int, down: Long, event: Long) {
        val e = MotionEvent.obtain(down, event, action, x, y, 0)
        val dash = dashboard
        if (dash != null && dash.visibility == View.VISIBLE) {
            dash.dispatchTouchEvent(e)
        } else {
            if (action == MotionEvent.ACTION_HOVER_MOVE) {
                webView.dispatchGenericMotionEvent(e)
            } else {
                webView.dispatchTouchEvent(e)
            }
        }
        e.recycle()
    }

    companion object {
        const val FRAME_MS = 16L
        const val SCROLL_STEP_PX = 24
        const val WHEEL_TICK_PX = 128f
        const val WHEEL_TICKS_PER_FRAME = SCROLL_STEP_PX / WHEEL_TICK_PX
    }
}
