package com.tvapp.livetv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.tvapp.livetv.settings.AppLanguage
import com.tvapp.livetv.settings.AppLanguageStore
import com.tvapp.livetv.settings.ChannelPanelSide
import com.tvapp.livetv.settings.DisplayPreferences
import com.tvapp.livetv.settings.DisplayPreferencesStore
import com.tvapp.livetv.settings.InfoBarPosition
import com.tvapp.livetv.settings.SleepTimerStore
import com.tvapp.livetv.update.AppUpdateManager
import com.tvapp.livetv.tifinput.IptvInputChannelSyncRepository
import com.tvapp.livetv.tifinput.IptvInputResolver
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class DisplaySettingsActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private lateinit var displayStore: DisplayPreferencesStore
    private lateinit var sleepTimerStore: SleepTimerStore
    private lateinit var languageStore: AppLanguageStore
    private var current = DisplayPreferences()
    private var changed = false
    private var pendingApkUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_settings)
        configureWindow()
        displayStore = DisplayPreferencesStore(this)
        sleepTimerStore = SleepTimerStore(this)
        languageStore = AppLanguageStore(this)
        current = displayStore.load()
        content = findViewById(R.id.settings_content)
        buildSettings()
        content.post { firstFocusableRow()?.requestFocus() }
    }

    private fun configureWindow() {
        val metrics = resources.displayMetrics
        window.setGravity(Gravity.END or Gravity.CENTER_VERTICAL)
        window.setLayout(
            (metrics.widthPixels * OSD_WIDTH_FRACTION).toInt(),
            (metrics.heightPixels * OSD_HEIGHT_FRACTION).toInt(),
        )
        window.attributes = window.attributes.apply {
            x = (metrics.widthPixels * OSD_EDGE_GAP_FRACTION).toInt()
            dimAmount = 0f
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    }

    private fun buildSettings() {
        content.removeAllViews()
        section(R.string.language_settings)
        val languages = AppLanguage.entries
        choice(
            R.string.app_language,
            languages.map { languageLabel(it) },
            languages.indexOf(languageStore.load()).coerceAtLeast(0),
        ) { index ->
            val language = languages[index]
            languageStore.save(language)
            markChanged()
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language.languageTag),
            )
        }

        section(R.string.info_bar_content)
        choice(
            R.string.info_bar_position,
            listOf(getString(R.string.top), getString(R.string.bottom)),
            if (current.infoBarPosition == InfoBarPosition.TOP) 0 else 1,
        ) { index -> update { copy(infoBarPosition = InfoBarPosition.entries[index]) } }
        toggle(R.string.show_current_program, current.showCurrentProgram) {
            update { copy(showCurrentProgram = it) }
        }
        toggle(R.string.show_next_program, current.showNextProgram) {
            update { copy(showNextProgram = it) }
        }
        number(R.string.info_bar_opacity, 30, 100, current.infoBarOpacityPercent, 5, ::percentLabel) {
            update { copy(infoBarOpacityPercent = it) }
        }
        number(R.string.info_bar_duration, 0, 15, current.infoBarDurationSeconds, 1, ::durationLabel) {
            update { copy(infoBarDurationSeconds = it) }
        }

        section(R.string.channel_list)
        choice(
            R.string.channel_panel_position,
            listOf(getString(R.string.left), getString(R.string.right)),
            if (current.channelPanelSide == ChannelPanelSide.LEFT) 0 else 1,
        ) { index -> update { copy(channelPanelSide = ChannelPanelSide.entries[index]) } }
        number(
            R.string.channel_panel_opacity,
            30,
            100,
            current.channelPanelOpacityPercent,
            5,
            ::percentLabel,
        ) { update { copy(channelPanelOpacityPercent = it) } }
        toggle(R.string.show_channel_logo, current.showChannelLogo) {
            update { copy(showChannelLogo = it) }
        }
        toggle(R.string.show_channel_program, current.showChannelProgram) {
            update { copy(showChannelProgram = it) }
        }
        toggle(R.string.show_channel_progress, current.showChannelProgress) {
            update { copy(showChannelProgress = it) }
        }
        toggle(R.string.show_channel_source_badge, current.showChannelSourceBadge) {
            update { copy(showChannelSourceBadge = it) }
        }

        section(R.string.playback_settings)
        toggle(R.string.subtitles_default, current.subtitlesEnabled) {
            update { copy(subtitlesEnabled = it) }
        }
        val timerValues = listOf(0, 15, 30, 60, 90, 120)
        val remaining = sleepTimerStore.remainingMinutes()
        val timerIndex = if (remaining <= 0) 0 else timerValues.indices.minByOrNull {
            abs(timerValues[it] - remaining)
        } ?: 0
        choice(R.string.sleep_timer, timerValues.map(::timerLabel), timerIndex) { index ->
            sleepTimerStore.schedule(timerValues[index])
            markChanged()
        }

        section(R.string.startup_settings)
        toggle(R.string.launch_tvapp_on_boot, current.launchOnBoot) {
            update { copy(launchOnBoot = it) }
        }

        section(R.string.application_settings)
        action(
            R.string.iptv_input_name,
            getString(R.string.iptv_input_sync),
            ::syncIptvInput,
        )
        action(
            R.string.check_for_updates,
            getString(R.string.current_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
            ::checkForUpdates,
        )
    }

    private fun section(titleRes: Int) {
        content.addView(TextView(this).apply {
            text = getString(titleRes)
            setTextColor(getColor(R.color.accent))
            textSize = 15f
            setPadding(dp(18), dp(22), dp(18), dp(7))
        })
    }

    private fun toggle(titleRes: Int, initial: Boolean, changed: (Boolean) -> Unit) {
        var enabled = initial
        val row = settingRow(titleRes)
        fun render() {
            row.value.text = getString(if (enabled) R.string.on else R.string.off)
        }
        row.root.setOnClickListener {
            enabled = !enabled
            render()
            changed(enabled)
        }
        row.root.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                row.root.performClick()
                true
            } else false
        }
        render()
    }

    private fun choice(
        titleRes: Int,
        labels: List<String>,
        initialIndex: Int,
        changed: (Int) -> Unit,
    ) {
        var index = initialIndex.coerceIn(labels.indices)
        val row = settingRow(titleRes)
        fun select(direction: Int) {
            index = (index + direction + labels.size) % labels.size
            row.value.text = "<  ${labels[index]}  >"
            changed(index)
        }
        row.root.setOnClickListener { select(1) }
        row.root.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> true.also { select(-1) }
                KeyEvent.KEYCODE_DPAD_RIGHT -> true.also { select(1) }
                else -> false
            }
        }
        row.value.text = "<  ${labels[index]}  >"
    }

    private fun number(
        titleRes: Int,
        minimum: Int,
        maximum: Int,
        initial: Int,
        step: Int,
        label: (Int) -> String,
        changed: (Int) -> Unit,
    ) {
        var number = initial.coerceIn(minimum, maximum)
        val row = settingRow(titleRes)
        fun adjust(direction: Int) {
            number = (number + direction * step).coerceIn(minimum, maximum)
            row.value.text = "<  ${label(number)}  >"
            changed(number)
        }
        row.root.setOnClickListener { adjust(1) }
        row.root.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> true.also { adjust(-1) }
                KeyEvent.KEYCODE_DPAD_RIGHT -> true.also { adjust(1) }
                else -> false
            }
        }
        row.value.text = "<  ${label(number)}  >"
    }

    private fun settingRow(titleRes: Int): SettingRow {
        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(58),
            ).apply { setMargins(dp(8), dp(2), dp(8), dp(2)) }
            background = AppCompatResources.getDrawable(
                this@DisplaySettingsActivity,
                R.drawable.bg_settings_item,
            )
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            isFocusable = true
            isClickable = true
            setPadding(dp(18), 0, dp(16), 0)
        }
        val title = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            text = getString(titleRes)
            setTextColor(getColorStateList(R.color.settings_title_text))
            textSize = 17f
            maxLines = 2
            isDuplicateParentStateEnabled = true
        }
        val value = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            gravity = Gravity.END
            setTextColor(getColorStateList(R.color.settings_value_text))
            textSize = 15f
            isDuplicateParentStateEnabled = true
        }
        row.addView(title)
        row.addView(value)
        content.addView(row)
        return SettingRow(row, value)
    }

    private fun action(titleRes: Int, initialValue: String, clicked: (SettingRow) -> Unit) {
        val row = settingRow(titleRes)
        row.value.text = initialValue
        row.root.setOnClickListener { clicked(row) }
    }

    private fun checkForUpdates(row: SettingRow) {
        if (!row.root.isEnabled) return
        row.root.isEnabled = false
        row.value.text = getString(R.string.update_checking)
        lifecycleScope.launch {
            runCatching {
                val manager = AppUpdateManager(this@DisplaySettingsActivity)
                val update = manager.check() ?: return@runCatching null
                row.value.text = getString(R.string.update_downloading, update.versionName)
                update to manager.download(update)
            }.onSuccess { result ->
                if (result == null) {
                    row.value.text = getString(R.string.update_not_available)
                } else {
                    row.value.text = getString(R.string.update_installing, result.first.versionName)
                    installUpdate(result.second)
                }
            }.onFailure { error ->
                row.value.text = getString(
                    R.string.update_failed,
                    error.message ?: error.javaClass.simpleName,
                )
            }
            row.root.isEnabled = true
        }
    }

    private fun syncIptvInput(row: SettingRow) {
        if (!row.root.isEnabled) return
        val inputId = IptvInputResolver.findInputId(this)
        if (inputId == null) {
            row.value.setText(R.string.iptv_input_not_found)
            return
        }
        row.root.isEnabled = false
        row.value.setText(R.string.iptv_input_syncing)
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    IptvInputChannelSyncRepository(this@DisplaySettingsActivity).sync(inputId)
                }
            }.onSuccess { result ->
                row.value.text = getString(
                    R.string.iptv_input_sync_complete,
                    result.synced,
                    result.removed,
                )
            }.onFailure { error ->
                row.value.text = getString(
                    R.string.iptv_input_sync_failed,
                    error.message ?: error.javaClass.simpleName,
                )
            }
            row.root.isEnabled = true
        }
    }

    private fun installUpdate(apkUri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingApkUri = apkUri
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        startActivity(AppUpdateManager(this).installerIntent(apkUri))
    }

    override fun onResume() {
        super.onResume()
        val uri = pendingApkUri ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()) {
            pendingApkUri = null
            startActivity(AppUpdateManager(this).installerIntent(uri))
        }
    }

    private fun update(transform: DisplayPreferences.() -> DisplayPreferences) {
        current = current.transform()
        displayStore.save(current)
        markChanged()
    }

    private fun markChanged() {
        changed = true
        setResult(Activity.RESULT_OK)
    }

    private fun firstFocusableRow(): View? = (0 until content.childCount)
        .map(content::getChildAt)
        .firstOrNull(View::isFocusable)

    private fun languageLabel(language: AppLanguage): String = when (language) {
        AppLanguage.SYSTEM -> getString(R.string.system_language)
        AppLanguage.TURKISH -> getString(R.string.turkish)
        AppLanguage.ENGLISH -> getString(R.string.english)
    }

    private fun percentLabel(value: Int) = getString(R.string.percent_value, value)

    private fun durationLabel(value: Int) = if (value == 0) {
        getString(R.string.always_visible)
    } else getString(R.string.seconds_value, value)

    private fun timerLabel(value: Int) = if (value == 0) {
        getString(R.string.off)
    } else getString(R.string.minutes_value, value)

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun finish() {
        if (changed) setResult(Activity.RESULT_OK)
        super.finish()
    }

    private data class SettingRow(val root: LinearLayout, val value: TextView)

    private companion object {
        const val OSD_WIDTH_FRACTION = 0.40f
        const val OSD_HEIGHT_FRACTION = 0.94f
        const val OSD_EDGE_GAP_FRACTION = 0.012f
    }
}
