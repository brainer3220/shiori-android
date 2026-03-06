package dev.shiori.android.corenetwork

import com.squareup.moshi.Json

data class LinksQuery(
    val limit: Int? = null,
    val offset: Int? = null,
    val read: Boolean? = null,
    val sort: String? = null,
    val trash: Boolean = false,
)

data class CreateLinkRequest(
    val url: String,
    val title: String? = null,
    val read: Boolean? = null,
)

data class BulkReadStateRequest(
    val ids: List<Long>,
    val read: Boolean,
)

data class UpdateLinkRequest(
    val title: String? = null,
    val summary: String? = null,
    val clearSummary: Boolean = false,
    val read: Boolean? = null,
    val restore: Boolean? = null,
)

data class LinkListResponse(
    val links: List<LinkResponse> = emptyList(),
    val limit: Int? = null,
    val offset: Int? = null,
    val total: Int? = null,
)

data class CreateLinkResponse(
    val success: Boolean = true,
    @Json(name = "linkId")
    val linkId: String,
    val duplicate: Boolean = false,
)

data class BulkReadStateResponse(
    val updated: List<LinkResponse> = emptyList(),
)

data class DeleteLinkResponse(
    val deleted: Boolean = true,
    val message: String? = null,
)

data class EmptyTrashResponse(
    @Json(name = "removed_count")
    val removedCount: Int? = null,
    val message: String? = null,
)

data class LinkResponse(
    val id: Long,
    val url: String,
    val title: String? = null,
    val summary: String? = null,
    val domain: String? = null,
    val read: Boolean? = null,
    val status: String? = null,
    val tags: List<String> = emptyList(),
    @Json(name = "image_url")
    val imageUrl: String? = null,
    @Json(name = "created_at")
    val createdAt: String? = null,
    @Json(name = "updated_at")
    val updatedAt: String? = null,
)

sealed interface ShioriApiError {
    object Validation : ShioriApiError
    object Unauthorized : ShioriApiError
    object NotFound : ShioriApiError
    object Conflict : ShioriApiError
    object RateLimited : ShioriApiError
    data class Server(val statusCode: Int) : ShioriApiError
    data class Network(val cause: Throwable) : ShioriApiError
    data class Unknown(val cause: Throwable) : ShioriApiError
}

sealed interface ShioriApiResult<out T> {
    data class Success<T>(val value: T) : ShioriApiResult<T>
    data class Failure(val error: ShioriApiError) : ShioriApiResult<Nothing>
}
