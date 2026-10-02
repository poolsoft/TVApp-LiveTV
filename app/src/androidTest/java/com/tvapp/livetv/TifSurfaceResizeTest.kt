package com.tvapp.livetv

import android.view.SurfaceView
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tvapp.livetv.ui.ResizableTvView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TifSurfaceResizeTest {
    @Test fun surfaceOnlyReceivesFinalAspectFittedBounds() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = ResizableTvView(ApplicationProvider.getApplicationContext())
            val surface = (0 until view.childCount).map(view::getChildAt).filterIsInstance<SurfaceView>().single()
            view.fitSurfaceToBounds = true
            view.setVideoSize(1920, 1080)
            fun layout(width: Int, height: Int) {
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
            }
            layout(400, 400)
            val sizes = mutableListOf<Pair<Int, Int>>()
            surface.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                sizes += (right - left) to (bottom - top)
            }
            layout(800, 800)
            assertEquals(listOf(800 to 450), sizes)
            sizes.clear()
            layout(400, 400)
            assertEquals(listOf(400 to 225), sizes)
        }
    }

    @Test fun surfaceFollowsGridAndFullscreenBoundsWithoutRetuning() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = ResizableTvView(ApplicationProvider.getApplicationContext())
            val surface = (0 until view.childCount).map(view::getChildAt).filterIsInstance<SurfaceView>().single()
            fun size(width: Int, height: Int) {
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                view.layout(0, 0, width, height)
            }
            view.fitSurfaceToBounds = true
            view.setVideoSize(1920, 1080)
            size(400, 400)
            assertEquals(400, surface.width)
            assertEquals(225, surface.height)
            assertEquals(400, surface.measuredWidth)
            assertEquals(225, surface.measuredHeight)
            assertEquals(87, surface.top)
            size(800, 450)
            assertEquals(800, surface.width)
            assertEquals(450, surface.height)
            assertEquals(800, surface.measuredWidth)
            assertEquals(450, surface.measuredHeight)
            view.fitSurfaceToBounds = false
            size(600, 400)
            assertEquals(600, surface.width)
            assertEquals(400, surface.height)
        }
    }
}
