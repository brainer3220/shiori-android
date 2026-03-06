package dev.shiori.android

import android.content.Intent
import dev.shiori.android.corenetwork.ApiKeyProvider
import dev.shiori.android.corenetwork.CreateLinkRequest
import dev.shiori.android.corenetwork.CreateLinkResponse
import dev.shiori.android.corenetwork.DeleteLinkResponse
import dev.shiori.android.corenetwork.EmptyTrashResponse
import dev.shiori.android.corenetwork.LinkListResponse
import dev.shiori.android.corenetwork.LinkMutationResponse
import dev.shiori.android.corenetwork.LinkReadFilter
import dev.shiori.android.corenetwork.LinkResponse
import dev.shiori.android.corenetwork.ShioriApiError
import dev.shiori.android.corenetwork.ShioriApiClient
import dev.shiori.android.corenetwork.ShioriApiResult
import dev.shiori.android.corenetwork.LinkSortOrder
import dev.shiori.android.corenetwork.LinksQuery
import dev.shiori.android.corenetwork.createShioriApiClient
import dev.shiori.android.corenetwork.read
import java.net.URI
import java.util.Locale

enum class LinkBrowseDestination {
    Inbox,
    Archive,
    Trash,
}

data class LinkCardModel(
    val id: String,
    val url: String,
    val title: String,
    val rawTitle: String?,
    val domain: String,
    val summary: String?,
    val read: Boolean?,
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

internal sealed interface IncomingLinkIntent {
    object None : IncomingLinkIntent

    data class Supported(val url: String) : IncomingLinkIntent

    object Unsupported : IncomingLinkIntent
}

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

    suspend fun saveLink(
        config: ApiAccessConfig,
        request: CreateLinkRequest,
    ): ShioriApiResult<CreateLinkResponse>

    suspend fun updateReadState(
        config: ApiAccessConfig,
        ids: List<String>,
        read: Boolean,
    ): ShioriApiResult<List<LinkResponse>>

    suspend fun updateLink(
        config: ApiAccessConfig,
        id: String,
        request: dev.shiori.android.corenetwork.UpdateLinkRequest,
    ): ShioriApiResult<LinkMutationResponse>

    suspend fun restoreLink(
        config: ApiAccessConfig,
        id: String,
    ): ShioriApiResult<LinkMutationResponse>

    suspend fun deleteLink(
        config: ApiAccessConfig,
        id: String,
    ): ShioriApiResult<DeleteLinkResponse>

    suspend fun emptyTrash(config: ApiAccessConfig): ShioriApiResult<EmptyTrashResponse>
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

    override suspend fun saveLink(
        config: ApiAccessConfig,
        request: CreateLinkRequest,
    ): ShioriApiResult<CreateLinkResponse> = clientFactory.create(config).createLink(request)

    override suspend fun updateReadState(
        config: ApiAccessConfig,
        ids: List<String>,
        read: Boolean,
    ): ShioriApiResult<List<LinkResponse>> {
        val result = clientFactory.create(config).updateReadState(dev.shiori.android.corenetwork.BulkReadStateRequest(ids = ids, read = read))
        return when (result) {
            is ShioriApiResult.Success -> ShioriApiResult.Success(emptyList())
            is ShioriApiResult.Failure -> result
        }
    }

    override suspend fun updateLink(
        config: ApiAccessConfig,
        id: String,
        request: dev.shiori.android.corenetwork.UpdateLinkRequest,
    ): ShioriApiResult<LinkMutationResponse> = clientFactory.create(config).updateLink(id, request)

    override suspend fun restoreLink(
        config: ApiAccessConfig,
        id: String,
    ): ShioriApiResult<LinkMutationResponse> = clientFactory.create(config).restoreLink(id)

    override suspend fun deleteLink(
        config: ApiAccessConfig,
        id: String,
    ): ShioriApiResult<DeleteLinkResponse> = clientFactory.create(config).deleteLink(id)

    override suspend fun emptyTrash(config: ApiAccessConfig): ShioriApiResult<EmptyTrashResponse> =
        clientFactory.create(config).emptyTrash()
}

internal fun LinkBrowseDestination.toLinksQuery(limit: Int, offset: Int): LinksQuery = when (this) {
    LinkBrowseDestination.Inbox -> LinksQuery(
        limit = limit,
        offset = offset,
        read = LinkReadFilter.Unread,
        sort = LinkSortOrder.Newest,
    )

    LinkBrowseDestination.Archive -> LinksQuery(
        limit = limit,
        offset = offset,
        read = LinkReadFilter.Read,
        sort = LinkSortOrder.Newest,
    )

    LinkBrowseDestination.Trash -> LinksQuery(
        limit = limit,
        offset = offset,
        trash = true,
    )
}

internal fun parseSavedDestination(rawValue: String?): LinkBrowseDestination =
    LinkBrowseDestination.values().firstOrNull { it.name == rawValue } ?: LinkBrowseDestination.Inbox

internal fun mergeLinkCards(
    existing: List<LinkCardModel>,
    incoming: List<LinkCardModel>,
): List<LinkCardModel> {
    val merged = LinkedHashMap<String, LinkCardModel>(existing.size + incoming.size)
    existing.forEach { merged[it.id] = it }
    incoming.forEach { merged[it.id] = it }
    return merged.values.toList()
}

internal fun LinkResponse.toCardModel(): LinkCardModel = LinkCardModel(
    id = id,
    url = url,
    title = title?.takeIf { it.isNotBlank() } ?: url,
    rawTitle = title?.takeIf { it.isNotBlank() },
    domain = domain?.takeIf { it.isNotBlank() } ?: url.toDomainFallback(),
    summary = summary?.takeIf { it.isNotBlank() },
    read = read,
    status = status?.replaceFirstChar { char ->
        if (char.isLowerCase()) {
            char.titlecase(Locale.getDefault())
        } else {
            char.toString()
        }
    },
    readState = if (read) "Read" else "Unread",
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

internal fun ShioriApiError.toSaveMessage(): String = when (this) {
    ShioriApiError.Validation -> "Shiori rejected this link. Check the URL and try again."
    ShioriApiError.Unauthorized -> "Your API key is no longer authorized. Update it in API access."
    ShioriApiError.NotFound -> "Shiori could not find the save endpoint. Check the server URL."
    ShioriApiError.Conflict -> "Shiori is still processing this link. Try saving it again in a moment."
    ShioriApiError.RateLimited -> "Shiori rate limited this request. Wait a moment before trying again."
    is ShioriApiError.Server -> "Shiori returned an unexpected server error (${statusCode})."
    is ShioriApiError.Network -> "Could not reach your Shiori server. Check the connection and try again."
    is ShioriApiError.Unknown -> "An unexpected error interrupted link saving. Try again."
}

internal fun ShioriApiError.toUpdateMessage(): String = when (this) {
    ShioriApiError.Validation -> "Shiori rejected that link update. Check the values and try again."
    ShioriApiError.Unauthorized -> "Your API key is no longer authorized. Update it in API access."
    ShioriApiError.NotFound -> "Shiori could not find that link anymore. Refresh and try again."
    ShioriApiError.Conflict -> "Shiori is still processing this link, so read state or metadata cannot change yet. Try again in a moment."
    ShioriApiError.RateLimited -> "Shiori rate limited this update. Wait a moment before trying again."
    is ShioriApiError.Server -> "Shiori returned an unexpected server error (${statusCode})."
    is ShioriApiError.Network -> "Could not reach your Shiori server. Check the connection and try again."
    is ShioriApiError.Unknown -> "An unexpected error interrupted this link update. Try again."
}

internal fun ShioriApiError.toDeleteMessage(): String = when (this) {
    ShioriApiError.Validation -> "Shiori rejected that trash action. Refresh and try again."
    ShioriApiError.Unauthorized -> "Your API key is no longer authorized. Update it in API access."
    ShioriApiError.NotFound -> "Shiori could not find that link anymore. Refresh and try again."
    ShioriApiError.Conflict -> "Shiori is still processing this link, so this trash action cannot finish yet. Try again in a moment."
    ShioriApiError.RateLimited -> "Shiori rate limited this trash action. Wait a moment before trying again."
    is ShioriApiError.Server -> "Shiori returned an unexpected server error (${statusCode})."
    is ShioriApiError.Network -> "Could not reach your Shiori server. Check the connection and try again."
    is ShioriApiError.Unknown -> "An unexpected error interrupted this trash action. Try again."
}

internal fun normalizeLinkUrl(rawValue: String): String = rawValue.trim()

internal fun normalizeLinkTitle(rawValue: String): String = rawValue.trim()

internal fun resolveIncomingLinkIntent(intent: Intent?): IncomingLinkIntent {
    intent ?: return IncomingLinkIntent.None

    return when (intent.action) {
        Intent.ACTION_SEND -> {
            val url = extractFirstSupportedUrl(
                listOf(
                    intent.getCharSequenceExtra(Intent.EXTRA_TEXT),
                    intent.clipData?.getItemAt(0)?.text,
                    intent.dataString,
                ),
            )
            if (url != null) IncomingLinkIntent.Supported(url) else IncomingLinkIntent.Unsupported
        }

        Intent.ACTION_SEND_MULTIPLE -> {
            IncomingLinkIntent.Unsupported
        }

        Intent.ACTION_VIEW -> {
            val url = extractFirstSupportedUrl(listOf(intent.dataString))
            if (url != null) IncomingLinkIntent.Supported(url) else IncomingLinkIntent.Unsupported
        }

        else -> IncomingLinkIntent.None
    }
}

internal fun buildIncomingIntentKey(intent: Intent?): String? {
    intent ?: return null
    val action = intent.action ?: return null
    if (
        action != Intent.ACTION_SEND &&
        action != Intent.ACTION_SEND_MULTIPLE &&
        action != Intent.ACTION_VIEW
    ) {
        return null
    }

    val multipleText = intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
        ?.joinToString(separator = "|") { it.toString() }
        .orEmpty()
    val clipText = buildString {
        val clipData = intent.clipData
        if (clipData != null) {
            repeat(clipData.itemCount) { index ->
                append(clipData.getItemAt(index).text?.toString().orEmpty())
                append('|')
            }
        }
    }

    return listOf(
        action,
        intent.dataString.orEmpty(),
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty(),
        multipleText,
        clipText,
    ).joinToString(separator = "\n")
}

internal fun extractFirstSupportedUrl(candidates: Iterable<CharSequence?>): String? {
    candidates.forEach { candidate ->
        val text = candidate?.toString().orEmpty()
        val supportedUrl = findSupportedUrlInText(text)
        if (supportedUrl != null) {
            return supportedUrl
        }
    }
    return null
}

internal fun findSupportedUrlInText(text: String): String? {
    val normalized = normalizeLinkUrl(text)
    if (isLinkUrlValid(normalized)) {
        return normalized
    }

    URL_PATTERN.findAll(text).forEach { match ->
        val candidate = match.value.trimEnd('.', ',', ';', ':', ')', ']', '}')
        if (isLinkUrlValid(candidate)) {
            return candidate
        }
    }

    return null
}

internal fun isLinkUrlValid(rawValue: String): Boolean {
    val value = normalizeLinkUrl(rawValue)
    if (value.isEmpty()) {
        return false
    }

    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    val host = uri.host?.takeIf { it.isNotBlank() }

    return (scheme == "https" || scheme == "http") && host != null
}

internal fun LinkResponse.toBrowseDestination(): LinkBrowseDestination = if (read == true) {
    LinkBrowseDestination.Archive
} else {
    LinkBrowseDestination.Inbox
}

internal fun LinkCardModel.toBrowseDestination(): LinkBrowseDestination = if (read == true) {
    LinkBrowseDestination.Archive
} else {
    LinkBrowseDestination.Inbox
}

internal fun CreateLinkResponse.toBrowseDestination(request: CreateLinkRequest): LinkBrowseDestination = when {
    duplicate -> LinkBrowseDestination.Inbox
    request.read == true -> LinkBrowseDestination.Archive
    else -> LinkBrowseDestination.Inbox
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

private val URL_PATTERN = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
