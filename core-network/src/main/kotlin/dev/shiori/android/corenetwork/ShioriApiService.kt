package dev.shiori.android.corenetwork

import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.PUT

internal interface ShioriApiService {
    @GET("api/links")
    suspend fun getLinks(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("read") read: String? = null,
        @Query("sort") sort: String? = null,
        @Query("trash") trash: Boolean? = null,
        @Query("search") search: String? = null,
        @Query("tag") tag: String? = null,
        @Query("include_content") includeContent: Boolean? = null,
    ): Response<LinkListResponse>

    @POST("api/links")
    suspend fun createLink(
        @Body request: CreateLinkRequest,
    ): Response<CreateLinkResponse>

    @PATCH("api/links")
    suspend fun updateLinks(
        @Body request: BulkReadStateRequest,
    ): Response<BulkReadStateResponse>

    @PATCH("api/links/{id}")
    suspend fun updateLink(
        @Path("id") id: String,
        @Body request: RequestBody,
    ): Response<LinkMutationResponse>

    @PATCH("api/links/{id}")
    suspend fun restoreLink(
        @Path("id") id: String,
        @Body request: RequestBody,
    ): Response<LinkMutationResponse>

    @HTTP(method = "DELETE", path = "api/links", hasBody = false)
    suspend fun emptyTrash(): Response<EmptyTrashResponse>

    @DELETE("api/links/{id}")
    suspend fun deleteLink(
        @Path("id") id: String,
    ): Response<DeleteLinkResponse>

    @GET("api/tags")
    suspend fun getTags(): Response<TagListResponse>

    @POST("api/tags")
    suspend fun createTag(
        @Body request: CreateTagRequest,
    ): Response<TagMutationResponse>

    @PATCH("api/tags/{id}")
    suspend fun updateTag(
        @Path("id") id: String,
        @Body request: UpdateTagRequest,
    ): Response<TagMutationResponse>

    @DELETE("api/tags/{id}")
    suspend fun deleteTag(
        @Path("id") id: String,
    ): Response<DeleteTagResponse>

    @PUT("api/links/{id}/tags")
    suspend fun setLinkTags(
        @Path("id") id: String,
        @Body request: SetLinkTagsRequest,
    ): Response<SetLinkTagsResponse>
}
