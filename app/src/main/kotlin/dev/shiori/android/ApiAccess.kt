package dev.shiori.android

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal const val SHIORI_BASE_URL = "https://www.shiori.sh"

data class ApiAccessConfig(
    val apiKey: String = "",
) {
    constructor(@Suppress("UNUSED_PARAMETER") serverUrl: String, apiKey: String) : this(apiKey = apiKey)
}

interface ApiAccessStore {
    fun readConfig(): ApiAccessConfig
    fun saveConfig(config: ApiAccessConfig)
    fun clearApiKey()
    fun clearAll()
}

object ApiAccessInputValidator {
    fun normalizeApiKey(rawValue: String): String = rawValue.trim()

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
        apiKey = preferences.getString(KEY_API_KEY, "").orEmpty(),
    )

    override fun saveConfig(config: ApiAccessConfig) {
        preferences.edit()
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
        const val KEY_API_KEY = "api_key"
    }
}

object AppDependencies {
    @Volatile
    private var storeOverride: ApiAccessStore? = null

    @Volatile
    private var linksRepositoryOverride: LinksRepository? = null

    fun apiAccessStore(context: Context): ApiAccessStore =
        storeOverride ?: EncryptedApiAccessStore(context.applicationContext)

    fun linksRepository(): LinksRepository = linksRepositoryOverride ?: DefaultLinksRepository()

    @VisibleForTesting
    fun overrideStoreForTests(store: ApiAccessStore?) {
        storeOverride = store
    }

    @VisibleForTesting
    fun overrideLinksRepositoryForTests(repository: LinksRepository?) {
        linksRepositoryOverride = repository
    }

    @VisibleForTesting
    fun resetForTests() {
        storeOverride = null
        linksRepositoryOverride = null
    }
}
