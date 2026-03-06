package dev.shiori.android

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.CheckBox
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.shiori.android.corenetwork.CreateLinkRequest
import dev.shiori.android.corenetwork.CreateLinkResponse
import dev.shiori.android.corenetwork.LinkResponse
import dev.shiori.android.corenetwork.ShioriApiResult
import dev.shiori.android.corenetwork.UpdateLinkRequest
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
    private lateinit var addLinkHeadingText: TextView
    private lateinit var addLinkSubtitleText: TextView
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
    private lateinit var trashRetentionText: TextView
    private lateinit var emptyTrashButton: MaterialButton
    private lateinit var linkSelectionStatusText: TextView
    private lateinit var linkSelectionActionsRow: View
    private lateinit var markSelectedReadButton: MaterialButton
    private lateinit var markSelectedUnreadButton: MaterialButton
    private lateinit var clearSelectionButton: MaterialButton

    private val linksAdapter = LinkListAdapter(
        onSelectionChanged = ::onLinkSelectionChanged,
        onReadToggleClicked = ::toggleLinkReadState,
        onEditClicked = ::showEditLinkDialog,
        onDeleteClicked = ::confirmDeleteLink,
        onRestoreClicked = ::restoreLink,
    )

    private var savedConfig = ApiAccessConfig()
    private var validationStatus = ApiValidationStatus.Idle
    private var isWorking = false
    private var isSavingLink = false
    private var isUpdatingLinks = false
    private var currentScreen = Screen.Access
    private var currentDestination = LinkBrowseDestination.Inbox
    private var addLinkStatusMessage: String? = null
    private var accessStatusOverrideMessage: String? = null
    private var pendingSharedUrl: String? = null
    private var pendingBrowserStatusMessage: String? = null
    private var lastHandledIntentKey: String? = null
    private var openBrowserAfterValidation = false
    private val selectedLinkIds = linkedSetOf<Long>()
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
        val resumeBrowserAfterValidation = currentScreen == Screen.Browser
        currentDestination = savedInstanceState?.getString(KEY_DESTINATION)?.let(LinkBrowseDestination::valueOf)
            ?: LinkBrowseDestination.Inbox
        pendingSharedUrl = savedInstanceState?.getString(KEY_PENDING_SHARED_URL)
        accessStatusOverrideMessage = savedInstanceState?.getString(KEY_ACCESS_STATUS_OVERRIDE)
        pendingBrowserStatusMessage = savedInstanceState?.getString(KEY_PENDING_BROWSER_STATUS)
        lastHandledIntentKey = savedInstanceState?.getString(KEY_LAST_HANDLED_INTENT)

        loadStoredConfig()
        handleIncomingIntent(intent)
        if (resumeBrowserAfterValidation && isSavedConfigValid() && !hasUnsavedChanges() && currentScreen != Screen.Browser) {
            startConnectionValidation(openBrowserOnSuccess = true)
        }
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
        outState.putString(KEY_PENDING_BROWSER_STATUS, pendingBrowserStatusMessage)
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
        addLinkHeadingText = findViewById(R.id.add_link_heading_text)
        addLinkSubtitleText = findViewById(R.id.add_link_subtitle_text)
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
        trashRetentionText = findViewById(R.id.trash_retention_text)
        emptyTrashButton = findViewById(R.id.empty_trash_button)
        linkSelectionStatusText = findViewById(R.id.link_selection_status_text)
        linkSelectionActionsRow = findViewById(R.id.link_selection_actions_row)
        markSelectedReadButton = findViewById(R.id.mark_selected_read_button)
        markSelectedUnreadButton = findViewById(R.id.mark_selected_unread_button)
        clearSelectionButton = findViewById(R.id.clear_selection_button)
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

        markSelectedReadButton.setOnClickListener {
            updateSelectedLinksReadState(read = true)
        }

        markSelectedUnreadButton.setOnClickListener {
            updateSelectedLinksReadState(read = false)
        }

        clearSelectionButton.setOnClickListener {
            clearSelectedLinks()
        }

        loadMoreButton.setOnClickListener {
            val state = currentLinkState()
            if (!state.hasLoadedOnce && state.items.isEmpty()) {
                fetchLinks(currentDestination, reset = true)
            } else {
                fetchLinks(currentDestination, reset = false)
            }
        }

        emptyTrashButton.setOnClickListener {
            confirmEmptyTrash()
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
        currentScreen = Screen.Access

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
            startConnectionValidation(openBrowserOnSuccess = true)
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
                is ShioriApiResult.Success -> handleLinkSaved(result.value, request)
                is ShioriApiResult.Failure -> {
                    isSavingLink = false
                    addLinkStatusMessage = result.error.toSaveMessage()
                    renderBrowserState()
                }
            }
        }
    }

    private fun handleLinkSaved(response: CreateLinkResponse, request: CreateLinkRequest) {
        val destination = response.toBrowseDestination(request)
        addLinkUrlInput.setText("")
        addLinkTitleInput.setText("")
        addLinkReadCheckbox.isChecked = false
        addLinkStatusMessage = when {
            response.duplicate -> getString(R.string.message_link_duplicate_inbox)
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

    private fun onLinkSelectionChanged(item: LinkCardModel, isSelected: Boolean) {
        if (!isLinkActionAvailable()) {
            return
        }

        if (isSelected) {
            selectedLinkIds.add(item.id)
        } else {
            selectedLinkIds.remove(item.id)
        }
        renderBrowserState()
    }

    private fun clearSelectedLinks(renderAfterClear: Boolean = true) {
        if (selectedLinkIds.isEmpty()) {
            return
        }

        selectedLinkIds.clear()
        if (renderAfterClear) {
            renderBrowserState()
        }
    }

    private fun pruneSelectedLinks() {
        val visibleIds = currentLinkState().items.mapTo(mutableSetOf()) { it.id }
        selectedLinkIds.retainAll(visibleIds)
    }

    private fun updateSelectedLinksReadState(read: Boolean) {
        if (!isLinkActionAvailable()) {
            addLinkStatusMessage = getString(R.string.message_selection_disabled_in_trash)
            renderBrowserState()
            return
        }
        if (selectedLinkIds.isEmpty()) {
            addLinkStatusMessage = getString(R.string.message_no_selected_links)
            renderBrowserState()
            return
        }

        val ids = selectedLinkIds.toList()
        isUpdatingLinks = true
        addLinkStatusMessage = getString(R.string.message_link_updating)
        renderBrowserState()

        lifecycleScope.launch {
            when (val result = linksRepository.updateReadState(savedConfig, ids, read)) {
                is ShioriApiResult.Success -> {
                    if (result.value.isNotEmpty()) {
                        applyUpdatedLinks(result.value)
                    } else {
                        applyLocalReadState(ids, read)
                    }
                    clearSelectedLinks(renderAfterClear = false)
                    isUpdatingLinks = false
                    addLinkStatusMessage = getString(
                        if (read) R.string.message_links_marked_read else R.string.message_links_marked_unread,
                    )
                    renderBrowserState()
                }

                is ShioriApiResult.Failure -> {
                    isUpdatingLinks = false
                    addLinkStatusMessage = result.error.toUpdateMessage()
                    renderBrowserState()
                }
            }
        }
    }

    private fun toggleLinkReadState(item: LinkCardModel) {
        if (!isLinkActionAvailable() || isUpdatingLinks) {
            return
        }

        val targetRead = item.read != true
        isUpdatingLinks = true
        addLinkStatusMessage = getString(R.string.message_link_updating)
        renderBrowserState()

        lifecycleScope.launch {
            when (
                val result = linksRepository.updateLink(
                    savedConfig,
                    item.id,
                    UpdateLinkRequest(read = targetRead),
                )
            ) {
                is ShioriApiResult.Success -> {
                    applyUpdatedLink(result.value)
                    isUpdatingLinks = false
                    addLinkStatusMessage = getString(
                        if (targetRead) R.string.message_link_read_updated else R.string.message_link_unread_updated,
                    )
                    renderBrowserState()
                }

                is ShioriApiResult.Failure -> {
                    isUpdatingLinks = false
                    addLinkStatusMessage = result.error.toUpdateMessage()
                    renderBrowserState()
                }
            }
        }
    }

    private fun confirmDeleteLink(item: LinkCardModel) {
        if (!isLinkActionAvailable() || isUpdatingLinks) {
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_move_to_trash)
            .setMessage(getString(R.string.message_confirm_move_to_trash, item.title))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_move_to_trash) { _, _ ->
                deleteLink(item)
            }
            .show()
    }

    private fun deleteLink(item: LinkCardModel) {
        isUpdatingLinks = true
        addLinkStatusMessage = getString(R.string.message_link_deleting)
        renderBrowserState()

        lifecycleScope.launch {
            when (val result = linksRepository.deleteLink(savedConfig, item.id)) {
                is ShioriApiResult.Success -> {
                    removeLinkFromActiveLists(item.id)
                    resetLinkState(LinkBrowseDestination.Trash)
                    clearSelectedLinks(renderAfterClear = false)
                    isUpdatingLinks = false
                    addLinkStatusMessage = getString(R.string.message_link_moved_to_trash)
                    renderBrowserState()
                }

                is ShioriApiResult.Failure -> {
                    isUpdatingLinks = false
                    addLinkStatusMessage = result.error.toDeleteMessage()
                    renderBrowserState()
                }
            }
        }
    }

    private fun restoreLink(item: LinkCardModel) {
        if (currentDestination != LinkBrowseDestination.Trash || isUpdatingLinks) {
            return
        }

        isUpdatingLinks = true
        addLinkStatusMessage = getString(R.string.message_link_restoring)
        renderBrowserState()

        lifecycleScope.launch {
            when (val result = linksRepository.restoreLink(savedConfig, item.id)) {
                is ShioriApiResult.Success -> {
                    val restoredDestination = result.value.toBrowseDestination()
                    applyUpdatedLink(result.value)
                    isUpdatingLinks = false
                    addLinkStatusMessage = getString(
                        if (restoredDestination == LinkBrowseDestination.Archive) {
                            R.string.message_link_restored_archive
                        } else {
                            R.string.message_link_restored_inbox
                        },
                    )
                    currentDestination = restoredDestination
                    renderBrowserState()
                    ensureCurrentDestinationLoaded()
                }

                is ShioriApiResult.Failure -> {
                    isUpdatingLinks = false
                    addLinkStatusMessage = result.error.toDeleteMessage()
                    renderBrowserState()
                }
            }
        }
    }

    private fun confirmEmptyTrash() {
        if (currentDestination != LinkBrowseDestination.Trash || isUpdatingLinks) {
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_empty_trash)
            .setMessage(R.string.message_confirm_empty_trash)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_empty_trash) { _, _ ->
                emptyTrash()
            }
            .show()
    }

    private fun emptyTrash() {
        isUpdatingLinks = true
        addLinkStatusMessage = getString(R.string.message_trash_emptying)
        renderBrowserState()

        lifecycleScope.launch {
            when (val result = linksRepository.emptyTrash(savedConfig)) {
                is ShioriApiResult.Success -> {
                    val removedCount = result.value.removedCount ?: currentLinkState().items.size
                    updateLinkState(LinkBrowseDestination.Trash) {
                        it.copy(
                            items = emptyList(),
                            isInitialLoading = false,
                            isLoadingMore = false,
                            hasLoadedOnce = true,
                            endReached = true,
                            nextOffset = 0,
                            total = 0,
                            message = getString(R.string.message_links_empty),
                        )
                    }
                    clearSelectedLinks(renderAfterClear = false)
                    isUpdatingLinks = false
                    addLinkStatusMessage = getString(R.string.message_trash_emptied, removedCount)
                    renderBrowserState()
                }

                is ShioriApiResult.Failure -> {
                    isUpdatingLinks = false
                    addLinkStatusMessage = result.error.toDeleteMessage()
                    renderBrowserState()
                }
            }
        }
    }

    private fun showEditLinkDialog(item: LinkCardModel) {
        if (!isLinkActionAvailable() || isUpdatingLinks) {
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_link, null)
        val titleInput = dialogView.findViewById<TextInputEditText>(R.id.edit_link_title_input)
        val summaryInput = dialogView.findViewById<TextInputEditText>(R.id.edit_link_summary_input)
        val clearSummaryCheckbox = dialogView.findViewById<CheckBox>(R.id.edit_link_clear_summary_checkbox)

        titleInput.setText(item.rawTitle.orEmpty())
        summaryInput.setText(item.summary.orEmpty())
        clearSummaryCheckbox.setOnCheckedChangeListener { _, isChecked ->
            summaryInput.isEnabled = !isChecked
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.title_edit_link)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_save_changes, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val requestedTitle = normalizeLinkTitle(titleInput.text?.toString().orEmpty()).takeIf { value ->
                    value.isNotEmpty()
                }
                val requestedSummary = if (clearSummaryCheckbox.isChecked) {
                    null
                } else {
                    summaryInput.text?.toString()?.trim()?.takeIf { value -> value.isNotEmpty() } ?: item.summary
                }

                if (requestedTitle == item.rawTitle && requestedSummary == item.summary) {
                    dialog.dismiss()
                    return@setOnClickListener
                }

                dialog.dismiss()
                submitMetadataUpdate(
                    itemId = item.id,
                    request = UpdateLinkRequest(
                        title = requestedTitle,
                        summary = requestedSummary,
                        clearSummary = clearSummaryCheckbox.isChecked,
                    ),
                )
            }
        }

        dialog.show()
    }

    private fun submitMetadataUpdate(itemId: Long, request: UpdateLinkRequest) {
        isUpdatingLinks = true
        addLinkStatusMessage = getString(R.string.message_link_updating)
        renderBrowserState()

        lifecycleScope.launch {
            when (val result = linksRepository.updateLink(savedConfig, itemId, request)) {
                is ShioriApiResult.Success -> {
                    applyUpdatedLink(result.value)
                    isUpdatingLinks = false
                    addLinkStatusMessage = getString(R.string.message_link_metadata_updated)
                    renderBrowserState()
                }

                is ShioriApiResult.Failure -> {
                    isUpdatingLinks = false
                    addLinkStatusMessage = result.error.toUpdateMessage()
                    renderBrowserState()
                }
            }
        }
    }

    private fun applyUpdatedLinks(updatedLinks: List<LinkResponse>) {
        updatedLinks.forEach(::applyUpdatedLink)
    }

    private fun applyUpdatedLink(updatedLink: LinkResponse) {
        val updatedCard = updatedLink.toCardModel()
        LinkBrowseDestination.values().forEach { destination ->
            val state = linkStates.getValue(destination)
            val existingIndex = state.items.indexOfFirst { it.id == updatedCard.id }
            val shouldInclude = matchesDestination(updatedLink, destination)
            val updatedItems = when {
                shouldInclude && existingIndex >= 0 -> state.items.toMutableList().apply { set(existingIndex, updatedCard) }
                shouldInclude && existingIndex < 0 && state.hasLoadedOnce && destination != LinkBrowseDestination.Trash -> listOf(updatedCard) + state.items
                !shouldInclude && existingIndex >= 0 -> state.items.filterNot { it.id == updatedCard.id }
                else -> state.items
            }

            if (updatedItems != state.items) {
                linkStates[destination] = state.copy(
                    items = updatedItems,
                    message = if (updatedItems.isEmpty() && state.hasLoadedOnce) {
                        getString(R.string.message_links_empty)
                    } else {
                        null
                    },
                    total = state.total?.let { total ->
                        when {
                            shouldInclude && existingIndex < 0 -> total + 1
                            !shouldInclude && existingIndex >= 0 -> (total - 1).coerceAtLeast(0)
                            else -> total
                        }
                    },
                )
            }
        }

        pruneSelectedLinks()
    }

    private fun removeLinkFromActiveLists(id: Long) {
        listOf(LinkBrowseDestination.Inbox, LinkBrowseDestination.Archive).forEach { destination ->
            val state = linkStates.getValue(destination)
            val updatedItems = state.items.filterNot { it.id == id }
            if (updatedItems != state.items) {
                linkStates[destination] = state.copy(
                    items = updatedItems,
                    message = if (updatedItems.isEmpty() && state.hasLoadedOnce) {
                        getString(R.string.message_links_empty)
                    } else {
                        null
                    },
                    total = state.total?.let { (it - 1).coerceAtLeast(0) },
                )
            }
        }

        pruneSelectedLinks()
    }

    private fun resetLinkState(destination: LinkBrowseDestination) {
        linkStates[destination] = LinkListUiState()
    }

    private fun applyLocalReadState(ids: List<Long>, read: Boolean) {
        val idSet = ids.toSet()
        LinkBrowseDestination.values().forEach { destination ->
            val state = linkStates.getValue(destination)
            val updatedItems = state.items.mapNotNull { item ->
                if (!idSet.contains(item.id)) {
                    item
                } else {
                    val updatedItem = item.copy(
                        read = read,
                        readState = if (read) "Read" else "Unread",
                    )
                    if (matchesDestination(updatedItem, destination)) updatedItem else null
                }
            }

            if (updatedItems != state.items) {
                linkStates[destination] = state.copy(
                    items = updatedItems,
                    message = if (updatedItems.isEmpty() && state.hasLoadedOnce) getString(R.string.message_links_empty) else null,
                )
            }
        }

        pruneSelectedLinks()
    }

    private fun isLinkActionAvailable(): Boolean = currentDestination != LinkBrowseDestination.Trash

    private fun matchesDestination(link: LinkResponse, destination: LinkBrowseDestination): Boolean = when (destination) {
        LinkBrowseDestination.Inbox -> link.status != "trashed" && link.read != true
        LinkBrowseDestination.Archive -> link.status != "trashed" && link.read == true
        LinkBrowseDestination.Trash -> link.status == "trashed"
    }

    private fun matchesDestination(link: LinkCardModel, destination: LinkBrowseDestination): Boolean = when (destination) {
        LinkBrowseDestination.Inbox -> link.read != true
        LinkBrowseDestination.Archive -> link.read == true
        LinkBrowseDestination.Trash -> false
    }

    private fun validateConnection() {
        startConnectionValidation(openBrowserOnSuccess = false)
    }

    private fun startConnectionValidation(openBrowserOnSuccess: Boolean) {
        if (!isSavedConfigValid() || hasUnsavedChanges()) {
            render()
            return
        }

        if (openBrowserOnSuccess) {
            openBrowserAfterValidation = true
        }

        if (isWorking) {
            return
        }

        accessStatusOverrideMessage = null
        currentScreen = Screen.Access
        isWorking = true
        validationStatus = ApiValidationStatus.Checking
        render()

        lifecycleScope.launch {
            validationStatus = checker.validate(
                serverUrl = savedConfig.serverUrl,
                apiKey = savedConfig.apiKey,
            )
            isWorking = false
            if (validationStatus == ApiValidationStatus.Success && openBrowserAfterValidation) {
                openBrowserAfterValidation = false
                openBrowserValidated()
            } else {
                if (validationStatus != ApiValidationStatus.Success) {
                    openBrowserAfterValidation = false
                }
                render()
            }
        }
    }

    private fun openBrowser() {
        if (!isSavedConfigValid() || hasUnsavedChanges()) {
            render()
            return
        }

        if (validationStatus != ApiValidationStatus.Success) {
            startConnectionValidation(openBrowserOnSuccess = true)
            return
        }

        openBrowserValidated()
    }

    private fun openBrowserValidated() {
        accessStatusOverrideMessage = null
        currentScreen = Screen.Browser
        pendingBrowserStatusMessage?.let {
            addLinkStatusMessage = it
            pendingBrowserStatusMessage = null
        }
        render()
        ensureCurrentDestinationLoaded()
        consumePendingSharedUrl()
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val intentKey = buildIncomingIntentKey(intent)
        if (intentKey != null) {
            if (intentKey == lastHandledIntentKey) {
                return
            }
            lastHandledIntentKey = intentKey
        }

        when (val incomingLinkIntent = resolveIncomingLinkIntent(intent)) {
            IncomingLinkIntent.None -> {
                pendingBrowserStatusMessage = null
                if (isSavedConfigValid() && !hasUnsavedChanges() && currentScreen != Screen.Browser) {
                    startConnectionValidation(openBrowserOnSuccess = true)
                }
            }

            is IncomingLinkIntent.Supported -> {
                pendingBrowserStatusMessage = null
                pendingSharedUrl = incomingLinkIntent.url
                if (isSavedConfigValid() && !hasUnsavedChanges()) {
                    startConnectionValidation(openBrowserOnSuccess = true)
                } else {
                    currentScreen = Screen.Access
                    accessStatusOverrideMessage = getString(R.string.message_shared_link_requires_access)
                    render()
                }
            }

            IncomingLinkIntent.Unsupported -> {
                pendingSharedUrl = null
                if (isSavedConfigValid() && !hasUnsavedChanges()) {
                    pendingBrowserStatusMessage = getString(R.string.message_shared_link_unsupported)
                    startConnectionValidation(openBrowserOnSuccess = true)
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

        clearSelectedLinks(renderAfterClear = false)
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
        selectedLinkIds.clear()
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
        pruneSelectedLinks()
        val rawLinkUrl = addLinkUrlInput.text?.toString().orEmpty()
        val isLinkUrlValid = isLinkUrlValid(rawLinkUrl)
        val linkActionsAvailable = isLinkActionAvailable()
        val isTrashDestination = currentDestination == LinkBrowseDestination.Trash

        val addLinkVisibility = if (isTrashDestination) View.GONE else View.VISIBLE
        addLinkHeadingText.visibility = addLinkVisibility
        addLinkSubtitleText.visibility = addLinkVisibility
        addLinkUrlLayout.visibility = addLinkVisibility
        addLinkUrlInput.visibility = addLinkVisibility
        findViewById<View>(R.id.add_link_title_layout).visibility = addLinkVisibility
        addLinkTitleInput.visibility = addLinkVisibility
        addLinkReadCheckbox.visibility = addLinkVisibility
        addLinkButton.visibility = addLinkVisibility

        linkSelectionStatusText.visibility = if (isTrashDestination) View.GONE else View.VISIBLE
        linkSelectionActionsRow.visibility = if (isTrashDestination) View.GONE else View.VISIBLE
        clearSelectionButton.visibility = if (isTrashDestination) View.GONE else View.VISIBLE

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

        linkSelectionStatusText.text = when {
            !linkActionsAvailable -> getString(R.string.message_selection_disabled_in_trash)
            selectedLinkIds.isNotEmpty() -> getString(R.string.message_selection_count, selectedLinkIds.size)
            else -> getString(R.string.message_selection_idle)
        }
        trashRetentionText.visibility = if (currentDestination == LinkBrowseDestination.Trash) View.VISIBLE else View.INVISIBLE
        emptyTrashButton.visibility = if (currentDestination == LinkBrowseDestination.Trash) View.VISIBLE else View.INVISIBLE
        emptyTrashButton.isEnabled = currentDestination == LinkBrowseDestination.Trash &&
            !isUpdatingLinks &&
            state.items.isNotEmpty()
        if (isTrashDestination) {
            (browserScreen as? NestedScrollView)?.post {
                (browserScreen as? NestedScrollView)?.smoothScrollTo(0, emptyTrashButton.top)
            }
        }
        markSelectedReadButton.isEnabled = linkActionsAvailable && !isUpdatingLinks && selectedLinkIds.isNotEmpty()
        markSelectedUnreadButton.isEnabled = linkActionsAvailable && !isUpdatingLinks && selectedLinkIds.isNotEmpty()
        clearSelectionButton.isEnabled = selectedLinkIds.isNotEmpty()

        linksAdapter.submitItems(
            newItems = state.items,
            selectedIds = selectedLinkIds,
            itemActionsEnabled = !isUpdatingLinks,
            selectionEnabled = linkActionsAvailable,
            destination = currentDestination,
        )

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
        const val KEY_PENDING_BROWSER_STATUS = "pending_browser_status"
        const val KEY_LAST_HANDLED_INTENT = "last_handled_intent"
    }
}
