package com.tvapp.livetv.ui

import android.content.Context
import android.media.tv.TvView
import android.util.AttributeSet
import android.view.SurfaceView
import kotlin.math.min

class ResizableTvView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : TvView(context, attrs) {
    var fitSurfaceToBounds = false
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }
    private var videoWidth = 0
    private var videoHeight = 0

    fun setVideoSize(width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        if (fitSurfaceToBounds) requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (!fitSurfaceToBounds) return
        val scale = if (videoWidth > 0 && videoHeight > 0)
            min(measuredWidth.toFloat() / videoWidth, measuredHeight.toFloat() / videoHeight) else null
        val surfaceWidth = scale?.let { (videoWidth * it).toInt() } ?: measuredWidth
        val surfaceHeight = scale?.let { (videoHeight * it).toInt() } ?: measuredHeight
        for (index in 0 until childCount) {
            (getChildAt(index) as? SurfaceView)?.let { surface ->
                surface.holder.setSizeFromLayout()
                surface.measure(MeasureSpec.makeMeasureSpec(surfaceWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(surfaceHeight, MeasureSpec.EXACTLY))
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        if (!fitSurfaceToBounds) {
            super.onLayout(changed, left, top, right, bottom)
            return
        }
        // TIF may retain a requested surface rectangle from the previous grid size.
        val scale = if (videoWidth > 0 && videoHeight > 0) min(width.toFloat() / videoWidth, height.toFloat() / videoHeight) else null
        val surfaceWidth = scale?.let { (videoWidth * it).toInt() } ?: width
        val surfaceHeight = scale?.let { (videoHeight * it).toInt() } ?: height
        val x = (width - surfaceWidth) / 2
        val y = (height - surfaceHeight) / 2
        for (index in 0 until childCount) {
            (getChildAt(index) as? SurfaceView)?.layout(x, y, x + surfaceWidth, y + surfaceHeight)
        }
    }
}
