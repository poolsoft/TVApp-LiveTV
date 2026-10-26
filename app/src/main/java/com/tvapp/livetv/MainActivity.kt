package com.tvapp.livetv

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import com.tvapp.livetv.playback.ChannelZapper
import com.tvapp.livetv.playback.MultiViewController
import com.tvapp.livetv.playback.VodPlayerOverlayController
import com.tvapp.livetv.ui.TrackSelectionHelper
import com.tvapp.livetv.ui.TvOverlayLayoutHelper
import com.tvapp.livetv.data.XmlTvRepository
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvTrackInfo
import android.os.Bundle
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Rational
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ArrayAdapter
import android.widget.AdapterView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.tvapp.livetv.data.ChannelRepository
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.data.IptvPageAnchor
import com.tvapp.livetv.data.IptvPageDirection
import com.tvapp.livetv.data.ProgramRepository
import com.tvapp.livetv.data.ProgramSummary
import com.tvapp.livetv.databinding.ActivityMainBinding
import com.tvapp.livetv.diagnostics.CrashReportStore
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.home.HomeRecentChannelsPublisher
import com.tvapp.livetv.playback.TifPlaybackController
import com.tvapp.livetv.playback.IptvPlaybackController
import com.tvapp.livetv.playback.gridProfileForCellCount
import com.tvapp.livetv.playback.IptvBufferingState
import com.tvapp.livetv.playback.IptvPlaybackProfile
import com.tvapp.livetv.playback.IptvPlaybackHealthSnapshot
import com.tvapp.livetv.playback.IptvRecoveryAction
import com.tvapp.livetv.playback.IptvContentKind
import com.tvapp.livetv.playback.IptvPlaybackPhase
import com.tvapp.livetv.playback.IptvTrackOption
import com.tvapp.livetv.playback.ExternalPlayerLauncher
import com.tvapp.livetv.playback.IptvResumeStore
import com.tvapp.livetv.playback.IptvAspectMode
import com.tvapp.livetv.playback.IptvViewPreferencesStore
import com.tvapp.livetv.playback.ChannelNavigator
import com.tvapp.livetv.playback.PlaybackHistoryStore
import com.tvapp.livetv.platform.DeviceCapabilities
import com.tvapp.livetv.platform.DeviceCapabilitiesSession
import com.tvapp.livetv.platform.DeviceResourcePolicy
import com.tvapp.livetv.platform.ExperienceMode
import com.tvapp.livetv.platform.resourcePolicyFor
import com.tvapp.livetv.platform.resolveExperienceMode
import com.tvapp.livetv.image.ChannelLogoLoader
import com.tvapp.livetv.settings.ChannelPanelSide
import com.tvapp.livetv.settings.ChannelListFilterStore
import com.tvapp.livetv.settings.ChannelTrackPreferenceStore
import com.tvapp.livetv.settings.resolveTrackPreferences
import com.tvapp.livetv.settings.ChannelSourceFilter
import com.tvapp.livetv.settings.DisplayPreferences
import com.tvapp.livetv.settings.DisplayPreferencesStore
import com.tvapp.livetv.settings.InfoBarPosition
import com.tvapp.livetv.settings.IptvPlaybackPreferences
import com.tvapp.livetv.settings.IptvPlaybackPreferencesStore
import com.tvapp.livetv.settings.SleepTimerStore
import com.tvapp.livetv.settings.ParentalControlStore
import com.tvapp.livetv.settings.ExternalPlayerPreferencesStore
import com.tvapp.livetv.settings.ExperienceModePreferencesStore
import com.tvapp.livetv.settings.MultiViewPreferencesStore
import com.tvapp.livetv.billing.IptvAccessDialogs
import com.tvapp.livetv.billing.IptvEntitlementManager
import com.tvapp.livetv.ui.ChannelAdapter
import com.tvapp.livetv.ui.ChannelRowOptions
import com.tvapp.livetv.ui.ParentalPinDialog
import com.tvapp.livetv.ui.OsdCoordinator
import com.tvapp.livetv.ui.PlaybackSurfaceMode
import com.tvapp.livetv.ui.PlaybackUiState
import com.tvapp.livetv.ui.PrimaryOsd
import com.tvapp.livetv.ui.RemoteAction
import com.tvapp.livetv.ui.RemoteActionRouter
import com.tvapp.livetv.ui.RemoteKey
import com.tvapp.livetv.ui.RemoteKeyPhase
import com.tvapp.livetv.ui.RemoteUiContext
import com.tvapp.livetv.ui.VideoQuality
import com.tvapp.livetv.ui.MultiViewChannelAdapter
import com.tvapp.livetv.ui.MultiViewFocusResolver
import com.tvapp.livetv.ui.TvUiComponents
import com.tvapp.livetv.ui.isRadioChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale

class MainActivity : TvRemoteActivity() {
    companion object {
        const val EXTRA_VOD_SOURCE_KEY = "com.tvapp.livetv.extra.VOD_SOURCE_KEY"
        const val EXTRA_VOD_STREAM_URL = "com.tvapp.livetv.extra.VOD_STREAM_URL"
        const val EXTRA_VOD_TITLE = "com.tvapp.livetv.extra.VOD_TITLE"
        const val EXTRA_VOD_EPISODE_TITLE = "com.tvapp.livetv.extra.VOD_EPISODE_TITLE"
        const val EXTRA_VOD_LOGO_URL = "com.tvapp.livetv.extra.VOD_LOGO_URL"
        const val EXTRA_VOD_START_OVER = "com.tvapp.livetv.extra.VOD_START_OVER"
        const val EXTRA_LIVE_MODE = "com.tvapp.livetv.extra.LIVE_MODE"
        const val EXTRA_LIVE_TARGET_SOURCE = "com.tvapp.livetv.extra.LIVE_TARGET_SOURCE"
        const val MODE_PREFS = "playback-mode"
        const val LAST_VOD_KEY = "last-vod-key"
        private const val LAST_LIVE_KEY = "last-live-key"
        private const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"
        private const val MAX_CHANNEL_DIGITS = 5
        private const val NUMBER_ENTRY_TIMEOUT_MS = 1_500L
        private const val COMPACT_PANEL_WIDTH_FRACTION = 0.25f
        private const val EXPANDED_PANEL_FRACTION = 0.44f
        private const val INFO_COMPACT_HEIGHT_FRACTION = 0.205f
        private const val IPTV_INFO_EXTRA_HEIGHT_DP = 66
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
        private const val IPTV_PLAYBACK_CHECK_INTERVAL_MS = 5_000L
        private const val EPG_REFRESH_INTERVAL_MS = 15_000L
        private const val EPG_FOCUS_DEBOUNCE_MS = 300L
        private const val EPG_LIST_WINDOW_RADIUS = 5
        private const val IPTV_MAX_LIVE_OFFSET_MS = 60_000L
        /** Max automatic full re-tunes after watchdog recovery is exhausted
         *  before the failure OSD becomes the only recovery path. The
         *  watchdog itself already re-prepares MAX_WATCHDOG_RECOVERY_COUNT
         *  times and every alternative stream is tried first. */
        private const val IPTV_EXHAUSTED_RETUNE_LIMIT = 2
        private const val IPTV_VOD_SEEK_STEP_MS = 30_000L
        private const val IPTV_LIBRARY_FILTER_PREFS = "iptv-library-filter"
        private const val IPTV_LIBRARY_SOURCE_ID = "source-id"
        private const val IPTV_LIBRARY_CONTENT_TYPE = "content-type"
        private const val IPTV_LIBRARY_CATEGORY = "category"
        private const val IPTV_LIBRARY_PAGE_SIZE = 250
        private const val MOBILE_SWIPE_DISTANCE_DP = 56
        private val SEARCH_COMBINING_MARKS = Regex("\\p{M}+")
    }

    private enum class ChannelPanelContent { NORMAL, IPTV_LIBRARY }
    private enum class IptvLibraryContentType { ALL, LIVE, VOD, CONTINUE }
    private lateinit var multiViewController: MultiViewController
    private val multiViewDraft: LinkedHashMap<String, LiveChannel>
        get() = multiViewController.multiViewDraft
    private var multiViewAdding: Boolean
        get() = multiViewController.multiViewAdding
        set(value) { multiViewController.multiViewAdding = value }
    private var multiViewDraftInitialized: Boolean
        get() = multiViewController.multiViewDraftInitialized
        set(value) { multiViewController.multiViewDraftInitialized = value }
    private var restoredMultiViewKeys: List<String>? = null
    private enum class IptvControlRow { TIMELINE, BUTTONS }
    private enum class IptvControlButton { PLAY_PAUSE, BUFFER, SPEED, MORE, CONNECTION }
    private var vodPlayerOverlayController: VodPlayerOverlayController? = null
    private var currentVodHasEpisodes: Boolean = false
    private data class ChannelListModeOption(
        val label: String,
        val selected: Boolean,
        val apply: () -> Unit,
    )

    private lateinit var binding: ActivityMainBinding
    private val activityStartAt = SystemClock.elapsedRealtime()
    private lateinit var repository: ChannelRepository
    private lateinit var iptvRepository: IptvRepository
    private lateinit var programRepository: ProgramRepository
    private lateinit var playback: TifPlaybackController
    private lateinit var iptvPlayback: IptvPlaybackController
    private lateinit var secondaryIptvPlayback: IptvPlaybackController
    private lateinit var playbackHistory: PlaybackHistoryStore
    private val vodHistory by lazy { PlaybackHistoryStore(this, "vod-playback-history") }
    private val modePreferences by lazy { getSharedPreferences(MODE_PREFS, MODE_PRIVATE) }
    private var vodMode = false
    private lateinit var displayPreferencesStore: DisplayPreferencesStore
    private lateinit var channelTrackPreferenceStore: ChannelTrackPreferenceStore
    private lateinit var channelListFilterStore: ChannelListFilterStore
    private lateinit var sleepTimerStore: SleepTimerStore
    private lateinit var parentalControlStore: ParentalControlStore
    private lateinit var iptvResumeStore: IptvResumeStore
    private lateinit var iptvViewPreferencesStore: IptvViewPreferencesStore
    private lateinit var homeRecentChannelsPublisher: HomeRecentChannelsPublisher
    private lateinit var debugLog: CrashReportStore
    private lateinit var deviceCapabilities: DeviceCapabilities
    private lateinit var deviceResourcePolicy: DeviceResourcePolicy
    private lateinit var multiViewPreferencesStore: MultiViewPreferencesStore
    private var experienceMode = ExperienceMode.IPTV_ONLY_TV
    private lateinit var adapter: ChannelAdapter
    private lateinit var osdCoordinator: OsdCoordinator
    private var lastTifTrackLogSignature: String? = null
    private var lastTifCallbackLogSignature: String? = null
    private var lastIptvHealthLogSignature: String? = null
    private var displayPreferences = DisplayPreferences()
    private var iptvSourceNames: Map<String, String> = emptyMap()

    /** True from channel switch until the new channel's video really renders;
     *  drives the optional black-screen-while-tuning overlay (Ayarlar >
     *  Oynatma). Never lets a stale render callback from the previous channel
     *  end the blackout for the wrong tune. */
    private var blackScreenTuneGeneration = 0
    private var blackScreenActive = false
    private var channels: List<LiveChannel> = emptyList()
    private var normalPanelChannels: List<LiveChannel> = emptyList()
    private val currentPrograms = mutableMapOf<String, ProgramSummary>()
    private var currentChannel: LiveChannel? = null
    private var pendingTuneStartedAt = 0L
    private var pendingTuneSourceKey: String? = null
    private var activePassthroughInputId: String? = null
    private var resumeTifPlayback = false
    private var channelPanelContent = ChannelPanelContent.NORMAL
    private var iptvLibraryChannels: List<LiveChannel> = emptyList()
    private var iptvLibrarySourceId: Long? = null
    private var iptvLibraryContentType = IptvLibraryContentType.ALL
    private var iptvLibraryCategory: String? = null
    private var channelSearchQuery = ""
    private var normalizedChannelSearchQuery = ""
    private var iptvLibraryWindowStart = 0
    private var iptvLibraryTotalCount = 0
    private var iptvLibraryHasPrevious = false
    private var iptvLibraryHasNext = false
    private var iptvLibraryFirstAnchor: IptvPageAnchor? = null
    private var iptvLibraryLastAnchor: IptvPageAnchor? = null
    private var iptvLibraryGeneration = 0L
    private var iptvLibraryLoadJob: Job? = null
    private var alphabetRail: com.tvapp.livetv.ui.AlphabetRail? = null
    private var alphabetLoadJob: Job? = null
    private var alphabetRightJob: Job? = null
    private var alphabetRightHandled = false
    private var alphabetBrowsing = false
    private var alphabetFilterJob: Job? = null
    private var alphabetLetter: String? = null
    private var iptvAlphabetInitials: List<String>? = null
    private var continuedAlphabetChannels: List<LiveChannel> = emptyList()
    private var alphabetTargetPosition = 0
    private var currentPlaybackUsesIptvLibrary = false
    private var lockedChannelRecordsHistory = true
    private var internalMiniPlayerActive = false
    private val iptvOverlayActive: Boolean
        get() = multiViewController.iptvOverlayActive
    private val iptvOverlayChannel: LiveChannel?
        get() = multiViewController.iptvOverlayChannel
    private val iptvGridActive: Boolean
        get() = multiViewController.iptvGridActive
    private val gridActiveIndex: Int
        get() = multiViewController.gridActiveIndex
    private val gridFullscreenIndex: Int?
        get() = multiViewController.gridFullscreenIndex
    private val gridChannels: List<LiveChannel>
        get() = multiViewController.gridChannels
    private var yellowLongPressJob: Job? = null
    private var yellowLongPressHandled = false
    private var greenLongPressJob: Job? = null
    private var greenLongPressHandled = false
    private var settingsLongPressJob: Job? = null
    private var settingsLongPressHandled = false
    private var lastChannelLongPressJob: Job? = null
    private var lastChannelLongPressHandled = false
    private var okLongPressJob: Job? = null
    private var okLongPressHandled = false
    private val unlockedChannels = mutableSetOf<String>()
    private var favoriteFilter = false
    private var sourceFilter = ChannelSourceFilter.ALL
    private var channelPanelExpanded = false
    private lateinit var channelZapper: ChannelZapper
    private lateinit var trackSelectionHelper: TrackSelectionHelper
    private lateinit var tvOverlayLayoutHelper: TvOverlayLayoutHelper
    private var numberInput: String
        get() = channelZapper.numberInput
        set(value) { channelZapper.numberInput = value }
    private var focusedTuneJob: Job? = null
    private var channelResolutionGeneration = 0
    private var vodResolveJob: Job? = null
    private var infoBarJob: Job? = null
    private var channelPanelJob: Job? = null
    private var programJob: Job? = null
    private var channelLoadJob: Job? = null
    private var clockJob: Job? = null
    private var diagnosticsJob: Job? = null
    private var statusRetryAction: (() -> Unit)? = null
    private var restoredInitialChannel = false
    private var pendingEditorChannelKey: String? = null
    private var pendingHomeChannelKey: String? = null
    private var pendingExternalChannelUri: String? = null
    private var focusedListSourceKey: String? = null
    private var focusedAutoTunePreviousChannel: LiveChannel? = null
    private var focusedAutoTuneTargetKey: String? = null
    private var visibleProgramsJob: Job? = null
    private var visibleProgramsRequestId = 0L
    private var epgRefreshJob: Job? = null
    private var epgAutoRefreshJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var iptvControlsJob: Job? = null
    private var iptvNoticeJob: Job? = null
    private var iptvLiveHealthJob: Job? = null
    private var iptvInfoTickerJob: Job? = null
    private var homeRecentPublishJob: Job? = null
    private var currentIptvContentKind = IptvContentKind.UNKNOWN
    private var catchUpReturnChannel: LiveChannel? = null
    private var iptvManualTimeshift = false
    private var iptvPlaybackFailed = false
    private var iptvControlRow = IptvControlRow.TIMELINE
    private var selectedIptvButton = IptvControlButton.PLAY_PAUSE
    private var iptvControlsInteractive = false
    private var pendingBufferSeconds = IptvPlaybackPreferences.DEFAULT_BUFFER_SECONDS
    private var iptvAlternativeStreams: List<LiveChannel> = emptyList()
    private var iptvAlternativeIndex = 0
    private var iptvAlternativeLoadKey: String? = null
    private var iptvExhaustedRetuneKey: String? = null
    private var iptvExhaustedRetuneCount = 0
    private val openExternalSubtitle = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null || currentChannel?.source != LiveChannel.Source.IPTV) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        if (iptvPlayback.addExternalSubtitle(uri, contentResolver.getType(uri))) {
            displayPreferencesStore.updateTrackPreferences(subtitlesEnabled = true)
            displayPreferences = displayPreferencesStore.load()
            Toast.makeText(this, R.string.external_subtitle_added, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, R.string.external_subtitle_failed, Toast.LENGTH_LONG).show()
        }
    }
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
        deviceResourcePolicy = resourcePolicyFor(
            deviceCapabilities,
            forceFourGridStreams = multiViewPreferencesStore.forceFourStreams(),
        )
        applyDisplayPreferences()
        iptvPlayback.applySubtitleAppearance()
        scheduleSleepTimer()
        val previousMode = experienceMode
        applyExperienceMode()
        if (experienceMode != previousMode) loadChannels(preserveCurrentPlayback = true)
    }
    private val programGuide = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val sourceKey = result.data?.getStringExtra(
            ProgramGuideActivity.EXTRA_SELECTED_SOURCE_KEY,
        )
        val catchUpStart = result.data?.getLongExtra(ProgramGuideActivity.EXTRA_CATCHUP_START, 0L) ?: 0L
        val catchUpEnd = result.data?.getLongExtra(ProgramGuideActivity.EXTRA_CATCHUP_END, 0L) ?: 0L
        if (sourceKey != null && catchUpStart > 0L && catchUpEnd > catchUpStart) {
            lifecycleScope.launch {
                val liveChannel = channels.firstOrNull { it.sourceKey == sourceKey }
                    ?: withContext(Dispatchers.IO) { iptvRepository.channel(sourceKey) }
                val archived = withContext(Dispatchers.IO) {
                    iptvRepository.catchUpChannel(sourceKey, catchUpStart, catchUpEnd)
                }
                if (liveChannel != null && archived != null) {
                    catchUpReturnChannel = liveChannel
                    selectChannel(archived, recordHistory = false)
                    hideChannelPanel()
                } else {
                    Toast.makeText(this@MainActivity, R.string.catchup_unavailable, Toast.LENGTH_LONG).show()
                }
            }
        } else {
            channels.firstOrNull { it.sourceKey == sourceKey }?.let(::selectChannel)
            if (sourceKey != null) hideChannelPanel()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoredMultiViewKeys = savedInstanceState?.getStringArrayList("multiview-draft")
        vodMode = savedInstanceState?.getBoolean("vod-mode") == true
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        osdCoordinator = OsdCoordinator(::renderPlaybackUiState)
        renderPlaybackUiState(osdCoordinator.state)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        binding.tvView.keepScreenOn = true

        repository = ChannelRepository(this)
        iptvRepository = IptvRepository(this)
        programRepository = ProgramRepository(this)
        debugLog = CrashReportStore(this)
        deviceCapabilities = DeviceCapabilitiesSession.get(this)
        multiViewPreferencesStore = MultiViewPreferencesStore(this)
        deviceResourcePolicy = resourcePolicyFor(
            deviceCapabilities,
            forceFourGridStreams = multiViewPreferencesStore.forceFourStreams(),
        )
        ChannelLogoLoader.configure(this, deviceCapabilities)
        applyExperienceMode()
        playback = TifPlaybackController(binding.tvView)
        iptvPlayback = IptvPlaybackController(
            this,
            binding.iptvPlayerView,
        )
        secondaryIptvPlayback = IptvPlaybackController(
            this,
            binding.secondaryIptvPlayerView,
            IptvPlaybackProfile.SECONDARY,
        )
        secondaryIptvPlayback.onPlaybackError = { error ->
            debugLog.recordDebug("IPTV_PIP_FAILURE | ${error.errorCodeName}")
            if (iptvOverlayActive) stopIptvOverlay()
        }
        playbackHistory = PlaybackHistoryStore(this)
        displayPreferencesStore = DisplayPreferencesStore(this)
        channelTrackPreferenceStore = ChannelTrackPreferenceStore(this)
        channelListFilterStore = ChannelListFilterStore(this)
        sleepTimerStore = SleepTimerStore(this)
        parentalControlStore = ParentalControlStore(this)
        iptvResumeStore = IptvResumeStore(this)
        iptvViewPreferencesStore = IptvViewPreferencesStore(this)
        applyIptvAspectMode(iptvViewPreferencesStore.aspectMode())
        homeRecentChannelsPublisher = HomeRecentChannelsPublisher(this) { op, sourceKey, error ->
            debugLog.recordDebug(
                "HOME_WATCH_NEXT_FAILURE | op=$op, sourceKey=$sourceKey, " +
                    "err=${error.javaClass.simpleName}: ${error.message}",
            )
        }
        lifecycleScope.launch(Dispatchers.IO) {
            homeRecentChannelsPublisher.cleanupLegacyLiveChannels()
            homeRecentChannelsPublisher.ensurePreviewChannel()
        }
        pendingHomeChannelKey = intent.data?.getQueryParameter("sourceKey")
        pendingExternalChannelUri = intent.getStringExtra(TvChannelViewActivity.EXTRA_TIF_CHANNEL_URI)
        sourceFilter = ChannelSourceFilter.ALL
        favoriteFilter = false
        channelPanelContent = ChannelPanelContent.NORMAL
        setupMultiViewController()
        setupChannelZapper()
        setupTrackSelectionHelper()
        setupTvOverlayLayoutHelper()
        setupIptvControls()
        setupVodPlayerControls()
        debugLog.recordDebug("MAIN_CREATE | savedState=${savedInstanceState != null}")
        debugLog.recordDebug(
            "DEVICE_CAPABILITIES | mode=$experienceMode, " +
                "leanback=${deviceCapabilities.isLeanbackDevice}, " +
                "liveTvFeature=${deviceCapabilities.reportsLiveTvFeature}, " +
                "tvInputManager=${deviceCapabilities.hasTvInputManager}, " +
                "vendorTuners=${deviceCapabilities.vendorTunerInputCount}, " +
                "tvPermission=${deviceCapabilities.hasTvListingsPermission}, " +
                "pip=${deviceCapabilities.supportsPictureInPicture}, " +
                "lowRam=${deviceCapabilities.isLowRamDevice}, " +
                "memoryClassMb=${deviceCapabilities.memoryClassMegabytes}, " +
                "hardwareDecoders=${deviceCapabilities.hardwareVideoDecoderCount}, " +
                "decoderInstances=${deviceCapabilities.maximumConcurrentVideoDecoders}, " +
                "gridLimit=${deviceResourcePolicy.maximumGridStreams}, " +
                "multiView=${deviceResourcePolicy.supportsMultiView}",
        )
        if (intent.getBooleanExtra(BootLaunchReceiver.EXTRA_STARTED_AFTER_BOOT, false)) {
            debugLog.recordDebug("MAIN_STARTED_AFTER_BOOT")
        }
        playback.onTracksChanged = { tracks ->
            logTifTracks(tracks)
            applyPreferredTracks()
        }
        playback.onVideoSizeChanged = { width, height ->
            binding.tvView.setVideoSize(width, height)
            debugLog.recordDebug(
                "TIF_VIDEO_SIZE_RAW | channel=${currentChannel?.sourceKey}, width=$width, height=$height",
            )
        }
        playback.onCallbackEvent = { event ->
            val detail = event.values.entries.joinToString { (key, value) -> "$key=$value" }
            val signature = "${currentChannel?.sourceKey}|${event.name}|$detail"
            if (signature != lastTifCallbackLogSignature) {
                lastTifCallbackLogSignature = signature
                debugLog.recordDebug("TIF_CALLBACK_RAW | event=${event.name}, $detail")
            }
        }
        playback.onVideoStateChanged = { available, _ ->
            val channel = currentChannel
            if (channel != null && channel.source == LiveChannel.Source.TIF) {
                if (available) recordTuneReady(channel)
                if (available && blackScreenActive) endBlackout("TIF_VIDEO_AVAILABLE")
                when {
                    channel.isRadioChannel() -> updateAudioOnlyPanel(channel, true)
                    available -> updateAudioOnlyPanel(channel, false)
                    else -> updateAudioOnlyPanel(channel, false)
                }
            }
        }
        iptvPlayback.onPlaybackError = { error ->
            if (currentChannel?.sourceKey != iptvPlayback.tunedSourceKey()) {
                debugLog.recordDebug(
                    "IPTV_STALE_ERROR_DROPPED | tuned=${iptvPlayback.tunedSourceKey()}, " +
                        "current=${currentChannel?.sourceKey}",
                )
            } else {
                handleIptvPlaybackError(error)
            }
        }
        iptvPlayback.onPlaybackReady = {
            if (currentChannel?.sourceKey == iptvPlayback.tunedSourceKey()) {
                val recoveredFromFailure = iptvPlaybackFailed
                iptvPlaybackFailed = false
                iptvExhaustedRetuneKey = null
                iptvExhaustedRetuneCount = 0
                setIptvBufferingVisible(false)
                if (blackScreenActive) endBlackout("IPTV_PLAYBACK_READY")
                if (recoveredFromFailure && binding.channelPanel.visibility != View.VISIBLE) {
                    hideIptvPlaybackControls()
                }
                if (osdCoordinator.state.primaryOsd == PrimaryOsd.STATUS &&
                    currentChannel?.source == LiveChannel.Source.IPTV &&
                    binding.channelPanel.visibility != View.VISIBLE
                ) {
                    osdCoordinator.hideStatus()
                }
                currentChannel?.let { channel ->
                    recordTuneReady(channel)
                    updateTechnicalBadgesForIptv(channel)
                }
                if (vodMode) {
                    vodPlayerOverlayController?.scheduleAutoHide()
                }
            }
        }
        iptvPlayback.onPlaybackEnded = {
            val channel = currentChannel
            if (channel != null && currentIptvContentKind == IptvContentKind.VOD) {
                iptvResumeStore.clear(channel.sourceKey)
                lifecycleScope.launch(Dispatchers.IO) {
                    homeRecentChannelsPublisher.removeVod(channel.sourceKey)
                }
            }
        }
        iptvPlayback.onBuffering = { state ->
            if (currentChannel?.sourceKey == iptvPlayback.tunedSourceKey() &&
                currentChannel?.source == LiveChannel.Source.IPTV
            ) {
                when (state) {
                    IptvBufferingState.NONE -> setIptvBufferingVisible(false)
                    IptvBufferingState.LOADING -> {
                        updateIptvBufferingStatus(R.string.iptv_opening)
                        setIptvBufferingVisible(true)
                    }
                    IptvBufferingState.BUFFERING -> {
                        setIptvBufferingVisible(true, compact = true)
                    }
                }
            } else {
                setIptvBufferingVisible(false)
            }
        }
        iptvPlayback.onContentKindChanged = { kind ->
            if (currentChannel?.sourceKey == iptvPlayback.tunedSourceKey() &&
                currentChannel?.source == LiveChannel.Source.IPTV
            ) {
                currentIptvContentKind = kind
                startIptvPlaybackMonitor(kind)
            }
        }
        iptvPlayback.onTracksChanged = {
            if (currentChannel?.sourceKey == iptvPlayback.tunedSourceKey() &&
                currentChannel?.source == LiveChannel.Source.IPTV
            ) {
                applyPreferredIptvTracks()
                currentChannel?.let(::updateTechnicalBadgesForIptv)
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

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) = Unit
        })
        binding.retryButton.setOnClickListener {
            statusRetryAction?.invoke() ?: ensurePermissionAndLoad()
        }
        binding.closeButton.setOnClickListener {
            dismissStatusDialog()
        }
        binding.scanButton.setOnClickListener { openTunerSetup() }
        binding.allFilter.setOnClickListener {
            applyChannelFilter(source = ChannelSourceFilter.ALL)
        }
        binding.satelliteFilter.setOnClickListener {
            applyChannelFilter(source = ChannelSourceFilter.SATELLITE)
        }
        binding.radioFilter.setOnClickListener {
            applyChannelFilter(source = ChannelSourceFilter.RADIO)
        }
        binding.iptvFilter.setOnClickListener {
            applyChannelFilter(source = ChannelSourceFilter.IPTV)
        }
        binding.favoriteFilter.setOnClickListener {
            applyChannelFilter(showFavorites = !favoriteFilter)
        }
        iptvPlayback.onHealthChanged = { health ->
            val channel = currentChannel?.takeIf { it.source == LiveChannel.Source.IPTV }
            val signature = listOf(
                channel?.id,
                health.phase,
                health.firstFrameRendered,
                health.retryAttempt,
                health.lastErrorCode,
            ).joinToString("|")
            if (signature != lastIptvHealthLogSignature) {
                lastIptvHealthLogSignature = signature
                lifecycleScope.launch(Dispatchers.IO) {
                    debugLog.recordDebug(
                        "IPTV_HEALTH | channelId=${channel?.id ?: -1}, " +
                            "phase=${health.phase}, kind=${health.contentKind}, " +
                            "playing=${health.isPlaying}, firstFrame=${health.firstFrameRendered}, " +
                            "startupMs=${health.startupDurationMillis ?: -1}, " +
                            "resolution=${health.width ?: 0}x${health.height ?: 0}, " +
                            "videoCodec=${health.videoCodec ?: "unknown"}, " +
                            "audioCodec=${health.audioCodec ?: "unknown"}, " +
                            "bitrate=${health.bitrateBps ?: -1}, " +
                            "bandwidth=${health.estimatedBandwidthBps ?: -1}, " +
                            "bufferMs=${health.bufferedDurationMillis}, dropped=${health.droppedFrames}, " +
                            "dropRate=${health.droppedFrameRate ?: -1f}, fps=${health.framesPerSecond ?: -1f}, " +
                            "container=${health.containerFormat ?: "unknown"}, " +
                            "profile=${health.videoProfile ?: "unknown"}, " +
                            "audioHz=${health.audioSampleRateHz ?: -1}, " +
                            "audioChannels=${health.audioChannelCount ?: -1}, " +
                            "retry=${health.retryAttempt}, failure=${health.lastFailureClass}, " +
                            "error=${health.lastErrorCode ?: "none"}",
                    )
                }
            }
        }
        iptvPlayback.onRecovery = { recovery ->
            val channelId = currentChannel?.takeIf { it.source == LiveChannel.Source.IPTV }?.id ?: -1L
            lifecycleScope.launch(Dispatchers.IO) {
                debugLog.recordDebug(
                    "IPTV_RECOVERY | channelId=$channelId, reason=${recovery.reason}, " +
                        "action=${recovery.action}, attempt=${recovery.attempt}",
                )
            }
            if (recovery.action == IptvRecoveryAction.REPREPARE) {
                showIptvNotice(R.string.iptv_reconnecting)
            }
        }
        binding.channelSearchButton.setOnClickListener { showChannelSearchDialog() }
        setupMobileTouchControls()
        setupMobileColorActions()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackNavigation()
            }
        })

        applyDisplayPreferences()
        startClock()
        scheduleSleepTimer()
        ensurePermissionAndLoad()
        // Cold-start deep link from VodHomeActivity (singleTop may deliver via
        // onCreate instead of onNewIntent when the task is freshly created).
        handleVodSourceDeepLink(intent)
        recordPerformance("activity_create", activityStartAt, "mode=$experienceMode")
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
        osdCoordinator.showStatus()
        binding.statusTitle.text = title
        binding.statusMessage.text = detail.orEmpty().ifBlank { getString(R.string.no_detail) }
        binding.closeButton.requestFocus()
    }

    private fun ensurePermissionAndLoad() {
        if (experienceMode != ExperienceMode.HYBRID_TV) {
            debugLog.recordDebug("TIF_SKIPPED | mode=$experienceMode, loading IPTV-only data")
            loadChannels()
            return
        }
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
        osdCoordinator.showStatus()
        binding.statusTitle.setText(R.string.permission_required_title)
        binding.statusMessage.setText(R.string.permission_required_message)
        tvListingsPermission.launch(READ_TV_LISTINGS)
    }

    private fun loadChannels(preserveCurrentPlayback: Boolean = false) {
        val loadStartedAt = SystemClock.elapsedRealtime()
        val includeTif = experienceMode == ExperienceMode.HYBRID_TV
        debugLog.recordDebug(
            "CHANNEL_LOAD_START | includeTif=$includeTif, preserve=$preserveCurrentPlayback",
        )
        refreshIptvSourceNames()
        if (channels.isEmpty()) {
            val cached = ChannelRepository.cachedChannels()
            if (cached.isNotEmpty()) {
                channels = cached
                applyChannelFilter(requestFocus = false)
                showChannels(cached)
            }
        }

        channelLoadJob?.cancel()
        channelLoadJob = lifecycleScope.launch {
            val inputs = withContext(Dispatchers.IO) {
                if (includeTif) repository.tunerInputs() else emptyList()
            }
            binding.inputSummary.text = resources.getQuantityString(
                R.plurals.input_count,
                inputs.size,
                inputs.size,
            )
            val result = withContext(Dispatchers.IO) {
                repository.channels(includeTif = includeTif)
            }
            result.fold(
                onSuccess = success@ { loaded ->
                    debugLog.recordDebug("CHANNEL_LOAD_SUCCESS | count=${loaded.size}")
                    recordPerformance(
                        "channel_load",
                        loadStartedAt,
                        "count=${loaded.size}, includeTif=$includeTif",
                    )
                    val currentKey = currentChannel?.sourceKey
                    val playingChannel = currentChannel
                    channels = loaded
                    currentChannel = loaded.firstOrNull { it.sourceKey == currentKey }
                        ?: playingChannel.takeIf { preserveCurrentPlayback || vodMode }
                    applyChannelFilter(requestFocus = false)
                    startEpgRefresh()
                    checkAndScheduleEpgAutoRefresh()
                    if (!vodMode) {
                        if (loaded.isEmpty()) showEmptyState(inputs) else showChannels(loaded)
                    } else {
                        osdCoordinator.hideStatus()
                    }
                    lifecycleScope.launch(Dispatchers.IO) {
                        homeRecentChannelsPublisher.ensurePreviewChannel(loaded)
                        homeRecentChannelsPublisher.syncResumeVods(iptvRepository, iptvResumeStore)
                    }
                    if (vodMode) return@success
                    val editorChannelKey = pendingEditorChannelKey
                    pendingEditorChannelKey = null
                    val requestedKey = pendingHomeChannelKey ?: editorChannelKey
                    pendingHomeChannelKey = null
                    val requestedUri = pendingExternalChannelUri
                    pendingExternalChannelUri = null
                    val editorChannel = requestedUri?.let { uri ->
                        loaded.firstOrNull { it.uri == uri }
                    } ?: requestedKey?.let { key ->
                        panelChannels().firstOrNull { it.sourceKey == key }
                            ?: loaded.firstOrNull { it.sourceKey == key }
                    }
                    if (editorChannel != null) {
                        restoredInitialChannel = true
                        selectChannel(editorChannel)
                    } else if (requestedKey != null && requestedKey.startsWith("iptv:")) {
                        val startupChannels = panelChannels().ifEmpty { loaded.filterNot { it.isVodContent() } }
                        lifecycleScope.launch {
                            val resolved = withContext(Dispatchers.IO) {
                                iptvRepository.channel(requestedKey)
                            }
                            if (resolved != null) {
                                restoredInitialChannel = true
                                selectChannel(resolved)
                            } else if (!restoredInitialChannel) {
                                restoredInitialChannel = true
                                val lastKey = playbackHistory.keys().firstOrNull()
                                val matchedChannel = lastKey?.let { key ->
                                    startupChannels.firstOrNull { it.sourceKey == key }
                                }
                                if (matchedChannel != null) {
                                    selectChannel(matchedChannel)
                                } else if (lastKey != null && (lastKey.startsWith("iptv:") || BuildConfig.MOBILE_UI_ENABLED)) {
                                    val fallbackResolved = withContext(Dispatchers.IO) {
                                        iptvRepository.channel(lastKey)
                                    }
                                    if (fallbackResolved != null && !fallbackResolved.isVodContent()) {
                                        selectChannel(fallbackResolved)
                                    } else if (startupChannels.isNotEmpty()) {
                                        selectChannel(startupChannels.first())
                                    }
                                } else if (startupChannels.isNotEmpty()) {
                                    selectChannel(startupChannels.first())
                                }
                            }
                        }
                    } else if (!restoredInitialChannel) {
                        restoredInitialChannel = true
                        val startupChannels = panelChannels().ifEmpty { loaded.filterNot { it.isVodContent() } }
                        val lastKey = playbackHistory.keys().firstOrNull()
                        val matchedChannel = lastKey?.let { key ->
                            startupChannels.firstOrNull { it.sourceKey == key }
                        }
                        if (matchedChannel != null) {
                            selectChannel(matchedChannel)
                        } else if (lastKey != null && (lastKey.startsWith("iptv:") || BuildConfig.MOBILE_UI_ENABLED)) {
                            lifecycleScope.launch {
                                val resolved = withContext(Dispatchers.IO) {
                                    iptvRepository.channel(lastKey)
                                }
                                if (resolved != null && !resolved.isVodContent()) {
                                    selectChannel(resolved)
                                } else if (startupChannels.isNotEmpty()) {
                                    selectChannel(startupChannels.first())
                                }
                            }
                        } else if (startupChannels.isNotEmpty()) {
                            selectChannel(startupChannels.first())
                        }
                    }
                },
                onFailure = {
                    recordPerformance(
                        "channel_load_failed",
                        loadStartedAt,
                        "includeTif=$includeTif, error=${it.javaClass.simpleName}",
                    )
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
        binding.retryButton.setText(R.string.retry)
        binding.closeButton.visibility = View.VISIBLE
        osdCoordinator.hideStatus()
        updateChannelCount(panelChannels().size)
    }

    private fun showEmptyState(inputs: List<TvInputInfo>) {
        if (experienceMode != ExperienceMode.HYBRID_TV) {
            statusRetryAction = ::openIptvEditor
            osdCoordinator.showStatus()
            binding.statusTitle.setText(R.string.no_channels_title)
            binding.statusMessage.setText(R.string.iptv_only_empty_message)
            binding.retryButton.setText(R.string.manage_iptv_sources)
            binding.closeButton.visibility = View.VISIBLE
            binding.retryButton.requestFocus()
            return
        }
        statusRetryAction = null
        binding.retryButton.setText(R.string.retry)
        binding.closeButton.visibility = View.VISIBLE
        osdCoordinator.showStatus()
        binding.statusTitle.setText(R.string.no_channels_title)
        binding.statusMessage.text = if (inputs.isEmpty()) {
            getString(R.string.no_inputs_message)
        } else {
            getString(R.string.no_channels_message, inputs.joinToString("\n") { it.id })
        }
    }

    private fun showReadError(inputs: List<TvInputInfo>, error: Throwable) {
        osdCoordinator.showStatus()
        binding.statusTitle.setText(R.string.permission_title)
        binding.statusMessage.text = getString(
            R.string.permission_message,
            error.javaClass.simpleName,
            error.message.orEmpty().ifBlank { getString(R.string.no_detail) },
            inputs.joinToString("\n") { it.id }.ifBlank { getString(R.string.none) },
        )
    }

    private fun showPermissionDenied(inputs: List<TvInputInfo>) {
        osdCoordinator.showStatus()
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
        if (vodMode && !channel.isVodContent()) return
        if (channel.sourceKey != currentChannel?.sourceKey) saveCurrentIptvResumePosition()
        if (channel.isVodContent()) {
            enterVodMode()
            modePreferences.edit().putString(LAST_VOD_KEY, channel.sourceKey).apply()
        }
        val generation = ++channelResolutionGeneration
        if (channel.source == LiveChannel.Source.IPTV) {
            // Give immediate visual feedback before the asynchronous resolution
            // completes: update the infobar and selection so the UI reacts on
            // the first key press instead of appearing unresponsive over TIF.
            if (channel.sourceKey != currentChannel?.sourceKey) {
                currentChannel = channel
                adapter.select(channel.sourceKey)
                updateInfobarChannel(channel)

                updateTechnicalBadges(channel, emptyList())
                if (channel.isVodContent()) {
                    setInfoBarVisible(false)
                    showVodPlayerControls()
                } else {
                    showInfoBar()
                }
                loadPrograms(channel)
                setIptvBufferingVisible(false)
                iptvPlayback.stop()
                updateIptvBufferingStatus(R.string.iptv_connecting)
            }
            lifecycleScope.launch {
                val (resolved, preference) = withContext(Dispatchers.IO) {
                    val resolvedChannel = if (channel.uri.isBlank()) {
                        iptvRepository.channel(channel.sourceKey)
                    } else {
                        channel
                    }
                    resolvedChannel to repository.channelPreference(channel.sourceKey)
                }
                if (generation != channelResolutionGeneration) {
                    setIptvBufferingVisible(false)
                    return@launch
                }
                if (resolved == null) {
                    setIptvBufferingVisible(false)
                    debugLog.recordDebug("IPTV_CHANNEL_RESOLVE_MISSING | key=${channel.sourceKey}")
                    showIptvNotice(R.string.channel_not_found)
                    return@launch
                }
                selectResolvedChannel(
                    resolved.copy(
                        displayNumber = channel.displayNumber,
                        inMainList = channel.inMainList,
                        playbackEngineOverride = preference?.playbackEngineOverride,
                    ),
                    recordHistory,
                )
            }
            return
        }
        selectResolvedChannel(channel, recordHistory)
    }

    private fun selectResolvedChannel(channel: LiveChannel, recordHistory: Boolean) {
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
        pendingTuneStartedAt = SystemClock.elapsedRealtime()
        pendingTuneSourceKey = channel.sourceKey
        adapter.select(channel.sourceKey)
        binding.tvView.visibility = View.GONE
        binding.iptvPlayerView.visibility = View.GONE
        binding.audioOnlyPanel.visibility = View.GONE
        osdCoordinator.hideStatus()
        binding.parentalLockChannel.text = channel.displayName
        osdCoordinator.showParentalLock()
        updateInfobarChannel(channel)

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

    /** Black-screen-while-tuning: optionally hides the previous channel's
     *  frozen video under the OSD from channel switch until the new channel
     *  really renders. The infobar stays visible on top so the user can still
     *  read what they switched to. While the blackout is active the previous
     *  channel's audio is muted too, and playback.unmutedByPlayback restores
     *  the level when the new channel renders (or on a failure path). */
    private fun startBlackout() {
        if (!displayPreferences.blackScreenWhileTuning) return
        if (blackScreenActive) return
        val target = binding.blackoutView
        target.visibility = View.VISIBLE
        binding.iptvBufferingContainer.visibility = View.GONE
        binding.blackoutAnimation.apply {
            if (composition == null) {
                setFailureListener { error ->
                    debugLog.recordDebug("TUNING_ANIMATION_FAILURE | ${error.javaClass.simpleName}")
                }
                setAnimation(R.raw.channel_tuning)
            }
            playAnimation()
        }
        blackScreenActive = true
        blackScreenTuneGeneration++
        playback.setMuted(true)
        debugLog.recordDebug("BLACKOUT_START | generation=$blackScreenTuneGeneration")
    }

    private fun endBlackout(reason: String) {
        if (!blackScreenActive) return
        blackScreenActive = false
        binding.blackoutView.visibility = View.GONE
        binding.blackoutAnimation.cancelAnimation()
        playback.setMuted(false)
        debugLog.recordDebug("BLACKOUT_END | reason=$reason")
    }

    private fun playSelectedChannel(channel: LiveChannel, recordHistory: Boolean = true) {
        if (vodMode && !channel.isVodContent()) return
        if (channel.source == LiveChannel.Source.IPTV && !hasIptvAccess {
                playSelectedChannel(channel, recordHistory)
            }
        ) return
        if (iptvOverlayActive) stopIptvOverlay()
        if (iptvGridActive) stopIptvGrid(resumePrevious = false)
        // Repeat OK presses on the channel that is already connecting must not
        // restart playback and reset the retry/alternative-stream cycle.
        if (channel.sourceKey == currentChannel?.sourceKey) {
            val phase = iptvPlayback.healthSnapshot().phase
            if (phase == IptvPlaybackPhase.PREPARING || phase == IptvPlaybackPhase.BUFFERING) {
                debugLog.recordDebug(
                    "CHANNEL_SELECT_IGNORED | key=${channel.sourceKey}, phase=$phase",
                )
                return
            }
        }
        saveCurrentIptvResumePosition()
        debugLog.recordDebug(
            "CHANNEL_SELECT | number=${channel.displayNumber}, key=${channel.sourceKey}",
        )
        // Hide the old channel's video behind a black layer; the infobar above
        // it stays readable. If this tune renders nothing, the failure/error
        // paths end the blackout instead of leaving a permanent black screen.
        startBlackout()
        focusedTuneJob?.cancel()
        activePassthroughInputId = null
        currentPlaybackUsesIptvLibrary = !recordHistory
        currentChannel = channel
        if (!channel.iptvContentType.equals("CATCHUP", ignoreCase = true)) {
            catchUpReturnChannel = null
        }
        updateChannelActionLabels()
        currentIptvContentKind = IptvContentKind.UNKNOWN
        iptvManualTimeshift = false
        iptvPlaybackFailed = false
        hideIptvPlaybackControls()
        iptvNoticeJob?.cancel()
        iptvLiveHealthJob?.cancel()
        binding.iptvNotice.visibility = View.GONE
        osdCoordinator.hideParentalLock()
        if (channel.isVodContent()) {
            vodHistory.record(channel.sourceKey)
        } else if (recordHistory) {
            playbackHistory.record(channel.sourceKey)
            homeRecentPublishJob?.cancel()
            homeRecentPublishJob = lifecycleScope.launch(Dispatchers.IO) {
                delay(2_500L)
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
        updateInfobarChannel(channel)

        updateTechnicalBadges(channel, emptyList())
        updateAudioOnlyPanel(channel, channel.isRadioChannel())
        osdCoordinator.hideStatus()
        statusRetryAction = null
        if (channel.isVodContent()) {
            setInfoBarVisible(false)
            showVodPlayerControls()
        } else {
            showInfoBar()
        }
        loadPrograms(channel)
        runCatching {
            when (channel.source) {
                LiveChannel.Source.TIF -> {
                    debugLog.recordDebug(
                        "TIF_CHANNEL_RAW | key=${channel.sourceKey}, inputId=${channel.inputId}, " +
                            "uri=${channel.uri}, number=${channel.displayNumber}, " +
                            "name=${channel.displayName}, videoFormat=${channel.videoFormat}, " +
                            "serviceType=${channel.serviceType}, encrypted=${channel.encrypted}, " +
                            "locked=${channel.locked}",
                    )
                    setIptvBufferingVisible(false)
                    iptvPlayback.stop()
                    binding.iptvPlayerView.visibility = View.GONE
                    binding.tvView.visibility = View.VISIBLE
                    playback.play(channel)
                    applyPreferredTracks()
                }
                LiveChannel.Source.IPTV -> {
                    playback.stop()
                    binding.tvView.visibility = View.GONE
                    binding.iptvPlayerView.visibility = View.VISIBLE
                    updateIptvBufferingStatus(R.string.iptv_connecting)
                    setIptvBufferingVisible(true)
                    val resumePosition = if (channel.iptvContentType == "VOD") {
                        iptvResumeStore.position(channel.sourceKey)
                    } else {
                        0L
                    }
                    prepareIptvAlternatives(channel)
                    updateTechnicalBadgesForIptv(channel)
                    iptvPlayback.play(channel, resumePosition)
                    if (resumePosition > 0L) {
                        showIptvPlaybackControls(R.string.iptv_resumed)
                    }
                }
            }
        }
            .onFailure {
                debugLog.recordDebug("PLAYBACK_FAILURE | ${it.javaClass.name}: ${it.message}")
                if (channel.source == LiveChannel.Source.IPTV) {
                    showIptvPlaybackFailure(it)
                } else {
                    setIptvBufferingVisible(false)
                    showPlaybackError(channel, it.message ?: it.javaClass.simpleName)
                }
            }
    }

    /** Shows or hides the IPTV spinner, but never lets a stale callback from a
     *  previous channel keep it on screen: when no IPTV channel is currently
     *  tuned, the spinner is always hidden. */
    private fun setIptvBufferingVisible(visible: Boolean, compact: Boolean = false) {
        val shouldShow = visible && !blackScreenActive && currentChannel?.source == LiveChannel.Source.IPTV
        binding.iptvBufferingCorner.visibility = if (shouldShow && compact) View.VISIBLE else View.GONE
        if (compact) {
            binding.iptvBufferingContainer.visibility = View.GONE
            return
        }
        if (shouldShow) {
            binding.iptvBufferingSpinner.visibility = View.VISIBLE
            binding.iptvBufferingContainer.visibility = View.VISIBLE
        } else {
            binding.iptvBufferingContainer.visibility = View.GONE
            binding.iptvBufferingSpinner.visibility = View.VISIBLE
        }
    }

    private fun updateIptvBufferingStatus(statusResId: Int) {
        val status = getString(statusResId)
        val channelName = currentChannel
            ?.takeIf { it.source == LiveChannel.Source.IPTV }
            ?.displayName
            ?.trim()
            ?.takeIf(String::isNotBlank)
        binding.iptvBufferingText.text = channelName?.let {
            getString(R.string.iptv_playback_status, it, status)
        } ?: status
    }

    private fun prepareIptvAlternatives(channel: LiveChannel) {
        iptvAlternativeLoadKey = channel.sourceKey
        iptvAlternativeStreams = emptyList()
        iptvAlternativeIndex = 0
        lifecycleScope.launch {
            val alternatives = withContext(Dispatchers.IO) {
                iptvRepository.alternativeStreams(channel.sourceKey)
            }
            if (currentChannel?.sourceKey == channel.sourceKey) {
                iptvAlternativeStreams = alternatives
                debugLog.recordDebug(
                    "IPTV_ALTERNATIVES_READY | key=${channel.sourceKey}, count=${alternatives.size}",
                )
            }
        }
    }

    private fun handleIptvPlaybackError(error: androidx.media3.common.PlaybackException) {
        val channel = currentChannel
        debugLog.recordDebug("IPTV_PLAYBACK_FAILURE | ${error.errorCodeName}")
        if (channel?.source != LiveChannel.Source.IPTV) {
            showPlaybackError(channel, error.message ?: error.errorCodeName)
            return
        }
        if (!iptvPlayback.automaticRecoveryEnabled()) {
            showPlaybackError(channel, error.message ?: error.errorCodeName)
            return
        }
        lifecycleScope.launch {
            val alternatives = if (iptvAlternativeLoadKey == channel.sourceKey &&
                iptvAlternativeStreams.isNotEmpty()
            ) {
                iptvAlternativeStreams
            } else {
                runCatching {
                    withContext(Dispatchers.IO) {
                        iptvRepository.alternativeStreams(channel.sourceKey)
                    }
                }.getOrElse { fetchError ->
                    debugLog.recordDebug(
                        "IPTV_ALTERNATIVES_FAILURE | key=${channel.sourceKey}, " +
                            "${fetchError.javaClass.simpleName}: ${fetchError.message}",
                    )
                    showIptvPlaybackFailure(fetchError)
                    recoverIptvExhaustedTune(channel)
                    return@launch
                }.also {
                    iptvAlternativeLoadKey = channel.sourceKey
                    iptvAlternativeStreams = it
                    iptvAlternativeIndex = 0
                }
            }
            if (currentChannel?.sourceKey != channel.sourceKey) return@launch
            val alternative = alternatives.getOrNull(iptvAlternativeIndex++)
            if (alternative == null) {
                showIptvPlaybackFailure(error)
                recoverIptvExhaustedTune(channel)
                return@launch
            }
            val snapshot = iptvPlayback.playbackSnapshot()
            val startPosition = snapshot.positionMillis.takeIf {
                channel.iptvContentType == "VOD"
            } ?: 0L
            debugLog.recordDebug(
                "IPTV_ALTERNATIVE_PLAY | key=${channel.sourceKey}, " +
                    "index=$iptvAlternativeIndex/${alternatives.size}",
            )
            showIptvNotice(R.string.iptv_alternative_stream)
            // A failed alternative switch must never take the app down: the
            // exception is contained here and surfaces as the failure OSD.
            runCatching {
                iptvPlayback.play(
                    channel.copy(
                        uri = alternative.uri,
                        inputId = alternative.inputId,
                        userAgent = alternative.userAgent,
                        referrer = alternative.referrer,
                        origin = alternative.origin,
                        subtitleUrl = alternative.subtitleUrl,
                    ),
                    startPosition,
                )
            }.onFailure {
                debugLog.recordDebug(
                    "IPTV_ALTERNATIVE_PLAY_FAILURE | key=${channel.sourceKey}, " +
                        "${it.javaClass.simpleName}: ${it.message}",
                )
                iptvAlternativeIndex--
                showIptvPlaybackFailure(it)
                recoverIptvExhaustedTune(channel)
            }
        }
    }

    /** Last-resort guard for a stream that freezes and never recovers: the
     *  watchdog has already re-prepared MAX_WATCHDOG_RECOVERY_COUNT times and
     *  every alternative stream has failed, so the tune would otherwise die
     *  silently on a frozen frame. Performs a bounded number of automatic
     *  full re-tunes of the same channel; when they are used up the failure
     *  OSD with the retry button stays the only recovery path. */
    private fun recoverIptvExhaustedTune(channel: LiveChannel) {
        if (currentChannel?.sourceKey != channel.sourceKey) return
        if (iptvExhaustedRetuneKey != channel.sourceKey) {
            iptvExhaustedRetuneKey = channel.sourceKey
            iptvExhaustedRetuneCount = 0
        }
        if (iptvExhaustedRetuneCount >= IPTV_EXHAUSTED_RETUNE_LIMIT) {
            debugLog.recordDebug(
                "IPTV_EXHAUSTED_RETUNE_GIVE_UP | key=${channel.sourceKey}, " +
                    "attempts=$iptvExhaustedRetuneCount",
            )
            return
        }
        iptvExhaustedRetuneCount++
        val attempt = iptvExhaustedRetuneCount
        debugLog.recordDebug(
            "IPTV_EXHAUSTED_RETUNE | key=${channel.sourceKey}, " +
                "attempt=$attempt/$IPTV_EXHAUSTED_RETUNE_LIMIT",
        )
        showIptvNotice(R.string.iptv_reconnecting)
        // stop() resets the tuned key and playback state so selectChannel is
        // not swallowed by the CHANNEL_SELECT_IGNORED connecting guard, and
        // the fresh tune restarts the watchdog retry/alternative cycle.
        iptvPlayback.stop()
        selectChannel(channel, recordHistory = !currentPlaybackUsesIptvLibrary)
    }

    private fun showIptvPlaybackFailure(error: Throwable? = null) {
        endBlackout("IPTV_FAILURE")
        iptvPlaybackFailed = true
        osdCoordinator.hideStatus()
        showIptvPlaybackControls(R.string.iptv_stream_failed, autoHide = false)
        if (currentChannel?.source == LiveChannel.Source.IPTV) {
            binding.iptvBufferingSpinner.visibility = View.GONE
            binding.iptvBufferingText.text = formatIptvErrorMessage(error)
            binding.iptvBufferingContainer.visibility = View.VISIBLE
        }
    }

    private fun formatIptvErrorMessage(error: Throwable?): String {
        val channelName = currentChannel
            ?.takeIf { it.source == LiveChannel.Source.IPTV }
            ?.displayName
            ?.trim()
            ?.takeIf(String::isNotBlank)

        val baseReason = when (error) {
            is androidx.media3.common.PlaybackException -> {
                val httpCode = findHttpErrorCode(error)
                when {
                    httpCode == 403 || httpCode == 401 -> getString(R.string.iptv_error_http_forbidden, httpCode)
                    httpCode == 404 -> getString(R.string.iptv_error_http_not_found)
                    httpCode != null && httpCode >= 500 -> getString(R.string.iptv_error_http_server, httpCode)
                    httpCode != null -> getString(R.string.iptv_error_http_generic, httpCode)
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                        getString(R.string.iptv_error_network)
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        getString(R.string.iptv_error_timeout)
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                        getString(R.string.iptv_error_decoder)
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
                        getString(R.string.iptv_error_format)
                    else -> getString(R.string.iptv_error_generic)
                }
            }
            null -> getString(R.string.iptv_error_generic)
            else -> error.localizedMessage ?: getString(R.string.iptv_error_generic)
        }

        val fullMessage = if (channelName != null) {
            getString(R.string.iptv_playback_status, channelName, baseReason)
        } else {
            baseReason
        }

        return if (displayPreferences.showDiagnosticsOverlay && error != null) {
            val techDetail = if (error is androidx.media3.common.PlaybackException) {
                "${error.errorCodeName} (${error.errorCode})"
            } else {
                "${error.javaClass.simpleName}: ${error.message}"
            }
            "$fullMessage\n[$techDetail]"
        } else {
            fullMessage
        }
    }

    private fun findHttpErrorCode(throwable: Throwable): Int? {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                return current.responseCode
            }
            current = current.cause
        }
        return null
    }

    private fun showPlaybackError(channel: LiveChannel?, detail: String) {
        endBlackout("PLAYBACK_ERROR")
        if (binding.channelPanel.visibility == View.VISIBLE ||
            osdCoordinator.state.primaryOsd == PrimaryOsd.CHANNEL_PANEL ||
            (vodMode && !isFinishing && currentChannel == null)
        ) {
            showIptvNotice(R.string.iptv_stream_failed)
            return
        }
        osdCoordinator.showStatus()
        binding.statusTitle.setText(R.string.playback_error_title)
        binding.inputSummary.text = channel?.displayName.orEmpty()
        binding.statusMessage.text = detail
        statusRetryAction = channel?.let { failedChannel ->
            { selectChannel(failedChannel, recordHistory = !currentPlaybackUsesIptvLibrary) }
        }
        binding.statusPanel.post {
            binding.retryButton.requestFocus()
        }
    }

    private fun dismissStatusDialog() {
        vodResolveJob?.cancel()
        vodResolveJob = null
        iptvPlayback.stop()
        binding.iptvBufferingContainer.visibility = View.GONE
        statusRetryAction = null
        osdCoordinator.hideStatus()
        modePreferences.edit().remove(LAST_VOD_KEY).apply()
        if (vodMode) {
            openVodLibrary()
        }
    }

    private fun confirmListChannel(channel: LiveChannel) {
        if (multiViewAdding) {
            toggleMultiViewChannel(channel)
            return
        }
        focusedTuneJob?.cancel()
        focusedAutoTunePreviousChannel = null
        focusedAutoTuneTargetKey = null
        val recordHistory = channelPanelContent == ChannelPanelContent.NORMAL || BuildConfig.MOBILE_UI_ENABLED
        if (channel.sourceKey != currentChannel?.sourceKey) selectChannel(channel, recordHistory)
        hideChannelPanel()
    }

    private fun setupChannelZapper() {
        channelZapper = ChannelZapper(
            binding = binding,
            scope = lifecycleScope,
            showInfoBar = { showInfoBar() },
            getNavigationChannels = { playbackNavigationChannels() },
            getCurrentChannel = { currentChannel },
            selectChannel = { ch, record -> selectChannel(ch, record) },
            shouldRecordNavigationHistory = { shouldRecordNavigationHistory() },
            isMultiViewAdding = { multiViewAdding },
            toggleMultiViewChannel = { ch -> toggleMultiViewChannel(ch) },
            isChannelPanelVisible = { binding.channelPanel.visibility == View.VISIBLE },
            hideChannelPanel = { hideChannelPanel() },
            focusCurrentListChannel = { focusCurrentListChannel() },
            pageChannelList = { offset -> pageChannelList(offset) },
            isIptvLibraryContent = { channelPanelContent == ChannelPanelContent.IPTV_LIBRARY },
            openIptvLibraryNumber = { num -> openIptvLibraryNumber(num) },
            isVodMode = { vodMode },
            isCurrentPlaybackUsesIptvLibrary = { currentPlaybackUsesIptvLibrary },
            getHistoryKeys = { playbackHistory.keys() },
        )
    }

    private fun zap(offset: Int) {
        channelZapper.zap(offset)
    }

    private fun appendChannelDigit(digit: Int) {
        channelZapper.appendChannelDigit(digit)
    }

    private fun commitChannelNumber() {
        channelZapper.commitChannelNumber()
    }

    private fun openPreviousChannel() {
        channelZapper.openPreviousChannel()
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
        if (!displayPreferences.channelFocusAutoTune || alphabetBrowsing || multiViewAdding || channel.isVodContent()) return
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
        loadProgramWindow(channel.sourceKey, EPG_FOCUS_DEBOUNCE_MS)
    }

    private fun recordTuneReady(channel: LiveChannel) {
        if (pendingTuneSourceKey != channel.sourceKey || pendingTuneStartedAt <= 0L) return
        recordPerformance(
            "channel_tune_ready",
            pendingTuneStartedAt,
            "source=${channel.source}, mode=$experienceMode",
        )
        pendingTuneStartedAt = 0L
        pendingTuneSourceKey = null
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupMobileTouchControls() {
        if (!BuildConfig.MOBILE_UI_ENABLED) return
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                if (binding.channelPanel.visibility == View.VISIBLE) {
                    hideChannelPanel()
                } else if (binding.infoBar.visibility == View.VISIBLE) {
                    setInfoBarVisible(false)
                } else {
                    currentChannel?.let(::showInfoBarForChannel) ?: showInfoBar()
                }
                return true
            }

            override fun onFling(
                start: MotionEvent?,
                end: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                start ?: return false
                val deltaX = end.x - start.x
                val deltaY = end.y - start.y
                val threshold = dp(MOBILE_SWIPE_DISTANCE_DP).toFloat()
                return if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)) {
                    if (kotlin.math.abs(deltaY) < threshold) return false
                    zap(if (deltaY < 0) 1 else -1)
                    true
                } else {
                    if (kotlin.math.abs(deltaX) < threshold) return false
                    openMobileChannelPanel()
                    true
                }
            }
        })
        val listener = View.OnTouchListener { _, event -> detector.onTouchEvent(event) }
        binding.tvView.setOnTouchListener(listener)
        binding.iptvPlayerView.setOnTouchListener(listener)
    }

    private fun setupMobileColorActions() {
        if (!BuildConfig.MOBILE_UI_ENABLED) return
        fun touchTarget(view: View, action: () -> Unit) {
            view.isClickable = true
            view.isFocusable = false
            view.foreground = ContextCompat.getDrawable(
                this,
                android.R.drawable.list_selector_background,
            )
            view.setOnClickListener { action() }
        }
        touchTarget(binding.channelActionRed) {
            if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
                openIptvEditor()
            } else {
                openChannelEditor()
            }
        }
        touchTarget(binding.channelActionGreen) {
            showIptvGridPicker()
        }
        binding.channelActionGreen.setOnLongClickListener {
            showIptvGridPicker()
            true
        }
        touchTarget(binding.channelActionYellow) {
            if (BuildConfig.MOBILE_UI_ENABLED) showIptvLibraryFilterDialog()
            else cycleChannelListMode()
        }
        binding.channelActionYellow.setOnLongClickListener {
            if (BuildConfig.MOBILE_UI_ENABLED) showIptvLibraryFilterDialog()
            else showChannelListModeDialog()
            true
        }
        touchTarget(binding.channelActionBlue) {
            if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
                showIptvLibraryFilterDialog()
            } else {
                showChannelSearchDialog()
            }
        }
        updateMobileChannelActionState()
    }

    private fun updateMobileChannelActionState() {
        if (!BuildConfig.MOBILE_UI_ENABLED) return
        binding.channelActionBlue.isEnabled = true
        binding.channelActionBlue.alpha = 1f
    }

    private fun openMobileChannelPanel() {
        if (vodMode) {
            openVodLibrary()
            return
        }
        if (BuildConfig.MOBILE_UI_ENABLED) openSavedIptvLibrary()
        else showChannelPanel(expanded = false)
    }

    private fun loadVisiblePrograms() {
        if (binding.channelPanel.visibility != View.VISIBLE) return
        val layoutManager = binding.channelList.layoutManager as? LinearLayoutManager ?: return
        val first = layoutManager.findFirstVisibleItemPosition().takeIf { it >= 0 } ?: return
        val last = layoutManager.findLastVisibleItemPosition().takeIf { it >= first } ?: return
        val list = panelChannels()
        val centerKey = focusedListSourceKey?.takeIf { key ->
            list.any { it.sourceKey == key }
        } ?: list.getOrNull((first + last) / 2)?.sourceKey ?: return
        loadProgramWindow(centerKey, 0L)
    }

    private fun loadProgramWindow(centerSourceKey: String, debounceMillis: Long) {
        visibleProgramsJob?.cancel()
        val requestId = ++visibleProgramsRequestId
        visibleProgramsJob = lifecycleScope.launch {
            if (debounceMillis > 0L) delay(debounceMillis)
            if (
                binding.channelPanel.visibility != View.VISIBLE ||
                (debounceMillis > 0L && focusedListSourceKey != centerSourceKey)
            ) return@launch
            val window = programWindow(centerSourceKey)
            val prioritizedWindow = window.sortedByDescending {
                it.sourceKey == centerSourceKey
            }
            val now = System.currentTimeMillis()
            val missing = window.filter { channel ->
                currentPrograms[channel.sourceKey]?.let {
                    now in it.startTimeMillis until it.endTimeMillis
                } != true
            }
            if (missing.isEmpty()) return@launch
            val epgStartedAt = SystemClock.elapsedRealtime()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    programRepository.currentProgramsForListWindow(
                        prioritizedWindow.filter { candidate ->
                            missing.any { it.sourceKey == candidate.sourceKey }
                        },
                    )
                }
            }
            if (
                requestId != visibleProgramsRequestId ||
                binding.channelPanel.visibility != View.VISIBLE ||
                (focusedListSourceKey != null && focusedListSourceKey != centerSourceKey)
            ) return@launch
            val programs = result.getOrNull() ?: return@launch
            debugLog.recordDebug(
                "EPG_LIST_WINDOW_RESULT | center=$centerSourceKey, " +
                    "requested=${missing.size}, found=${programs.size}, " +
                    "tif=${missing.count { it.source == LiveChannel.Source.TIF }}, " +
                    "iptv=${missing.count { it.source == LiveChannel.Source.IPTV }}, " +
                    "durationMs=${SystemClock.elapsedRealtime() - epgStartedAt}",
            )
            updateCachedPrograms(missing, programs)
            missing.forEach { channel ->
                adapter.submitProgram(channel.sourceKey, programs[channel.sourceKey])
            }
        }
    }

    private fun programWindow(centerSourceKey: String?): List<LiveChannel> {
        val list = panelChannels()
        if (list.isEmpty()) return emptyList()
        val center = list.indexOfFirst { it.sourceKey == centerSourceKey }.takeIf { it >= 0 } ?: 0
        val start = (center - EPG_LIST_WINDOW_RADIUS).coerceAtLeast(0)
        val endExclusive = (center + EPG_LIST_WINDOW_RADIUS + 1).coerceAtMost(list.size)
        return list.subList(start, endExclusive)
    }

    private fun updateCachedProgram(sourceKey: String, program: ProgramSummary?) {
        if (program == null || program.title.isBlank()) {
            val now = System.currentTimeMillis()
            if (currentPrograms[sourceKey]?.let { now in it.startTimeMillis until it.endTimeMillis } == true) {
                return
            }
            currentPrograms.remove(sourceKey)
        } else {
            currentPrograms[sourceKey] = program
        }
    }

    private fun updateCachedPrograms(
        refreshedChannels: Collection<LiveChannel>,
        fresh: Map<String, ProgramSummary>,
    ) {
        refreshedChannels.forEach { channel ->
            updateCachedProgram(channel.sourceKey, fresh[channel.sourceKey])
        }
    }

    private fun startEpgRefresh() {
        epgRefreshJob?.cancel()
        epgRefreshJob = lifecycleScope.launch {
            while (true) {
                delay(EPG_REFRESH_INTERVAL_MS)
                refreshCurrentPrograms()
            }
        }
    }

    private fun checkAndScheduleEpgAutoRefresh() {
        if (epgAutoRefreshJob?.isActive == true) return
        epgAutoRefreshJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(25_000L)
            val xmlTvRepo = XmlTvRepository(this@MainActivity)
            if (xmlTvRepo.shouldAutoRefresh()) {
                debugLog.recordDebug("EPG_AUTO_REFRESH_START | source=xmltv")
                val activeKeys = xmlTvRepo.activeChannelKeys()
                runCatching {
                    val count = xmlTvRepo.refreshSavedUrls(activeKeys)
                    debugLog.recordDebug("EPG_AUTO_REFRESH_SUCCESS | count=$count")
                    withContext(Dispatchers.Main) {
                        currentChannel?.let(::loadPrograms)
                    }
                }.onFailure { error ->
                    debugLog.recordDebug(
                        "EPG_AUTO_REFRESH_FAILURE | ${error.javaClass.simpleName}: ${error.message}",
                    )
                }
            }
        }
    }

    private suspend fun refreshCurrentPrograms() {
        val selected = currentChannel ?: return
        if (binding.channelPanel.visibility == View.VISIBLE) {
            val centerSourceKey = focusedListSourceKey ?: selected.sourceKey
            val window = programWindow(centerSourceKey).sortedByDescending {
                it.sourceKey == centerSourceKey
            }
            val result = withContext(Dispatchers.IO) {
                runCatching { programRepository.currentProgramsForListWindow(window) }
            }
            if (
                binding.channelPanel.visibility != View.VISIBLE ||
                (focusedListSourceKey != null && focusedListSourceKey != centerSourceKey)
            ) return
            result.getOrNull()?.let { fresh ->
                updateCachedPrograms(window, fresh)
                window.forEach { channel ->
                    adapter.submitProgram(channel.sourceKey, fresh[channel.sourceKey])
                }
            }
        }
        loadPrograms(selected, clearExisting = false)
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
            val current = programs.current?.takeIf { it.title.isNotBlank() }
            updateCachedProgram(channel.sourceKey, current)
            adapter.submitProgram(channel.sourceKey, current)
            debugLog.recordDebug(
                "EPG_QUERY_RESULT | channel=${channel.id}, " +
                    "current=${programs.current?.title}, next=${programs.next?.title}",
            )
            if (current == null) {
                binding.currentProgram.setText(R.string.no_program_information)
                binding.currentProgram.visibility = if (displayPreferences.showCurrentProgram) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                binding.programMeta.visibility = View.GONE
                binding.infoProgress.visibility = View.GONE
            }
            current?.let(::updateCurrentProgramUi)
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
            if (programs.next == null || programs.next.title.isBlank()) {
                binding.nextProgram.visibility = View.GONE
            }
        }
    }

    private fun updateCurrentProgramUi(current: ProgramSummary) {
        binding.currentProgram.text = current.title
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
        binding.programMeta.visibility =
            if (displayPreferences.showCurrentProgram) View.VISIBLE else View.GONE
        binding.infoProgress.visibility =
            if (displayPreferences.showCurrentProgram) View.VISIBLE else View.GONE
    }

    private fun showInfoBar() {
        if (isVodPlaybackActive()) {
            setInfoBarVisible(false)
            binding.infoBar.visibility = View.GONE
            showVodPlayerControls()
            return
        }
        if (currentChannel?.source == LiveChannel.Source.IPTV) {
            showIptvPlaybackChrome(interactive = false)
        } else {
            hideIptvPlaybackControls(hideInfoBar = false)
        }
        setInfoBarVisible(true)
        infoBarJob?.cancel()
        if (channelPanelExpanded) return
        val seconds = displayPreferences.infoBarDurationSeconds
        if (seconds == 0) return
        infoBarJob = lifecycleScope.launch {
            delay(seconds * 1_000L)
            if (numberInput.isEmpty()) {
                setInfoBarVisible(false)
            }
        }
    }

    private fun startIptvPlaybackMonitor(kind: IptvContentKind) {
        iptvLiveHealthJob?.cancel()
        val sourceKey = currentChannel?.sourceKey ?: return
        iptvLiveHealthJob = lifecycleScope.launch {
            while (currentChannel?.sourceKey == sourceKey) {
                delay(IPTV_PLAYBACK_CHECK_INTERVAL_MS)
                when (kind) {
                    IptvContentKind.LIVE -> {
                        if (
                            !iptvManualTimeshift &&
                            iptvPlayback.catchUpToLive(IPTV_MAX_LIVE_OFFSET_MS)
                        ) {
                            debugLog.recordDebug("IPTV_LIVE_EDGE_CORRECTED | channel=$sourceKey")
                        }
                    }
                    IptvContentKind.VOD -> saveCurrentIptvResumePosition()
                    IptvContentKind.UNKNOWN -> Unit
                }
            }
        }
    }

    private fun saveCurrentIptvResumePosition() {
        val channel = currentChannel ?: return
        if (!channel.isVodContent() || iptvPlayback.tunedSourceKey() != channel.sourceKey) return
        if (channel.source != LiveChannel.Source.IPTV || currentIptvContentKind != IptvContentKind.VOD) {
            return
        }
        val snapshot = iptvPlayback.playbackSnapshot()
        iptvResumeStore.save(channel.sourceKey, snapshot.positionMillis, snapshot.durationMillis)
        lifecycleScope.launch(Dispatchers.IO) {
            if (snapshot.durationMillis > 0L && snapshot.positionMillis >= snapshot.durationMillis - 60_000L) {
                homeRecentChannelsPublisher.removeVod(channel.sourceKey)
            } else {
                homeRecentChannelsPublisher.publishVod(channel, snapshot.positionMillis, snapshot.durationMillis)
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
                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                        iptvManualTimeshift = false
                        iptvPlayback.goLive()
                    }
                    else -> return
                }
                showIptvPlaybackControls(R.string.iptv_returned_live, activateControls = true)
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
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        iptvPlayback.stopVod()
                        val stoppedChannel = currentChannel
                        if (stoppedChannel != null) {
                            iptvResumeStore.clear(stoppedChannel.sourceKey)
                            lifecycleScope.launch(Dispatchers.IO) {
                                homeRecentChannelsPublisher.removeVod(stoppedChannel.sourceKey)
                            }
                        }
                    }
                    else -> return
                }
                showIptvPlaybackControls(
                    if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) R.string.iptv_vod_stopped
                    else if (iptvPlayback.playbackSnapshot().isPlaying) {
                        R.string.iptv_vod_playing
                    } else {
                        R.string.iptv_vod_paused
                    },
                    activateControls = true,
                )
            }
            IptvContentKind.UNKNOWN -> {
                if (keyCode !in setOf(KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
                    return
                }
                iptvPlayback.togglePlayPause()
                showIptvPlaybackControls(activateControls = true)
            }
        }
    }

    private fun showIptvPlaybackChrome(interactive: Boolean) {
        val wasHidden = binding.iptvPlaybackContainer.visibility != View.VISIBLE
        iptvControlsInteractive = interactive
        osdCoordinator.showInfoBar(iptvChrome = true, interactive = interactive)
        binding.programMeta.visibility = View.GONE
        binding.nextProgram.visibility = View.GONE
        if (wasHidden) pendingBufferSeconds = iptvPlayback.targetBufferSeconds()
        updateInfoBarHeight()
        updateInfoColorActions()
        updateIptvPlaybackControls()
        updateIptvControlHints()
        iptvInfoTickerJob?.cancel()
        iptvInfoTickerJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (binding.infoBar.visibility == View.VISIBLE && currentChannel?.source == LiveChannel.Source.IPTV) {
                    updateIptvPlaybackControls()
                    delay(500L)
                }
            }
        }
    }

    private fun setupIptvControls() {
        binding.iptvBtnPlayPause.setOnClickListener {
            selectedIptvButton = IptvControlButton.PLAY_PAUSE
            activateSelectedIptvButton()
            showIptvPlaybackControls()
        }
        binding.iptvBtnBuffer.setOnClickListener {
            selectedIptvButton = IptvControlButton.BUFFER
            activateSelectedIptvButton()
            showIptvPlaybackControls()
        }
        binding.iptvBtnSpeed.setOnClickListener {
            selectedIptvButton = IptvControlButton.SPEED
            activateSelectedIptvButton()
            showIptvPlaybackControls()
        }

        binding.iptvBtnMore.setOnClickListener {
            selectedIptvButton = IptvControlButton.MORE
            activateSelectedIptvButton()
            showIptvPlaybackControls()
        }
        binding.iptvBtnConnection.setOnClickListener {
            currentChannel?.let { showLiveConnectionMethod(it, applyImmediately = true) }
        }
        val connectionIcon = ContextCompat.getDrawable(this, R.drawable.ic_link)?.apply { setBounds(0, 0, dp(14), dp(14)) }
        binding.iptvBtnConnection.setCompoundDrawablesRelative(connectionIcon, null, null, null)
        binding.iptvSeekbarRow.setOnClickListener {
            iptvControlRow = IptvControlRow.TIMELINE
            if (currentIptvContentKind == IptvContentKind.LIVE) {
                iptvManualTimeshift = false
                iptvPlayback.goLive()
            } else {
                iptvPlayback.togglePlayPause()
            }
            updateIptvPlaybackControls()
            showIptvPlaybackControls()
        }
    }

    private fun showIptvPlaybackControls(
        stateText: Int? = null,
        autoHide: Boolean = true,
        activateControls: Boolean = iptvControlsInteractive,
    ) {
        if (isVodPlaybackActive()) {
            setInfoBarVisible(false)
            binding.infoBar.visibility = View.GONE
            showVodPlayerControls(autoHide = autoHide)
            return
        }
        if (binding.channelPanel.visibility == View.VISIBLE || osdCoordinator.state.primaryOsd == PrimaryOsd.CHANNEL_PANEL) {
            if (stateText != null && stateText != 0) {
                showIptvNotice(stateText)
            }
            updateIptvPlaybackControls()
            return
        }
        val wasHidden = binding.iptvPlaybackContainer.visibility != View.VISIBLE
        focusedTuneJob?.cancel()
        channelPanelJob?.cancel()
        channelPanelExpanded = false
        osdCoordinator.showIptvControls(activateControls)
        binding.advancedFilterRow.visibility = View.GONE

        showIptvPlaybackChrome(interactive = activateControls)

        setInfoBarVisible(true)

        if (wasHidden) {
            pendingBufferSeconds = iptvPlayback.targetBufferSeconds()
            iptvControlRow = IptvControlRow.TIMELINE
            selectedIptvButton = IptvControlButton.PLAY_PAUSE
        }
        if (stateText != null && stateText != 0) {
            showIptvNotice(stateText)
        }

        binding.iptvBtnSpeed.visibility = if (currentIptvContentKind == IptvContentKind.VOD) View.VISIBLE else View.GONE

        updateIptvPlaybackControls()

        iptvControlsJob?.cancel()
        infoBarJob?.cancel()
        if (!autoHide) return

        iptvControlsJob = lifecycleScope.launch {
            delay(IPTV_CONTROL_TIMEOUT_MS)
            hideIptvPlaybackControls()
        }
    }

    private fun hideIptvPlaybackControls(hideInfoBar: Boolean = true) {
        iptvControlsJob?.cancel()
        iptvInfoTickerJob?.cancel()
        iptvControlsInteractive = false
        osdCoordinator.hideIptvControls(hideInfoBar)
        updateInfoBarHeight()
        updateInfoColorActions()
        iptvControlRow = IptvControlRow.TIMELINE
        val defaultBg = ContextCompat.getDrawable(this, R.drawable.bg_focusable)
        binding.iptvSeekbarRow.background = defaultBg
        val controlBg = ContextCompat.getDrawable(this, R.drawable.bg_iptv_control)
        binding.iptvBtnPlayPause.background = controlBg
        binding.iptvBtnBuffer.background = controlBg
        binding.iptvBtnSpeed.background = controlBg
        binding.iptvBtnMore.background = controlBg
        binding.iptvBtnConnection.background = controlBg

        if (displayPreferences.showCurrentProgram) binding.programMeta.visibility = View.VISIBLE
        if (displayPreferences.showNextProgram) binding.nextProgram.visibility = View.VISIBLE
        if (hideInfoBar) {
            setInfoBarVisible(false)
        } else {
            updateIptvActionBarState()
        }
    }

    private fun showIptvNotice(message: Int) {
        showIptvNoticeText(getString(message))
    }

    private fun showIptvNoticeText(text: String) {
        binding.iptvNotice.text = text
        (binding.iptvNotice.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.END or if (displayPreferences.infoBarPosition == InfoBarPosition.TOP) {
                Gravity.BOTTOM
            } else {
                Gravity.TOP
            }
            binding.iptvNotice.layoutParams = this
        }
        binding.iptvNotice.visibility = View.VISIBLE
        iptvNoticeJob?.cancel()
        iptvNoticeJob = lifecycleScope.launch {
            delay(2_500L)
            binding.iptvNotice.visibility = View.GONE
        }
    }


    private fun setupVodPlayerControls() {
        val controller = VodPlayerOverlayController(
            binding = binding,
            iptvPlayback = iptvPlayback,
            scope = lifecycleScope,
            onOpenVodLibrary = { openVodLibrary() },
            onShowSpeedChoices = { showIptvSpeedChoices() },
            onShowAudioTracks = { showAudioTracks() },
            onHideInfoBar = {
                setInfoBarVisible(false)
                infoBarJob?.cancel()
                binding.infoBar.visibility = View.GONE
                if (binding.channelPanel.visibility == View.VISIBLE) {
                    binding.channelPanel.visibility = View.GONE
                }
                binding.iptvPlaybackContainer.visibility = View.GONE
            },
            getCurrentChannel = { currentChannel },
            getSourceName = { ch -> iptvSourceNames[ch.inputId] ?: getString(R.string.iptv_library_vod) },
            formatPlaybackTime = { ms -> formatPlaybackTime(ms) },
            getString = { resId -> getString(resId) },
            onOpenEpisodeList = { showEpisodePickerForActiveVod() },
            hasEpisodes = { currentVodHasEpisodes || currentChannel?.episodeTitle?.isNotBlank() == true },
        )
        controller.setup()
        vodPlayerOverlayController = controller
    }

    private fun showEpisodePickerForActiveVod(): Boolean {
        val active = currentChannel ?: return false
        val activeKey = active.sourceKey
        lifecycleScope.launch {
            val appDb = com.tvapp.livetv.data.local.TVAppDatabase.getInstance(this@MainActivity)
            val currentMeta = withContext(Dispatchers.IO) {
                runCatching { appDb.vodDao().metadata(activeKey) }.getOrNull()
            } ?: run {
                openVodLibrary()
                return@launch
            }

            val seriesKey = currentMeta.parentKey ?: if (currentMeta.kind == "SERIES") currentMeta.sourceKey else null
            if (seriesKey == null) {
                openVodLibrary()
                return@launch
            }

            val episodes = withContext(Dispatchers.IO) {
                appDb.vodDao().episodesForSeries(seriesKey)
            }

            if (episodes.isNotEmpty()) {
                val episodeNames = episodes.map { ep ->
                    val epNum = ep.episodeNumber
                    val sNum = ep.seasonNumber ?: 1
                    if (epNum != null) {
                        getString(R.string.vod_season_episode_fmt, sNum, epNum) +
                            (if (ep.name.isNotBlank() && !ep.name.startsWith("$epNum")) " - ${ep.name}" else "")
                    } else ep.name
                }.toTypedArray()

                val currentIndex = episodes.indexOfFirst { it.sourceKey == activeKey }.coerceAtLeast(0)

                AlertDialog.Builder(this@MainActivity, R.style.Theme_TVApp_Dialog)
                    .setTitle(currentChannel?.displayName ?: getString(R.string.vod_select_episode))
                    .setSingleChoiceItems(episodeNames, currentIndex) { dialog, which ->
                        val chosen = episodes.getOrNull(which)
                        if (chosen != null && chosen.sourceKey != activeKey) {
                            openVod(chosen.sourceKey)
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.close, null)
                    .show()
                return@launch
            }

            // Check if series has episodes JSON in streamUrl
            val seriesMeta = withContext(Dispatchers.IO) {
                if (currentMeta.kind == "SERIES") currentMeta else appDb.vodDao().metadata(seriesKey)
            }
            val rawEpisodes = seriesMeta?.streamUrl
            if (!rawEpisodes.isNullOrBlank() && rawEpisodes.startsWith("[")) {
                try {
                    val jsonArray = org.json.JSONArray(rawEpisodes)
                    val parsedEpisodes = mutableListOf<Pair<String, String>>()
                    for (i in 0 until jsonArray.length()) {
                        val epObj = jsonArray.optJSONObject(i)
                        if (epObj != null) {
                            val epTitle = epObj.optString("title").trim().ifEmpty {
                                epObj.optString("name").trim()
                            }.ifEmpty { "${i + 1}. Bölüm" }
                            val epUrl = epObj.optString("videoUrl").trim().ifEmpty {
                                epObj.optString("url").trim()
                            }
                            if (epUrl.isNotEmpty()) {
                                parsedEpisodes.add(Pair(epTitle, epUrl))
                            }
                        }
                    }

                    if (parsedEpisodes.isNotEmpty()) {
                        val titles = parsedEpisodes.map { it.first }.toTypedArray()
                        val currentIndex = parsedEpisodes.indexOfFirst { it.first == active.episodeTitle }.coerceAtLeast(0)
                        AlertDialog.Builder(this@MainActivity, R.style.Theme_TVApp_Dialog)
                            .setTitle(currentChannel?.displayName ?: seriesMeta.name)
                            .setSingleChoiceItems(titles, currentIndex) { dialog, which ->
                                val (epTitle, epUrl) = parsedEpisodes[which]
                                val epKey = "${seriesKey}:ep:${which}_${epUrl.hashCode()}"
                                val vodIntent = Intent().apply {
                                    putExtra(EXTRA_VOD_SOURCE_KEY, epKey)
                                    putExtra(EXTRA_VOD_STREAM_URL, epUrl)
                                    putExtra(EXTRA_VOD_TITLE, seriesMeta.name)
                                    putExtra(EXTRA_VOD_EPISODE_TITLE, epTitle)
                                    putExtra(EXTRA_VOD_LOGO_URL, seriesMeta.logoUrl)
                                }
                                handleVodSourceDeepLink(vodIntent)
                                dialog.dismiss()
                            }
                            .setNegativeButton(R.string.close, null)
                            .show()
                        return@launch
                    }
                } catch (e: Exception) {
                    debugLog.recordDebug("Parse episodes JSON failed: ${e.message}")
                }
            }

            openVodLibrary()
        }
        return true
    }

    private fun showVodPlayerControls(autoHide: Boolean = true) {
        vodPlayerOverlayController?.showControls(autoHide)
    }

    private fun openVod(sourceKey: String, startOver: Boolean = false) {
        val vodIntent = Intent().apply {
            putExtra(EXTRA_VOD_SOURCE_KEY, sourceKey)
            if (startOver) putExtra(EXTRA_VOD_START_OVER, true)
        }
        handleVodSourceDeepLink(vodIntent)
    }

    private fun hideVodPlayerControls() {
        vodPlayerOverlayController?.hideControls()
    }

    private fun updateVodPlayerControls() {
        vodPlayerOverlayController?.updateControls()
    }

    private fun updateIptvPlaybackControls() {
        val state = iptvPlayback.playbackSnapshot()
        val isVod = showsVodTimeline()
        binding.iptvSeekbarRow.visibility = if (isVod) View.VISIBLE else View.GONE
        binding.iptvBtnSpeed.visibility = if (isVod) View.VISIBLE else View.GONE
        binding.iptvBtnConnection.visibility = if (!isVod) View.VISIBLE else View.GONE

        if (!isVod && iptvControlRow == IptvControlRow.TIMELINE) iptvControlRow = IptvControlRow.BUTTONS
        if ((selectedIptvButton == IptvControlButton.SPEED && !isVod) ||
            (selectedIptvButton == IptvControlButton.CONNECTION && isVod)) {
            selectedIptvButton = IptvControlButton.PLAY_PAUSE
        }
        val duration = state.durationMillis.coerceAtLeast(1L)
        binding.iptvControlProgress.isIndeterminate = state.durationMillis <= 0L
        binding.iptvControlProgress.progress =
            ((state.positionMillis * 1_000L / duration).coerceIn(0L, 1_000L)).toInt()
        binding.iptvControlProgress.secondaryProgress =
            ((state.bufferedPositionMillis * 1_000L / duration).coerceIn(0L, 1_000L)).toInt()
        binding.iptvTimePosition.text = if (state.playbackSpeed != null) formatPlaybackTime(state.positionMillis)
            else getString(R.string.unknown_value)
        binding.iptvTimeDuration.text = if (currentIptvContentKind == IptvContentKind.LIVE) {
            getString(R.string.iptv_live_edge)
        } else {
            if (state.durationMillis > 0) formatPlaybackTime(state.durationMillis) else getString(R.string.unknown_value)
        }

        val isPlaying = state.isPlaying
        binding.iptvBtnPlayPause.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (!isVod) R.drawable.ic_live else if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            0,
            0,
            0,
        )
        binding.iptvBtnPlayPause.setText(
            if (!isVod) R.string.iptv_live_edge else if (isPlaying) R.string.iptv_pause_label else R.string.iptv_play_label,
        )
        val atLiveEdge = state.isAtLiveEdge()
        binding.iptvBtnPlayPause.setTextColor(ContextCompat.getColor(this,
            if (!isVod && atLiveEdge) R.color.remote_red else if (isVod) R.color.text_primary else R.color.text_secondary))
        androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(binding.iptvBtnPlayPause,
            android.content.res.ColorStateList.valueOf(binding.iptvBtnPlayPause.currentTextColor))
        binding.iptvBtnPlayPause.contentDescription = getString(if (isVod) {
            if (isPlaying) R.string.iptv_pause_label else R.string.iptv_play_label
        } else if (atLiveEdge) R.string.iptv_live_edge else if (state.distanceToLiveDefaultMillis != null) {
            R.string.iptv_return_to_live
        } else R.string.iptv_live_status_unknown)
        setIptvControlLabel(
            binding.iptvBtnBuffer,
            getString(R.string.iptv_buffer_label),
            state.bufferedDurationMillis?.let { getString(R.string.iptv_buffer_actual_seconds, it / 1_000f) }
                ?: getString(R.string.unknown_value),
        )
        setIptvControlLabel(
            binding.iptvBtnSpeed,
            getString(R.string.iptv_speed_label),
            state.playbackSpeed?.let { String.format(Locale.getDefault(), "%.2gx", it) }
                ?: getString(R.string.unknown_value),
        )
        binding.iptvBtnConnection.setText(if (iptvPlayback.removesLiveOverlap())
            R.string.iptv_live_connection_overlap_short else R.string.iptv_live_connection_standard_short)

        updateIptvActionBarState()
        updateInfoBarHeight()
        renderIptvControlSelection()
    }

    private fun showsVodTimeline(): Boolean = vodMode || currentChannel?.isVodContent() == true ||
        (currentChannel?.iptvContentType.equals("LIVE", true).not() && currentIptvContentKind == IptvContentKind.VOD)

    private fun isVodPlaybackActive(): Boolean =
        (vodMode || currentIptvContentKind == IptvContentKind.VOD || showsVodTimeline() || currentChannel?.isVodContent() == true) &&
            currentChannel?.source == LiveChannel.Source.IPTV

    private fun setIptvControlLabel(view: TextView, label: String, value: String) {
        val fullText = "$label  $value"
        view.text = SpannableString(fullText).apply {
            setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this@MainActivity, R.color.accent)),
                label.length + 2,
                fullText.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    private fun cycleIptvButton(direction: Int) {
        val visibleButtons = buildList {
            add(IptvControlButton.PLAY_PAUSE)
            add(IptvControlButton.BUFFER)
            if (showsVodTimeline()) add(IptvControlButton.SPEED)
            add(IptvControlButton.MORE)
            if (!showsVodTimeline()) add(IptvControlButton.CONNECTION)
        }
        val idx = visibleButtons.indexOf(selectedIptvButton).coerceAtLeast(0)
        selectedIptvButton = visibleButtons[(idx + direction + visibleButtons.size) % visibleButtons.size]
        renderIptvControlSelection()
    }

    private fun renderIptvControlSelection() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_iptv_control_selected)
        val defaultBg = ContextCompat.getDrawable(this, R.drawable.bg_iptv_control)

        if (!iptvControlsInteractive) {
            binding.iptvSeekbarRow.clearFocus()
            binding.iptvBtnPlayPause.clearFocus()
            binding.iptvBtnBuffer.clearFocus()
            binding.iptvBtnSpeed.clearFocus()
            binding.iptvBtnMore.clearFocus()
            binding.iptvBtnConnection.clearFocus()
            binding.iptvSeekbarRow.background = defaultBg
            binding.iptvBtnPlayPause.background = defaultBg
            binding.iptvBtnBuffer.background = defaultBg
            binding.iptvBtnSpeed.background = defaultBg
            binding.iptvBtnMore.background = defaultBg
            binding.iptvBtnConnection.background = defaultBg
            return
        }

        binding.iptvSeekbarRow.background = if (iptvControlRow == IptvControlRow.TIMELINE) {
            selectedBg
        } else {
            defaultBg
        }

        val isButtonsRow = iptvControlRow == IptvControlRow.BUTTONS
        fun updateButton(btn: View, isThisSelected: Boolean) {
            btn.background = if (isButtonsRow && isThisSelected) {
                selectedBg
            } else {
                defaultBg
            }
        }

        updateButton(binding.iptvBtnPlayPause, selectedIptvButton == IptvControlButton.PLAY_PAUSE)
        updateButton(binding.iptvBtnBuffer, selectedIptvButton == IptvControlButton.BUFFER)
        updateButton(binding.iptvBtnSpeed, selectedIptvButton == IptvControlButton.SPEED)
        updateButton(binding.iptvBtnMore, selectedIptvButton == IptvControlButton.MORE)
        updateButton(binding.iptvBtnConnection, selectedIptvButton == IptvControlButton.CONNECTION)
    }

    private fun updateIptvActionBarState() {
        val isIptv = currentChannel?.source == LiveChannel.Source.IPTV
        if (!isIptv || binding.iptvPlaybackContainer.visibility != View.VISIBLE) {
            binding.iptvActionBar.visibility = View.GONE
            return
        }
        binding.iptvActionBar.visibility = View.VISIBLE
        val isChannelPanelOpen = binding.channelPanel.visibility == View.VISIBLE
        binding.iptvActionBar.alpha = when {
            isChannelPanelOpen -> 0.35f
            !iptvControlsInteractive -> 0.50f
            else -> 1.0f
        }
        binding.iptvSeekbarRow.alpha = if (iptvControlsInteractive) 1.0f else 0.65f
        val focusable = iptvControlsInteractive && !isChannelPanelOpen
        binding.iptvBtnPlayPause.isFocusable = focusable
        binding.iptvBtnBuffer.isFocusable = focusable
        binding.iptvBtnSpeed.isFocusable = focusable
        binding.iptvBtnMore.isFocusable = focusable
        binding.iptvBtnConnection.isFocusable = focusable
    }

    private fun activateSelectedIptvButton() {
        when (selectedIptvButton) {
            IptvControlButton.PLAY_PAUSE -> {
                if (!showsVodTimeline()) {
                    iptvManualTimeshift = false
                    if (!iptvPlayback.goLive()) currentChannel?.let { iptvPlayback.play(it) }
                } else {
                    iptvPlayback.togglePlayPause()
                }
            }
            IptvControlButton.BUFFER -> {
                showIptvBufferChoices()
            }
            IptvControlButton.SPEED -> {
                showIptvSpeedChoices()
            }
            IptvControlButton.MORE -> showIptvMoreChoices()
            IptvControlButton.CONNECTION -> currentChannel?.let { showLiveConnectionMethod(it, applyImmediately = true) }
        }
        updateIptvPlaybackControls()
    }

    private fun showIptvBufferChoices() {
        val options = IptvPlaybackPreferences.BUFFER_OPTIONS
        val labels = options.map {
            if (it == 0) getString(R.string.automatic_abr) else getString(R.string.seconds_value, it)
        }.toTypedArray()
        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.iptv_default_buffer)
            .setSingleChoiceItems(labels, options.indexOf(pendingBufferSeconds)) { dialog, which ->
                pendingBufferSeconds = options[which]
                iptvPlayback.setTargetBufferSeconds(pendingBufferSeconds)
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showIptvSpeedChoices() {
        val options = IptvPlaybackPreferences.SPEED_OPTIONS
        val labels = options.map { String.format(Locale.getDefault(), "%.2gx", it) }.toTypedArray()
        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.iptv_default_vod_speed)
            .setSingleChoiceItems(labels, options.indexOf(iptvPlayback.vodPlaybackSpeed())) { dialog, which ->
                iptvPlayback.setVodPlaybackSpeed(options[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showIptvMoreChoices() {
        val channelKey = currentChannel?.sourceKey ?: return
        lifecycleScope.launch {
            val nextEpisode = withContext(Dispatchers.IO) {
                com.tvapp.livetv.data.VodRepository(this@MainActivity).nextEpisode(channelKey)
            }
            if (currentChannel?.sourceKey != channelKey || isFinishing) return@launch
            val labels = mutableListOf(
                getString(R.string.iptv_action_video),
                getString(R.string.iptv_action_audio),
                getString(R.string.iptv_action_subtitle),
                getString(R.string.iptv_subtitle_appearance),
            )
            if (nextEpisode != null) labels += getString(R.string.vod_next_episode, nextEpisode.name)
            AlertDialog.Builder(this@MainActivity, R.style.Theme_TVApp_Dialog)
                .setTitle(R.string.iptv_more_controls)
                .setItems(labels.toTypedArray()) { _, which ->
                    when (which) {
                        0 -> showIptvVideoOptions()
                        1 -> showAudioTracks()
                        2 -> showSubtitleTracks()
                        3 -> com.tvapp.livetv.ui.IptvSubtitleAppearanceDialog.show(this@MainActivity) {
                            iptvPlayback.applySubtitleAppearance()
                        }
                        4 -> nextEpisode?.let {
                            handleVodSourceDeepLink(Intent().putExtra(EXTRA_VOD_SOURCE_KEY, it.sourceKey))
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun bufferTargetLabel(seconds: Int): String = if (seconds == 0) {
        getString(R.string.iptv_buffer_target_auto)
    } else {
        getString(R.string.iptv_buffer_target, seconds)
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
        val osdTheme = if (BuildConfig.MOBILE_UI_ENABLED) com.tvapp.livetv.settings.OsdTheme.DEFAULT
            else displayPreferences.osdTheme
        adapter.applyRowOptions(
            ChannelRowOptions(
                osdTheme = osdTheme,
                showLogo = displayPreferences.showChannelLogo,
                showProgram = displayPreferences.showChannelProgram,
                showProgress = displayPreferences.showChannelProgress,
                showSourceBadge = displayPreferences.showChannelSourceBadge,
            ),
        )
        binding.infoBar.background.mutate().alpha =
            (displayPreferences.infoBarOpacityPercent * 255 / 100).coerceIn(0, 255)
        com.tvapp.livetv.ui.OsdThemeAppearance.apply(osdTheme, binding.channelPanel, binding.infoCard,
            displayPreferences.channelPanelOpacityPercent, displayPreferences.infoBarOpacityPercent)

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
        startDiagnosticsObservation()
    }

    private fun applyExperienceMode() {
        experienceMode = resolveExperienceMode(
            deviceCapabilities,
            mobileUiEnabled = BuildConfig.MOBILE_UI_ENABLED,
            override = ExperienceModePreferencesStore(this).load(),
        )
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
        val resolved = resolveCurrentTrackPreferences()
        val preferredAudio = resolved.audioLanguage
        if (preferredAudio != null) {
            audio.firstOrNull { it.language.equals(preferredAudio, ignoreCase = true) }
                ?.let { playback.selectAudio(it.id) }
        }

        if (!resolved.subtitlesEnabled) {
            playback.selectSubtitle(null)
            return
        }
        val preferredSubtitle = resolved.subtitleLanguage
        val track = subtitles.firstOrNull {
            it.language.equals(preferredSubtitle, ignoreCase = true)
        } ?: subtitles.firstOrNull()
        playback.selectSubtitle(track?.id)
    }

    private fun resolveCurrentTrackPreferences() = resolveTrackPreferences(
        channel = currentChannel?.sourceKey?.let(channelTrackPreferenceStore::load),
        globalAudioLanguage = displayPreferences.preferredAudioLanguage,
        globalSubtitlesEnabled = displayPreferences.subtitlesEnabled,
        globalSubtitleLanguage = displayPreferences.preferredSubtitleLanguage,
    )

    private fun rememberChannelAudioLanguage(language: String?) {
        val key = currentChannel?.sourceKey ?: return
        language ?: return
        channelTrackPreferenceStore.save(key) { it.copy(audioLanguage = language) }
    }

    private fun rememberChannelSubtitle(language: String?) {
        val key = currentChannel?.sourceKey ?: return
        channelTrackPreferenceStore.save(key) {
            it.copy(subtitlesEnabled = true, subtitleLanguage = language)
        }
    }

    private fun rememberChannelSubtitlesOff() {
        val key = currentChannel?.sourceKey ?: return
        channelTrackPreferenceStore.save(key) {
            it.copy(subtitlesEnabled = false, subtitleLanguage = null)
        }
    }

    private fun updateTechnicalBadges(channel: LiveChannel, tracks: List<TvTrackInfo>) {
        val videoTrack = tracks.filter { it.type == TvTrackInfo.TYPE_VIDEO }
            .maxByOrNull { it.videoWidth.toLong() * it.videoHeight }
        val callbackSize = if (
            channel.source == LiveChannel.Source.TIF && channel.sourceKey == currentChannel?.sourceKey
        ) playback.currentVideoState() else null
        val width = callbackSize?.width?.takeIf { it > 0 } ?: videoTrack?.videoWidth ?: 0
        val height = callbackSize?.height?.takeIf { it > 0 } ?: videoTrack?.videoHeight ?: 0
        val format = channel.videoFormat.orEmpty().uppercase(Locale.ROOT)
        val radio = channel.isRadioChannel()
        val quality = if (radio) null else VideoQuality.resolutionLabel(width, height, format)
        val targetRadioVis = if (radio) View.VISIBLE else View.GONE
        if (binding.radioBadge.visibility != targetRadioVis) binding.radioBadge.visibility = targetRadioVis
        if (radio) {
            if (binding.qualityBadge.visibility != View.GONE) binding.qualityBadge.visibility = View.GONE
        } else if (quality != null) {
            if (binding.qualityBadge.text != quality) binding.qualityBadge.text = quality
            if (binding.qualityBadge.visibility != View.VISIBLE) binding.qualityBadge.visibility = View.VISIBLE
        } else {
            if (binding.qualityBadge.visibility != View.GONE) binding.qualityBadge.visibility = View.GONE
        }
        binding.sourceBadgeIcon.setImageResource(
            if (channel.source == LiveChannel.Source.IPTV) {
                R.drawable.ic_source_iptv
            } else {
                R.drawable.ic_source_tif
            },
        )
        val targetLockVis = if (
            channel.encrypted || channel.locked || parentalControlStore.isLocked(channel.sourceKey)
        ) View.VISIBLE else View.GONE
        if (binding.lockBadge.visibility != targetLockVis) binding.lockBadge.visibility = targetLockVis

        val audioTracks = tracks.filter { it.type == TvTrackInfo.TYPE_AUDIO }
        val subtitleTracks = tracks.filter { it.type == TvTrackInfo.TYPE_SUBTITLE }
        val trackMetadata = tracks.joinToString(" ") { track ->
            "${track.description ?: ""} ${track.extra}"
        }.lowercase(Locale.ROOT)
        val dolby = listOf("dolby", "ac3", "ac-3", "eac3", "e-ac-3")
            .any(trackMetadata::contains)
        val targetDolbyVis = if (dolby) View.VISIBLE else View.GONE
        if (binding.dolbyBadge.visibility != targetDolbyVis) binding.dolbyBadge.visibility = targetDolbyVis

        val audioText = audioTracks.firstOrNull()?.language?.let(::displayLanguage)
            ?: audioTracks.size.takeIf { it > 0 }?.toString()
        if (binding.audioBadge.text != audioText) binding.audioBadge.text = audioText
        val targetAudioVis = if (audioTracks.isEmpty() || dolby) View.GONE else View.VISIBLE
        if (binding.audioBadge.visibility != targetAudioVis) binding.audioBadge.visibility = targetAudioVis

        val subText = subtitleTracks.firstOrNull()?.language?.let(::displayLanguage)
            ?: subtitleTracks.size.takeIf { it > 0 }?.toString()
        if (binding.subtitleBadge.text != subText) binding.subtitleBadge.text = subText
        val targetSubVis = if (subtitleTracks.isEmpty()) View.GONE else View.VISIBLE
        if (binding.subtitleBadge.visibility != targetSubVis) binding.subtitleBadge.visibility = targetSubVis

        val hasTeletext = listOf("teletext", "teletekst", "txt")
            .any(trackMetadata::contains)
        val targetTxtVis = if (hasTeletext) View.VISIBLE else View.GONE
        if (binding.txtBadge.visibility != targetTxtVis) binding.txtBadge.visibility = targetTxtVis

        val activeSlots = booleanArrayOf(
            true,
            false,
            radio || quality != null,
            audioTracks.isNotEmpty(),
            subtitleTracks.isNotEmpty(),
            hasTeletext,
            channel.encrypted || channel.locked ||
                parentalControlStore.isLocked(channel.sourceKey),
        )
        val slots = listOf(
            binding.techSlotSource,
            binding.techSlotBitrate,
            binding.techSlotQualityBadge,
            binding.techSlotAudio,
            binding.techSlotSubtitle,
            binding.techSlotTxt,
            binding.techSlotLock,
        )
        slots.forEachIndexed { index, slot ->
            val targetVis = if (activeSlots[index]) View.VISIBLE else View.GONE
            if (slot.visibility != targetVis) slot.visibility = targetVis
        }
    }

    private fun logTifTracks(tracks: List<TvTrackInfo>) {
        val channelKey = currentChannel?.sourceKey
        val signature = channelKey + "|" + tracks.joinToString(";") { track ->
            listOf(
                track.type,
                track.id,
                track.language?.trimEnd('\u0000'),
                track.debugValue { if (type == TvTrackInfo.TYPE_VIDEO) videoWidth else 0 },
                track.debugValue { if (type == TvTrackInfo.TYPE_VIDEO) videoHeight else 0 },
                track.extra?.toString(),
            ).joinToString(":")
        }
        if (signature == lastTifTrackLogSignature) return
        lastTifTrackLogSignature = signature
        if (tracks.isEmpty()) {
            debugLog.recordDebug("TIF_TRACK_RAW | channel=$channelKey, tracks=empty")
            return
        }
        tracks.forEachIndexed { index, track ->
            val type = when (track.type) {
                TvTrackInfo.TYPE_VIDEO -> "VIDEO"
                TvTrackInfo.TYPE_AUDIO -> "AUDIO"
                TvTrackInfo.TYPE_SUBTITLE -> "SUBTITLE"
                else -> "UNKNOWN(${track.type})"
            }
            val typeFields = when (track.type) {
                TvTrackInfo.TYPE_VIDEO -> listOf(
                    "videoWidth=${track.debugValue { videoWidth }}",
                    "videoHeight=${track.debugValue { videoHeight }}",
                    "videoFrameRate=${track.debugValue { videoFrameRate }}",
                    "videoPixelAspectRatio=${track.debugValue { videoPixelAspectRatio }}",
                    "videoActiveFormatDescription=${track.debugValue { videoActiveFormatDescription }}",
                )
                TvTrackInfo.TYPE_AUDIO -> listOf(
                    "audioChannelCount=${track.debugValue { audioChannelCount }}",
                    "audioSampleRate=${track.debugValue { audioSampleRate }}",
                )
                else -> emptyList()
            }
            val extraFields = track.debugExtraValues()
                .joinToString(", ") { (key, value) -> "$key=$value" }
                .ifBlank { track.extra?.toString() ?: "null" }
            debugLog.recordDebug(
                "TIF_TRACK_RAW | channel=$channelKey, index=$index, type=$type, id=${track.id}, " +
                    "language=${track.language?.trimEnd('\u0000')}, description=${track.description}, " +
                    "${typeFields.joinToString(", ")}, extra={$extraFields}",
            )
        }
    }

    private inline fun TvTrackInfo.debugValue(reader: TvTrackInfo.() -> Any): String =
        runCatching { reader().toString() }.getOrElse { error ->
            "unavailable(${error.javaClass.simpleName})"
        }

    private fun TvTrackInfo.debugExtraValues(): List<Pair<String, String>> = runCatching {
        val values = extra ?: return@runCatching emptyList()
        values.keySet().sorted().map { key -> key to values.get(key).debugMetadataValue() }
    }.getOrDefault(emptyList())

    private fun Any?.debugMetadataValue(): String = when (this) {
        null -> "null"
        is ByteArray -> "bytes(${size}) " + take(64).joinToString("") { "%02x".format(it) }
        is IntArray -> joinToString(prefix = "[", postfix = "]")
        is LongArray -> joinToString(prefix = "[", postfix = "]")
        is FloatArray -> joinToString(prefix = "[", postfix = "]")
        is DoubleArray -> joinToString(prefix = "[", postfix = "]")
        is BooleanArray -> joinToString(prefix = "[", postfix = "]")
        is Array<*> -> joinToString(prefix = "[", postfix = "]") { it.toString() }
        else -> toString().replace('\n', ' ').replace('\r', ' ').take(256)
    }

    private fun updateTechnicalBadgesForIptv(
        channel: LiveChannel,
        controller: IptvPlaybackController = iptvPlayback,
    ) {
        if (channel.source != LiveChannel.Source.IPTV) return
        val info = controller.currentTechnicalInfo()
        val width = info.width ?: 0
        val height = info.height ?: 0
        val format = channel.videoFormat.orEmpty().uppercase(Locale.ROOT)
        val radio = channel.isRadioChannel()
        val quality = if (radio) null else VideoQuality.resolutionLabel(width, height, format)
        val targetRadioVis = if (radio) View.VISIBLE else View.GONE
        if (binding.radioBadge.visibility != targetRadioVis) binding.radioBadge.visibility = targetRadioVis
        if (radio) {
            if (binding.qualityBadge.visibility != View.GONE) binding.qualityBadge.visibility = View.GONE
        } else if (quality != null || info.isAdaptive) {
            val qualityText = if (info.isAdaptive) {
                if (quality != null) "$quality · ABR" else "ABR"
            } else {
                quality
            }
            if (binding.qualityBadge.text != qualityText) binding.qualityBadge.text = qualityText
            if (binding.qualityBadge.visibility != View.VISIBLE) binding.qualityBadge.visibility = View.VISIBLE
        } else {
            if (binding.qualityBadge.visibility != View.GONE) binding.qualityBadge.visibility = View.GONE
        }
        binding.sourceBadgeIcon.setImageResource(R.drawable.ic_source_iptv)
        val bitrateText = formatBitrate(info.bitrate)
        val hasBitrate = !radio && bitrateText != "-"
        if (hasBitrate) {
            if (binding.bitrateBadge.text != bitrateText) binding.bitrateBadge.text = bitrateText
            if (binding.bitrateBadge.visibility != View.VISIBLE) binding.bitrateBadge.visibility = View.VISIBLE
        } else {
            if (binding.bitrateBadge.visibility != View.GONE) binding.bitrateBadge.visibility = View.GONE
        }
        val targetLockVis = if (
            channel.encrypted || channel.locked || parentalControlStore.isLocked(channel.sourceKey)
        ) View.VISIBLE else View.GONE
        if (binding.lockBadge.visibility != targetLockVis) binding.lockBadge.visibility = targetLockVis

        val targetDolbyVis = if (info.hasDolby) View.VISIBLE else View.GONE
        if (binding.dolbyBadge.visibility != targetDolbyVis) binding.dolbyBadge.visibility = targetDolbyVis

        val audioLang = info.audioLanguage?.let(::displayLanguage)
        val audioText = audioLang ?: if (info.hasAudio) "1" else null
        if (binding.audioBadge.text != audioText) binding.audioBadge.text = audioText
        val targetAudioVis = if (!info.hasAudio || info.hasDolby) View.GONE else View.VISIBLE
        if (binding.audioBadge.visibility != targetAudioVis) binding.audioBadge.visibility = targetAudioVis

        val subLang = info.subtitleLanguage?.let(::displayLanguage)
        val subText = subLang ?: if (info.hasSubtitles) "1" else null
        if (binding.subtitleBadge.text != subText) binding.subtitleBadge.text = subText
        val targetSubVis = if (info.hasSubtitles) View.VISIBLE else View.GONE
        if (binding.subtitleBadge.visibility != targetSubVis) binding.subtitleBadge.visibility = targetSubVis
        if (binding.txtBadge.visibility != View.GONE) binding.txtBadge.visibility = View.GONE

        val activeSlots = booleanArrayOf(
            true,
            hasBitrate,
            radio || quality != null || info.isAdaptive,
            info.hasAudio,
            info.hasSubtitles,
            false,
            channel.encrypted || channel.locked || parentalControlStore.isLocked(channel.sourceKey),
        )
        val slots = listOf(
            binding.techSlotSource,
            binding.techSlotBitrate,
            binding.techSlotQualityBadge,
            binding.techSlotAudio,
            binding.techSlotSubtitle,
            binding.techSlotTxt,
            binding.techSlotLock,
        )
        slots.forEachIndexed { index, slot ->
            val targetVis = if (activeSlots[index]) View.VISIBLE else View.GONE
            if (slot.visibility != targetVis) slot.visibility = targetVis
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

    private fun setupTrackSelectionHelper() {
        trackSelectionHelper = TrackSelectionHelper(
            activity = this,
            playback = playback,
            iptvPlayback = iptvPlayback,
            getCurrentChannel = { currentChannel },
            showEditorError = { resId -> showEditorError(resId) },
            rememberChannelAudioLanguage = { lang -> rememberChannelAudioLanguage(lang) },
            rememberChannelSubtitle = { lang -> rememberChannelSubtitle(lang) },
            rememberChannelSubtitlesOff = { rememberChannelSubtitlesOff() },
        )
    }

    private fun displayLanguage(code: String): String = trackSelectionHelper.displayLanguage(code)

    private fun showAudioTracks() {
        trackSelectionHelper.showAudioTracks()
    }

    /** TXT/Teletext remote key. Temporary behavior until a real teletext
     *  page view exists: opens the VOD home screen. Swap the body for the
     *  teletext view when it is implemented. */
    private fun showTxtTracks() {
        if (iptvGridActive) return
        openVodLibrary()
    }

    private fun enterVodMode() {
        if (!vodMode) {
            currentChannel?.takeUnless { it.isVodContent() }?.let {
                modePreferences.edit().putString(LAST_LIVE_KEY, it.sourceKey).apply()
            }
        }
        vodMode = true
        channelZapper.clearNumberInput()
        focusedTuneJob?.cancel()
    }

    private fun openVodLibrary() {
        if (!hasIptvAccess(::openVodLibrary)) return
        saveCurrentIptvResumePosition()
        enterVodMode()
        channelResolutionGeneration++
        if (iptvGridActive) stopIptvGrid(resumePrevious = false)
        if (iptvOverlayActive) stopIptvOverlay()
        playback.stop()
        if (currentChannel?.isVodContent() == true && iptvPlayback.healthSnapshot().phase == IptvPlaybackPhase.READY) {
            iptvPlayback.pause()
        } else {
            iptvPlayback.stop()
        }
        hideChannelPanel()
        hideIptvPlaybackControls()
        hideVodPlayerControls()
        startActivity(Intent(this, VodHomeActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP,
        ))
    }

    private fun showPlaybackModeDialog() {
        val lastVod = modePreferences.getString(LAST_VOD_KEY, null)
        val actions = mutableListOf<Pair<String, () -> Unit>>()
        actions += (getString(R.string.switch_to_live_tv) to { switchToLiveMode() })
        actions += (getString(R.string.vod_home_title) to { openVodLibrary() })
        if (lastVod != null) {
            actions += (getString(R.string.continue_watching) to {
                handleVodSourceDeepLink(Intent().putExtra(EXTRA_VOD_SOURCE_KEY, lastVod))
            })
        }
        actions += (getString(R.string.open_browser) to { openTvBrowser() })

        AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.playback_mode_title)
            .setItems(actions.map { it.first }.toTypedArray()) { _, which ->
                actions.getOrNull(which)?.second?.invoke()
            }.setNegativeButton(R.string.close, null).show()
    }

    private fun openTvBrowser() {
        startActivity(Intent(this, com.tvapp.livetv.browser.TvBrowserActivity::class.java))
    }

    private var quickHubDialog: AlertDialog? = null

    private fun showQuickHubDialog() {
        if (isFinishing || isDestroyed) return
        quickHubDialog?.dismiss()

        val dialogView = layoutInflater.inflate(R.layout.dialog_quick_hub, null)
        val hintText = dialogView.findViewById<TextView>(R.id.hub_hint_text)

        val itemLiveTv = dialogView.findViewById<View>(R.id.hub_item_live_tv)
        val itemVod = dialogView.findViewById<View>(R.id.hub_item_vod)
        val itemIptv = dialogView.findViewById<View>(R.id.hub_item_iptv)
        val itemXmltv = dialogView.findViewById<View>(R.id.hub_item_xmltv)
        val itemBrowser = dialogView.findViewById<View>(R.id.hub_item_browser)
        val itemGuide = dialogView.findViewById<View>(R.id.hub_item_guide)
        val itemMultiview = dialogView.findViewById<View>(R.id.hub_item_multiview)
        val itemSettings = dialogView.findViewById<View>(R.id.hub_item_settings)

        fun bindFocusHint(view: View, hintResId: Int) {
            view.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    hintText.setText(hintResId)
                }
            }
        }

        bindFocusHint(itemLiveTv, R.string.quick_hub_live_tv_hint)
        bindFocusHint(itemVod, R.string.quick_hub_vod_hint)
        bindFocusHint(itemIptv, R.string.quick_hub_iptv_hint)
        bindFocusHint(itemXmltv, R.string.quick_hub_xmltv_hint)
        bindFocusHint(itemBrowser, R.string.quick_hub_browser_hint)
        bindFocusHint(itemGuide, R.string.quick_hub_guide_hint)
        bindFocusHint(itemMultiview, R.string.quick_hub_multiview_hint)
        bindFocusHint(itemSettings, R.string.quick_hub_settings_hint)

        val dialog = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setView(dialogView)
            .create()

        itemLiveTv.setOnClickListener {
            dialog.dismiss()
            if (vodMode) switchToLiveMode()
            showChannelPanel(expanded = false)
        }
        itemVod.setOnClickListener {
            dialog.dismiss()
            openVodLibrary()
        }
        itemIptv.setOnClickListener {
            dialog.dismiss()
            openIptvEditor()
        }
        itemXmltv.setOnClickListener {
            dialog.dismiss()
            startActivity(Intent(this, XmlTvSourcesActivity::class.java))
        }
        itemBrowser.setOnClickListener {
            dialog.dismiss()
            openTvBrowser()
        }
        itemGuide.setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, ProgramGuideActivity::class.java).apply {
                putExtra(ProgramGuideActivity.EXTRA_CURRENT_SOURCE_KEY, currentChannel?.sourceKey)
            }
            programGuide.launch(intent)
        }
        itemMultiview.setOnClickListener {
            dialog.dismiss()
            showIptvGridPicker()
        }
        itemSettings.setOnClickListener {
            dialog.dismiss()
            openDisplaySettings()
        }

        dialog.setOnShowListener {
            itemLiveTv.post { itemLiveTv.requestFocus() }
        }
        quickHubDialog = dialog
        dialog.show()
    }

    private fun switchToLiveMode(targetSource: String? = null) {
        saveCurrentIptvResumePosition()
        vodMode = false
        vodResolveJob?.cancel()
        vodResolveJob = null
        binding.iptvBufferingContainer.visibility = View.GONE
        intent.removeExtra(EXTRA_VOD_SOURCE_KEY)
        intent.removeExtra(EXTRA_VOD_STREAM_URL)
        intent.removeExtra(EXTRA_VOD_TITLE)
        intent.removeExtra(EXTRA_VOD_EPISODE_TITLE)
        intent.removeExtra(EXTRA_VOD_LOGO_URL)
        if (currentChannel?.isVodContent() == true) {
            currentChannel = null
        }
        channelResolutionGeneration++
        val requestGeneration = channelResolutionGeneration
        iptvPlayback.stop()
        hideIptvPlaybackControls()
        vodPlayerOverlayController?.hideControls()
        binding.vodPlayerOverlay.visibility = View.GONE
        channelPanelContent = ChannelPanelContent.NORMAL
        applyChannelFilter(requestFocus = false)
        val lastKey = modePreferences.getString(LAST_LIVE_KEY, null)
        lifecycleScope.launch {
            val last = channels.firstOrNull { it.sourceKey == lastKey } ?: withContext(Dispatchers.IO) {
                lastKey?.let { iptvRepository.channel(it) }
            }
            if (vodMode || requestGeneration != channelResolutionGeneration) return@launch
            val channel = when (targetSource) {
                "TIF" -> channels.firstOrNull { it.source == LiveChannel.Source.TIF && !it.isVodContent() }
                "IPTV" -> channels.firstOrNull { it.source == LiveChannel.Source.IPTV && !it.isVodContent() }
                else -> null
            } ?: last?.takeUnless { it.isVodContent() } ?: channels.firstOrNull { !it.isVodContent() }
            if (channel != null) selectChannel(channel, recordHistory = false)
            else loadChannels(preserveCurrentPlayback = false)
            showChannelPanel(expanded = false)
        }
    }

    private fun showSubtitleTracks() {
        trackSelectionHelper.showSubtitleTracks()
    }

    private fun trackLabel(track: TvTrackInfo): String = trackSelectionHelper.trackLabel(track)

    private fun applyPreferredIptvTracks() {
        displayPreferences = displayPreferencesStore.load()
        val audioTracks = iptvPlayback.audioTracks()
        val subtitleTracks = iptvPlayback.subtitleTracks()
        val resolved = resolveCurrentTrackPreferences()
        val preferredAudio = resolved.audioLanguage
        if (preferredAudio != null) {
            audioTracks.firstOrNull {
                !it.selected && it.language.equals(preferredAudio, ignoreCase = true)
            }?.let { iptvPlayback.selectAudioTrack(it.id) }
        }
        if (!resolved.subtitlesEnabled) {
            if (subtitleTracks.any(IptvTrackOption::selected)) iptvPlayback.selectSubtitleTrack(null)
        } else {
            val preferred = subtitleTracks.firstOrNull {
                it.language.equals(resolved.subtitleLanguage, ignoreCase = true)
            } ?: subtitleTracks.firstOrNull()
            if (preferred != null && !preferred.selected) iptvPlayback.selectSubtitleTrack(preferred.id)
        }
        updateIptvTrackBadges(audioTracks, subtitleTracks)
    }

    private fun updateIptvTrackBadges(
        audioTracks: List<IptvTrackOption>,
        subtitleTracks: List<IptvTrackOption>,
    ) {
        binding.audioBadge.text = audioTracks.firstOrNull(IptvTrackOption::selected)?.language
            ?.let(::displayLanguage) ?: audioTracks.size.takeIf { it > 0 }?.toString()
        binding.audioBadge.visibility = if (audioTracks.isEmpty()) View.GONE else View.VISIBLE
        binding.subtitleBadge.text = subtitleTracks.firstOrNull(IptvTrackOption::selected)?.language
            ?.let(::displayLanguage) ?: subtitleTracks.size.takeIf { it > 0 }?.toString()
        binding.subtitleBadge.visibility = if (subtitleTracks.isEmpty()) View.GONE else View.VISIBLE
        binding.techSlotAudio.visibility = if (audioTracks.isEmpty()) View.GONE else View.VISIBLE
        binding.techSlotSubtitle.visibility = if (subtitleTracks.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun iptvTrackLabel(track: IptvTrackOption): String = trackSelectionHelper.iptvTrackLabel(track)

    private fun openDisplaySettings() {
        displaySettings.launch(Intent(this, DisplaySettingsActivity::class.java))
    }

    private fun openSystemSettings() {
        val intents = listOf(
            Intent(android.provider.Settings.ACTION_SETTINGS),
            Intent("android.settings.QUICK_SETTINGS"),
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                if (::debugLog.isInitialized) {
                    debugLog.recordDebug("SYSTEM_SETTINGS_OPENED | action=${intent.action}")
                }
                return
            } catch (e: Exception) {
                if (::debugLog.isInitialized) {
                    debugLog.recordDebug("SYSTEM_SETTINGS_FAIL | action=${intent.action}, err=${e.message}")
                }
            }
        }
        Toast.makeText(this, R.string.system_settings_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun showPhysicalInputSelector() {
        if (vodMode) {
            showPlaybackModeDialog()
            return
        }
        val inputs = repository.physicalInputs()
        if (inputs.isEmpty()) {
            Toast.makeText(this, R.string.no_physical_inputs, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = inputs.map { input ->
            val name = input.loadLabel(this).toString().ifBlank { input.id.substringAfterLast('/') }
            val type = when (input.type) {
                TvInputInfo.TYPE_TUNER -> getString(R.string.input_type_tv)
                TvInputInfo.TYPE_HDMI -> getString(R.string.input_type_hdmi)
                TvInputInfo.TYPE_COMPOSITE -> getString(R.string.input_type_av)
                TvInputInfo.TYPE_COMPONENT -> getString(R.string.input_type_component)
                TvInputInfo.TYPE_DISPLAY_PORT -> getString(R.string.input_type_display_port)
                else -> getString(R.string.input_type_external)
            }
            "$name  ·  $type"
        }.toTypedArray()
        val selected = inputs.indexOfFirst { input ->
            input.id == activePassthroughInputId ||
                (activePassthroughInputId == null && input.id == currentChannel?.inputId)
        }.coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.physical_inputs)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                dialog.dismiss()
                selectPhysicalInput(inputs[which])
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun selectPhysicalInput(input: TvInputInfo) {
        hideChannelPanel()
        setInfoBarVisible(false)
        debugLog.recordDebug("PHYSICAL_INPUT_SELECT | id=${input.id}, type=${input.type}")
        if (!input.isPassthroughInput && input.type == TvInputInfo.TYPE_TUNER) {
            val channel = currentChannel?.takeIf {
                it.source == LiveChannel.Source.TIF && it.inputId == input.id
            } ?: channels.firstOrNull {
                it.source == LiveChannel.Source.TIF && it.inputId == input.id
            }
            if (channel == null) {
                Toast.makeText(this, R.string.input_has_no_channels, Toast.LENGTH_SHORT).show()
                return
            }
            playback.stop()
            selectChannel(channel, recordHistory = false)
            return
        }
        focusedTuneJob?.cancel()
        iptvPlayback.stop()
        playback.stop()
        binding.iptvPlayerView.visibility = View.GONE
        binding.audioOnlyPanel.visibility = View.GONE
        osdCoordinator.hideParentalLock()
        osdCoordinator.hideStatus()
        binding.tvView.visibility = View.VISIBLE
        activePassthroughInputId = input.id
        endBlackout("PHYSICAL_INPUT")
        runCatching { playback.playPassthrough(input.id) }
            .onFailure { error ->
                debugLog.recordDebug(
                    "PHYSICAL_INPUT_FAILURE | id=${input.id}, ${error.javaClass.name}: ${error.message}",
                )
                showPlaybackError(null, error.message ?: error.javaClass.simpleName)
            }
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
        osdCoordinator.setPlaybackMode(
            if (internalMiniPlayerActive) PlaybackSurfaceMode.INTERNAL_MINI_PLAYER
            else PlaybackSurfaceMode.SINGLE,
        )
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
        channelZapper.clearNumberInput()
        osdCoordinator.clearOsd()
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
        if (vodMode) {
            openVodLibrary()
            return
        }
        hideChannelPanel()
        infoBarJob?.cancel()
        setInfoBarVisible(false)
        programGuide.launch(
            Intent(this, ProgramGuideActivity::class.java).putExtra(
                ProgramGuideActivity.EXTRA_CURRENT_SOURCE_KEY,
                currentChannel?.sourceKey,
            ),
        )
    }

    private fun showIptvLibraryFilterDialog() {
        if (!hasIptvAccess(::showIptvLibraryFilterDialog)) return
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
                getString(R.string.iptv_library_continue),
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
            typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long,
                ) {
                    val categoryEnabled = contentTypes.getOrNull(position) !=
                        IptvLibraryContentType.CONTINUE
                    categorySpinner.isEnabled = categoryEnabled
                    categorySpinner.alpha = if (categoryEnabled) 1f else 0.45f
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
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
                    val category = if (type == IptvLibraryContentType.CONTINUE) {
                        null
                    } else {
                        categories.getOrNull(categorySpinner.selectedItemPosition)
                    }
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
        if (!hasIptvAccess(::openSavedIptvLibrary)) return
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
        if (!hasIptvAccess(::openIptvEditor)) return
        focusedTuneJob?.cancel()
        iptvSources.launch(Intent(this, IptvSourcesActivity::class.java))
    }

    private fun hasIptvAccess(onGranted: () -> Unit): Boolean {
        if (IptvEntitlementManager(this).snapshot().accessGranted) return true
        IptvAccessDialogs.requireAccess(this, onGranted)
        return false
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
        if (contentType == IptvLibraryContentType.ALL) {
            applyIptvLibraryFilter(sourceId, sourceName, IptvLibraryContentType.LIVE, category)
            return
        }
        if (contentType != IptvLibraryContentType.LIVE) {
            openVodLibrary()
            return
        }
        alphabetFilterJob?.cancel()
        alphabetLetter = null
        iptvAlphabetInitials = null
        continuedAlphabetChannels = emptyList()
        channelPanelContent = ChannelPanelContent.IPTV_LIBRARY
        adapter.showIptvMembership(true)
        iptvLibraryLoadJob?.cancel()
        iptvLibrarySourceId = sourceId
        iptvLibraryContentType = contentType
        iptvLibraryCategory = category
        channelSearchQuery = ""
        binding.channelSearchButton.isSelected = false
        iptvLibraryGeneration++
        iptvLibraryWindowStart = 0
        iptvLibraryTotalCount = 0
        iptvLibraryHasPrevious = false
        iptvLibraryHasNext = false
        iptvLibraryFirstAnchor = null
        iptvLibraryLastAnchor = null
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
                IptvLibraryContentType.CONTINUE -> getString(R.string.iptv_library_continue)
            },
            category,
        ).joinToString(" · ")
        adapter.submitList(iptvLibraryChannels)
        adapter.submitPrograms(currentPrograms)
        updateIptvLibraryCount()
        val generation = iptvLibraryGeneration
        lifecycleScope.launch {
            if (contentType == IptvLibraryContentType.CONTINUE) {
                val sourceInputId = "iptv:$sourceId"
                val continued = withContext(Dispatchers.IO) {
                    iptvResumeStore.entries().mapNotNull { entry ->
                        iptvRepository.channel(entry.sourceKey)
                    }.filter { channel ->
                        channel.inputId == sourceInputId && channel.iptvContentType == "VOD"
                    }
                }.mapIndexed { index, channel ->
                    channel.copy(displayNumber = (index + 1).toString())
                }
                if (
                    channelPanelContent != ChannelPanelContent.IPTV_LIBRARY ||
                    iptvLibrarySourceId != sourceId ||
                    generation != iptvLibraryGeneration ||
                    iptvLibraryContentType != IptvLibraryContentType.CONTINUE
                ) return@launch
                continuedAlphabetChannels = continued
                iptvLibraryChannels = continued
                iptvLibraryTotalCount = continued.size
                iptvLibraryWindowStart = 0
                iptvLibraryHasPrevious = false
                iptvLibraryHasNext = false
                adapter.submitList(continued)
                currentChannel?.let { adapter.select(it.sourceKey) }
                updateIptvLibraryCount()
                focusCurrentListChannel()
                return@launch
            }
            val count = withContext(Dispatchers.IO) {
                iptvRepository.libraryChannelCount(
                    sourceId,
                    category,
                    contentType.name,
                    channelSearchQuery,
                )
            }
            if (generation != iptvLibraryGeneration) return@launch
            iptvLibraryTotalCount = count
            updateIptvLibraryCount()
            loadIptvLibraryWindow(IptvPageDirection.FIRST)
        }
        if (binding.channelPanel.visibility != View.VISIBLE) {
            showChannelPanel(expanded = false)
        } else {
            focusCurrentListChannel()
            scheduleChannelPanelClose()
        }
    }

    private fun loadIptvLibraryWindow(
        direction: IptvPageDirection,
        targetIndex: Int = 0,
    ) {
        if (
            channelPanelContent != ChannelPanelContent.IPTV_LIBRARY ||
            iptvLibraryContentType == IptvLibraryContentType.CONTINUE ||
            iptvLibraryLoadJob?.isActive == true
        ) return
        val sourceId = iptvLibrarySourceId ?: return
        val category = iptvLibraryCategory
        val contentType = iptvLibraryContentType
        val query = channelSearchQuery
        val initials = iptvAlphabetInitials
        val generation = iptvLibraryGeneration
        val anchor = when (direction) {
            IptvPageDirection.NEXT -> iptvLibraryLastAnchor
            IptvPageDirection.PREVIOUS -> iptvLibraryFirstAnchor
            else -> null
        }
        if ((direction == IptvPageDirection.NEXT || direction == IptvPageDirection.PREVIOUS) &&
            anchor == null
        ) return
        iptvLibraryLoadJob = lifecycleScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val page = withContext(Dispatchers.IO) {
                iptvRepository.libraryLiveChannelsWindow(
                    sourceId,
                    category,
                    contentType.name,
                    IPTV_LIBRARY_PAGE_SIZE,
                    direction,
                    anchor,
                    targetIndex,
                    query,
                    initials,
                )
            }
            if (
                channelPanelContent != ChannelPanelContent.IPTV_LIBRARY ||
                iptvLibrarySourceId != sourceId ||
                iptvLibraryCategory != category ||
                iptvLibraryContentType != contentType ||
                channelSearchQuery != query ||
                iptvLibraryGeneration != generation
            ) return@launch
            val previousSize = iptvLibraryChannels.size
            iptvLibraryWindowStart = when (direction) {
                IptvPageDirection.FIRST -> 0
                IptvPageDirection.NEXT -> iptvLibraryWindowStart + previousSize
                IptvPageDirection.PREVIOUS ->
                    (iptvLibraryWindowStart - page.channels.size).coerceAtLeast(0)
                IptvPageDirection.LAST ->
                    (iptvLibraryTotalCount - page.channels.size).coerceAtLeast(0)
                IptvPageDirection.AT_INDEX -> targetIndex.coerceIn(
                    0,
                    (iptvLibraryTotalCount - page.channels.size).coerceAtLeast(0),
                )
            }
            iptvLibraryFirstAnchor = page.firstAnchor
            iptvLibraryLastAnchor = page.lastAnchor
            iptvLibraryHasPrevious = iptvLibraryWindowStart > 0
            iptvLibraryHasNext =
                iptvLibraryWindowStart + page.channels.size < iptvLibraryTotalCount
            iptvLibraryChannels = page.channels.mapIndexed { index, channel ->
                channel.copy(displayNumber = (iptvLibraryWindowStart + index + 1).toString())
            }
            adapter.submitList(iptvLibraryChannels)
            currentChannel?.let { adapter.select(it.sourceKey) }
            updateIptvLibraryCount()
            debugLog.recordDebug(
                "IPTV_LIBRARY_KEYSET | source=$sourceId, direction=$direction, " +
                    "windowStart=$iptvLibraryWindowStart, loaded=${page.channels.size}, " +
                    "total=$iptvLibraryTotalCount, hasPrevious=$iptvLibraryHasPrevious, " +
                    "hasNext=$iptvLibraryHasNext, " +
                    "durationMs=${SystemClock.elapsedRealtime() - startedAt}",
            )
            val target = when (direction) {
                IptvPageDirection.PREVIOUS, IptvPageDirection.LAST ->
                    iptvLibraryChannels.lastIndex.coerceAtLeast(0)
                else -> 0
            }
            if (alphabetBrowsing) {
                alphabetTargetPosition = target
                binding.channelList.scrollToPosition(target)
                loadVisiblePrograms()
            } else focusIptvLibraryPosition(target)
        }
    }

    private fun openChannelAlphabet() {
        focusedTuneJob?.cancel()
        channelPanelJob?.cancel()
        if (alphabetRail != null || alphabetLoadJob?.isActive == true) return
        val isLibrary = channelPanelContent == ChannelPanelContent.IPTV_LIBRARY &&
            iptvLibraryContentType != IptvLibraryContentType.CONTINUE
        val sourceId = iptvLibrarySourceId
        val category = iptvLibraryCategory
        val type = iptvLibraryContentType
        val query = channelSearchQuery
        val generation = iptvLibraryGeneration
        val isContinue = channelPanelContent == ChannelPanelContent.IPTV_LIBRARY &&
            iptvLibraryContentType == IptvLibraryContentType.CONTINUE
        val channels = when {
            isLibrary -> panelChannels().toList()
            isContinue -> continuedAlphabetChannels
            else -> buildNormalPanelChannels(includeAlphabet = false)
        }
        val initialLetter = alphabetLetter ?: getString(R.string.all_channels)
        val baseTitle = binding.channelListTitle.text.toString().let {
            if (alphabetLetter != null) it.removeSuffix(" · $alphabetLetter") else it
        }
        alphabetLoadJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (isLibrary && sourceId != null) {
                        iptvRepository.libraryAlphabetInitials(sourceId, category, type.name, query)
                    } else channels.groupBy { com.tvapp.livetv.ui.AlphabetJump.letter(it.displayName) }
                        .mapValues { (_, entries) -> entries.map { it.displayName } }
                }
            }
            if (binding.channelPanel.visibility != View.VISIBLE ||
                generation != iptvLibraryGeneration || query != channelSearchQuery
            ) return@launch
            val initialsByLetter = result.getOrElse {
                debugLog.recordDebug("ALPHABET_INDEX_FAILURE | ${it.javaClass.simpleName}")
                scheduleChannelPanelClose()
                return@launch
            }
            val letters = com.tvapp.livetv.ui.AlphabetJump.targets(initialsByLetter.keys.map { it to 0 })
            val targets = listOf(com.tvapp.livetv.ui.AlphabetTarget(getString(R.string.all_channels), -1)) +
                letters.mapIndexed { index, target -> target.copy(position = index) }
            alphabetTargetPosition = panelChannels().indexOfFirst { it.sourceKey == focusedListSourceKey }.coerceAtLeast(0)
            alphabetBrowsing = true
            alphabetRail = com.tvapp.livetv.ui.AlphabetRail(binding.channelList, targets, initialLetter, onJump = { position ->
                focusedTuneJob?.cancel()
                alphabetLetter = letters.getOrNull(position)?.letter
                binding.channelListTitle.text = listOfNotNull(baseTitle, alphabetLetter).joinToString(" · ")
                alphabetTargetPosition = 0
                if (isLibrary) {
                    alphabetFilterJob?.cancel()
                    iptvLibraryLoadJob?.cancel()
                    iptvAlphabetInitials = alphabetLetter?.let { initialsByLetter[it].orEmpty() }
                    val selectedInitials = iptvAlphabetInitials
                    val filterGeneration = ++iptvLibraryGeneration
                    iptvLibraryFirstAnchor = null
                    iptvLibraryLastAnchor = null
                    alphabetFilterJob = lifecycleScope.launch {
                        val count = withContext(Dispatchers.IO) {
                            iptvRepository.libraryChannelCount(
                                sourceId ?: return@withContext 0, category, type.name, query, selectedInitials,
                            )
                        }
                        if (filterGeneration != iptvLibraryGeneration) return@launch
                        iptvLibraryTotalCount = count
                        updateIptvLibraryCount()
                        loadIptvLibraryWindow(IptvPageDirection.FIRST)
                    }
                } else if (isContinue) {
                    iptvLibraryChannels = channels.filter {
                        alphabetLetter == null || com.tvapp.livetv.ui.AlphabetJump.letter(it.displayName) == alphabetLetter
                    }.mapIndexed { index, channel -> channel.copy(displayNumber = (index + 1).toString()) }
                    iptvLibraryTotalCount = iptvLibraryChannels.size
                    adapter.submitList(iptvLibraryChannels)
                    updateIptvLibraryCount()
                    binding.channelList.scrollToPosition(0)
                } else {
                    applyChannelFilter(requestFocus = false)
                    binding.channelList.scrollToPosition(0)
                }
            }, onClose = {
                alphabetRail = null
                lifecycleScope.launch {
                    if (isLibrary) {
                        alphabetFilterJob?.join()
                        iptvLibraryLoadJob?.join()
                    }
                    binding.channelList.post {
                        if (binding.channelPanel.visibility == View.VISIBLE && !isFinishing) {
                            binding.channelList.findViewHolderForAdapterPosition(alphabetTargetPosition)
                                ?.itemView?.requestFocus()
                            scheduleChannelPanelClose()
                        }
                        alphabetBrowsing = false
                    }
                }
            }).also { it.show() }
        }
    }

    private fun focusIptvLibraryPosition(position: Int) {
        if (iptvLibraryChannels.isEmpty() || binding.channelPanel.visibility != View.VISIBLE) return
        val target = position.coerceIn(0, iptvLibraryChannels.lastIndex)
        binding.channelList.scrollToPosition(target)
        binding.channelList.post {
            binding.channelList.findViewHolderForAdapterPosition(target)
                ?.itemView
                ?.requestFocus()
            loadVisiblePrograms()
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
        val selectingMultiView = multiViewAdding
        val index = number?.minus(1) ?: return
        if (index !in 0 until iptvLibraryTotalCount) {
            binding.nowChannel.setText(R.string.channel_not_found)
            return
        }
        if (iptvLibraryContentType == IptvLibraryContentType.CONTINUE) {
            val channel = iptvLibraryChannels.getOrNull(index) ?: return
            if (multiViewAdding) {
                toggleMultiViewChannel(channel)
                return
            }
            selectChannel(channel, recordHistory = BuildConfig.MOBILE_UI_ENABLED)
            hideChannelPanel()
            return
        }
        lifecycleScope.launch {
            val page = withContext(Dispatchers.IO) {
                iptvRepository.libraryLiveChannelsWindow(
                    sourceId,
                    iptvLibraryCategory,
                    iptvLibraryContentType.name,
                    IPTV_LIBRARY_PAGE_SIZE,
                    IptvPageDirection.AT_INDEX,
                    targetIndex = index,
                    query = channelSearchQuery,
                    initials = iptvAlphabetInitials,
                )
            }
            val channel = page.channels.firstOrNull() ?: return@launch
            if (selectingMultiView) {
                if (!multiViewAdding) return@launch
                toggleMultiViewChannel(channel)
                return@launch
            }
            selectChannel(channel.copy(displayNumber = number.toString()), recordHistory = BuildConfig.MOBILE_UI_ENABLED)
            hideChannelPanel()
        }
    }

    private fun showIptvLibraryBoundary(last: Boolean) {
        iptvLibrarySourceId ?: return
        if (iptvLibraryTotalCount == 0 || iptvLibraryLoadJob?.isActive == true) return
        if (iptvLibraryContentType == IptvLibraryContentType.CONTINUE) {
            val target = if (last) iptvLibraryChannels.lastIndex else 0
            binding.channelList.scrollToPosition(target)
            binding.channelList.post {
                binding.channelList.findViewHolderForAdapterPosition(target)
                    ?.itemView
                    ?.requestFocus()
            }
            return
        }
        loadIptvLibraryWindow(
            if (last) IptvPageDirection.LAST else IptvPageDirection.FIRST,
        )
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun setupMultiViewController() {
        multiViewController = MultiViewController(
            activity = this,
            binding = binding,
            playback = playback,
            iptvPlayback = iptvPlayback,
            secondaryIptvPlayback = secondaryIptvPlayback,
            iptvRepository = iptvRepository,
            deviceResourcePolicy = deviceResourcePolicy,
            osdCoordinator = osdCoordinator,
            debugLog = debugLog,
            scope = lifecycleScope,
            getCurrentChannel = { currentChannel },
            getAllChannels = { channels },
            getIptvLibraryChannels = { iptvLibraryChannels },
            isChannelPanelContentIptvLibrary = { channelPanelContent == ChannelPanelContent.IPTV_LIBRARY },
            isVodMode = { vodMode },
            openVodLibrary = { openVodLibrary() },
            hasIptvAccess = { action -> hasIptvAccess(action) },
            playSelectedChannel = { channel, recordHistory -> playSelectedChannel(channel, recordHistory) },
            endBlackout = { reason -> endBlackout(reason) },
            showChannelPanel = { expanded -> showChannelPanel(expanded) },
            hideChannelPanel = { hideChannelPanel() },
            setInfoBarVisible = { visible -> setInfoBarVisible(visible) },
            updateChannelActionLabels = { updateChannelActionLabels() },
            cancelFocusedTuneJob = { focusedTuneJob?.cancel() },
            cancelChannelPanelJob = { channelPanelJob?.cancel() },
        )
        multiViewController.restoredMultiViewKeys = restoredMultiViewKeys
    }

    private fun showIptvGridPicker(initialSelection: List<LiveChannel>? = null) {
        multiViewController.showIptvGridPicker(initialSelection)
    }

    private fun startIptvGrid(selected: List<LiveChannel>, connectionsChecked: Boolean = false) {
        multiViewController.startIptvGrid(selected, connectionsChecked)
    }

    private fun stopIptvGrid(resumePrevious: Boolean) {
        multiViewController.stopIptvGrid(resumePrevious)
    }

    private fun startIptvOverlay(channel: LiveChannel, connectionsChecked: Boolean = false) {
        multiViewController.startIptvOverlay(channel, connectionsChecked)
    }

    private fun stopIptvOverlay() {
        multiViewController.stopIptvOverlay()
    }

    private fun beginMultiViewChannelSelection() {
        multiViewController.beginMultiViewChannelSelection()
    }

    private fun toggleMultiViewChannel(channel: LiveChannel) {
        multiViewController.toggleMultiViewChannel(channel)
    }

    private fun closeActiveGridChannelFullscreen() {
        multiViewController.closeActiveGridChannelFullscreen()
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
        lifecycleScope.launch {
            val (resolvedChannel, state) = withContext(Dispatchers.IO) {
                val resolved = if (
                    channel.source == LiveChannel.Source.IPTV && channel.uri.isBlank()
                ) {
                    iptvRepository.channel(channel.sourceKey)?.copy(
                        displayNumber = channel.displayNumber,
                        inMainList = channel.inMainList,
                    ) ?: channel
                } else {
                    channel
                }
                resolved to (
                    repository.isFavorite(channel.sourceKey) to if (
                        channel.source == LiveChannel.Source.IPTV
                    ) {
                        iptvRepository.isChannelSelected(channel.sourceKey)
                    } else {
                        true
                    }
                )
            }
            showChannelManagementDialog(resolvedChannel, state.first, state.second)
        }
    }

    private fun showChannelManagementDialog(
        channel: LiveChannel,
        favorite: Boolean,
        inMainList: Boolean,
    ) {
        val labels = mutableListOf<String>()
        val handlers = mutableListOf<() -> Unit>()
        fun action(label: String, handler: () -> Unit) {
            labels += label
            handlers += handler
        }
        action(getString(if (favorite) R.string.remove_favorite else R.string.add_favorite)) {
            updateChannelInPlace(channel, channel.copy(favorite = !favorite)) {
                repository.setFavorite(channel, !favorite)
            }
        }
        if (channel.source == LiveChannel.Source.IPTV) {
            if (channel.iptvContentType.equals("LIVE", true)) {
                action(getString(R.string.iptv_live_connection_method)) { showLiveConnectionMethod(channel) }
            }
            if (channel.sourceKey == currentChannel?.sourceKey) {
                action(getString(R.string.iptv_quality_action)) { showIptvVideoQuality() }
            }
            action(getString(R.string.iptv_pip)) { startIptvOverlay(channel) }
            action(getString(R.string.open_external_player)) { openExternalPlayer(channel) }
            action(getString(R.string.add_external_subtitle)) {
                openExternalSubtitle.launch(
                    arrayOf(
                        "application/x-subrip",
                        "text/vtt",
                        "text/plain",
                        "application/ttml+xml",
                    ),
                )
            }
            action(
                getString(
                    if (inMainList) R.string.remove_from_channel_list
                    else R.string.add_to_channel_list,
                ),
            ) {
                updateIptvMainListMembership(channel, !inMainList)
            }
        }
        action(getString(R.string.move_up)) {
            updateChannel { repository.moveChannel(channel.sourceKey, -1) }
        }
        action(getString(R.string.move_down)) {
            updateChannel { repository.moveChannel(channel.sourceKey, 1) }
        }
        action(getString(R.string.change_channel_number)) { showChannelNumberEditor(channel) }
        action(getString(R.string.change_channel_name)) { showChannelNameEditor(channel) }
        action(
            getString(
                if (parentalControlStore.isLocked(channel.sourceKey)) {
                    R.string.unlock_channel
                } else {
                    R.string.lock_channel
                },
            ),
        ) { toggleChannelLock(channel) }
        action(getString(R.string.system_information)) { showChannelSystemInformation(channel) }
        action(getString(R.string.program_guide)) {
            programGuide.launch(
                Intent(this, ProgramGuideActivity::class.java).putExtra(
                    ProgramGuideActivity.EXTRA_CURRENT_SOURCE_KEY,
                    channel.sourceKey,
                ),
            )
        }
        if (BuildConfig.DIAGNOSTICS_ENABLED) {
            action(getString(R.string.epg_diagnostics)) {
                val runtime = if (
                    channel.source == LiveChannel.Source.TIF &&
                    channel.sourceKey == currentChannel?.sourceKey
                ) {
                    val tracks = playback.allTracks()
                    val video = playback.currentVideoState()
                    "tracks=${tracks.size}, video=${video.width}x${video.height}, " +
                        "available=${video.available}, unavailableReason=${video.unavailableReason}"
                } else {
                    "not-current-tif-channel"
                }
                startActivity(EpgDiagnosticsActivity.intent(this, channel, runtime))
            }
        }
        action(getString(if (channel.sourceKey in multiViewDraft) R.string.multiview_remove_channel else R.string.multiview_add_channel)) {
            toggleMultiViewChannel(channel)
        }
        AlertDialog.Builder(this)
            .setTitle(channel.displayName)
            .setItems(labels.toTypedArray()) { _, which -> handlers[which]() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showLiveConnectionMethod(channel: LiveChannel, applyImmediately: Boolean = false) {
        lifecycleScope.launch {
            try {
                val enabled = withContext(Dispatchers.IO) {
                    val defaultMethod = com.tvapp.livetv.settings.IptvPlaybackPreferencesStore(this@MainActivity).load().defaultLiveConnectionMethod
                    val pref = repository.channelPreference(channel.sourceKey)
                    pref?.removeLiveOverlap ?: (defaultMethod == com.tvapp.livetv.settings.IptvPlaybackPreferences.LIVE_CONNECTION_OVERLAP)
                }
                if (isFinishing || isDestroyed) return@launch
                var selected = enabled
                val dialog = AlertDialog.Builder(this@MainActivity)
                    .setTitle(getString(R.string.iptv_live_connection_channel, channel.displayName))
                    .setSingleChoiceItems(arrayOf(
                        getString(R.string.iptv_live_connection_standard),
                        getString(R.string.iptv_live_connection_overlap),
                    ), if (enabled) 1 else 0) { _, which -> selected = which == 1 }
                    .setPositiveButton(R.string.save, null)
                    .setNegativeButton(R.string.close, null)
                    .show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { button ->
                    button.isEnabled = false
                    lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) { repository.setRemoveLiveOverlap(channel, selected) }
                            dialog.dismiss()
                            val applied = applyImmediately && currentChannel?.sourceKey == channel.sourceKey
                            if (applied) iptvPlayback.play(channel)
                            Toast.makeText(this@MainActivity, if (applied) R.string.iptv_live_connection_applied
                                else R.string.iptv_live_connection_saved, Toast.LENGTH_LONG).show()
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            debugLog.recordDebug("LIVE_CONNECTION_SAVE_FAILURE | ${error.javaClass.simpleName}")
                            button.isEnabled = true
                            Toast.makeText(this@MainActivity, R.string.iptv_live_connection_error, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                debugLog.recordDebug("LIVE_CONNECTION_LOAD_FAILURE | ${error.javaClass.simpleName}")
                Toast.makeText(this@MainActivity, R.string.iptv_live_connection_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showChannelSystemInformation(channel: LiveChannel) {
        lifecycleScope.launch {
            val rawValues = if (channel.source == LiveChannel.Source.TIF) {
                withContext(Dispatchers.IO) { repository.tifChannelRawValues(channel.id) }
                    .getOrElse { error ->
                        listOf(
                            getString(R.string.system_info_raw_read_error) to
                                "${error.javaClass.simpleName}: ${error.message.orEmpty()}",
                        )
                    }
            } else {
                emptyList()
            }
            if (!isFinishing && !isDestroyed) {
                showChannelSystemInformationDialog(channel, rawValues)
            }
        }
    }

    private fun showChannelSystemInformationDialog(
        channel: LiveChannel,
        rawValues: List<Pair<String, String>>,
    ) {
        val isCurrentTif = channel.source == LiveChannel.Source.TIF &&
            channel.sourceKey == currentChannel?.sourceKey
        val isCurrentIptv = channel.source == LiveChannel.Source.IPTV &&
            channel.sourceKey == currentChannel?.sourceKey
        val iptvHealth = if (isCurrentIptv) iptvPlayback.healthSnapshot() else null
        val tracks = if (isCurrentTif) playback.allTracks() else emptyList()
        val videoState = if (isCurrentTif) playback.currentVideoState() else null
        val videoTrack = tracks.filter { it.type == TvTrackInfo.TYPE_VIDEO }
            .maxByOrNull { it.videoWidth.toLong() * it.videoHeight }
        val width = videoState?.width?.takeIf { it > 0 } ?: videoTrack?.videoWidth ?: 0
        val height = videoState?.height?.takeIf { it > 0 } ?: videoTrack?.videoHeight ?: 0
        val quality = VideoQuality.label(
            width,
            height,
            channel.videoFormat.orEmpty().uppercase(Locale.ROOT),
        ) ?: getString(R.string.unknown_value)
        val rows = buildList {
            add(getString(R.string.system_info_section_app) to "")
            add(getString(R.string.system_info_source) to channel.source.name)
            add(getString(R.string.system_info_channel_id) to channel.id.toString())
            add(
                getString(R.string.system_info_source_key) to if (channel.source == LiveChannel.Source.IPTV) {
                    getString(R.string.system_info_private_value_hidden)
                } else {
                    channel.sourceKey
                },
            )
            add(getString(R.string.system_info_input_id) to channel.inputId)
            add(
                getString(R.string.system_info_channel_uri) to if (channel.source == LiveChannel.Source.IPTV) {
                    sanitizedIptvLocation(channel.uri)
                } else {
                    channel.uri
                },
            )
            add(getString(R.string.system_info_channel_number) to channel.displayNumber)
            add(getString(R.string.system_info_channel_name) to channel.displayName)
            add(getString(R.string.system_info_service_type) to channel.serviceType.orUnknown())
            add(getString(R.string.system_info_video_format) to channel.videoFormat.orUnknown())
            if (channel.source == LiveChannel.Source.TIF) {
                add(getString(R.string.system_info_section_raw_channel) to "")
                if (rawValues.isEmpty()) {
                    add(getString(R.string.system_info_raw_channel_empty) to getString(R.string.unknown_value))
                } else {
                    addAll(rawValues)
                }
            }
            if (channel.source == LiveChannel.Source.TIF) {
            add(getString(R.string.system_info_section_callback) to "")
            add(
                getString(R.string.system_info_live_video_state) to when (videoState?.available) {
                    true -> getString(R.string.system_info_video_available)
                    false -> getString(R.string.system_info_video_unavailable)
                    null -> getString(R.string.unknown_value)
                },
            )
            add(
                getString(R.string.system_info_callback_resolution) to if (width > 0 && height > 0) {
                    "$width × $height"
                } else {
                    getString(R.string.unknown_value)
                },
            )
            videoState?.unavailableReason?.let { reason ->
                add(getString(R.string.system_info_unavailable_reason) to reason.toString())
            }
            val callbackEvents = if (isCurrentTif) playback.callbackEventHistory() else emptyList()
            if (callbackEvents.isEmpty()) {
                add(getString(R.string.system_info_callback_events) to getString(R.string.none))
            } else {
                val callbackTime = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
                callbackEvents.forEachIndexed { index, event ->
                    val prefix = getString(R.string.system_info_callback_index, index + 1)
                    add("$prefix ${getString(R.string.system_info_callback_name)}" to event.name)
                    add(
                        "$prefix ${getString(R.string.system_info_callback_time)}" to
                            callbackTime.format(Date(event.timestampMillis)),
                    )
                    event.values.forEach { (key, value) -> add("$prefix $key" to value) }
                }
            }
            }
            if (channel.source == LiveChannel.Source.IPTV) {
                add(getString(R.string.system_info_section_iptv_health) to "")
                if (iptvHealth == null) {
                    add(
                        getString(R.string.system_info_iptv_health) to
                            getString(R.string.system_info_tracks_current_only),
                    )
                } else {
                    addIptvHealthRows(iptvHealth)
                }
            }
            add(
                getString(R.string.system_info_quality) to if (iptvHealth != null) {
                    VideoQuality.label(
                        iptvHealth.width ?: 0,
                        iptvHealth.height ?: 0,
                        channel.videoFormat.orEmpty().uppercase(Locale.ROOT),
                    ) ?: getString(R.string.unknown_value)
                } else {
                    quality
                },
            )
            add(getString(R.string.system_info_encrypted) to yesNo(channel.encrypted))
            add(getString(R.string.system_info_locked) to yesNo(channel.locked))
            if (channel.source == LiveChannel.Source.TIF && tracks.isEmpty()) {
                add(
                    getString(R.string.system_info_tracks) to getString(
                        if (channel.sourceKey == currentChannel?.sourceKey) {
                            R.string.system_info_tracks_not_reported
                        } else {
                            R.string.system_info_tracks_current_only
                        },
                    ),
                )
            } else if (channel.source == LiveChannel.Source.TIF) {
                tracks.forEachIndexed { index, track ->
                    val prefix = getString(R.string.system_info_track_index, index + 1)
                    add("$prefix ${getString(R.string.system_info_track_type)}" to trackTypeLabel(track))
                    add("$prefix ID" to track.id)
                    add("$prefix ${getString(R.string.system_info_language)}" to track.language.orUnknown())
                    add("$prefix ${getString(R.string.system_info_description)}" to track.description?.toString().orUnknown())
                    if (track.type == TvTrackInfo.TYPE_VIDEO) {
                        add("$prefix ${getString(R.string.system_info_resolution)}" to "${track.debugValue { videoWidth }} × ${track.debugValue { videoHeight }}")
                        add("$prefix FPS" to track.debugValue { videoFrameRate })
                        add("$prefix ${getString(R.string.system_info_pixel_ratio)}" to track.debugValue { videoPixelAspectRatio })
                        add("$prefix AFD" to track.debugValue { videoActiveFormatDescription })
                    }
                    if (track.type == TvTrackInfo.TYPE_AUDIO) {
                        add("$prefix ${getString(R.string.system_info_audio_channels)}" to track.debugValue { audioChannelCount })
                        add("$prefix ${getString(R.string.system_info_sample_rate)}" to track.debugValue { audioSampleRate })
                    }
                    val extraValues = track.debugExtraValues()
                    if (extraValues.isEmpty()) {
                        add("$prefix Extra" to track.extra?.toString().orUnknown())
                    } else {
                        extraValues.forEach { (key, value) ->
                            add("$prefix Extra / $key" to value)
                        }
                    }
                }
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
            rows.forEachIndexed { index, (key, value) -> addSystemInformationRow(key, value, index) }
        }
        val scroll = ScrollView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            addView(content)
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(getString(R.string.system_information_channel, channel.displayName))
            .setView(scroll)
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.setOnShowListener { scroll.requestFocus() }
        dialog.show()
    }

    private fun MutableList<Pair<String, String>>.addIptvHealthRows(
        health: IptvPlaybackHealthSnapshot,
    ) {
        fun duration(value: Long?): String = value?.let { "$it ms" }
            ?: getString(R.string.unknown_value)
        fun bitrate(value: Long?): String = value?.let { "${it / 1_000} kbps" }
            ?: getString(R.string.unknown_value)
        add(getString(R.string.system_info_iptv_phase) to health.phase.name)
        add(getString(R.string.system_info_iptv_content_kind) to health.contentKind.name)
        add(getString(R.string.system_info_iptv_playing) to yesNo(health.isPlaying))
        add(getString(R.string.system_info_iptv_first_frame) to yesNo(health.firstFrameRendered))
        add(getString(R.string.system_info_iptv_startup_time) to duration(health.startupDurationMillis))
        add(getString(R.string.system_info_iptv_last_frame_age) to duration(health.timeSinceLastFrameMillis))
        add(getString(R.string.system_info_iptv_progress_age) to duration(health.timeSinceLastProgressMillis))
        add(getString(R.string.system_info_resolution) to if (health.width != null && health.height != null) {
            "${health.width} × ${health.height}"
        } else {
            getString(R.string.unknown_value)
        })
        add(getString(R.string.system_info_iptv_video_codec) to health.videoCodec.orUnknown())
        add(getString(R.string.system_info_iptv_audio_codec) to health.audioCodec.orUnknown())
        add(getString(R.string.system_info_iptv_bitrate) to bitrate(health.bitrateBps?.toLong()))
        add(getString(R.string.system_info_iptv_bandwidth) to bitrate(health.estimatedBandwidthBps))
        add(getString(R.string.system_info_iptv_buffer) to duration(health.bufferedDurationMillis))
        add(getString(R.string.system_info_iptv_dropped_frames) to health.droppedFrames.toString())
        add(getString(R.string.system_info_iptv_dropped_frame_rate) to health.droppedFrameRate?.let { "%.2f".format(it) }.orUnknown())
        add(getString(R.string.system_info_iptv_frame_rate) to health.framesPerSecond?.let { "%.2f fps".format(it) }.orUnknown())
        add(getString(R.string.system_info_iptv_container) to health.containerFormat.orUnknown())
        add(getString(R.string.system_info_iptv_video_profile) to health.videoProfile.orUnknown())
        add(getString(R.string.system_info_iptv_audio_sample_rate) to health.audioSampleRateHz?.let { "$it Hz" }.orUnknown())
        add(getString(R.string.system_info_iptv_audio_channels) to health.audioChannelCount?.toString().orUnknown())
        add(getString(R.string.system_info_iptv_retry) to health.retryAttempt.toString())
        add(
            getString(R.string.system_info_iptv_failure_class) to
                (health.lastFailureClass?.name ?: getString(R.string.none)),
        )
        add(
            getString(R.string.system_info_iptv_last_error) to
                (health.lastErrorCode ?: getString(R.string.none)),
        )
    }

    private fun sanitizedIptvLocation(value: String): String = runCatching {
        val uri = android.net.Uri.parse(value)
        val scheme = uri.scheme?.takeIf(String::isNotBlank)
            ?: return@runCatching getString(R.string.system_info_private_value_hidden)
        val host = uri.host?.takeIf(String::isNotBlank)
        if (host == null) "$scheme://…" else "$scheme://$host/…"
    }.getOrDefault(getString(R.string.system_info_private_value_hidden))

    private fun LinearLayout.addSystemInformationRow(key: String, value: String, index: Int) {
        if (value.isEmpty()) {
            addView(TextView(this@MainActivity).apply {
                text = key
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(10), dp(14), dp(10), dp(8))
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ))
            return
        }
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(dp(10), dp(8), dp(10), dp(8))
            if (index % 2 == 0) setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.system_info_row))
            addView(TextView(this@MainActivity).apply {
                text = key
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent))
                textSize = 13f
                setPadding(0, 0, dp(12), 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.36f))
            addView(TextView(this@MainActivity).apply {
                text = value
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                textSize = 13f
                setTextIsSelectable(true)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.64f))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun trackTypeLabel(track: TvTrackInfo): String = when (track.type) {
        TvTrackInfo.TYPE_VIDEO -> getString(R.string.system_info_video)
        TvTrackInfo.TYPE_AUDIO -> getString(R.string.system_info_audio)
        TvTrackInfo.TYPE_SUBTITLE -> getString(R.string.system_info_subtitle)
        else -> getString(R.string.unknown_value)
    }

    private fun String?.orUnknown(): String = this?.takeIf(String::isNotBlank)
        ?: getString(R.string.unknown_value)

    private fun yesNo(value: Boolean): String = getString(if (value) R.string.yes else R.string.no)

    private fun openExternalPlayer(channel: LiveChannel) {
        val preference = ExternalPlayerPreferencesStore(this).load()
        lifecycleScope.launch {
            val resolved = withContext(Dispatchers.IO) { iptvRepository.withSourceHeaders(channel) }
            if (!ExternalPlayerLauncher.launch(this@MainActivity, resolved, preference)) {
                Toast.makeText(this@MainActivity, R.string.external_player_not_found, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showIptvVideoOptions() {
        val channel = currentChannel?.takeIf { it.source == LiveChannel.Source.IPTV } ?: return
        val actions = arrayOf(
            getString(R.string.iptv_quality),
            getString(R.string.iptv_aspect_ratio),
            getString(R.string.external_player),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.iptv_video_options)
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> showIptvVideoQuality()
                    1 -> showIptvAspectRatio()
                    2 -> openExternalPlayer(channel)
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showIptvVideoQuality() {
        val tracks = iptvPlayback.videoTracks()
        if (tracks.isEmpty()) {
            showEditorError(R.string.tracks_not_available)
            return
        }
        val labels = buildList {
            add(getString(R.string.iptv_quality_auto))
            addAll(tracks.map(::iptvVideoTrackLabel))
        }.toTypedArray()
        val selectedId = iptvPlayback.videoTrackOverrideId()
        val selectedIndex = tracks.indexOfFirst { it.id == selectedId }
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: 0
        AlertDialog.Builder(this)
            .setTitle(R.string.iptv_quality)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                iptvPlayback.selectVideoTrack(tracks.getOrNull(which - 1)?.id)
                dialog.dismiss()
                showIptvPlaybackControls(iptvPlaybackStateText())
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun iptvVideoTrackLabel(track: IptvTrackOption): String = buildList {
        if (track.width != null && track.height != null) add("${track.width}×${track.height}")
        track.bitrate?.let { bitrate ->
            add(String.format(Locale.getDefault(), "%.1f Mbps", bitrate / 1_000_000f))
        }
        track.label?.takeIf(String::isNotBlank)?.let(::add)
        if (isEmpty()) add(track.mimeType?.substringAfter('/') ?: track.id)
    }.joinToString(" · ")

    private fun showIptvAspectRatio() {
        val modes = IptvAspectMode.entries
        val labels = arrayOf(
            getString(R.string.iptv_aspect_fit),
            getString(R.string.iptv_aspect_fill),
            getString(R.string.iptv_aspect_zoom),
        )
        val selected = modes.indexOf(iptvViewPreferencesStore.aspectMode()).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.iptv_aspect_ratio)
            .setSingleChoiceItems(labels, selected) { dialog, which ->
                modes.getOrNull(which)?.let { mode ->
                    iptvViewPreferencesStore.setAspectMode(mode)
                    applyIptvAspectMode(mode)
                }
                dialog.dismiss()
                showIptvPlaybackControls(iptvPlaybackStateText())
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun applyIptvAspectMode(mode: IptvAspectMode) {
        binding.iptvPlayerView.resizeMode = when (mode) {
            IptvAspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            IptvAspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            IptvAspectMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }

    private fun updateChannelInPlace(
        original: LiveChannel,
        updated: LiveChannel,
        action: suspend () -> Unit,
    ) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { action() }
            channels = channels.map { if (it.sourceKey == original.sourceKey) updated else it }
            if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
                iptvLibraryChannels = iptvLibraryChannels.map {
                    if (it.sourceKey == original.sourceKey) updated.copy(displayNumber = it.displayNumber)
                    else it
                }
                adapter.submitList(iptvLibraryChannels)
                adapter.select(currentChannel?.sourceKey ?: updated.sourceKey)
                focusListChannel(updated.sourceKey)
            } else {
                applyChannelFilter(requestFocus = true)
            }
        }
    }

    private fun updateIptvMainListMembership(channel: LiveChannel, selected: Boolean) {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                iptvRepository.setChannelSelected(channel.sourceKey, selected)
                repository.channels().getOrDefault(channels)
            }
            channels = loaded
            currentChannel = loaded.firstOrNull { it.sourceKey == currentChannel?.sourceKey }
                ?: currentChannel
            if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
                iptvLibraryChannels = iptvLibraryChannels.map { item ->
                    if (item.sourceKey == channel.sourceKey) item.copy(inMainList = selected)
                    else item
                }
                adapter.submitList(iptvLibraryChannels)
                adapter.select(currentChannel?.sourceKey ?: channel.sourceKey)
                focusListChannel(channel.sourceKey)
            } else {
                applyChannelFilter(requestFocus = true)
            }
        }
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

    private fun updateInfobarChannel(channel: LiveChannel) {
        binding.nowChannel.text = channel.displayName
        binding.nowNumber.text = channel.displayNumber
        val sourceName = if (channel.source == LiveChannel.Source.IPTV) iptvSourceNames[channel.inputId] else null
        if (!sourceName.isNullOrBlank()) {
            binding.nowSourceName.text = sourceName
            binding.nowSourceName.visibility = View.VISIBLE
        } else {
            binding.nowSourceName.visibility = View.GONE
        }
    }

    private fun refreshIptvSourceNames() {
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { iptvRepository.sourceNames() }.onSuccess { names ->
                iptvSourceNames = names
                withContext(Dispatchers.Main) {
                    currentChannel?.let { ch ->
                        if (ch.source == LiveChannel.Source.IPTV) {
                            val name = iptvSourceNames[ch.inputId]
                            if (!name.isNullOrBlank()) {
                                binding.nowSourceName.text = name
                                binding.nowSourceName.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
    }

    private fun showInfoBarForChannel(channel: LiveChannel) {
        if (channel.isVodContent() || vodMode) {
            setInfoBarVisible(false)
            binding.infoBar.visibility = View.GONE
            showVodPlayerControls()
            return
        }
        updateInfobarChannel(channel)
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
        val input = EditText(ContextThemeWrapper(this, R.style.Theme_TVApp_Dialog)).apply {
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
        val input = EditText(ContextThemeWrapper(this, R.style.Theme_TVApp_Dialog)).apply {
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
        val dialog = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
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

    private fun showChannelSearchDialog() {
        focusedTuneJob?.cancel()
        val input = EditText(ContextThemeWrapper(this, R.style.Theme_TVApp_Dialog)).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.channel_search_hint)
            setText(channelSearchQuery)
            selectAll()
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_TVApp_Dialog)
            .setTitle(R.string.search_channels)
            .setView(input)
            .setPositiveButton(R.string.apply, null)
            .setNeutralButton(R.string.clear_search, null)
            .setNegativeButton(R.string.close, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                applyChannelSearch(input.text.toString())
                dialog.dismiss()
            }
            dialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                applyChannelSearch("")
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun applyChannelSearch(value: String) {
        alphabetFilterJob?.cancel()
        alphabetLetter = null
        iptvAlphabetInitials = null
        channelSearchQuery = value.trim()
        normalizedChannelSearchQuery = normalizeSearchText(channelSearchQuery)
        binding.channelSearchButton.isSelected = channelSearchQuery.isNotEmpty()
        if (channelPanelContent != ChannelPanelContent.IPTV_LIBRARY) {
            applyChannelFilter(requestFocus = true)
            return
        }
        val sourceId = iptvLibrarySourceId ?: return
        if (iptvLibraryContentType == IptvLibraryContentType.CONTINUE) {
            val generation = ++iptvLibraryGeneration
            val query = channelSearchQuery
            lifecycleScope.launch {
                val sourceInputId = "iptv:$sourceId"
                val filtered = withContext(Dispatchers.IO) {
                    iptvResumeStore.entries().mapNotNull { iptvRepository.channel(it.sourceKey) }
                        .filter { it.inputId == sourceInputId && it.iptvContentType == "VOD" }
                        .filter(::matchesChannelSearch)
                }.mapIndexed { index, channel -> channel.copy(displayNumber = (index + 1).toString()) }
                if (generation != iptvLibraryGeneration || query != channelSearchQuery) return@launch
                continuedAlphabetChannels = filtered
                iptvLibraryChannels = filtered
                iptvLibraryTotalCount = filtered.size
                adapter.submitList(filtered)
                updateIptvLibraryCount()
                focusIptvLibraryPosition(0)
            }
            return
        }
        iptvLibraryLoadJob?.cancel()
        val generation = ++iptvLibraryGeneration
        val query = channelSearchQuery
        iptvLibraryChannels = emptyList()
        iptvLibraryWindowStart = 0
        iptvLibraryFirstAnchor = null
        iptvLibraryLastAnchor = null
        lifecycleScope.launch {
            iptvLibraryTotalCount = withContext(Dispatchers.IO) {
                iptvRepository.libraryChannelCount(
                    sourceId,
                    iptvLibraryCategory,
                    iptvLibraryContentType.name,
                    query,
                )
            }
            if (generation != iptvLibraryGeneration || query != channelSearchQuery) return@launch
            updateIptvLibraryCount()
            loadIptvLibraryWindow(IptvPageDirection.FIRST)
        }
    }

    private fun matchesChannelSearch(channel: LiveChannel): Boolean {
        val query = normalizedChannelSearchQuery
        if (query.isBlank()) return true
        return listOf(channel.displayNumber, channel.displayName, channel.groupTitle.orEmpty())
            .any { normalizeSearchText(it).contains(query) }
    }

    private fun normalizeSearchText(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFD)
        .replace(SEARCH_COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)

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
        return normalPanelChannels
    }

    private fun buildNormalPanelChannels(includeAlphabet: Boolean = true): List<LiveChannel> {
        return channels.asSequence()
        .filterNot { it.isVodContent() }
        .filter { channel ->
            when (sourceFilter) {
                ChannelSourceFilter.ALL -> true
                ChannelSourceFilter.SATELLITE ->
                    channel.source == LiveChannel.Source.TIF && !channel.isRadioChannel()
                ChannelSourceFilter.RADIO -> channel.isRadioChannel()
                ChannelSourceFilter.IPTV -> channel.source == LiveChannel.Source.IPTV
                ChannelSourceFilter.FAVORITES -> channel.favorite
            }
        }
        .filter { channel -> !favoriteFilter || channel.favorite }
        .filter(::matchesChannelSearch)
        .filter { !includeAlphabet || alphabetLetter == null || com.tvapp.livetv.ui.AlphabetJump.letter(it.displayName) == alphabetLetter }
        .mapIndexed { index, channel -> channel.copy(displayNumber = (index + 1).toString()) }
        .toList()
    }

    private fun applyChannelFilter(
        showFavorites: Boolean = favoriteFilter,
        source: ChannelSourceFilter = sourceFilter,
        requestFocus: Boolean = true,
    ) {
        if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY || source != sourceFilter ||
            showFavorites != favoriteFilter || !displayPreferences.channelAlphabetNavigation
        ) alphabetLetter = null
        alphabetFilterJob?.cancel()
        iptvLibraryLoadJob?.cancel()
        iptvLibraryGeneration++
        iptvLibraryHasPrevious = false
        iptvLibraryHasNext = false
        iptvLibraryFirstAnchor = null
        iptvLibraryLastAnchor = null
        channelPanelContent = ChannelPanelContent.NORMAL
        adapter.showIptvMembership(false)
        favoriteFilter = showFavorites
        sourceFilter = source
        channelListFilterStore.save(sourceFilter, favoriteFilter)
        binding.allFilter.isSelected = sourceFilter == ChannelSourceFilter.ALL
        binding.satelliteFilter.isSelected = sourceFilter == ChannelSourceFilter.SATELLITE
        binding.radioFilter.isSelected = sourceFilter == ChannelSourceFilter.RADIO
        binding.iptvFilter.isSelected = sourceFilter == ChannelSourceFilter.IPTV
        binding.favoriteFilter.isSelected = showFavorites
        binding.sourceFilterRow.visibility = View.GONE
        binding.channelListTitle.text = listOfNotNull(getString(R.string.channel_list), alphabetLetter).joinToString(" · ")
        updateChannelListModeIcon()
        updateChannelActionLabels()
        normalPanelChannels = buildNormalPanelChannels()
        val filtered = normalPanelChannels
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
        adapter.showMultiViewSelection(if (multiViewAdding) multiViewDraft.keys.toList() else emptyList())
        val library = channelPanelContent == ChannelPanelContent.IPTV_LIBRARY
        val red = R.string.edit_short
        val green = R.string.iptv_pip_grid_short_long
        val yellow = R.string.channel_source_short
        val blue = if (library) R.string.filter_short else R.string.search_short
        binding.redActionLabel.setText(red)
        binding.greenActionLabel.text = if (multiViewAdding) getString(R.string.multiview_selection_count, multiViewDraft.size) else getString(green)
        binding.yellowActionLabel.setText(yellow)
        binding.blueActionLabel.setText(blue)
        updateMobileChannelActionState()
        updateInfoColorActions()
    }

    private fun updateInfoColorActions() {
        binding.infoColorActions.removeAllViews()
        if (!iptvControlsInteractive && catchUpReturnChannel == null) {
            binding.infoColorActions.addView(TextView(this).apply {
                text = getString(R.string.ok_long_mode_hint)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                textSize = 9f
                setPadding(dp(6), 0, dp(6), 0)
                setSingleLine(true)
                if (BuildConfig.MOBILE_UI_ENABLED) {
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { showPlaybackModeDialog() }
                }
            })
        }
        if (binding.iptvPlaybackContainer.visibility == View.VISIBLE) {
            catchUpReturnChannel?.let { liveChannel ->
                binding.infoColorActions.addView(
                    TvUiComponents.colorAction(
                        this,
                        R.drawable.key_red,
                        getString(R.string.catchup_return_live),
                        interactive = BuildConfig.MOBILE_UI_ENABLED,
                        clicked = {
                            catchUpReturnChannel = null
                            selectChannel(liveChannel)
                        },
                    ),
                )
            }
            val hints = if (iptvControlsInteractive) {
                intArrayOf(
                    R.string.iptv_controls_up_down_hint,
                    R.string.iptv_controls_left_right_hint,
                    R.string.iptv_controls_ok_hint,
                    R.string.iptv_controls_back_hint,
                )
            } else {
                intArrayOf(
                    if (vodMode) R.string.vod_library_key_hint else R.string.iptv_controls_up_down_channel_hint,
                    R.string.iptv_controls_media_hint,
                )
            }
            hints.forEach { label ->
                binding.infoColorActions.addView(TextView(this).apply {
                    setText(label)
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    textSize = 9f
                    setPadding(dp(10), 0, 0, 0)
                    setSingleLine(true)
                })
            }
            return
        }
        fun action(color: Int, label: Int, handler: () -> Unit) {
            if (BuildConfig.MOBILE_UI_ENABLED) {
                binding.infoColorActions.addView(
                    TvUiComponents.colorAction(
                        this,
                        colorKeyDrawable(color),
                        getString(label),
                        interactive = true,
                        clicked = handler,
                    ),
                )
                return
            }
            if (binding.infoColorActions.childCount > 0) {
                binding.infoColorActions.addView(TextView(this).apply {
                    text = ","
                    setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                    textSize = 11f
                    setPadding(dp(7), 0, dp(7), 0)
                })
            }
            binding.infoColorActions.addView(
                TvUiComponents.colorAction(
                    this,
                    colorKeyDrawable(color),
                    getString(label),
                    interactive = false,
                ),
            )
        }
        if (vodMode) action(R.color.remote_green, R.string.vod_home_title, ::openVodLibrary)
        else action(R.color.remote_green, R.string.iptv_pip_grid_short_long, ::showIptvGridPicker)
        action(R.color.remote_blue, R.string.settings_short, ::openDisplaySettings)
        // TXT hint inside the technical row, far right, next to the badges.
        binding.infoColorActions.addView(TextView(this).apply {
            text = getString(R.string.txt_key_hint)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            textSize = 9f
            setPadding(dp(10), 0, 0, 0)
            setSingleLine(true)
        })
    }

    private fun updateIptvControlHints() = updateInfoColorActions()

    private fun colorKeyDrawable(color: Int): Int = when (color) {
        R.color.remote_red -> R.drawable.key_red
        R.color.remote_green -> R.drawable.key_green
        R.color.remote_yellow -> R.drawable.key_yellow
        else -> R.drawable.key_blue
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun renderPlaybackUiState(state: PlaybackUiState) {
        channelPanelExpanded = state.channelPanelExpanded
        iptvControlsInteractive = state.iptvControlsInteractive
        binding.channelPanel.visibility = if (state.primaryOsd == PrimaryOsd.CHANNEL_PANEL) {
            View.VISIBLE
        } else {
            View.GONE
        }
        val vodActive = isVodPlaybackActive()
        binding.infoBar.visibility = if (state.infoBarVisible && !vodActive) View.VISIBLE else View.GONE
        binding.iptvPlaybackContainer.visibility = if (
            state.infoBarVisible && state.iptvChromeVisible && !vodActive
        ) View.VISIBLE else View.GONE
        if (!vodActive && binding.vodPlayerOverlay.visibility == View.VISIBLE) {
            binding.vodPlayerOverlay.visibility = View.GONE
            vodPlayerOverlayController?.hideControls()
        }
        binding.recentChannelsPanel.visibility = if (
            state.primaryOsd == PrimaryOsd.RECENT_CHANNELS
        ) View.VISIBLE else View.GONE
        binding.parentalLockPanel.visibility = if (
            state.primaryOsd == PrimaryOsd.PARENTAL_LOCK
        ) View.VISIBLE else View.GONE
        binding.statusPanel.visibility = if (state.primaryOsd == PrimaryOsd.STATUS) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.iptvGrid.visibility = if (state.playbackMode == PlaybackSurfaceMode.IPTV_GRID) {
            View.VISIBLE
        } else {
            View.GONE
        }
        binding.infoColorActions.visibility = if (
            state.infoBarVisible && state.primaryOsd != PrimaryOsd.CHANNEL_PANEL
        ) View.VISIBLE else View.GONE
        updateIptvActionBarState()
    }

    private fun setInfoBarVisible(visible: Boolean) {
        if (visible && isVodPlaybackActive()) {
            osdCoordinator.hideInfoBar()
            binding.infoBar.visibility = View.GONE
            binding.iptvPlaybackContainer.visibility = View.GONE
            showVodPlayerControls()
            return
        }
        if (!visible && iptvControlsInteractive) {
            iptvControlsInteractive = false
            iptvControlRow = IptvControlRow.TIMELINE
            renderIptvControlSelection()
            updateIptvControlHints()
        }
        if (!visible) iptvInfoTickerJob?.cancel()
        if (visible) {
            vodPlayerOverlayController?.hideControls()
            binding.vodPlayerOverlay.visibility = View.GONE
            osdCoordinator.showInfoBar(
                iptvChrome = currentChannel?.source == LiveChannel.Source.IPTV,
                interactive = iptvControlsInteractive,
            )
        } else {
            osdCoordinator.hideInfoBar()
        }
    }

    private fun updateChannelListModeIcon() {
        val (icon, description) = when {
            channelPanelContent == ChannelPanelContent.IPTV_LIBRARY ->
                R.drawable.ic_iptv_library to R.string.iptv_library
            sourceFilter == ChannelSourceFilter.FAVORITES ->
                R.drawable.ic_star to R.string.favorite_channels
            sourceFilter == ChannelSourceFilter.SATELLITE ->
                R.drawable.ic_source_tif to R.string.satellite_channels
            sourceFilter == ChannelSourceFilter.RADIO ->
                R.drawable.ic_radio to R.string.radio_channels
            sourceFilter == ChannelSourceFilter.IPTV ->
                R.drawable.ic_source_iptv to R.string.iptv_filter
            else -> R.drawable.ic_list to R.string.all_channels
        }
        binding.channelListModeIcon.setImageResource(icon)
        binding.channelListModeIcon.contentDescription = getString(description)
    }

    private fun toggleChannelPanel() {
        if (binding.channelPanel.visibility == View.VISIBLE) hideChannelPanel()
        else openMobileChannelPanel()
    }

    private fun showChannelPanel(expanded: Boolean) {
        if (vodMode) {
            openVodLibrary()
            return
        }
        android.os.Trace.beginSection("channel_panel_open")
        val openedAt = SystemClock.elapsedRealtime()
        try {
            if (binding.channelPanel.visibility != View.VISIBLE) {
                focusedAutoTunePreviousChannel = null
                focusedAutoTuneTargetKey = null
            }
            channelPanelExpanded = expanded
            osdCoordinator.showChannelPanel(
                expanded = expanded,
                iptvChrome = currentChannel?.source == LiveChannel.Source.IPTV,
            )
            // Keep in-row program progress current while the list is open.
            if (expanded) adapter.startProgramTicker(lifecycleScope)
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
            binding.channelList.post {
                loadVisiblePrograms()
                recordPerformance(
                    "channel_panel_open",
                    openedAt,
                    "count=${adapter.itemCount}, content=$channelPanelContent",
                )
            }
            scheduleChannelPanelClose()
        } finally {
            android.os.Trace.endSection()
        }
    }

    private fun hideChannelPanel() {
        if (multiViewAdding) {
            channelZapper.clearNumberInput()
        }
        multiViewAdding = false
        adapter.showMultiViewSelection(emptyList())
        alphabetLoadJob?.cancel()
        alphabetRightJob?.cancel()
        alphabetRail?.close()
        focusedTuneJob?.cancel()
        channelPanelJob?.cancel()
        adapter.stopProgramTicker()
        focusedAutoTunePreviousChannel = null
        focusedAutoTuneTargetKey = null
        channelPanelExpanded = false
        osdCoordinator.hideChannelPanel(keepInfoBar = currentChannel != null)
        binding.advancedFilterRow.visibility = View.GONE
        applyPanelGeometry()
        if (!isVodPlaybackActive()) {
            currentChannel?.let { showInfoBar() }
        }
    }

    private fun scheduleChannelPanelClose() {
        if (multiViewAdding) return
        channelPanelJob?.cancel()
        channelPanelJob = lifecycleScope.launch {
            delay(CHANNEL_PANEL_TIMEOUT_MS)
            if (binding.channelPanel.visibility == View.VISIBLE) hideChannelPanel()
        }
    }

    private fun setupTvOverlayLayoutHelper() {
        tvOverlayLayoutHelper = TvOverlayLayoutHelper(
            context = this,
            binding = binding,
            getDisplayPreferences = { displayPreferences },
            isChannelPanelExpanded = { channelPanelExpanded },
            showsVodTimeline = { showsVodTimeline() },
            dp = { dp(it) },
        )
    }

    private fun applyPanelGeometry() = tvOverlayLayoutHelper.applyPanelGeometry()

    private fun currentInfoBarHeight(screenHeight: Int): Int =
        tvOverlayLayoutHelper.currentInfoBarHeight(screenHeight)

    private fun updateInfoBarHeight() = tvOverlayLayoutHelper.updateInfoBarHeight()

    private fun startClock() {
        clockJob?.cancel()
        clockJob = lifecycleScope.launch {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEE, d MMMM yyyy", Locale("tr"))
            while (true) {
                val now = Date()
                binding.clockTime.text = timeFormat.format(now)
                binding.clockDate.text = dateFormat.format(now).uppercase(Locale("tr"))
                delay(CLOCK_REFRESH_MS)
            }
        }
    }

    private fun formatBitrate(bps: Number?): String {
        val value = bps?.toLong() ?: return "-"
        if (value <= 0) return "-"
        return if (value >= 1_000_000) {
            String.format(Locale.US, "%.1f Mbps", value / 1_000_000f)
        } else {
            String.format(Locale.US, "%d kbps", value / 1000)
        }
    }

    private fun updateDiagnosticsOverlay() {
        if (!displayPreferences.showDiagnosticsOverlay) {
            binding.diagnosticsOverlay.visibility = View.GONE
            diagnosticsJob?.cancel()
            diagnosticsJob = null
            return
        }
        val channel = currentChannel
        if (channel == null) {
            binding.diagnosticsOverlay.visibility = View.GONE
            return
        }
        if (channel.source == LiveChannel.Source.IPTV) {
            binding.diagResFpsLabel.setText(R.string.diagnostics_res_fps)
            binding.diagCodecLabel.setText(R.string.diagnostics_codec)
            binding.diagBitrateLabel.setText(R.string.diagnostics_bitrate)
            binding.diagBandwidthLabel.setText(R.string.diagnostics_bandwidth)
            binding.diagBufferLabel.setText(R.string.diagnostics_buffer)
            binding.diagDroppedLabel.setText(R.string.diagnostics_dropped)
            binding.diagAudioLabel.setText(R.string.diagnostics_audio)
            val health = iptvPlayback.healthSnapshot()
            val width = health.width ?: 0
            val height = health.height ?: 0
            val fps = health.framesPerSecond ?: 0f
            val resText = if (width > 0 && height > 0) {
                if (fps > 0f) "${width}x${height} @ ${String.format(Locale.US, "%.1f", fps)}fps"
                else "${width}x${height}"
            } else {
                "-"
            }
            binding.diagResFps.text = resText
            binding.diagCodec.text = health.videoCodec?.substringAfterLast('/')?.take(16) ?: "-"
            binding.diagBitrate.text = formatBitrate(health.bitrateBps)
            binding.diagBandwidth.text = formatBitrate(health.estimatedBandwidthBps)
            binding.diagBuffer.text = health.bufferedDurationMillis?.let {
                String.format(Locale.US, "%.1f s", it / 1000.0)
            } ?: "-"
            binding.diagDropped.text = health.droppedFrames?.toString() ?: "-"
            val audioInfo = health.audioCodec?.substringAfterLast('/')?.take(10)
            val channelCount = health.audioChannelCount?.takeIf { it > 0 }
            binding.diagAudio.text = listOfNotNull(
                audioInfo,
                channelCount?.let { "${it}ch" },
            ).joinToString(" / ").ifBlank { "-" }
            binding.diagnosticsOverlay.visibility = View.VISIBLE
        } else if (channel.source == LiveChannel.Source.TIF) {
            val tracks = playback.allTracks()
            val videoTrack = tracks.firstOrNull { it.type == TvTrackInfo.TYPE_VIDEO }
            val audioTrack = tracks.firstOrNull { it.type == TvTrackInfo.TYPE_AUDIO }
            val videoState = playback.currentVideoState()
            val width = if (videoState.width > 0) videoState.width else videoTrack?.videoWidth ?: 0
            val height = if (videoState.height > 0) videoState.height else videoTrack?.videoHeight ?: 0
            val fps = videoTrack?.videoFrameRate?.takeIf { it > 0f } ?: 0f
            val resText = if (width > 0 && height > 0) {
                val quality = VideoQuality.resolutionLabel(
                    width,
                    height,
                    channel.videoFormat.orEmpty().uppercase(Locale.ROOT),
                )
                val dimensions = "${width}x${height}"
                val resolution = quality?.let { "$dimensions ($it)" } ?: dimensions
                if (fps > 0f) "$resolution @ ${String.format(Locale.US, "%.1f", fps)}fps"
                else resolution
            } else {
                channel.videoFormat?.takeIf { it.isNotBlank() } ?: "-"
            }
            binding.diagResFpsLabel.setText(R.string.diagnostics_res_fps)
            binding.diagResFps.text = resText

            binding.diagCodecLabel.setText(R.string.diagnostics_tif_input)
            binding.diagCodec.text = channel.inputId.substringAfterLast('/').ifBlank { channel.inputId }

            binding.diagBitrateLabel.setText(R.string.diagnostics_tif_video_state)
            binding.diagBitrate.text = when (videoState.available) {
                true -> getString(R.string.diagnostics_tif_video_available)
                false -> getString(
                    R.string.diagnostics_tif_video_unavailable,
                    videoState.unavailableReason?.toString() ?: "-",
                )
                null -> "-"
            }

            binding.diagBandwidthLabel.setText(R.string.diagnostics_tif_tracks)
            binding.diagBandwidth.text = getString(
                R.string.diagnostics_tif_track_counts,
                tracks.count { it.type == TvTrackInfo.TYPE_VIDEO },
                tracks.count { it.type == TvTrackInfo.TYPE_AUDIO },
                tracks.count { it.type == TvTrackInfo.TYPE_SUBTITLE },
            )

            binding.diagBufferLabel.setText(R.string.diagnostics_tif_audio_rate)
            binding.diagBuffer.text = audioTrack?.audioSampleRate
                ?.takeIf { it > 0 }
                ?.let { "$it Hz" }
                ?: "-"

            binding.diagDroppedLabel.setText(R.string.diagnostics_tif_pixel_aspect)
            binding.diagDropped.text = videoTrack?.videoPixelAspectRatio
                ?.takeIf { it > 0f }
                ?.let { String.format(Locale.US, "%.3f", it) }
                ?: "-"

            binding.diagAudioLabel.setText(R.string.diagnostics_audio)
            val audioDescription = audioTrack?.description?.toString()?.takeIf { it.isNotBlank() }
                ?: audioTrack?.language
            val audioChannelCount = audioTrack?.audioChannelCount?.takeIf { it > 0 }
            binding.diagAudio.text = listOfNotNull(
                audioDescription?.take(16),
                audioChannelCount?.let { "${it}ch" },
            ).joinToString(" / ").ifBlank { "-" }
            binding.diagnosticsOverlay.visibility = View.VISIBLE
        } else {
            binding.diagnosticsOverlay.visibility = View.GONE
        }
    }

    private fun startDiagnosticsObservation() {
        diagnosticsJob?.cancel()
        if (!displayPreferences.showDiagnosticsOverlay) {
            binding.diagnosticsOverlay.visibility = View.GONE
            return
        }
        diagnosticsJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    updateDiagnosticsOverlay()
                    delay(1000L)
                }
            }
        }
    }

    private fun focusCurrentListChannel() {
        focusListChannel(currentChannel?.sourceKey)
    }

    private fun focusListChannel(sourceKey: String?) {
        val visibleChannels = panelChannels()
        if (visibleChannels.isEmpty()) {
            when {
                favoriteFilter -> binding.favoriteFilter.requestFocus()
                sourceFilter == ChannelSourceFilter.SATELLITE -> binding.satelliteFilter.requestFocus()
                sourceFilter == ChannelSourceFilter.RADIO -> binding.radioFilter.requestFocus()
                sourceFilter == ChannelSourceFilter.IPTV -> binding.iptvFilter.requestFocus()
                else -> binding.allFilter.requestFocus()
            }
            return
        }
        val index = visibleChannels.indexOfFirst { it.sourceKey == sourceKey }
            .takeIf { it >= 0 } ?: 0
        binding.channelList.scrollToPosition(index)
        binding.channelList.post {
            binding.channelList.findViewHolderForAdapterPosition(index)?.itemView?.requestFocus()
        }
    }

    private fun cycleChannelListMode() {
        focusedTuneJob?.cancel()
        if (experienceMode != ExperienceMode.HYBRID_TV) {
            when {
                channelPanelContent == ChannelPanelContent.IPTV_LIBRARY -> applyChannelFilter(
                    showFavorites = false,
                    source = ChannelSourceFilter.ALL,
                )
                sourceFilter == ChannelSourceFilter.ALL -> applyChannelFilter(
                    showFavorites = false,
                    source = ChannelSourceFilter.FAVORITES,
                )
                sourceFilter == ChannelSourceFilter.FAVORITES -> openSavedIptvLibrary()
                else -> applyChannelFilter(showFavorites = false, source = ChannelSourceFilter.ALL)
            }
            return
        }
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
                source = ChannelSourceFilter.RADIO,
            )
            sourceFilter == ChannelSourceFilter.RADIO -> applyChannelFilter(
                showFavorites = false,
                source = ChannelSourceFilter.IPTV,
            )
            sourceFilter == ChannelSourceFilter.IPTV -> applyChannelFilter(
                showFavorites = false,
                source = ChannelSourceFilter.FAVORITES,
            )
            else -> openSavedIptvLibrary()
        }
    }

    private fun showChannelListModeDialog() {
        val options = buildList {
            add(
                ChannelListModeOption(
                    label = getString(R.string.all_channels),
                    selected = channelPanelContent == ChannelPanelContent.NORMAL &&
                        sourceFilter == ChannelSourceFilter.ALL,
                    apply = { applyChannelFilter(false, ChannelSourceFilter.ALL) },
                ),
            )
            if (experienceMode == ExperienceMode.HYBRID_TV) {
                add(
                    ChannelListModeOption(
                        getString(R.string.satellite_channels),
                        channelPanelContent == ChannelPanelContent.NORMAL &&
                            sourceFilter == ChannelSourceFilter.SATELLITE,
                    ) { applyChannelFilter(false, ChannelSourceFilter.SATELLITE) },
                )
                add(
                    ChannelListModeOption(
                        getString(R.string.radio_channels),
                        channelPanelContent == ChannelPanelContent.NORMAL &&
                            sourceFilter == ChannelSourceFilter.RADIO,
                    ) { applyChannelFilter(false, ChannelSourceFilter.RADIO) },
                )
                add(
                    ChannelListModeOption(
                        getString(R.string.iptv_filter),
                        channelPanelContent == ChannelPanelContent.NORMAL &&
                            sourceFilter == ChannelSourceFilter.IPTV,
                    ) { applyChannelFilter(false, ChannelSourceFilter.IPTV) },
                )
            }
            add(
                ChannelListModeOption(
                    getString(R.string.favorite_channels),
                    channelPanelContent == ChannelPanelContent.NORMAL &&
                        sourceFilter == ChannelSourceFilter.FAVORITES,
                    apply = { applyChannelFilter(false, ChannelSourceFilter.FAVORITES) },
                ),
            )
            add(
                ChannelListModeOption(
                    getString(R.string.iptv_library),
                    channelPanelContent == ChannelPanelContent.IPTV_LIBRARY,
                    ::openSavedIptvLibrary,
                ),
            )
        }
        val labels = options.map(ChannelListModeOption::label).toTypedArray()
        val checked = options.indexOfFirst(ChannelListModeOption::selected).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_channel_source)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                focusedTuneJob?.cancel()
                options[which].apply()
                dialog.dismiss()
                if (binding.channelPanel.visibility != View.VISIBLE) showChannelPanel(false)
                scheduleChannelPanelClose()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun recordPerformance(operation: String, startedAt: Long, detail: String) {
        if (!BuildConfig.DIAGNOSTICS_ENABLED && !BuildConfig.DEBUG) return
        debugLog.recordDebug(
            "PERF | operation=$operation, durationMs=${SystemClock.elapsedRealtime() - startedAt}, $detail",
        )
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
        val rawTarget = currentIndex + direction * pageSize
        if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
            if (direction < 0 && rawTarget < 0 && iptvLibraryHasPrevious) {
                focusedTuneJob?.cancel()
                loadIptvLibraryWindow(IptvPageDirection.PREVIOUS)
                return
            }
            if (direction > 0 && rawTarget >= adapter.itemCount && iptvLibraryHasNext) {
                focusedTuneJob?.cancel()
                loadIptvLibraryWindow(IptvPageDirection.NEXT)
                return
            }
        }
        val targetIndex = rawTarget.coerceIn(0, adapter.itemCount - 1)
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
                if (iptvLibraryHasPrevious) {
                    loadIptvLibraryWindow(IptvPageDirection.PREVIOUS)
                } else {
                    showIptvLibraryBoundary(last = true)
                }
                return true
            }
            if (direction > 0 && currentIndex == visible.lastIndex) {
                if (iptvLibraryHasNext) {
                    loadIptvLibraryWindow(IptvPageDirection.NEXT)
                } else {
                    showIptvLibraryBoundary(last = false)
                }
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

    private val remoteActionRouter = RemoteActionRouter()

    private fun handleBackNavigation() {
        when (remoteActionRouter.route(remoteUiContext(), RemoteKey.BACK, RemoteKeyPhase.DOWN)) {
            RemoteAction.DISMISS_RECENT_CHANNELS -> hideRecentChannels()
            RemoteAction.HANDLE_IPTV_CONTROLS -> hideIptvPlaybackControls()
            RemoteAction.HANDLE_GRID -> if (gridFullscreenIndex != null) {
                closeActiveGridChannelFullscreen()
            } else {
                stopIptvGrid(resumePrevious = true)
            }
            RemoteAction.DISMISS_STATUS -> dismissStatusDialog()
            RemoteAction.HANDLE_CHANNEL_PANEL -> if (channelPanelExpanded) {
                showChannelPanel(expanded = false)
            } else {
                hideChannelPanel()
            }
            RemoteAction.HIDE_INFO_BAR -> {
                infoBarJob?.cancel()
                setInfoBarVisible(false)
            }
            RemoteAction.SHOW_RECENT_CHANNELS -> when (osdCoordinator.state.playbackMode) {
                PlaybackSurfaceMode.IPTV_OVERLAY -> stopIptvOverlay()
                PlaybackSurfaceMode.INTERNAL_MINI_PLAYER -> toggleInternalMiniPlayer()
                else -> showRecentChannels()
            }
            RemoteAction.OPEN_VOD_LIBRARY -> openVodLibrary()
            RemoteAction.HANDLE_PARENTAL_LOCK -> showRecentChannels()
            else -> Unit
        }
    }

    private fun remoteUiContext(): RemoteUiContext = RemoteUiContext(
        playbackUiState = osdCoordinator.state,
        dialogOwnsInput = currentFocus?.rootView != null &&
            currentFocus?.rootView !== binding.root.rootView,
        isIptv = currentChannel?.source == LiveChannel.Source.IPTV,
        isVod = vodMode,
    )

    private fun showRecentChannels() {
        val recent = playbackHistory.keys().asSequence()
            .filter { it != currentChannel?.sourceKey }
            .mapNotNull { key -> channels.firstOrNull { it.sourceKey == key } }
            .filterNot { it.isVodContent() }
            .take(5)
            .toList()
        if (recent.isEmpty()) return
        binding.recentChannelsContainer.removeAllViews()
        val inflater = layoutInflater
        recent.forEach { channel ->
            val card = inflater.inflate(
                R.layout.item_recent_channel_card,
                binding.recentChannelsContainer,
                false,
            )
            card.findViewById<TextView>(R.id.recent_channel_number).text = channel.displayNumber
            card.findViewById<ImageView>(R.id.recent_channel_source_icon).setImageResource(
                if (channel.source == LiveChannel.Source.IPTV) R.drawable.ic_source_iptv
                else R.drawable.ic_source_tif,
            )
            card.findViewById<TextView>(R.id.recent_channel_name).text = channel.displayName
            val program = currentPrograms[channel.sourceKey]
            val programView = card.findViewById<TextView>(R.id.recent_channel_program)
            if (program != null) {
                programView.text = program.title
                programView.visibility = View.VISIBLE
            } else {
                programView.visibility = View.GONE
            }
            card.setOnClickListener {
                hideRecentChannels()
                selectChannel(channel)
            }
            binding.recentChannelsContainer.addView(card)
        }
        osdCoordinator.showRecentChannels()
        binding.recentChannelsContainer.getChildAt(0)?.requestFocus()
    }

    private fun hideRecentChannels() {
        osdCoordinator.hideRecentChannels()
        binding.recentChannelsContainer.removeAllViews()
    }

    override fun dispatchKeyEvent(rawEvent: KeyEvent): Boolean {
        val event = rawEvent.asTvRemoteEvent()
        if (
            BuildConfig.DEBUG &&
            displayPreferences.verboseRemoteKeyLogging &&
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            ::debugLog.isInitialized
        ) {
            debugLog.recordDebug(
                "REMOTE_KEY | name=${KeyEvent.keyCodeToString(event.keyCode)}, " +
                    "code=${event.keyCode}, scan=${event.scanCode}, repeat=${event.repeatCount}, device=${event.deviceId}",
            )
        }
        val isVodActive = isVodPlaybackActive()

        if (isVodActive) {
            if (vodPlayerOverlayController?.handleDispatchKeyEvent(event) == true) {
                return true
            }
        }

        val routedAction = remoteActionRouter.route(
            remoteUiContext(),
            event.toRemoteKey(),
            event.toRemoteKeyPhase(),
        )
        if (routedAction == RemoteAction.FORWARD_TO_DIALOG) {
            return super.dispatchKeyEvent(event)
        }
        if (routedAction == RemoteAction.IGNORE_CHANNEL_NAVIGATION) return true
        if (routedAction == RemoteAction.OPEN_VOD_LIBRARY) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) openVodLibrary()
            return true
        }
        if (multiViewAdding && binding.channelPanel.visibility == View.VISIBLE &&
            event.keyCode == KeyEvent.KEYCODE_BACK
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) showIptvGridPicker(multiViewDraft.values.toList())
            return true
        }
        val isSettingsKey = event.keyCode in setOf(
            KeyEvent.KEYCODE_SETTINGS,
            KeyEvent.KEYCODE_TV_CONTENTS_MENU,
            312, // KEYCODE_QUICK_SETTINGS fallback on some Android TV builds
        )
        if (isSettingsKey) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                settingsLongPressHandled = false
                settingsLongPressJob?.cancel()
                settingsLongPressJob = lifecycleScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    settingsLongPressHandled = true
                    if (::debugLog.isInitialized) {
                        debugLog.recordDebug(
                            "SETTINGS_KEY | long_press -> openSystemSettings, code=${event.keyCode}, name=${KeyEvent.keyCodeToString(event.keyCode)}",
                        )
                    }
                    openSystemSettings()
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                settingsLongPressJob?.cancel()
                if (!settingsLongPressHandled) {
                    if (::debugLog.isInitialized) {
                        debugLog.recordDebug(
                            "SETTINGS_KEY | short_press -> openDisplaySettings, code=${event.keyCode}, name=${KeyEvent.keyCodeToString(event.keyCode)}",
                        )
                    }
                    openDisplaySettings()
                }
                settingsLongPressHandled = false
            }
            return true
        }
        if (
            event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode == KeyEvent.KEYCODE_PROG_RED &&
            binding.channelPanel.visibility != View.VISIBLE &&
            !iptvGridActive
        ) {
            catchUpReturnChannel?.let { liveChannel ->
                catchUpReturnChannel = null
                selectChannel(liveChannel)
                return true
            }
        }
        if (routedAction == RemoteAction.HANDLE_GRID) {
            multiViewController.handleGridKeyEvent(event)
            return true
        }
        if (routedAction == RemoteAction.DISMISS_STATUS ||
            osdCoordinator.state.primaryOsd == PrimaryOsd.STATUS
        ) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    dismissStatusDialog()
                }
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (routedAction == RemoteAction.DISMISS_RECENT_CHANNELS ||
            osdCoordinator.state.primaryOsd == PrimaryOsd.RECENT_CHANNELS
        ) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_DOWN) {
                hideRecentChannels()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.keyCode == KeyEvent.KEYCODE_LAST_CHANNEL) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                lastChannelLongPressHandled = false
                lastChannelLongPressJob?.cancel()
                lastChannelLongPressJob = lifecycleScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    lastChannelLongPressHandled = true
                    showRecentChannels()
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                lastChannelLongPressJob?.cancel()
                if (!lastChannelLongPressHandled) openPreviousChannel()
                lastChannelLongPressHandled = false
            }
            return true
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
        val isIptv = currentChannel?.source == LiveChannel.Source.IPTV
        val isChannelPanelClosed = binding.channelPanel.visibility != View.VISIBLE
        val hasNoBlockingPlaybackOsd = isChannelPanelClosed &&
            binding.statusPanel.visibility != View.VISIBLE &&
            binding.recentChannelsPanel.visibility != View.VISIBLE &&
            binding.parentalLockPanel.visibility != View.VISIBLE &&
            numberInput.isEmpty()

        val isOkKey = event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
        if (isOkKey && hasNoBlockingPlaybackOsd && !vodMode && currentIptvContentKind != IptvContentKind.VOD && !iptvControlsInteractive) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                okLongPressHandled = false
                okLongPressJob?.cancel()
                okLongPressJob = lifecycleScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    okLongPressHandled = true
                    showQuickHubDialog()
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                okLongPressJob?.cancel()
                if (!okLongPressHandled) {
                    toggleChannelPanel()
                }
                okLongPressHandled = false
            }
            return true
        }

        if (
            event.action == KeyEvent.ACTION_DOWN &&
            event.repeatCount == 0 &&
            isIptv &&
            (vodMode || currentIptvContentKind == IptvContentKind.VOD) &&
            hasNoBlockingPlaybackOsd && !iptvControlsInteractive &&
            event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
        ) {
            iptvControlRow = IptvControlRow.TIMELINE
            showIptvPlaybackControls(activateControls = true)
            val offset = if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                -IPTV_VOD_SEEK_STEP_MS
            } else {
                IPTV_VOD_SEEK_STEP_MS
            }
            iptvPlayback.seekBy(offset)
            updateIptvPlaybackControls()
            renderIptvControlSelection()
            debugLog.recordDebug(
                "IPTV_VOD_QUICK_SEEK | direction=${if (offset < 0) "back" else "forward"}, " +
                    "offsetMs=$offset, channel=${currentChannel?.sourceKey}",
            )
            return true
        }

        if (
            event.action == KeyEvent.ACTION_DOWN &&
            isIptv &&
            isChannelPanelClosed &&
            iptvControlsInteractive &&
            binding.iptvPlaybackContainer.visibility == View.VISIBLE
        ) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_PROG_RED -> {
                    val liveChannel = catchUpReturnChannel
                    if (liveChannel != null) {
                        catchUpReturnChannel = null
                        selectChannel(liveChannel)
                    } else if (iptvPlaybackFailed) {
                        iptvPlayback.retry()
                    } else if (currentIptvContentKind == IptvContentKind.LIVE) {
                        iptvManualTimeshift = false
                        iptvPlayback.goLive()
                    } else {
                        iptvPlayback.restartVod()
                    }
                    showIptvPlaybackControls()
                    return true
                }
                KeyEvent.KEYCODE_PROG_GREEN -> {
                    showAudioTracks()
                    return true
                }
                KeyEvent.KEYCODE_PROG_YELLOW -> {
                    showSubtitleTracks()
                    return true
                }
                KeyEvent.KEYCODE_PROG_BLUE -> {
                    showIptvVideoOptions()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    iptvControlRow = if (showsVodTimeline()) IptvControlRow.TIMELINE else IptvControlRow.BUTTONS
                    renderIptvControlSelection()
                    showIptvPlaybackControls()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    iptvControlRow = IptvControlRow.BUTTONS
                    renderIptvControlSelection()
                    showIptvPlaybackControls()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (iptvControlRow == IptvControlRow.TIMELINE) {
                        val offset = -IPTV_VOD_SEEK_STEP_MS
                        if (iptvPlayback.seekBy(offset) && currentIptvContentKind == IptvContentKind.LIVE) {
                            iptvManualTimeshift = true
                        }
                    } else {
                        cycleIptvButton(-1)
                    }
                    showIptvPlaybackControls()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (iptvControlRow == IptvControlRow.TIMELINE) {
                        val offset = IPTV_VOD_SEEK_STEP_MS
                        if (iptvPlayback.seekBy(offset) && currentIptvContentKind == IptvContentKind.LIVE) {
                            iptvManualTimeshift = true
                        }
                    } else {
                        cycleIptvButton(1)
                    }
                    showIptvPlaybackControls()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (iptvControlRow == IptvControlRow.TIMELINE) {
                        iptvControlRow = IptvControlRow.BUTTONS
                    } else {
                        activateSelectedIptvButton()
                    }
                    showIptvPlaybackControls()
                    return true
                }
                KeyEvent.KEYCODE_CHANNEL_UP -> {
                    zap(1)
                    return true
                }
                KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    zap(-1)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    hideIptvPlaybackControls()
                    return true
                }
            }
        }
        val greenOwnedByIptvControls = isIptv &&
            isChannelPanelClosed &&
            iptvControlsInteractive &&
            binding.iptvPlaybackContainer.visibility == View.VISIBLE
        if (event.keyCode == KeyEvent.KEYCODE_PROG_GREEN && !greenOwnedByIptvControls) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                greenLongPressHandled = false
                greenLongPressJob?.cancel()
                focusedTuneJob?.cancel()
                greenLongPressJob = lifecycleScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    greenLongPressHandled = true
                    showIptvGridPicker()
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                greenLongPressJob?.cancel()
                if (!greenLongPressHandled) {
                    focusedTuneJob?.cancel()
                    showIptvGridPicker()
                }
                greenLongPressHandled = false
            }
            return true
        }
        if (
            event.keyCode == KeyEvent.KEYCODE_PROG_YELLOW &&
            binding.channelPanel.visibility == View.VISIBLE
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                yellowLongPressHandled = false
                yellowLongPressJob?.cancel()
                yellowLongPressJob = lifecycleScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    yellowLongPressHandled = true
                    showChannelListModeDialog()
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                yellowLongPressJob?.cancel()
                if (!yellowLongPressHandled) cycleChannelListMode()
                yellowLongPressHandled = false
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT &&
            displayPreferences.channelAlphabetNavigation &&
            binding.channelPanel.visibility == View.VISIBLE && binding.channelList.hasFocus()
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                alphabetRightHandled = false
                alphabetRightJob?.cancel()
                alphabetRightJob = lifecycleScope.launch {
                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                    alphabetRightHandled = true
                    openChannelAlphabet()
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                alphabetRightJob?.cancel()
                if (!alphabetRightHandled && !event.isCanceled) pageChannelList(1)
            }
            return true
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
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER -> if (numberInput.isNotEmpty()) {
                    commitChannelNumber()
                } else if (
                    binding.parentalLockPanel.visibility == View.VISIBLE &&
                    currentChannel != null
                ) {
                    requestChannelPin(requireNotNull(currentChannel))
                } else if (vodMode || currentIptvContentKind == IptvContentKind.VOD) {
                    showIptvPlaybackControls(activateControls = true)
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
                KeyEvent.KEYCODE_DPAD_LEFT -> if (
                    binding.channelPanel.visibility == View.VISIBLE &&
                    binding.channelList.hasFocus()
                ) {
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
                    showPlaybackModeDialog()
                }
                KeyEvent.KEYCODE_PROG_GREEN -> showIptvGridPicker()
                KeyEvent.KEYCODE_PROG_YELLOW -> if (binding.channelPanel.visibility != View.VISIBLE) {
                    return super.dispatchKeyEvent(event)
                }
                KeyEvent.KEYCODE_PROG_BLUE -> if (
                    binding.channelPanel.visibility == View.VISIBLE
                ) {
                    if (channelPanelContent == ChannelPanelContent.IPTV_LIBRARY) {
                        showIptvLibraryFilterDialog()
                    } else {
                        showChannelSearchDialog()
                    }
                } else {
                    openDisplaySettings()
                }
                KeyEvent.KEYCODE_WINDOW -> enterTvPictureInPicture()
                KeyEvent.KEYCODE_LANGUAGE_SWITCH,
                KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
                KeyEvent.KEYCODE_TV_AUDIO_DESCRIPTION -> showAudioTracks()
                KeyEvent.KEYCODE_CAPTIONS,
                175 -> showSubtitleTracks()
                KeyEvent.KEYCODE_TV_TELETEXT, 233 -> showTxtTracks()
                KeyEvent.KEYCODE_TV_INPUT -> showPhysicalInputSelector()
                KeyEvent.KEYCODE_GUIDE -> openProgramGuide()
                KeyEvent.KEYCODE_SEARCH -> {
                    if (binding.channelPanel.visibility != View.VISIBLE) showChannelPanel(expanded = false)
                    showChannelSearchDialog()
                }
                KeyEvent.KEYCODE_INFO -> if (binding.infoBar.visibility == View.VISIBLE) {
                    openProgramGuide()
                } else {
                    currentChannel?.let(::showInfoBarForChannel) ?: showInfoBar()
                }
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

    private fun KeyEvent.toRemoteKeyPhase(): RemoteKeyPhase = when {
        action == KeyEvent.ACTION_UP -> RemoteKeyPhase.UP
        repeatCount > 0 -> RemoteKeyPhase.REPEAT
        else -> RemoteKeyPhase.DOWN
    }

    private fun KeyEvent.toRemoteKey(): RemoteKey = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> RemoteKey.BACK
        KeyEvent.KEYCODE_SETTINGS, KeyEvent.KEYCODE_TV_CONTENTS_MENU, 312 -> RemoteKey.SETTINGS
        KeyEvent.KEYCODE_LAST_CHANNEL -> RemoteKey.LAST_CHANNEL
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_STOP,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> RemoteKey.MEDIA
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN -> RemoteKey.DIRECTION
        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_CHANNEL_DOWN -> RemoteKey.CHANNEL
        KeyEvent.KEYCODE_PROG_RED,
        KeyEvent.KEYCODE_PROG_GREEN,
        KeyEvent.KEYCODE_PROG_YELLOW,
        KeyEvent.KEYCODE_PROG_BLUE -> RemoteKey.COLOR
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> RemoteKey.OK
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> RemoteKey.NUMBER
        KeyEvent.KEYCODE_INFO -> RemoteKey.INFO
        KeyEvent.KEYCODE_GUIDE -> RemoteKey.GUIDE
        KeyEvent.KEYCODE_TV_INPUT -> RemoteKey.INPUT
        KeyEvent.KEYCODE_MENU -> RemoteKey.MENU
        else -> RemoteKey.OTHER
    }

    private fun iptvPlaybackStateText(): Int = when (currentIptvContentKind) {
        IptvContentKind.LIVE -> R.string.iptv_live_edge
        IptvContentKind.VOD -> if (iptvPlayback.playbackSnapshot().isPlaying) {
            R.string.iptv_vod_playing
        } else {
            R.string.iptv_vod_paused
        }
        IptvContentKind.UNKNOWN -> R.string.iptv_vod_playing
    }

    override fun onStart() {
        super.onStart()
        endBlackout("ACTIVITY_START")
        if (vodMode || !resumeTifPlayback) return
        resumeTifPlayback = false
        val passthroughInputId = activePassthroughInputId
        binding.tvView.postDelayed({
            if (passthroughInputId != null) {
                debugLog.recordDebug("TIF_SESSION_RESUME | passthrough=$passthroughInputId")
                playback.playPassthrough(passthroughInputId)
            } else {
                currentChannel?.takeIf { it.source == LiveChannel.Source.TIF }?.let { channel ->
                    debugLog.recordDebug("TIF_SESSION_RESUME | channel=${channel.sourceKey}")
                    playback.play(channel)
                    applyPreferredTracks()
                }
            }
        }, 250L)
    }

    override fun onStop() {
        alphabetLoadJob?.cancel()
        alphabetRightJob?.cancel()
        alphabetRail?.close()
        binding.blackoutAnimation.pauseAnimation()
        saveCurrentIptvResumePosition()
        if (!vodMode && !isChangingConfigurations &&
            (activePassthroughInputId != null || currentChannel?.source == LiveChannel.Source.TIF)
        ) {
            resumeTifPlayback = true
            debugLog.recordDebug("TIF_SESSION_RELEASE | background")
            playback.stop()
        }
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("multiview-draft", ArrayList(multiViewDraft.keys))
        outState.putBoolean("vod-mode", vodMode)
    }

    override fun onDestroy() {
        saveCurrentIptvResumePosition()
        if (::debugLog.isInitialized) {
            debugLog.recordDebug(
                "MAIN_DESTROY | finishing=$isFinishing, config=$isChangingConfigurations",
            )
        }
        channelZapper.cancel()
        focusedTuneJob?.cancel()
        infoBarJob?.cancel()
        alphabetLoadJob?.cancel()
        alphabetFilterJob?.cancel()
        alphabetRightJob?.cancel()
        alphabetRail?.close()
        channelPanelJob?.cancel()
        programJob?.cancel()
        visibleProgramsJob?.cancel()
        channelLoadJob?.cancel()
        epgRefreshJob?.cancel()
        sleepTimerJob?.cancel()
        iptvControlsJob?.cancel()
        iptvLiveHealthJob?.cancel()
        greenLongPressJob?.cancel()
        iptvInfoTickerJob?.cancel()
        settingsLongPressJob?.cancel()
        clockJob?.cancel()
        playback.stop()
        iptvPlayback.release()
        secondaryIptvPlayback.release()
        multiViewController.release()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        ChannelLogoLoader.trimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
            when {
                iptvGridActive -> stopIptvGrid(resumePrevious = true)
                iptvOverlayActive -> stopIptvOverlay()
            }
            debugLog.recordDebug("MEMORY_PRESSURE | level=$level, auxiliaryPlaybackStopped=true")
        }
    }

    /** Handles the VOD_SOURCE_KEY deep link from VodHomeActivity on both cold
     *  start (onCreate) and warm delivery (onNewIntent, singleTop). */
    private fun handleVodSourceDeepLink(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_LIVE_MODE, false) == true) {
            intent.removeExtra(EXTRA_LIVE_MODE)
            val targetSource = intent.getStringExtra(EXTRA_LIVE_TARGET_SOURCE)
            intent.removeExtra(EXTRA_LIVE_TARGET_SOURCE)
            switchToLiveMode(targetSource)
            return
        }
        val directStreamUrl = intent?.getStringExtra(EXTRA_VOD_STREAM_URL)
        val sourceKey = intent?.getStringExtra(EXTRA_VOD_SOURCE_KEY)
            ?: if (!directStreamUrl.isNullOrBlank()) "direct_vod_${System.currentTimeMillis()}" else null
            ?: return

        // Consume intent extras immediately so that re-entering onResume / focus does not re-trigger deep link loop
        intent?.removeExtra(EXTRA_VOD_STREAM_URL)
        intent?.removeExtra(EXTRA_VOD_SOURCE_KEY)

        enterVodMode()
        showVodPlayerControls(autoHide = false)
        val requestGeneration = ++channelResolutionGeneration
        val startOver = intent?.getBooleanExtra(EXTRA_VOD_START_OVER, false) == true
        intent?.removeExtra(EXTRA_VOD_START_OVER)
        if (startOver) {
            saveCurrentIptvResumePosition()
            iptvPlayback.stop()
            iptvResumeStore.clear(sourceKey)
        }

        if (!directStreamUrl.isNullOrBlank()) {
            val mainTitle = intent?.getStringExtra(EXTRA_VOD_TITLE) ?: getString(R.string.vod_archive_label)
            val subTitle = intent?.getStringExtra(EXTRA_VOD_EPISODE_TITLE)
            val logo = intent?.getStringExtra(EXTRA_VOD_LOGO_URL)
            currentVodHasEpisodes = !subTitle.isNullOrBlank() || sourceKey.contains(":ep:")
            intent?.removeExtra(EXTRA_VOD_TITLE)
            intent?.removeExtra(EXTRA_VOD_EPISODE_TITLE)
            intent?.removeExtra(EXTRA_VOD_LOGO_URL)

            iptvPlayback.stop()
            val customReferrer = intent?.getStringExtra(com.tvapp.livetv.browser.TvBrowserActivity.EXTRA_VOD_REFERRER)
            val customOrigin = intent?.getStringExtra(com.tvapp.livetv.browser.TvBrowserActivity.EXTRA_VOD_ORIGIN)
            val customUserAgent = intent?.getStringExtra(com.tvapp.livetv.browser.TvBrowserActivity.EXTRA_VOD_USER_AGENT)
            val customCookie = intent?.getStringExtra(com.tvapp.livetv.browser.TvBrowserActivity.EXTRA_VOD_COOKIE)
            intent?.removeExtra(com.tvapp.livetv.browser.TvBrowserActivity.EXTRA_VOD_REFERRER)
            intent?.removeExtra(com.tvapp.livetv.browser.TvBrowserActivity.EXTRA_VOD_ORIGIN)
            intent?.removeExtra(com.tvapp.livetv.browser.TvBrowserActivity.EXTRA_VOD_USER_AGENT)
            intent?.removeExtra(com.tvapp.livetv.browser.TvBrowserActivity.EXTRA_VOD_COOKIE)

            val baseChannel = LiveChannel(
                id = sourceKey.hashCode().toLong(),
                sourceKey = sourceKey,
                inputId = "iptv:direct_vod",
                displayNumber = "VOD",
                displayName = mainTitle,
                source = LiveChannel.Source.IPTV,
                uri = directStreamUrl,
                logoUrl = logo,
                iptvContentType = "VOD",
                userAgent = customUserAgent,
                referrer = customReferrer,
                origin = customOrigin,
            ).apply {
                episodeTitle = subTitle
                cookie = customCookie
            }

            // Dogrudan medya akisi ise (mp4, m3u8, mkv vs.) web resolver'a girmeden direkt oynat
            if (com.tvapp.livetv.vod.WebVideoResolver.isDirectStream(directStreamUrl)) {
                selectChannel(baseChannel, recordHistory = true)
                return
            }

            // Web sayfasi / sniffer gerektiren URL ise cozumle
            binding.iptvBufferingSpinner.visibility = View.VISIBLE
            binding.iptvBufferingContainer.visibility = View.VISIBLE
            binding.iptvBufferingText.text = getString(R.string.vod_archive_preparing)

            vodResolveJob?.cancel()
            vodResolveJob = lifecycleScope.launch {
                val resolved = withContext(Dispatchers.IO) {
                    com.tvapp.livetv.vod.WebVideoResolver.resolveStream(directStreamUrl, this@MainActivity)
                }
                binding.iptvBufferingContainer.visibility = View.GONE
                if (!vodMode || requestGeneration != channelResolutionGeneration) return@launch
                val finalStreamUrl = if (resolved.isPlayable && resolved.streamUrl.isNotBlank()) resolved.streamUrl else directStreamUrl
                val playChannel = baseChannel.copy(
                    uri = finalStreamUrl,
                    userAgent = customUserAgent ?: resolved.userAgent,
                    referrer = customReferrer ?: resolved.referrer,
                    origin = customOrigin ?: resolved.origin,
                ).apply {
                    episodeTitle = subTitle
                    cookie = customCookie ?: resolved.cookie
                }
                selectChannel(playChannel, recordHistory = true)
            }
            return
        }

        if (currentChannel?.sourceKey == sourceKey && iptvPlayback.tunedSourceKey() == sourceKey &&
            iptvPlayback.healthSnapshot().phase == IptvPlaybackPhase.READY
        ) {
            iptvPlayback.play()
            showVodPlayerControls(autoHide = true)
            return
        }

        // VOD items resolution directly from repository/DB
        iptvPlayback.stop()
        binding.iptvBufferingSpinner.visibility = View.VISIBLE
        binding.iptvBufferingContainer.visibility = View.VISIBLE
        binding.iptvBufferingText.text = getString(R.string.vod_archive_preparing)

        vodResolveJob?.cancel()
        vodResolveJob = lifecycleScope.launch {
            val channel = withContext(Dispatchers.IO) {
                val direct = channels.firstOrNull { it.sourceKey == sourceKey }
                    ?: runCatching { iptvRepository.channel(sourceKey) }.getOrNull()

                if (direct != null) {
                    val vodPending = direct.copy(iptvContentType = "VOD")
                    if (com.tvapp.livetv.vod.WebVideoResolver.isDirectStream(vodPending.uri)) {
                        vodPending
                    } else {
                        // Web tabanli VOD icerigi (JSON listeleri, web sayfasi embedleri):
                        val resolved = com.tvapp.livetv.vod.WebVideoResolver.resolveStream(vodPending.uri, this@MainActivity)
                        val finalStreamUrl = if (resolved.isPlayable && resolved.streamUrl.isNotBlank()) resolved.streamUrl else vodPending.uri
                        vodPending.copy(
                            uri = finalStreamUrl,
                            userAgent = resolved.userAgent ?: vodPending.userAgent,
                            referrer = resolved.referrer ?: vodPending.referrer,
                            origin = resolved.origin ?: vodPending.origin,
                        ).apply {
                            cookie = resolved.cookie ?: vodPending.cookie
                        }
                    }
                } else {
                    val appDb = com.tvapp.livetv.data.local.TVAppDatabase.getInstance(this@MainActivity)
                    var meta = runCatching { appDb.vodDao().metadata(sourceKey) }.getOrNull()
                    var dynamicEpTitle: String? = null
                    var dynamicEpUrl: String? = null

                    // JSON VOD dizi bolumu cozumlemesi (sourceKey format: parentKey:ep:index_hash)
                    if (meta == null && sourceKey.contains(":ep:")) {
                        val parentKey = sourceKey.substringBefore(":ep:")
                        val parentMeta = runCatching { appDb.vodDao().metadata(parentKey) }.getOrNull()
                        if (parentMeta != null && !parentMeta.streamUrl.isNullOrBlank() && parentMeta.streamUrl.startsWith("[")) {
                            try {
                                val epArray = org.json.JSONArray(parentMeta.streamUrl)
                                val epPart = sourceKey.substringAfter(":ep:")
                                val epIdx = epPart.substringBefore('_').toIntOrNull() ?: 0
                                if (epIdx in 0 until epArray.length()) {
                                    val epObj = epArray.optJSONObject(epIdx)
                                    if (epObj != null) {
                                        val sNum = epObj.optInt("season", epObj.optInt("season_number", 1)).coerceAtLeast(1)
                                        val eNum = epObj.optInt("episode", epObj.optInt("episode_number", epIdx + 1)).coerceAtLeast(1)
                                        val rawTitle = epObj.optString("title").trim().ifEmpty { epObj.optString("name").trim() }
                                        dynamicEpTitle = if (rawTitle.isNotEmpty() && !rawTitle.equals("$eNum. Bölüm", true)) {
                                            "$sNum. Sezon $eNum. Bölüm - $rawTitle"
                                        } else {
                                            "$sNum. Sezon $eNum. Bölüm"
                                        }
                                        dynamicEpUrl = epObj.optString("videoUrl").trim().ifEmpty { epObj.optString("url").trim() }
                                        meta = parentMeta.copy(sourceKey = sourceKey, streamUrl = dynamicEpUrl)
                                    }
                                }
                            } catch (e: Exception) {
                                debugLog.recordDebug("Parse dynamic JSON episode in resolve failed: ${e.message}")
                            }
                        }
                    }

                    if (meta != null && !meta.streamUrl.isNullOrBlank()) {
                        val hasEp = meta.parentKey != null || meta.kind == "SERIES" ||
                            (!meta.streamUrl.isNullOrBlank() && meta.streamUrl.startsWith("[")) ||
                            sourceKey.contains(":ep:")
                        withContext(Dispatchers.Main) {
                            currentVodHasEpisodes = hasEp
                        }
                        val parentTitle = meta.parentKey?.let { pKey ->
                            runCatching { appDb.vodDao().metadata(pKey)?.name }.getOrNull()
                        }
                        val mainTitle = parentTitle ?: meta.name
                        val subTitle = dynamicEpTitle ?: if (parentTitle != null) {
                            val sNum = meta.seasonNumber ?: 1
                            val epNum = meta.episodeNumber
                            if (epNum != null) getString(R.string.vod_season_episode_fmt, sNum, epNum) else meta.name
                        } else null

                        val pendingChannel = LiveChannel(
                            id = meta.sourceKey.hashCode().toLong(),
                            sourceKey = meta.sourceKey,
                            inputId = "iptv:${meta.sourceId}",
                            displayNumber = "VOD",
                            displayName = mainTitle,
                            source = LiveChannel.Source.IPTV,
                            uri = meta.streamUrl,
                            logoUrl = meta.logoUrl,
                            iptvContentType = "VOD",
                        ).apply {
                            episodeTitle = subTitle
                        }

                        // Eger dogrudan medya akisi ise web resolver calistirma
                        if (com.tvapp.livetv.vod.WebVideoResolver.isDirectStream(meta.streamUrl)) {
                            pendingChannel
                        } else {
                            val resolved = com.tvapp.livetv.vod.WebVideoResolver.resolveStream(meta.streamUrl, this@MainActivity)
                            val finalStreamUrl = if (resolved.isPlayable && resolved.streamUrl.isNotBlank()) resolved.streamUrl else meta.streamUrl
                            pendingChannel.copy(
                                uri = finalStreamUrl,
                                userAgent = resolved.userAgent ?: pendingChannel.userAgent,
                                referrer = resolved.referrer ?: pendingChannel.referrer,
                                origin = resolved.origin ?: pendingChannel.origin,
                            ).apply {
                                episodeTitle = subTitle
                                cookie = resolved.cookie ?: pendingChannel.cookie
                            }
                        }
                    } else null
                }
            }
            binding.iptvBufferingContainer.visibility = View.GONE
            if (!vodMode || requestGeneration != channelResolutionGeneration) return@launch
            if (channel != null) {
                selectChannel(channel, recordHistory = true)
            } else {
                modePreferences.edit().remove(LAST_VOD_KEY).apply()
                showIptvNotice(R.string.channel_not_found)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleVodSourceDeepLink(intent)
        val requestedUri = intent.getStringExtra(TvChannelViewActivity.EXTRA_TIF_CHANNEL_URI)
        if (requestedUri != null) {
            if (currentChannel?.uri == requestedUri) return
            channels.firstOrNull { it.uri == requestedUri }?.let(::selectChannel)
                ?: run {
                    pendingExternalChannelUri = requestedUri
                    loadChannels(preserveCurrentPlayback = true)
                }
            return
        }
        intent.data?.getQueryParameter("sourceKey")?.let { sourceKey ->
            val inList = channels.firstOrNull { it.sourceKey == sourceKey }
            if (inList != null) {
                selectChannel(inList)
            } else {
                lifecycleScope.launch {
                    val vod = withContext(Dispatchers.IO) { iptvRepository.channel(sourceKey) }
                    if (vod != null) {
                        selectChannel(vod)
                    } else {
                        pendingHomeChannelKey = sourceKey
                        loadChannels(preserveCurrentPlayback = true)
                    }
                }
            }
        }
    }

}
