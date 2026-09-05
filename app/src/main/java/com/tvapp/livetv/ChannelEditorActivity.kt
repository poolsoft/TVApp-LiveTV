package com.tvapp.livetv

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.tvapp.livetv.data.ChannelRepository
import com.tvapp.livetv.data.AppBackupRepository
import com.tvapp.livetv.data.XmlTvRepository
import com.tvapp.livetv.data.TifRepository
import com.tvapp.livetv.databinding.ActivityChannelEditorBinding
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.IptvPlaybackController
import com.tvapp.livetv.settings.ChannelSourceFilterStore
import com.tvapp.livetv.settings.ParentalControlStore
import com.tvapp.livetv.ui.ChannelEditorAdapter
import com.tvapp.livetv.ui.ParentalPinDialog
import com.tvapp.livetv.billing.IptvAccessDialogs
import com.tvapp.livetv.billing.IptvEntitlementManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChannelEditorActivity : AppCompatActivity() {
    private enum class Mode { NORMAL, MULTI_SELECT, MOVE }

    private lateinit var binding: ActivityChannelEditorBinding
    private lateinit var repository: ChannelRepository
    private lateinit var crashReportStore: CrashReportStore
    private lateinit var adapter: ChannelEditorAdapter
    private lateinit var iptvPreview: IptvPlaybackController
    private lateinit var parentalControlStore: ParentalControlStore
    private lateinit var xmlTvRepository: XmlTvRepository
    private val channels = mutableListOf<LiveChannel>()
    private val selectedKeys = linkedSetOf<String>()
    private val movingKeys = linkedSetOf<String>()
    private var originalOrder: List<LiveChannel> = emptyList()
    private var mode = Mode.NORMAL
    private var focusedChannel: LiveChannel? = null
    private var previewJob: Job? = null
    private var channelLoadJob: Job? = null
    private var moveTargetJob: Job? = null
    private var moveTargetInput = ""
    private var navigationNumberJob: Job? = null
    private var navigationNumberInput = ""
    private var previewSourceKey: String? = null
    private var operationInProgress = false
    private var restoredStateApplied = false
    private var restoredFocusedKey: String? = null
    private var restoredMode: Mode = Mode.NORMAL
    private lateinit var backupRepository: AppBackupRepository
    private val editIptvSources = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadChannels(syncMessage = true)
            setResult(RESULT_OK)
        }
    }
    private val createBackupFile = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let(::exportBackup)
    }
    private val openBackupFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let(::confirmImportBackup)
    }
    private val openXmlTvFile = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { importXmlTv { xmlTvRepository.importDocument(it) } }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChannelEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        crashReportStore = CrashReportStore(this)
        crashReportStore.recordDebug("EDITOR_CREATE | savedState=${savedInstanceState != null}")
        if (savedInstanceState != null) {
            crashReportStore.recordRecreation(
                "Activity restored after a child/system screen",
            )
            restoredFocusedKey = savedInstanceState.getString(STATE_FOCUSED_SOURCE_KEY)
            restoredMode = runCatching {
                Mode.valueOf(savedInstanceState.getString(STATE_MODE).orEmpty())
            }.getOrDefault(Mode.NORMAL).takeUnless { it == Mode.MOVE } ?: Mode.NORMAL
            selectedKeys.addAll(
                savedInstanceState.getStringArrayList(STATE_SELECTED_KEYS).orEmpty(),
            )
        }
        repository = ChannelRepository(this)
        backupRepository = AppBackupRepository(this)
        parentalControlStore = ParentalControlStore(this)
        xmlTvRepository = XmlTvRepository(this)
        adapter = ChannelEditorAdapter(
            ::onChannelFocused,
            ::onChannelClicked,
        ) { channel -> parentalControlStore.isLocked(channel.sourceKey) }
        iptvPreview = IptvPlaybackController(this, binding.previewIptv)
        binding.channelList.layoutManager = LinearLayoutManager(this)
        binding.channelList.adapter = adapter
        loadChannels(
            syncMessage = false,
            preferredKey = restoredFocusedKey
                ?: intent.getStringExtra(EXTRA_CURRENT_SOURCE_KEY),
        )
    }

    private fun loadChannels(syncMessage: Boolean, preferredKey: String? = focusedChannel?.sourceKey) {
        channelLoadJob?.cancel()
        channelLoadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.channels(
                    includeHidden = true,
                    refreshSources = syncMessage,
                )
            }
            result.onSuccess { loaded ->
                crashReportStore.recordDebug(
                    "EDITOR_CHANNEL_LOAD_SUCCESS | count=${loaded.size}, sync=$syncMessage",
                )
                val previousCount = channels.size
                channels.clear()
                channels.addAll(loaded)
                adapter.submitList(loaded)
                binding.channelCount.text = resources.getQuantityString(
                    R.plurals.channel_count,
                    loaded.size,
                    loaded.size,
                )
                if (syncMessage) {
                    val added = (loaded.size - previousCount).coerceAtLeast(0)
                    binding.syncStatus.text = getString(R.string.sync_complete, added)
                }
                if (!restoredStateApplied) {
                    restoredStateApplied = true
                    mode = restoredMode
                    selectedKeys.retainAll(loaded.mapTo(hashSetOf()) { it.sourceKey })
                    adapter.setSelection(selectedKeys)
                    updateModeLabels()
                }
                focusChannel(preferredKey ?: loaded.firstOrNull()?.sourceKey)
            }.onFailure { error ->
                crashReportStore.recordDebug(
                    "EDITOR_CHANNEL_LOAD_FAILURE | ${error.javaClass.name}: ${error.message}",
                )
                binding.syncStatus.text = error.message ?: error.javaClass.simpleName
            }
        }
    }

    private fun focusChannel(sourceKey: String?) {
        val index = channels.indexOfFirst { it.sourceKey == sourceKey }.takeIf { it >= 0 } ?: 0
        if (channels.isEmpty()) return
        binding.channelList.scrollToPosition(index)
        binding.channelList.post {
            binding.channelList.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
        }
    }

    private fun pageChannels(direction: Int) {
        if (channels.isEmpty() || mode == Mode.MOVE) return
        val layoutManager = binding.channelList.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val last = layoutManager.findLastVisibleItemPosition().coerceAtLeast(first)
        val pageSize = (last - first).coerceAtLeast(1)
        val currentIndex = channels.indexOfFirst {
            it.sourceKey == focusedChannel?.sourceKey
        }.takeIf { it >= 0 } ?: first
        val targetIndex = (currentIndex + direction * pageSize).coerceIn(0, channels.lastIndex)
        focusChannel(channels[targetIndex].sourceKey)
    }

    private fun onChannelFocused(channel: LiveChannel) {
        focusedChannel = channel
        binding.previewNumber.text = channel.displayNumber
        binding.previewName.text = channel.displayName
        binding.previewState.text = when {
            parentalControlStore.isLocked(channel.sourceKey) -> getString(R.string.locked_channel)
            channel.hidden -> getString(R.string.channel_skipped)
            channel.favorite -> getString(R.string.channel_favorite)
            else -> getString(R.string.channel_active)
        }
        previewJob?.cancel()
        if (parentalControlStore.isLocked(channel.sourceKey)) {
            previewSourceKey = null
            binding.previewTv.reset()
            binding.previewTv.visibility = View.GONE
            iptvPreview.stop()
            binding.previewIptv.visibility = View.GONE
            return
        }
        if (mode == Mode.MOVE || previewSourceKey == channel.sourceKey) return
        previewSourceKey = channel.sourceKey
        previewJob = lifecycleScope.launch {
            delay(PREVIEW_DELAY_MS)
            runCatching {
                when (channel.source) {
                    LiveChannel.Source.TIF -> {
                        iptvPreview.stop()
                        binding.previewIptv.visibility = View.GONE
                        binding.previewTv.visibility = View.VISIBLE
                        binding.previewTv.tune(channel.inputId, android.net.Uri.parse(channel.uri))
                    }
                    LiveChannel.Source.IPTV -> {
                        binding.previewTv.reset()
                        binding.previewTv.visibility = View.GONE
                        binding.previewIptv.visibility = View.VISIBLE
                        iptvPreview.play(channel)
                    }
                }
            }.onFailure { binding.syncStatus.text = it.message ?: it.javaClass.simpleName }
        }
    }

    private fun onChannelClicked(channel: LiveChannel) {
        when (mode) {
            Mode.NORMAL -> showChannelProperties(channel)
            Mode.MULTI_SELECT -> toggleSelection(channel.sourceKey)
            Mode.MOVE -> commitMove()
        }
    }

    private fun toggleSelection(sourceKey: String) {
        if (!selectedKeys.add(sourceKey)) selectedKeys.remove(sourceKey)
        adapter.setSelection(selectedKeys)
        updateModeLabels()
    }

    private fun toggleSkipped() {
        val channel = focusedChannel ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                repository.setHidden(channel.sourceKey, !channel.hidden)
            }
            loadChannels(syncMessage = false, preferredKey = channel.sourceKey)
        }
    }

    private fun startSingleMove() {
        val channel = focusedChannel ?: return
        movingKeys.clear()
        movingKeys.add(channel.sourceKey)
        enterMoveMode()
    }

    private fun startMultiMove() {
        if (selectedKeys.isEmpty()) return
        movingKeys.clear()
        movingKeys.addAll(selectedKeys)
        enterMoveMode()
    }

    private fun enterMoveMode() {
        previewJob?.cancel()
        clearMoveTargetInput()
        originalOrder = channels.toList()
        mode = Mode.MOVE
        adapter.setSelection(selectedKeys, movingKeys)
        updateModeLabels()
    }

    private fun moveBlock(offset: Int) {
        if (movingKeys.isEmpty()) return
        val moving = channels.filter { it.sourceKey in movingKeys }
        val firstIndex = channels.indexOfFirst { it.sourceKey in movingKeys }
        val remaining = channels.filterNot { it.sourceKey in movingKeys }
        val insertionIndex = (firstIndex + offset).coerceIn(0, remaining.size)
        val reordered = remaining.toMutableList().apply { addAll(insertionIndex, moving) }
        if (reordered.map { it.sourceKey } == channels.map { it.sourceKey }) return
        channels.clear()
        channels.addAll(reordered)
        adapter.submitList(reordered)
        adapter.setSelection(selectedKeys, movingKeys)
        focusChannel(moving.first().sourceKey)
    }

    private fun commitMove() {
        if (moveTargetInput.isNotEmpty()) {
            commitMoveTargetInput()
            return
        }
        val order = channels.map { it.sourceKey }
        persistOrder(focusedChannel?.sourceKey) { repository.replaceOrder(order) }
    }

    private fun appendMoveTargetDigit(digit: Int) {
        if (moveTargetInput.length >= MAX_CHANNEL_NUMBER_DIGITS) moveTargetInput = ""
        moveTargetInput += digit
        binding.syncStatus.text = getString(R.string.move_target_preview, moveTargetInput)
        moveTargetJob?.cancel()
        moveTargetJob = lifecycleScope.launch {
            delay(MOVE_TARGET_DELAY_MS)
            commitMoveTargetInput()
        }
    }

    private fun appendNavigationDigit(digit: Int) {
        if (navigationNumberInput.length >= MAX_CHANNEL_NUMBER_DIGITS) {
            navigationNumberInput = ""
        }
        navigationNumberInput += digit
        binding.syncStatus.text = getString(
            R.string.editor_channel_number_preview,
            navigationNumberInput,
        )
        navigationNumberJob?.cancel()
        navigationNumberJob = lifecycleScope.launch {
            delay(NAVIGATION_NUMBER_DELAY_MS)
            focusEnteredChannelNumber()
        }
    }

    private fun focusEnteredChannelNumber() {
        val entered = navigationNumberInput
        navigationNumberInput = ""
        navigationNumberJob?.cancel()
        navigationNumberJob = null
        val target = channels.firstOrNull { it.displayNumber == entered }
            ?: entered.toIntOrNull()
                ?.takeIf { it in 1..channels.size }
                ?.let { channels[it - 1] }
        if (target == null) {
            binding.syncStatus.setText(R.string.channel_number_not_found)
            return
        }
        focusChannel(target.sourceKey)
        updateModeLabels()
    }

    private fun clearNavigationNumberInput() {
        navigationNumberJob?.cancel()
        navigationNumberJob = null
        navigationNumberInput = ""
    }

    private fun commitMoveTargetInput() {
        val number = moveTargetInput.toIntOrNull()
        clearMoveTargetInput()
        if (number == null || number !in 1..channels.size) {
            showError(R.string.invalid_channel_number)
            return
        }
        moveCurrentSelectionToNumber(number)
    }

    private fun moveCurrentSelectionToNumber(number: Int) {
        if (movingKeys.isEmpty()) return
        val keysToMove = movingKeys.toSet()
        val preferredKey = channels.firstOrNull { it.sourceKey in keysToMove }?.sourceKey
        val activeOrder = channels.map { it.sourceKey }
        persistOrder(preferredKey) {
            repository.moveChannelsToNumber(keysToMove, number, activeOrder)
        }
    }

    private fun showMoveTargetEditor() {
        if (movingKeys.isEmpty()) return
        val currentPosition = channels.indexOfFirst { it.sourceKey in movingKeys }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: 1
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentPosition.toString())
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.move_target_order)
            .setMessage(getString(R.string.selected_channel_count, movingKeys.size))
            .setView(input)
            .setPositiveButton(R.string.move) { _, _ ->
                val number = input.text.toString().toIntOrNull()
                if (number == null || number !in 1..channels.size) {
                    showError(R.string.invalid_channel_number)
                } else {
                    moveCurrentSelectionToNumber(number)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun clearMoveTargetInput() {
        moveTargetJob?.cancel()
        moveTargetJob = null
        moveTargetInput = ""
    }

    private fun cancelMode() {
        clearMoveTargetInput()
        if (mode == Mode.MOVE && originalOrder.isNotEmpty()) {
            channels.clear()
            channels.addAll(originalOrder)
            adapter.submitList(originalOrder)
        }
        selectedKeys.clear()
        movingKeys.clear()
        mode = Mode.NORMAL
        adapter.setSelection(emptySet())
        updateModeLabels()
        focusChannel(focusedChannel?.sourceKey)
    }

    private fun updateModeLabels() {
        binding.modeTitle.text = when (mode) {
            Mode.NORMAL -> getString(R.string.editor_mode_normal)
            Mode.MULTI_SELECT -> getString(R.string.editor_mode_multi, selectedKeys.size)
            Mode.MOVE -> getString(R.string.editor_mode_move, movingKeys.size)
        }
        binding.greenAction.text = when (mode) {
            Mode.NORMAL -> getString(R.string.move_channel)
            Mode.MULTI_SELECT -> getString(R.string.move_selected)
            Mode.MOVE -> getString(R.string.save_order)
        }
        binding.redAction.text = when (mode) {
            Mode.NORMAL, Mode.MULTI_SELECT -> getString(R.string.skip_channel)
            Mode.MOVE -> getString(R.string.move_target_order)
        }
        binding.yellowAction.text = when (mode) {
            Mode.NORMAL -> getString(R.string.multi_select)
            Mode.MULTI_SELECT -> getString(R.string.move_selected_to_number)
            Mode.MOVE -> getString(R.string.cancel)
        }
    }

    private fun showChannelProperties(channel: LiveChannel) {
        val actions = arrayOf(
            getString(if (channel.favorite) R.string.remove_favorite else R.string.add_favorite),
            getString(R.string.change_channel_number),
            getString(R.string.change_channel_name),
            getString(
                if (parentalControlStore.isLocked(channel.sourceKey)) {
                    R.string.unlock_channel
                } else {
                    R.string.lock_channel
                },
            ),
        )
        AlertDialog.Builder(this)
            .setTitle(channel.displayName)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> updateChannel(channel.sourceKey) {
                        repository.setFavorite(channel.sourceKey, !channel.favorite)
                    }
                    1 -> showNumberEditor(channel)
                    2 -> showNameEditor(channel)
                    3 -> toggleChannelLock(channel)
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun toggleChannelLock(channel: LiveChannel) {
        if (parentalControlStore.isLocked(channel.sourceKey)) {
            ParentalPinDialog.verify(this, getString(R.string.unlock_channel)) {
                setChannelLocked(channel, false)
            }
            return
        }
        if (parentalControlStore.hasPin()) {
            setChannelLocked(channel, true)
        } else {
            ParentalPinDialog.create(this) { setChannelLocked(channel, true) }
        }
    }

    private fun setChannelLocked(channel: LiveChannel, locked: Boolean) {
        parentalControlStore.setLocked(channel.sourceKey, locked)
        if (locked && focusedChannel?.sourceKey == channel.sourceKey) {
            previewJob?.cancel()
            previewSourceKey = null
            binding.previewTv.reset()
            binding.previewTv.visibility = View.GONE
            iptvPreview.stop()
            binding.previewIptv.visibility = View.GONE
        }
        binding.syncStatus.text = getString(
            if (locked) {
                R.string.lock_channel
            } else {
                R.string.unlock_channel
            },
        )
        adapter.notifyDataSetChanged()
        setResult(RESULT_OK)
    }

    private fun showNumberEditor(channel: LiveChannel) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(channel.displayNumber)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.move_to_channel_number)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val number = input.text.toString().toIntOrNull()
                when {
                    number == null || number !in 1..channels.size -> {
                        showError(R.string.invalid_channel_number)
                    }
                    else -> updateChannel(channel.sourceKey) {
                        repository.moveChannelsToNumber(
                            setOf(channel.sourceKey),
                            number,
                            channels.map { it.sourceKey },
                        )
                    }
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showMultiNumberEditor() {
        if (selectedKeys.isEmpty()) {
            cancelMode()
            return
        }
        val firstSelectedKey = channels.firstOrNull { it.sourceKey in selectedKeys }?.sourceKey
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.target_start_number)
            .setMessage(getString(R.string.selected_channel_count, selectedKeys.size))
            .setView(input)
            .setPositiveButton(R.string.move) { _, _ ->
                val number = input.text.toString().toIntOrNull()
                if (number == null || number !in 1..channels.size) {
                    showError(R.string.invalid_channel_number)
                } else {
                    val keysToMove = selectedKeys.toSet()
                    val activeOrder = channels.map { it.sourceKey }
                    persistOrder(firstSelectedKey) {
                        repository.moveChannelsToNumber(keysToMove, number, activeOrder)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showNameEditor(channel: LiveChannel) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(channel.displayName)
            selectAll()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.change_channel_name)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isBlank()) showError(R.string.invalid_channel_name)
                else updateChannel(channel.sourceKey) {
                    repository.setCustomName(channel.sourceKey, name)
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun updateChannel(sourceKey: String, action: suspend () -> Unit) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { action() } }
            result.onSuccess {
                loadChannels(syncMessage = false, preferredKey = sourceKey)
            }.onFailure { error ->
                binding.syncStatus.text = error.message ?: error.javaClass.simpleName
                AlertDialog.Builder(this@ChannelEditorActivity)
                    .setMessage(error.message ?: error.javaClass.simpleName)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun persistOrder(preferredKey: String?, action: suspend () -> Unit) {
        if (operationInProgress) return
        crashReportStore.recordDebug(
            "ORDER_PERSIST_START | mode=$mode, moving=${movingKeys.size}, preferred=$preferredKey",
        )
        operationInProgress = true
        clearMoveTargetInput()
        previewJob?.cancel()
        binding.channelList.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { action() } }
            operationInProgress = false
            binding.channelList.isEnabled = true
            result.onSuccess {
                crashReportStore.recordDebug("ORDER_PERSIST_SUCCESS")
                selectedKeys.clear()
                movingKeys.clear()
                mode = Mode.NORMAL
                adapter.setSelection(emptySet())
                updateModeLabels()
                binding.syncStatus.setText(R.string.order_saved)
                loadChannels(syncMessage = false, preferredKey = preferredKey)
            }.onFailure { error ->
                crashReportStore.recordDebug(
                    "ORDER_PERSIST_FAILURE | ${error.javaClass.name}: ${error.message}",
                )
                binding.syncStatus.text = error.message ?: error.javaClass.simpleName
                AlertDialog.Builder(this@ChannelEditorActivity)
                    .setTitle(R.string.channel_move_failed)
                    .setMessage(error.message ?: error.javaClass.simpleName)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun showError(message: Int) {
        AlertDialog.Builder(this).setMessage(message)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private fun showSourceManagement() {
        val actions = arrayOf(
            getString(R.string.sync_tif),
            getString(R.string.channel_sources),
            getString(R.string.iptv_sources_title),
            getString(R.string.xmltv_alternative_epg),
            getString(R.string.export_backup),
            getString(R.string.import_backup),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.source_management)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> loadChannels(syncMessage = true)
                    1 -> showChannelSources()
                    2 -> openIptvSources()
                    3 -> showXmlTvManagement()
                    4 -> createBackupFile.launch(defaultBackupFileName())
                    5 -> openBackupFile.launch(arrayOf("application/json", "text/plain"))
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun openIptvSources() {
        if (IptvEntitlementManager(this).snapshot().accessGranted) {
            editIptvSources.launch(Intent(this, IptvSourcesActivity::class.java))
        } else {
            IptvAccessDialogs.requireAccess(this, ::openIptvSources)
        }
    }

    private fun showXmlTvManagement() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.xmltv_url_hint)
            setSingleLine()
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_focusable)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        fun action(text: Int) = android.widget.TextView(this).apply {
            setText(text)
            setTextColor(getColor(R.color.text_primary))
            textSize = 16f
            gravity = android.view.Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setBackgroundResource(R.drawable.bg_focusable)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val fileAction = action(R.string.xmltv_from_file)
        val savedAction = action(R.string.xmltv_saved_sources)
        val clearAction = action(R.string.xmltv_clear)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(android.widget.TextView(this@ChannelEditorActivity).apply {
                text = xmlTvRepository.sourceLabel() ?: getString(R.string.xmltv_not_configured)
                setTextColor(getColor(R.color.text_secondary))
                setPadding(0, 0, 0, dp(12))
            })
            addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            listOf(fileAction, savedAction, clearAction).forEach { view ->
                addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
                    topMargin = dp(8)
                })
            }
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.xmltv_alternative_epg)
            .setView(container)
            .setPositiveButton(R.string.xmltv_add_url, null)
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = input.text.toString().trim()
                val scheme = runCatching { android.net.Uri.parse(url).scheme?.lowercase() }.getOrNull()
                if (scheme == "http" || scheme == "https") {
                    dialog.dismiss()
                    importXmlTv { xmlTvRepository.importUrl(url) }
                } else {
                    input.error = getString(R.string.xmltv_url_invalid)
                    input.requestFocus()
                }
            }
            fileAction.setOnClickListener {
                dialog.dismiss()
                openXmlTvFile.launch(arrayOf("application/xml", "text/xml", "*/*"))
            }
            savedAction.setOnClickListener {
                dialog.dismiss()
                showXmlTvSavedSources()
            }
            clearAction.setOnClickListener {
                dialog.dismiss()
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { xmlTvRepository.clear() }
                    binding.syncStatus.setText(R.string.xmltv_cleared)
                }
            }
            input.requestFocus()
        }
        dialog.show()
    }

    private fun showXmlTvUrlEditor() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.xmltv_url_hint)
            setSingleLine()
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            setBackgroundResource(R.drawable.bg_focusable)
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val container = LinearLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(input, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.xmltv_from_url)
            .setView(container)
            .setPositiveButton(R.string.update, null)
            .setNegativeButton(R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = input.text.toString().trim()
                val scheme = runCatching { android.net.Uri.parse(url).scheme?.lowercase() }.getOrNull()
                if (scheme == "http" || scheme == "https") {
                    dialog.dismiss()
                    importXmlTv { xmlTvRepository.importUrl(url) }
                } else {
                    input.error = getString(R.string.xmltv_url_invalid)
                    input.requestFocus()
                }
            }
            input.requestFocus()
        }
        dialog.show()
    }

    private fun showXmlTvSavedSources() {
        lifecycleScope.launch {
            val sources = withContext(Dispatchers.IO) { xmlTvRepository.sources() }
            if (sources.isEmpty()) {
                binding.syncStatus.setText(R.string.xmltv_not_configured)
                return@launch
            }
            val labels = sources.map { source ->
                getString(
                    R.string.xmltv_source_row,
                    source.name,
                    if (source.kind == XmlTvRepository.KIND_URL) getString(R.string.xmltv_source_url)
                    else getString(R.string.xmltv_source_file),
                )
            }.toTypedArray()
            AlertDialog.Builder(this@ChannelEditorActivity, R.style.Theme_TVApp_Dialog)
                .setTitle(R.string.xmltv_saved_sources)
                .setItems(labels) { _, index -> showXmlTvSourceActions(sources[index]) }
                .setNegativeButton(R.string.close, null)
                .show()
        }
    }

    private fun showXmlTvSourceActions(source: com.tvapp.livetv.data.local.XmlTvSourceEntity) {
        val actions = if (source.kind == XmlTvRepository.KIND_URL) {
            arrayOf(getString(R.string.update), getString(R.string.delete))
        } else arrayOf(getString(R.string.delete))
        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(source.name)
            .setMessage(source.location)
            .setItems(actions) { _, index ->
                if (source.kind == XmlTvRepository.KIND_URL && index == 0) {
                    importXmlTv { xmlTvRepository.refreshSource(source) }
                } else {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { xmlTvRepository.deleteSource(source.id) }
                        binding.syncStatus.setText(R.string.xmltv_source_deleted)
                    }
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun importXmlTv(action: () -> Int) {
        operationInProgress = true
        binding.syncStatus.setText(R.string.xmltv_importing)
        lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { action() } }
                .onSuccess { count -> binding.syncStatus.text = getString(R.string.xmltv_import_complete, count) }
                .onFailure(::showBackupError)
            operationInProgress = false
        }
    }

    private fun defaultBackupFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        return "TVApp-backup-$timestamp.json"
    }

    private fun exportBackup(uri: android.net.Uri) {
        operationInProgress = true
        binding.syncStatus.text = getString(R.string.backup_exporting)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { backupRepository.exportTo(uri) }
            }.onSuccess { summary ->
                binding.syncStatus.text = getString(
                    R.string.backup_export_complete,
                    summary.channelCount,
                    summary.iptvSourceCount,
                )
            }.onFailure { error ->
                binding.syncStatus.text = error.message ?: error.javaClass.simpleName
                showBackupError(error)
            }
            operationInProgress = false
        }
    }

    private fun confirmImportBackup(uri: android.net.Uri) {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_backup)
            .setMessage(R.string.import_backup_confirm)
            .setPositiveButton(R.string.import_action) { _, _ -> importBackup(uri) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun importBackup(uri: android.net.Uri) {
        operationInProgress = true
        binding.syncStatus.text = getString(R.string.backup_importing)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { backupRepository.importFrom(uri) }
            }.onSuccess { summary ->
                binding.syncStatus.text = getString(
                    R.string.backup_import_complete,
                    summary.channelCount,
                    summary.iptvSourceCount,
                )
                setResult(RESULT_OK)
                loadChannels(syncMessage = false)
            }.onFailure { error ->
                binding.syncStatus.text = error.message ?: error.javaClass.simpleName
                showBackupError(error)
            }
            operationInProgress = false
        }
    }

    private fun showBackupError(error: Throwable) {
        crashReportStore.recordDebug(
            "BACKUP_FAILURE | ${error.javaClass.name}: ${error.message}",
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.backup_failed)
            .setMessage(error.message ?: error.javaClass.simpleName)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showChannelSources() {
        val inputs = TifRepository(this).tunerInputs()
        if (inputs.isEmpty()) {
            showError(R.string.no_tuner_sources)
            return
        }
        val store = ChannelSourceFilterStore(this)
        val availableIds = inputs.mapTo(linkedSetOf()) { it.id }
        val enabledIds = store.enabledInputIds(availableIds).toMutableSet()
        val labels = inputs.map { input ->
            val label = input.loadLabel(this).toString().ifBlank { input.id }
            "$label\n${input.id.substringAfterLast('/')}"
        }.toTypedArray()
        val checked = inputs.map { it.id in enabledIds }.toBooleanArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.channel_sources)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                if (isChecked) enabledIds.add(inputs[which].id)
                else enabledIds.remove(inputs[which].id)
            }
            .setPositiveButton(R.string.save) { _, _ ->
                store.save(enabledIds)
                setResult(RESULT_OK)
                loadChannels(syncMessage = true)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (operationInProgress) return true
        val editorColor = editorColorFor(event.keyCode)
        val handlesMoveDirection = mode == Mode.MOVE &&
            event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)
        val handlesMoveConfirm = mode == Mode.MOVE && event.keyCode in setOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
        )
        val handlesPage = event.keyCode in setOf(
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
        )
        val handlesBack = event.keyCode == KeyEvent.KEYCODE_BACK
        val digit = digitForKeyCode(event.keyCode)

        // Vendor TV firmware may attach system actions to the key-up phase of color keys.
        // Consume the complete press once the editor owns that remote key.
        if (event.action != KeyEvent.ACTION_DOWN) {
            return if (
                editorColor != null || handlesMoveDirection || handlesPage || handlesBack ||
                handlesMoveConfirm || digit != null
            ) {
                true
            } else {
                super.dispatchKeyEvent(event)
            }
        }
        if (event.repeatCount > 0 && !handlesMoveDirection) {
            return if (editorColor != null || handlesBack || handlesMoveConfirm || digit != null) {
                true
            } else {
                super.dispatchKeyEvent(event)
            }
        }

        crashReportStore.recordEditorEvent(
            "keyCode=${event.keyCode}, keyName=${KeyEvent.keyCodeToString(event.keyCode)}, " +
                "scanCode=${event.scanCode}, deviceId=${event.deviceId}, source=${event.source}, " +
                "mode=$mode, focused=${focusedChannel?.sourceKey}, selected=${selectedKeys.size}",
        )

        if (editorColor != null || handlesBack) clearNavigationNumberInput()

        when (editorColor) {
            EditorColor.RED -> if (mode == Mode.MOVE) showMoveTargetEditor() else toggleSkipped()
            EditorColor.GREEN -> when (mode) {
                Mode.NORMAL -> startSingleMove()
                Mode.MULTI_SELECT -> startMultiMove()
                Mode.MOVE -> commitMove()
            }
            EditorColor.YELLOW -> when (mode) {
                Mode.NORMAL -> {
                    mode = Mode.MULTI_SELECT
                    updateModeLabels()
                }
                Mode.MULTI_SELECT -> showMultiNumberEditor()
                Mode.MOVE -> cancelMode()
            }
            EditorColor.BLUE -> if (mode == Mode.NORMAL) showSourceManagement()
            null -> if (digit != null) {
                if (mode == Mode.MOVE) appendMoveTargetDigit(digit)
                else appendNavigationDigit(digit)
            } else when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> if (mode == Mode.MOVE) moveBlock(-1)
                    else if (!wrapEditorList(-1)) return super.dispatchKeyEvent(event)
                KeyEvent.KEYCODE_DPAD_DOWN -> if (mode == Mode.MOVE) moveBlock(1)
                    else if (!wrapEditorList(1)) return super.dispatchKeyEvent(event)
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> if (mode == Mode.MOVE) {
                    commitMove()
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> pageChannels(-1)
                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> pageChannels(1)
                KeyEvent.KEYCODE_BACK -> if (mode == Mode.NORMAL) {
                    crashReportStore.recordEditorExit(
                        "BACK received in NORMAL mode; keyCode=${event.keyCode}, " +
                            "scanCode=${event.scanCode}, deviceId=${event.deviceId}, " +
                            "source=${event.source}, repeat=${event.repeatCount}",
                    )
                    finishWithFocusedChannel()
                } else {
                    cancelMode()
                }
                else -> return super.dispatchKeyEvent(event)
            }
        }
        return true
    }

    private fun wrapEditorList(direction: Int): Boolean {
        if (!binding.channelList.hasFocus() || channels.isEmpty()) return false
        val currentIndex = channels.indexOfFirst { it.sourceKey == focusedChannel?.sourceKey }
        val targetIndex = when {
            direction < 0 && currentIndex == 0 -> channels.lastIndex
            direction > 0 && currentIndex == channels.lastIndex -> 0
            else -> return false
        }
        focusChannel(channels[targetIndex].sourceKey)
        return true
    }

    private fun finishWithFocusedChannel() {
        setResult(
            RESULT_OK,
            Intent().putExtra(EXTRA_SELECTED_SOURCE_KEY, focusedChannel?.sourceKey),
        )
        finish()
    }

    private fun editorColorFor(keyCode: Int): EditorColor? = when (keyCode) {
        KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_F1 -> EditorColor.RED
        KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_F2 -> EditorColor.GREEN
        KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_F3 -> EditorColor.YELLOW
        KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_F4 -> EditorColor.BLUE
        else -> null
    }

    private fun digitForKeyCode(keyCode: Int): Int? = when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> keyCode - KeyEvent.KEYCODE_0
        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
            keyCode - KeyEvent.KEYCODE_NUMPAD_0
        else -> null
    }

    override fun onDestroy() {
        if (::crashReportStore.isInitialized) {
            crashReportStore.recordDebug(
                "EDITOR_DESTROY | finishing=$isFinishing, " +
                    "config=$isChangingConfigurations, mode=$mode",
            )
        }
        if (isChangingConfigurations && ::crashReportStore.isInitialized) {
            crashReportStore.recordRecreation(
                "onDestroy reported isChangingConfigurations=true; mode=$mode, " +
                    "focused=${focusedChannel?.sourceKey}",
            )
        }
        previewJob?.cancel()
        channelLoadJob?.cancel()
        moveTargetJob?.cancel()
        navigationNumberJob?.cancel()
        binding.previewTv.reset()
        iptvPreview.release()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_FOCUSED_SOURCE_KEY, focusedChannel?.sourceKey)
        outState.putString(STATE_MODE, mode.name)
        outState.putStringArrayList(STATE_SELECTED_KEYS, ArrayList(selectedKeys))
        super.onSaveInstanceState(outState)
    }

    companion object {
        const val EXTRA_CURRENT_SOURCE_KEY = "com.tvapp.livetv.extra.CURRENT_SOURCE_KEY"
        const val EXTRA_SELECTED_SOURCE_KEY = "com.tvapp.livetv.extra.SELECTED_SOURCE_KEY"
        private const val PREVIEW_DELAY_MS = 450L
        private const val MOVE_TARGET_DELAY_MS = 1_200L
        private const val NAVIGATION_NUMBER_DELAY_MS = 900L
        private const val MAX_CHANNEL_NUMBER_DIGITS = 4
        private const val STATE_FOCUSED_SOURCE_KEY = "editor-focused-source-key"
        private const val STATE_MODE = "editor-mode"
        private const val STATE_SELECTED_KEYS = "editor-selected-keys"
    }

    private enum class EditorColor { RED, GREEN, YELLOW, BLUE }
}
