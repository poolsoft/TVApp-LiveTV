package com.tvapp.livetv

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvTrackInfo
import android.os.Bundle
import android.graphics.Rect
import android.util.Rational
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.ui.PlayerView
import com.tvapp.livetv.data.ChannelRepository
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.ProgramRepository
import com.tvapp.livetv.data.ProgramSummary
import com.tvapp.livetv.databinding.ActivityMainBinding
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.home.HomeRecentChannelsPublisher
import com.tvapp.livetv.playback.TifPlaybackController
import com.tvapp.livetv.playback.IptvPlaybackController
import com.tvapp.livetv.playback.IptvContentKind
import com.tvapp.livetv.playback.ChannelNavigator
import com.tvapp.livetv.playback.PlaybackHistoryStore
import com.tvapp.livetv.settings.ChannelPanelSide
import com.tvapp.livetv.settings.ChannelListFilterStore
import com.tvapp.livetv.settings.ChannelSourceFilter
import com.tvapp.livetv.settings.DisplayPreferences
import com.tvapp.livetv.settings.DisplayPreferencesStore
import com.tvapp.livetv.settings.InfoBarPosition
import com.tvapp.livetv.settings.SleepTimerStore
import com.tvapp.livetv.settings.ParentalControlStore
import com.tvapp.livetv.ui.ChannelAdapter
import com.tvapp.livetv.ui.ChannelRowOptions
import com.tvapp.livetv.ui.ParentalPinDialog
import com.tvapp.livetv.ui.isRadioChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
        private const val MAX_CHANNEL_DIGITS = 5
        private const val NUMBER_ENTRY_TIMEOUT_MS = 1_500L
        private const val COMPACT_PANEL_WIDTH_FRACTION = 0.25f
        private const val EXPANDED_PANEL_FRACTION = 0.44f
        private const val INFO_HEIGHT_FRACTION = 0.26f
        private const val OVERLAY_GAP_FRACTION = 0.008f
        private const val VERTICAL_MARGIN_FRACTION = 0.026f
        private const val INFO_HORIZONTAL_PADDING_FRACTION = 0.012f
        private const val INFO_OUTER_MARGIN_FRACTION = 0.007f
        private const val INFO_VERTICAL_PADDING_FRACTION = 0.012f
        private const val CHANNEL_ACTIONS_HEIGHT_FRACTION = 0.12f
        private const val LIST_HORIZONTAL_PADDING_FRACTION = 0.006f
        private const val EXPANDED_LIST_BOTTOM_PADDING_FRACTION = 0.11f
        private const val CLOCK_REFRESH_MS = 30_000L
        private const val CHANNEL_PANEL_TIMEOUT_MS = 10_000L
        private const val INTERNAL_MINI_WIDTH_FRACTION = 0.38f
        private const val IPTV_CONTROLS_WIDTH_FRACTION = 0.68f
        private const val IPTV_CONTROLS_BOTTOM_MARGIN_FRACTION = 0.045f
        private const val IPTV_CONTROL_TIMEOUT_MS = 6_000L
        private const val IPTV_LIVE_CHECK_INTERVAL_MS = 12_000L
        private const val IPTV_MAX_LIVE_OFFSET_MS = 18_000L
        private const val IPTV_VOD_SEEK_STEP_MS = 30_000L
        private const val IPTV_LIBRARY_FILTER_PREFS = "iptv-library-filter"
        private const val IPTV_LIBRARY_SOURCE_ID = "source-id"
        private const val IPTV_LIBRARY_CONTENT_TYPE = "content-type"
        private const val IPTV_LIBRARY_CATEGORY = "category"
        private const val IPTV_LIBRARY_PAGE_SIZE = 250
        private const val IPTV_LIBRARY_PREFETCH_DISTANCE = 24
    }

    private enum class ChannelPanelContent { NORMAL, IPTV_LIBRARY }
    private enum class IptvLibraryContentType { ALL, LIVE, VOD }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ChannelRepository
    private lateinit var iptvRepository: IptvRepository
    private lateinit var programRepository: ProgramRepository
    private lateinit var playback: TifPlaybackController
    private lateinit var iptvPlayback: IptvPlaybackController
    private lateinit var secondaryPlayback: TifPlaybackController
    private lateinit var secondaryIptvPlayback: IptvPlaybackController
    private lateinit var playbackHistory: PlaybackHistoryStore
    private lateinit var displayPreferencesStore: DisplayPreferencesStore
    private lateinit var channelListFilterStore: ChannelListFilterStore
    private lateinit var sleepTimerStore: SleepTimerStore
    private lateinit var parentalControlStore: ParentalControlStore
    private lateinit var homeRecentChannelsPublisher: HomeRecentChannelsPublisher
    private lateinit var debugLog: CrashReportStore
    private lateinit var adapter: ChannelAdapter
    private var displayPreferences = DisplayPreferences()
    private var channels: List<LiveChannel> = emptyList()
    private var currentPrograms: Map<Long, ProgramSummary> = emptyMap()
    private var currentChannel: LiveChannel? = null
    private var channelPanelContent = ChannelPanelContent.NORMAL
    private var iptvLibraryChannels: List<LiveChannel> = emptyList()
    private var iptvLibrarySourceId: Long? = null
    private var iptvLibraryContentType = IptvLibraryContentType.ALL
    private var iptvLibraryCategory: String? = null
    private var iptvLibraryOffset = 0
    private var iptvLibraryTotalCount = 0
    private var iptvLibraryExhausted = true
    private var iptvLibraryLoadJob: Job? = null
    private var currentPlaybackUsesIptvLibrary = false
    private var lockedChannelRecordsHistory = true
    private var internalMiniPlayerActive = false
    private var multiViewActive = false
    private var multiViewActiveSide = 0
    private var multiViewSatelliteChannel: LiveChannel? = null
    private var multiViewIptvChannel: LiveChannel? = null
    private var multiViewLongPressJob: Job? = null
    private var iptvOverlayActive = false
    private var iptvGridActive = false
    private var gridActiveIndex = 0
    private var gridFullscreenIndex: Int? = null
    private var gridLongPressHandled = false
    private var gridLongPressJob: Job? = null
    private var gridReturnChannel: LiveChannel? = null
    private var gridChannels: List<LiveChannel> = emptyList()
    private val gridSelectedKeys = mutableListOf<String>()
    private val gridCells = mutableListOf<FrameLayout>()
    private val gridLabels = mutableListOf<TextView>()
    private val gridControllers = mutableListOf<IptvPlaybackController>()
    private val unlockedChannels = mutableSetOf<String>()
    private val loggedRemoteKeyCodes = mutableSetOf<Int>()
    private var favoriteFilter = false
    private var sourceFilter = ChannelSourceFilter.ALL
    private var channelPanelExpanded = false
    private var numberInput = ""
    private var numberInputJob: Job? = null
    private var focusedTuneJob: Job? = null
    private var infoBarJob: Job? = null
    private var channelPanelJob: Job? = null
    private var programJob: Job? = null
    private var channelProgramsJob: Job? = null
    private var channelLoadJob: Job? = null
    private var clockJob: Job? = null
    private var statusRetryAction: (() -> Unit)? = null
    private var restoredInitialChannel = false
    private var pendingEditorChannelKey: String? = null
    private var pendingHomeChannelKey: String? = null
    private var focusedListSourceKey: String? = null
    private var focusedAutoTunePreviousChannel: LiveChannel? = null
    private var focusedAutoTuneTargetKey: String? = null
    private var focusedProgramJob: Job? = null
    private var visibleProgramsJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var iptvControlsJob: Job? = null
    private var iptvLiveHealthJob: Job? = null
    private var currentIptvContentKind = IptvContentKind.UNKNOWN
    private val tvListingsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            loadChannels()
        } else {
            showPermissionDenied(repository.tunerInputs())
        }
    }
    private val tunerSetup = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        loadChannels()
    }
    private val channelEditor = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        unlockedChannels.removeAll { parentalControlStore.isLocked(it) }
        pendingEditorChannelKey = result.data?.getStringExtra(
            ChannelEditorActivity.EXTRA_SELECTED_SOURCE_KEY,
        )
        loadChannels(preserveCurrentPlayback = true)
    }
    private val iptvSources = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        loadChannels(preserveCurrentPlayback = true)
        if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
            openSavedIptvLibrary()
        }
    }
    private val displaySettings = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        applyDisplayPreferences()
        scheduleSleepTimer()
    }
    private val programGuide = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val sourceKey = result.data?.getStringExtra(
            ProgramGuideActivity.EXTRA_SELECTED_SOURCE_KEY,
        )
        channels.firstOrNull { it.sourceKey == sourceKey }?.let(::selectChannel)
        if (sourceKey != null) hideChannelPanel()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.tvView.keepScreenOn = true

        repository = ChannelRepository(this)
        iptvRepository = IptvRepository(this)
        programRepository = ProgramRepository(this)
        playback = TifPlaybackController(binding.tvView)
        iptvPlayback = IptvPlaybackController(this, binding.iptvPlayerView)
        secondaryPlayback = TifPlaybackController(binding.secondaryTvView)
        secondaryIptvPlayback = IptvPlaybackController(this, binding.secondaryIptvPlayerView)
        secondaryIptvPlayback.onPlaybackError = { error ->
            debugLog.recordDebug("MULTIVIEW_IPTV_FAILURE | ${error.errorCodeName}: ${error.message}")
            if (iptvOverlayActive) stopIptvOverlay() else stopMultiView()
        }
        playbackHistory = PlaybackHistoryStore(this)
        displayPreferencesStore = DisplayPreferencesStore(this)
        channelListFilterStore = ChannelListFilterStore(this)
        sleepTimerStore = SleepTimerStore(this)
        parentalControlStore = ParentalControlStore(this)
        homeRecentChannelsPublisher = HomeRecentChannelsPublisher(this)
        pendingHomeChannelKey = intent.data?.getQueryParameter("sourceKey")
        sourceFilter = ChannelSourceFilter.ALL
        favoriteFilter = false
        channelPanelContent = ChannelPanelContent.NORMAL
        debugLog = CrashReportStore(this)
        prepareIptvGrid()
        debugLog.recordDebug("MAIN_CREATE | savedState=${savedInstanceState != null}")
        if (intent.getBooleanExtra(BootLaunchReceiver.EXTRA_STARTED_AFTER_BOOT, false)) {
            debugLog.recordDebug("MAIN_STARTED_AFTER_BOOT")
        }
        playback.onTracksChanged = { applyPreferredTracks() }
        playback.onVideoStateChanged = { available, _ ->
            val channel = currentChannel
            if (channel != null && channel.source == LiveChannel.Source.TIF) {
                when {
                    channel.isRadioChannel() -> updateAudioOnlyPanel(channel, true)
                    available -> updateAudioOnlyPanel(channel, false)
                    else -> updateAudioOnlyPanel(channel, false)
                }
            }
        }
        iptvPlayback.onPlaybackError = { error ->
            debugLog.recordDebug("IPTV_PLAYBACK_FAILURE | ${error.errorCodeName}: ${error.message}")
            showPlaybackError(currentChannel, error.message ?: error.errorCodeName)
        }
        iptvPlayback.onContentKindChanged = { kind ->
            if (currentChannel?.source == LiveChannel.Source.IPTV) {
                currentIptvContentKind = kind
                if (kind == IptvContentKind.LIVE) startIptvLiveHealthMonitor()
                else iptvLiveHealthJob?.cancel()
            }
        }
        adapter = ChannelAdapter(
            ::confirmListChannel,
            ::showChannelManagement,
            ::scheduleFocusedTune,
            { channel -> parentalControlStore.isLocked(channel.sourceKey) },
        )
        binding.channelList.layoutManager = LinearLayoutManager(this)
        binding.channelList.adapter = adapter
        binding.channelList.itemAnimator = null
        binding.channelList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) loadVisiblePrograms()
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (channelPanelContent != ChannelPanelContent.IPTV_LIBRARY) return
                val manager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                if (
                    manager.findLastVisibleItemPosition() >=
                    adapter.itemCount - IPTV_LIBRARY_PREFETCH_DISTANCE
                ) {
                    loadNextIptvLibraryPage()
                }
            }
        })
        binding.retryButton.setOnClickListener {
            statusRetryAction?.invoke() ?: ensurePermissionAndLoad()
        }
        binding.closeButton.setOnClickListener { binding.statusPanel.visibility = View.GONE }
        binding.scanButton.setOnClickListener { openTunerSetup() }
        binding.allFilter.setOnClickListener {
            applyChannelFilter(source = ChannelSourceFilter.ALL)
        }
        binding.satelliteFilter.setOnClickListener {
            applyChannelFilter(source = ChannelSourceFilter.SATELLITE)
        }
        binding.iptvFilter.setOnClickListener {
            applyChannelFilter(source = ChannelSourceFilter.IPTV)
        }
        binding.favoriteFilter.setOnClickListener {
            applyChannelFilter(showFavorites = !favoriteFilter)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        applyDisplayPreferences()
        startClock()
        scheduleSleepTimer()
        ensurePermissionAndLoad()
    }

    private fun scheduleSleepTimer() {
        sleepTimerJob?.cancel()
        val remainingMillis = sleepTimerStore.endAtMillis() - System.currentTimeMillis()
        if (remainingMillis <= 0L) {
            if (sleepTimerStore.endAtMillis() != 0L) sleepTimerStore.clear()
            return
        }
        sleepTimerJob = lifecycleScope.launch {
            delay(remainingMillis)
            sleepTimerStore.clear()
            debugLog.recordDebug("SLEEP_TIMER_EXPIRED")
            playback.stop()
            iptvPlayback.stop()
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            binding.tvView.keepScreenOn = false
            binding.iptvPlayerView.keepScreenOn = false
            moveTaskToBack(true)
        }
    }

    private fun openTunerSetup() {
        val setupIntent = repository.tunerSetupIntent()
        if (setupIntent == null) {
            showSetupError(
                title = getString(R.string.setup_not_available_title),
                detail = getString(R.string.setup_not_available_message),
            )
            return
        }

        try {
            tunerSetup.launch(setupIntent)
        } catch (error: ActivityNotFoundException) {
            showSetupError(getString(R.string.setup_open_error_title), error.message)
        } catch (error: SecurityException) {
            showSetupError(getString(R.string.setup_open_error_title), error.message)
        }
    }

    private fun showSetupError(title: String, detail: String?) {
        binding.statusPanel.visibility = View.VISIBLE
        binding.statusTitle.text = title
        binding.statusMessage.text = detail.orEmpty().ifBlank { getString(R.string.no_detail) }
        binding.closeButton.requestFocus()
    }

    private fun ensurePermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(
                this,
                READ_TV_LISTINGS,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            loadChannels()
            return
        }

        val inputs = repository.tunerInputs()
        binding.inputSummary.text = resources.getQuantityString(
            R.plurals.input_count,
            inputs.size,
            inputs.size,
        )
        binding.statusPanel.visibility = View.VISIBLE
        binding.statusTitle.setText(R.string.permission_required_title)
        binding.statusMessage.setText(R.string.permission_required_message)
        tvListingsPermission.launch(READ_TV_LISTINGS)
    }

    private fun loadChannels(preserveCurrentPlayback: Boolean = false) {
        val inputs = repository.tunerInputs()
        binding.inputSummary.text = resources.getQuantityString(
            R.plurals.input_count,
            inputs.size,
            inputs.size,
        )

        channelLoadJob?.cancel()
        channelLoadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { repository.channels() }
            result.fold(
                onSuccess = { loaded ->
                    debugLog.recordDebug("CHANNEL_LOAD_SUCCESS | count=${loaded.size}")
                    val currentKey = currentChannel?.sourceKey
                    val playingChannel = currentChannel
                    channels = loaded
                    currentChannel = loaded.firstOrNull { it.sourceKey == currentKey }
                        ?: playingChannel.takeIf { preserveCurrentPlayback }
                    applyChannelFilter(requestFocus = false)
                    loadChannelPrograms(loaded)
                    if (loaded.isEmpty()) showEmptyState(inputs) else showChannels(loaded)
                    val editorChannelKey = pendingEditorChannelKey
                    pendingEditorChannelKey = null
                    val requestedKey = pendingHomeChannelKey ?: editorChannelKey
                    pendingHomeChannelKey = null
                    val editorChannel = requestedKey?.let { key ->
                        panelChannels().firstOrNull { it.sourceKey == key }
                            ?: loaded.firstOrNull { it.sourceKey == key }
                    }
                    if (editorChannel != null) {
                        restoredInitialChannel = true
                        selectChannel(editorChannel)
                    } else if (!restoredInitialChannel && loaded.isNotEmpty()) {
                        restoredInitialChannel = true
                        val startupChannels = panelChannels().ifEmpty { loaded }
                        val startupChannel = playbackHistory.keys()
                            .firstNotNullOfOrNull { key ->
                                startupChannels.firstOrNull { it.sourceKey == key }
                            }
                            ?: startupChannels.first()
                        selectChannel(startupChannel)
                    }
                },
                onFailure = {
                    debugLog.recordDebug(
                        "CHANNEL_LOAD_FAILURE | ${it.javaClass.name}: ${it.message}",
                    )
                    showReadError(inputs, it)
                },
            )
        }
    }

    private fun showChannels(loaded: List<LiveChannel>) {
        statusRetryAction = null
        binding.statusPanel.visibility = View.GONE
        updateChannelCount(panelChannels().size)
    }

    private fun showEmptyState(inputs: List<TvInputInfo>) {
        binding.statusPanel.visibility = View.VISIBLE
        binding.statusTitle.setText(R.string.no_channels_title)
        binding.statusMessage.text = if (inputs.isEmpty()) {
            getString(R.string.no_inputs_message)
        } else {
            getString(R.string.no_channels_message, inputs.joinToString("\n") { it.id })
        }
    }

    private fun showReadError(inputs: List<TvInputInfo>, error: Throwable) {
        binding.statusPanel.visibility = View.VISIBLE
        binding.statusTitle.setText(R.string.permission_title)
        binding.statusMessage.text = getString(
            R.string.permission_message,
            error.javaClass.simpleName,
            error.message.orEmpty().ifBlank { getString(R.string.no_detail) },
            inputs.joinToString("\n") { it.id }.ifBlank { getString(R.string.none) },
        )
    }

    private fun showPermissionDenied(inputs: List<TvInputInfo>) {
        binding.statusPanel.visibility = View.VISIBLE
        binding.statusTitle.setText(R.string.permission_denied_title)
        binding.inputSummary.text = resources.getQuantityString(
            R.plurals.input_count,
            inputs.size,
            inputs.size,
        )
        binding.statusMessage.setText(R.string.permission_denied_message)
    }

    private fun selectChannel(channel: LiveChannel) = selectChannel(channel, recordHistory = true)

    private fun selectChannel(channel: LiveChannel, recordHistory: Boolean) {
        if (parentalControlStore.isLocked(channel.sourceKey) && channel.sourceKey !in unlockedChannels) {
            showLockedChannel(channel, recordHistory)
            return
        }
        playSelectedChannel(channel, recordHistory)
    }

    private fun showLockedChannel(channel: LiveChannel, recordHistory: Boolean = true) {
        focusedTuneJob?.cancel()
        playback.stop()
        iptvPlayback.stop()
        lockedChannelRecordsHistory = recordHistory
        currentPlaybackUsesIptvLibrary = !recordHistory
        currentChannel = channel
        adapter.select(channel.sourceKey)
        binding.tvView.visibility = View.GONE
        binding.iptvPlayerView.visibility = View.GONE
        binding.audioOnlyPanel.visibility = View.GONE
        binding.statusPanel.visibility = View.GONE
        binding.parentalLockChannel.text = channel.displayName
        binding.parentalLockPanel.visibility = View.VISIBLE
        binding.nowChannel.text = channel.displayName
        binding.nowNumber.text = channel.displayNumber
        updateTechnicalBadges(channel, emptyList())
        showInfoBar()
        loadPrograms(channel)
    }

    private fun requestChannelPin(channel: LiveChannel) {
        ParentalPinDialog.verify(this, channel.displayName) {
            unlockedChannels.add(channel.sourceKey)
            playSelectedChannel(channel, lockedChannelRecordsHistory)
        }
    }

    private fun playSelectedChannel(channel: LiveChannel, recordHistory: Boolean = true) {
        if (iptvOverlayActive) stopIptvOverlay()
        if (iptvGridActive) stopIptvGrid(resumePrevious = false)
        debugLog.recordDebug(
            "CHANNEL_SELECT | number=${channel.displayNumber}, key=${channel.sourceKey}",
        )
        focusedTuneJob?.cancel()
        currentPlaybackUsesIptvLibrary = !recordHistory
        currentChannel = channel
        updateChannelActionLabels()
        currentIptvContentKind = IptvContentKind.UNKNOWN
        iptvControlsJob?.cancel()
        iptvLiveHealthJob?.cancel()
        binding.iptvPlaybackControls.visibility = View.GONE
        binding.parentalLockPanel.visibility = View.GONE
        if (recordHistory) {
            playbackHistory.record(channel.sourceKey)
            lifecycleScope.launch(Dispatchers.IO) {
                runCatching {
                    homeRecentChannelsPublisher.publish(channels, playbackHistory.keys())
                }.onFailure { error ->
                    debugLog.recordDebug(
                        "HOME_RECENTS_FAILURE | ${error.javaClass.name}: ${error.message}",
                    )
                }
            }
        }
        adapter.select(channel.sourceKey)
        binding.nowChannel.text = channel.displayName
        binding.nowNumber.text = channel.displayNumber
        updateTechnicalBadges(channel, emptyList())
        updateAudioOnlyPanel(channel, channel.isRadioChannel())
        binding.statusPanel.visibility = View.GONE
        statusRetryAction = null
        showInfoBar()
        loadPrograms(channel)
        runCatching {
            when (channel.source) {
                LiveChannel.Source.TIF -> {
                    iptvPlayback.stop()
                    binding.iptvPlayerView.visibility = View.GONE
                    binding.tvView.visibility = View.VISIBLE
                    playback.play(channel)
                }
                LiveChannel.Source.IPTV -> {
                    playback.stop()
                    binding.tvView.visibility = View.GONE
                    binding.iptvPlayerView.visibility = View.VISIBLE
                    iptvPlayback.play(channel)
                }
            }
        }
            .onFailure {
                debugLog.recordDebug("PLAYBACK_FAILURE | ${it.javaClass.name}: ${it.message}")
                showPlaybackError(channel, it.message ?: it.javaClass.simpleName)
            }
    }

    private fun showPlaybackError(channel: LiveChannel?, detail: String) {
        binding.statusPanel.visibility = View.VISIBLE
        binding.statusTitle.setText(R.string.playback_error_title)
        binding.inputSummary.text = channel?.displayName.orEmpty()
        binding.statusMessage.text = detail
        statusRetryAction = channel?.let { failedChannel ->
            { selectChannel(failedChannel, recordHistory = !currentPlaybackUsesIptvLibrary) }
        }
        binding.retryButton.requestFocus()
    }

    private fun confirmListChannel(channel: LiveChannel) {
        focusedTuneJob?.cancel()
        focusedAutoTunePreviousChannel = null
        focusedAutoTuneTargetKey = null
        val recordHistory = channelPanelContent == ChannelPanelContent.NORMAL
        if (channel.sourceKey != currentChannel?.sourceKey) selectChannel(channel, recordHistory)
        hideChannelPanel()
    }

    private fun zap(offset: Int) {
        ChannelNavigator.adjacent(playbackNavigationChannels(), currentChannel?.sourceKey, offset)
            ?.let { channel ->
                selectChannel(channel, recordHistory = shouldRecordNavigationHistory())
                if (binding.channelPanel.visibility == View.VISIBLE) {
                    binding.channelList.post(::focusCurrentListChannel)
                }
            }
    }

    private fun appendChannelDigit(digit: Int) {
        if (numberInput.length >= MAX_CHANNEL_DIGITS) numberInput = ""
        numberInput += digit
        binding.nowNumber.text = numberInput
        binding.nowChannel.setText(R.string.channel_number_entry)
        showInfoBar()
        numberInputJob?.cancel()
        numberInputJob = lifecycleScope.launch {
            delay(NUMBER_ENTRY_TIMEOUT_MS)
            commitChannelNumber()
        }
    }

    private fun commitChannelNumber() {
        val entered = numberInput
        numberInput = ""
        numberInputJob?.cancel()
        if (entered.isBlank()) return
        if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
            openIptvLibraryNumber(entered.toIntOrNull())
            return
        }
        ChannelNavigator.byNumber(playbackNavigationChannels(), entered)?.let { channel ->
            selectChannel(channel, recordHistory = shouldRecordNavigationHistory())
            if (binding.channelPanel.visibility == View.VISIBLE) hideChannelPanel()
        } ?: run { binding.nowChannel.setText(R.string.channel_not_found) }
    }

    private fun openPreviousChannel() {
        if (currentPlaybackUsesIptvLibrary) {
            zap(-1)
            return
        }
        ChannelNavigator.previousDistinct(
            playbackNavigationChannels(),
            playbackHistory.keys(),
            currentChannel?.sourceKey,
        )?.let(::selectChannel)
    }

    private fun playbackNavigationChannels(): List<LiveChannel> {
        return if (
            currentPlaybackUsesIptvLibrary &&
            iptvLibraryChannels.any { it.sourceKey == currentChannel?.sourceKey }
        ) iptvLibraryChannels else panelChannels()
    }

    private fun shouldRecordNavigationHistory(): Boolean = if (
        binding.channelPanel.visibility == View.VISIBLE
    ) {
        channelPanelContent == ChannelPanelContent.NORMAL
    } else {
        !currentPlaybackUsesIptvLibrary
    }

    private fun scheduleFocusedTune(channel: LiveChannel) {
        focusedListSourceKey = channel.sourceKey
        loadFocusedProgram(channel)
        focusedTuneJob?.cancel()
        if (!displayPreferences.channelFocusAutoTune) return
        focusedTuneJob = lifecycleScope.launch {
            delay(displayPreferences.channelFocusTuneDelayMillis.toLong())
            if (
                binding.channelPanel.visibility == View.VISIBLE &&
                focusedListSourceKey == channel.sourceKey
            ) {
                focusedAutoTunePreviousChannel = currentChannel
                    ?.takeIf { it.sourceKey != channel.sourceKey }
                focusedAutoTuneTargetKey = channel.sourceKey
                selectChannel(
                    channel,
                    recordHistory = channelPanelContent == ChannelPanelContent.NORMAL,
                )
            }
        }
    }

    private fun loadFocusedProgram(channel: LiveChannel) {
        if (currentPrograms[channel.id] != null) return
        focusedProgramJob?.cancel()
        focusedProgramJob = lifecycleScope.launch {
            delay(180L)
            val current = withContext(Dispatchers.IO) {
                runCatching { programRepository.nowAndNext(channel).current }.getOrNull()
            }
            if (focusedListSourceKey != channel.sourceKey || current == null) return@launch
            currentPrograms = currentPrograms + (channel.id to current)
            adapter.submitPrograms(currentPrograms)
        }
    }

    private fun loadVisiblePrograms() {
        if (binding.channelPanel.visibility != View.VISIBLE) return
        val layoutManager = binding.channelList.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition().takeIf { it >= 0 } ?: return
        val last = layoutManager.findLastVisibleItemPosition().takeIf { it >= first } ?: return
        val visibleChannels = panelChannels()
        val missing = (first..last).mapNotNull { position ->
            visibleChannels.getOrNull(position)?.takeIf { channel -> currentPrograms[channel.id] == null }
        }
        if (missing.isEmpty()) return
        visibleProgramsJob?.cancel()
        visibleProgramsJob = lifecycleScope.launch {
            val programs = withContext(Dispatchers.IO) {
                missing.mapNotNull { channel ->
                    runCatching { programRepository.nowAndNext(channel).current }
                        .getOrNull()
                        ?.let { current -> channel.id to current }
                }.toMap()
            }
            if (programs.isNotEmpty()) {
                currentPrograms = currentPrograms + programs
                adapter.submitPrograms(currentPrograms)
            }
        }
    }

    private fun loadChannelPrograms(loaded: List<LiveChannel>) {
        channelProgramsJob?.cancel()
        channelProgramsJob = lifecycleScope.launch {
            val loadedPrograms = withContext(Dispatchers.IO) {
                runCatching {
                    programRepository.currentPrograms(loaded.mapTo(mutableSetOf()) { it.id })
                }.getOrDefault(emptyMap())
            }
            currentPrograms = currentPrograms + loadedPrograms
            adapter.submitPrograms(currentPrograms)
        }
    }

    private fun loadPrograms(channel: LiveChannel, clearExisting: Boolean = true) {
        programJob?.cancel()
        if (clearExisting) {
            binding.currentProgram.visibility = View.GONE
            binding.nextProgram.visibility = View.GONE
            binding.programMeta.visibility = View.GONE
            binding.infoProgress.visibility = View.GONE
        }
        programJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { programRepository.nowAndNext(channel) }
            }
            if (currentChannel?.sourceKey != channel.sourceKey) return@launch
            val programs = result.onFailure { error ->
                debugLog.recordDebug(
                    "EPG_QUERY_FAILURE | channel=${channel.id}, " +
                        "${error.javaClass.name}: ${error.message}",
                )
            }.getOrNull() ?: return@launch
            programs.current?.let { current ->
                currentPrograms = currentPrograms + (channel.id to current)
                adapter.submitPrograms(currentPrograms)
            }
            debugLog.recordDebug(
                "EPG_QUERY_RESULT | channel=${channel.id}, " +
                    "current=${programs.current?.title}, next=${programs.next?.title}",
            )
            if (programs.current == null && programs.next == null) {
                binding.currentProgram.setText(R.string.no_program_information)
                binding.currentProgram.visibility = if (displayPreferences.showCurrentProgram) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                binding.programMeta.visibility = View.GONE
                binding.infoProgress.visibility = View.GONE
                binding.nextProgram.visibility = View.GONE
            }
            programs.current?.takeIf { it.title.isNotBlank() }?.let { current ->
                val title = current.title
                binding.currentProgram.text = title
                binding.currentProgram.visibility = if (displayPreferences.showCurrentProgram) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                binding.programTime.text = getString(
                    R.string.program_time_format,
                    timeFormat.format(Date(current.startTimeMillis)),
                    timeFormat.format(Date(current.endTimeMillis)),
                )
                val now = System.currentTimeMillis()
                val duration = (current.endTimeMillis - current.startTimeMillis).coerceAtLeast(1L)
                val progress = (((now - current.startTimeMillis) * 100L / duration)
                    .coerceIn(0L, 100L)).toInt()
                binding.infoProgress.progress = progress
                binding.programRemaining.text = getString(R.string.percent_value, progress)
                val showDetails = displayPreferences.showCurrentProgram
                binding.programMeta.visibility = if (showDetails) View.VISIBLE else View.GONE
                binding.infoProgress.visibility = if (showDetails) View.VISIBLE else View.GONE
            }
            programs.next?.takeIf { it.title.isNotBlank() }?.let { next ->
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                binding.nextProgram.text = getString(
                    R.string.next_program_with_time_format,
                    next.title,
                    timeFormat.format(Date(next.startTimeMillis)),
                    timeFormat.format(Date(next.endTimeMillis)),
                )
                binding.nextProgram.visibility = if (displayPreferences.showNextProgram) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
    }

    private fun showInfoBar() {
        binding.infoBar.visibility = View.VISIBLE
        infoBarJob?.cancel()
        if (channelPanelExpanded) return
        val seconds = displayPreferences.infoBarDurationSeconds
        if (seconds == 0) return
        infoBarJob = lifecycleScope.launch {
            delay(seconds * 1_000L)
            if (numberInput.isEmpty()) {
                binding.infoBar.visibility = View.GONE
            }
        }
    }

    private fun startIptvLiveHealthMonitor() {
        iptvLiveHealthJob?.cancel()
        val sourceKey = currentChannel?.sourceKey ?: return
        iptvLiveHealthJob = lifecycleScope.launch {
            while (currentChannel?.sourceKey == sourceKey) {
                delay(IPTV_LIVE_CHECK_INTERVAL_MS)
                if (iptvPlayback.catchUpToLive(IPTV_MAX_LIVE_OFFSET_MS)) {
                    debugLog.recordDebug("IPTV_LIVE_EDGE_CORRECTED | channel=$sourceKey")
                }
            }
        }
    }

    private fun handleIptvMediaKey(keyCode: Int) {
        when (currentIptvContentKind) {
            IptvContentKind.LIVE -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_NEXT -> iptvPlayback.goLive()
                    else -> return
                }
                showIptvPlaybackControls(R.string.iptv_returned_live)
            }
            IptvContentKind.VOD -> {
                when (keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> iptvPlayback.togglePlayPause()
                    KeyEvent.KEYCODE_MEDIA_PLAY -> iptvPlayback.play()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> iptvPlayback.pause()
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                    KeyEvent.KEYCODE_MEDIA_NEXT -> iptvPlayback.seekBy(IPTV_VOD_SEEK_STEP_MS)
                    KeyEvent.KEYCODE_MEDIA_REWIND,
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> iptvPlayback.seekBy(-IPTV_VOD_SEEK_STEP_MS)
                    KeyEvent.KEYCODE_MEDIA_STOP -> iptvPlayback.stopVod()
                    else -> return
                }
                showIptvPlaybackControls(
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) R.string.iptv_vod_stopped
                    else if (iptvPlayback.playbackSnapshot().isPlaying) {
                        R.string.iptv_vod_playing
                    } else {
                        R.string.iptv_vod_paused
                    },
                )
            }
            IptvContentKind.UNKNOWN -> {
                if (keyCode !in setOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
                    return
                }
                iptvPlayback.togglePlayPause()
            }
        }
    }

    private fun showIptvPlaybackControls(stateText: Int) {
        binding.iptvPlaybackControls.visibility = View.VISIBLE
        binding.iptvControlTitle.text = currentChannel?.displayName.orEmpty()
        binding.iptvControlState.setText(stateText)
        val vod = currentIptvContentKind == IptvContentKind.VOD
        binding.iptvControlProgress.visibility = if (vod) View.VISIBLE else View.GONE
        binding.iptvControlPosition.visibility = if (vod) View.VISIBLE else View.GONE
        binding.iptvControlHint.visibility = if (vod) View.VISIBLE else View.GONE
        iptvControlsJob?.cancel()
        iptvControlsJob = lifecycleScope.launch {
            val startedAt = System.currentTimeMillis()
            while (System.currentTimeMillis() - startedAt < IPTV_CONTROL_TIMEOUT_MS) {
                if (vod) updateVodPlaybackControls()
                delay(500L)
            }
            binding.iptvPlaybackControls.visibility = View.GONE
        }
    }

    private fun updateVodPlaybackControls() {
        val state = iptvPlayback.playbackSnapshot()
        val duration = state.durationMillis.coerceAtLeast(1L)
        binding.iptvControlProgress.progress =
            ((state.positionMillis * 1_000L / duration).coerceIn(0L, 1_000L)).toInt()
        binding.iptvControlPosition.text = getString(
            R.string.program_time_format,
            formatPlaybackTime(state.positionMillis),
            formatPlaybackTime(state.durationMillis),
        )
    }

    private fun formatPlaybackTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    private fun applyDisplayPreferences() {
        displayPreferences = displayPreferencesStore.load()
        adapter.applyRowOptions(
            ChannelRowOptions(
                showLogo = displayPreferences.showChannelLogo,
                showProgram = displayPreferences.showChannelProgram,
                showProgress = displayPreferences.showChannelProgress,
                showSourceBadge = displayPreferences.showChannelSourceBadge,
            ),
        )
        binding.infoBar.background.mutate().alpha =
            (displayPreferences.infoBarOpacityPercent * 255 / 100).coerceIn(0, 255)
        binding.channelPanel.background.mutate().alpha =
            (displayPreferences.channelPanelOpacityPercent * 255 / 100).coerceIn(0, 255)

        (binding.channelPanel.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = if (displayPreferences.channelPanelSide == ChannelPanelSide.LEFT) {
                Gravity.START
            } else {
                Gravity.END
            }
            binding.channelPanel.layoutParams = this
        }
        applyPanelGeometry()
        if (!displayPreferences.showCurrentProgram) binding.currentProgram.visibility = View.GONE
        if (!displayPreferences.showNextProgram) binding.nextProgram.visibility = View.GONE
        if (!displayPreferences.subtitlesEnabled) playback.selectSubtitle(null)
    }

    private fun applyPreferredTracks() {
        displayPreferences = displayPreferencesStore.load()
        val audio = playback.audioTracks()
        val subtitles = playback.subtitleTracks()
        currentChannel?.let { channel ->
            val tracks = playback.allTracks()
            updateTechnicalBadges(channel, tracks)
            when {
                channel.isRadioChannel() -> updateAudioOnlyPanel(channel, true)
                else -> updateAudioOnlyPanel(channel, false)
            }
        }
        val preferredAudio = displayPreferences.preferredAudioLanguage
        if (preferredAudio != null) {
            audio.firstOrNull { it.language.equals(preferredAudio, ignoreCase = true) }
                ?.let { playback.selectAudio(it.id) }
        }

        if (!displayPreferences.subtitlesEnabled) {
            playback.selectSubtitle(null)
            return
        }
        val preferredSubtitle = displayPreferences.preferredSubtitleLanguage
        val track = subtitles.firstOrNull {
            it.language.equals(preferredSubtitle, ignoreCase = true)
        } ?: subtitles.firstOrNull()
        playback.selectSubtitle(track?.id)
    }

    private fun updateTechnicalBadges(channel: LiveChannel, tracks: List<TvTrackInfo>) {
        val videoTrack = tracks.firstOrNull { it.type == TvTrackInfo.TYPE_VIDEO }
        val width = videoTrack?.videoWidth ?: 0
        val format = channel.videoFormat.orEmpty().uppercase(Locale.ROOT)
        val channelName = channel.displayName.uppercase(Locale.ROOT)
        val radio = channel.isRadioChannel()
        val quality = when {
            radio -> null
            width >= 3_840 || "2160" in format || "4320" in format ||
                "4K" in channelName || "UHD" in channelName -> "4K"
            width >= 1_280 || "720" in format || "1080" in format ||
                "HD" in channelName -> "HD"
            else -> "SD"
        }
        binding.qualityBadge.visibility = View.GONE
        binding.radioBadge.visibility = View.GONE
        if (radio) {
            binding.radioBadge.visibility = View.VISIBLE
        } else {
            binding.qualityBadge.text = quality
            binding.qualityBadge.visibility = View.VISIBLE
        }
        binding.sourceBadgeIcon.setImageResource(
            if (channel.source == LiveChannel.Source.IPTV) {
                R.drawable.ic_source_iptv
            } else {
                R.drawable.ic_source_tif
            },
        )
        binding.lockBadge.visibility = if (
            channel.encrypted || channel.locked || parentalControlStore.isLocked(channel.sourceKey)
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val audioTracks = tracks.filter { it.type == TvTrackInfo.TYPE_AUDIO }
        val subtitleTracks = tracks.filter { it.type == TvTrackInfo.TYPE_SUBTITLE }
        val trackMetadata = tracks.joinToString(" ") { track ->
            "${track.description ?: ""} ${track.extra}"
        }.lowercase(Locale.ROOT)
        val dolby = listOf("dolby", "ac3", "ac-3", "eac3", "e-ac-3")
            .any(trackMetadata::contains)
        binding.dolbyBadge.visibility = if (dolby) View.VISIBLE else View.GONE

        binding.audioBadge.text = audioTracks.firstOrNull()?.language?.let(::displayLanguage)
            ?: audioTracks.size.takeIf { it > 0 }?.toString()
        binding.audioBadge.visibility = if (audioTracks.isEmpty() || dolby) {
            View.GONE
        } else {
            View.VISIBLE
        }
        binding.subtitleBadge.text = subtitleTracks.firstOrNull()?.language
            ?.let(::displayLanguage)
            ?: subtitleTracks.size.takeIf { it > 0 }?.toString()
        binding.subtitleBadge.visibility = if (subtitleTracks.isEmpty()) View.GONE else View.VISIBLE
        val hasTeletext = listOf("teletext", "teletekst", "txt")
            .any(trackMetadata::contains)
        binding.txtBadge.visibility = if (hasTeletext) View.VISIBLE else View.GONE

        val activeSlots = booleanArrayOf(
            true,
            true,
            audioTracks.isNotEmpty(),
            subtitleTracks.isNotEmpty(),
            hasTeletext,
            channel.encrypted || channel.locked ||
                parentalControlStore.isLocked(channel.sourceKey),
        )
        val slots = listOf(
            binding.techSlotSource,
            binding.techSlotQualityBadge,
            binding.techSlotAudio,
            binding.techSlotSubtitle,
            binding.techSlotTxt,
            binding.techSlotLock,
        )
        slots.forEachIndexed { index, slot ->
            slot.visibility = if (activeSlots[index]) View.VISIBLE else View.GONE
        }
    }

    private fun updateAudioOnlyPanel(channel: LiveChannel, audioOnly: Boolean) {
        binding.audioOnlyPanel.visibility = if (audioOnly) View.VISIBLE else View.GONE
        if (!audioOnly) return
        val radio = channel.isRadioChannel()
        binding.audioOnlyIcon.setImageResource(
            if (radio) R.drawable.ic_radio else R.drawable.ic_tv_off,
        )
        binding.audioOnlyIcon.contentDescription = getString(
            if (radio) R.string.radio_broadcast else R.string.video_unavailable,
        )
        binding.audioOnlySoundIcon.visibility = if (radio) View.GONE else View.VISIBLE
        binding.audioOnlyChannel.text = channel.displayName
        binding.audioOnlyMessage.setText(
            if (radio) R.string.radio_broadcast else R.string.audio_only_program,
        )
    }

    private fun displayLanguage(code: String): String = Locale.forLanguageTag(code)
        .getDisplayLanguage(Locale("tr"))
        .takeIf(String::isNotBlank)
        ?: code.uppercase(Locale.ROOT)

    private fun showAudioTracks() {
        val tracks = playback.audioTracks()
        if (tracks.isEmpty()) {
            showEditorError(R.string.tracks_not_available)
            return
        }
        val selected = playback.selectedTrackId(TvTrackInfo.TYPE_AUDIO)
        AlertDialog.Builder(this)
            .setTitle(R.string.audio_tracks)
            .setSingleChoiceItems(
                tracks.map(::trackLabel).toTypedArray(),
                tracks.indexOfFirst { it.id == selected },
            ) { dialog, which ->
                val track = tracks[which]
                playback.selectAudio(track.id)
                track.language?.let { displayPreferencesStore.updateTrackPreferences(audioLanguage = it) }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showSubtitleTracks() {
        val tracks = playback.subtitleTracks()
        val selected = playback.selectedTrackId(TvTrackInfo.TYPE_SUBTITLE)
        val labels = buildList {
            add(getString(R.string.subtitles_off))
            addAll(tracks.map(::trackLabel))
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.subtitle_tracks)
            .setSingleChoiceItems(labels, tracks.indexOfFirst { it.id == selected } + 1) {
                    dialog, which ->
                if (which == 0) {
                    playback.selectSubtitle(null)
                    displayPreferencesStore.updateTrackPreferences(subtitlesEnabled = false)
                } else {
                    val track = tracks[which - 1]
                    playback.selectSubtitle(track.id)
                    displayPreferencesStore.updateTrackPreferences(
                        subtitlesEnabled = true,
                        subtitleLanguage = track.language,
                    )
                }
                displayPreferences = displayPreferencesStore.load()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun trackLabel(track: TvTrackInfo): String {
        val language = track.language?.takeIf(String::isNotBlank)?.let { code ->
            Locale.forLanguageTag(code).getDisplayLanguage(Locale("tr"))
                .takeIf(String::isNotBlank)
                ?: code.uppercase(Locale.ROOT)
        } ?: getString(R.string.unknown_language)
        val description = track.description?.toString()?.takeIf(String::isNotBlank)
        return listOfNotNull(language, description).distinct().joinToString(" · ")
    }

    private fun openDisplaySettings() {
        displaySettings.launch(Intent(this, DisplaySettingsActivity::class.java))
    }

    private fun enterTvPictureInPicture() {
        if (currentChannel == null || isInPictureInPictureMode) return
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            toggleInternalMiniPlayer()
            return
        }

        hideChromeForPictureInPicture()
        val playerView = if (binding.iptvPlayerView.visibility == View.VISIBLE) {
            binding.iptvPlayerView
        } else {
            binding.tvView
        }
        val sourceRect = Rect().takeIf { playerView.getGlobalVisibleRect(it) }
        val parameters = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply { sourceRect?.let(::setSourceRectHint) }
            .build()
        val entered = runCatching { enterPictureInPictureMode(parameters) }
            .onFailure { error ->
                debugLog.recordDebug(
                    "PIP_ENTER_FAILURE | ${error.javaClass.name}: ${error.message}",
                )
            }
            .getOrDefault(false)
        debugLog.recordDebug("PIP_ENTER | success=$entered")
        if (!entered) restoreChromeAfterPictureInPicture()
    }

    private fun toggleInternalMiniPlayer() {
        internalMiniPlayerActive = !internalMiniPlayerActive
        val width = if (internalMiniPlayerActive) {
            (resources.displayMetrics.widthPixels * INTERNAL_MINI_WIDTH_FRACTION).toInt()
        } else {
            FrameLayout.LayoutParams.MATCH_PARENT
        }
        val height = if (internalMiniPlayerActive) {
            (width * 9f / 16f).toInt()
        } else {
            FrameLayout.LayoutParams.MATCH_PARENT
        }
        listOf(binding.tvView, binding.iptvPlayerView).forEach { player ->
            player.layoutParams = (player.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = width
                this.height = height
                gravity = if (internalMiniPlayerActive) {
                    Gravity.TOP or Gravity.END
                } else {
                    Gravity.FILL
                }
                val margin = if (internalMiniPlayerActive) 24.dp else 0
                setMargins(margin, margin, margin, margin)
            }
            player.elevation = if (internalMiniPlayerActive) 12.dp.toFloat() else 0f
        }
        if (internalMiniPlayerActive) {
            hideChromeForPictureInPicture()
        } else {
            restoreChromeAfterPictureInPicture()
        }
        debugLog.recordDebug("INTERNAL_MINI_PLAYER | active=$internalMiniPlayerActive")
    }

    private fun hideChromeForPictureInPicture() {
        infoBarJob?.cancel()
        numberInputJob?.cancel()
        numberInput = ""
        binding.channelPanel.visibility = View.GONE
        binding.infoBar.visibility = View.GONE
        binding.statusPanel.visibility = View.GONE
        binding.audioOnlyPanel.visibility = View.GONE
    }

    private fun restoreChromeAfterPictureInPicture() {
        currentChannel?.let { channel ->
            updateAudioOnlyPanel(channel, channel.isRadioChannel())
            showInfoBar()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        debugLog.recordDebug("PIP_MODE_CHANGED | active=$isInPictureInPictureMode")
        if (isInPictureInPictureMode) {
            hideChromeForPictureInPicture()
        } else if (!isFinishing) {
            restoreChromeAfterPictureInPicture()
        }
    }

    private fun openProgramGuide() {
        hideChannelPanel()
        infoBarJob?.cancel()
        binding.infoBar.visibility = View.GONE
        programGuide.launch(
            Intent(this, ProgramGuideActivity::class.java).putExtra(
                ProgramGuideActivity.EXTRA_CURRENT_SOURCE_KEY,
                currentChannel?.sourceKey,
            ),
        )
    }

    private fun showIptvLibraryFilterDialog() {
        focusedTuneJob?.cancel()
        channelPanelJob?.cancel()
        lifecycleScope.launch {
            val sources = withContext(Dispatchers.IO) { iptvRepository.sources() }
            if (sources.isEmpty()) {
                val emptyDialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle(R.string.iptv_library)
                    .setMessage(R.string.iptv_library_empty)
                    .setPositiveButton(android.R.string.ok, null)
                    .create()
                emptyDialog.setOnDismissListener {
                    if (binding.channelPanel.visibility == View.VISIBLE) {
                        scheduleChannelPanelClose()
                    }
                }
                emptyDialog.show()
                return@launch
            }

            val preferences = getSharedPreferences(IPTV_LIBRARY_FILTER_PREFS, MODE_PRIVATE)
            val savedSourceId = preferences.getLong(IPTV_LIBRARY_SOURCE_ID, -1L)
                .takeUnless { it == -1L }
                ?.takeIf { saved -> sources.any { it.source.id == saved } }
            val initialSourceId = savedSourceId ?: sources.maxOf { it.source.id }
            val savedType = runCatching {
                IptvLibraryContentType.valueOf(
                    preferences.getString(
                        IPTV_LIBRARY_CONTENT_TYPE,
                        IptvLibraryContentType.ALL.name,
                    ).orEmpty(),
                )
            }.getOrDefault(IptvLibraryContentType.ALL)
            val savedCategory = preferences.getString(IPTV_LIBRARY_CATEGORY, null)

            val sourceIds = sources.map { it.source.id }
            val sourceNames = sources.map { it.source.name }
            val contentTypes = IptvLibraryContentType.entries
            val contentTypeNames = listOf(
                getString(R.string.iptv_library_all_content),
                getString(R.string.iptv_library_live),
                getString(R.string.iptv_library_vod),
            )
            val container = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24.dp, 8.dp, 24.dp, 4.dp)
            }
            fun labeledSpinner(label: Int): Spinner {
                container.addView(TextView(this@MainActivity).apply {
                    setText(label)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    textSize = 13f
                    setPadding(0, 12.dp, 0, 4.dp)
                })
                return Spinner(this@MainActivity).also(container::addView)
            }
            val sourceSpinner = labeledSpinner(R.string.iptv_library_sources)
            val typeSpinner = labeledSpinner(R.string.iptv_library_content_type)
            val categorySpinner = labeledSpinner(R.string.iptv_library_categories)
            sourceSpinner.adapter = dialogSpinnerAdapter(sourceNames)
            typeSpinner.adapter = dialogSpinnerAdapter(contentTypeNames)
            sourceSpinner.setSelection(sourceIds.indexOf(initialSourceId).coerceAtLeast(0))
            typeSpinner.setSelection(contentTypes.indexOf(savedType).coerceAtLeast(0))

            var categories: List<String?> = listOf(null) + withContext(Dispatchers.IO) {
                iptvRepository.sourceCategories(initialSourceId)
            }
            fun refreshCategories(preferred: String? = null) {
                categorySpinner.adapter = dialogSpinnerAdapter(
                    categories.map { it ?: getString(R.string.all_iptv_categories) },
                )
                categorySpinner.setSelection(categories.indexOf(preferred).coerceAtLeast(0))
            }
            var sourceLoadGeneration = 0
            val sourceListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val sourceId = sourceIds.getOrNull(position) ?: return
                    val generation = ++sourceLoadGeneration
                    lifecycleScope.launch {
                        val loaded = withContext(Dispatchers.IO) {
                            iptvRepository.sourceCategories(sourceId)
                        }
                        if (generation == sourceLoadGeneration) {
                            categories = listOf(null) + loaded
                            refreshCategories()
                            debugLog.recordDebug(
                                "IPTV_LIBRARY_CATEGORIES_LOADED | source=$sourceId, " +
                                    "count=${loaded.size}",
                            )
                        }
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
            sourceSpinner.onItemSelectedListener = sourceListener
            refreshCategories(savedCategory)

            val dialog = AlertDialog.Builder(this@MainActivity)
                .setTitle(R.string.iptv_library_filter_title)
                .setView(container)
                .setPositiveButton(R.string.apply, null)
                .setNeutralButton(R.string.main_channel_list, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                    val sourceIndex = sourceSpinner.selectedItemPosition
                    val sourceId = sourceIds.getOrNull(sourceIndex)
                        ?: return@setOnClickListener
                    val type = contentTypes.getOrElse(typeSpinner.selectedItemPosition) {
                        IptvLibraryContentType.ALL
                    }
                    val category = categories.getOrNull(categorySpinner.selectedItemPosition)
                    preferences.edit()
                        .putLong(IPTV_LIBRARY_SOURCE_ID, sourceId)
                        .putString(IPTV_LIBRARY_CONTENT_TYPE, type.name)
                        .putString(IPTV_LIBRARY_CATEGORY, category)
                        .apply()
                    applyIptvLibraryFilter(
                        sourceId,
                        sourceNames[sourceIndex],
                        type,
                        category,
                    )
                    dialog.dismiss()
                }
                dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                    applyChannelFilter(
                        showFavorites = false,
                        source = ChannelSourceFilter.ALL,
                    )
                    dialog.dismiss()
                }
            }
            dialog.setOnDismissListener {
                if (binding.channelPanel.visibility == View.VISIBLE) scheduleChannelPanelClose()
            }
            dialog.show()
        }
    }

    private fun openSavedIptvLibrary() {
        focusedTuneJob?.cancel()
        lifecycleScope.launch {
            val sources = withContext(Dispatchers.IO) { iptvRepository.sources() }
            if (sources.isEmpty()) {
                showIptvLibraryFilterDialog()
                return@launch
            }
            val preferences = getSharedPreferences(IPTV_LIBRARY_FILTER_PREFS, MODE_PRIVATE)
            val savedSourceId = preferences.getLong(IPTV_LIBRARY_SOURCE_ID, -1L)
                .takeUnless { it == -1L }
                ?.takeIf { saved -> sources.any { it.source.id == saved } }
            val sourceId = savedSourceId ?: sources.maxOf { it.source.id }
            val source = sources.first { it.source.id == sourceId }
            if (source.channelCount == 0) {
                showIptvLibraryFilterDialog()
                return@launch
            }
            val contentType = runCatching {
                IptvLibraryContentType.valueOf(
                    preferences.getString(
                        IPTV_LIBRARY_CONTENT_TYPE,
                        IptvLibraryContentType.ALL.name,
                    ).orEmpty(),
                )
            }.getOrDefault(IptvLibraryContentType.ALL)
            val category = preferences.getString(IPTV_LIBRARY_CATEGORY, null)
                ?.takeIf { savedSourceId != null }
            val sourceName = source.source.name
            preferences.edit().putLong(IPTV_LIBRARY_SOURCE_ID, sourceId).apply()
            debugLog.recordDebug(
                "IPTV_LIBRARY_OPEN | source=$sourceId, count=${source.channelCount}",
            )
            applyIptvLibraryFilter(
                sourceId,
                sourceName,
                contentType,
                category,
            )
        }
    }

    private fun openIptvEditor() {
        focusedTuneJob?.cancel()
        iptvSources.launch(Intent(this, IptvSourcesActivity::class.java))
    }

    private fun dialogSpinnerAdapter(items: List<String>) = ArrayAdapter(
        this,
        android.R.layout.simple_spinner_dropdown_item,
        items,
    )

    private fun applyIptvLibraryFilter(
        sourceId: Long,
        sourceName: String,
        contentType: IptvLibraryContentType,
        category: String?,
    ) {
        channelPanelContent = ChannelPanelContent.IPTV_LIBRARY
        iptvLibraryLoadJob?.cancel()
        iptvLibrarySourceId = sourceId
        iptvLibraryContentType = contentType
        iptvLibraryCategory = category
        iptvLibraryOffset = 0
        iptvLibraryTotalCount = 0
        iptvLibraryExhausted = false
        iptvLibraryChannels = emptyList()
        binding.sourceFilterRow.visibility = View.GONE
        binding.advancedFilterRow.visibility = View.GONE
        updateChannelListModeIcon()
        updateChannelActionLabels()
        binding.channelListTitle.text = listOfNotNull(
            sourceName,
            when (contentType) {
                IptvLibraryContentType.ALL -> null
                IptvLibraryContentType.LIVE -> getString(R.string.iptv_library_live_short)
                IptvLibraryContentType.VOD -> getString(R.string.iptv_library_vod)
            },
            category,
        ).joinToString(" · ")
        adapter.submitList(iptvLibraryChannels)
        adapter.submitPrograms(currentPrograms)
        updateIptvLibraryCount()
        lifecycleScope.launch {
            iptvLibraryTotalCount = withContext(Dispatchers.IO) {
                iptvRepository.libraryChannelCount(
                    sourceId,
                    category,
                    contentType.name,
                )
            }
            updateIptvLibraryCount()
            loadNextIptvLibraryPage()
        }
        if (binding.channelPanel.visibility != View.VISIBLE) {
            showChannelPanel(expanded = false)
        } else {
            focusCurrentListChannel()
            scheduleChannelPanelClose()
        }
    }

    private fun loadNextIptvLibraryPage() {
        if (
            channelPanelContent != ChannelPanelContent.IPTV_LIBRARY ||
            iptvLibraryExhausted ||
            iptvLibraryLoadJob?.isActive == true
        ) return
        val sourceId = iptvLibrarySourceId ?: return
        iptvLibraryLoadJob = lifecycleScope.launch {
            val accepted = withContext(Dispatchers.IO) {
                iptvRepository.libraryLiveChannelsPage(
                    sourceId,
                    iptvLibraryCategory,
                    iptvLibraryContentType.name,
                    IPTV_LIBRARY_PAGE_SIZE,
                    iptvLibraryOffset,
                )
            }
            iptvLibraryOffset += accepted.size
            iptvLibraryExhausted = iptvLibraryOffset >= iptvLibraryTotalCount ||
                accepted.size < IPTV_LIBRARY_PAGE_SIZE
            if (
                channelPanelContent != ChannelPanelContent.IPTV_LIBRARY ||
                iptvLibrarySourceId != sourceId
            ) return@launch
            val start = iptvLibraryChannels.size
            val numbered = accepted.mapIndexed { index, channel ->
                channel.copy(displayNumber = (start + index + 1).toString())
            }
            iptvLibraryChannels = iptvLibraryChannels + numbered
            adapter.appendItems(numbered)
            currentChannel?.let { adapter.select(it.sourceKey) }
            updateIptvLibraryCount()
            debugLog.recordDebug(
                "IPTV_LIBRARY_PAGE | source=$sourceId, offset=$iptvLibraryOffset, " +
                    "added=${numbered.size}, loaded=${iptvLibraryChannels.size}, " +
                    "total=$iptvLibraryTotalCount, " +
                    "finished=$iptvLibraryExhausted",
            )
            if (start == 0) {
                focusCurrentListChannel()
                binding.channelList.post {
                    val manager = binding.channelList.layoutManager as? LinearLayoutManager
                    if (
                        !iptvLibraryExhausted &&
                        (manager?.findLastVisibleItemPosition() ?: -1) >=
                        adapter.itemCount - IPTV_LIBRARY_PREFETCH_DISTANCE
                    ) {
                        loadNextIptvLibraryPage()
                    }
                }
            }
            if (numbered.isEmpty() && !iptvLibraryExhausted) {
                binding.channelList.post(::loadNextIptvLibraryPage)
            }
        }
    }

    private fun updateIptvLibraryCount() {
        binding.channelCount.text = getString(
            R.string.iptv_library_loaded_count,
            iptvLibraryChannels.size,
            iptvLibraryTotalCount,
        )
    }

    private fun openIptvLibraryNumber(number: Int?) {
        val sourceId = iptvLibrarySourceId ?: return
        val index = number?.minus(1) ?: return
        if (index !in 0 until iptvLibraryTotalCount) {
            binding.nowChannel.setText(R.string.channel_not_found)
            return
        }
        lifecycleScope.launch {
            val channel = withContext(Dispatchers.IO) {
                iptvRepository.libraryLiveChannelsPage(
                    sourceId,
                    iptvLibraryCategory,
                    iptvLibraryContentType.name,
                    1,
                    index,
                ).firstOrNull()
            } ?: return@launch
            selectChannel(channel.copy(displayNumber = number.toString()), recordHistory = false)
            hideChannelPanel()
        }
    }

    private fun showIptvLibraryBoundary(last: Boolean) {
        val sourceId = iptvLibrarySourceId ?: return
        if (iptvLibraryTotalCount == 0 || iptvLibraryLoadJob?.isActive == true) return
        iptvLibraryLoadJob = lifecycleScope.launch {
            val offset = if (last) {
                ((iptvLibraryTotalCount - 1) / IPTV_LIBRARY_PAGE_SIZE) * IPTV_LIBRARY_PAGE_SIZE
            } else {
                0
            }
            val page = withContext(Dispatchers.IO) {
                iptvRepository.libraryLiveChannelsPage(
                    sourceId,
                    iptvLibraryCategory,
                    iptvLibraryContentType.name,
                    IPTV_LIBRARY_PAGE_SIZE,
                    offset,
                )
            }
            iptvLibraryOffset = offset + page.size
            iptvLibraryExhausted = iptvLibraryOffset >= iptvLibraryTotalCount
            iptvLibraryChannels = page.mapIndexed { index, channel ->
                channel.copy(displayNumber = (offset + index + 1).toString())
            }
            adapter.submitList(iptvLibraryChannels)
            updateIptvLibraryCount()
            val target = if (last) iptvLibraryChannels.lastIndex else 0
            binding.channelList.scrollToPosition(target.coerceAtLeast(0))
            binding.channelList.post {
                binding.channelList.findViewHolderForAdapterPosition(target)
                    ?.itemView
                    ?.requestFocus()
            }
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun prepareIptvGrid() {
        repeat(4) { index ->
            val cell = layoutInflater.inflate(
                R.layout.view_iptv_grid_cell,
                binding.iptvGrid,
                false,
            ) as FrameLayout
            val playerView = cell.findViewById<PlayerView>(R.id.grid_player)
            val label = cell.findViewById<TextView>(R.id.grid_label)
            cell.layoutParams = GridLayout.LayoutParams(
                GridLayout.spec(index / 2, 1, 1f),
                GridLayout.spec(index % 2, 1, 1f),
            ).apply {
                width = 0
                height = 0
                setMargins(3.dp, 3.dp, 3.dp, 3.dp)
            }
            binding.iptvGrid.addView(cell)
            gridCells += cell
            gridLabels += label
            gridControllers += IptvPlaybackController(this, playerView).apply {
                onPlaybackError = { error ->
                    debugLog.recordDebug(
                        "IPTV_GRID_FAILURE | cell=$index, ${error.errorCodeName}: ${error.message}",
                    )
                }
            }
        }
    }

    private fun availableLiveIptvChannels(): List<LiveChannel> {
        val candidates = if (
            channelPanelContent == ChannelPanelContent.IPTV_LIBRARY &&
            iptvLibraryChannels.isNotEmpty()
        ) {
            iptvLibraryChannels
        } else {
            channels
        }
        return candidates.asSequence()
            .filter { it.source == LiveChannel.Source.IPTV }
            .filter { it.iptvContentType != IptvLibraryContentType.VOD.name }
            .distinctBy { it.sourceKey }
            .toList()
    }

    private fun showIptvPipPicker() {
        val choices = availableLiveIptvChannels()
        if (choices.isEmpty()) {
            Toast.makeText(this, R.string.iptv_pip_no_channels, Toast.LENGTH_LONG).show()
            return
        }
        channelPanelJob?.cancel()
        AlertDialog.Builder(this)
            .setTitle(R.string.iptv_pip_title)
            .setItems(choices.map { it.displayName }.toTypedArray()) { _, which ->
                startIptvOverlay(choices[which])
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun startIptvOverlay(channel: LiveChannel) {
        if (currentChannel?.source != LiveChannel.Source.TIF) return
        if (multiViewActive) stopMultiView()
        if (iptvGridActive) stopIptvGrid(resumePrevious = true)
        iptvOverlayActive = true
        secondaryPlayback.stop()
        binding.secondaryTvView.visibility = View.GONE
        val width = (resources.displayMetrics.widthPixels * 0.32f).toInt()
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
        secondaryIptvPlayback.play(channel)
        secondaryIptvPlayback.setMuted(true)
        hideChannelPanel()
        debugLog.recordDebug("IPTV_OVERLAY_START | channel=${channel.sourceKey}")
    }

    private fun stopIptvOverlay() {
        if (!iptvOverlayActive) return
        iptvOverlayActive = false
        secondaryIptvPlayback.stop()
        binding.secondaryIptvPlayerView.visibility = View.GONE
        debugLog.recordDebug("IPTV_OVERLAY_STOP")
    }

    private fun showIptvGridPicker() {
        val choices = availableLiveIptvChannels()
        if (choices.isEmpty()) {
            Toast.makeText(this, R.string.iptv_grid_no_channels, Toast.LENGTH_LONG).show()
            return
        }
        channelPanelJob?.cancel()
        val checked = BooleanArray(choices.size) { index ->
            choices[index].sourceKey in gridSelectedKeys ||
                (gridSelectedKeys.isEmpty() && choices[index].sourceKey == currentChannel?.sourceKey)
        }
        lateinit var dialog: AlertDialog
        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.iptv_grid_title)
            .setMultiChoiceItems(
                choices.map { it.displayName }.toTypedArray(),
                checked,
            ) { target, which, isChecked ->
                checked[which] = isChecked
                if (isChecked && checked.count { it } > 4) {
                    checked[which] = false
                    (target as AlertDialog).listView.setItemChecked(which, false)
                    Toast.makeText(this, R.string.iptv_grid_maximum, Toast.LENGTH_SHORT).show()
                }
            }
            .setPositiveButton(R.string.iptv_grid_start_green, null)
            .setNegativeButton(R.string.close_red, null)
            .create()
        fun startSelectedGrid() {
            val selected = choices.filterIndexed { index, _ -> checked[index] }.take(4)
            if (selected.isEmpty()) {
                Toast.makeText(this, R.string.iptv_grid_empty, Toast.LENGTH_SHORT).show()
            } else {
                gridSelectedKeys.clear()
                gridSelectedKeys += selected.map { it.sourceKey }
                dialog.dismiss()
                startIptvGrid(selected)
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                startSelectedGrid()
            }
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_PROG_GREEN -> true.also { startSelectedGrid() }
                KeyEvent.KEYCODE_PROG_RED -> true.also { dialog.dismiss() }
                else -> false
            }
        }
        dialog.show()
    }

    private fun startIptvGrid(selected: List<LiveChannel>) {
        if (selected.isEmpty()) return
        if (multiViewActive) stopMultiView()
        stopIptvOverlay()
        gridReturnChannel = currentChannel
        gridChannels = selected.take(4)
        gridActiveIndex = 0
        gridFullscreenIndex = null
        iptvGridActive = true
        playback.stop()
        iptvPlayback.stop()
        binding.tvView.visibility = View.GONE
        binding.iptvPlayerView.visibility = View.GONE
        binding.audioOnlyPanel.visibility = View.GONE
        binding.parentalLockPanel.visibility = View.GONE
        binding.statusPanel.visibility = View.GONE
        hideChannelPanel()
        binding.infoBar.visibility = View.GONE
        binding.iptvGrid.visibility = View.VISIBLE
        renderIptvGrid()
        debugLog.recordDebug(
            "IPTV_GRID_START | channels=${gridChannels.joinToString { it.sourceKey }}",
        )
    }

    private fun renderIptvGrid() {
        gridControllers.forEachIndexed { index, controller ->
            val channel = gridChannels.getOrNull(index)
            gridCells[index].visibility = if (channel == null) View.INVISIBLE else View.VISIBLE
            if (channel == null) {
                controller.stop()
                gridLabels[index].text = ""
            } else {
                gridLabels[index].text = channel.displayName
                controller.play(channel)
            }
        }
        applyIptvGridLayout()
        updateIptvGridFocus()
    }

    private fun applyIptvGridLayout() {
        gridCells.forEachIndexed { index, cell ->
            val fullscreen = gridFullscreenIndex
            cell.visibility = when {
                index !in gridChannels.indices -> View.INVISIBLE
                fullscreen == null || fullscreen == index -> View.VISIBLE
                else -> View.GONE
            }
            cell.layoutParams = GridLayout.LayoutParams(
                if (fullscreen == index) {
                    GridLayout.spec(0, 2, 1f)
                } else {
                    GridLayout.spec(index / 2, 1, 1f)
                },
                if (fullscreen == index) {
                    GridLayout.spec(0, 2, 1f)
                } else {
                    GridLayout.spec(index % 2, 1, 1f)
                },
            ).apply {
                width = 0
                height = 0
                setMargins(3.dp, 3.dp, 3.dp, 3.dp)
            }
        }
    }

    private fun updateIptvGridFocus() {
        gridCells.forEachIndexed { index, cell ->
            cell.background = ContextCompat.getDrawable(
                this,
                if (index == gridActiveIndex) {
                    R.drawable.bg_grid_cell_selected
                } else {
                    R.drawable.bg_grid_cell
                },
            )
            gridControllers[index].setMuted(index != gridActiveIndex)
        }
    }

    private fun moveIptvGridFocus(keyCode: Int) {
        if (gridFullscreenIndex != null) return
        val candidate = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> if (gridActiveIndex % 2 == 1) gridActiveIndex - 1 else -1
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (gridActiveIndex % 2 == 0) gridActiveIndex + 1 else -1
            KeyEvent.KEYCODE_DPAD_UP -> gridActiveIndex - 2
            KeyEvent.KEYCODE_DPAD_DOWN -> gridActiveIndex + 2
            else -> -1
        }
        if (candidate in gridChannels.indices) {
            gridActiveIndex = candidate
            updateIptvGridFocus()
        }
    }

    private fun stopIptvGrid(resumePrevious: Boolean) {
        if (!iptvGridActive) return
        val previous = gridReturnChannel
        iptvGridActive = false
        gridLongPressJob?.cancel()
        gridFullscreenIndex = null
        gridControllers.forEach(IptvPlaybackController::stop)
        binding.iptvGrid.visibility = View.GONE
        gridChannels = emptyList()
        gridReturnChannel = null
        if (resumePrevious && previous != null) playSelectedChannel(previous, recordHistory = false)
        debugLog.recordDebug("IPTV_GRID_STOP | resume=$resumePrevious")
    }

    private fun openActiveGridChannelFullscreen() {
        if (gridChannels.getOrNull(gridActiveIndex) == null) return
        gridFullscreenIndex = gridActiveIndex
        applyIptvGridLayout()
        updateIptvGridFocus()
    }

    private fun closeActiveGridChannel() {
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

    private fun showActiveGridChannelActions() {
        val channel = gridChannels.getOrNull(gridActiveIndex) ?: return
        AlertDialog.Builder(this)
            .setTitle(channel.displayName)
            .setItems(
                arrayOf(
                    getString(R.string.grid_channel_change),
                    getString(R.string.grid_channel_close),
                ),
            ) { _, which ->
                if (which == 0) showGridChannelReplacementPicker() else closeActiveGridChannel()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showGridChannelReplacementPicker() {
        val targetIndex = gridActiveIndex
        val choices = availableLiveIptvChannels().filter { candidate ->
            gridChannels.none { it.sourceKey == candidate.sourceKey }
        }
        if (choices.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(R.string.grid_channel_change)
            .setItems(choices.map { it.displayName }.toTypedArray()) { _, which ->
                val replacement = choices[which]
                gridChannels = gridChannels.toMutableList().apply {
                    this[targetIndex] = replacement
                }
                gridSelectedKeys.clear()
                gridSelectedKeys += gridChannels.map { it.sourceKey }
                gridFullscreenIndex = null
                renderIptvGrid()
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showChannelManagement(channel: LiveChannel) {
        focusedTuneJob?.cancel()
        focusedListSourceKey = null
        channelPanelJob?.cancel()
        if (
            focusedAutoTuneTargetKey == channel.sourceKey &&
            currentChannel?.sourceKey == channel.sourceKey
        ) {
            focusedAutoTunePreviousChannel?.let { previous ->
                playSelectedChannel(previous, recordHistory = false)
            }
        }
        focusedAutoTunePreviousChannel = null
        focusedAutoTuneTargetKey = null
        val actions = arrayOf(
            getString(if (channel.favorite) R.string.remove_favorite else R.string.add_favorite),
            getString(R.string.move_up),
            getString(R.string.move_down),
            getString(R.string.change_channel_number),
            getString(R.string.change_channel_name),
            getString(
                if (parentalControlStore.isLocked(channel.sourceKey)) {
                    R.string.unlock_channel
                } else {
                    R.string.lock_channel
                },
            ),
            getString(if (multiViewActive) R.string.close_multi_view else R.string.open_multi_view),
        )
        AlertDialog.Builder(this)
            .setTitle(channel.displayName)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> updateChannel { repository.setFavorite(channel.sourceKey, !channel.favorite) }
                    1 -> updateChannel { repository.moveChannel(channel.sourceKey, -1) }
                    2 -> updateChannel { repository.moveChannel(channel.sourceKey, 1) }
                    3 -> showChannelNumberEditor(channel)
                    4 -> showChannelNameEditor(channel)
                    5 -> toggleChannelLock(channel)
                    6 -> if (multiViewActive) stopMultiView() else startMultiView(channel)
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun toggleChannelLock(channel: LiveChannel) {
        if (parentalControlStore.isLocked(channel.sourceKey)) {
            ParentalPinDialog.verify(this, getString(R.string.unlock_channel)) {
                parentalControlStore.setLocked(channel.sourceKey, false)
                unlockedChannels.remove(channel.sourceKey)
                updateTechnicalBadges(channel, playback.allTracks())
                adapter.notifyDataSetChanged()
                loadChannels()
            }
            return
        }
        val lockChannel = {
            parentalControlStore.setLocked(channel.sourceKey, true)
            unlockedChannels.remove(channel.sourceKey)
            updateTechnicalBadges(channel, playback.allTracks())
            adapter.notifyDataSetChanged()
            if (currentChannel?.sourceKey == channel.sourceKey) {
                hideChannelPanel()
                showLockedChannel(channel)
            }
            loadChannels()
        }
        if (parentalControlStore.hasPin()) lockChannel() else {
            ParentalPinDialog.create(this, lockChannel)
        }
    }

    private fun startMultiView(channel: LiveChannel) {
        val playing = currentChannel ?: return
        val satellite = listOf(playing, channel).firstOrNull {
            it.source == LiveChannel.Source.TIF
        }
        val iptv = listOf(playing, channel).firstOrNull {
            it.source == LiveChannel.Source.IPTV
        }
        if (satellite == null || iptv == null) {
            Toast.makeText(this, R.string.multiview_requires_satellite_iptv, Toast.LENGTH_LONG).show()
            return
        }
        stopIptvOverlay()
        if (iptvGridActive) stopIptvGrid(resumePrevious = true)
        if (currentChannel?.sourceKey != satellite.sourceKey) {
            playSelectedChannel(satellite, recordHistory = false)
        }
        multiViewActive = true
        internalMiniPlayerActive = false
        multiViewActiveSide = 0
        multiViewSatelliteChannel = satellite
        multiViewIptvChannel = iptv
        val screenWidth = resources.displayMetrics.widthPixels
        val halfWidth = screenWidth / 2
        resizePlayer(binding.tvView, halfWidth, Gravity.START)
        binding.iptvPlayerView.visibility = View.GONE
        secondaryPlayback.stop()
        binding.secondaryTvView.visibility = View.GONE
        binding.secondaryIptvPlayerView.visibility = View.VISIBLE
        resizePlayer(binding.secondaryIptvPlayerView, halfWidth, Gravity.END)
        secondaryIptvPlayback.play(iptv)
        resizeMultiViewFocusBorders(halfWidth)
        updateMultiViewFocus()
        hideChannelPanel()
        showInfoBar()
        debugLog.recordDebug(
            "MULTIVIEW_START | satellite=${satellite.sourceKey}, iptv=${iptv.sourceKey}",
        )
    }

    private fun updateMultiViewFocus() {
        if (!multiViewActive) return
        playback.setMuted(multiViewActiveSide != 0)
        secondaryIptvPlayback.setMuted(multiViewActiveSide != 1)
        binding.multiViewLeftFocus.visibility = if (multiViewActiveSide == 0) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.multiViewRightFocus.visibility = if (multiViewActiveSide == 1) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val focused = if (multiViewActiveSide == 0) {
            multiViewSatelliteChannel
        } else {
            multiViewIptvChannel
        }
        focused?.let { channel ->
            updateTechnicalBadges(
                channel,
                if (channel.source == LiveChannel.Source.TIF) playback.allTracks() else emptyList(),
            )
            showInfoBarForChannel(channel)
        }
        debugLog.recordDebug(
            "MULTIVIEW_FOCUS | side=${if (multiViewActiveSide == 0) "satellite" else "iptv"}",
        )
    }

    private fun resizeMultiViewFocusBorders(width: Int) {
        listOf(
            binding.multiViewLeftFocus to Gravity.START,
            binding.multiViewRightFocus to Gravity.END,
        ).forEach { (view, gravityValue) ->
            view.layoutParams = (view.layoutParams as FrameLayout.LayoutParams).apply {
                this.width = width
                height = FrameLayout.LayoutParams.MATCH_PARENT
                gravity = gravityValue
            }
        }
    }

    private fun zapMultiViewSatellite(offset: Int) {
        val satelliteChannels = channels.filter { it.source == LiveChannel.Source.TIF }
        val current = multiViewSatelliteChannel ?: return
        ChannelNavigator.adjacent(satelliteChannels, current.sourceKey, offset)?.let { next ->
            multiViewSatelliteChannel = next
            playSelectedChannel(next, recordHistory = false)
            val halfWidth = resources.displayMetrics.widthPixels / 2
            resizePlayer(binding.tvView, halfWidth, Gravity.START)
            updateMultiViewFocus()
            debugLog.recordDebug("MULTIVIEW_SATELLITE_CHANGE | channel=${next.sourceKey}")
        }
    }

    private fun showMultiViewIptvPicker() {
        val choices = channels.asSequence()
            .filter { it.source == LiveChannel.Source.IPTV }
            .filter { it.iptvContentType != IptvLibraryContentType.VOD.name }
            .distinctBy { it.sourceKey }
            .toList()
        if (choices.isEmpty()) {
            Toast.makeText(this, R.string.iptv_pip_no_channels, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.multiview_select_iptv)
            .setItems(choices.map { it.displayName }.toTypedArray()) { _, which ->
                val selected = choices[which]
                multiViewIptvChannel = selected
                secondaryIptvPlayback.play(selected)
                updateMultiViewFocus()
                debugLog.recordDebug("MULTIVIEW_IPTV_CHANGE | channel=${selected.sourceKey}")
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun stopMultiView() {
        if (!multiViewActive) return
        multiViewActive = false
        multiViewLongPressJob?.cancel()
        playback.setMuted(false)
        secondaryPlayback.stop()
        secondaryIptvPlayback.stop()
        secondaryIptvPlayback.setMuted(false)
        binding.secondaryTvView.visibility = View.GONE
        binding.secondaryIptvPlayerView.visibility = View.GONE
        binding.multiViewLeftFocus.visibility = View.GONE
        binding.multiViewRightFocus.visibility = View.GONE
        resizePlayer(binding.tvView, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.FILL)
        resizePlayer(binding.iptvPlayerView, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.FILL)
        multiViewSatelliteChannel = null
        multiViewIptvChannel = null
        currentChannel?.let(::showInfoBarForChannel)
        debugLog.recordDebug("MULTIVIEW_STOP")
    }

    private fun resizePlayer(view: View, width: Int, gravityValue: Int) {
        view.layoutParams = (view.layoutParams as FrameLayout.LayoutParams).apply {
            this.width = width
            height = FrameLayout.LayoutParams.MATCH_PARENT
            gravity = gravityValue
            setMargins(0, 0, 0, 0)
        }
    }

    private fun showInfoBarForChannel(channel: LiveChannel) {
        binding.nowChannel.text = channel.displayName
        binding.nowNumber.text = channel.displayNumber
        showInfoBar()
    }

    private fun openChannelEditor() {
        channelEditor.launch(
            Intent(this, ChannelEditorActivity::class.java).putExtra(
                ChannelEditorActivity.EXTRA_CURRENT_SOURCE_KEY,
                currentChannel?.sourceKey,
            ),
        )
    }

    private fun showChannelNumberEditor(channel: LiveChannel) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(channel.displayNumber)
            selectAll()
        }
        showTextEditor(
            title = getString(R.string.move_to_channel_number),
            input = input,
        ) { value ->
            val number = value.toIntOrNull()
            if (number == null || number <= 0) {
                showEditorError(R.string.invalid_channel_number)
            } else {
                updateChannel {
                    repository.moveChannelsToNumber(
                        setOf(channel.sourceKey),
                        number,
                        channels.map { it.sourceKey },
                    )
                }
            }
        }
    }

    private fun showChannelNameEditor(channel: LiveChannel) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setText(channel.displayName)
            selectAll()
        }
        showTextEditor(
            title = getString(R.string.change_channel_name),
            input = input,
        ) { value ->
            if (value.isBlank()) {
                showEditorError(R.string.invalid_channel_name)
            } else {
                updateChannel { repository.setCustomName(channel.sourceKey, value.trim()) }
            }
        }
    }

    private fun showTextEditor(
        title: String,
        input: EditText,
        onSave: (String) -> Unit,
    ) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                onSave(input.text.toString())
            }
        }
        dialog.show()
    }

    private fun showEditorError(message: Int) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun updateChannel(action: suspend () -> Unit) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { action() }
            loadChannels()
        }
    }

    private fun panelChannels(): List<LiveChannel> {
        if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
            return iptvLibraryChannels
        }
        return channels.asSequence()
        .filter { channel ->
            when (sourceFilter) {
                ChannelSourceFilter.ALL -> true
                ChannelSourceFilter.SATELLITE -> channel.source == LiveChannel.Source.TIF
                ChannelSourceFilter.IPTV -> channel.source == LiveChannel.Source.IPTV
            }
        }
        .filter { channel -> !favoriteFilter || channel.favorite }
        .mapIndexed { index, channel -> channel.copy(displayNumber = (index + 1).toString()) }
        .toList()
    }

    private fun applyChannelFilter(
        showFavorites: Boolean = favoriteFilter,
        source: ChannelSourceFilter = sourceFilter,
        requestFocus: Boolean = true,
    ) {
        iptvLibraryLoadJob?.cancel()
        iptvLibraryExhausted = true
        channelPanelContent = ChannelPanelContent.NORMAL
        favoriteFilter = showFavorites
        sourceFilter = source
        channelListFilterStore.save(sourceFilter, favoriteFilter)
        binding.allFilter.isSelected = sourceFilter == ChannelSourceFilter.ALL
        binding.satelliteFilter.isSelected = sourceFilter == ChannelSourceFilter.SATELLITE
        binding.iptvFilter.isSelected = sourceFilter == ChannelSourceFilter.IPTV
        binding.favoriteFilter.isSelected = showFavorites
        binding.sourceFilterRow.visibility = View.GONE
        binding.channelListTitle.setText(R.string.channel_list)
        updateChannelListModeIcon()
        updateChannelActionLabels()
        val filtered = panelChannels()
        currentChannel = filtered.firstOrNull { it.sourceKey == currentChannel?.sourceKey }
            ?: currentChannel
        adapter.submitList(filtered)
        adapter.submitPrograms(currentPrograms)
        currentChannel?.let { adapter.select(it.sourceKey) }
        updateChannelCount(filtered.size)
        binding.channelList.post(::loadVisiblePrograms)
        if (requestFocus) focusCurrentListChannel()
    }

    private fun updateChannelCount(count: Int) {
        binding.channelCount.text = resources.getQuantityString(
            R.plurals.channel_count,
            count,
            count,
        )
    }

    private fun updateChannelActionLabels() {
        val library = channelPanelContent == ChannelPanelContent.IPTV_LIBRARY
        binding.redActionLabel.setText(R.string.edit_short)
        binding.greenActionLabel.setText(
            if (library || currentChannel?.source == LiveChannel.Source.IPTV) {
                R.string.iptv_grid
            } else {
                R.string.iptv_pip
            },
        )
        binding.yellowActionLabel.setText(
            if (library) R.string.select_list_short else R.string.channel_source_short,
        )
        binding.blueActionLabel.setText(
            R.string.empty_action,
        )
    }

    private fun updateChannelListModeIcon() {
        val (icon, description) = when {
            channelPanelContent == ChannelPanelContent.IPTV_LIBRARY ->
                R.drawable.ic_iptv_library to R.string.iptv_library
            sourceFilter == ChannelSourceFilter.SATELLITE ->
                R.drawable.ic_source_tif to R.string.satellite_channels
            sourceFilter == ChannelSourceFilter.IPTV ->
                R.drawable.ic_source_iptv to R.string.iptv_filter
            else -> R.drawable.ic_list to R.string.all_channels
        }
        binding.channelListModeIcon.setImageResource(icon)
        binding.channelListModeIcon.contentDescription = getString(description)
    }

    private fun toggleChannelPanel() {
        if (binding.channelPanel.visibility == View.VISIBLE) hideChannelPanel()
        else showChannelPanel(expanded = false)
    }

    private fun showChannelPanel(expanded: Boolean) {
        if (binding.channelPanel.visibility != View.VISIBLE) {
            focusedAutoTunePreviousChannel = null
            focusedAutoTuneTargetKey = null
        }
        channelPanelExpanded = expanded
        binding.channelPanel.visibility = View.VISIBLE
        binding.sourceFilterRow.visibility = View.GONE
        binding.advancedFilterRow.visibility = if (
            expanded && channelPanelContent == ChannelPanelContent.NORMAL
        ) View.VISIBLE else View.GONE
        binding.channelList.setPadding(
            (resources.displayMetrics.widthPixels * LIST_HORIZONTAL_PADDING_FRACTION).toInt(),
            0,
            (resources.displayMetrics.widthPixels * LIST_HORIZONTAL_PADDING_FRACTION).toInt(),
            if (expanded) {
                (resources.displayMetrics.heightPixels * EXPANDED_LIST_BOTTOM_PADDING_FRACTION)
                    .toInt()
            } else {
                (resources.displayMetrics.heightPixels * VERTICAL_MARGIN_FRACTION).toInt()
            },
        )
        applyPanelGeometry()
        showInfoBar()
        focusCurrentListChannel()
        binding.channelList.post(::loadVisiblePrograms)
        scheduleChannelPanelClose()
    }

    private fun hideChannelPanel() {
        focusedTuneJob?.cancel()
        channelPanelJob?.cancel()
        focusedAutoTunePreviousChannel = null
        focusedAutoTuneTargetKey = null
        channelPanelExpanded = false
        binding.channelPanel.visibility = View.GONE
        binding.advancedFilterRow.visibility = View.GONE
        applyPanelGeometry()
        currentChannel?.let { showInfoBar() }
    }

    private fun scheduleChannelPanelClose() {
        channelPanelJob?.cancel()
        channelPanelJob = lifecycleScope.launch {
            delay(CHANNEL_PANEL_TIMEOUT_MS)
            if (binding.channelPanel.visibility == View.VISIBLE) hideChannelPanel()
        }
    }

    private fun applyPanelGeometry() {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val verticalMargin = (screenHeight * VERTICAL_MARGIN_FRACTION).toInt()
        val overlayGap = (screenWidth * OVERLAY_GAP_FRACTION).toInt()
        val infoOuterMargin = (screenWidth * INFO_OUTER_MARGIN_FRACTION).toInt()
        val panelWidth = if (channelPanelExpanded) {
            (screenWidth * EXPANDED_PANEL_FRACTION).toInt()
        } else {
            (screenWidth * COMPACT_PANEL_WIDTH_FRACTION).toInt()
        }
        binding.channelPanel.layoutParams =
            (binding.channelPanel.layoutParams as FrameLayout.LayoutParams).apply {
                width = panelWidth
                height = if (channelPanelExpanded) {
                    FrameLayout.LayoutParams.MATCH_PARENT
                } else {
                    screenHeight - (verticalMargin * 2)
                }
                gravity = (if (displayPreferences.channelPanelSide == ChannelPanelSide.LEFT) {
                    Gravity.START
                } else {
                    Gravity.END
                }) or Gravity.TOP
                val horizontal = 0
                val vertical = if (channelPanelExpanded) 0 else verticalMargin
                setMargins(horizontal, vertical, horizontal, vertical)
            }
        binding.channelActions.layoutParams = binding.channelActions.layoutParams.apply {
            height = (screenHeight * CHANNEL_ACTIONS_HEIGHT_FRACTION).toInt()
        }
        binding.infoBar.layoutParams =
            (binding.infoBar.layoutParams as FrameLayout.LayoutParams).apply {
                width = if (channelPanelExpanded) {
                    screenWidth - panelWidth - overlayGap - infoOuterMargin
                } else {
                    screenWidth - panelWidth - overlayGap - infoOuterMargin
                }
                height = (screenHeight * INFO_HEIGHT_FRACTION).toInt()
                gravity = (if (displayPreferences.channelPanelSide == ChannelPanelSide.LEFT) {
                    Gravity.END
                } else {
                    Gravity.START
                }) or if (displayPreferences.infoBarPosition == InfoBarPosition.TOP) {
                    Gravity.TOP
                } else {
                    Gravity.BOTTOM
                }
                if (displayPreferences.channelPanelSide == ChannelPanelSide.LEFT) {
                    setMargins(0, verticalMargin, infoOuterMargin, verticalMargin)
                } else {
                    setMargins(infoOuterMargin, verticalMargin, 0, verticalMargin)
                }
            }
        val infoHorizontalPadding = (screenWidth * INFO_HORIZONTAL_PADDING_FRACTION).toInt()
        val infoVerticalPadding = (screenHeight * INFO_VERTICAL_PADDING_FRACTION).toInt()
        binding.infoBar.setPadding(
            infoHorizontalPadding,
            infoVerticalPadding,
            infoHorizontalPadding,
            infoVerticalPadding,
        )
        binding.iptvPlaybackControls.layoutParams =
            (binding.iptvPlaybackControls.layoutParams as FrameLayout.LayoutParams).apply {
                width = (screenWidth * IPTV_CONTROLS_WIDTH_FRACTION).toInt()
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (screenHeight * IPTV_CONTROLS_BOTTOM_MARGIN_FRACTION).toInt()
            }
    }

    private fun startClock() {
        clockJob?.cancel()
        clockJob = lifecycleScope.launch {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEE, d MMMM yyyy", Locale("tr"))
            var refreshPrograms = false
            while (true) {
                val now = Date()
                binding.clockTime.text = timeFormat.format(now)
                binding.clockDate.text = dateFormat.format(now).uppercase(Locale("tr"))
                if (refreshPrograms && channels.isNotEmpty()) {
                    loadChannelPrograms(channels)
                    currentChannel?.let { loadPrograms(it, clearExisting = false) }
                }
                refreshPrograms = !refreshPrograms
                delay(CLOCK_REFRESH_MS)
            }
        }
    }

    private fun focusCurrentListChannel() {
        val visibleChannels = panelChannels()
        if (visibleChannels.isEmpty()) {
            when {
                favoriteFilter -> binding.favoriteFilter.requestFocus()
                sourceFilter == ChannelSourceFilter.SATELLITE -> binding.satelliteFilter.requestFocus()
                sourceFilter == ChannelSourceFilter.IPTV -> binding.iptvFilter.requestFocus()
                else -> binding.allFilter.requestFocus()
            }
            return
        }
        val index = visibleChannels.indexOfFirst { it.sourceKey == currentChannel?.sourceKey }
            .takeIf { it >= 0 } ?: 0
        binding.channelList.scrollToPosition(index)
        binding.channelList.post {
            binding.channelList.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
        }
    }

    private fun cycleChannelListMode() {
        focusedTuneJob?.cancel()
        when {
            channelPanelContent == ChannelPanelContent.IPTV_LIBRARY -> applyChannelFilter(
                showFavorites = false,
                source = ChannelSourceFilter.ALL,
            )
            sourceFilter == ChannelSourceFilter.ALL -> applyChannelFilter(
                showFavorites = false,
                source = ChannelSourceFilter.SATELLITE,
            )
            sourceFilter == ChannelSourceFilter.SATELLITE -> applyChannelFilter(
                showFavorites = false,
                source = ChannelSourceFilter.IPTV,
            )
            else -> openSavedIptvLibrary()
        }
    }

    private fun pageChannelList(direction: Int) {
        if (!binding.channelList.hasFocus() || adapter.itemCount == 0) return
        val manager = binding.channelList.layoutManager as? LinearLayoutManager ?: return
        val focusedItem = binding.channelList.findContainingItemView(currentFocus ?: return) ?: return
        val currentIndex = binding.channelList.getChildAdapterPosition(focusedItem)
            .takeIf { it >= 0 } ?: return
        val first = manager.findFirstVisibleItemPosition().coerceAtLeast(0)
        val last = manager.findLastVisibleItemPosition().coerceAtLeast(first)
        val pageSize = (last - first + 1).coerceAtLeast(1)
        val targetIndex = (currentIndex + direction * pageSize).coerceIn(0, adapter.itemCount - 1)
        focusedTuneJob?.cancel()
        binding.channelList.scrollToPosition(targetIndex)
        binding.channelList.post {
            binding.channelList.findViewHolderForAdapterPosition(targetIndex)
                ?.itemView
                ?.requestFocus()
        }
    }

    private fun wrapChannelList(direction: Int): Boolean {
        if (!binding.channelList.hasFocus()) return false
        val visible = panelChannels()
        if (visible.isEmpty()) return false
        val focusedItem = binding.channelList.findContainingItemView(currentFocus ?: return false)
            ?: return false
        val currentIndex = binding.channelList.getChildAdapterPosition(focusedItem)
        if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
            if (direction < 0 && currentIndex == 0) {
                showIptvLibraryBoundary(last = true)
                return true
            }
            if (direction > 0 && currentIndex == visible.lastIndex && iptvLibraryExhausted) {
                showIptvLibraryBoundary(last = false)
                return true
            }
        }
        val targetIndex = when {
            direction < 0 && currentIndex == 0 -> visible.lastIndex
            direction > 0 && currentIndex == visible.lastIndex -> 0
            else -> return false
        }
        binding.channelList.scrollToPosition(targetIndex)
        binding.channelList.post {
            binding.channelList.findViewHolderForAdapterPosition(targetIndex)
                ?.itemView
                ?.requestFocus()
        }
        return true
    }

    private fun handleBackNavigation() {
        when {
            iptvGridActive -> stopIptvGrid(resumePrevious = true)
            iptvOverlayActive -> stopIptvOverlay()
            multiViewActive -> stopMultiView()
            internalMiniPlayerActive -> toggleInternalMiniPlayer()
            binding.statusPanel.visibility == View.VISIBLE -> {
                binding.statusPanel.visibility = View.GONE
            }
            binding.channelPanel.visibility == View.VISIBLE && channelPanelExpanded -> {
                showChannelPanel(expanded = false)
            }
            binding.channelPanel.visibility == View.VISIBLE -> hideChannelPanel()
            binding.infoBar.visibility == View.VISIBLE -> {
                infoBarJob?.cancel()
                binding.infoBar.visibility = View.GONE
            }
            else -> Unit
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (
            event.action == KeyEvent.ACTION_DOWN &&
            ::debugLog.isInitialized &&
            loggedRemoteKeyCodes.add(event.keyCode)
        ) {
            debugLog.recordDebug(
                "REMOTE_KEY | screen=main, name=${KeyEvent.keyCodeToString(event.keyCode)}, " +
                    "code=${event.keyCode}, scan=${event.scanCode}, device=${event.deviceId}",
            )
        }
        if (iptvGridActive) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                        gridLongPressHandled = false
                        gridLongPressJob?.cancel()
                        gridLongPressJob = lifecycleScope.launch {
                            delay(ViewConfiguration.getLongPressTimeout().toLong())
                            gridLongPressHandled = true
                            showActiveGridChannelActions()
                        }
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        gridLongPressJob?.cancel()
                        if (!gridLongPressHandled) openActiveGridChannelFullscreen()
                    }
                }
                KeyEvent.KEYCODE_BACK -> if (event.action == KeyEvent.ACTION_DOWN) {
                    if (gridFullscreenIndex != null) {
                        gridFullscreenIndex = null
                        applyIptvGridLayout()
                        updateIptvGridFocus()
                    } else {
                        stopIptvGrid(resumePrevious = true)
                    }
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
        if (multiViewActive) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> if (event.action == KeyEvent.ACTION_DOWN) {
                    multiViewLongPressJob?.cancel()
                    multiViewActiveSide = 0
                    updateMultiViewFocus()
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (event.action == KeyEvent.ACTION_DOWN) {
                    multiViewLongPressJob?.cancel()
                    multiViewActiveSide = 1
                    updateMultiViewFocus()
                }
                KeyEvent.KEYCODE_CHANNEL_UP -> if (event.action == KeyEvent.ACTION_DOWN) {
                    multiViewLongPressJob?.cancel()
                    zapMultiViewSatellite(1)
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> if (event.action == KeyEvent.ACTION_DOWN) {
                    multiViewLongPressJob?.cancel()
                    zapMultiViewSatellite(-1)
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> {
                    if (
                        multiViewActiveSide == 1 &&
                        event.action == KeyEvent.ACTION_DOWN &&
                        event.repeatCount == 0
                    ) {
                        multiViewLongPressJob?.cancel()
                        multiViewLongPressJob = lifecycleScope.launch {
                            delay(ViewConfiguration.getLongPressTimeout().toLong())
                            showMultiViewIptvPicker()
                        }
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        multiViewLongPressJob?.cancel()
                    }
                }
                KeyEvent.KEYCODE_BACK -> if (event.action == KeyEvent.ACTION_DOWN) {
                    multiViewLongPressJob?.cancel()
                    stopMultiView()
                }
            }
            return true
        }
        if (binding.statusPanel.visibility == View.VISIBLE) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    binding.statusPanel.visibility = View.GONE
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        val isIptvMediaKey = event.keyCode in setOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        ) && currentChannel?.source == LiveChannel.Source.IPTV
        if (isIptvMediaKey) {
            if (event.action == KeyEvent.ACTION_DOWN) handleIptvMediaKey(event.keyCode)
            return true
        }
        if (
            event.action == KeyEvent.ACTION_DOWN &&
            binding.iptvPlaybackControls.visibility == View.VISIBLE &&
            currentIptvContentKind == IptvContentKind.VOD
        ) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    iptvPlayback.seekBy(-IPTV_VOD_SEEK_STEP_MS)
                    showIptvPlaybackControls(
                        if (iptvPlayback.playbackSnapshot().isPlaying) {
                            R.string.iptv_vod_playing
                        } else R.string.iptv_vod_paused,
                    )
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    iptvPlayback.seekBy(IPTV_VOD_SEEK_STEP_MS)
                    showIptvPlaybackControls(
                        if (iptvPlayback.playbackSnapshot().isPlaying) {
                            R.string.iptv_vod_playing
                        } else R.string.iptv_vod_paused,
                    )
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    iptvPlayback.togglePlayPause()
                    showIptvPlaybackControls(
                        if (iptvPlayback.playbackSnapshot().isPlaying) {
                            R.string.iptv_vod_playing
                        } else {
                            R.string.iptv_vod_paused
                        },
                    )
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    iptvControlsJob?.cancel()
                    binding.iptvPlaybackControls.visibility = View.GONE
                    return true
                }
            }
        }
        val isPictureInPictureKey = event.keyCode == KeyEvent.KEYCODE_WINDOW
        if (event.action != KeyEvent.ACTION_DOWN && isPictureInPictureKey) return true
        if (event.action != KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BACK) {
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (binding.channelPanel.visibility == View.VISIBLE) scheduleChannelPanelClose()
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> handleBackNavigation()
                KeyEvent.KEYCODE_CHANNEL_UP -> zap(1)
                KeyEvent.KEYCODE_CHANNEL_DOWN -> zap(-1)
                KeyEvent.KEYCODE_LAST_CHANNEL -> openPreviousChannel()
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> if (numberInput.isNotEmpty()) {
                    commitChannelNumber()
                } else if (
                    binding.parentalLockPanel.visibility == View.VISIBLE &&
                    currentChannel != null
                ) {
                    requestChannelPin(requireNotNull(currentChannel))
                } else if (binding.channelPanel.visibility != View.VISIBLE) {
                    toggleChannelPanel()
                } else {
                    focusedTuneJob?.cancel()
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_DPAD_UP -> if (binding.channelPanel.visibility != View.VISIBLE) {
                    zap(1)
                } else if (wrapChannelList(-1)) {
                    Unit
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> if (binding.channelPanel.visibility != View.VISIBLE) {
                    zap(-1)
                } else if (wrapChannelList(1)) {
                    Unit
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (binding.channelPanel.visibility != View.VISIBLE) {
                    openPreviousChannel()
                } else if (binding.channelList.hasFocus()) {
                    pageChannelList(-1)
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (
                    binding.channelPanel.visibility == View.VISIBLE &&
                    binding.channelList.hasFocus()
                ) {
                    pageChannelList(1)
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_PROG_RED -> if (binding.channelPanel.visibility == View.VISIBLE) {
                    if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
                        openIptvEditor()
                    } else {
                        openChannelEditor()
                    }
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_PROG_GREEN -> {
                    if (
                        channelPanelContent == ChannelPanelContent.IPTV_LIBRARY ||
                        currentChannel?.source == LiveChannel.Source.IPTV
                    ) {
                        showIptvGridPicker()
                    } else {
                        showIptvPipPicker()
                    }
                }
                KeyEvent.KEYCODE_PROG_YELLOW -> if (binding.channelPanel.visibility == View.VISIBLE) {
                    cycleChannelListMode()
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_PROG_BLUE -> if (
                    binding.channelPanel.visibility == View.VISIBLE
                ) {
                    Unit
                } else {
                    openDisplaySettings()
                }
                KeyEvent.KEYCODE_WINDOW -> enterTvPictureInPicture()
                KeyEvent.KEYCODE_LANGUAGE_SWITCH,
                KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> showAudioTracks()
                KeyEvent.KEYCODE_CAPTIONS -> showSubtitleTracks()
                KeyEvent.KEYCODE_SETTINGS,
                KeyEvent.KEYCODE_TV_CONTENTS_MENU -> openDisplaySettings()
                KeyEvent.KEYCODE_GUIDE,
                KeyEvent.KEYCODE_INFO -> openProgramGuide()
                KeyEvent.KEYCODE_MENU -> toggleChannelPanel()
                else -> {
                    val digit = when (event.keyCode) {
                        KeyEvent.KEYCODE_0 -> 0
                        KeyEvent.KEYCODE_1 -> 1
                        KeyEvent.KEYCODE_2 -> 2
                        KeyEvent.KEYCODE_3 -> 3
                        KeyEvent.KEYCODE_4 -> 4
                        KeyEvent.KEYCODE_5 -> 5
                        KeyEvent.KEYCODE_6 -> 6
                        KeyEvent.KEYCODE_7 -> 7
                        KeyEvent.KEYCODE_8 -> 8
                        KeyEvent.KEYCODE_9 -> 9
                        else -> return super.dispatchKeyEvent(event)
                    }
                    appendChannelDigit(digit)
                }
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        if (::debugLog.isInitialized) {
            debugLog.recordDebug(
                "MAIN_DESTROY | finishing=$isFinishing, config=$isChangingConfigurations",
            )
        }
        numberInputJob?.cancel()
        focusedTuneJob?.cancel()
        infoBarJob?.cancel()
        channelPanelJob?.cancel()
        programJob?.cancel()
        channelProgramsJob?.cancel()
        focusedProgramJob?.cancel()
        visibleProgramsJob?.cancel()
        channelLoadJob?.cancel()
        sleepTimerJob?.cancel()
        iptvControlsJob?.cancel()
        iptvLiveHealthJob?.cancel()
        clockJob?.cancel()
        playback.stop()
        iptvPlayback.release()
        secondaryPlayback.stop()
        secondaryIptvPlayback.release()
        gridControllers.forEach(IptvPlaybackController::release)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val sourceKey = intent.data?.getQueryParameter("sourceKey") ?: return
        channels.firstOrNull { it.sourceKey == sourceKey }?.let(::selectChannel)
            ?: run {
                pendingHomeChannelKey = sourceKey
                loadChannels(preserveCurrentPlayback = true)
            }
    }

}
