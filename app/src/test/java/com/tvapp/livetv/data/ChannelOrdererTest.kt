package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChannelOrdererTest {
    @Test
    fun moveToPosition_keepsSelectedRelativeOrderAndMakesNumbersConsecutive() {
        val order = (1..25).map(Int::toString)

        val result = ChannelOrderer.moveToPosition(
            order = order,
            selectedKeys = setOf("12", "15", "23"),
            startNumber = 2,
        )

        assertEquals(listOf("12", "15", "23"), result.subList(1, 4))
        assertEquals(25, result.distinct().size)
    }

    @Test
    fun moveToPosition_movesSingleChannelToRequestedNumber() {
        val result = ChannelOrderer.moveToPosition(
            order = listOf("a", "b", "c", "d"),
            selectedKeys = setOf("d"),
            startNumber = 2,
        )

        assertEquals(listOf("a", "d", "b", "c"), result)
    }
}
