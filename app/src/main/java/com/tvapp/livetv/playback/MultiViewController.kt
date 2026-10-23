package com.tvapp.livetv.playback

import android.content.DialogInterface
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.MainActivity
import com.tvapp.livetv.R
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.platform.DeviceResourcePolicy
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.databinding.ActivityMainBinding
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.ui.MultiViewChannelAdapter
import com.tvapp.livetv.ui.MultiViewFocusResolver
import com.tvapp.livetv.ui.OsdCoordinator
import com.tvapp.livetv.ui.PlaybackSurfaceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MultiViewController(
    private val activity: MainActivity,
    private val binding: ActivityMainBinding,
    private val playback: TifPlaybackController,
    private val iptvPlayback: IptvPlaybackController,
    private val secondaryIptvPlayback: IptvPlaybackController,
    private val iptvRepository: IptvRepository,
    private val deviceResourcePolicy: DeviceResourcePolicy,
    private val osdCoordinator: OsdCoordinator,
    private val debugLog: CrashReportStore,
    private val scope: LifecycleCoroutineScope,
    private val getCurrentChannel: () -> LiveChannel?,
    private val getAllChannels: () -> List<LiveChannel>,
    private val getIptvLibraryChannels: () -> List<LiveChannel>,
    private val isChannelPanelContentIptvLibrary: () -> Boolean,
    private val isVodMode: () -> Boolean,
    private val openVodLibrary: () -> Unit,
    private val hasIptvAccess: ((() -> Unit) -> Boolean),
    private val playSelectedChannel: (LiveChannel, Boolean) -> Unit,
    private val endBlackout: (String) -> Unit,
    private val showChannelPanel: (Boolean) -> Unit,
    private val hideChannelPanel: () -> Unit,
    private val setInfoBarVisible: (Boolean) -> Unit,
    private val updateChannelActionLabels: () -> Unit,
    private val cancelFocusedTuneJob: () -> Unit,
    private val cancelChannelPanelJob: () -> Unit,
) {
    val multiViewDraft = linkedMapOf<String, LiveChannel>()
    var multiViewAdding = false
    var multiViewDraftInitialized = false
    var restoredMultiViewKeys: List<String>? = null

    var iptvOverlayActive = false
        private set
    var iptvOverlayChannel: LiveChannel? = null
        private set

    var iptvGridActive = false
        private set
    var gridActiveIndex = 0
        private set
    var gridFullscreenIndex: Int? = null
        private set
    var gridChannels: List<LiveChannel> = emptyList()
        private set

    private var gridLongPressHandled = false
    private var gridLongPressJob: Job? = null
    private var gridTifViewportGeneration = 0
    private var gridReturnChannel: LiveChannel? = null
    val gridSelectedKeys = mutableListOf<String>()
    private val gridCells = mutableListOf<FrameLayout>()
    private val gridPlayerViews = mutableListOf<PlayerView>()
    private val gridLabels = mutableListOf<TextView>()
    val gridControllers = mutableListOf<IptvPlaybackController>()
    private val renderedGridKeys = mutableListOf<String?>()
    private var renderedGridTifKey: String? = null
    private var gridTifSurfaceLayoutListenerAttached = false

    private val Int.dp: Int
        get() = (this * activity.resources.displayMetrics.density).toInt()

    fun prepareIptvGrid() {
        if (gridCells.isNotEmpty()) return
        repeat(4) { index ->
            val cell = activity.layoutInflater.inflate(
                R.layout.view_iptv_grid_cell,
                binding.iptvGrid,
                false,
            ) as FrameLayout
            val playerView = cell.findViewById<PlayerView>(R.id.grid_player)
            val label = cell.findViewById<TextView>(R.id.grid_label)
            cell.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(index / 2, 1, GridLayout.FILL, 1f),
                GridLayout.spec(index % 2, 1, GridLayout.FILL, 1f),
            ).apply {
                width = 0
                height = 0
                setMargins(3.dp, 3.dp, 3.dp, 3.dp)
            }
            cell.setOnClickListener {
                if (index in gridChannels.indices) {
                    gridActiveIndex = index
                    updateIptvGridFocus()
                    openActiveGridChannel()
                }
            }
            binding.iptvGrid.addView(cell)
            cell.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (iptvGridActive) positionGridTifSurface()
            }
            gridCells += cell
            gridPlayerViews += playerView
            gridLabels += label
            renderedGridKeys += null
            gridControllers += IptvPlaybackController(
                context = activity,
                playerView = playerView,
                profile = gridProfileForCellCount(deviceResourcePolicy.maximumGridStreams),
            ).apply {
                onHealthChanged = {
                    if (iptvGridActive && gridFullscreenIndex != null && gridFullscreenIndex != index) pause()
                }
                onPlaybackError = { error ->
                    debugLog.recordDebug(
                        "IPTV_GRID_FAILURE | cell=$index, ${error.errorCodeName}",
                    )
                }
            }
        }
    }

    fun availableMultiViewChannels(): List<LiveChannel> = buildList {
        addAll(getAllChannels())
        if (isChannelPanelContentIptvLibrary()) addAll(getIptvLibraryChannels())
    }.asSequence()
        .filter { it.source == LiveChannel.Source.TIF || it.source == LiveChannel.Source.IPTV }
        .filter {
            it.source != LiveChannel.Source.IPTV ||
                it.iptvContentType != "VOD"
        }
        .distinctBy { it.sourceKey }
        .toList()

    fun checkSourceConnections(selected: List<LiveChannel>, proceed: () -> Unit) {
        val background = getCurrentChannel()?.sourceKey
        val grid = gridChannels.map { it.sourceKey }
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { iptvRepository.connectionLimitViolation(selected) } }
            if (background != getCurrentChannel()?.sourceKey || grid != gridChannels.map { it.sourceKey }) return@launch
            result.onSuccess { violation ->
                if (violation == null) proceed() else Toast.makeText(
                    activity,
                    activity.getString(R.string.iptv_source_connection_limit_reached, violation.first, violation.second),
                    Toast.LENGTH_LONG,
                ).show()
            }.onFailure { debugLog.recordDebug("IPTV_CONNECTION_LIMIT_FAILURE | ${it.javaClass.simpleName}") }
        }
    }

    fun startIptvOverlay(channel: LiveChannel, connectionsChecked: Boolean = false) {
        if (!hasIptvAccess { startIptvOverlay(channel) }) return
        val background = if (iptvGridActive) {
            gridChannels.firstOrNull { it.sourceKey != channel.sourceKey } ?: gridReturnChannel
        } else getCurrentChannel()
        if (background == null) return
        if (channel.source != LiveChannel.Source.IPTV) {
            Toast.makeText(activity, R.string.iptv_pip_requires_iptv, Toast.LENGTH_LONG).show()
            return
        }
        if (channel.sourceKey == background.sourceKey) {
            Toast.makeText(activity, R.string.iptv_pip_requires_different_channel, Toast.LENGTH_LONG).show()
            return
        }
        if (!connectionsChecked) {
            checkSourceConnections(listOf(background, channel)) { startIptvOverlay(channel, connectionsChecked = true) }
            return
        }
        if (iptvGridActive) {
            stopIptvGrid(resumePrevious = false)
            playSelectedChannel(background, false)
        }
        iptvOverlayActive = true
        iptvOverlayChannel = channel
        multiViewDraftInitialized = true
        multiViewDraft.clear()
        listOf(background, channel).forEach { multiViewDraft[it.sourceKey] = it }
        endBlackout("IPTV_OVERLAY")
        osdCoordinator.setPlaybackMode(PlaybackSurfaceMode.IPTV_OVERLAY)
        val width = (activity.resources.displayMetrics.widthPixels * 0.32f).toInt()
        val height = (width * 9f / 16f).toInt()
        binding.secondaryIptvPlayerView.layoutParams =
            (binding.secondaryIptvPlayerView.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = width
                this.height = height
                gravity = Gravity.END or Gravity.BOTTOM
                setMargins(20.dp, 20.dp, 20.dp, 20.dp)
            }
        binding.secondaryIptvPlayerView.elevation = 16.dp.toFloat()
        binding.secondaryIptvPlayerView.visibility = View.VISIBLE
        secondaryIptvPlayback.setMuted(true)
        secondaryIptvPlayback.play(channel)
        hideChannelPanel()
        debugLog.recordDebug("IPTV_OVERLAY_START | channel=${channel.sourceKey}")
    }

    fun stopIptvOverlay() {
        if (!iptvOverlayActive) return
        iptvOverlayActive = false
        iptvOverlayChannel = null
        osdCoordinator.setPlaybackMode(PlaybackSurfaceMode.SINGLE)
        secondaryIptvPlayback.stop()
        binding.secondaryIptvPlayerView.visibility = View.GONE
        debugLog.recordDebug("IPTV_OVERLAY_STOP")
    }

    fun beginMultiViewChannelSelection() {
        cancelFocusedTuneJob()
        multiViewAdding = true
        showChannelPanel(false)
        updateChannelActionLabels()
        cancelChannelPanelJob()
    }

    fun toggleMultiViewChannel(channel: LiveChannel) {
        multiViewDraftInitialized = true
        val selectingFromList = multiViewAdding
        scope.launch {
            val resolved = if (channel.source == LiveChannel.Source.IPTV && channel.uri.isBlank()) {
                withContext(Dispatchers.IO) { iptvRepository.channel(channel.sourceKey) } ?: return@launch
            } else channel
            if (selectingFromList && !multiViewAdding) return@launch
            when {
                resolved.sourceKey in multiViewDraft -> multiViewDraft.remove(resolved.sourceKey)
                resolved.iptvContentType == "VOD" ->
                    Toast.makeText(activity, R.string.multiview_live_only, Toast.LENGTH_SHORT).show()
                multiViewDraft.size >= deviceResourcePolicy.maximumGridStreams ->
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.iptv_grid_maximum, deviceResourcePolicy.maximumGridStreams),
                        Toast.LENGTH_SHORT,
                    ).show()
                resolved.source == LiveChannel.Source.TIF && multiViewDraft.values.any { it.source == LiveChannel.Source.TIF } ->
                    Toast.makeText(activity, R.string.multiview_requires_single_tuner, Toast.LENGTH_SHORT).show()
                else -> {
                    val violation = withContext(Dispatchers.IO) {
                        iptvRepository.connectionLimitViolation(multiViewDraft.values.toList() + resolved)
                    }
                    if (selectingFromList && !multiViewAdding) return@launch
                    if (violation == null) multiViewDraft[resolved.sourceKey] = resolved
                    else Toast.makeText(
                        activity,
                        activity.getString(R.string.iptv_source_connection_limit_reached, violation.first, violation.second),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
            if (multiViewAdding) updateChannelActionLabels()
        }
    }

    fun showIptvGridPicker(initialSelection: List<LiveChannel>? = null) {
        if (isVodMode()) {
            openVodLibrary()
            return
        }
        val restored = restoredMultiViewKeys
        if (initialSelection == null && restored != null) {
            restoredMultiViewKeys = null
            val known = getAllChannels().associateBy { it.sourceKey }
            scope.launch {
                val selection = withContext(Dispatchers.IO) {
                    restored.take(4).mapNotNull { known[it] ?: iptvRepository.channel(it) }
                }
                if (!activity.isFinishing && !activity.isDestroyed) showIptvGridPicker(selection)
            }
            return
        }
        val initial = initialSelection ?: when {
            multiViewDraftInitialized -> multiViewDraft.values.toList()
            iptvGridActive -> gridChannels
            iptvOverlayActive -> listOfNotNull(getCurrentChannel(), iptvOverlayChannel)
            else -> multiViewDraft.values.toList()
        }
        multiViewDraftInitialized = true
        multiViewDraft.clear()
        initial.forEach { multiViewDraft[it.sourceKey] = it }
        cancelFocusedTuneJob()
        hideChannelPanel()
        val themedContext = ContextThemeWrapper(activity, R.style.Theme_TVApp_Dialog)
        val dialogView = LayoutInflater.from(themedContext).inflate(R.layout.dialog_multiview_picker, null)
        val selectionLabel: TextView = dialogView.findViewById(R.id.picker_selection_label)
        val recyclerView: RecyclerView = dialogView.findViewById(R.id.picker_recycler)
        recyclerView.layoutManager = LinearLayoutManager(themedContext)
        recyclerView.layoutParams.height = (multiViewDraft.size.coerceIn(1, 4) * 52).dp
        recyclerView.visibility = if (multiViewDraft.isEmpty()) View.GONE else View.VISIBLE
        lateinit var dialog: AlertDialog
        val adapter = MultiViewChannelAdapter { channel, _ ->
            multiViewDraft.remove(channel.sourceKey)
            dialog.dismiss()
            showIptvGridPicker(multiViewDraft.values.toList())
        }
        recyclerView.adapter = adapter
        adapter.submitList(multiViewDraft.values.toList(), multiViewDraft)
        selectionLabel.text = if (multiViewDraft.isEmpty()) activity.getString(R.string.multiview_picker_none_selected)
        else activity.getString(R.string.multiview_picker_filter, activity.getString(R.string.multiview_selected_filter), multiViewDraft.size)

        fun startSelected() {
            val selected = multiViewDraft.values.toList()
            if (selected.isEmpty()) return
            if (selected.size == 1 && selected.single().source == LiveChannel.Source.IPTV &&
                selected.single().sourceKey != getCurrentChannel()?.sourceKey && getCurrentChannel() != null
            ) {
                AlertDialog.Builder(themedContext, R.style.Theme_TVApp_Dialog)
                    .setTitle(R.string.multiview_single_mode)
                    .setItems(arrayOf(activity.getString(R.string.pip_start_action), activity.getString(R.string.iptv_grid_start))) { _, mode ->
                        dialog.dismiss()
                        if (mode == 0) startIptvOverlay(selected.single()) else startIptvGrid(selected)
                    }.setNegativeButton(R.string.cancel, null).show()
            } else {
                dialog.dismiss()
                startIptvGrid(selected)
            }
        }

        fun addChannels() {
            dialog.dismiss()
            beginMultiViewChannelSelection()
        }

        dialog = AlertDialog.Builder(themedContext, R.style.Theme_TVApp_Dialog)
            .setView(dialogView)
            .setPositiveButton(R.string.iptv_grid_start, null)
            .setNeutralButton(R.string.multiview_add_channels, null)
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).apply {
                isEnabled = multiViewDraft.isNotEmpty()
                setOnClickListener { startSelected() }
            }
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener { addChannels() }
            if (multiViewDraft.isEmpty()) dialog.getButton(DialogInterface.BUTTON_NEUTRAL).requestFocus()
            else recyclerView.requestFocus()
        }
        dialog.setOnKeyListener { _, code, event ->
            if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return@setOnKeyListener false
            when (code) {
                KeyEvent.KEYCODE_PROG_GREEN -> { startSelected(); true }
                KeyEvent.KEYCODE_PROG_YELLOW -> { addChannels(); true }
                KeyEvent.KEYCODE_PROG_RED -> {
                    multiViewDraft.clear()
                    dialog.dismiss()
                    showIptvGridPicker(emptyList())
                    true
                }
                else -> false
            }
        }
        dialog.show()
    }

    fun startIptvGrid(selected: List<LiveChannel>, connectionsChecked: Boolean = false) {
        val distinct = selected.distinctBy { it.sourceKey }
        if (distinct.isEmpty()) return
        if (distinct.size > deviceResourcePolicy.maximumGridStreams) {
            Toast.makeText(
                activity,
                activity.getString(R.string.iptv_grid_maximum, deviceResourcePolicy.maximumGridStreams),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        if (distinct.count { it.source == LiveChannel.Source.TIF } > 1) {
            Toast.makeText(activity, R.string.multiview_requires_single_tuner, Toast.LENGTH_LONG).show()
            return
        }
        if (distinct.any { it.source == LiveChannel.Source.IPTV } &&
            !hasIptvAccess { startIptvGrid(distinct) }
        ) return
        if (!connectionsChecked) {
            checkSourceConnections(distinct) { startIptvGrid(distinct, connectionsChecked = true) }
            return
        }
        prepareIptvGrid()
        stopIptvOverlay()
        val previousActiveKey = gridChannels.getOrNull(gridActiveIndex)?.sourceKey
        if (!iptvGridActive) {
            gridReturnChannel = getCurrentChannel()
            playback.stop()
            iptvPlayback.stop()
        }
        gridChannels = distinct
        gridSelectedKeys.clear()
        gridSelectedKeys += distinct.map { it.sourceKey }
        gridActiveIndex = distinct.indexOfFirst { it.sourceKey == (previousActiveKey ?: getCurrentChannel()?.sourceKey) }
            .takeIf { it >= 0 } ?: 0
        gridFullscreenIndex = null
        iptvGridActive = true
        osdCoordinator.setPlaybackMode(PlaybackSurfaceMode.IPTV_GRID)
        endBlackout("IPTV_GRID")
        binding.iptvPlayerView.visibility = View.GONE
        binding.audioOnlyPanel.visibility = View.GONE
        hideChannelPanel()
        setInfoBarVisible(false)
        renderIptvGrid()
        debugLog.recordDebug("MULTIVIEW_START | channels=${gridChannels.joinToString { it.sourceKey }}")
    }

    fun renderIptvGrid() {
        multiViewDraftInitialized = true
        multiViewDraft.clear()
        gridChannels.forEach { multiViewDraft[it.sourceKey] = it }
        val tifChannel = gridChannels.firstOrNull { it.source == LiveChannel.Source.TIF }
        gridControllers.forEachIndexed { index, controller ->
            val channel = gridChannels.getOrNull(index)
            gridCells[index].visibility = if (channel == null) View.INVISIBLE else View.VISIBLE
            gridLabels[index].text = channel?.displayName.orEmpty()
            if (channel == null || channel.source == LiveChannel.Source.TIF) {
                if (renderedGridKeys[index] != null) controller.stop()
                renderedGridKeys[index] = null
                gridPlayerViews[index].visibility = View.GONE
            } else {
                gridPlayerViews[index].visibility = View.VISIBLE
                if (renderedGridKeys[index] != channel.sourceKey) {
                    controller.stop()
                    controller.setMuted(index != gridActiveIndex)
                    controller.play(channel)
                    renderedGridKeys[index] = channel.sourceKey
                }
            }
        }
        if (tifChannel == null) {
            if (renderedGridTifKey != null) playback.stop()
            renderedGridTifKey = null
            binding.tvView.visibility = View.GONE
        } else {
            if (renderedGridTifKey != tifChannel.sourceKey) {
                binding.tvView.visibility = View.GONE
                playback.stop()
                binding.tvView.setVideoSize(0, 0)
                renderedGridTifKey = tifChannel.sourceKey
            }
        }
        applyIptvGridLayout()
        updateIptvGridFocus()
        binding.iptvGrid.post(::positionGridTifSurface)
    }

    private fun applyIptvGridLayout() {
        val count = gridChannels.size
        val fullscreen = gridFullscreenIndex
        gridCells.forEachIndexed { index, cell ->
            if (index !in gridChannels.indices) {
                cell.visibility = View.GONE
                cell.layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(index / 2, 1, GridLayout.FILL, 0f),
                    GridLayout.spec(index % 2, 1, GridLayout.FILL, 0f),
                ).apply {
                    width = 0
                    height = 0
                }
                return@forEachIndexed
            }
            if (fullscreen != null && fullscreen != index) {
                gridControllers[index].pause()
                cell.visibility = View.GONE
                return@forEachIndexed
            }
            cell.visibility = View.VISIBLE
            val rowSpec: GridLayout.Spec
            val columnSpec: GridLayout.Spec
            when {
                fullscreen == index || count == 1 -> {
                    rowSpec = GridLayout.spec(0, 2, GridLayout.FILL, 1f)
                    columnSpec = GridLayout.spec(0, 2, GridLayout.FILL, 1f)
                }
                count <= 2 -> {
                    rowSpec = GridLayout.spec(0, 2, GridLayout.FILL, 1f)
                    columnSpec = GridLayout.spec(index.coerceIn(0, 1), 1, GridLayout.FILL, 1f)
                }
                count == 3 && index == 0 -> {
                    rowSpec = GridLayout.spec(0, 2, GridLayout.FILL, 1f)
                    columnSpec = GridLayout.spec(0, 1, GridLayout.FILL, 1f)
                }
                count == 3 -> {
                    rowSpec = GridLayout.spec((index - 1).coerceIn(0, 1), 1, GridLayout.FILL, 1f)
                    columnSpec = GridLayout.spec(1, 1, GridLayout.FILL, 1f)
                }
                else -> {
                    rowSpec = GridLayout.spec((index / 2).coerceIn(0, 1), 1, GridLayout.FILL, 1f)
                    columnSpec = GridLayout.spec((index % 2).coerceIn(0, 1), 1, GridLayout.FILL, 1f)
                }
            }
            cell.layoutParams = GridLayout.LayoutParams(rowSpec, columnSpec).apply {
                width = 0
                height = 0
                val margin = if (fullscreen == index) 0 else 3.dp
                setMargins(margin, margin, margin, margin)
            }
        }
        binding.iptvGrid.requestLayout()
        if (!gridTifSurfaceLayoutListenerAttached) {
            gridTifSurfaceLayoutListenerAttached = true
            binding.iptvGrid.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                if (iptvGridActive) positionGridTifSurface()
            }
        }
        binding.iptvGrid.post(::positionGridTifSurface)
        binding.iptvGrid.doOnLayout {
            if (!iptvGridActive) return@doOnLayout
            gridControllers.forEachIndexed { index, controller ->
                if (gridCells[index].visibility == View.VISIBLE &&
                    gridChannels.getOrNull(index)?.source == LiveChannel.Source.IPTV
                ) {
                    controller.reattachVideoSurface()
                    controller.goLive()
                    controller.play()
                }
            }
        }
    }

    private fun positionGridTifSurface() {
        val tifIndex = gridChannels.indexOfFirst { it.source == LiveChannel.Source.TIF }
        if (!iptvGridActive || tifIndex !in gridCells.indices) return
        if (gridFullscreenIndex != null && gridFullscreenIndex != tifIndex) {
            gridTifViewportGeneration++
            binding.tvView.visibility = View.GONE
            return
        }
        val cell = gridCells[tifIndex]
        binding.tvView.fitSurfaceToBounds = true
        if (cell.width <= 0 || cell.height <= 0) return
        val targetLeft = (binding.iptvGrid.x + cell.x).toInt()
        val targetTop = (binding.iptvGrid.y + cell.y).toInt()
        val params = binding.tvView.layoutParams as FrameLayout.LayoutParams
        if (
            binding.tvView.visibility == View.VISIBLE &&
            params.width == cell.width &&
            params.height == cell.height &&
            params.leftMargin == targetLeft &&
            params.topMargin == targetTop
        ) {
            return
        }
        binding.tvView.layoutParams = params.apply {
            width = cell.width
            height = cell.height
            leftMargin = targetLeft
            topMargin = targetTop
            rightMargin = 0
            bottomMargin = 0
            gravity = Gravity.TOP or Gravity.START
        }
        binding.tvView.visibility = View.VISIBLE
        val generation = ++gridTifViewportGeneration
        val channel = gridChannels[tifIndex]
        binding.tvView.doOnLayout {
            binding.tvView.post {
                if (!iptvGridActive || generation != gridTifViewportGeneration ||
                    gridChannels.getOrNull(tifIndex)?.sourceKey != channel.sourceKey ||
                    binding.tvView.visibility != View.VISIBLE
                ) return@post
                playback.stop()
                playback.setMuted(gridActiveIndex != tifIndex)
                playback.play(channel)
                debugLog.recordDebug("MULTIVIEW_TIF_VIEWPORT | width=${binding.tvView.width}, height=${binding.tvView.height}")
            }
        }
    }

    fun restorePrimaryPlayerSurfaces() {
        binding.tvView.fitSurfaceToBounds = false
        listOf<View>(binding.tvView, binding.iptvPlayerView).forEach { view ->
            view.layoutParams = (view.layoutParams as FrameLayout.LayoutParams).apply {
                width = FrameLayout.LayoutParams.MATCH_PARENT
                height = FrameLayout.LayoutParams.MATCH_PARENT
                setMargins(0, 0, 0, 0)
                gravity = Gravity.FILL
            }
        }
    }

    fun updateIptvGridFocus() {
        val tifIndex = gridChannels.indexOfFirst { it.source == LiveChannel.Source.TIF }
        if (!iptvGridActive) return
        gridCells.forEachIndexed { index, cell ->
            cell.background = ContextCompat.getDrawable(
                activity,
                if (index == gridActiveIndex) R.drawable.bg_grid_cell_selected else R.drawable.bg_grid_cell,
            )
            gridControllers[index].setMuted(
                index != gridActiveIndex ||
                    gridChannels.getOrNull(index)?.source != LiveChannel.Source.IPTV,
            )
        }
        playback.setMuted(gridActiveIndex != tifIndex)
        debugLog.recordDebug(
            "MULTIVIEW_FOCUS | slot=$gridActiveIndex, channel=${gridChannels.getOrNull(gridActiveIndex)?.sourceKey}",
        )
    }

    fun moveIptvGridFocus(keyCode: Int) {
        if (gridFullscreenIndex != null) return
        val candidate = MultiViewFocusResolver.nextFocus(
            keyCode,
            gridActiveIndex,
            gridChannels.size,
        )
        if (candidate in gridChannels.indices && candidate != gridActiveIndex) {
            gridActiveIndex = candidate
            updateIptvGridFocus()
        }
    }

    fun zapIptvGridChannel(offset: Int) {
        val current = gridChannels.getOrNull(gridActiveIndex) ?: return
        val pool = availableMultiViewChannels().filter { candidate ->
            candidate.source == current.source &&
                gridChannels.none {
                    it.sourceKey == candidate.sourceKey && it.sourceKey != current.sourceKey
                }
        }
        val currentIndex = pool.indexOfFirst { it.sourceKey == current.sourceKey }
        if (currentIndex < 0 || pool.size < 2) return
        replaceIptvGridChannel(
            gridActiveIndex,
            pool[Math.floorMod(currentIndex + offset, pool.size)],
        )
    }

    fun replaceIptvGridChannel(index: Int, replacement: LiveChannel, connectionsChecked: Boolean = false) {
        if (index !in gridChannels.indices) return
        if (replacement.source == LiveChannel.Source.TIF &&
            gridChannels.withIndex().any { it.index != index && it.value.source == LiveChannel.Source.TIF }
        ) return
        if (!connectionsChecked) {
            checkSourceConnections(gridChannels.toMutableList().apply { this[index] = replacement }) {
                if (iptvGridActive) replaceIptvGridChannel(index, replacement, connectionsChecked = true)
            }
            return
        }
        gridChannels = gridChannels.toMutableList().apply { this[index] = replacement }
        gridSelectedKeys.clear()
        gridSelectedKeys += gridChannels.map { it.sourceKey }
        if (gridFullscreenIndex != null) gridFullscreenIndex = index
        renderIptvGrid()
    }

    fun stopIptvGrid(resumePrevious: Boolean) {
        if (!iptvGridActive) return
        val focused = gridChannels.getOrNull(gridActiveIndex)
        val fallback = gridReturnChannel
        iptvGridActive = false
        gridTifViewportGeneration++
        gridLongPressJob?.cancel()
        gridFullscreenIndex = null
        gridControllers.forEach(IptvPlaybackController::stop)
        renderedGridKeys.indices.forEach { renderedGridKeys[it] = null }
        renderedGridTifKey = null
        playback.stop()
        playback.setMuted(false)
        restorePrimaryPlayerSurfaces()
        osdCoordinator.setPlaybackMode(PlaybackSurfaceMode.SINGLE)
        gridChannels = emptyList()
        gridSelectedKeys.clear()
        gridReturnChannel = null
        if (resumePrevious) (focused ?: fallback)?.let {
            playSelectedChannel(it, false)
        }
        debugLog.recordDebug("MULTIVIEW_STOP | resume=$resumePrevious, channel=${focused?.sourceKey}")
    }

    fun openActiveGridChannelFullscreen() {
        if (gridChannels.getOrNull(gridActiveIndex) == null) return
        gridFullscreenIndex = gridActiveIndex
        applyIptvGridLayout()
        updateIptvGridFocus()
    }

    fun closeActiveGridChannelFullscreen() {
        gridFullscreenIndex = null
        applyIptvGridLayout()
        updateIptvGridFocus()
    }

    fun openActiveGridChannel() {
        if (gridFullscreenIndex != null) {
            closeActiveGridChannelFullscreen()
        } else {
            openActiveGridChannelFullscreen()
        }
    }

    fun closeActiveGridChannel() {
        val mutable = gridChannels.toMutableList()
        if (gridActiveIndex !in mutable.indices) return
        mutable.removeAt(gridActiveIndex)
        if (mutable.isEmpty()) {
            stopIptvGrid(resumePrevious = true)
            return
        }
        gridChannels = mutable
        gridSelectedKeys.clear()
        gridSelectedKeys += mutable.map { it.sourceKey }
        gridActiveIndex = gridActiveIndex.coerceAtMost(mutable.lastIndex)
        gridFullscreenIndex = null
        renderIptvGrid()
    }

    fun showActiveGridChannelActions() {
        val channel = gridChannels.getOrNull(gridActiveIndex) ?: return
        val labels = mutableListOf(
            activity.getString(R.string.grid_channel_change),
            activity.getString(R.string.grid_channel_close),
            activity.getString(R.string.multiview_add_channels),
        )
        val pipBackground = if (gridChannels.size == 2) gridChannels.firstOrNull { it.sourceKey != channel.sourceKey } else gridReturnChannel
        val canUsePip = gridChannels.size <= 2 && channel.source == LiveChannel.Source.IPTV &&
            pipBackground != null && pipBackground.sourceKey != channel.sourceKey
        if (canUsePip) labels += activity.getString(R.string.multiview_to_pip)
        AlertDialog.Builder(activity, R.style.Theme_TVApp_Dialog)
            .setTitle(channel.displayName)
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showGridChannelReplacementPicker()
                    1 -> closeActiveGridChannel()
                    2 -> showIptvGridPicker()
                    3 -> {
                        stopIptvGrid(resumePrevious = false)
                        pipBackground?.let { playSelectedChannel(it, false) }
                        startIptvOverlay(channel)
                    }
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    fun showGridChannelReplacementPicker() {
        val targetIndex = gridActiveIndex
        val hasOtherTif = gridChannels.withIndex().any {
            it.index != targetIndex && it.value.source == LiveChannel.Source.TIF
        }
        val choices = availableMultiViewChannels().filter { candidate ->
            gridChannels.none { it.sourceKey == candidate.sourceKey } &&
                (!hasOtherTif || candidate.source != LiveChannel.Source.TIF)
        }
        if (choices.isEmpty()) return
        AlertDialog.Builder(activity)
            .setTitle(R.string.grid_channel_change)
            .setItems(choices.map { it.displayName }.toTypedArray()) { _, which ->
                replaceIptvGridChannel(targetIndex, choices[which])
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    fun handleGridKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_PROG_GREEN -> if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                showIptvGridPicker()
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    gridLongPressHandled = false
                    gridLongPressJob?.cancel()
                    gridLongPressJob = scope.launch {
                        delay(ViewConfiguration.getLongPressTimeout().toLong())
                        gridLongPressHandled = true
                        showActiveGridChannelActions()
                    }
                } else if (event.action == KeyEvent.ACTION_UP) {
                    gridLongPressJob?.cancel()
                    if (!gridLongPressHandled) {
                        openActiveGridChannel()
                    }
                }
            }
            KeyEvent.KEYCODE_BACK -> if (event.action == KeyEvent.ACTION_DOWN) {
                if (gridFullscreenIndex != null) {
                    closeActiveGridChannelFullscreen()
                } else {
                    stopIptvGrid(resumePrevious = true)
                }
            }
            KeyEvent.KEYCODE_CHANNEL_UP -> if (event.action == KeyEvent.ACTION_DOWN) {
                zapIptvGridChannel(1)
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN -> if (event.action == KeyEvent.ACTION_DOWN) {
                zapIptvGridChannel(-1)
            }
            else -> if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT,
                    KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_UP,
                    KeyEvent.KEYCODE_DPAD_DOWN -> moveIptvGridFocus(event.keyCode)
                }
            }
        }
        return true
    }

    fun release() {
        gridControllers.forEach(IptvPlaybackController::release)
    }
}
