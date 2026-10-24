package com.tvapp.livetv.browser

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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

    private val handler = Handler(Looper.getMainLooper())
    private var dirX = 0
    private var dirY = 0
    private var holdStart = 0L
    private val edgeZonePx get() = webView.resources.displayMetrics.density * 48f

    private val mover = object : Runnable {
        override fun run() {
            if (dirX == 0 && dirY == 0) return
            val held = SystemClock.uptimeMillis() - holdStart
            val speed = CursorGeometry.speedForHoldMs(held, speedMultiplier())
            val p = CursorGeometry.step(
                CursorGeometry.Point(x, y), dirX, dirY, speed, webView.width, webView.height
            )
            x = p.x
            y = p.y
            val ticks = CursorGeometry.wheelStep(
                dirY, y, webView.height, edgeZonePx, WHEEL_TICKS_PER_FRAME
            )
            if (ticks != 0f) {
                wheel(ticks)
            }
            invalidate()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    fun center(width: Int, height: Int) {
        x = width / 2f
        y = height / 2f
        invalidate()
    }

    fun startMove(dx: Int, dy: Int) {
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
        if (action == MotionEvent.ACTION_HOVER_MOVE) {
            webView.dispatchGenericMotionEvent(e)
        } else {
            webView.dispatchTouchEvent(e)
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
