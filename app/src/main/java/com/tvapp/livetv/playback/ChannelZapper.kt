package com.tvapp.livetv.playback

import androidx.lifecycle.LifecycleCoroutineScope
import com.tvapp.livetv.R
import com.tvapp.livetv.databinding.ActivityMainBinding
import com.tvapp.livetv.model.LiveChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ChannelZapper(
    private val binding: ActivityMainBinding,
    private val scope: LifecycleCoroutineScope,
    private val showInfoBar: () -> Unit,
    private val getNavigationChannels: () -> List<LiveChannel>,
    private val getCurrentChannel: () -> LiveChannel?,
    private val selectChannel: (LiveChannel, Boolean) -> Unit,
    private val shouldRecordNavigationHistory: () -> Boolean,
    private val isMultiViewAdding: () -> Boolean,
    private val toggleMultiViewChannel: (LiveChannel) -> Unit,
    private val isChannelPanelVisible: () -> Boolean,
    private val hideChannelPanel: () -> Unit,
    private val focusCurrentListChannel: () -> Unit,
    private val pageChannelList: (Int) -> Unit,
    private val isIptvLibraryContent: () -> Boolean,
    private val openIptvLibraryNumber: (Int?) -> Unit,
    private val isVodMode: () -> Boolean,
    private val isCurrentPlaybackUsesIptvLibrary: () -> Boolean,
    private val getHistoryKeys: () -> List<String>,
) {
    companion object {
        const val MAX_CHANNEL_DIGITS = 5
        const val NUMBER_ENTRY_TIMEOUT_MS = 1_500L
    }

    var numberInput: String = ""
        set(value) {
            field = value
            if (value.isEmpty()) {
                numberInputJob?.cancel()
            }
        }

    private var numberInputJob: Job? = null

    fun appendChannelDigit(digit: Int) {
        if (numberInput.length >= MAX_CHANNEL_DIGITS) numberInput = ""
        numberInput += digit
        binding.nowNumber.text = numberInput
        binding.nowChannel.setText(R.string.channel_number_entry)
        showInfoBar()
        numberInputJob?.cancel()
        numberInputJob = scope.launch {
            delay(NUMBER_ENTRY_TIMEOUT_MS)
            commitChannelNumber()
        }
    }

    fun commitChannelNumber() {
        val entered = numberInput
        numberInput = ""
        numberInputJob?.cancel()
        if (entered.isBlank()) return
        if (isIptvLibraryContent()) {
            openIptvLibraryNumber(entered.toIntOrNull())
            return
        }
        ChannelNavigator.byNumber(getNavigationChannels(), entered)?.let { channel ->
            if (isMultiViewAdding()) {
                toggleMultiViewChannel(channel)
                return@let
            }
            selectChannel(channel, shouldRecordNavigationHistory())
            if (isChannelPanelVisible()) hideChannelPanel()
        } ?: run { binding.nowChannel.setText(R.string.channel_not_found) }
    }

    fun clearNumberInput() {
        numberInputJob?.cancel()
        numberInput = ""
    }

    fun openPreviousChannel() {
        if (isCurrentPlaybackUsesIptvLibrary()) {
            zap(-1)
            return
        }
        ChannelNavigator.previousDistinct(
            getNavigationChannels(),
            getHistoryKeys(),
            getCurrentChannel()?.sourceKey,
        )?.let { selectChannel(it, true) }
    }

    fun zap(offset: Int) {
        if (isVodMode()) return
        if (isMultiViewAdding() && isChannelPanelVisible()) {
            pageChannelList(offset)
            return
        }
        ChannelNavigator.adjacent(getNavigationChannels(), getCurrentChannel()?.sourceKey, offset)
            ?.let { channel ->
                selectChannel(channel, shouldRecordNavigationHistory())
                if (isChannelPanelVisible()) {
                    focusCurrentListChannel()
                }
            }
    }

    fun cancel() {
        numberInputJob?.cancel()
    }
}
