package com.tvapp.livetv

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.tvapp.livetv.settings.ChannelPanelSide
import com.tvapp.livetv.settings.DisplayPreferences
import com.tvapp.livetv.settings.DisplayPreferencesStore
import com.tvapp.livetv.settings.InfoBarPosition
import com.tvapp.livetv.settings.SleepTimerStore

class DisplaySettingsActivity : AppCompatActivity() {
    private var changed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_settings)
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
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_host, DisplaySettingsFragment())
                .commit()
        }
    }

    fun markChanged() {
        changed = true
        setResult(Activity.RESULT_OK)
    }

    override fun finish() {
        if (changed) setResult(Activity.RESULT_OK)
        super.finish()
    }

    private companion object {
        const val OSD_WIDTH_FRACTION = 0.44f
        const val OSD_HEIGHT_FRACTION = 0.92f
        const val OSD_EDGE_GAP_FRACTION = 0.008f
    }
}

class DisplaySettingsFragment : PreferenceFragmentCompat() {
    private lateinit var store: DisplayPreferencesStore
    private lateinit var sleepTimerStore: SleepTimerStore

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        store = DisplayPreferencesStore(requireContext())
        sleepTimerStore = SleepTimerStore(requireContext())
        preferenceScreen = buildScreen(store.load())
    }

    private fun buildScreen(current: DisplayPreferences): PreferenceScreen {
        val context = requireContext()
        return preferenceManager.createPreferenceScreen(context).apply {
            addAttachedCategory(R.string.info_bar_content) {
                addPreference(listPreference(
                    KEY_INFO_POSITION,
                    R.string.info_bar_position,
                    arrayOf(getString(R.string.top), getString(R.string.bottom)),
                    arrayOf(InfoBarPosition.TOP.name, InfoBarPosition.BOTTOM.name),
                    current.infoBarPosition.name,
                ) { update { copy(infoBarPosition = InfoBarPosition.valueOf(it)) } })
                addPreference(booleanPreference(
                    KEY_SHOW_CURRENT,
                    R.string.show_current_program,
                    current.showCurrentProgram,
                ) { update { copy(showCurrentProgram = it) } })
                addPreference(booleanPreference(
                    KEY_SHOW_NEXT,
                    R.string.show_next_program,
                    current.showNextProgram,
                ) { update { copy(showNextProgram = it) } })
                addPreference(numberPreference(
                    KEY_INFO_OPACITY,
                    R.string.info_bar_opacity,
                    30,
                    100,
                    current.infoBarOpacityPercent,
                ) { update { copy(infoBarOpacityPercent = it) } })
                addPreference(numberPreference(
                    KEY_DURATION,
                    R.string.info_bar_duration,
                    0,
                    15,
                    current.infoBarDurationSeconds,
                ) { update { copy(infoBarDurationSeconds = it) } })
            }

            addAttachedCategory(R.string.channel_list) {
                addPreference(listPreference(
                    KEY_PANEL_SIDE,
                    R.string.channel_panel_position,
                    arrayOf(getString(R.string.left), getString(R.string.right)),
                    arrayOf(ChannelPanelSide.LEFT.name, ChannelPanelSide.RIGHT.name),
                    current.channelPanelSide.name,
                ) { update { copy(channelPanelSide = ChannelPanelSide.valueOf(it)) } })
                addPreference(numberPreference(
                    KEY_PANEL_OPACITY,
                    R.string.channel_panel_opacity,
                    30,
                    100,
                    current.channelPanelOpacityPercent,
                ) { update { copy(channelPanelOpacityPercent = it) } })
                addPreference(booleanPreference(
                    KEY_SHOW_LOGO,
                    R.string.show_channel_logo,
                    current.showChannelLogo,
                ) { update { copy(showChannelLogo = it) } })
                addPreference(booleanPreference(
                    KEY_SHOW_PROGRAM,
                    R.string.show_channel_program,
                    current.showChannelProgram,
                ) { update { copy(showChannelProgram = it) } })
                addPreference(booleanPreference(
                    KEY_SHOW_PROGRESS,
                    R.string.show_channel_progress,
                    current.showChannelProgress,
                ) { update { copy(showChannelProgress = it) } })
                addPreference(booleanPreference(
                    KEY_SHOW_SOURCE,
                    R.string.show_channel_source_badge,
                    current.showChannelSourceBadge,
                ) { update { copy(showChannelSourceBadge = it) } })
            }

            addAttachedCategory(R.string.playback_settings) {
                addPreference(booleanPreference(
                    KEY_SUBTITLES,
                    R.string.subtitles_default,
                    current.subtitlesEnabled,
                ) { update { copy(subtitlesEnabled = it) } })
                addPreference(listPreference(
                    KEY_SLEEP_TIMER,
                    R.string.sleep_timer,
                    arrayOf(
                        getString(R.string.off),
                        getString(R.string.minutes_value, 15),
                        getString(R.string.minutes_value, 30),
                        getString(R.string.minutes_value, 60),
                        getString(R.string.minutes_value, 90),
                        getString(R.string.minutes_value, 120),
                    ),
                    arrayOf("0", "15", "30", "60", "90", "120"),
                    sleepTimerStore.remainingMinutes().let { remaining ->
                        listOf(15, 30, 60, 90, 120).minByOrNull {
                            kotlin.math.abs(it - remaining)
                        }?.takeIf { remaining > 0 }?.toString() ?: "0"
                    },
                ) { minutes ->
                    sleepTimerStore.schedule(minutes.toIntOrNull() ?: 0)
                    (activity as? DisplaySettingsActivity)?.markChanged()
                })
            }

            addAttachedCategory(R.string.startup_settings) {
                addPreference(booleanPreference(
                    KEY_LAUNCH_ON_BOOT,
                    R.string.launch_tvapp_on_boot,
                    current.launchOnBoot,
                ) { update { copy(launchOnBoot = it) } })
            }

        }
    }

    private fun PreferenceScreen.addAttachedCategory(
        titleRes: Int,
        populate: PreferenceCategory.() -> Unit,
    ) {
        val category = category(titleRes)
        addPreference(category)
        category.populate()
    }

    private fun category(titleRes: Int) = PreferenceCategory(requireContext()).apply {
        title = getString(titleRes)
        isPersistent = false
    }

    private fun booleanPreference(
        key: String,
        titleRes: Int,
        checked: Boolean,
        changed: (Boolean) -> Unit,
    ) = SwitchPreferenceCompat(requireContext()).apply {
        this.key = key
        title = getString(titleRes)
        isChecked = checked
        isPersistent = false
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            changed(value as Boolean)
            true
        }
    }

    private fun listPreference(
        key: String,
        titleRes: Int,
        labels: Array<String>,
        values: Array<String>,
        selected: String,
        changed: (String) -> Unit,
    ) = ListPreference(requireContext()).apply {
        this.key = key
        title = getString(titleRes)
        entries = labels
        entryValues = values
        value = selected
        isPersistent = false
        summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            changed(value as String)
            true
        }
    }

    private fun numberPreference(
        key: String,
        titleRes: Int,
        minimum: Int,
        maximum: Int,
        current: Int,
        changed: (Int) -> Unit,
    ) = SeekBarPreference(requireContext()).apply {
        this.key = key
        title = getString(titleRes)
        min = minimum
        max = maximum
        value = current
        seekBarIncrement = 5
        showSeekBarValue = true
        isPersistent = false
        onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            changed(value as Int)
            true
        }
    }

    private fun update(transform: DisplayPreferences.() -> DisplayPreferences) {
        store.save(store.load().transform())
        (activity as? DisplaySettingsActivity)?.markChanged()
    }

    private companion object {
        const val KEY_INFO_POSITION = "ui-info-position"
        const val KEY_SHOW_CURRENT = "ui-show-current"
        const val KEY_SHOW_NEXT = "ui-show-next"
        const val KEY_INFO_OPACITY = "ui-info-opacity"
        const val KEY_DURATION = "ui-info-duration"
        const val KEY_PANEL_SIDE = "ui-panel-side"
        const val KEY_PANEL_OPACITY = "ui-panel-opacity"
        const val KEY_SHOW_LOGO = "ui-show-logo"
        const val KEY_SHOW_PROGRAM = "ui-show-program"
        const val KEY_SHOW_PROGRESS = "ui-show-progress"
        const val KEY_SHOW_SOURCE = "ui-show-source"
        const val KEY_SUBTITLES = "ui-subtitles"
        const val KEY_SLEEP_TIMER = "playback-sleep-timer"
        const val KEY_LAUNCH_ON_BOOT = "ui-launch-on-boot"
    }
}
