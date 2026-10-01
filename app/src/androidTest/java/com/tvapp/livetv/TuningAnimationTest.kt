package com.tvapp.livetv

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TuningAnimationTest {
    @Test
    fun bundledAnimationParsesWithoutExternalImages() {
        val result = LottieCompositionFactory.fromRawResSync(
            ApplicationProvider.getApplicationContext(), R.raw.channel_tuning,
        )
        val composition = result.value
        assertNotNull(result.exception?.message, composition)
        assertTrue(composition!!.duration > 0)
        assertTrue(composition.images.isEmpty())
        val bitmap = android.graphics.Bitmap.createBitmap(224, 224, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLACK)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            LottieDrawable().apply {
                setComposition(composition)
                setBounds(0, 0, 224, 224)
                progress = 0.5f
                draw(android.graphics.Canvas(bitmap))
            }
        }
        val pixels = IntArray(224 * 224)
        bitmap.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        assertTrue("Animation must be visible against black", pixels.any { (it and 0x00ffffff) != 0 })
        bitmap.recycle()
    }
}
