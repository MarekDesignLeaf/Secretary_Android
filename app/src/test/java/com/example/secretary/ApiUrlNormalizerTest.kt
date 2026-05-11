package com.example.secretary

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiUrlNormalizerTest {
    @Test
    fun normalizeAddsRequiredTrailingSlash() {
        assertEquals(
            "https://example.com/api/",
            ApiUrlNormalizer.normalize(" https://example.com/api ")
        )
    }

    @Test
    fun normalizeKeepsExistingTrailingSlash() {
        assertEquals(
            "https://example.com/api/",
            ApiUrlNormalizer.normalize("https://example.com/api/")
        )
    }

    @Test
    fun resolveAvoidsMissingOrDoubleSlash() {
        assertEquals(
            "https://example.com/auth/refresh",
            ApiUrlNormalizer.resolve("https://example.com", "/auth/refresh")
        )
    }
}
