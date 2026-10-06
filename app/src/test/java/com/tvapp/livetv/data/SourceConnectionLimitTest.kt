package com.tvapp.livetv.data

import com.tvapp.livetv.data.local.IptvSourceEntity
import org.junit.Assert.*
import org.junit.Test

class SourceConnectionLimitTest {
    @Test fun unknownLimitsAreNotGuessedAndProviderLimitCannotBeRaised() {
        val source = IptvSourceEntity(name = "Fixture", location = "fixture", kind = "XTREAM")
        assertNull(source.connectionLimit())
        assertEquals(2, source.copy(maximumConnections = 2).connectionLimit())
        assertEquals(1, source.copy(maximumConnections = 4, reportedMaximumConnections = 1).connectionLimit())
        assertEquals(2, source.copy(maximumConnections = 2, reportedMaximumConnections = 4).connectionLimit())
        assertNull(source.copy(maximumConnections = 0, reportedMaximumConnections = -1).connectionLimit())
    }
}
