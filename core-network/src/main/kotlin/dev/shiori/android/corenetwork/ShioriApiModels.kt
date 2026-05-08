package dev.shiori.android.corenetwork

import com.squareup.moshi.Json

data class LinksQuery(
    val limit: Int? = null,
    val offset: Int? = null,
    val read: LinkReadFilter? = null,
    val sort: LinkSortOrder? = null,
    val trash: Boolean = false,
    val search: String? = null,
    val tag: String? = null,
    val includeContent: Boolean = false,
)

enum class LinkReadFilter(val value: String) {
    All("all"),
    Read("read"),
    Unread("unread"),
}

enum class LinkSortOrder(val value: String) {
    Newest("newest"),
    Oldest("oldest"),
}

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

data class CreateTagRequest(
    val name: String,
)

data class UpdateTagRequest(
    val name: String,
)

data class SetLinkTagsRequest(
    val tagIds: List<String>,
)

data class LinkListResponse(
    val links: List<LinkResponse> = emptyList(),
    val limit: Int? = null,
    val offset: Int? = null,
    val total: Int? = null,
)

data class CreateLinkResponse(
    val success: Boolean = true,
    @param:Json(name = "linkId")
    val linkId: String,
    val duplicate: Boolean = false,
)

data class BulkReadStateResponse(
    val updated: Int? = null,
)

data class LinkMutationResponse(
    val success: Boolean = true,
    val message: String? = null,
    @param:Json(name = "linkId")
    val linkId: String,
)

data class DeleteLinkResponse(
    @param:Json(name = "linkId")
    val linkId: String? = null,
    val message: String? = null,
)

data class EmptyTrashResponse(
    val deleted: Int? = null,
    val message: String? = null,
)

data class TagListResponse(
    val success: Boolean = true,
    val tags: List<TagResponse> = emptyList(),
)

data class TagMutationResponse(
    val success: Boolean = true,
    val tag: TagResponse? = null,
    @param:Json(name = "tagId")
    val tagId: String? = null,
    val message: String? = null,
)

data class DeleteTagResponse(
    val success: Boolean = true,
    val deleted: Boolean = false,
    @param:Json(name = "tagId")
    val tagId: String? = null,
    val message: String? = null,
)

data class SetLinkTagsResponse(
    val success: Boolean = true,
    @param:Json(name = "linkId")
    val linkId: String? = null,
    val tags: List<TagResponse> = emptyList(),
    val message: String? = null,
)

data class TagResponse(
    val id: String,
    val name: String,
    val position: Int? = null,
    @param:Json(name = "created_at")
    val createdAt: String? = null,
    @param:Json(name = "updated_at")
    val updatedAt: String? = null,
)

data class LinkResponse(
    val id: String,
    val url: String,
    val title: String? = null,
    val summary: String? = null,
    val domain: String? = null,
    val status: String? = null,
    @param:Json(name = "favicon_url")
    val faviconUrl: String? = null,
    @param:Json(name = "image_url")
    val imageUrl: String? = null,
    val source: String? = null,
    @param:Json(name = "created_at")
    val createdAt: String? = null,
    @param:Json(name = "updated_at")
    val updatedAt: String? = null,
    @param:Json(name = "read_at")
    val readAt: String? = null,
    @param:Json(name = "hn_url")
    val hnUrl: String? = null,
    @param:Json(name = "file_storage_path")
    val fileStoragePath: String? = null,
    @param:Json(name = "file_type")
    val fileType: String? = null,
    @param:Json(name = "file_mime_type")
    val fileMimeType: String? = null,
    @param:Json(name = "notion_page_id")
    val notionPageId: String? = null,
    val tags: List<TagResponse> = emptyList(),
)

val LinkResponse.read: Boolean
    get() = readAt != null

sealed interface ShioriApiError {
    object Validation : ShioriApiError
    object Unauthorized : ShioriApiError
    object NotFound : ShioriApiError
    object Conflict : ShioriApiError
    data class RateLimited(
        val retryAfterSeconds: Int? = null,
        val resetAtEpochSeconds: Long? = null,
    ) : ShioriApiError
    data class Server(val statusCode: Int) : ShioriApiError
    data class Network(val cause: Throwable) : ShioriApiError
    data class Unknown(val cause: Throwable) : ShioriApiError
}

sealed interface ShioriApiResult<out T> {
    data class Success<T>(val value: T) : ShioriApiResult<T>
    data class Failure(val error: ShioriApiError) : ShioriApiResult<Nothing>
}
