package com.tvapp.livetv

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.media.tv.TvTrackInfo
import android.os.Bundle
import android.graphics.Rect
import android.util.Rational
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tvapp.livetv.data.ChannelRepository
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
        private const val MAX_CHANNEL_DIGITS = 4
        private const val NUMBER_ENTRY_TIMEOUT_MS = 1_500L
        private const val LIST_FOCUS_TUNE_DELAY_MS = 1_500L
        private const val COMPACT_PANEL_WIDTH_FRACTION = 0.25f
        private const val COMPACT_PANEL_HEIGHT_FRACTION = 0.88f
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
        private const val AUDIO_ONLY_CONFIRM_DELAY_MS = 3_000L
        private const val CHANNEL_PANEL_TIMEOUT_MS = 10_000L
        private const val INTERNAL_MINI_WIDTH_FRACTION = 0.38f
        private const val IPTV_CONTROLS_WIDTH_FRACTION = 0.68f
        private const val IPTV_CONTROLS_BOTTOM_MARGIN_FRACTION = 0.045f
        private const val IPTV_CONTROL_TIMEOUT_MS = 6_000L
        private const val IPTV_LIVE_CHECK_INTERVAL_MS = 12_000L
        private const val IPTV_MAX_LIVE_OFFSET_MS = 18_000L
        private const val IPTV_VOD_SEEK_STEP_MS = 30_000L
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: ChannelRepository
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
    private var internalMiniPlayerActive = false
    private var multiViewActive = false
    private val unlockedChannels = mutableSetOf<String>()
    private var favoriteFilter = false
    private var sourceFilter = ChannelSourceFilter.ALL
    private var channelPanelExpanded = false
    private var numberInput = ""
    private var numberInputJob: Job? = null
    private var audioOnlyJob: Job? = null
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
        programRepository = ProgramRepository(this)
        playback = TifPlaybackController(binding.tvView)
        iptvPlayback = IptvPlaybackController(this, binding.iptvPlayerView)
        secondaryPlayback = TifPlaybackController(binding.secondaryTvView)
        secondaryIptvPlayback = IptvPlaybackController(this, binding.secondaryIptvPlayerView)
        secondaryIptvPlayback.onPlaybackError = { error ->
            debugLog.recordDebug("MULTIVIEW_IPTV_FAILURE | ${error.errorCodeName}: ${error.message}")
            stopMultiView()
        }
        playbackHistory = PlaybackHistoryStore(this)
        displayPreferencesStore = DisplayPreferencesStore(this)
        channelListFilterStore = ChannelListFilterStore(this)
        sleepTimerStore = SleepTimerStore(this)
        parentalControlStore = ParentalControlStore(this)
        homeRecentChannelsPublisher = HomeRecentChannelsPublisher(this)
        pendingHomeChannelKey = intent.data?.getQueryParameter("sourceKey")
        channelListFilterStore.load().also { savedFilter ->
            sourceFilter = savedFilter.source
            favoriteFilter = savedFilter.favoritesOnly
        }
        debugLog = CrashReportStore(this)
        debugLog.recordDebug("MAIN_CREATE | savedState=${savedInstanceState != null}")
        if (intent.getBooleanExtra(BootLaunchReceiver.EXTRA_STARTED_AFTER_BOOT, false)) {
            debugLog.recordDebug("MAIN_STARTED_AFTER_BOOT")
        }
        playback.onTracksChanged = { applyPreferredTracks() }
        playback.onVideoStateChanged = { available, reason ->
            val channel = currentChannel
            if (channel != null && channel.source == LiveChannel.Source.TIF) {
                audioOnlyJob?.cancel()
                when {
                    channel.isRadioChannel() -> updateAudioOnlyPanel(channel, true)
                    available -> updateAudioOnlyPanel(channel, false)
                    reason == TvInputManager.VIDEO_UNAVAILABLE_REASON_AUDIO_ONLY ->
                        scheduleAudioOnlyPanel(channel, audioOnlyReason = true)
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

    private fun selectChannel(channel: LiveChannel) {
        if (parentalControlStore.isLocked(channel.sourceKey) && channel.sourceKey !in unlockedChannels) {
            showLockedChannel(channel)
            return
        }
        playSelectedChannel(channel)
    }

    private fun showLockedChannel(channel: LiveChannel) {
        focusedTuneJob?.cancel()
        audioOnlyJob?.cancel()
        playback.stop()
        iptvPlayback.stop()
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
            playSelectedChannel(channel)
        }
    }

    private fun playSelectedChannel(channel: LiveChannel) {
        debugLog.recordDebug(
            "CHANNEL_SELECT | number=${channel.displayNumber}, key=${channel.sourceKey}",
        )
        focusedTuneJob?.cancel()
        audioOnlyJob?.cancel()
        currentChannel = channel
        currentIptvContentKind = IptvContentKind.UNKNOWN
        iptvControlsJob?.cancel()
        iptvLiveHealthJob?.cancel()
        binding.iptvPlaybackControls.visibility = View.GONE
        binding.parentalLockPanel.visibility = View.GONE
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
            { selectChannel(failedChannel) }
        }
        binding.retryButton.requestFocus()
    }

    private fun confirmListChannel(channel: LiveChannel) {
        focusedTuneJob?.cancel()
        if (channel.sourceKey != currentChannel?.sourceKey) selectChannel(channel)
        hideChannelPanel()
    }

    private fun zap(offset: Int) {
        ChannelNavigator.adjacent(panelChannels(), currentChannel?.sourceKey, offset)
            ?.let(::selectChannel)
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
        ChannelNavigator.byNumber(panelChannels(), entered)?.let { channel ->
            selectChannel(channel)
            if (binding.channelPanel.visibility == View.VISIBLE) hideChannelPanel()
        } ?: run { binding.nowChannel.setText(R.string.channel_not_found) }
    }

    private fun openPreviousChannel() {
        ChannelNavigator.previousDistinct(
            panelChannels(),
            playbackHistory.keys(),
            currentChannel?.sourceKey,
        )?.let(::selectChannel)
    }

    private fun scheduleFocusedTune(channel: LiveChannel) {
        focusedListSourceKey = channel.sourceKey
        loadFocusedProgram(channel)
        focusedTuneJob?.cancel()
        focusedTuneJob = lifecycleScope.launch {
            delay(LIST_FOCUS_TUNE_DELAY_MS)
            if (binding.channelPanel.visibility == View.VISIBLE) selectChannel(channel)
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
        if (channel.source == LiveChannel.Source.IPTV) {
            binding.currentProgram.setText(R.string.no_program_information)
            binding.currentProgram.visibility = if (displayPreferences.showCurrentProgram) {
                View.VISIBLE
            } else {
                View.GONE
            }
            return
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
            val hasAudio = tracks.any { it.type == TvTrackInfo.TYPE_AUDIO }
            val hasVideo = tracks.any { it.type == TvTrackInfo.TYPE_VIDEO }
            when {
                channel.isRadioChannel() -> updateAudioOnlyPanel(channel, true)
                hasVideo -> {
                    audioOnlyJob?.cancel()
                    updateAudioOnlyPanel(channel, false)
                }
                tracks.isNotEmpty() && hasAudio -> scheduleAudioOnlyPanel(channel)
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
        val videoHeight = videoTrack?.videoHeight ?: 0
        val aspect = if (width > 0 && videoHeight > 0) {
            if (width * 9 >= videoHeight * 16 - 16 && width * 9 <= videoHeight * 16 + 16) {
                "16:9"
            } else {
                "4:3"
            }
        } else {
            null
        }
        binding.aspectBadge.text = aspect
        binding.aspectBadge.visibility = if (aspect == null || radio) View.GONE else View.VISIBLE
        val resolution = format
            .substringAfter("VIDEO_FORMAT_", format)
            .takeIf { it.any(Char::isDigit) }
        binding.resolutionBadge.text = resolution
        binding.resolutionBadge.visibility = if (resolution == null || radio) {
            View.GONE
        } else {
            View.VISIBLE
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

        val activeSlots = booleanArrayOf(
            true,
            true,
            audioTracks.isNotEmpty(),
            subtitleTracks.isNotEmpty(),
            aspect != null && !radio,
            resolution != null && !radio,
            channel.encrypted || channel.locked,
        )
        val slots = listOf(
            binding.techSlotQualityBadge,
            binding.techSlotSource,
            binding.techSlotAudio,
            binding.techSlotSubtitle,
            binding.techSlotAspect,
            binding.techSlotResolution,
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

    private fun scheduleAudioOnlyPanel(
        channel: LiveChannel,
        audioOnlyReason: Boolean = false,
    ) {
        audioOnlyJob?.cancel()
        audioOnlyJob = lifecycleScope.launch {
            delay(AUDIO_ONLY_CONFIRM_DELAY_MS)
            if (currentChannel?.sourceKey != channel.sourceKey) return@launch
            val tracks = playback.allTracks()
            val hasAudio = tracks.any { it.type == TvTrackInfo.TYPE_AUDIO }
            val hasVideo = tracks.any { it.type == TvTrackInfo.TYPE_VIDEO }
            updateAudioOnlyPanel(channel, (hasAudio || audioOnlyReason) && !hasVideo)
        }
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

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun showChannelManagement(channel: LiveChannel) {
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
        val primary = currentChannel ?: return
        if (channel.sourceKey == primary.sourceKey) return
        multiViewActive = true
        internalMiniPlayerActive = false
        val screenWidth = resources.displayMetrics.widthPixels
        val halfWidth = screenWidth / 2
        resizePlayer(binding.tvView, halfWidth, Gravity.START)
        resizePlayer(binding.iptvPlayerView, halfWidth, Gravity.START)
        when (channel.source) {
            LiveChannel.Source.TIF -> {
                binding.secondaryIptvPlayerView.visibility = View.GONE
                binding.secondaryTvView.visibility = View.VISIBLE
                resizePlayer(binding.secondaryTvView, halfWidth, Gravity.END)
                secondaryPlayback.play(channel)
            }
            LiveChannel.Source.IPTV -> {
                binding.secondaryTvView.visibility = View.GONE
                binding.secondaryIptvPlayerView.visibility = View.VISIBLE
                resizePlayer(binding.secondaryIptvPlayerView, halfWidth, Gravity.END)
                secondaryIptvPlayback.play(channel)
                secondaryIptvPlayback.setMuted(true)
            }
        }
        hideChannelPanel()
        showInfoBar()
        debugLog.recordDebug("MULTIVIEW_START | primary=${primary.sourceKey}, secondary=${channel.sourceKey}")
    }

    private fun stopMultiView() {
        if (!multiViewActive) return
        multiViewActive = false
        secondaryPlayback.stop()
        secondaryIptvPlayback.stop()
        binding.secondaryTvView.visibility = View.GONE
        binding.secondaryIptvPlayerView.visibility = View.GONE
        resizePlayer(binding.tvView, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.FILL)
        resizePlayer(binding.iptvPlayerView, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.FILL)
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

    private fun panelChannels(): List<LiveChannel> = channels.asSequence()
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

    private fun applyChannelFilter(
        showFavorites: Boolean = favoriteFilter,
        source: ChannelSourceFilter = sourceFilter,
        requestFocus: Boolean = true,
    ) {
        favoriteFilter = showFavorites
        sourceFilter = source
        channelListFilterStore.save(sourceFilter, favoriteFilter)
        binding.allFilter.isSelected = sourceFilter == ChannelSourceFilter.ALL
        binding.satelliteFilter.isSelected = sourceFilter == ChannelSourceFilter.SATELLITE
        binding.iptvFilter.isSelected = sourceFilter == ChannelSourceFilter.IPTV
        binding.favoriteFilter.isSelected = showFavorites
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

    private fun toggleChannelPanel() {
        if (binding.channelPanel.visibility == View.VISIBLE) hideChannelPanel()
        else showChannelPanel(expanded = false)
    }

    private fun showChannelPanel(expanded: Boolean) {
        channelPanelExpanded = expanded
        binding.channelPanel.visibility = View.VISIBLE
        binding.advancedFilterRow.visibility = if (expanded) View.VISIBLE else View.GONE
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
                    (screenHeight * COMPACT_PANEL_HEIGHT_FRACTION).toInt()
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

    private fun cycleSourceFilter(direction: Int) {
        val filters = listOf(
            ChannelSourceFilter.ALL,
            ChannelSourceFilter.SATELLITE,
            ChannelSourceFilter.IPTV,
        )
        val currentIndex = filters.indexOf(sourceFilter).coerceAtLeast(0)
        val targetIndex = (currentIndex + direction + filters.size) % filters.size
        focusedTuneJob?.cancel()
        applyChannelFilter(source = filters[targetIndex])
    }

    private fun wrapChannelList(direction: Int): Boolean {
        if (!binding.channelList.hasFocus()) return false
        val visible = panelChannels()
        if (visible.isEmpty()) return false
        val focusedItem = binding.channelList.findContainingItemView(currentFocus ?: return false)
            ?: return false
        val currentIndex = binding.channelList.getChildAdapterPosition(focusedItem)
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
                KeyEvent.KEYCODE_DPAD_CENTER -> if (numberInput.isNotEmpty()) {
                    commitChannelNumber()
                } else if (
                    binding.parentalLockPanel.visibility == View.VISIBLE &&
                    currentChannel != null
                ) {
                    requestChannelPin(requireNotNull(currentChannel))
                } else if (binding.channelPanel.visibility != View.VISIBLE) {
                    toggleChannelPanel()
                } else {
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
                    cycleSourceFilter(-1)
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (
                    binding.channelPanel.visibility == View.VISIBLE &&
                    binding.channelList.hasFocus()
                ) {
                    cycleSourceFilter(1)
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_PROG_RED -> if (binding.channelPanel.visibility == View.VISIBLE) {
                    openChannelEditor()
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_PROG_GREEN -> if (binding.channelPanel.visibility == View.VISIBLE) {
                    showAudioTracks()
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_PROG_YELLOW -> if (binding.channelPanel.visibility == View.VISIBLE) {
                    showSubtitleTracks()
                } else {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_PROG_BLUE -> openDisplaySettings()
                KeyEvent.KEYCODE_WINDOW -> enterTvPictureInPicture()
                KeyEvent.KEYCODE_LANGUAGE_SWITCH -> showAudioTracks()
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
        audioOnlyJob?.cancel()
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
