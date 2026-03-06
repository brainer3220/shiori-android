package dev.shiori.android

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DefaultApiConnectionCheckerTest {
    private lateinit var server: MockWebServer
    private lateinit var checker: DefaultApiConnectionChecker

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        checker = DefaultApiConnectionChecker()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `returns success when the api responds`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("{\"links\":[],\"has_next\":false}")
                .addHeader("Content-Type", "application/json"),
        )

        val result = checker.validate(server.url("/").toString(), "test-api-key")

        assertEquals(ApiValidationStatus.Success, result)
        val request = server.takeRequest(1, TimeUnit.SECONDS)
        requireNotNull(request)
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"))
        assertEquals("/api/links?limit=1", request.path)
    }

    @Test
    fun `maps unauthorized responses distinctly`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val result = checker.validate(server.url("/").toString(), "test-api-key")

        assertEquals(ApiValidationStatus.Unauthorized, result)
    }

    @Test
    fun `maps non auth failures to generic failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        val result = checker.validate(server.url("/").toString(), "test-api-key")

        assertEquals(ApiValidationStatus.Failure, result)
    }
}
