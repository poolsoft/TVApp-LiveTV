package com.tvapp.livetv.remote

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.tvapp.livetv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * REMOTEEDIT OSD: a small card over playback showing live remote-import
 * progress (queued from the web panel or phone). Binds to the
 * [RemoteImportQueue] listener and to the frontmost activity's content view
 * via [Application.ActivityLifecycleCallbacks], so MainActivity needs no
 * code changes. The card appears while an import runs and self-dismisses a
 * few seconds after it finishes. It is non-focusable except for its Cancel
 * button and never steals focus from active playback OSDs; a newer request
 * replaces the visible one ("one primary OSD at a time" TV rule).
 */
object RemoteImportOverlayController {

    private const val AUTO_DISMISS_MILLIS = 5_000L

    private var scope: CoroutineScope? = null
    private var queueRef: RemoteImportQueue? = null
    private var appContext: Context? = null
    private var lifecycleCallbacks: Application.ActivityLifecycleCallbacks? = null

    /** Frontmost activity (weakly held) as host for the card. */
    @Volatile
    private var currentActivity: WeakReference<Activity>? = null

    /** Last request rendered; drives auto-dismiss and the Cancel button. */
    @Volatile
    private var lastRequest: RemoteImportQueue.Request? = null

    @Volatile
    private var attached: View? = null

    fun bind(context: Context, queue: RemoteImportQueue) {
        unbind()
        val application = context.applicationContext as? Application ?: return
        appContext = application.applicationContext
        queueRef = queue
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = WeakReference(activity)
                // Re-attach after host switches (rotation/background return).
                lastRequest?.let { last -> render(last) }
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity?.get() === activity) removeCard()
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity?.get() === activity) {
                    currentActivity = null
                    attached = null
                }
            }
        }
        lifecycleCallbacks = callbacks
        application.registerActivityLifecycleCallbacks(callbacks)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { newScope ->
            queue.listener = RemoteImportQueue.Listener { request ->
                lastRequest = request
                newScope.launch { render(request) }
            }
        }
    }

    fun unbind() {
        scope?.cancel()
        scope = null
        queueRef?.listener = null
        queueRef = null
        lifecycleCallbacks?.let { callbacks ->
            (appContext as? Application)?.unregisterActivityLifecycleCallbacks(callbacks)
        }
        lifecycleCallbacks = null
        appContext = null
        currentActivity = null
        removeCard()
        lastRequest = null
    }

    private fun host(): ViewGroup? {
        val activity = currentActivity?.get() ?: return null
        if (activity.isFinishing || activity.isDestroyed) return null
        return activity.findViewById(android.R.id.content) ?: return null
    }

    private fun render(request: RemoteImportQueue.Request) {
        val finished = when (request.status) {
            RemoteImportQueue.Request.Status.DONE,
            RemoteImportQueue.Request.Status.FAILED,
            RemoteImportQueue.Request.Status.CANCELLED,
            -> true

            else -> false
        }
        if (finished) {
            updateCard(request, finished = true)
            val renderedRequest = request
            scope?.launch {
                delay(AUTO_DISMISS_MILLIS)
                if (lastRequest?.id == renderedRequest.id) {
                    removeCard()
                    lastRequest = null
                }
            }
        } else {
            updateCard(request, finished = false)
        }
    }

    private fun removeCard() {
        attached?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        attached = null
    }

    private fun updateCard(request: RemoteImportQueue.Request, finished: Boolean) {
        val host = host() ?: return
        var card = attached
        if (card == null || card.parent == null) {
            removeCard()
            card = buildCard(host.context)
            host.addView(
                card,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END,
                ).apply {
                    val margin = dp(host.context, 20)
                    setMargins(margin, margin, margin, margin)
                },
            )
            attached = card
        }
        val panel = card as LinearLayout
        val title = panel.findViewById<TextView>(R.id.remote_import_title)
        val detail = panel.findViewById<TextView>(R.id.remote_import_detail)
        val cancel = panel.findViewById<Button>(R.id.remote_import_cancel)
        title.text = requestText(request)
        detail.text = statusText(host.context, request)
        cancel.visibility =
            if (request.status == RemoteImportQueue.Request.Status.RUNNING ||
                request.status == RemoteImportQueue.Request.Status.PENDING
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
    }

    private fun requestText(request: RemoteImportQueue.Request): String =
        (if (request.kind == RemoteImportQueue.Request.Kind.XMLTV) "XMLTV" else "IPTV") +
            " · " + request.name

    private fun statusText(context: Context, request: RemoteImportQueue.Request): String = when (request.status) {
        RemoteImportQueue.Request.Status.PENDING ->
            context.getString(R.string.remote_import_status_pending)

        RemoteImportQueue.Request.Status.RUNNING ->
            context.getString(R.string.remote_import_status_running, request.importedChannels)

        RemoteImportQueue.Request.Status.DONE ->
            context.getString(R.string.remote_import_status_done, request.importedChannels)

        RemoteImportQueue.Request.Status.CANCELLED ->
            context.getString(R.string.remote_import_status_cancelled)

        else -> request.error ?: context.getString(R.string.remote_import_status_failed)
    }

    private fun buildCard(context: Context): View {
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(226, 17, 24, 29))
                setStroke(dp(context, 1), Color.argb(85, 255, 255, 255))
                cornerRadius = dp(context, 10).toFloat()
            }
            val pad = dp(context, 16)
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(context).apply {
            id = R.id.remote_import_title
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        val detail = TextView(context).apply {
            id = R.id.remote_import_detail
            setTextColor(Color.argb(210, 255, 255, 255))
            textSize = 14f
        }
        val cancel = Button(context).apply {
            id = R.id.remote_import_cancel
            text = context.getString(R.string.remote_import_cancel)
            isAllCaps = false
            setTextColor(Color.WHITE)
            textSize = 14f
            minHeight = dp(context, 40)
            background = GradientDrawable().apply {
                setColor(Color.argb(70, 255, 255, 255))
                cornerRadius = dp(context, 8).toFloat()
            }
            setOnClickListener {
                val id = lastRequest?.id ?: return@setOnClickListener
                queueRef?.cancel(id)
            }
        }
        panel.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        panel.addView(
            detail,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 6) },
        )
        panel.addView(
            cancel,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 10) },
        )
        return panel
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
