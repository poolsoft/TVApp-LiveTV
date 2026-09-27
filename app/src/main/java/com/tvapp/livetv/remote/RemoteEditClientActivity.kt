package com.tvapp.livetv.remote

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.tvapp.livetv.R
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * REMOTEEDIT-003: phone-side "connect to TV" screen. Pairs with the 6-digit
 * code, lists channels with keyset paging, and lets the user toggle favorites
 * / hidden state. Edits made while offline are queued locally and replayed
 * on demand (REMOTEEDIT-004 behavior on the phone side). Touch-first: this
 * screen is only reached from the mobile flavor.
 */
class RemoteEditClientActivity : AppCompatActivity() {

    private lateinit var store: RemoteEditClientStore
    private lateinit var client: RemoteEditClient
    private lateinit var status: TextView
    private lateinit var listContainer: LinearLayout
    private var rows: List<RemoteEditClient.ChannelRow> = emptyList()
    private var nextCursor: String? = null
    private var query: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = RemoteEditClientStore(this)
        client = RemoteEditClient(store)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(8), 0, dp(8))
        }
        content.addView(status)
        val search = EditText(this).apply {
            hint = getString(R.string.remote_edit_client_search_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine()
        }
        search.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: android.text.Editable?) {
                    query = s?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    rows = emptyList()
                    nextPage()
                }
            },
        )
        content.addView(search)
        listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listContainer)
        setContentView(content)

        if (store.token().isBlank()) {
            showConnectPrompt()
        } else {
            nextPage()
        }
    }

    private fun showConnectPrompt() {
        val addressInput = EditText(this).apply {
            hint = getString(R.string.remote_edit_client_address_hint)
            setText(store.address())
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
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
            addView(codeInput)
            addView(nameInput)
        }
        AlertDialog.Builder(this)
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
            .show()
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
                    nextPage()
                }

                is RemoteEditClient.PairResult.Refused -> {
                    status.text = result.message
                    showConnectPrompt()
                }
            }
        }
    }

    private fun nextPage() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                client.channels(after = nextCursor, query = query)
            }
            loaded.onSuccess { page ->
                rows = rows + page
                nextCursor = page.lastOrNull()?.sourceKey
                renderRows()
                status.text = getString(R.string.remote_edit_client_row_count, rows.size)
            }.onFailure { error ->
                status.text = error.message ?: getString(R.string.remote_edit_client_error)
            }
        }
    }

    private fun renderRows() {
        listContainer.removeAllViews()
        rows.forEach { row ->
            val rowView = TextView(this).apply {
                textSize = 16f
                setPadding(dp(8), dp(12), dp(8), dp(12))
                text = buildString {
                    append(row.displayNumber)
                    append("  ")
                    append(row.displayName)
                    if (row.favorite) append("  ★")
                    if (row.hidden) append("  ⃰")
                }
                setOnClickListener { showRowActions(row) }
            }
            listContainer.addView(rowView)
        }
    }

    private fun showRowActions(row: RemoteEditClient.ChannelRow) {
        val actions = listOf(
            getString(R.string.remote_edit_client_toggle_favorite) to {
                patchChannel(row, JSONObject().put("favorite", !row.favorite))
            },
            getString(R.string.remote_edit_client_toggle_hidden) to {
                patchChannel(row, JSONObject().put("hidden", !row.hidden))
            },
        )
        val labels = actions.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(row.displayName)
            .setItems(labels) { _, which -> actions[which].second() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun patchChannel(row: RemoteEditClient.ChannelRow, patch: JSONObject) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                client.patch(row.sourceKey, revisionFor(row), patch)
            }
            when (result) {
                is RemoteEditClient.PatchResult.Applied -> {
                    rows = rows.map { current ->
                        if (current.sourceKey != row.sourceKey) {
                            current
                        } else {
                            current.copy(
                                favorite = patch.optBoolean("favorite", current.favorite),
                                hidden = patch.optBoolean("hidden", current.hidden),
                            )
                        }
                    }
                    renderRows()
                }

                is RemoteEditClient.PatchResult.Conflict -> {
                    Toast.makeText(
                        this@RemoteEditClientActivity,
                        R.string.remote_edit_client_conflict,
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                is RemoteEditClient.PatchResult.Failed -> {
                    // Offline or unreachable: queue for later replay.
                    store.enqueue(RemoteEditClientStore.PendingOp(row.sourceKey, revisionFor(row), patch))
                    Toast.makeText(
                        this@RemoteEditClientActivity,
                        R.string.remote_edit_client_queued,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    /** The phone does not track revisions per row yet; row-level revisions land
     *  with the live-refresh sprint. Until then optimistic edits use 0 and any
     *  conflict is surfaced to the user instead of being force-written. */
    private fun revisionFor(row: RemoteEditClient.ChannelRow): Long = 0L

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
