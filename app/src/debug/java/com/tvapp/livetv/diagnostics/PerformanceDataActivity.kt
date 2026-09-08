package com.tvapp.livetv.diagnostics

import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import androidx.room.withTransaction
import com.tvapp.livetv.R
import com.tvapp.livetv.data.local.IptvChannelEntity
import com.tvapp.livetv.data.local.IptvSourceEntity
import com.tvapp.livetv.data.local.TVAppDatabase
import com.tvapp.livetv.data.local.XmlTvProgramEntity
import com.tvapp.livetv.data.local.XmlTvSourceEntity
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PerformanceDataActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var actionButtons: List<Button>
    private val debugLog by lazy { CrashReportStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildContent())
    }

    private fun buildContent() = ScrollView(this).apply {
        setBackgroundColor(Color.rgb(16, 18, 22))
        addView(LinearLayout(this@PerformanceDataActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(32), dp(48), dp(32))
            addView(label(R.string.debug_performance_title, 28f))
            addView(label(R.string.debug_performance_warning, 15f).apply {
                setTextColor(Color.rgb(255, 193, 7))
                setPadding(0, dp(8), 0, dp(16))
            })
            status = label(R.string.debug_performance_cleared, 17f).apply {
                setPadding(0, 0, 0, dp(16))
            }
            addView(status)
            actionButtons = DATA_SET_SIZES.map { count ->
                actionButton(
                    getString(
                        R.string.debug_performance_generate,
                        NumberFormat.getIntegerInstance().format(count),
                    ),
                ) { generate(count) }.also(::addView)
            } + actionButton(getString(R.string.debug_performance_clear), ::clearData).also(::addView) +
                actionButton(getString(R.string.close), ::finish).also(::addView)
        })
    }

    private fun generate(count: Int) {
        setBusy(true)
        status.text = getString(
            R.string.debug_performance_generating,
            NumberFormat.getIntegerInstance().format(count),
        )
        lifecycleScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val result = withContext(Dispatchers.IO) { runCatching { generateDatabase(count) } }
            val duration = SystemClock.elapsedRealtime() - startedAt
            setBusy(false)
            result.onSuccess { generated ->
                status.text = getString(
                    R.string.debug_performance_ready,
                    NumberFormat.getIntegerInstance().format(generated.first),
                    NumberFormat.getIntegerInstance().format(generated.second),
                    duration,
                )
                debugLog.recordDebug(
                    "PERF_DATA_READY | channels=${generated.first}, programs=${generated.second}, " +
                        "durationMs=$duration, database=$PERFORMANCE_DATABASE",
                )
            }.onFailure { error ->
                status.text = getString(
                    R.string.debug_performance_failed,
                    error.message ?: error.javaClass.simpleName,
                )
                debugLog.recordDebug(
                    "PERF_DATA_FAILURE | count=$count, durationMs=$duration, " +
                        "error=${error.javaClass.simpleName}",
                )
            }
        }
    }

    private suspend fun generateDatabase(count: Int): Pair<Int, Int> {
        deleteDatabase(PERFORMANCE_DATABASE)
        val database = Room.databaseBuilder(
            applicationContext,
            TVAppDatabase::class.java,
            PERFORMANCE_DATABASE,
        ).build()
        return try {
            var generatedSourceId = -1L
            database.withTransaction {
                val now = System.currentTimeMillis()
                val iptvSourceId = database.iptvDao().insertSource(
                    IptvSourceEntity(
                        name = "Performance IPTV $count",
                        location = "debug://performance/iptv/$count",
                        kind = "DEBUG",
                        lastUpdatedAt = now,
                    ),
                )
                generatedSourceId = iptvSourceId
                val xmlTvSourceId = database.xmlTvDao().insertSource(
                    XmlTvSourceEntity(
                        name = "Performance XMLTV $count",
                        location = "debug://performance/xmltv/$count",
                        kind = "DEBUG",
                        lastUpdatedAt = now,
                    ),
                )
                var start = 0
                while (start < count) {
                    val end = (start + INSERT_BATCH_SIZE).coerceAtMost(count)
                    database.iptvDao().upsertChannels(
                        (start until end).map { index -> iptvChannel(iptvSourceId, index, now) },
                    )
                    database.xmlTvDao().insertPrograms(
                        (start until end).map { index -> xmlTvProgram(xmlTvSourceId, index, now) },
                    )
                    start = end
                }
            }
            database.iptvDao().channelCount(generatedSourceId) to
                database.xmlTvDao().programCount()
        } finally {
            database.close()
        }
    }

    private fun clearData() {
        setBusy(true)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { deleteDatabase(PERFORMANCE_DATABASE) }
            status.setText(R.string.debug_performance_cleared)
            debugLog.recordDebug("PERF_DATA_CLEARED | database=$PERFORMANCE_DATABASE")
            setBusy(false)
        }
    }

    private fun iptvChannel(sourceId: Long, index: Int, now: Long) = IptvChannelEntity(
        sourceKey = "debug:performance:$index",
        sourceId = sourceId,
        tvgId = "perf-$index",
        tvgName = "Performance Channel $index",
        displayName = "Performance Channel ${index + 1}",
        streamUrl = "https://performance.invalid/live/$index.m3u8",
        logoUrl = if (index % 3 == 0) "https://performance.invalid/logo/$index.png" else null,
        groupTitle = "Category ${index % CATEGORY_COUNT}",
        userAgent = null,
        referrer = null,
        originalIndex = index,
        contentType = if (index % VOD_INTERVAL == 0) "VOD" else "LIVE",
        selected = index < SELECTED_CHANNEL_COUNT,
        lastSeenAt = now,
    )

    private fun xmlTvProgram(sourceId: Long, index: Int, now: Long) = XmlTvProgramEntity(
        sourceId = sourceId,
        channelId = "perf-$index",
        channelName = "Performance Channel $index",
        normalizedChannelId = "perf$index",
        normalizedChannelName = "performancechannel$index",
        title = "Performance Programme ${index + 1}",
        description = "Generated debug programme for repeatable performance measurements.",
        startTimeMillis = now - PROGRAM_HALF_HOUR_MS,
        endTimeMillis = now + PROGRAM_HALF_HOUR_MS,
    )

    private fun setBusy(busy: Boolean) {
        actionButtons.forEach { it.isEnabled = !busy }
    }

    private fun actionButton(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val PERFORMANCE_DATABASE = "tv-app-performance.db"
        const val INSERT_BATCH_SIZE = 500
        const val CATEGORY_COUNT = 40
        const val VOD_INTERVAL = 5
        const val SELECTED_CHANNEL_COUNT = 50
        const val PROGRAM_HALF_HOUR_MS = 30 * 60 * 1_000L
        val DATA_SET_SIZES = listOf(500, 15_000, 50_000)
    }
}
