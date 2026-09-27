package com.tvapp.livetv.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tvapp.livetv.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * REMOTEEDIT-003 (redesigned): phone-side "TV management" screen. The phone
 * no longer edits channels; this page only manages sources — add IPTV
 * playlist / XMLTV EPG by URL, update or refresh an existing URL source,
 * delete a source — and shows queued import status. Detailed channel work
 * (renames, favorites, reordering, channel selection) lives in the embedded
 * web panel served by the TV at http://<tv-ip>:8890/.
 *
 * Address entry is eased with NSD: the TV advertises `_tvapp._tcp.` on the
 * Wi-Fi network, so the phone discovers `http://<ip>:<port>` without typing.
 * Touch-first: this screen is only reached from the mobile flavor.
 */
class RemoteEditClientActivity : AppCompatActivity() {

    private lateinit var store: RemoteEditClientStore
    private lateinit var client: RemoteEditClient
    private lateinit var status: TextView
    private lateinit var listContainer: LinearLayout
    private var sources: List<RemoteEditClient.SourceRow> = emptyList()
    private var imports: List<RemoteEditClient.ImportRow> = emptyList()
    private var importPollJob: Job? = null

    private var nsdManager: NsdManager? = null
    private var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RemoteEditClientStore(this)
        client = RemoteEditClient(store)
        setContentView(buildContentView())
        title = getString(R.string.remote_edit_client_title)

        if (store.token().isBlank()) {
            showConnectPrompt()
        } else {
            refreshAll()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNsdDiscovery()
        importPollJob?.cancel()
    }

    /* ---------- View construction ---------- */

    private fun buildContentView(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        status = TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(8), 0, dp(8))
        }
        content.addView(status)

        val addIptv = TextView(this).apply {
            text = getString(R.string.remote_edit_client_add_iptv)
            textSize = 16f
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            setOnClickListener { showAddSourceDialog(RemoteImportQueue.Request.Kind.IPTV) }
        }
        val addXmltv = TextView(this).apply {
            text = getString(R.string.remote_edit_client_add_xmltv)
            textSize = 16f
            setPadding(0, dp(4), 0, dp(4))
            isClickable = true
            setOnClickListener { showAddSourceDialog(RemoteImportQueue.Request.Kind.XMLTV) }
        }
        content.addView(addIptv)
        content.addView(addXmltv)

        content.addView(TextView(this).apply {
            text = getString(R.string.remote_edit_client_sources_header)
            textSize = 15f
            setPadding(0, dp(12), 0, dp(4))
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { weight = 1f }
        }
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(listContainer)
        content.addView(scroll)
        return content
    }

    /* ---------- Pairing / discovery ---------- */

    private fun showConnectPrompt() {
        val addressInput = EditText(this).apply {
            hint = getString(R.string.remote_edit_client_address_hint)
            setText(store.address())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val discovered = TextView(this).apply {
            text = getString(R.string.remote_edit_client_discovering)
            textSize = 13f
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        discovered.setOnClickListener {
            val address = discovered.tag as? String ?: return@setOnClickListener
            addressInput.setText(address)
        }
        val codeInput = EditText(this).apply {
            hint = getString(R.string.remote_edit_client_code_hint)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.remote_edit_client_name_hint)
            setText(android.os.Build.MODEL)
            setSingleLine()
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, 0, pad, 0)
            addView(addressInput)
            addView(discovered)
            addView(codeInput)
            addView(nameInput)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.remote_edit_client_connect_title)
            .setView(container)
            .setPositiveButton(R.string.remote_edit_client_connect) { _, _ ->
                val address = addressInput.text.toString().trim()
                if (address.isNotBlank()) {
                    store.setAddress(address)
                    pairAndLoad(codeInput.text.toString(), nameInput.text.toString().trim())
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.show()
        startNsdDiscovery { serviceAddress ->
            runOnUiThread {
                discovered.text = getString(R.string.remote_edit_client_found_tv, serviceAddress)
                discovered.tag = serviceAddress
                if (addressInput.text.isNullOrBlank()) addressInput.setText(serviceAddress)
            }
        }
    }

    /** NSD discovery of `_tvapp._tcp.`; the first found TV prefills the
     *  address so the user does not type IP:port by hand. */
    private fun startNsdDiscovery(onFound: (String) -> Unit) {
        val manager = nsdManager ?: (getSystemService(Context.NSD_SERVICE) as? NsdManager) ?: return
        nsdManager = manager
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceName?.contains(SERVICE_NAME_HINT, ignoreCase = true) != true) return
                runCatching {
                    manager.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val host = info.host?.hostAddress ?: return
                                val port = info.port
                                if (host.isNotBlank() && port > 0) onFound("$host:$port")
                            }
                        },
                    )
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
        }
        nsdDiscoveryListener = listener
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun stopNsdDiscovery() {
        val manager = nsdManager ?: return
        val listener = nsdDiscoveryListener ?: return
        runCatching { manager.stopServiceDiscovery(listener) }
        nsdDiscoveryListener = null
    }

    private fun pairAndLoad(code: String, deviceName: String) {
        lifecycleScope.launch {
            status.text = getString(R.string.remote_edit_client_pairing)
            val result = withContext(Dispatchers.IO) {
                client.pair(store.address(), code, deviceName)
            }
            when (result) {
                is RemoteEditClient.PairResult.Paired -> {
                    store.setToken(result.token)
                    status.text = getString(R.string.remote_edit_client_connected)
                    refreshAll()
                }

                is RemoteEditClient.PairResult.Refused -> {
                    status.text = result.message
                    showConnectPrompt()
                }
            }
        }
    }

    /* ---------- Source management ---------- */

    private fun refreshAll() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { client.sources() }
            loaded.onSuccess { rows ->
                sources = rows
                renderRows()
                status.text = getString(R.string.remote_edit_client_source_count, rows.size)
            }.onFailure { error ->
                status.text = error.message ?: getString(R.string.remote_edit_client_error)
            }
        }
        pollImports()
    }

    private fun renderRows() {
        listContainer.removeAllViews()
        sources.forEach { row -> listContainer.addView(sourceRowView(row)) }
        if (imports.isNotEmpty()) {
            listContainer.addView(TextView(this).apply {
                text = getString(R.string.remote_edit_client_imports_header)
                textSize = 15f
                setPadding(0, dp(16), 0, dp(4))
            })
            imports.forEach { import ->
                listContainer.addView(TextView(this).apply {
                    textSize = 14f
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    text = formatImport(import)
                })
            }
        }
    }

    private fun formatImport(import: RemoteEditClient.ImportRow): String = buildString {
        append(if (import.kind == "xmltv" || import.kind == "epg") "XMLTV" else "IPTV")
        append(" · ")
        append(import.name)
        append(" · ")
        append(import.status)
        if (import.importedChannels > 0) {
            append(" · ")
            append(getString(R.string.remote_edit_client_import_channels, import.importedChannels))
        }
        import.error?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }

    private fun sourceRowView(row: RemoteEditClient.SourceRow): View {
        val kindLabel = if (row.kind == "xmltv") {
            getString(R.string.remote_edit_client_kind_xmltv)
        } else {
            getString(R.string.remote_edit_client_kind_iptv)
        }
        val summary = if (row.kind == "xmltv") {
            getString(R.string.remote_edit_client_channel_count, row.channelCount)
        } else {
            getString(
                R.string.remote_edit_client_selection_count,
                row.channelCount,
                row.selectedCount ?: 0,
            )
        }
        return TextView(this).apply {
            textSize = 16f
            setPadding(dp(8), dp(12), dp(8), dp(12))
            text = buildString {
                append(kindLabel).append(" · ").append(row.name)
                append('\n')
                append(summary)
                if (!row.error.isNullOrBlank()) {
                    append('\n')
                    append(getString(R.string.remote_edit_client_source_error, row.error))
                }
            }
            setOnClickListener { showSourceActions(row) }
        }
    }

    private fun showSourceActions(row: RemoteEditClient.SourceRow) {
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        if (row.urlKind) {
            actions += getString(R.string.remote_edit_client_refresh) to { doRefresh(row) }
            actions += getString(R.string.remote_edit_client_change_url) to { showChangeUrlDialog(row) }
        }
        actions += getString(R.string.remote_edit_client_delete) to { confirmDelete(row) }
        val labels = actions.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(row.name)
            .setItems(labels) { _, which -> actions[which].second() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun doRefresh(row: RemoteEditClient.SourceRow) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { client.refreshSource(row.kind, row.id) }
            when (result) {
                is RemoteEditClient.MutationResult.Applied -> {
                    toast(R.string.remote_edit_client_refresh_started)
                    refreshAll()
                }

                is RemoteEditClient.MutationResult.Failed ->
                    toast(R.string.remote_edit_client_error)
            }
        }
    }

    private fun showChangeUrlDialog(row: RemoteEditClient.SourceRow) {
        val input = EditText(this).apply {
            hint = getString(R.string.remote_edit_client_url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.remote_edit_client_change_url)
            .setView(input)
            .setPositiveButton(R.string.remote_edit_client_queue_import) { _, _ ->
                val url = input.text.toString().trim()
                if (!url.startsWith("http")) {
                    toast(R.string.xmltv_url_invalid)
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    // Address change = refresh with the new URL (the TV
                    // re-imports in place through the same pipeline).
                    val result = withContext(Dispatchers.IO) {
                        client.queueImport(row.name, url, row.kind)
                    }
                    result.onSuccess {
                        toast(R.string.remote_edit_client_import_queued)
                        refreshAll()
                    }.onFailure { toast(R.string.remote_edit_client_error) }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(row: RemoteEditClient.SourceRow) {
        AlertDialog.Builder(this)
            .setTitle(R.string.remote_edit_client_delete)
            .setMessage(getString(R.string.remote_edit_client_delete_confirm, row.name))
            .setPositiveButton(R.string.remote_edit_client_delete) { _, _ ->
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { client.deleteSource(row.kind, row.id) }
                    when (result) {
                        is RemoteEditClient.MutationResult.Applied -> refreshAll()
                        is RemoteEditClient.MutationResult.Failed ->
                            toast(R.string.remote_edit_client_error)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddSourceDialog(kind: RemoteImportQueue.Request.Kind) {
        val urlInput = EditText(this).apply {
            hint = getString(R.string.remote_edit_client_url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
        }
        val nameInput = EditText(this).apply {
            hint = getString(R.string.remote_edit_client_source_name_hint)
            setSingleLine()
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, 0, pad, 0)
            addView(urlInput)
            addView(nameInput)
        }
        AlertDialog.Builder(this)
            .setTitle(
                if (kind == RemoteImportQueue.Request.Kind.XMLTV) {
                    R.string.remote_edit_client_add_xmltv
                } else {
                    R.string.remote_edit_client_add_iptv
                },
            )
            .setView(container)
            .setPositiveButton(R.string.remote_edit_client_queue_import) { _, _ ->
                val url = urlInput.text.toString().trim()
                if (url.startsWith("http")) {
                    queueImport(
                        nameInput.text.toString().trim(),
                        url,
                        if (kind == RemoteImportQueue.Request.Kind.XMLTV) "xmltv" else "iptv",
                    )
                } else {
                    Toast.makeText(this, R.string.xmltv_url_invalid, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun queueImport(name: String, url: String, kind: String) {
        lifecycleScope.launch {
            val queued = withContext(Dispatchers.IO) { client.queueImport(name, url, kind) }
            queued.onSuccess {
                toast(R.string.remote_edit_client_import_queued)
                refreshAll()
            }.onFailure { toast(R.string.remote_edit_client_error) }
        }
    }

    private fun pollImports() {
        importPollJob?.cancel()
        importPollJob = lifecycleScope.launch {
            while (isActive) {
                val loaded = withContext(Dispatchers.IO) { client.imports() }
                loaded.onSuccess { rows ->
                    imports = rows
                    renderRows()
                }
                delay(3_000)
            }
        }
    }

    private fun toast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val SERVICE_TYPE = "_tvapp._tcp."
        const val SERVICE_NAME_HINT = "TVApp"
    }
}
