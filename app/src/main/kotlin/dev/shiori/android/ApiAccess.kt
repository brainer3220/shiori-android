package dev.shiori.android

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dev.shiori.android.corenetwork.ApiKeyProvider
import dev.shiori.android.corenetwork.LinksQuery
import dev.shiori.android.corenetwork.ShioriApiError
import dev.shiori.android.corenetwork.ShioriApiResult
import dev.shiori.android.corenetwork.createShioriApiClient
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApiAccessConfig(
    val serverUrl: String = "",
    val apiKey: String = "",
)

enum class ApiValidationStatus {
    Idle,
    Checking,
    Success,
    Unauthorized,
    Failure,
}

interface ApiAccessStore {
    fun readConfig(): ApiAccessConfig
    fun saveConfig(config: ApiAccessConfig)
    fun clearApiKey()
    fun clearAll()
}

interface ApiConnectionChecker {
    suspend fun validate(serverUrl: String, apiKey: String): ApiValidationStatus
}

object ApiAccessInputValidator {
    fun normalizeServerUrl(rawValue: String): String = rawValue.trim().removeSuffix("/")

    fun normalizeApiKey(rawValue: String): String = rawValue.trim()

    fun isServerUrlValid(rawValue: String): Boolean {
        val value = normalizeServerUrl(rawValue)
        if (value.isEmpty()) {
            return false
        }

        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false

        return when (scheme) {
            "https" -> true
            "http" -> host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
            else -> false
        }
    }

    fun isApiKeyValidLooking(rawValue: String): Boolean {
        val value = normalizeApiKey(rawValue)
        return value.length >= 8 && value.none(Char::isWhitespace)
    }
}

class EncryptedApiAccessStore(context: Context) : ApiAccessStore {
    private val preferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun readConfig(): ApiAccessConfig = ApiAccessConfig(
        serverUrl = preferences.getString(KEY_SERVER_URL, "").orEmpty(),
        apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
    )

    override fun saveConfig(config: ApiAccessConfig) {
        preferences.edit()
            .putString(KEY_SERVER_URL, ApiAccessInputValidator.normalizeServerUrl(config.serverUrl))
            .putString(KEY_API_KEY, ApiAccessInputValidator.normalizeApiKey(config.apiKey))
            .apply()
    }

    override fun clearApiKey() {
        preferences.edit()
            .remove(KEY_API_KEY)
            .apply()
    }

    override fun clearAll() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "secure_api_access"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_API_KEY = "api_key"
    }
}

class DefaultApiConnectionChecker : ApiConnectionChecker {
    override suspend fun validate(serverUrl: String, apiKey: String): ApiValidationStatus {
        return withContext(Dispatchers.IO) {
            val client = createShioriApiClient(
                baseUrl = ApiAccessInputValidator.normalizeServerUrl(serverUrl),
                apiKeyProvider = ApiKeyProvider { ApiAccessInputValidator.normalizeApiKey(apiKey) },
            )

            when (val result = client.getLinks(LinksQuery(limit = 1))) {
                is ShioriApiResult.Success -> ApiValidationStatus.Success
                is ShioriApiResult.Failure -> when (result.error) {
                    ShioriApiError.Unauthorized -> ApiValidationStatus.Unauthorized
                    else -> ApiValidationStatus.Failure
                }
            }
        }
    }
}

object AppDependencies {
    @Volatile
    private var storeOverride: ApiAccessStore? = null

    @Volatile
    private var checkerOverride: ApiConnectionChecker? = null

    fun apiAccessStore(context: Context): ApiAccessStore =
        storeOverride ?: EncryptedApiAccessStore(context.applicationContext)

    fun connectionChecker(): ApiConnectionChecker = checkerOverride ?: DefaultApiConnectionChecker()

    @VisibleForTesting
    fun overrideStoreForTests(store: ApiAccessStore?) {
        storeOverride = store
    }

    @VisibleForTesting
    fun overrideCheckerForTests(checker: ApiConnectionChecker?) {
        checkerOverride = checker
    }

    @VisibleForTesting
    fun resetForTests() {
        storeOverride = null
        checkerOverride = null
    }
}
