package dev.shiori.android

import dev.shiori.android.corenetwork.CreateLinkRequest
import dev.shiori.android.corenetwork.CreateLinkResponse
import dev.shiori.android.corenetwork.LinkListResponse
import dev.shiori.android.corenetwork.LinkReadFilter
import dev.shiori.android.corenetwork.LinkResponse
import dev.shiori.android.corenetwork.LinkSortOrder
import dev.shiori.android.corenetwork.LinksQuery
import dev.shiori.android.corenetwork.ShioriApiClient
import dev.shiori.android.corenetwork.DeleteLinkResponse
import dev.shiori.android.corenetwork.EmptyTrashResponse
import dev.shiori.android.corenetwork.ShioriApiResult
import dev.shiori.android.corenetwork.UpdateLinkRequest
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksBrowserTest {

    @Test
    fun `browse destinations map to documented link queries`() {
        assertEquals(
            LinksQuery(limit = 20, offset = 40, read = LinkReadFilter.Unread, sort = LinkSortOrder.Newest),
            LinkBrowseDestination.Inbox.toLinksQuery(limit = 20, offset = 40),
        )
        assertEquals(
            LinksQuery(limit = 20, offset = 40, read = LinkReadFilter.Read, sort = LinkSortOrder.Newest),
            LinkBrowseDestination.Archive.toLinksQuery(limit = 20, offset = 40),
        )
        assertEquals(
            LinksQuery(limit = 20, offset = 40, trash = true),
            LinkBrowseDestination.Trash.toLinksQuery(limit = 20, offset = 40),
        )
    }

    @Test
    fun `saved destination parsing falls back to inbox for unexpected values`() {
        assertEquals(LinkBrowseDestination.Archive, parseSavedDestination("Archive"))
        assertEquals(LinkBrowseDestination.Inbox, parseSavedDestination("unexpected"))
        assertEquals(LinkBrowseDestination.Inbox, parseSavedDestination(null))
    }

    @Test
    fun `mergeLinkCards keeps first position while refreshing duplicates`() {
        val existing = listOf(
            LinkCardModel("1", "https://example.com/1", "One", "One", "example.com", null, false, null, "Unread", null, null),
            LinkCardModel("2", "https://example.com/2", "Two", "Two", "example.com", null, false, null, "Unread", null, null),
        )
        val incoming = listOf(
            LinkCardModel("2", "https://example.com/2", "Two updated", "Two updated", "example.com", null, true, null, "Read", null, null),
            LinkCardModel("3", "https://example.com/3", "Three", "Three", "example.com", null, false, null, "Unread", null, null),
        )

        assertEquals(
            listOf(
                LinkCardModel("1", "https://example.com/1", "One", "One", "example.com", null, false, null, "Unread", null, null),
                LinkCardModel("2", "https://example.com/2", "Two updated", "Two updated", "example.com", null, true, null, "Read", null, null),
                LinkCardModel("3", "https://example.com/3", "Three", "Three", "example.com", null, false, null, "Unread", null, null),
            ),
            mergeLinkCards(existing, incoming),
        )
    }

    @Test
    fun `repository uses trash endpoint only for trash destination`() = runTest {
        val client = FakeShioriApiClient()
        val repository = DefaultLinksRepository(clientFactory = ShioriApiClientFactory { client })
        val config = ApiAccessConfig("https://shiori.example.com", "test-api-key")

        val inboxResult = repository.loadLinks(config, LinkBrowseDestination.Inbox, limit = 20, offset = 0)
        val trashResult = repository.loadLinks(config, LinkBrowseDestination.Trash, limit = 20, offset = 40)

        assertSame(client.inboxResult, inboxResult)
        assertSame(client.trashResult, trashResult)
        assertEquals(LinksQuery(limit = 20, offset = 0, read = LinkReadFilter.Unread, sort = LinkSortOrder.Newest), client.lastInboxQuery)
        assertEquals(LinksQuery(limit = 20, offset = 40, trash = true), client.lastTrashQuery)
    }

    @Test
    fun `mergeLinkCards removes duplicate incoming entries while keeping refreshed values`() {
        val existing = listOf(
            LinkCardModel("1", "https://example.com/1", "One", "One", "example.com", null, false, null, "Unread", null, null),
        )
        val incoming = listOf(
            LinkCardModel("2", "https://example.com/2", "Two", "Two", "example.com", null, false, null, "Unread", null, null),
            LinkCardModel("2", "https://example.com/2", "Two refreshed", "Two refreshed", "example.com", null, true, null, "Read", null, null),
        )

        assertEquals(
            listOf(
                LinkCardModel("1", "https://example.com/1", "One", "One", "example.com", null, false, null, "Unread", null, null),
                LinkCardModel("2", "https://example.com/2", "Two refreshed", "Two refreshed", "example.com", null, true, null, "Read", null, null),
            ),
            mergeLinkCards(existing, incoming),
        )
    }

    @Test
    fun `repository forwards create link requests to api client`() = runTest {
        val client = FakeShioriApiClient()
        val repository = DefaultLinksRepository(clientFactory = ShioriApiClientFactory { client })
        val config = ApiAccessConfig("https://shiori.example.com", "test-api-key")
        val request = CreateLinkRequest(
            url = "https://example.com/article",
            title = "Article",
            read = true,
        )

        val result = repository.saveLink(config, request)

        assertSame(client.createResult, result)
        assertEquals(request, client.lastCreateRequest)
    }

    @Test
    fun `saved link destination follows create response contract`() {
        assertEquals(
            LinkBrowseDestination.Inbox,
            CreateLinkResponse(success = true, linkId = "new-link", duplicate = false)
                .toBrowseDestination(CreateLinkRequest(url = "https://example.com/new", read = false)),
        )
        assertEquals(
            LinkBrowseDestination.Archive,
            CreateLinkResponse(success = true, linkId = "read-link", duplicate = false)
                .toBrowseDestination(CreateLinkRequest(url = "https://example.com/read", read = true)),
        )
        assertEquals(
            LinkBrowseDestination.Inbox,
            CreateLinkResponse(success = true, linkId = "duplicate-link", duplicate = true)
                .toBrowseDestination(CreateLinkRequest(url = "https://example.com/duplicate", read = true)),
        )
    }

    @Test
    fun `repository forwards bulk read updates to api client`() = runTest {
        val client = FakeShioriApiClient()
        val repository = DefaultLinksRepository(clientFactory = ShioriApiClientFactory { client })
        val config = ApiAccessConfig("https://shiori.example.com", "test-api-key")

        val result = repository.updateReadState(config, ids = listOf("2", "3"), read = true)

        assertEquals(ShioriApiResult.Success(emptyList<LinkResponse>()), result)
        assertEquals(dev.shiori.android.corenetwork.BulkReadStateRequest(ids = listOf("2", "3"), read = true), client.lastBulkRequest)
    }

    @Test
    fun `repository forwards single link updates to api client`() = runTest {
        val client = FakeShioriApiClient()
        val repository = DefaultLinksRepository(clientFactory = ShioriApiClientFactory { client })
        val config = ApiAccessConfig("https://shiori.example.com", "test-api-key")
        val request = UpdateLinkRequest(title = "Updated title", summary = null, read = true)

        val result = repository.updateLink(config, id = "8", request = request)

        assertSame(client.singleUpdateResult, result)
        assertEquals("8", client.lastUpdatedId)
        assertEquals(request, client.lastUpdateRequest)
    }

    @Test
    fun `repository forwards trash actions to api client`() = runTest {
        val client = FakeShioriApiClient()
        val repository = DefaultLinksRepository(clientFactory = ShioriApiClientFactory { client })
        val config = ApiAccessConfig("https://shiori.example.com", "test-api-key")

        val restoreResult = repository.restoreLink(config, id = "12")
        val deleteResult = repository.deleteLink(config, id = "13")
        val emptyTrashResult = repository.emptyTrash(config)

        assertSame(client.restoreResult, restoreResult)
        assertSame(client.deleteResult, deleteResult)
        assertSame(client.emptyTrashResult, emptyTrashResult)
        assertEquals("12", client.lastRestoredId)
        assertEquals("13", client.lastDeletedId)
        assertTrue(client.emptyTrashCalled)
    }

    @Test
    fun `link save validation accepts only http and https urls with hosts`() {
        assertTrue(isLinkUrlValid("https://example.com/article"))
        assertTrue(isLinkUrlValid("http://localhost:8080/preview"))
        assertEquals("https://example.com/article", normalizeLinkUrl(" https://example.com/article "))

        val invalidCases = listOf(
            "",
            "example.com/article",
            "ftp://example.com/file",
            "https:///missing-host",
        )

        invalidCases.forEach { value ->
            assertEquals(false, isLinkUrlValid(value))
        }
    }

    @Test
    fun `saved link destination follows read state`() {
        assertEquals(LinkBrowseDestination.Inbox, LinkResponse(id = "1", url = "https://example.com", readAt = null).toBrowseDestination())
        assertEquals(LinkBrowseDestination.Inbox, LinkResponse(id = "1", url = "https://example.com").toBrowseDestination())
        assertEquals(LinkBrowseDestination.Archive, LinkResponse(id = "1", url = "https://example.com", readAt = "2026-03-07T10:00:00Z").toBrowseDestination())
    }

    @Test
    fun `shared text extraction finds first supported url`() {
        assertEquals(
            "https://example.com/article",
            extractFirstSupportedUrl(
                listOf(
                    "not a url",
                    "Check this out: https://example.com/article)",
                    "https://ignored.example.com/after",
                ),
            ),
        )
    }

    @Test
    fun `shared text finder ignores unsupported content`() {
        assertEquals(
            null,
            findSupportedUrlInText("shared note without a valid url"),
        )
    }

    @Test
    fun `shared text finder keeps direct supported urls`() {
        assertEquals(
            "https://example.com/from-view",
            findSupportedUrlInText("https://example.com/from-view"),
        )
    }

    private class FakeShioriApiClient : ShioriApiClient {
        val inboxResult = ShioriApiResult.Success(LinkListResponse())
        val trashResult = ShioriApiResult.Success(LinkListResponse())
        val createResult = ShioriApiResult.Success(CreateLinkResponse(success = true, linkId = "9"))
        val updateResult = ShioriApiResult.Success(dev.shiori.android.corenetwork.BulkReadStateResponse(updated = 2))
        val singleUpdateResult = ShioriApiResult.Success(LinkResponse(id = "8", url = "https://example.com/8", title = "Updated title", readAt = "2026-03-07T10:00:00Z"))
        val restoreResult = ShioriApiResult.Success(LinkResponse(id = "12", url = "https://example.com/12", title = "Restored title"))
        val deleteResult = ShioriApiResult.Success(DeleteLinkResponse(linkId = "13", message = "Link deleted"))
        val emptyTrashResult = ShioriApiResult.Success(EmptyTrashResponse(deleted = 3, message = "Trash emptied"))
        var lastInboxQuery: LinksQuery? = null
        var lastTrashQuery: LinksQuery? = null
        var lastCreateRequest: CreateLinkRequest? = null
        var lastBulkRequest: dev.shiori.android.corenetwork.BulkReadStateRequest? = null
        var lastUpdatedId: String? = null
        var lastUpdateRequest: UpdateLinkRequest? = null
        var lastRestoredId: String? = null
        var lastDeletedId: String? = null
        var emptyTrashCalled: Boolean = false

        override suspend fun getLinks(query: LinksQuery): ShioriApiResult<LinkListResponse> {
            lastInboxQuery = query
            return inboxResult
        }

        override suspend fun getTrashLinks(query: LinksQuery): ShioriApiResult<LinkListResponse> {
            lastTrashQuery = query
            return trashResult
        }

        override suspend fun createLink(request: CreateLinkRequest): ShioriApiResult<CreateLinkResponse> {
            lastCreateRequest = request
            return createResult
        }

        override suspend fun updateReadState(request: dev.shiori.android.corenetwork.BulkReadStateRequest): ShioriApiResult<dev.shiori.android.corenetwork.BulkReadStateResponse> {
            lastBulkRequest = request
            return updateResult
        }

        override suspend fun updateLink(id: String, request: dev.shiori.android.corenetwork.UpdateLinkRequest): ShioriApiResult<LinkResponse> {
            lastUpdatedId = id
            lastUpdateRequest = request
            return singleUpdateResult
        }

        override suspend fun restoreLink(id: String): ShioriApiResult<LinkResponse> {
            lastRestoredId = id
            return restoreResult
        }

        override suspend fun emptyTrash(): ShioriApiResult<EmptyTrashResponse> {
            emptyTrashCalled = true
            return emptyTrashResult
        }

        override suspend fun deleteLink(id: String): ShioriApiResult<DeleteLinkResponse> {
            lastDeletedId = id
            return deleteResult
        }
    }
}
