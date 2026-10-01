package com.tvapp.livetv.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.PopupWindow
import com.tvapp.livetv.R
import kotlin.math.min

/** The popup owns focus so filtering cannot trigger selection or auto-tuning. */
class AlphabetRail(
    private val anchor: View,
    private val targets: List<AlphabetTarget>,
    initialLetter: String,
    private val onJump: (Int) -> Unit,
    onClose: () -> Unit,
) {
    private val density = anchor.resources.displayMetrics.density
    private val popup = PopupWindow()
    private val rail = object : View(anchor.context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private var selected = targets.indexOfFirst { it.letter == initialLetter }.coerceAtLeast(0)
        private var first = 0
        private val capacity get() = (height / (22 * density)).toInt().coerceAtLeast(1)
        private val visibleCount get() = min(capacity, targets.size)
        private val cell get() = height.toFloat() / visibleCount.coerceAtLeast(1)

        init {
            isFocusable = true
            isFocusableInTouchMode = true
            contentDescription = context.getString(R.string.alphabet_navigation)
        }

        private fun select(index: Int) {
            val target = index.coerceIn(0, targets.lastIndex)
            if (selected == target) return
            selected = target
            if (selected < first) first = selected
            if (selected >= first + visibleCount) first = selected - visibleCount + 1
            invalidate()
            onJump(targets[selected].position)
        }

        override fun onDraw(canvas: Canvas) {
            if (targets.isEmpty()) return
            if (selected >= first + visibleCount) first = selected - visibleCount + 1
            val x = width - 22 * density
            val y = (selected - first + 0.5f) * cell
            paint.color = context.getColor(R.color.panel)
            canvas.drawRoundRect(width - 38 * density, 0f, width.toFloat(), height.toFloat(), 8 * density, 8 * density, paint)
            paint.color = context.getColor(R.color.accent)
            path.reset()
            path.moveTo(width.toFloat(), y - cell)
            path.lineTo(width - 34 * density, y - cell)
            path.cubicTo(width - 34 * density, y - cell / 2, 4 * density, y - cell / 2, 4 * density, y)
            path.cubicTo(4 * density, y + cell / 2, width - 34 * density, y + cell / 2, width - 34 * density, y + cell)
            path.lineTo(width.toFloat(), y + cell)
            path.close()
            canvas.drawPath(path, paint)
            paint.textAlign = Paint.Align.CENTER
            for (index in first until min(first + visibleCount, targets.size)) {
                paint.textSize = min(15 * resources.displayMetrics.scaledDensity, cell * 0.65f)
                val textWidth = paint.measureText(targets[index].letter)
                if (textWidth > 38 * density) paint.textSize *= 38 * density / textWidth
                paint.color = context.getColor(if (index == selected) R.color.black else R.color.text_primary)
                paint.isFakeBoldText = index == selected
                val baseline = (index - first + 0.5f) * cell - (paint.ascent() + paint.descent()) / 2
                canvas.drawText(targets[index].letter, if (index == selected) width / 2f else x, baseline, paint)
            }
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> { select((selected - 1 + targets.size) % targets.size); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { select((selected + 1) % targets.size); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> { close(); true }
            else -> true
        }

        override fun onKeyUp(keyCode: Int, event: KeyEvent) = true

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                select(first + (event.y / cell).toInt())
            }
            if (event.action == MotionEvent.ACTION_UP) performClick()
            return true
        }

        override fun performClick(): Boolean { super.performClick(); return true }
    }

    init {
        popup.contentView = rail
        popup.width = (72 * density).toInt()
        popup.height = anchor.height
        popup.isFocusable = true
        popup.isOutsideTouchable = true
        popup.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        popup.setOnDismissListener { onClose() }
    }

    fun show() {
        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        popup.showAtLocation(anchor, Gravity.TOP or Gravity.LEFT, location[0] + anchor.width - popup.width, location[1])
        rail.requestFocus()
    }

    fun close() = popup.dismiss()
}
