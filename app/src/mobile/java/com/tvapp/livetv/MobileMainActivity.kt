package com.tvapp.livetv

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.tvapp.livetv.data.ChannelRepository
import com.tvapp.livetv.data.IptvRepository
import com.tvapp.livetv.databinding.ActivityMobileMainBinding
import com.tvapp.livetv.model.LiveChannel
import com.tvapp.livetv.playback.IptvPlaybackController
import com.tvapp.livetv.playback.IptvPlaybackPhase
import com.tvapp.livetv.ui.MobileCategoryAdapter
import com.tvapp.livetv.ui.MobileChannelAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class MobileMainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMobileMainBinding
    private lateinit var channelRepository: ChannelRepository
    private lateinit var iptvRepository: IptvRepository
    private lateinit var iptvPlayback: IptvPlaybackController
    private lateinit var audioManager: AudioManager

    private lateinit var channelAdapter: MobileChannelAdapter
    private lateinit var categoryAdapter: MobileCategoryAdapter
    private lateinit var sideChannelAdapter: MobileChannelAdapter
    private lateinit var sideCategoryAdapter: MobileCategoryAdapter

    private val allChannels = mutableListOf<LiveChannel>()
    private var currentChannel: LiveChannel? = null
    private var currentCategory = "Tümü"
    private var currentSearchQuery = ""
    private var sideCategory = "Tümü"
    private var sideSearchQuery = ""

    private val hideControlsHandler = Handler(Looper.getMainLooper())
    private val hideHudHandler = Handler(Looper.getMainLooper())

    private var resizeModeIndex = 0
    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
    )

    private val iptvSourcesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        loadChannels()
    }

    private val iptvSelectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        loadChannels()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMobileMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        channelRepository = ChannelRepository(this)
        iptvRepository = IptvRepository(this)

        setupPlayback()
        setupRecyclerViews()
        setupSideChannelDrawer()
        setupSearchAndActions()
        setupPlayerControls()
        setupGestureDetector()
        setupBackNavigation()

        loadChannels()
    }

    private fun setupPlayback() {
        iptvPlayback = IptvPlaybackController(
            context = this,
            playerView = binding.mobilePlayerView,
            enableIjkFallback = true,
        )

        iptvPlayback.onPlaybackReady = {
            binding.mobileBuffering.visibility = View.GONE
            binding.overlayBtnPlayPause.setImageResource(R.drawable.ic_pause)
        }

        iptvPlayback.onPlaybackError = { error ->
            binding.mobileBuffering.visibility = View.GONE
            Toast.makeText(
                this,
                getString(R.string.playback_error_template, error.message),
                Toast.LENGTH_SHORT,
            ).show()
        }

        iptvPlayback.onHealthChanged = { health ->
            if (health.phase == IptvPlaybackPhase.BUFFERING) {
                binding.mobileBuffering.visibility = View.VISIBLE
            } else if (health.phase == IptvPlaybackPhase.READY || health.isPlaying) {
                binding.mobileBuffering.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerViews() {
        categoryAdapter = MobileCategoryAdapter { selectedCat ->
            currentCategory = selectedCat
            applyFilters()
        }
        binding.mobileCategoryRecycler.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false,
        )
        binding.mobileCategoryRecycler.adapter = categoryAdapter

        channelAdapter = MobileChannelAdapter(
            onChannelClicked = { channel ->
                playChannel(channel)
            },
            onFavoriteToggled = { channel ->
                toggleFavorite(channel)
            },
        )
        binding.mobileChannelRecycler.layoutManager = LinearLayoutManager(this)
        binding.mobileChannelRecycler.adapter = channelAdapter
    }

    private fun setupSideChannelDrawer() {
        sideCategoryAdapter = MobileCategoryAdapter { selectedCat ->
            sideCategory = selectedCat
            applySideFilters()
        }
        binding.sideDrawerCategoryList.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.HORIZONTAL,
            false,
        )
        binding.sideDrawerCategoryList.adapter = sideCategoryAdapter

        sideChannelAdapter = MobileChannelAdapter(
            onChannelClicked = { channel ->
                playChannel(channel)
            },
            onFavoriteToggled = { channel ->
                toggleFavorite(channel)
            },
        )
        binding.sideDrawerChannelList.layoutManager = LinearLayoutManager(this)
        binding.sideDrawerChannelList.adapter = sideChannelAdapter

        binding.sideDrawerSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                sideSearchQuery = s?.toString()?.trim().orEmpty()
                applySideFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.overlayBtnChannels.setOnClickListener {
            toggleSideChannelDrawer()
        }

        binding.sideDrawerBtnClose.setOnClickListener {
            closeSideChannelDrawer()
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this) {
            if (binding.mobileSideChannelDrawer.visibility == View.VISIBLE) {
                closeSideChannelDrawer()
            } else if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun setupSearchAndActions() {
        binding.mobileSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearchQuery = s?.toString()?.trim().orEmpty()
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.mobileBtnSources.setOnClickListener {
            iptvSourcesLauncher.launch(Intent(this, IptvSourcesActivity::class.java))
        }

        binding.mobileBtnChannelSelect.setOnClickListener {
            iptvSelectionLauncher.launch(Intent(this, IptvChannelSelectionActivity::class.java))
        }

        binding.mobileBtnSettings.setOnClickListener {
            startActivity(Intent(this, DisplaySettingsActivity::class.java))
        }

        binding.mobileEmptyActionButton.setOnClickListener {
            iptvSourcesLauncher.launch(Intent(this, IptvSourcesActivity::class.java))
        }
    }

    private fun setupPlayerControls() {
        binding.overlayBtnPlayPause.setOnClickListener {
            if (iptvPlayback.playbackSnapshot().isPlaying) {
                iptvPlayback.pause()
                binding.overlayBtnPlayPause.setImageResource(R.drawable.ic_play)
            } else {
                iptvPlayback.play()
                binding.overlayBtnPlayPause.setImageResource(R.drawable.ic_pause)
            }
            scheduleHideControls()
        }

        binding.overlayBtnFullscreen.setOnClickListener {
            toggleFullscreen()
        }

        binding.overlayBtnSettings.setOnClickListener {
            startActivity(Intent(this, DisplaySettingsActivity::class.java))
        }

        binding.overlayBtnBack.setOnClickListener {
            if (binding.mobileSideChannelDrawer.visibility == View.VISIBLE) {
                closeSideChannelDrawer()
            } else if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }

        binding.overlayBtnAspect.setOnClickListener {
            resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
            binding.mobilePlayerView.resizeMode = resizeModes[resizeModeIndex]
            scheduleHideControls()
        }

        scheduleHideControls()
    }

    private fun setupGestureDetector() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControlsOverlay()
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (e1 == null) return false
                val deltaY = e1.y - e2.y
                val width = binding.mobileVideoContainer.width
                val isLeftHalf = e1.x < width / 2

                if (abs(distanceY) > abs(distanceX)) {
                    if (isLeftHalf) {
                        adjustBrightness(deltaY / binding.mobileVideoContainer.height)
                    } else {
                        adjustVolume(deltaY / binding.mobileVideoContainer.height)
                    }
                    return true
                }
                return false
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (e1 == null) return false
                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > 120) {
                    if (deltaX < 0) {
                        zapChannel(1)
                    } else {
                        zapChannel(-1)
                    }
                    return true
                }
                return false
            }
        })

        binding.mobileTouchSurface.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun loadChannels() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                channelRepository.channels(includeTif = false)
            }

            result.fold(
                onSuccess = { channels ->
                    allChannels.clear()
                    allChannels.addAll(channels)

                    if (allChannels.isEmpty()) {
                        binding.mobileEmptyContainer.visibility = View.VISIBLE
                        binding.mobileChannelRecycler.visibility = View.GONE
                        binding.mobileCategoryRecycler.visibility = View.GONE
                        binding.mobileChannelCountText.visibility = View.GONE
                    } else {
                        binding.mobileEmptyContainer.visibility = View.GONE
                        binding.mobileChannelRecycler.visibility = View.VISIBLE
                        binding.mobileCategoryRecycler.visibility = View.VISIBLE
                        binding.mobileChannelCountText.visibility = View.VISIBLE

                        setupCategories()
                        applyFilters()
                        setupSideCategories()
                        applySideFilters()

                        if (currentChannel == null && allChannels.isNotEmpty()) {
                            playChannel(allChannels.first())
                        }
                    }
                },
                onFailure = { error ->
                    Toast.makeText(this@MobileMainActivity, error.message, Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    private fun setupCategories() {
        val categories = mutableListOf("Tümü", "Favoriler")
        val groupTitles = allChannels.mapNotNull { it.groupTitle?.trim() }
            .filter { it.isNotBlank() && it !in categories }
            .distinct()
            .sorted()
        categories.addAll(groupTitles)
        categoryAdapter.submitCategories(categories, currentCategory)
    }

    private fun applyFilters() {
        var filtered = allChannels.asSequence()

        if (currentCategory == "Favoriler") {
            filtered = filtered.filter { it.favorite }
        } else if (currentCategory != "Tümü" && currentCategory.isNotBlank()) {
            filtered = filtered.filter { it.groupTitle == currentCategory }
        }

        if (currentSearchQuery.isNotBlank()) {
            val queryLower = currentSearchQuery.lowercase()
            filtered = filtered.filter { channel ->
                channel.displayName.lowercase().contains(queryLower) ||
                    channel.displayNumber.contains(queryLower)
            }
        }

        val resultList = filtered.toList()
        channelAdapter.submitList(resultList)
        binding.mobileChannelCountText.text = getString(R.string.channel_count_format, resultList.size)
    }

    private fun toggleSideChannelDrawer() {
        if (binding.mobileSideChannelDrawer.visibility == View.VISIBLE) {
            closeSideChannelDrawer()
        } else {
            openSideChannelDrawer()
        }
    }

    private fun openSideChannelDrawer() {
        binding.mobileSideChannelDrawer.visibility = View.VISIBLE
        hideControlsHandler.removeCallbacksAndMessages(null)
        setupSideCategories()
        applySideFilters()
    }

    private fun closeSideChannelDrawer() {
        binding.mobileSideChannelDrawer.visibility = View.GONE
        scheduleHideControls()
    }

    private fun setupSideCategories() {
        val categories = mutableListOf("Tümü", "Favoriler")
        val groupTitles = allChannels.mapNotNull { it.groupTitle?.trim() }
            .filter { it.isNotBlank() && it !in categories }
            .distinct()
            .sorted()
        categories.addAll(groupTitles)
        sideCategoryAdapter.submitCategories(categories, sideCategory)
    }

    private fun applySideFilters() {
        var filtered = allChannels.asSequence()

        if (sideCategory == "Favoriler") {
            filtered = filtered.filter { it.favorite }
        } else if (sideCategory != "Tümü" && sideCategory.isNotBlank()) {
            filtered = filtered.filter { it.groupTitle == sideCategory }
        }

        if (sideSearchQuery.isNotBlank()) {
            val queryLower = sideSearchQuery.lowercase()
            filtered = filtered.filter { channel ->
                channel.displayName.lowercase().contains(queryLower) ||
                    channel.displayNumber.contains(queryLower)
            }
        }

        val resultList = filtered.toList()
        sideChannelAdapter.submitList(resultList)
    }

    private fun playChannel(channel: LiveChannel) {
        currentChannel = channel
        channelAdapter.currentPlayingKey = channel.sourceKey
        sideChannelAdapter.currentPlayingKey = channel.sourceKey
        channelAdapter.notifyDataSetChanged()
        sideChannelAdapter.notifyDataSetChanged()

        binding.overlayChannelTitle.text = channel.displayName
        binding.overlayChannelQuality.text = channel.videoFormat ?: ""
        binding.overlayChannelQuality.visibility = if (!channel.videoFormat.isNullOrBlank()) View.VISIBLE else View.GONE
        binding.mobileBuffering.visibility = View.VISIBLE

        iptvPlayback.play(channel)
        scheduleHideControls()
    }

    private fun zapChannel(direction: Int) {
        val currentList = channelAdapter.currentList
        if (currentList.isEmpty()) return

        val currentIndex = currentList.indexOfFirst { it.sourceKey == currentChannel?.sourceKey }
        val nextIndex = if (currentIndex == -1) {
            0
        } else {
            (currentIndex + direction + currentList.size) % currentList.size
        }
        playChannel(currentList[nextIndex])
    }

    private fun toggleFavorite(channel: LiveChannel) {
        lifecycleScope.launch {
            val updated = channel.copy(favorite = !channel.favorite)
            withContext(Dispatchers.IO) {
                channelRepository.setFavorite(channel.sourceKey, updated.favorite)
            }
            val index = allChannels.indexOfFirst { it.sourceKey == channel.sourceKey }
            if (index >= 0) {
                allChannels[index] = updated
            }
            applyFilters()
            applySideFilters()
        }
    }

    private fun toggleFullscreen() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        applyOrientationLayout(isLandscape)
    }

    private fun applyOrientationLayout(isLandscape: Boolean) {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        if (isLandscape) {
            binding.mobileContentContainer.visibility = View.GONE
            binding.mobileVideoContainer.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            binding.overlayBtnBack.visibility = View.VISIBLE
            binding.overlayBtnChannels.visibility = View.VISIBLE
            binding.overlayBtnFullscreen.setImageResource(R.drawable.ic_fullscreen_exit)
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            closeSideChannelDrawer()
            binding.mobileContentContainer.visibility = View.VISIBLE
            binding.mobileVideoContainer.layoutParams.height = dp(230)
            binding.overlayBtnBack.visibility = View.GONE
            binding.overlayBtnChannels.visibility = View.GONE
            binding.overlayBtnFullscreen.setImageResource(R.drawable.ic_fullscreen)
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        binding.mobileVideoContainer.requestLayout()
    }

    private fun toggleControlsOverlay() {
        if (binding.mobileSideChannelDrawer.visibility == View.VISIBLE) {
            closeSideChannelDrawer()
            return
        }
        if (binding.mobileControlsOverlay.visibility == View.VISIBLE) {
            binding.mobileControlsOverlay.visibility = View.GONE
            hideControlsHandler.removeCallbacksAndMessages(null)
        } else {
            binding.mobileControlsOverlay.visibility = View.VISIBLE
            scheduleHideControls()
        }
    }

    private fun scheduleHideControls() {
        hideControlsHandler.removeCallbacksAndMessages(null)
        hideControlsHandler.postDelayed({
            if (!isFinishing && !isDestroyed) {
                binding.mobileControlsOverlay.visibility = View.GONE
            }
        }, 4000)
    }

    private fun adjustVolume(deltaFraction: Float) {
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val delta = (deltaFraction * maxVolume * 2).roundToInt()
        val newVolume = (currentVolume + delta).coerceIn(0, maxVolume)

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        val percent = ((newVolume.toFloat() / maxVolume) * 100).roundToInt()

        showGestureHud(R.drawable.ic_volume_up, "$percent%", percent)
    }

    private fun adjustBrightness(deltaFraction: Float) {
        val currentBrightness = window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
        val newBrightness = (currentBrightness + deltaFraction * 0.5f).coerceIn(0.01f, 1.0f)

        val layoutParams = window.attributes
        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams

        val percent = (newBrightness * 100).roundToInt()
        showGestureHud(R.drawable.ic_brightness_medium, "$percent%", percent)
    }

    private fun showGestureHud(iconRes: Int, text: String, progress: Int) {
        binding.mobileGestureHud.visibility = View.VISIBLE
        binding.hudIcon.setImageResource(iconRes)
        binding.hudText.text = text
        binding.hudProgress.progress = progress

        hideHudHandler.removeCallbacksAndMessages(null)
        hideHudHandler.postDelayed({
            binding.mobileGestureHud.visibility = View.GONE
        }, 1200)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && iptvPlayback.playbackSnapshot().isPlaying) {
            if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                runCatching { enterPictureInPictureMode(params) }
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    override fun onResume() {
        super.onResume()
        if (currentChannel != null && !iptvPlayback.playbackSnapshot().isPlaying) {
            iptvPlayback.play()
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            // PiP modunda oynatmaya devam et
        } else {
            iptvPlayback.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideControlsHandler.removeCallbacksAndMessages(null)
        hideHudHandler.removeCallbacksAndMessages(null)
        iptvPlayback.release()
    }
}
