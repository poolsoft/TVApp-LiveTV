package com.tvapp.livetv.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AlphabetJumpTest {
    @Test
    fun groupsExistingInitialsWithoutChangingChannelOrder() {
        assertEquals(listOf(AlphabetTarget("A", 7), AlphabetTarget("T", 0), AlphabetTarget("#", 5)),
            AlphabetJump.targets(listOf("TRT" to 0, " trt 2" to 3, "123" to 5, "ATV" to 7, "!" to 8)))
    }

    @Test
    fun preservesTurkishInitialsAndHandlesEmptyNames() {
        assertEquals(listOf("Ç", "I", "İ", "Ş", "#"),
            AlphabetJump.targets(listOf("şov" to 0, "iz" to 1, "ışık" to 2, "Çay" to 3, "" to 4)).map { it.letter })
        assertEquals(emptyList<AlphabetTarget>(), AlphabetJump.targets(emptyList()))
    }
}
