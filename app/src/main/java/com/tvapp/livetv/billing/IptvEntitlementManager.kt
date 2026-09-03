package com.tvapp.livetv.billing

import android.content.Context
import com.tvapp.livetv.BuildConfig
import java.util.concurrent.TimeUnit

enum class IptvAccessState {
    FREE_BUILD,
    FIRST_INSTALL,
    TRIAL_ACTIVE,
    TRIAL_EXPIRED,
    PURCHASE_PENDING,
    PURCHASE_COMPLETED,
    PURCHASE_RESTORED,
}

data class IptvEntitlementSnapshot(
    val state: IptvAccessState,
    val accessGranted: Boolean,
    val trialRemainingMillis: Long = 0L,
    val simulated: Boolean = false,
)

class IptvEntitlementManager(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun snapshot(now: Long = System.currentTimeMillis()): IptvEntitlementSnapshot {
        val trialStartedAt = preferences.getLong(KEY_TRIAL_STARTED_AT, 0L)
        return IptvEntitlementPolicy.evaluate(
            proRequired = BuildConfig.IPTV_PRO_REQUIRED,
            simulatedState = debugState(),
            trialStartedAt = trialStartedAt,
            now = now,
        )
    }

    fun startTrial(now: Long = System.currentTimeMillis()): IptvEntitlementSnapshot {
        if (debugState() == IptvAccessState.FIRST_INSTALL) {
            setDebugState(IptvAccessState.TRIAL_ACTIVE)
            return snapshot(now)
        }
        if (preferences.getLong(KEY_TRIAL_STARTED_AT, 0L) == 0L) {
            preferences.edit().putLong(KEY_TRIAL_STARTED_AT, now).apply()
        }
        return snapshot(now)
    }

    fun setDebugState(state: IptvAccessState?) {
        check(BuildConfig.DEBUG) { "Test yetkisi yalnızca debug derlemesinde değiştirilebilir." }
        preferences.edit().apply {
            if (state == null) remove(KEY_DEBUG_STATE) else putString(KEY_DEBUG_STATE, state.name)
        }.apply()
    }

    private fun debugState(): IptvAccessState? {
        if (!BuildConfig.DEBUG) return null
        return preferences.getString(KEY_DEBUG_STATE, null)?.let { stored ->
            runCatching { IptvAccessState.valueOf(stored) }.getOrNull()
        }
    }

    companion object {
        val TRIAL_DURATION_MILLIS: Long = TimeUnit.DAYS.toMillis(7)
        private const val PREFERENCES = "iptv-entitlement"
        private const val KEY_TRIAL_STARTED_AT = "trial-started-at"
        private const val KEY_DEBUG_STATE = "debug-state"
    }
}

internal object IptvEntitlementPolicy {
    fun evaluate(
        proRequired: Boolean,
        simulatedState: IptvAccessState?,
        trialStartedAt: Long,
        now: Long,
    ): IptvEntitlementSnapshot {
        simulatedState?.let { state ->
            return IptvEntitlementSnapshot(
                state = state,
                accessGranted = state.grantsAccess,
                trialRemainingMillis = if (state == IptvAccessState.TRIAL_ACTIVE) {
                    IptvEntitlementManager.TRIAL_DURATION_MILLIS / 2
                } else 0L,
                simulated = true,
            )
        }
        if (!proRequired) {
            return IptvEntitlementSnapshot(IptvAccessState.FREE_BUILD, accessGranted = true)
        }
        if (trialStartedAt == 0L) {
            return IptvEntitlementSnapshot(IptvAccessState.FIRST_INSTALL, accessGranted = false)
        }
        val remaining = (
            trialStartedAt + IptvEntitlementManager.TRIAL_DURATION_MILLIS - now
        ).coerceAtLeast(0L)
        return IptvEntitlementSnapshot(
            state = if (remaining > 0L) IptvAccessState.TRIAL_ACTIVE else IptvAccessState.TRIAL_EXPIRED,
            accessGranted = remaining > 0L,
            trialRemainingMillis = remaining,
        )
    }

    private val IptvAccessState.grantsAccess: Boolean
        get() = this == IptvAccessState.FREE_BUILD ||
            this == IptvAccessState.TRIAL_ACTIVE ||
            this == IptvAccessState.PURCHASE_COMPLETED ||
            this == IptvAccessState.PURCHASE_RESTORED
}
