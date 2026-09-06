package com.tvapp.livetv

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.media.tv.TvContract
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tvapp.livetv.data.EpgDiagnosticStep
import com.tvapp.livetv.data.ProgramRepository
import com.tvapp.livetv.data.ChannelRepository
import com.tvapp.livetv.data.TifRepository
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.PlaybackHistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EpgDiagnosticsActivity : AppCompatActivity() {
    private lateinit var channel: LiveChannel
    private lateinit var results: LinearLayout
    private lateinit var runButton: Button
    private val repository by lazy { ProgramRepository(this) }
    private val tifRepository by lazy { TifRepository(this) }
    private val channelRepository by lazy { ChannelRepository(this) }
    private val playbackHistory by lazy { PlaybackHistoryStore(this) }
    private val database by lazy { TVAppDatabase.getInstance(this) }
    private val debugLog by lazy { CrashReportStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val suppliedChannel = intent.toChannel()
        if (suppliedChannel != null) {
            showChannel(suppliedChannel)
        } else {
            setContentView(text(getString(R.string.epg_diagnostics_running), 18f, false).apply {
                gravity = Gravity.CENTER
                setBackgroundColor(ContextCompat.getColor(context, R.color.panel))
            })
            lifecycleScope.launch {
                val fallback = withContext(Dispatchers.IO) {
                    val channels = channelRepository.channels(includeHidden = true).getOrNull().orEmpty()
                    val lastKey = playbackHistory.keys().firstOrNull()
                    channels.firstOrNull { it.sourceKey == lastKey } ?: channels.firstOrNull()
                }
                if (fallback == null) finish() else showChannel(fallback)
            }
        }
    }

    private fun showChannel(selectedChannel: LiveChannel) {
        channel = selectedChannel
        setContentView(buildContent())
        runDiagnostics()
    }

    private fun buildContent(): View = ScrollView(this).apply {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(ContextCompat.getColor(this@EpgDiagnosticsActivity, R.color.panel))
        addView(LinearLayout(this@EpgDiagnosticsActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(30), dp(48), dp(36))
            addView(text(getString(R.string.epg_diagnostics_channel, channel.displayName), 26f, true))
            addView(text(
                getString(
                    R.string.epg_diagnostics_summary,
                    channel.displayNumber,
                    channel.id,
                    channel.source.name,
                    channel.epgId ?: "-",
                    channel.epgSourceId?.toString() ?: "-",
                ),
                15f,
                false,
            ).apply { setPadding(0, dp(10), 0, dp(18)) })
            runButton = Button(this@EpgDiagnosticsActivity).apply {
                setText(R.string.epg_diagnostics_run)
                setOnClickListener { runDiagnostics() }
            }
            addView(runButton, LinearLayout.LayoutParams(dp(280), dp(52)))
            results = LinearLayout(this@EpgDiagnosticsActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(18), 0, 0)
            }
            addView(results)
        })
    }

    private fun runDiagnostics() {
        runButton.isEnabled = false
        results.removeAllViews()
        results.addView(text(getString(R.string.epg_diagnostics_running), 16f, false))
        debugLog.recordDebug("EPG_DIAGNOSTICS_START | channel=${channel.sourceKey}")
        lifecycleScope.launch {
            val steps = withContext(Dispatchers.IO) {
                applicationDiagnostics() + repository.diagnose(channel)
            }
            results.removeAllViews()
            steps.forEach(::addResult)
            runButton.isEnabled = true
            runButton.requestFocus()
            debugLog.recordDebug("EPG_DIAGNOSTICS_END | channel=${channel.sourceKey}")
        }
    }

    private suspend fun applicationDiagnostics(): List<EpgDiagnosticStep> {
        fun step(query: String, block: () -> String): EpgDiagnosticStep {
            val startedAt = SystemClock.elapsedRealtime()
            return runCatching(block).fold(
                onSuccess = {
                    EpgDiagnosticStep(query, true, SystemClock.elapsedRealtime() - startedAt, it)
                },
                onFailure = {
                    EpgDiagnosticStep(
                        query,
                        false,
                        SystemClock.elapsedRealtime() - startedAt,
                        "${it.javaClass.simpleName}: ${it.message.orEmpty()}",
                    )
                },
            )
        }

        val roomChannels = runCatching {
            "userChannels=${database.channelDao().getAllChannels().size}; " +
                "groups=${database.channelDao().getGroups().size}"
        }
        val roomIptv = runCatching {
            val sources = database.iptvDao().getSources()
            val total = sources.sumOf { database.iptvDao().channelCount(it.id) }
            val selected = sources.sumOf { database.iptvDao().selectedChannelCount(it.id) }
            "sources=${sources.size}; channels=$total; selected=$selected"
        }

        return buildList {
            add(step("APP_CAPABILITIES") {
                val manager = packageManager
                "liveTv=${manager.hasSystemFeature(PackageManager.FEATURE_LIVE_TV)}; " +
                    "pip=${manager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)}; " +
                    "sdk=${android.os.Build.VERSION.SDK_INT}; device=${android.os.Build.MANUFACTURER} " +
                    android.os.Build.MODEL
            })
            add(step("TV_LISTINGS_PERMISSION") {
                "granted=${checkSelfPermission("android.permission.READ_TV_LISTINGS") == PackageManager.PERMISSION_GRANTED}"
            })
            add(step("TIF_INPUTS") {
                val inputs = tifRepository.inputs()
                "count=${inputs.size}; " + inputs.joinToString { input ->
                    "${input.id}[type=${input.type}, passthrough=${input.isPassthroughInput}]"
                }
            })
            add(step("TIF_CHANNEL_RAW") {
                if (channel.source != LiveChannel.Source.TIF) return@step "skipped: IPTV"
                val values = tifRepository.channelRawValues(channel.id).getOrThrow()
                "columns=${values.size}; " + values.take(24).joinToString { "${it.first}=${it.second}" }
            })
            add(step("TIF_LOGO_URI") {
                if (channel.source != LiveChannel.Source.TIF) return@step "skipped: IPTV"
                val uri = TvContract.buildChannelLogoUri(channel.id)
                contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    "readable=true; length=${it.length}; uri=$uri"
                } ?: "readable=false; uri=$uri"
            })
            add(step("ROOM_CHANNELS") {
                roomChannels.getOrThrow()
            })
            add(step("ROOM_IPTV") {
                roomIptv.getOrThrow()
            })
            add(EpgDiagnosticStep("PLAYBACK_CALLBACK", true, 0L, intent.getStringExtra(EXTRA_RUNTIME) ?: "unavailable"))
        }
    }

    private fun addResult(step: EpgDiagnosticStep) {
        val status = getString(
            if (step.success) R.string.epg_diagnostics_success else R.string.epg_diagnostics_failure,
        )
        val line = getString(
            R.string.epg_diagnostics_result,
            step.query,
            status,
            step.durationMillis,
            step.detail,
        )
        results.addView(text(line, 15f, false).apply {
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(ContextCompat.getColor(context, R.color.system_info_row))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })
        debugLog.recordDebug(
            "EPG_DIAGNOSTIC | channel=${channel.sourceKey}, query=${step.query}, " +
                "success=${step.success}, durationMs=${step.durationMillis}, detail=${step.detail}",
        )
    }

    private fun text(value: String, size: Float, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        gravity = Gravity.START
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_SOURCE_KEY = "source_key"
        private const val EXTRA_INPUT_ID = "input_id"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_SOURCE = "source"
        private const val EXTRA_EPG_ID = "epg_id"
        private const val EXTRA_EPG_SOURCE_ID = "epg_source_id"
        private const val EXTRA_RUNTIME = "runtime"

        fun intent(context: Context) = Intent(context, EpgDiagnosticsActivity::class.java)

        fun intent(context: Context, channel: LiveChannel, runtime: String) = Intent(context, EpgDiagnosticsActivity::class.java).apply {
            putExtra(EXTRA_ID, channel.id)
            putExtra(EXTRA_SOURCE_KEY, channel.sourceKey)
            putExtra(EXTRA_INPUT_ID, channel.inputId)
            putExtra(EXTRA_NUMBER, channel.displayNumber)
            putExtra(EXTRA_NAME, channel.displayName)
            putExtra(EXTRA_URI, channel.uri)
            putExtra(EXTRA_SOURCE, channel.source.name)
            putExtra(EXTRA_EPG_ID, channel.epgId)
            channel.epgSourceId?.let { putExtra(EXTRA_EPG_SOURCE_ID, it) }
            putExtra(EXTRA_RUNTIME, runtime)
        }

        private fun Intent.toChannel(): LiveChannel? {
            val sourceKey = getStringExtra(EXTRA_SOURCE_KEY) ?: return null
            return LiveChannel(
                id = getLongExtra(EXTRA_ID, -1L),
                sourceKey = sourceKey,
                inputId = getStringExtra(EXTRA_INPUT_ID).orEmpty(),
                displayNumber = getStringExtra(EXTRA_NUMBER).orEmpty(),
                displayName = getStringExtra(EXTRA_NAME).orEmpty(),
                uri = getStringExtra(EXTRA_URI).orEmpty(),
                source = runCatching {
                    LiveChannel.Source.valueOf(getStringExtra(EXTRA_SOURCE).orEmpty())
                }.getOrDefault(LiveChannel.Source.TIF),
                epgId = getStringExtra(EXTRA_EPG_ID),
                epgSourceId = if (hasExtra(EXTRA_EPG_SOURCE_ID)) {
                    getLongExtra(EXTRA_EPG_SOURCE_ID, -1L)
                } else {
                    null
                },
            )
        }
    }
}
