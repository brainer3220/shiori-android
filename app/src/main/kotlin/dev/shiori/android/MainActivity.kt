package dev.shiori.android

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var store: ApiAccessStore
    private lateinit var checker: ApiConnectionChecker

    private lateinit var serverUrlLayout: TextInputLayout
    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var validateButton: MaterialButton
    private lateinit var continueButton: MaterialButton
    private lateinit var clearButton: MaterialButton

    private var savedConfig = ApiAccessConfig()
    private var validationStatus = ApiValidationStatus.Idle
    private var isWorking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        store = AppDependencies.apiAccessStore(this)
        checker = AppDependencies.connectionChecker()

        bindViews()
        bindEvents()
        loadStoredConfig()
    }

    private fun bindViews() {
        serverUrlLayout = findViewById(R.id.server_url_layout)
        serverUrlInput = findViewById(R.id.server_url_input)
        apiKeyLayout = findViewById(R.id.api_key_layout)
        apiKeyInput = findViewById(R.id.api_key_input)
        statusText = findViewById(R.id.status_text)
        saveButton = findViewById(R.id.save_button)
        validateButton = findViewById(R.id.validate_button)
        continueButton = findViewById(R.id.continue_button)
        clearButton = findViewById(R.id.clear_button)
    }

    private fun bindEvents() {
        serverUrlInput.doAfterTextChanged {
            if (!isWorking) {
                render()
            }
        }
        apiKeyInput.doAfterTextChanged {
            if (!isWorking) {
                render()
            }
        }

        saveButton.setOnClickListener {
            saveAccess()
        }

        clearButton.setOnClickListener {
            store.clearApiKey()
            savedConfig = savedConfig.copy(apiKey = "")
            apiKeyInput.setText("")
            validationStatus = ApiValidationStatus.Idle
            render()
        }

        validateButton.setOnClickListener {
            validateConnection()
        }

        continueButton.setOnClickListener {
            Toast.makeText(this, R.string.message_links_locked, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadStoredConfig() {
        savedConfig = store.readConfig()
        serverUrlInput.setText(savedConfig.serverUrl)
        apiKeyInput.setText(savedConfig.apiKey)
        validationStatus = ApiValidationStatus.Idle
        render()
    }

    private fun currentDraft(): ApiAccessConfig = ApiAccessConfig(
        serverUrl = serverUrlInput.text?.toString().orEmpty(),
        apiKey = apiKeyInput.text?.toString().orEmpty(),
    )

    private fun saveAccess() {
        val draft = currentDraft()
        if (!ApiAccessInputValidator.isServerUrlValid(draft.serverUrl) ||
            !ApiAccessInputValidator.isApiKeyValidLooking(draft.apiKey)
        ) {
            render()
            return
        }

        val normalizedConfig = ApiAccessConfig(
            serverUrl = ApiAccessInputValidator.normalizeServerUrl(draft.serverUrl),
            apiKey = ApiAccessInputValidator.normalizeApiKey(draft.apiKey),
        )
        store.saveConfig(normalizedConfig)
        savedConfig = normalizedConfig
        validationStatus = ApiValidationStatus.Idle
        serverUrlInput.setText(savedConfig.serverUrl)
        apiKeyInput.setText(savedConfig.apiKey)
        render()
    }

    private fun validateConnection() {
        if (!isSavedConfigValid() || hasUnsavedChanges()) {
            render()
            return
        }

        isWorking = true
        validationStatus = ApiValidationStatus.Checking
        render()

        lifecycleScope.launch {
            validationStatus = checker.validate(
                serverUrl = savedConfig.serverUrl,
                apiKey = savedConfig.apiKey,
            )
            isWorking = false
            render()
        }
    }

    private fun render() {
        val draft = currentDraft()
        val hasUnsavedChanges = hasUnsavedChanges(draft)
        val isDraftServerValid = ApiAccessInputValidator.isServerUrlValid(draft.serverUrl)
        val isDraftApiKeyValid = ApiAccessInputValidator.isApiKeyValidLooking(draft.apiKey)

        serverUrlLayout.error = when {
            draft.serverUrl.isBlank() || isDraftServerValid -> null
            else -> getString(R.string.error_server_url)
        }
        apiKeyLayout.error = when {
            draft.apiKey.isBlank() || isDraftApiKeyValid -> null
            else -> getString(R.string.error_api_key)
        }

        saveButton.isEnabled = !isWorking && isDraftServerValid && isDraftApiKeyValid && hasUnsavedChanges
        clearButton.isEnabled = !isWorking && savedConfig.apiKey.isNotEmpty()
        validateButton.isEnabled = !isWorking && isSavedConfigValid() && !hasUnsavedChanges
        continueButton.isEnabled = !isWorking && isSavedConfigValid() && !hasUnsavedChanges

        statusText.text = when {
            isWorking && validationStatus == ApiValidationStatus.Checking -> getString(R.string.message_validating)
            hasUnsavedChanges -> getString(R.string.message_unsaved_changes)
            !isSavedConfigValid() -> getString(R.string.message_missing_access)
            validationStatus == ApiValidationStatus.Success -> getString(R.string.message_validation_success)
            validationStatus == ApiValidationStatus.Unauthorized -> getString(R.string.message_validation_unauthorized)
            validationStatus == ApiValidationStatus.Failure -> getString(R.string.message_validation_failure)
            else -> getString(R.string.message_saved_access)
        }
    }

    private fun hasUnsavedChanges(draft: ApiAccessConfig = currentDraft()): Boolean =
        ApiAccessInputValidator.normalizeServerUrl(draft.serverUrl) != savedConfig.serverUrl ||
            ApiAccessInputValidator.normalizeApiKey(draft.apiKey) != savedConfig.apiKey

    private fun isSavedConfigValid(): Boolean =
        ApiAccessInputValidator.isServerUrlValid(savedConfig.serverUrl) &&
            ApiAccessInputValidator.isApiKeyValidLooking(savedConfig.apiKey)
}
