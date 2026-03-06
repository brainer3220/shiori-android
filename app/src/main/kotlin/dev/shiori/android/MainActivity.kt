package dev.shiori.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.shiori.android.corenetwork.CreateLinkRequest
import dev.shiori.android.corenetwork.LinkResponse
import dev.shiori.android.corenetwork.ShioriApiResult
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var store: ApiAccessStore
    private lateinit var checker: ApiConnectionChecker
    private lateinit var linksRepository: LinksRepository

    private lateinit var accessScreen: View
    private lateinit var browserScreen: View
    private lateinit var serverUrlLayout: TextInputLayout
    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var validateButton: MaterialButton
    private lateinit var continueButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    private lateinit var editAccessButton: MaterialButton
    private lateinit var filtersGroup: MaterialButtonToggleGroup
    private lateinit var inboxButton: MaterialButton
    private lateinit var archiveButton: MaterialButton
    private lateinit var trashButton: MaterialButton
    private lateinit var browserStateText: TextView
    private lateinit var browserProgress: ProgressBar
    private lateinit var addLinkUrlLayout: TextInputLayout
    private lateinit var addLinkUrlInput: TextInputEditText
    private lateinit var addLinkTitleInput: TextInputEditText
    private lateinit var addLinkReadCheckbox: CheckBox
    private lateinit var addLinkStatusText: TextView
    private lateinit var addLinkButton: MaterialButton
    private lateinit var linksList: RecyclerView
    private lateinit var loadMoreButton: MaterialButton

    private val linksAdapter = LinkListAdapter()

    private var savedConfig = ApiAccessConfig()
    private var validationStatus = ApiValidationStatus.Idle
    private var isWorking = false
    private var isSavingLink = false
    private var currentScreen = Screen.Access
    private var currentDestination = LinkBrowseDestination.Inbox
    private var addLinkStatusMessage: String? = null
    private var accessStatusOverrideMessage: String? = null
    private var pendingSharedUrl: String? = null
    private var lastHandledIntentKey: String? = null
    private val linkStates = LinkBrowseDestination.values().associateWith { LinkListUiState() }.toMutableMap()

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
        linksRepository = AppDependencies.linksRepository()

        bindViews()
        bindEvents()
        setupLinksList()

        currentScreen = savedInstanceState?.getString(KEY_SCREEN)?.let(Screen::valueOf) ?: Screen.Access
        currentDestination = savedInstanceState?.getString(KEY_DESTINATION)?.let(LinkBrowseDestination::valueOf)
            ?: LinkBrowseDestination.Inbox
        pendingSharedUrl = savedInstanceState?.getString(KEY_PENDING_SHARED_URL)
        accessStatusOverrideMessage = savedInstanceState?.getString(KEY_ACCESS_STATUS_OVERRIDE)
        lastHandledIntentKey = savedInstanceState?.getString(KEY_LAST_HANDLED_INTENT)

        loadStoredConfig()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCREEN, currentScreen.name)
        outState.putString(KEY_DESTINATION, currentDestination.name)
        outState.putString(KEY_PENDING_SHARED_URL, pendingSharedUrl)
        outState.putString(KEY_ACCESS_STATUS_OVERRIDE, accessStatusOverrideMessage)
        outState.putString(KEY_LAST_HANDLED_INTENT, lastHandledIntentKey)
    }

    private fun bindViews() {
        accessScreen = findViewById(R.id.access_screen)
        browserScreen = findViewById(R.id.browser_screen)
        serverUrlLayout = findViewById(R.id.server_url_layout)
        serverUrlInput = findViewById(R.id.server_url_input)
        apiKeyLayout = findViewById(R.id.api_key_layout)
        apiKeyInput = findViewById(R.id.api_key_input)
        statusText = findViewById(R.id.status_text)
        saveButton = findViewById(R.id.save_button)
        validateButton = findViewById(R.id.validate_button)
        continueButton = findViewById(R.id.continue_button)
        clearButton = findViewById(R.id.clear_button)
        editAccessButton = findViewById(R.id.edit_access_button)
        filtersGroup = findViewById(R.id.links_filter_group)
        inboxButton = findViewById(R.id.filter_inbox_button)
        archiveButton = findViewById(R.id.filter_archive_button)
        trashButton = findViewById(R.id.filter_trash_button)
        browserStateText = findViewById(R.id.browser_state_text)
        browserProgress = findViewById(R.id.browser_progress)
        addLinkUrlLayout = findViewById(R.id.add_link_url_layout)
        addLinkUrlInput = findViewById(R.id.add_link_url_input)
        addLinkTitleInput = findViewById(R.id.add_link_title_input)
        addLinkReadCheckbox = findViewById(R.id.add_link_read_checkbox)
        addLinkStatusText = findViewById(R.id.add_link_status_text)
        addLinkButton = findViewById(R.id.add_link_button)
        linksList = findViewById(R.id.links_list)
        loadMoreButton = findViewById(R.id.load_more_button)
    }

    private fun bindEvents() {
        serverUrlInput.doAfterTextChanged {
            if (!isWorking) {
                accessStatusOverrideMessage = null
                render()
            }
        }
        apiKeyInput.doAfterTextChanged {
            if (!isWorking) {
                accessStatusOverrideMessage = null
                render()
            }
        }
        addLinkUrlInput.doAfterTextChanged {
            if (!isSavingLink) {
                addLinkStatusMessage = null
                renderBrowserState()
            }
        }
        addLinkTitleInput.doAfterTextChanged {
            if (!isSavingLink) {
                addLinkStatusMessage = null
                renderBrowserState()
            }
        }
        addLinkReadCheckbox.setOnCheckedChangeListener { _, _ ->
            if (!isSavingLink) {
                addLinkStatusMessage = null
                renderBrowserState()
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
            currentScreen = Screen.Access
            render()
        }

        validateButton.setOnClickListener {
            validateConnection()
        }

        continueButton.setOnClickListener {
            openBrowser()
        }

        editAccessButton.setOnClickListener {
            currentScreen = Screen.Access
            render()
        }

        filtersGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }

            val destination = when (checkedId) {
                R.id.filter_archive_button -> LinkBrowseDestination.Archive
                R.id.filter_trash_button -> LinkBrowseDestination.Trash
                else -> LinkBrowseDestination.Inbox
            }
            onDestinationSelected(destination)
        }

        addLinkButton.setOnClickListener {
            saveLink()
        }

        loadMoreButton.setOnClickListener {
            val state = currentLinkState()
            if (!state.hasLoadedOnce && state.items.isEmpty()) {
                fetchLinks(currentDestination, reset = true)
            } else {
                fetchLinks(currentDestination, reset = false)
            }
        }
    }

    private fun setupLinksList() {
        linksList.layoutManager = LinearLayoutManager(this)
        linksList.adapter = linksAdapter
    }

    private fun loadStoredConfig() {
        savedConfig = store.readConfig()
        serverUrlInput.setText(savedConfig.serverUrl)
        apiKeyInput.setText(savedConfig.apiKey)
        validationStatus = ApiValidationStatus.Idle

        if (!isSavedConfigValid()) {
            currentScreen = Screen.Access
        } else if (currentScreen != Screen.Access || !hasUnsavedChanges()) {
            currentScreen = Screen.Browser
        }

        render()
        if (currentScreen == Screen.Browser) {
            ensureCurrentDestinationLoaded()
        }
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
        val configChanged = normalizedConfig != savedConfig

        store.saveConfig(normalizedConfig)
        savedConfig = normalizedConfig
        validationStatus = ApiValidationStatus.Idle
        serverUrlInput.setText(savedConfig.serverUrl)
        apiKeyInput.setText(savedConfig.apiKey)

        if (configChanged) {
            resetLinkStates()
        }

        render()

        if (pendingSharedUrl != null && isSavedConfigValid()) {
            openBrowser()
        }
    }

    private fun currentLinkDraft(): CreateLinkRequest = CreateLinkRequest(
        url = addLinkUrlInput.text?.toString().orEmpty(),
        title = addLinkTitleInput.text?.toString(),
        read = addLinkReadCheckbox.isChecked,
    )

    private fun saveLink() {
        if (!isSavedConfigValid() || hasUnsavedChanges()) {
            currentScreen = Screen.Access
            render()
            return
        }

        val rawUrl = addLinkUrlInput.text?.toString().orEmpty()
        if (!isLinkUrlValid(rawUrl)) {
            renderBrowserState()
            return
        }

        isSavingLink = true
        addLinkStatusMessage = getString(R.string.message_link_saving)
        renderBrowserState()

        val draft = currentLinkDraft()
        val request = CreateLinkRequest(
            url = normalizeLinkUrl(draft.url),
            title = normalizeLinkTitle(draft.title.orEmpty()).takeIf { it.isNotEmpty() },
            read = draft.read,
        )

        lifecycleScope.launch {
            when (val result = linksRepository.saveLink(savedConfig, request)) {
                is ShioriApiResult.Success -> handleLinkSaved(result.value.link, result.value.duplicate)
                is ShioriApiResult.Failure -> {
                    isSavingLink = false
                    addLinkStatusMessage = result.error.toSaveMessage()
                    renderBrowserState()
                }
            }
        }
    }

    private fun handleLinkSaved(link: LinkResponse, duplicate: Boolean) {
        val destination = link.toBrowseDestination()
        addLinkUrlInput.setText("")
        addLinkTitleInput.setText("")
        addLinkReadCheckbox.isChecked = false
        addLinkStatusMessage = when {
            duplicate && destination == LinkBrowseDestination.Archive -> getString(R.string.message_link_duplicate_archive)
            duplicate -> getString(R.string.message_link_duplicate_inbox)
            destination == LinkBrowseDestination.Archive -> getString(R.string.message_link_saved_archive)
            else -> getString(R.string.message_link_saved_inbox)
        }
        isSavingLink = false

        if (currentDestination != destination) {
            currentDestination = destination
        }

        renderBrowserState()
        fetchLinks(currentDestination, reset = true)
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

    private fun openBrowser() {
        if (!isSavedConfigValid() || hasUnsavedChanges()) {
            render()
            return
        }

        accessStatusOverrideMessage = null
        currentScreen = Screen.Browser
        render()
        ensureCurrentDestinationLoaded()
        consumePendingSharedUrl()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val intentKey = buildIncomingIntentKey(intent) ?: return
        if (intentKey == lastHandledIntentKey) {
            return
        }

        lastHandledIntentKey = intentKey
        when (val incomingLinkIntent = resolveIncomingLinkIntent(intent)) {
            IncomingLinkIntent.None -> Unit
            is IncomingLinkIntent.Supported -> {
                pendingSharedUrl = incomingLinkIntent.url
                if (isSavedConfigValid() && !hasUnsavedChanges()) {
                    openBrowser()
                } else {
                    currentScreen = Screen.Access
                    accessStatusOverrideMessage = getString(R.string.message_shared_link_requires_access)
                    render()
                }
            }

            IncomingLinkIntent.Unsupported -> {
                pendingSharedUrl = null
                if (isSavedConfigValid() && !hasUnsavedChanges()) {
                    currentScreen = Screen.Browser
                    addLinkStatusMessage = getString(R.string.message_shared_link_unsupported)
                    accessStatusOverrideMessage = null
                    render()
                    ensureCurrentDestinationLoaded()
                } else {
                    currentScreen = Screen.Access
                    accessStatusOverrideMessage = getString(R.string.message_shared_link_unsupported)
                    render()
                }
            }
        }
    }

    private fun consumePendingSharedUrl() {
        val url = pendingSharedUrl ?: return
        if (isSavingLink) {
            return
        }

        pendingSharedUrl = null
        importSharedUrl(url)
    }

    private fun importSharedUrl(url: String) {
        addLinkUrlInput.setText(url)
        addLinkTitleInput.setText("")
        addLinkReadCheckbox.isChecked = false
        addLinkStatusMessage = getString(R.string.message_shared_link_received)
        renderBrowserState()
        saveLink()
    }

    private fun onDestinationSelected(destination: LinkBrowseDestination) {
        if (currentDestination == destination) {
            renderBrowserState()
            return
        }

        currentDestination = destination
        renderBrowserState()
        ensureCurrentDestinationLoaded()
    }

    private fun ensureCurrentDestinationLoaded() {
        val state = currentLinkState()
        if (!state.hasLoadedOnce && !state.isInitialLoading && !state.isLoadingMore) {
            fetchLinks(currentDestination, reset = true)
        }
    }

    private fun fetchLinks(destination: LinkBrowseDestination, reset: Boolean) {
        if (!isSavedConfigValid() || hasUnsavedChanges()) {
            currentScreen = Screen.Access
            render()
            return
        }

        val previousState = linkStates.getValue(destination)
        if (previousState.isInitialLoading || previousState.isLoadingMore) {
            return
        }

        if (!reset && previousState.endReached) {
            return
        }

        val requestOffset = if (reset) 0 else previousState.nextOffset
        updateLinkState(destination) {
            it.copy(
                isInitialLoading = reset,
                isLoadingMore = !reset,
                message = null,
                endReached = if (reset) false else it.endReached,
                nextOffset = if (reset) 0 else it.nextOffset,
            )
        }

        lifecycleScope.launch {
            when (
                val result = linksRepository.loadLinks(
                    config = savedConfig,
                    destination = destination,
                    limit = PAGE_SIZE,
                    offset = requestOffset,
                )
            ) {
                is ShioriApiResult.Success -> {
                    val response = result.value
                    val mappedItems = response.links.map { it.toCardModel() }
                    val mergedItems = if (reset) mappedItems else mergeLinkCards(previousState.items, mappedItems)
                    val responseOffset = response.offset ?: requestOffset
                    val nextOffset = responseOffset + mappedItems.size
                    val total = response.total
                    val endReached = when {
                        total != null -> mergedItems.size >= total
                        mappedItems.size < PAGE_SIZE -> true
                        else -> false
                    }

                    updateLinkState(destination) {
                        it.copy(
                            items = mergedItems,
                            isInitialLoading = false,
                            isLoadingMore = false,
                            hasLoadedOnce = true,
                            endReached = endReached,
                            nextOffset = nextOffset,
                            total = total,
                            message = if (mergedItems.isEmpty()) getString(R.string.message_links_empty) else null,
                        )
                    }
                }

                is ShioriApiResult.Failure -> {
                    updateLinkState(destination) {
                        it.copy(
                            isInitialLoading = false,
                            isLoadingMore = false,
                            hasLoadedOnce = true,
                            message = result.error.toBrowseMessage(),
                        )
                    }
                }
            }
        }
    }

    private fun updateLinkState(
        destination: LinkBrowseDestination,
        transform: (LinkListUiState) -> LinkListUiState,
    ) {
        linkStates[destination] = transform(linkStates.getValue(destination))
        if (destination == currentDestination) {
            renderBrowserState()
        }
    }

    private fun resetLinkStates() {
        LinkBrowseDestination.values().forEach { destination ->
            linkStates[destination] = LinkListUiState()
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
            accessStatusOverrideMessage != null -> accessStatusOverrideMessage
            isWorking && validationStatus == ApiValidationStatus.Checking -> getString(R.string.message_validating)
            hasUnsavedChanges -> getString(R.string.message_unsaved_changes)
            !isSavedConfigValid() -> getString(R.string.message_missing_access)
            validationStatus == ApiValidationStatus.Success -> getString(R.string.message_validation_success)
            validationStatus == ApiValidationStatus.Unauthorized -> getString(R.string.message_validation_unauthorized)
            validationStatus == ApiValidationStatus.Failure -> getString(R.string.message_validation_failure)
            else -> getString(R.string.message_saved_access)
        }

        accessScreen.visibility = if (currentScreen == Screen.Access) View.VISIBLE else View.GONE
        browserScreen.visibility = if (currentScreen == Screen.Browser && isSavedConfigValid() && !hasUnsavedChanges) View.VISIBLE else View.GONE

        if (browserScreen.visibility == View.VISIBLE) {
            renderBrowserState()
        }
    }

    private fun renderBrowserState() {
        if (browserScreen.visibility != View.VISIBLE) {
            return
        }

        when (currentDestination) {
            LinkBrowseDestination.Inbox -> inboxButton.isChecked = true
            LinkBrowseDestination.Archive -> archiveButton.isChecked = true
            LinkBrowseDestination.Trash -> trashButton.isChecked = true
        }

        val state = currentLinkState()
        val rawLinkUrl = addLinkUrlInput.text?.toString().orEmpty()
        val isLinkUrlValid = isLinkUrlValid(rawLinkUrl)
        addLinkUrlLayout.error = when {
            rawLinkUrl.isBlank() || isLinkUrlValid -> null
            else -> getString(R.string.error_link_url)
        }
        addLinkButton.isEnabled = !isSavingLink && isLinkUrlValid
        addLinkReadCheckbox.isEnabled = !isSavingLink
        addLinkUrlInput.isEnabled = !isSavingLink
        addLinkTitleInput.isEnabled = !isSavingLink
        addLinkStatusText.visibility = if (addLinkStatusMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        addLinkStatusText.text = addLinkStatusMessage

        linksAdapter.submitItems(state.items)

        browserProgress.visibility = if (state.isInitialLoading) View.VISIBLE else View.GONE
        linksList.visibility = if (state.items.isEmpty() && state.isInitialLoading) View.INVISIBLE else View.VISIBLE

        browserStateText.text = when {
            state.isInitialLoading -> getString(R.string.message_links_loading)
            state.items.isEmpty() && state.message != null -> state.message
            state.items.isNotEmpty() && state.message != null -> state.message
            state.items.isEmpty() -> getString(R.string.message_links_empty)
            state.total != null -> getString(R.string.message_links_count_with_total, state.items.size, state.total)
            else -> getString(R.string.message_links_count, state.items.size)
        }

        val showLoadMore = when {
            state.isInitialLoading -> false
            state.items.isEmpty() && state.message != null -> true
            state.items.isEmpty() -> false
            state.endReached -> false
            else -> true
        }

        loadMoreButton.visibility = if (showLoadMore) View.VISIBLE else View.GONE
        loadMoreButton.isEnabled = !state.isLoadingMore
        loadMoreButton.text = if (state.items.isEmpty() && state.message != null) {
            getString(R.string.action_retry_links)
        } else if (state.isLoadingMore) {
            getString(R.string.action_loading_more)
        } else {
            getString(R.string.action_load_more)
        }
    }

    private fun currentLinkState(): LinkListUiState = linkStates.getValue(currentDestination)

    private fun hasUnsavedChanges(draft: ApiAccessConfig = currentDraft()): Boolean =
        ApiAccessInputValidator.normalizeServerUrl(draft.serverUrl) != savedConfig.serverUrl ||
            ApiAccessInputValidator.normalizeApiKey(draft.apiKey) != savedConfig.apiKey

    private fun isSavedConfigValid(): Boolean =
        ApiAccessInputValidator.isServerUrlValid(savedConfig.serverUrl) &&
            ApiAccessInputValidator.isApiKeyValidLooking(savedConfig.apiKey)

    private enum class Screen {
        Access,
        Browser,
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val KEY_SCREEN = "screen"
        const val KEY_DESTINATION = "destination"
        const val KEY_PENDING_SHARED_URL = "pending_shared_url"
        const val KEY_ACCESS_STATUS_OVERRIDE = "access_status_override"
        const val KEY_LAST_HANDLED_INTENT = "last_handled_intent"
    }
}
