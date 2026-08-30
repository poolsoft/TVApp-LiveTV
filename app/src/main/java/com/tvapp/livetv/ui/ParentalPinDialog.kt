package com.tvapp.livetv.ui

import android.graphics.Typeface
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.tvapp.livetv.R
import com.tvapp.livetv.settings.ParentalControlStore

object ParentalPinDialog {
    fun create(
        activity: AppCompatActivity,
        onCreated: () -> Unit,
    ) {
        show(
            activity = activity,
            title = activity.getString(R.string.create_parental_pin),
            onComplete = { pin ->
                ParentalControlStore(activity).setPin(pin)
                onCreated()
                true
            },
        )
    }

    fun verify(
        activity: AppCompatActivity,
        title: String,
        onVerified: () -> Unit,
    ) {
        val store = ParentalControlStore(activity)
        show(
            activity = activity,
            title = title,
            onComplete = { pin ->
                store.verify(pin).also { verified ->
                    if (verified) onVerified()
                }
            },
        )
    }

    private fun show(
        activity: AppCompatActivity,
        title: String,
        onComplete: (String) -> Boolean,
    ) {
        val pinDigits = StringBuilder()
        val pinView = TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = 30f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(24.dp(activity), 28.dp(activity), 24.dp(activity), 28.dp(activity))
        }
        val feedbackView = TextView(activity).apply {
            gravity = Gravity.CENTER
            textSize = 15f
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            setText(R.string.pin_remote_hint)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(pinView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(feedbackView, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun render() {
            pinView.text = (0 until 4).joinToString("  ") { index ->
                if (index < pinDigits.length) "●" else "○"
            }
        }
        render()

        val dialog = AlertDialog.Builder(activity)
            .setTitle(title)
            .setView(content)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            pinView.requestFocus()
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                dialog.dismiss()
                return@setOnKeyListener true
            }
            val digit = keyCode.toDigit()
            when {
                digit != null && pinDigits.length < 4 -> {
                    pinDigits.append(digit)
                    render()
                    if (pinDigits.length == 4) {
                        pinView.postDelayed({
                            if (onComplete(pinDigits.toString())) {
                                dialog.dismiss()
                            } else {
                                feedbackView.setText(R.string.wrong_pin)
                                feedbackView.setTextColor(
                                    ContextCompat.getColor(activity, R.color.remote_red),
                                )
                                pinDigits.clear()
                                render()
                            }
                        }, 180L)
                    }
                    true
                }
                keyCode == KeyEvent.KEYCODE_DEL -> {
                    if (pinDigits.isNotEmpty()) pinDigits.deleteCharAt(pinDigits.lastIndex)
                    render()
                    true
                }
                else -> false
            }
        }
        dialog.show()
    }

    private fun Int.toDigit(): Int? = when (this) {
        KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> 0
        KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
        KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
        KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
        KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
        KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
        KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6
        KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> 7
        KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> 8
        KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> 9
        else -> null
    }

    private fun Int.dp(activity: AppCompatActivity): Int =
        (this * activity.resources.displayMetrics.density).toInt()
}
