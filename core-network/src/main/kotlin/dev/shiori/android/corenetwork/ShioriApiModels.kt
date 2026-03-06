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
    val ids: List<String>,
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
    val updated: Int? = null,
)

data class DeleteLinkResponse(
    @Json(name = "linkId")
    val linkId: String? = null,
    val message: String? = null,
)

data class EmptyTrashResponse(
    val deleted: Int? = null,
    val message: String? = null,
)

data class LinkResponse(
    val id: String,
    val url: String,
    val title: String? = null,
    val summary: String? = null,
    val domain: String? = null,
    val status: String? = null,
    @Json(name = "favicon_url")
    val faviconUrl: String? = null,
    @Json(name = "image_url")
    val imageUrl: String? = null,
    val source: String? = null,
    @Json(name = "created_at")
    val createdAt: String? = null,
    @Json(name = "updated_at")
    val updatedAt: String? = null,
    @Json(name = "read_at")
    val readAt: String? = null,
    @Json(name = "hn_url")
    val hnUrl: String? = null,
    @Json(name = "file_storage_path")
    val fileStoragePath: String? = null,
    @Json(name = "file_type")
    val fileType: String? = null,
    @Json(name = "file_mime_type")
    val fileMimeType: String? = null,
    @Json(name = "notion_page_id")
    val notionPageId: String? = null,
)

val LinkResponse.read: Boolean
    get() = readAt != null

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
