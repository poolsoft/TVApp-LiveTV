package com.tvapp.livetv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TifProviderDataInspectorTest {
    @Test
    fun extractsKnownVendorFieldsFromTextBlob() {
        val result = TifProviderDataInspector.inspect(
            "frequency=12345000; symbolRate:27500; polarization=H; serviceKey=42"
                .toByteArray(),
        ).toMap()

        assertEquals("12345000", result["internal_provider_data.frequency"])
        assertEquals("27500", result["internal_provider_data.symbolRate"])
        assertEquals("H", result["internal_provider_data.polarization"])
        assertEquals("42", result["internal_provider_data.serviceKey"])
        assertTrue(result.getValue("internal_provider_data.hex").isNotBlank())
    }

    @Test
    fun extractsKnownVendorFieldsFromUtf16Blob() {
        val result = TifProviderDataInspector.inspect(
            "satelliteId=7\u0000lnbId=2".toByteArray(Charsets.UTF_16LE),
        ).toMap()

        assertEquals("7", result["internal_provider_data.satelliteId"])
        assertEquals("2", result["internal_provider_data.lnbId"])
    }
}
