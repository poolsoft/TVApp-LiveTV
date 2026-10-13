package com.tvapp.livetv.ui

import com.tvapp.livetv.model.LiveChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorChannelSelectionTest {
    private fun channel(key: String, source: LiveChannel.Source) = LiveChannel(
        id = 1, sourceKey = key, inputId = "", displayNumber = "1", displayName = key,
        uri = "", source = source,
    )
    private val channels = listOf(
        channel("tif", LiveChannel.Source.TIF),
        channel("ip1", LiveChannel.Source.IPTV),
        channel("ip2", LiveChannel.Source.IPTV),
    )

    @Test fun mixedSelectionOnlyRemovesIptv() {
        assertEquals(listOf("ip1"), selectedIptvKeys(channels, setOf("tif", "ip1")))
    }

    @Test fun staleKeysAndUnselectedChannelsAreIgnored() {
        assertEquals(listOf("ip2"), selectedIptvKeys(channels, setOf("missing", "ip2")))
    }

    @Test fun tunerOnlyOrEmptySelectionCannotRemoveChannels() {
        assertEquals(emptyList<String>(), selectedIptvKeys(channels, setOf("tif")))
        assertEquals(emptyList<String>(), selectedIptvKeys(channels, emptySet()))
    }
}
