package dev.shiori.android.corenetwork

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response as OkHttpResponse
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.Buffer
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

fun interface ApiKeyProvider {
    fun getApiKey(): String?
}

class BearerAuthInterceptor(
    private val apiKeyProvider: ApiKeyProvider,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): OkHttpResponse {
        val apiKey = apiKeyProvider.getApiKey()?.trim().orEmpty()
        val request = chain.request().newBuilder().apply {
            if (apiKey.isNotEmpty()) {
                header("Authorization", "Bearer $apiKey")
            }
        }.build()

        return chain.proceed(request)
    }
}

interface ShioriApiClient {
    suspend fun getLinks(query: LinksQuery = LinksQuery()): ShioriApiResult<LinkListResponse>
    suspend fun getTrashLinks(query: LinksQuery = LinksQuery(trash = true)): ShioriApiResult<LinkListResponse>
    suspend fun createLink(request: CreateLinkRequest): ShioriApiResult<CreateLinkResponse>
    suspend fun updateReadState(request: BulkReadStateRequest): ShioriApiResult<BulkReadStateResponse>
    suspend fun updateLink(id: String, request: UpdateLinkRequest): ShioriApiResult<LinkMutationResponse>
    suspend fun restoreLink(id: String): ShioriApiResult<LinkMutationResponse>
    suspend fun emptyTrash(): ShioriApiResult<EmptyTrashResponse>
    suspend fun deleteLink(id: String): ShioriApiResult<DeleteLinkResponse>
    suspend fun getTags(): ShioriApiResult<TagListResponse>
    suspend fun createTag(request: CreateTagRequest): ShioriApiResult<TagMutationResponse>
    suspend fun updateTag(id: String, request: UpdateTagRequest): ShioriApiResult<TagMutationResponse>
    suspend fun deleteTag(id: String): ShioriApiResult<DeleteTagResponse>
    suspend fun setLinkTags(id: String, request: SetLinkTagsRequest): ShioriApiResult<SetLinkTagsResponse>
}

class DefaultShioriApiClient internal constructor(
    private val service: ShioriApiService,
) : ShioriApiClient {

    override suspend fun getLinks(query: LinksQuery): ShioriApiResult<LinkListResponse> = execute {
        service.getLinks(
            limit = query.limit,
            offset = query.offset,
            read = query.read?.value,
            sort = query.sort?.value,
            trash = query.trash.takeIf { it },
            search = query.search?.takeIf { it.isNotBlank() },
            tag = query.tag?.takeIf { it.isNotBlank() },
            includeContent = query.includeContent.takeIf { it },
        )
    }

    override suspend fun getTrashLinks(query: LinksQuery): ShioriApiResult<LinkListResponse> =
        getLinks(query.copy(trash = true))

    override suspend fun createLink(request: CreateLinkRequest): ShioriApiResult<CreateLinkResponse> = execute {
        service.createLink(request)
    }

    override suspend fun updateReadState(request: BulkReadStateRequest): ShioriApiResult<BulkReadStateResponse> = execute {
        service.updateLinks(request)
    }

    override suspend fun updateLink(id: String, request: UpdateLinkRequest): ShioriApiResult<LinkMutationResponse> = execute {
        service.updateLink(id, request.toRequestBody())
    }

    override suspend fun restoreLink(id: String): ShioriApiResult<LinkMutationResponse> = execute {
        service.restoreLink(id, UpdateLinkRequest(restore = true).toRequestBody())
    }

    override suspend fun emptyTrash(): ShioriApiResult<EmptyTrashResponse> = executeWithFallback(
        fallback = EmptyTrashResponse(message = "Trash emptied"),
    ) {
        service.emptyTrash()
    }

    override suspend fun deleteLink(id: String): ShioriApiResult<DeleteLinkResponse> = executeWithFallback(
        fallback = DeleteLinkResponse(message = "Link deleted"),
    ) {
        service.deleteLink(id)
    }

    override suspend fun getTags(): ShioriApiResult<TagListResponse> = execute {
        service.getTags()
    }

    override suspend fun createTag(request: CreateTagRequest): ShioriApiResult<TagMutationResponse> = execute {
        service.createTag(request)
    }

    override suspend fun updateTag(id: String, request: UpdateTagRequest): ShioriApiResult<TagMutationResponse> = execute {
        service.updateTag(id, request)
    }

    override suspend fun deleteTag(id: String): ShioriApiResult<DeleteTagResponse> = executeWithFallback(
        fallback = DeleteTagResponse(deleted = true, tagId = id),
    ) {
        service.deleteTag(id)
    }

    override suspend fun setLinkTags(id: String, request: SetLinkTagsRequest): ShioriApiResult<SetLinkTagsResponse> = executeWithFallback(
        fallback = SetLinkTagsResponse(linkId = id),
    ) {
        service.setLinkTags(id, request)
    }

    private suspend fun <T : Any> execute(
        block: suspend () -> Response<T>,
    ): ShioriApiResult<T> {
        return try {
            val response = block()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    ShioriApiResult.Success(body)
                } else {
                    ShioriApiResult.Failure(ShioriApiError.Unknown(IllegalStateException("Empty response body for HTTP ${response.code()}")))
                }
            } else {
                ShioriApiResult.Failure(response.toDomainError())
            }
        } catch (exception: IOException) {
            ShioriApiResult.Failure(ShioriApiError.Network(exception))
        } catch (exception: Throwable) {
            ShioriApiResult.Failure(ShioriApiError.Unknown(exception))
        }
    }

    private suspend fun <T : Any> executeWithFallback(
        fallback: T,
        block: suspend () -> Response<T>,
    ): ShioriApiResult<T> {
        return try {
            val response = block()
            if (response.isSuccessful) {
                ShioriApiResult.Success(response.body() ?: fallback)
            } else {
                ShioriApiResult.Failure(response.toDomainError())
            }
        } catch (exception: IOException) {
            ShioriApiResult.Failure(ShioriApiError.Network(exception))
        } catch (exception: Throwable) {
            ShioriApiResult.Failure(ShioriApiError.Unknown(exception))
        }
    }
}

fun createShioriApiClient(
    baseUrl: String,
    apiKeyProvider: ApiKeyProvider,
    okHttpClient: OkHttpClient? = null,
    moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build(),
): ShioriApiClient {
    val client = (okHttpClient ?: OkHttpClient.Builder().build())
        .newBuilder()
        .addInterceptor(BearerAuthInterceptor(apiKeyProvider))
        .build()

    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl.ensureTrailingSlash())
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    return DefaultShioriApiClient(retrofit.create(ShioriApiService::class.java))
}

private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"

private fun UpdateLinkRequest.toRequestBody(): RequestBody {
    val buffer = Buffer()
    val writer = JsonWriter.of(buffer)

    writer.beginObject()
    title?.let {
        writer.name("title")
        writer.value(it)
    }
    if (clearSummary) {
        val previousSerializeNulls = writer.serializeNulls
        writer.serializeNulls = true
        writer.name("summary")
        writer.nullValue()
        writer.serializeNulls = previousSerializeNulls
    } else {
        summary?.let {
            writer.name("summary")
            writer.value(it)
        }
    }
    read?.let {
        writer.name("read")
        writer.value(it)
    }
    restore?.let {
        writer.name("restore")
        writer.value(it)
    }
    writer.endObject()

    return buffer.readByteString().toRequestBody("application/json; charset=utf-8".toMediaType())
}

internal fun Int.toDomainError(): ShioriApiError = when (this) {
    400 -> ShioriApiError.Validation
    401 -> ShioriApiError.Unauthorized
    404 -> ShioriApiError.NotFound
    409 -> ShioriApiError.Conflict
    429 -> ShioriApiError.RateLimited()
    500 -> ShioriApiError.Server(this)
    else -> ShioriApiError.Server(this)
}

private fun Response<*>.toDomainError(): ShioriApiError {
    if (code() != 429) {
        return code().toDomainError()
    }

    return ShioriApiError.RateLimited(
        retryAfterSeconds = headers()["Retry-After"]?.trim()?.toIntOrNull()?.takeIf { it >= 0 },
        resetAtEpochSeconds = headers()["X-RateLimit-Reset"]?.trim()?.toLongOrNull()?.takeIf { it >= 0L },
    )
}
