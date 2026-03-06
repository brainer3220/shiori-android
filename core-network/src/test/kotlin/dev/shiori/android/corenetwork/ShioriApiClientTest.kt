package dev.shiori.android.corenetwork

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ShioriApiClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: ShioriApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = createShioriApiClient(
            baseUrl = server.url("/").toString(),
            apiKeyProvider = ApiKeyProvider { "test-api-key" },
            okHttpClient = OkHttpClient.Builder()
                .readTimeout(5, TimeUnit.SECONDS)
                .build(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `getLinks parses documented string ids and optional null fields`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "success": true,
                  "links": [
                    {
                      "id": "550e8400-e29b-41d4-a716-446655440000",
                      "url": "https://example.com/article",
                      "title": "Example Article",
                      "domain": "example.com",
                      "summary": "A brief AI-generated summary of the article.",
                      "favicon_url": null,
                      "image_url": null,
                      "status": "created",
                      "source": "api",
                      "created_at": "2026-02-21T12:00:00.000Z",
                      "updated_at": "2026-02-21T12:00:00.000Z",
                      "read_at": null,
                      "hn_url": null,
                      "file_storage_path": null,
                      "file_type": null,
                      "file_mime_type": null,
                      "notion_page_id": null
                    }
                  ],
                  "total": 142
                }
                """.trimIndent(),
            ),
        )

        val result = client.getLinks(
            LinksQuery(
                limit = 20,
                offset = 40,
                read = LinkReadFilter.Unread,
                sort = LinkSortOrder.Newest,
            ),
        )

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"))
        assertEquals("/api/links?limit=20&offset=40&read=unread&sort=newest", request.path)
        assertTrue(result is ShioriApiResult.Success)
        val response = (result as ShioriApiResult.Success).value
        assertEquals(142, response.total)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", response.links.single().id)
        assertEquals(null, response.links.single().faviconUrl)
        assertEquals(null, response.links.single().readAt)
    }

    @Test
    fun `getLinks accepts missing undocumented fields and non numeric ids`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "links": [
                    {
                      "id": "not-a-number",
                      "url": "https://example.com/missing-fields"
                    }
                  ],
                  "total": 1
                }
                """.trimIndent(),
            ),
        )

        val result = client.getLinks()

        assertTrue(result is ShioriApiResult.Success)
        val link = (result as ShioriApiResult.Success).value.links.single()
        assertEquals("not-a-number", link.id)
        assertEquals(false, link.read)
    }

    @Test
    fun `createLink posts request body and parses duplicate response`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "success": true,
                  "linkId": "550e8400-e29b-41d4-a716-446655440000",
                  "duplicate": true
                }
                """.trimIndent(),
            ),
        )

        val result = client.createLink(
            CreateLinkRequest(
                url = "https://example.com/article",
                title = "Article",
                read = false,
            ),
        )

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("Bearer test-api-key", request.getHeader("Authorization"))
        assertEquals("/api/links", request.path)
        assertTrue(body.contains("\"url\":\"https://example.com/article\""))
        assertTrue(body.contains("\"title\":\"Article\""))
        assertTrue(body.contains("\"read\":false"))
        assertTrue(!body.contains("summary"))
        assertTrue(result is ShioriApiResult.Success)
        val response = (result as ShioriApiResult.Success).value
        assertTrue(response.success)
        assertTrue(response.duplicate)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", response.linkId)
    }

    @Test
    fun `createLink omits optional fields when title and read are absent`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "success": true,
                  "linkId": "fresh-link"
                }
                """.trimIndent(),
            ),
        )

        val result = client.createLink(
            CreateLinkRequest(
                url = "https://example.com/minimal",
                title = null,
                read = null,
            ),
        )

        val request = server.takeRequest()
        val body = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertTrue(body.contains("\"url\":\"https://example.com/minimal\""))
        assertTrue(!body.contains("title"))
        assertTrue(!body.contains("read"))
        assertTrue(result is ShioriApiResult.Success)
        assertEquals("fresh-link", (result as ShioriApiResult.Success).value.linkId)
    }

    @Test
    fun `bulk update and restore use documented patch endpoints with string ids`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{" +
                    "\"success\":true," +
                    "\"updated\":1" +
                    "}"
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{" +
                    "\"id\":\"link-2\"," +
                    "\"url\":\"https://example.com/2\"" +
                    "}"
            ),
        )

        val bulkResult = client.updateReadState(BulkReadStateRequest(ids = listOf("link-2"), read = true))
        val restoreResult = client.restoreLink("link-2")

        val bulkRequest = server.takeRequest()
        val restoreRequest = server.takeRequest()

        assertEquals("PATCH", bulkRequest.method)
        assertEquals("/api/links", bulkRequest.path)
        assertTrue(bulkRequest.body.readUtf8().contains("\"ids\":[\"link-2\"]"))
        assertEquals("PATCH", restoreRequest.method)
        assertEquals("/api/links/link-2", restoreRequest.path)
        assertTrue(restoreRequest.body.readUtf8().contains("\"restore\":true"))
        assertTrue(bulkResult is ShioriApiResult.Success)
        assertTrue(restoreResult is ShioriApiResult.Success)
        assertEquals(1, (bulkResult as ShioriApiResult.Success).value.updated)
    }

    @Test
    fun `single link update sends metadata patch body`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{" +
                    "\"id\":\"link-12\"," +
                    "\"url\":\"https://example.com/12\"," +
                    "\"title\":\"Edited title\"," +
                    "\"summary\":null," +
                    "\"read_at\":\"2026-03-07T12:00:00Z\"" +
                    "}"
            ),
        )

        val result = client.updateLink(
            id = "link-12",
            request = UpdateLinkRequest(
                title = "Edited title",
                summary = null,
                clearSummary = true,
                read = true,
            ),
        )

        val request = server.takeRequest()
        val body = request.body.readUtf8()

        assertEquals("PATCH", request.method)
        assertEquals("/api/links/link-12", request.path)
        assertTrue(body.contains("\"title\":\"Edited title\""))
        assertTrue(body, body.contains("\"summary\":null"))
        assertTrue(result is ShioriApiResult.Success)
        assertEquals("link-12", (result as ShioriApiResult.Success).value.id)
    }

    @Test
    fun `trash endpoints send correct paths and methods`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                "{" +
                    "\"links\":[{" +
                    "\"id\":\"link-9\"," +
                    "\"url\":\"https://example.com/trashed\"" +
                    "}]," +
                    "\"total\":1" +
                    "}"
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"success\":true,\"deleted\":12}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{\"success\":true,\"message\":\"Link deleted successfully\",\"linkId\":\"link-9\"}"))

        val trashResult = client.getTrashLinks()
        val emptyTrashResult = client.emptyTrash()
        val deleteLinkResult = client.deleteLink("link-9")

        val trashRequest = server.takeRequest()
        val emptyTrashRequest = server.takeRequest()
        val deleteLinkRequest = server.takeRequest()

        assertEquals("/api/links?trash=true", trashRequest.path)
        assertEquals("DELETE", emptyTrashRequest.method)
        assertEquals("/api/links", emptyTrashRequest.path)
        assertEquals("DELETE", deleteLinkRequest.method)
        assertEquals("/api/links/link-9", deleteLinkRequest.path)
        assertTrue(trashResult is ShioriApiResult.Success)
        assertTrue(emptyTrashResult is ShioriApiResult.Success)
        assertTrue(deleteLinkResult is ShioriApiResult.Success)
        assertEquals(12, (emptyTrashResult as ShioriApiResult.Success).value.deleted)
        assertEquals("link-9", (deleteLinkResult as ShioriApiResult.Success).value.linkId)
    }

    @Test
    fun `createLink http error codes map to domain errors`() = runTest {
        val cases = listOf(
            400 to ShioriApiError.Validation,
            401 to ShioriApiError.Unauthorized,
            404 to ShioriApiError.NotFound,
            409 to ShioriApiError.Conflict,
            429 to ShioriApiError.RateLimited,
            500 to ShioriApiError.Server(500),
        )

        cases.forEach { (statusCode, expectedError) ->
            server.enqueue(MockResponse().setResponseCode(statusCode))
            val result = client.createLink(CreateLinkRequest(url = "https://example.com/$statusCode"))
            assertTrue(result is ShioriApiResult.Failure)
            assertEquals(expectedError, (result as ShioriApiResult.Failure).error)
            server.takeRequest()
        }
    }

    @Test
    fun `createLink network failures map to network errors`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))

        val result = client.createLink(CreateLinkRequest(url = "https://example.com/network"))

        assertTrue(result is ShioriApiResult.Failure)
        val error = (result as ShioriApiResult.Failure).error
        assertTrue(error is ShioriApiError.Network)
        assertTrue((error as ShioriApiError.Network).cause is java.io.IOException)
    }
}
