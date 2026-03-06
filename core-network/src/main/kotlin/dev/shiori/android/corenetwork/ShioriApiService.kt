package dev.shiori.android.corenetwork

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

internal interface ShioriApiService {
    @GET("api/links")
    suspend fun getLinks(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("read") read: Boolean? = null,
        @Query("sort") sort: String? = null,
        @Query("trash") trash: Boolean? = null,
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
        @Path("id") id: Long,
        @Body request: UpdateLinkRequest,
    ): Response<LinkResponse>

    @HTTP(method = "DELETE", path = "api/links", hasBody = false)
    suspend fun emptyTrash(): Response<EmptyTrashResponse>

    @DELETE("api/links/{id}")
    suspend fun deleteLink(
        @Path("id") id: Long,
    ): Response<DeleteLinkResponse>
}
