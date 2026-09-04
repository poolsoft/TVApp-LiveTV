package com.tvapp.livetv

import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.text.InputType
import android.widget.EditText
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.IptvSourceSummary
import com.tvapp.livetv.databinding.ActivityIptvSourcesBinding
import com.tvapp.livetv.diagnostics.CrashReportStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class IptvSourcesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityIptvSourcesBinding
    private lateinit var repository: IptvRepository
    private lateinit var debugLog: CrashReportStore
    private var sources: List<IptvSourceSummary> = emptyList()
    private var selectedSourcePosition = -1
    private var sourceListLongPressHandled = false
    private var sourceListLongPressJob: Job? = null

    private val selectChannels = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK)
            loadSources()
        }
    }

    private val openPlaylist = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        importDocument(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIptvSourcesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = IptvRepository(this)
        debugLog = CrashReportStore(this)

        binding.importUrlButton.setOnClickListener { showM3uUrlDialog() }
        binding.importFileButton.setOnClickListener {
            openPlaylist.launch(arrayOf("audio/x-mpegurl", "application/x-mpegurl", "text/*", "*/*"))
        }
        binding.importXtreamButton.setOnClickListener(::showXtreamDialog)
        binding.importStalkerButton.setOnClickListener(::showStalkerDialog)
        binding.closeButton.setOnClickListener {
            setResult(RESULT_OK)
            finish()
        }
        binding.sourceList.onItemClickListener = AdapterView.OnItemClickListener {
                _, _, position, _ ->
            selectedSourcePosition = position
            sources.getOrNull(position)?.let { summary ->
                openChannelSelection(summary.source.id, summary.source.name)
            }
        }
        binding.sourceList.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long,
            ) {
                selectedSourcePosition = position
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedSourcePosition = -1
            }
        }
        binding.sourceList.setOnItemLongClickListener { _, _, position, _ ->
            selectedSourcePosition = position
            showSourceActions(sources[position])
            true
        }
        loadSources()
        binding.importUrlButton.requestFocus()
    }

    private fun showM3uUrlDialog() {
        val name = credentialField(R.string.source_name_hint)
        val url = credentialField(R.string.iptv_url, InputType.TYPE_TEXT_VARIATION_URI)
        val status = TextView(this).apply {
            setTextColor(getColor(R.color.accent))
            textSize = 13f
            visibility = View.GONE
        }
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            addView(name)
            addView(url)
            addView(status, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = padding / 2 })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.import_iptv_url)
            .setView(content)
            .setPositiveButton(R.string.import_action, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            val submit = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            submit.setOnClickListener {
                val enteredUrl = url.text.toString().trim()
                val enteredName = name.text.toString().trim().ifBlank {
                    Uri.parse(enteredUrl).lastPathSegment
                        ?.substringBeforeLast('.')
                        ?.takeIf(String::isNotBlank)
                        ?: Uri.parse(enteredUrl).host.orEmpty()
                }
                when {
                    enteredUrl.isBlank() -> url.error = getString(R.string.iptv_url_required)
                    enteredName.isBlank() -> name.error = getString(R.string.iptv_source_name_required)
                    else -> {
                        submit.isEnabled = false
                        status.visibility = View.VISIBLE
                        status.setText(R.string.iptv_importing)
                        debugLog.recordDebug("IPTV_URL_IMPORT START | $enteredUrl")
                        lifecycleScope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { repository.importUrl(enteredUrl, enteredName) }
                            }
                            result.onSuccess { imported ->
                                debugLog.recordDebug(
                                    "IPTV_URL_IMPORT SUCCESS | source=${imported.sourceId}, channels=${imported.channelCount}",
                                )
                                status.text = getString(
                                    R.string.iptv_import_complete,
                                    imported.channelCount,
                                )
                                binding.importStatus.text = status.text
                                loadSources()
                                setResult(RESULT_OK)
                                delay(URL_SUCCESS_MESSAGE_MILLIS)
                                dialog.dismiss()
                                openChannelSelection(imported.sourceId, imported.sourceName)
                            }.onFailure { error ->
                                debugLog.recordDebug(
                                    "IPTV_URL_IMPORT FAILURE | ${error.javaClass.name}: ${error.message}",
                                )
                                status.text = getString(
                                    R.string.iptv_url_import_failed,
                                    error.message ?: error.javaClass.simpleName,
                                )
                                submit.isEnabled = true
                                url.requestFocus()
                            }
                        }
                    }
                }
            }
        }
        dialog.show()
    }

    private fun importDocument(uri: Uri) {
        val defaultName = documentName(uri).substringBeforeLast('.').ifBlank { "IPTV" }
        promptSourceName(defaultName) { name ->
            runImport("IPTV_FILE_IMPORT", uri.toString(), openSelectionAfter = true) {
                repository.importDocument(uri, name)
            }
        }
    }

    private fun promptSourceName(defaultName: String, onConfirmed: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(defaultName)
            selectAll()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.iptv_source_name)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    input.error = getString(R.string.iptv_source_name_required)
                } else {
                    dialog.dismiss()
                    onConfirmed(name)
                }
            }
        }
        dialog.show()
    }

    private fun showXtreamDialog(unused: View) {
        val name = credentialField(R.string.source_name_hint)
        val server = credentialField(R.string.server_url_hint, InputType.TYPE_TEXT_VARIATION_URI)
        val username = credentialField(R.string.username_hint)
        val password = credentialField(
            R.string.password_hint,
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
        )
        showSourceDialog(R.string.add_xtream_title, listOf(name, server, username, password)) {
            val values = listOf(name, server, username, password).map { it.text.toString().trim() }
            if (values.any(String::isBlank)) return@showSourceDialog false
            runImport("IPTV_XTREAM_IMPORT", values[1], openSelectionAfter = true) {
                repository.importXtream(values[1], values[2], values[3], values[0])
            }
            true
        }
    }

    private fun showStalkerDialog(unused: View) {
        val name = credentialField(R.string.source_name_hint)
        val portal = credentialField(R.string.portal_url_hint, InputType.TYPE_TEXT_VARIATION_URI)
        val mac = credentialField(R.string.mac_address_hint)
        showSourceDialog(R.string.add_stalker_title, listOf(name, portal, mac)) {
            val values = listOf(name, portal, mac).map { it.text.toString().trim() }
            if (values.any(String::isBlank)) return@showSourceDialog false
            runImport("IPTV_STALKER_IMPORT", values[1], openSelectionAfter = true) {
                repository.importStalker(values[1], values[2], values[0])
            }
            true
        }
    }

    private fun credentialField(hint: Int, variation: Int = InputType.TYPE_TEXT_VARIATION_NORMAL) =
        EditText(this).apply {
            this.hint = getString(hint)
            inputType = InputType.TYPE_CLASS_TEXT or variation
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            isSingleLine = true
        }

    private fun showSourceDialog(
        title: Int,
        fields: List<EditText>,
        submit: () -> Boolean,
    ) {
        val padding = (24 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
            fields.forEach { field -> addView(field) }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (submit()) dialog.dismiss() else fields.firstOrNull { it.text.isNullOrBlank() }
                    ?.apply { error = getString(R.string.required_fields); requestFocus() }
            }
        }
        dialog.show()
    }

    private fun showSourceActions(summary: IptvSourceSummary) {
        val actions = buildList {
            add(SourceAction.SELECT to getString(R.string.select_iptv_channels))
            if (summary.source.kind != IptvRepository.KIND_DOCUMENT) {
                add(SourceAction.REFRESH to getString(R.string.refresh_iptv_source))
            }
            add(SourceAction.RENAME to getString(R.string.rename_iptv_source))
            add(SourceAction.DELETE to getString(R.string.delete))
        }
        AlertDialog.Builder(this)
            .setTitle(summary.source.name)
            .setMessage(
                getString(
                    R.string.iptv_source_selection_summary,
                    summary.selectedChannelCount,
                    summary.channelCount,
                ),
            )
            .setItems(actions.map { it.second }.toTypedArray()) { _, which ->
                when (actions[which].first) {
                    SourceAction.SELECT -> openChannelSelection(
                        summary.source.id,
                        summary.source.name,
                    )
                    SourceAction.REFRESH -> runImport("IPTV_REFRESH", summary.source.location) {
                        repository.refresh(summary.source)
                    }
                    SourceAction.RENAME -> promptSourceName(summary.source.name) { name ->
                        renameSource(summary, name)
                    }
                    SourceAction.DELETE -> confirmDeleteSource(summary)
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun renameSource(summary: IptvSourceSummary, name: String) {
        setBusy(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.rename(summary.source, name) }
            }
            setBusy(false)
            result.onSuccess {
                setResult(RESULT_OK)
                binding.importStatus.setText(R.string.iptv_source_renamed)
                loadSources()
            }.onFailure(::showError)
        }
    }

    private fun deleteSource(summary: IptvSourceSummary) {
        setBusy(true)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { repository.delete(summary.source) }
            }
            setBusy(false)
            result.onSuccess {
                debugLog.recordDebug("IPTV_SOURCE_DELETED | ${summary.source.location}")
                setResult(RESULT_OK)
                binding.importStatus.setText(R.string.iptv_source_deleted)
                loadSources()
            }.onFailure(::showError)
        }
    }

    private fun runImport(
        event: String,
        detail: String,
        openSelectionAfter: Boolean = false,
        action: suspend () -> com.tvapp.livetv.data.IptvImportResult,
    ) {
        setBusy(true)
        binding.importStatus.setText(R.string.iptv_importing)
        debugLog.recordDebug("$event START | $detail")
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { action() } }
            setBusy(false)
            result.onSuccess { imported ->
                debugLog.recordDebug(
                    "$event SUCCESS | source=${imported.sourceId}, channels=${imported.channelCount}",
                )
                binding.importStatus.text = getString(
                    R.string.iptv_import_complete,
                    imported.channelCount,
                )
                loadSources()
                setResult(RESULT_OK)
                if (openSelectionAfter) {
                    openChannelSelection(imported.sourceId, imported.sourceName)
                }
            }.onFailure { error ->
                debugLog.recordDebug("$event FAILURE | ${error.javaClass.name}: ${error.message}")
                showError(error)
            }
        }
    }

    private fun loadSources() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repository.sources() } }
            result.onSuccess { loaded ->
                sources = loaded
                if (loaded.isEmpty()) {
                    selectedSourcePosition = -1
                } else {
                    selectedSourcePosition = selectedSourcePosition.coerceIn(0, loaded.lastIndex)
                }
                val labels = loaded.map { summary ->
                    getString(
                        R.string.iptv_source_row_typed,
                        summary.source.name,
                        sourceTypeLabel(summary.source.kind),
                        summary.channelCount,
                        summary.selectedChannelCount,
                    )
                }
                binding.sourceList.adapter = ArrayAdapter(
                    this@IptvSourcesActivity,
                    R.layout.item_iptv_source,
                    labels,
                )
                if (selectedSourcePosition >= 0) {
                    binding.sourceList.setSelection(selectedSourcePosition)
                }
            }.onFailure(::showError)
        }
    }

    private fun showError(error: Throwable) {
        setBusy(false)
        binding.importStatus.text = error.message ?: error.javaClass.simpleName
    }

    private fun setBusy(busy: Boolean) {
        binding.importUrlButton.isEnabled = !busy
        binding.importFileButton.isEnabled = !busy
        binding.importXtreamButton.isEnabled = !busy
        binding.importStalkerButton.isEnabled = !busy
        binding.sourceList.isEnabled = !busy
    }

    private fun sourceTypeLabel(kind: String): String = getString(
        when (kind) {
            IptvRepository.KIND_XTREAM -> R.string.source_type_xtream
            IptvRepository.KIND_STALKER -> R.string.source_type_stalker
            else -> R.string.source_type_m3u
        },
    )

    private fun documentName(uri: Uri): String {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true) cursor.getString(0) else uri.lastPathSegment.orEmpty()
        } finally {
            cursor?.close()
        }
    }

    private fun openChannelSelection(sourceId: Long, sourceName: String) {
        selectChannels.launch(
            Intent(this, IptvChannelSelectionActivity::class.java)
                .putExtra(IptvChannelSelectionActivity.EXTRA_SOURCE_ID, sourceId)
                .putExtra(IptvChannelSelectionActivity.EXTRA_SOURCE_NAME, sourceName),
        )
    }

    private fun confirmDeleteSource(summary: IptvSourceSummary) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.delete_iptv_source_confirm, summary.source.name))
            .setPositiveButton(R.string.delete) { _, _ -> deleteSource(summary) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private enum class SourceAction { SELECT, REFRESH, RENAME, DELETE }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (binding.sourceList.hasFocus()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        sourceListLongPressHandled = false
                        sourceListLongPressJob?.cancel()
                        sourceListLongPressJob = lifecycleScope.launch {
                            delay(ViewConfiguration.getLongPressTimeout().toLong())
                            sourceListLongPressHandled = true
                            val pos = selectedSourcePosition.takeIf { it in sources.indices }
                                ?: binding.sourceList.selectedItemPosition.takeIf { it in sources.indices }
                            pos?.let { showSourceActions(sources[it]) }
                        }
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        sourceListLongPressJob?.cancel()
                        if (sourceListLongPressHandled) {
                            sourceListLongPressHandled = false
                            return true
                        }
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        sourceListLongPressJob?.cancel()
        super.onDestroy()
    }

    private companion object {
        const val URL_SUCCESS_MESSAGE_MILLIS = 900L
    }
}
