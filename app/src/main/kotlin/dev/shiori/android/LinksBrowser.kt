package dev.shiori.android

import dev.shiori.android.corenetwork.ApiKeyProvider
import dev.shiori.android.corenetwork.LinkListResponse
import dev.shiori.android.corenetwork.LinkResponse
import dev.shiori.android.corenetwork.LinksQuery
import dev.shiori.android.corenetwork.ShioriApiClient
import dev.shiori.android.corenetwork.ShioriApiError
import dev.shiori.android.corenetwork.ShioriApiResult
import dev.shiori.android.corenetwork.createShioriApiClient
import java.net.URI
import java.util.Locale

enum class LinkBrowseDestination {
    Inbox,
    Archive,
    Trash,
}

data class LinkCardModel(
    val id: Long,
    val title: String,
    val domain: String,
    val summary: String?,
    val status: String?,
    val readState: String?,
    val createdAt: String?,
    val updatedAt: String?,
)

data class LinkListUiState(
    val items: List<LinkCardModel> = emptyList(),
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasLoadedOnce: Boolean = false,
    val endReached: Boolean = false,
    val nextOffset: Int = 0,
    val total: Int? = null,
    val message: String? = null,
)

fun interface ShioriApiClientFactory {
    fun create(config: ApiAccessConfig): ShioriApiClient
}

class DefaultShioriApiClientFactory : ShioriApiClientFactory {
    override fun create(config: ApiAccessConfig): ShioriApiClient = createShioriApiClient(
        baseUrl = ApiAccessInputValidator.normalizeServerUrl(config.serverUrl),
        apiKeyProvider = ApiKeyProvider { ApiAccessInputValidator.normalizeApiKey(config.apiKey) },
    )
}

interface LinksRepository {
    suspend fun loadLinks(
        config: ApiAccessConfig,
        destination: LinkBrowseDestination,
        limit: Int,
        offset: Int,
    ): ShioriApiResult<LinkListResponse>
}

class DefaultLinksRepository(
    private val clientFactory: ShioriApiClientFactory = DefaultShioriApiClientFactory(),
) : LinksRepository {
    override suspend fun loadLinks(
        config: ApiAccessConfig,
        destination: LinkBrowseDestination,
        limit: Int,
        offset: Int,
    ): ShioriApiResult<LinkListResponse> {
        val client = clientFactory.create(config)
        val query = destination.toLinksQuery(limit = limit, offset = offset)
        return if (destination == LinkBrowseDestination.Trash) {
            client.getTrashLinks(query)
        } else {
            client.getLinks(query)
        }
    }
}

internal fun LinkBrowseDestination.toLinksQuery(limit: Int, offset: Int): LinksQuery = when (this) {
    LinkBrowseDestination.Inbox -> LinksQuery(
        limit = limit,
        offset = offset,
        read = false,
        sort = "created_at",
    )

    LinkBrowseDestination.Archive -> LinksQuery(
        limit = limit,
        offset = offset,
        read = true,
        sort = "created_at",
    )

    LinkBrowseDestination.Trash -> LinksQuery(
        limit = limit,
        offset = offset,
        sort = "updated_at",
        trash = true,
    )
}

internal fun mergeLinkCards(
    existing: List<LinkCardModel>,
    incoming: List<LinkCardModel>,
): List<LinkCardModel> {
    val merged = LinkedHashMap<Long, LinkCardModel>(existing.size + incoming.size)
    existing.forEach { merged[it.id] = it }
    incoming.forEach { merged[it.id] = it }
    return merged.values.toList()
}

internal fun LinkResponse.toCardModel(): LinkCardModel = LinkCardModel(
    id = id,
    title = title?.takeIf { it.isNotBlank() } ?: url,
    domain = domain?.takeIf { it.isNotBlank() } ?: url.toDomainFallback(),
    summary = summary?.takeIf { it.isNotBlank() },
    status = status?.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(Locale.getDefault())
        } else {
            char.toString()
        }
    },
    readState = read?.let { if (it) "Read" else "Unread" },
    createdAt = createdAt.toTimestampLabel("Saved"),
    updatedAt = updatedAt.toTimestampLabel("Updated"),
)

internal fun ShioriApiError.toBrowseMessage(): String = when (this) {
    ShioriApiError.Validation -> "Shiori rejected this list request. Check your filters and try again."
    ShioriApiError.Unauthorized -> "Your API key is no longer authorized. Update it in API access."
    ShioriApiError.NotFound -> "Shiori could not find this list endpoint. Check the server URL."
    ShioriApiError.Conflict -> "Shiori is still processing one of these links. Try again in a moment."
    ShioriApiError.RateLimited -> "Shiori rate limited this request. Wait a moment before loading more."
    is ShioriApiError.Server -> "Shiori returned an unexpected server error (${statusCode})."
    is ShioriApiError.Network -> "Could not reach your Shiori server. Check the connection and try again."
    is ShioriApiError.Unknown -> "An unexpected error interrupted link loading. Try again."
}

private fun String?.toTimestampLabel(prefix: String): String? {
    val value = this?.takeIf { it.isNotBlank() } ?: return null
    val compact = value
        .replace('T', ' ')
        .removeSuffix("Z")
        .substringBefore('.')
    return "$prefix $compact"
}

private fun String.toDomainFallback(): String {
    val uri = runCatching { URI(this) }.getOrNull()
    return uri?.host?.takeIf { it.isNotBlank() } ?: this
}
