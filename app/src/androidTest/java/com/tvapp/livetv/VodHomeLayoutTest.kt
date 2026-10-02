package com.tvapp.livetv

import android.content.Intent
import android.content.Context
import android.view.View
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.IptvSourceEntity
import com.tvapp.livetv.data.local.IptvChannelEntity
import com.tvapp.livetv.data.local.VodMetadataEntity
import kotlinx.coroutines.runBlocking
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VodHomeLayoutTest {
    @Test fun populatedCatalogPagesAndDetailsRemainUsable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = TVAppDatabase.getInstance(context)
        val prefs = context.getSharedPreferences("vod-library", Context.MODE_PRIVATE)
        val oldSource = prefs.getLong("source", -1)
        val source = db.iptvDao().insertSource(IptvSourceEntity(name = "VOD UI test fixture", location = "fixture:vod-ui-${System.nanoTime()}", kind = "FILE"))
        try {
            db.iptvDao().upsertChannels((0 until 120).map { index ->
                IptvChannelEntity("ui-fixture:$source:$index", source, null, null, "Fixture movie $index", "https://example.invalid/$index.mp4",
                    null, "Fixture category", null, null, originalIndex = index, contentType = "VOD", lastSeenAt = 1)
            })
            db.vodDao().upsert(listOf(VodMetadataEntity("ui-fixture:$source:0", source, "MOVIE", "0", name = "Fixture movie 0",
                description = (1..30).joinToString("\n") { "Fixture description line $it" }, updatedAt = System.currentTimeMillis())))
            prefs.edit().putLong("source", source).commit()
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            ActivityScenario.launch<VodHomeActivity>(Intent(context, VodHomeActivity::class.java)).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.text("Fixture movie 0")), 10000))
                device.takeScreenshot(File(context.getExternalFilesDir(null), "vod-catalog-test.png"))
                scenario.onActivity { it.findViewById<View>(R.id.vod_next).performClick() }
                assertTrue(device.wait(Until.hasObject(By.text("Fixture movie 60")), 5000))
                scenario.onActivity { it.findViewById<View>(R.id.vod_previous).performClick() }
                assertTrue(device.wait(Until.hasObject(By.text("Fixture movie 59")), 5000))
                scenario.onActivity { it.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.vod_grid).scrollToPosition(0) }
                assertTrue(device.wait(Until.hasObject(By.text("Fixture movie 0")), 5000))
                device.findObject(By.text("Fixture movie 0")).click()
                assertTrue(device.wait(Until.hasObject(By.res("com.tvapp.livetv", "vod_detail_scroll")), 5000))
                device.takeScreenshot(File(context.getExternalFilesDir(null), "vod-detail-test.png"))
                device.pressBack()
                assertTrue(device.wait(Until.gone(By.res("com.tvapp.livetv", "vod_detail_scroll")), 5000))
                assertTrue(device.wait(Until.hasObject(By.text("Fixture movie 0")), 5000))
            }
        } finally {
            db.iptvDao().getSource(source)?.let { db.iptvDao().deleteSource(it) }
            prefs.edit().apply {
                prefs.all.keys.filter { it.startsWith("$source:") }.forEach(::remove)
                if (oldSource < 0) remove("source") else putLong("source", oldSource)
            }.commit()
        }
    }
    @Test fun searchDialogDoesNotResizeOrClearCatalog() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val intent = Intent(ApplicationProvider.getApplicationContext(), VodHomeActivity::class.java)
        ActivityScenario.launch<VodHomeActivity>(intent).use { scenario ->
            device.wait(Until.hasObject(By.res("com.tvapp.livetv", "vod_search_action")), 5000)
            var height = 0
            scenario.onActivity { activity ->
                assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING,
                    activity.window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST)
                height = activity.findViewById<View>(R.id.vod_grid).height
                activity.findViewById<View>(R.id.vod_search_action).performClick()
            }
            assertTrue(device.wait(Until.hasObject(By.res("com.tvapp.livetv", "vod_search")), 5000))
            device.findObject(By.res("com.tvapp.livetv", "vod_search")).text = "no-match-layout-test"
            scenario.onActivity { activity ->
                assertEquals(height, activity.findViewById<View>(R.id.vod_grid).height)
            }
            device.pressBack()
            if (device.hasObject(By.res("com.tvapp.livetv", "vod_search"))) device.pressBack()
            assertTrue(device.wait(Until.gone(By.res("com.tvapp.livetv", "vod_search")), 5000))
        }
    }
}
