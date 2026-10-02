package com.tvapp.livetv

import android.content.Intent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VodHomeLayoutTest {
    @Test fun repeatedSearchFinishesLoadingAndKeyboardDoesNotResizeCatalog() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), VodHomeActivity::class.java)
        ActivityScenario.launch<VodHomeActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
                    activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                val search = activity.findViewById<EditText>(R.id.vod_search)
                search.setText("no-match-vod-layout-test")
            }
            Thread.sleep(350)
            scenario.onActivity { it.findViewById<EditText>(R.id.vod_search).setText("second-no-match-vod-layout-test") }
            Thread.sleep(350)
            scenario.onActivity { it.findViewById<EditText>(R.id.vod_search).setText("") }
            Thread.sleep(1500)
            scenario.onActivity { activity ->
                assertEquals(View.GONE, activity.findViewById<View>(R.id.vod_loading).visibility)
            }
        }
    }
}
