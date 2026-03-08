package dev.shiori.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiAccessInputValidatorTest {
    @Test
    fun `normalizes api keys by trimming surrounding whitespace`() {
        assertEquals("secretKey42", ApiAccessInputValidator.normalizeApiKey("  secretKey42  "))
    }

    @Test
    fun `accepts only valid looking api keys`() {
        assertTrue(ApiAccessInputValidator.isApiKeyValidLooking("secretKey42"))
        assertFalse(ApiAccessInputValidator.isApiKeyValidLooking("short"))
        assertFalse(ApiAccessInputValidator.isApiKeyValidLooking("contains space"))
        assertFalse(ApiAccessInputValidator.isApiKeyValidLooking(""))
    }
}
