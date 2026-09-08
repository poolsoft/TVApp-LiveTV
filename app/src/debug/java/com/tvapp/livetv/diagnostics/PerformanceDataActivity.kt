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
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
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
        if (getDatabasePath(PERFORMANCE_DATABASE).exists()) {
            status.setText(R.string.debug_performance_existing_data)
        }
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
            } + actionButton(getString(R.string.debug_performance_analyze), ::analyzeQueries)
                .also(::addView) +
                actionButton(getString(R.string.debug_performance_clear), ::clearData).also(::addView) +
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
        ).addMigrations(TVAppDatabase.MIGRATION_13_14)
            .addCallback(TVAppDatabase.IPTV_SEARCH_CALLBACK)
            .build()
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

    private fun analyzeQueries() {
        if (!getDatabasePath(PERFORMANCE_DATABASE).exists()) {
            status.setText(R.string.debug_performance_missing_data)
            return
        }
        setBusy(true)
        status.setText(R.string.debug_performance_analyzing)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { runQueryAnalysis() }
            }
            setBusy(false)
            result.onSuccess { analyses ->
                val warnings = analyses.count { it.hasFullScan }
                val totalDuration = analyses.sumOf(QueryAnalysis::durationMs)
                status.text = getString(
                    R.string.debug_performance_analysis_ready,
                    analyses.size,
                    warnings,
                    totalDuration,
                )
                analyses.forEach { analysis ->
                    debugLog.recordDebug(
                        "QUERY_PLAN | name=${analysis.name}, durationMs=${analysis.durationMs}, " +
                            "rows=${analysis.rowCount}, fullScan=${analysis.hasFullScan}, " +
                            "plan=${analysis.plan.joinToString(" || ")}, " +
                            "recommendation=${analysis.recommendation ?: "none"}",
                    )
                }
            }.onFailure { error ->
                status.text = getString(
                    R.string.debug_performance_failed,
                    error.message ?: error.javaClass.simpleName,
                )
                debugLog.recordDebug(
                    "QUERY_PLAN_FAILURE | error=${error.javaClass.simpleName}",
                )
            }
        }
    }

    private suspend fun runQueryAnalysis(): List<QueryAnalysis> {
        val database = Room.databaseBuilder(
            applicationContext,
            TVAppDatabase::class.java,
            PERFORMANCE_DATABASE,
        ).addMigrations(TVAppDatabase.MIGRATION_13_14)
            .addCallback(TVAppDatabase.IPTV_SEARCH_CALLBACK)
            .build()
        return try {
            val sourceId = database.iptvDao().getSources().firstOrNull()?.id
                ?: error("Performance IPTV source is missing")
            analyzeCases(database.openHelper.readableDatabase, queryCases(sourceId))
        } finally {
            database.close()
        }
    }

    private fun analyzeCases(
        database: SupportSQLiteDatabase,
        cases: List<QueryCase>,
    ): List<QueryAnalysis> = cases.map { case ->
        val startedAt = SystemClock.elapsedRealtime()
        val rowCount = database.query(SimpleSQLiteQuery(case.sql, case.args)).use { cursor ->
            var count = 0
            while (cursor.moveToNext()) count++
            count
        }
        val duration = SystemClock.elapsedRealtime() - startedAt
        val plan = database.query(
            SimpleSQLiteQuery("EXPLAIN QUERY PLAN ${case.sql}", case.args),
        ).use { cursor ->
            buildList {
                val detailColumn = cursor.getColumnIndex("detail")
                while (cursor.moveToNext()) {
                    add(cursor.getString(if (detailColumn >= 0) detailColumn else 3))
                }
            }
        }
        QueryAnalysis(
            name = case.name,
            durationMs = duration,
            rowCount = rowCount,
            plan = plan,
            hasFullScan = plan.any(::isFullTableScan),
            recommendation = case.recommendation,
        )
    }

    private fun queryCases(sourceId: Long): List<QueryCase> {
        val now = System.currentTimeMillis()
        return listOf(
            QueryCase(
                "iptv_first_page",
                "SELECT * FROM iptv_channels WHERE sourceId = ? " +
                    "ORDER BY originalIndex LIMIT 120 OFFSET 0",
                arrayOf(sourceId),
            ),
            QueryCase(
                "iptv_high_offset",
                "SELECT * FROM iptv_channels WHERE sourceId = ? " +
                    "ORDER BY originalIndex LIMIT 120 OFFSET 14000",
                arrayOf(sourceId),
                "Replace high OFFSET paging with sourceId/originalIndex keyset paging.",
            ),
            QueryCase(
                "iptv_keyset_forward",
                "SELECT * FROM iptv_channels WHERE sourceId = ? " +
                    "AND (originalIndex > ? OR (originalIndex = ? AND sourceKey > ?)) " +
                    "ORDER BY originalIndex, sourceKey LIMIT 120",
                arrayOf(sourceId, 14000, 14000, "perf:$sourceId:14000"),
            ),
            QueryCase(
                "iptv_keyset_backward",
                "SELECT * FROM iptv_channels WHERE sourceId = ? " +
                    "AND (originalIndex < ? OR (originalIndex = ? AND sourceKey < ?)) " +
                    "ORDER BY originalIndex DESC, sourceKey DESC LIMIT 120",
                arrayOf(sourceId, 14000, 14000, "perf:$sourceId:14000"),
            ),
            QueryCase(
                "iptv_live_category",
                "SELECT * FROM iptv_channels WHERE sourceId = ? " +
                    "AND contentType = 'LIVE' AND groupTitle = ? " +
                    "ORDER BY originalIndex LIMIT 120",
                arrayOf(sourceId, "Category 11"),
            ),
            QueryCase(
                "iptv_selected",
                "SELECT * FROM iptv_channels WHERE sourceId = ? AND selected = 1 " +
                    "ORDER BY originalIndex LIMIT 120",
                arrayOf(sourceId),
                "Add (sourceId, selected, originalIndex) when this route moves to projection paging.",
            ),
            QueryCase(
                "iptv_search",
                "SELECT sourceKey, displayName, logoUrl, groupTitle, originalIndex, " +
                    "contentType, selected FROM iptv_channels WHERE sourceId = ? " +
                    "AND sourceKey IN (SELECT sourceKey FROM iptv_channel_search " +
                    "WHERE iptv_channel_search MATCH ?) " +
                    "ORDER BY originalIndex LIMIT 120",
                arrayOf(sourceId, "Channel* 149*"),
            ),
            QueryCase(
                "iptv_categories",
                "SELECT DISTINCT TRIM(groupTitle) FROM iptv_channels WHERE sourceId = ? " +
                    "AND groupTitle IS NOT NULL AND TRIM(groupTitle) != '' " +
                    "ORDER BY TRIM(groupTitle) COLLATE NOCASE",
                arrayOf(sourceId),
                "Store normalized categories or index (sourceId, groupTitle) to avoid temporary B-trees.",
            ),
            QueryCase(
                "xmltv_current_program",
                "SELECT * FROM xmltv_programs WHERE sourceId IN " +
                    "(SELECT id FROM xmltv_sources WHERE enabled = 1) " +
                    "AND startTimeMillis <= ? AND endTimeMillis > ? " +
                    "AND (normalizedChannelId = ? OR normalizedChannelName = ?)",
                arrayOf(now, now, "perf149", "performancechannel149"),
            ),
        )
    }

    private fun isFullTableScan(detail: String): Boolean {
        val normalized = detail.uppercase()
        val scansLargeTable = "SCAN TABLE IPTV_CHANNELS" in normalized ||
            "SCAN IPTV_CHANNELS" in normalized ||
            "SCAN TABLE XMLTV_PROGRAMS" in normalized ||
            "SCAN XMLTV_PROGRAMS" in normalized
        return scansLargeTable && "USING INDEX" !in normalized &&
            "USING COVERING INDEX" !in normalized
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

    private data class QueryCase(
        val name: String,
        val sql: String,
        val args: Array<out Any?>,
        val recommendation: String? = null,
    )

    private data class QueryAnalysis(
        val name: String,
        val durationMs: Long,
        val rowCount: Int,
        val plan: List<String>,
        val hasFullScan: Boolean,
        val recommendation: String?,
    )

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
