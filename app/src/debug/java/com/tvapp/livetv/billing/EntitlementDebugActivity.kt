package com.tvapp.livetv.billing

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.tvapp.livetv.R
import java.util.concurrent.TimeUnit

class EntitlementDebugActivity : AppCompatActivity() {
    private lateinit var manager: IptvEntitlementManager
    private lateinit var stateLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = IptvEntitlementManager(this)
        setContentView(buildContent())
        renderState()
    }

    private fun buildContent() = ScrollView(this).apply {
        setBackgroundColor(Color.rgb(16, 18, 22))
        addView(LinearLayout(this@EntitlementDebugActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(48), dp(32))
            addView(label(R.string.debug_billing_title, 28f))
            addView(label(R.string.debug_billing_warning, 15f).apply {
                setTextColor(Color.rgb(255, 193, 7))
                setPadding(0, dp(8), 0, dp(16))
            })
            stateLabel = label(R.string.debug_billing_state_unknown, 18f).apply {
                setPadding(0, 0, 0, dp(16))
            }
            addView(stateLabel)
            scenario(R.string.debug_state_first_install, IptvAccessState.FIRST_INSTALL)
            scenario(R.string.debug_state_trial_active, IptvAccessState.TRIAL_ACTIVE)
            scenario(R.string.debug_state_trial_expired, IptvAccessState.TRIAL_EXPIRED)
            scenario(R.string.debug_state_purchase_pending, IptvAccessState.PURCHASE_PENDING)
            scenario(R.string.debug_state_purchase_completed, IptvAccessState.PURCHASE_COMPLETED)
            scenario(R.string.debug_state_purchase_restored, IptvAccessState.PURCHASE_RESTORED)
            addView(actionButton(R.string.debug_state_normal) {
                manager.setDebugState(null)
                renderState()
            })
            addView(actionButton(R.string.debug_test_access) {
                IptvAccessDialogs.requireAccess(this@EntitlementDebugActivity) { renderState() }
            })
            addView(actionButton(R.string.close) { finish() })
        })
    }

    private fun LinearLayout.scenario(label: Int, state: IptvAccessState) {
        addView(actionButton(label) {
            manager.setDebugState(state)
            renderState()
        })
    }

    private fun actionButton(label: Int, action: () -> Unit) = Button(this).apply {
        setText(label)
        isAllCaps = false
        textSize = 17f
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setTextColor(getColorStateList(R.color.settings_title_text))
        background = getDrawable(R.drawable.bg_settings_item)
        setPadding(dp(20), 0, dp(20), 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(58)).apply {
            bottomMargin = dp(7)
        }
        setOnClickListener { action() }
    }

    private fun label(text: Int, size: Float) = TextView(this).apply {
        setText(text)
        textSize = size
        setTextColor(Color.WHITE)
    }

    private fun renderState() {
        val snapshot = manager.snapshot()
        val remainingHours = TimeUnit.MILLISECONDS.toHours(snapshot.trialRemainingMillis)
        stateLabel.text = getString(
            R.string.debug_billing_state,
            snapshot.state.name,
            if (snapshot.accessGranted) getString(R.string.debug_access_open) else getString(R.string.debug_access_locked),
            remainingHours,
        )
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
