package dev.shiori.android

import dev.shiori.android.corenetwork.LinkListResponse
import dev.shiori.android.corenetwork.LinksQuery
import dev.shiori.android.corenetwork.ShioriApiClient
import dev.shiori.android.corenetwork.ShioriApiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class LinksBrowserTest {

    @Test
    fun `browse destinations map to documented link queries`() {
        assertEquals(
            LinksQuery(limit = 20, offset = 40, read = false, sort = "created_at"),
            LinkBrowseDestination.Inbox.toLinksQuery(limit = 20, offset = 40),
        )
        assertEquals(
            LinksQuery(limit = 20, offset = 40, read = true, sort = "created_at"),
            LinkBrowseDestination.Archive.toLinksQuery(limit = 20, offset = 40),
        )
        assertEquals(
            LinksQuery(limit = 20, offset = 40, sort = "updated_at", trash = true),
            LinkBrowseDestination.Trash.toLinksQuery(limit = 20, offset = 40),
        )
    }

    @Test
    fun `mergeLinkCards keeps first position while refreshing duplicates`() {
        val existing = listOf(
            LinkCardModel(1, "One", "example.com", null, null, "Unread", null, null),
            LinkCardModel(2, "Two", "example.com", null, null, "Unread", null, null),
        )
        val incoming = listOf(
            LinkCardModel(2, "Two updated", "example.com", null, null, "Read", null, null),
            LinkCardModel(3, "Three", "example.com", null, null, "Unread", null, null),
        )

        assertEquals(
            listOf(
                LinkCardModel(1, "One", "example.com", null, null, "Unread", null, null),
                LinkCardModel(2, "Two updated", "example.com", null, null, "Read", null, null),
                LinkCardModel(3, "Three", "example.com", null, null, "Unread", null, null),
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
        assertEquals(LinksQuery(limit = 20, offset = 0, read = false, sort = "created_at"), client.lastInboxQuery)
        assertEquals(LinksQuery(limit = 20, offset = 40, sort = "updated_at", trash = true), client.lastTrashQuery)
    }

    private class FakeShioriApiClient : ShioriApiClient {
        val inboxResult = ShioriApiResult.Success(LinkListResponse())
        val trashResult = ShioriApiResult.Success(LinkListResponse())
        var lastInboxQuery: LinksQuery? = null
        var lastTrashQuery: LinksQuery? = null

        override suspend fun getLinks(query: LinksQuery): ShioriApiResult<LinkListResponse> {
            lastInboxQuery = query
            return inboxResult
        }

        override suspend fun getTrashLinks(query: LinksQuery): ShioriApiResult<LinkListResponse> {
            lastTrashQuery = query
            return trashResult
        }

        override suspend fun createLink(request: dev.shiori.android.corenetwork.CreateLinkRequest) = throw UnsupportedOperationException()

        override suspend fun updateReadState(request: dev.shiori.android.corenetwork.BulkReadStateRequest) = throw UnsupportedOperationException()

        override suspend fun updateLink(id: Long, request: dev.shiori.android.corenetwork.UpdateLinkRequest) = throw UnsupportedOperationException()

        override suspend fun restoreLink(id: Long) = throw UnsupportedOperationException()

        override suspend fun emptyTrash() = throw UnsupportedOperationException()

        override suspend fun deleteLink(id: Long) = throw UnsupportedOperationException()
    }
}
