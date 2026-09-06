package com.tvapp.livetv

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.KeyEvent
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tvapp.livetv.data.XmlTvRepository
import com.tvapp.livetv.data.XmlTvSourceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class XmlTvSourcesActivity : AppCompatActivity() {
    private val repository by lazy { XmlTvRepository(this) }
    private lateinit var sourceList: ListView
    private lateinit var status: TextView
    private var sources = emptyList<XmlTvSourceSummary>()

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runOperation { repository.importDocument(uri) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_xmltv_sources)
        sourceList = findViewById(R.id.source_list)
        status = findViewById(R.id.status)
        findViewById<View>(R.id.add_url).setOnClickListener { showUrlDialog() }
        findViewById<View>(R.id.add_file).setOnClickListener {
            openFile.launch(arrayOf("application/xml", "text/xml", "application/gzip", "*/*"))
        }
        findViewById<View>(R.id.open_matches).setOnClickListener {
            startActivity(Intent(this, XmlTvEpgEditorActivity::class.java))
        }
        findViewById<View>(R.id.close).setOnClickListener { finish() }
        sourceList.setOnItemClickListener { _, _, position, _ ->
            sources.getOrNull(position)?.let(::showSourceActions)
        }
        sourceList.setOnItemLongClickListener { _, _, position, _ ->
            sources.getOrNull(position)?.let(::showSourceActions)
            true
        }
        loadSources()
        findViewById<View>(R.id.add_url).requestFocus()
    }

    override fun onResume() {
        super.onResume()
        loadSources()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val direction = when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> -1
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> 1
            else -> 0
        }
        if (direction != 0 && sourceList.hasFocus() && sources.isNotEmpty()) {
            val current = sourceList.selectedItemPosition.coerceAtLeast(0)
            sourceList.setSelection((current + direction * 6).coerceIn(0, sources.lastIndex))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun loadSources() {
        lifecycleScope.launch {
            sources = withContext(Dispatchers.IO) { repository.sourceSummaries() }
            sourceList.adapter = ArrayAdapter(
                this@XmlTvSourcesActivity,
                R.layout.item_iptv_source,
                sources.map { summary ->
                    getString(
                        R.string.xmltv_source_row_status,
                        summary.source.name,
                        getString(
                            if (summary.source.kind == XmlTvRepository.KIND_URL) {
                                R.string.xmltv_source_url
                            } else {
                                R.string.xmltv_source_file
                            },
                        ),
                        summary.channelCount,
                        summary.programCount,
                        getString(if (summary.source.enabled) R.string.source_enabled else R.string.source_disabled),
                        summary.source.lastUpdatedAt.takeIf { it > 0L }?.let {
                            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                .format(Date(it))
                        } ?: getString(R.string.never),
                        summary.source.lastError?.let { getString(R.string.source_error_short, it) }.orEmpty(),
                    )
                },
            )
        }
    }

    private fun showUrlDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.xmltv_url_hint)
            setSingleLine()
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_focusable)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.xmltv_from_url)
            .setView(input)
            .setPositiveButton(R.string.xmltv_add_url, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = input.text.toString().trim()
                val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
                if (scheme != "http" && scheme != "https") {
                    input.error = getString(R.string.xmltv_url_invalid)
                    input.requestFocus()
                } else {
                    dialog.dismiss()
                    runOperation { repository.importUrl(url) }
                }
            }
            input.requestFocus()
        }
        dialog.show()
    }

    private fun showSourceActions(summary: XmlTvSourceSummary) {
        val actions = buildList {
            add(
                Action.TOGGLE to getString(
                    if (summary.source.enabled) R.string.disable_source else R.string.enable_source,
                ),
            )
            if (summary.source.kind == XmlTvRepository.KIND_URL) {
                add(Action.REFRESH to getString(R.string.update))
                add(Action.EDIT_URL to getString(R.string.edit_xmltv_source_url))
            }
            add(Action.RENAME to getString(R.string.rename_iptv_source))
            add(Action.MATCH to getString(R.string.xmltv_match_editor))
            add(Action.DELETE to getString(R.string.delete))
        }
        val actionViews = mutableListOf<TextView>()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(TextView(this@XmlTvSourcesActivity).apply {
                text = getString(
                    R.string.xmltv_source_counts,
                    summary.channelCount,
                    summary.programCount,
                )
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
                setPadding(dp(4), 0, dp(4), dp(8))
            })
            actions.forEach { (_, label) ->
                addView(TextView(this@XmlTvSourcesActivity).apply {
                    text = label
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 17f
                    gravity = Gravity.CENTER_VERTICAL
                    isFocusable = true
                    isClickable = true
                    setBackgroundResource(R.drawable.bg_focusable)
                    setPadding(dp(20), 0, dp(20), 0)
                    actionViews += this
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
                    topMargin = dp(6)
                })
            }
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(summary.source.name)
            .setView(content)
            .setNegativeButton(R.string.close, null)
            .create()
        actionViews.forEachIndexed { index, view ->
            view.setOnClickListener {
                dialog.dismiss()
                when (actions[index].first) {
                    Action.TOGGLE -> lifecycleScope.launch {
                        withContext(Dispatchers.IO) {
                            repository.setSourceEnabled(summary.source.id, !summary.source.enabled)
                        }
                        loadSources()
                    }
                    Action.REFRESH -> runOperation { repository.refreshSource(summary.source) }
                    Action.EDIT_URL -> showEditUrlDialog(summary)
                    Action.RENAME -> showRenameDialog(summary)
                    Action.MATCH -> startActivity(Intent(this, XmlTvEpgEditorActivity::class.java))
                    Action.DELETE -> confirmDelete(summary)
                }
            }
        }
        dialog.setOnShowListener { actionViews.firstOrNull()?.requestFocus() }
        dialog.show()
    }

    private fun showEditUrlDialog(summary: XmlTvSourceSummary) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setText(summary.source.location)
            selectAll()
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_focusable)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.edit_xmltv_source_url)
            .setView(input)
            .setPositiveButton(R.string.update, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = input.text.toString().trim()
                val scheme = runCatching { Uri.parse(url).scheme?.lowercase() }.getOrNull()
                if (scheme != "http" && scheme != "https") {
                    input.error = getString(R.string.xmltv_url_invalid)
                    input.requestFocus()
                } else {
                    dialog.dismiss()
                    runOperation { repository.updateUrl(summary.source, url) }
                }
            }
            input.requestFocus()
        }
        dialog.show()
    }

    private fun showRenameDialog(summary: XmlTvSourceSummary) {
        val input = EditText(this).apply {
            setText(summary.source.name)
            selectAll()
            setTextColor(getColor(R.color.text_primary))
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.rename_iptv_source)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    input.error = getString(R.string.required_fields)
                } else {
                    dialog.dismiss()
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { repository.renameSource(summary.source.id, name) }
                        loadSources()
                    }
                }
            }
            input.requestFocus()
        }
        dialog.show()
    }

    private fun confirmDelete(summary: XmlTvSourceSummary) {
        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setMessage(getString(R.string.xmltv_delete_source_confirm, summary.source.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { repository.deleteSource(summary.source.id) }
                    status.setText(R.string.xmltv_source_deleted)
                    loadSources()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun runOperation(action: () -> Int) {
        status.setText(R.string.xmltv_importing)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess { count ->
                    status.text = getString(R.string.xmltv_import_complete, count)
                    loadSources()
                }
                .onFailure { error ->
                    status.text = getString(
                        R.string.xmltv_import_failed,
                        error.message ?: error.javaClass.simpleName,
                    )
                }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private enum class Action { TOGGLE, REFRESH, EDIT_URL, RENAME, MATCH, DELETE }
}
