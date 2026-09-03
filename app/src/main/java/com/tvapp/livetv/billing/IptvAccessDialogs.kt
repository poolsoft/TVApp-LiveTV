package com.tvapp.livetv.billing

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.tvapp.livetv.R

object IptvAccessDialogs {
    fun requireAccess(activity: Activity, onGranted: () -> Unit): Boolean {
        val manager = IptvEntitlementManager(activity)
        val snapshot = manager.snapshot()
        if (snapshot.accessGranted) {
            onGranted()
            return true
        }
        when (snapshot.state) {
            IptvAccessState.FIRST_INSTALL -> AlertDialog.Builder(activity)
                .setTitle(R.string.iptv_trial_title)
                .setMessage(R.string.iptv_trial_message)
                .setPositiveButton(R.string.start_trial) { _, _ ->
                    manager.startTrial()
                    onGranted()
                }
                .setNegativeButton(R.string.close, null)
                .show()
            IptvAccessState.PURCHASE_PENDING -> AlertDialog.Builder(activity)
                .setTitle(R.string.purchase_pending_title)
                .setMessage(R.string.purchase_pending_message)
                .setPositiveButton(R.string.close, null)
                .show()
            else -> AlertDialog.Builder(activity)
                .setTitle(R.string.iptv_pro_required_title)
                .setMessage(R.string.iptv_pro_required_message)
                .setPositiveButton(R.string.purchase_iptv) { _, _ ->
                    Toast.makeText(activity, R.string.billing_not_connected, Toast.LENGTH_LONG).show()
                }
                .setNegativeButton(R.string.close, null)
                .show()
        }
        return false
    }
}
