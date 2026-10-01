package com.tvapp.livetv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DialogActionFocusTest {
    @Test
    fun everyFooterActionHasDistinctFocusAndReadableText() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_TVApp_Dialog)
            val dialog = AlertDialog.Builder(context)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.close, null)
                .create()
            dialog.create()
            try {
                for (id in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
                    val button = dialog.getButton(id)
                    val resting = intArrayOf(android.R.attr.state_enabled)
                    val focused = intArrayOf(android.R.attr.state_enabled, android.R.attr.state_focused)
                    val bitmap = Bitmap.createBitmap(100, 44, Bitmap.Config.ARGB_8888)
                    val background = button.background
                    background.setBounds(0, 0, bitmap.width, bitmap.height)
                    background.state = resting
                    background.draw(Canvas(bitmap))
                    val restingColor = bitmap.getPixel(50, 22)
                    bitmap.eraseColor(Color.TRANSPARENT)
                    background.state = focused
                    background.draw(Canvas(bitmap))
                    assertNotEquals(restingColor, bitmap.getPixel(50, 22))
                    assertEquals(context.getColor(R.color.accent), bitmap.getPixel(50, 22))
                    assertEquals(Color.BLACK, button.textColors.getColorForState(focused, Color.MAGENTA))
                    assertEquals(Color.WHITE, button.textColors.getColorForState(resting, Color.MAGENTA))
                    bitmap.recycle()
                }
            } finally {
                dialog.dismiss()
            }
        }
    }
}
