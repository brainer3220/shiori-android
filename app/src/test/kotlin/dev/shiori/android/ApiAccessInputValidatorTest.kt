package dev.shiori.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiAccessInputValidatorTest {
    @Test
    fun `accepts production base url and local development hosts`() {
        assertTrue(ApiAccessInputValidator.isServerUrlValid("https://www.shiori.sh"))
        assertTrue(ApiAccessInputValidator.isServerUrlValid("https://shiori.example.com"))
        assertTrue(ApiAccessInputValidator.isServerUrlValid("http://10.0.2.2:8080"))
        assertTrue(ApiAccessInputValidator.isServerUrlValid("http://10.0.3.2:8080"))
        assertTrue(ApiAccessInputValidator.isServerUrlValid("http://localhost:8080/"))
        assertTrue(ApiAccessInputValidator.isServerUrlValid("http://192.168.1.50:8080"))
        assertTrue(ApiAccessInputValidator.isServerUrlValid("http://shiori.local:8080"))
    }

    @Test
    fun `rejects insecure remote urls and malformed values`() {
        assertFalse(ApiAccessInputValidator.isServerUrlValid("http://shiori.example.com"))
        assertFalse(ApiAccessInputValidator.isServerUrlValid("not a url"))
        assertFalse(ApiAccessInputValidator.isServerUrlValid(""))
    }

    @Test
    fun `accepts and normalizes valid looking api keys`() {
        assertTrue(ApiAccessInputValidator.isApiKeyValidLooking("secretKey42"))
        assertFalse(ApiAccessInputValidator.isApiKeyValidLooking("short"))
        assertFalse(ApiAccessInputValidator.isApiKeyValidLooking("contains space"))
        assertEquals("secretKey42", ApiAccessInputValidator.normalizeApiKey("  secretKey42  "))
    }
}
