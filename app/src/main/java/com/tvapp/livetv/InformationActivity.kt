package com.tvapp.livetv

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tvapp.livetv.billing.IptvEntitlementManager
import com.tvapp.livetv.data.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class InformationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_information)
        configureWindow()

        val title = findViewById<TextView>(R.id.information_title)
        val body = findViewById<TextView>(R.id.information_body)
        findViewById<Button>(R.id.information_close).setOnClickListener { finish() }

        if (intent.getStringExtra(EXTRA_PAGE) == PAGE_GUIDE) {
            title.setText(R.string.user_guide)
            body.setText(R.string.user_guide_content)
        } else {
            title.setText(R.string.about)
            body.setText(R.string.loading)
            loadAbout(body)
        }
    }

    private fun configureWindow() {
        val metrics = resources.displayMetrics
        window.setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
        window.setLayout((metrics.widthPixels * 0.58f).toInt(), (metrics.heightPixels * 0.84f).toInt())
        window.attributes = window.attributes.apply {
            x = (metrics.widthPixels * 0.018f).toInt()
            dimAmount = 0f
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    private fun loadAbout(body: TextView) {
        lifecycleScope.launch {
            val statistics = withContext(Dispatchers.IO) { IptvRepository(this@InformationActivity).statistics() }
            val entitlement = IptvEntitlementManager(this@InformationActivity).snapshot()
            val remainingDays = TimeUnit.MILLISECONDS.toDays(entitlement.trialRemainingMillis)
            body.text = getString(
                R.string.about_content,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.FLAVOR,
                entitlement.state.name,
                getString(if (entitlement.accessGranted) R.string.access_active else R.string.access_inactive),
                remainingDays,
                statistics.sourceCount,
                statistics.channelCount,
                statistics.selectedChannelCount,
            )
        }
    }

    companion object {
        const val EXTRA_PAGE = "page"
        const val PAGE_ABOUT = "about"
        const val PAGE_GUIDE = "guide"
    }
}
