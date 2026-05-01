package dev.shiori.android

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Menu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.activity.enableEdgeToEdge
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.shiori.android.corenetwork.CreateLinkRequest
import dev.shiori.android.corenetwork.CreateLinkResponse
import dev.shiori.android.corenetwork.LinkResponse
import dev.shiori.android.corenetwork.ShioriApiResult
import dev.shiori.android.corenetwork.UpdateLinkRequest
import dev.shiori.android.corenetwork.read
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var store: ApiAccessStore
    private lateinit var linksRepository: LinksRepository

    private lateinit var rootContainer: ViewGroup
    private lateinit var accessScreen: View
    private lateinit var browserScreen: View
    private lateinit var browserScrollView: NestedScrollView
    private lateinit var browserContentContainer: ViewGroup
    private lateinit var browserTitleText: TextView
    private lateinit var accessTitleText: TextView
    private lateinit var accessSubtitleText: TextView
    private lateinit var apiKeyLayout: TextInputLayout
    private lateinit var apiKeyInput: TextInputEditText
    private lateinit var statusText: TextView
    private lateinit var saveButton: MaterialButton
    private lateinit var continueButton: MaterialButton
    private lateinit var clearButton: MaterialButton
    private lateinit var editAccessButton: MaterialButton
    private lateinit var filtersGroup: MaterialButtonToggleGroup
    private lateinit var inboxButton: MaterialButton
    private lateinit var archiveButton: MaterialButton
    private lateinit var trashButton: MaterialButton
    private lateinit var addLinkSection: View
    private lateinit var browserStateText: TextView
    private lateinit var sectionHeaderText: TextView
    private lateinit var browserProgress: ProgressBar
    private lateinit var addLinkUrlLayout: TextInputLayout
    private lateinit var addLinkUrlInput: TextInputEditText
    private lateinit var addLinkStatusText: TextView
    private lateinit var addLinkButton: ImageButton
    private lateinit var linkQueryCard: View
    private lateinit var linkQueryInput: TextInputEditText
    private lateinit var linkQueryButton: ImageButton
    private lateinit var searchNavButton: ImageButton
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
        onOpenClicked = ::openLink,
        onSelectionChanged = ::onLinkSelectionChanged,
        onReadToggleClicked = ::toggleLinkReadState,
        onEditClicked = ::showEditLinkDialog,
        onDeleteClicked = ::confirmDeleteLink,
        onRestoreClicked = { restoreLink(it) },
    )

    private var savedConfig = ApiAccessConfig()
    private var isSavingLink = false
    private var isUpdatingLinks = false
    private var currentScreen = Screen.Access
    private var currentDestination = LinkBrowseDestination.Inbox
    private var addLinkStatusMessage: String? = null
    private var accessStatusOverrideMessage: String? = null
    private var pendingSharedUrl: String? = null
    private var pendingBrowserStatusMessage: String? = null
    private var lastHandledIntentKey: String? = null
    private var lastHandledSharedUrl: String? = null
    private var isSearchVisible = false
    private var lastRenderedScreen: Screen? = null
    private var lastBrowserChromeState: BrowserChromeState? = null
    private val selectedLinkIds = linkedSetOf<String>()
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
        linksRepository = AppDependencies.linksRepository()

        bindViews()
        bindEvents()
        setupLinksList()
        registerBackNavigation()

        val restoredScreen = savedInstanceState?.getString(KEY_SCREEN)?.let(Screen::valueOf)
        currentScreen = restoredScreen ?: Screen.Access
        currentDestination = parseSavedDestination(savedInstanceState?.getString(KEY_DESTINATION))
        pendingSharedUrl = savedInstanceState?.getString(KEY_PENDING_SHARED_URL)
        accessStatusOverrideMessage = savedInstanceState?.getString(KEY_ACCESS_STATUS_OVERRIDE)
        pendingBrowserStatusMessage = savedInstanceState?.getString(KEY_PENDING_BROWSER_STATUS)
        lastHandledIntentKey = savedInstanceState?.getString(KEY_LAST_HANDLED_INTENT)
        lastHandledSharedUrl = savedInstanceState?.getString(KEY_LAST_HANDLED_SHARED_URL)
        linkQueryInput.setText(savedInstanceState?.getString(KEY_LINK_QUERY).orEmpty())
        isSearchVisible = savedInstanceState?.getBoolean(KEY_SEARCH_VISIBLE) ?: currentQuery().isNotBlank()

        loadStoredConfig(restoredScreen != null)
        handleIncomingIntent(intent)
        if (currentScreen == Screen.Browser && isSavedConfigValid() && !hasUnsavedChanges()) {
            ensureCurrentDestinationLoaded()
            consumePendingSharedUrl()
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
        outState.putString(KEY_LAST_HANDLED_SHARED_URL, lastHandledSharedUrl)
        outState.putString(KEY_LINK_QUERY, currentQuery())
        outState.putBoolean(KEY_SEARCH_VISIBLE, isSearchVisible)
    }

    private fun bindViews() {
        rootContainer = findViewById(R.id.root_container)
        accessScreen = findViewById(R.id.access_screen)
        browserScreen = findViewById(R.id.browser_screen)
        browserScrollView = findViewById(R.id.browser_scroll_view)
        browserContentContainer = findViewById(R.id.browser_content_container)
        browserTitleText = findViewById(R.id.browser_title_text)
        accessTitleText = findViewById(R.id.title_text)
        accessSubtitleText = findViewById(R.id.subtitle_text)
        apiKeyLayout = findViewById(R.id.api_key_layout)
        apiKeyInput = findViewById(R.id.api_key_input)
        statusText = findViewById(R.id.status_text)
        saveButton = findViewById(R.id.save_button)
        continueButton = findViewById(R.id.continue_button)
        clearButton = findViewById(R.id.clear_button)
        editAccessButton = findViewById(R.id.edit_access_button)
        filtersGroup = findViewById(R.id.links_filter_group)
        inboxButton = findViewById(R.id.filter_inbox_button)
        archiveButton = findViewById(R.id.filter_archive_button)
        trashButton = findViewById(R.id.filter_trash_button)
        addLinkSection = findViewById(R.id.add_link_section)
        browserStateText = findViewById(R.id.browser_state_text)
        sectionHeaderText = findViewById(R.id.section_header_text)
        browserProgress = findViewById(R.id.browser_progress)
        addLinkUrlLayout = findViewById(R.id.add_link_url_layout)
        addLinkUrlInput = findViewById(R.id.add_link_url_input)
        addLinkStatusText = findViewById(R.id.add_link_status_text)
        addLinkButton = findViewById(R.id.add_link_button)
        linkQueryCard = findViewById(R.id.link_query_card)
        linkQueryInput = findViewById(R.id.link_query_input)
        linkQueryButton = findViewById(R.id.link_query_button)
        searchNavButton = findViewById(R.id.search_nav_button)
        linksList = findViewById(R.id.links_list)
        loadMoreButton = findViewById(R.id.load_more_button)
        trashRetentionText = findViewById(R.id.trash_retention_text)
        emptyTrashButton = findViewById(R.id.empty_trash_button)
        linkSelectionStatusText = findViewById(R.id.link_selection_status_text)
        linkSelectionActionsRow = findViewById(R.id.link_selection_actions_row)
        markSelectedReadButton = findViewById(R.id.mark_selected_read_button)
        markSelectedUnreadButton = findViewById(R.id.mark_selected_unread_button)
        clearSelectionButton = findViewById(R.id.clear_selection_button)

        ViewCompat.setAccessibilityLiveRegion(browserStateText, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        ViewCompat.setAccessibilityLiveRegion(addLinkStatusText, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        ViewCompat.setAccessibilityLiveRegion(linkSelectionStatusText, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
    }

    private fun bindEvents() {
        apiKeyInput.doAfterTextChanged {
            accessStatusOverrideMessage = null
            render()
        }
        addLinkUrlInput.doAfterTextChanged {
            if (!isSavingLink) {
                addLinkStatusMessage = null
                renderBrowserState()
            }
        }
        linkQueryInput.doAfterTextChanged {
            if (!it.isNullOrBlank()) {
                isSearchVisible = true
            }
            if (selectedLinkIds.isNotEmpty()) {
                clearSelectedLinks(renderAfterClear = false)
            }
            ensureCurrentDestinationLoaded()
            renderBrowserState()
        }
        addLinkUrlInput.setOnEditorActionListener { _, actionId, _ ->
            if ((actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) &&
                addLinkButton.isEnabled
            ) {
                saveLink()
                true
            } else {
                false
            }
        }
        linkQueryInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(linkQueryInput)
                true
            } else {
                false
            }
        }

        saveButton.setOnClickListener {
            saveAccess()
        }

        continueButton.setOnClickListener {
            continueToBrowserFromAccess()
        }

        clearButton.setOnClickListener {
            store.clearApiKey()
            savedConfig = ApiAccessConfig()
            apiKeyInput.setText("")
            resetLinkStates()
            currentScreen = Screen.Access
            accessStatusOverrideMessage = null
            render()
        }

        editAccessButton.setOnClickListener { showBrowserMenu() }

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

        linkQueryButton.setOnClickListener {
            if (linkQueryInput.text.isNullOrBlank()) {
                isSearchVisible = false
                hideKeyboard(linkQueryInput)
                renderBrowserState()
            } else {
                linkQueryInput.setText("")
                focusInput(linkQueryInput)
            }
        }

        searchNavButton.setOnClickListener {
            if (isSearchVisible && currentQuery().isBlank()) {
                isSearchVisible = false
                hideKeyboard(linkQueryInput)
            } else {
                isSearchVisible = true
                focusInput(linkQueryInput)
            }
            renderBrowserState()
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
        linksList.itemAnimator?.apply {
            addDuration = 120L
            removeDuration = 120L
            moveDuration = 120L
            changeDuration = 120L
        }
        (linksList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
    }

    private fun registerBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentScreen == Screen.Access && isSavedConfigValid()) {
                    continueToBrowserFromAccess()
                    return
                }

                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    private fun continueToBrowserFromAccess() {
        if (!isSavedConfigValid()) {
            return
        }

        hideKeyboard(apiKeyInput)
        if (hasUnsavedChanges()) {
            confirmDiscardAccessChanges()
        } else {
            openBrowser()
        }
    }

    private fun confirmDiscardAccessChanges() {
        AlertDialog.Builder(this)
            .setTitle(R.string.title_discard_access_changes)
            .setMessage(R.string.message_confirm_discard_access_changes)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_discard_and_continue) { _, _ ->
                apiKeyInput.setText(savedConfig.apiKey)
                accessStatusOverrideMessage = null
                openBrowser()
            }
            .show()
    }

    private fun showBrowserMenu() {
        PopupMenu(this, editAccessButton).apply {
            menu.add(Menu.NONE, MENU_EDIT_ACCESS, 0, R.string.action_edit_access)

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT_ACCESS -> {
                        currentScreen = Screen.Access
                        render()
                        true
                    }

                    else -> false
                }
            }
            show()
        }
    }

    private fun focusInput(input: TextInputEditText) {
        input.requestFocus()
        input.post {
            input.setSelection(input.text?.length ?: 0)
            val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            inputMethodManager?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun hideKeyboard(target: View) {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        inputMethodManager?.hideSoftInputFromWindow(target.windowToken, 0)
        target.clearFocus()
    }

    private fun currentQuery(): String = linkQueryInput.text?.toString().orEmpty().trim()

    private fun visibleLinkItems(state: LinkListUiState): List<LinkCardModel> {
        val query = currentQuery()
        if (query.isBlank()) {
            return state.items
        }

        return state.items.filter { item ->
            listOfNotNull(item.title, item.domain, item.summary, item.url, item.readState, item.status)
                .any { value -> value.contains(query, ignoreCase = true) }
        }
    }

    private fun currentSectionTitle(query: String): String = when {
        query.isNotBlank() -> getString(R.string.title_search_results)
        currentDestination == LinkBrowseDestination.Inbox -> getString(R.string.title_today)
        currentDestination == LinkBrowseDestination.Archive -> getString(R.string.filter_archive)
        else -> getString(R.string.filter_trash)
    }

    private fun currentBrowserTitle(query: String): String = when {
        query.isNotBlank() -> getString(R.string.title_search_results)
        currentDestination == LinkBrowseDestination.Archive -> getString(R.string.filter_archive)
        currentDestination == LinkBrowseDestination.Trash -> getString(R.string.filter_trash)
        else -> getString(R.string.filter_inbox)
    }

    private fun updateFilterButtonStyles() {
        when (currentDestination) {
            LinkBrowseDestination.Inbox -> filtersGroup.check(R.id.filter_inbox_button)
            LinkBrowseDestination.Archive -> filtersGroup.check(R.id.filter_archive_button)
            LinkBrowseDestination.Trash -> filtersGroup.check(R.id.filter_trash_button)
        }

        styleFilterButton(inboxButton, currentDestination == LinkBrowseDestination.Inbox)
        styleFilterButton(archiveButton, currentDestination == LinkBrowseDestination.Archive)
        styleFilterButton(trashButton, currentDestination == LinkBrowseDestination.Trash)
    }

    private fun styleFilterButton(button: MaterialButton, selected: Boolean) {
        val backgroundColor = ContextCompat.getColor(
            this,
            if (selected) R.color.shiori_surface_selected else android.R.color.transparent,
        )
        val textColor = ContextCompat.getColor(
            this,
            if (selected) R.color.shiori_text_primary else R.color.shiori_text_secondary,
        )
        button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        button.setTextColor(textColor)
        button.iconTint = ColorStateList.valueOf(textColor)
    }

    private fun updateSearchButtonState(query: String) {
        val searchColor = ContextCompat.getColor(
            this,
            if (isSearchVisible || query.isNotBlank()) R.color.shiori_text_primary else R.color.shiori_text_secondary,
        )
        searchNavButton.imageTintList = ColorStateList.valueOf(searchColor)
        if (query.isBlank()) {
            linkQueryButton.setImageResource(R.drawable.ic_nav_search)
            linkQueryButton.contentDescription = getString(R.string.label_search_links)
        } else {
            linkQueryButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            linkQueryButton.contentDescription = getString(R.string.label_clear_search)
        }
    }

    private fun styleContinueButton(primary: Boolean) {
        val backgroundColor = ContextCompat.getColor(
            this,
            if (primary) R.color.shiori_accent_dark else R.color.shiori_white,
        )
        val textColor = ContextCompat.getColor(
            this,
            if (primary) R.color.shiori_white else R.color.shiori_text_primary,
        )
        continueButton.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        continueButton.setTextColor(textColor)
        continueButton.strokeWidth = if (primary) 0 else resources.displayMetrics.density.toInt().coerceAtLeast(1)
        continueButton.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.shiori_line))
    }

    private fun maybeAnimateBrowserContent(state: BrowserChromeState) {
        if (browserContentContainer.isLaidOut && lastBrowserChromeState != null && lastBrowserChromeState != state) {
            TransitionManager.beginDelayedTransition(
                browserContentContainer,
                AutoTransition().apply { duration = 180L },
            )
        }
        lastBrowserChromeState = state
    }

    private fun showTransientFeedback(
        message: String,
        actionLabelRes: Int? = null,
        action: (() -> Unit)? = null,
    ) {
        announceFeedback(message)
        val snackbar = Snackbar.make(rootContainer, message, Snackbar.LENGTH_LONG)
        snackbar.animationMode = Snackbar.ANIMATION_MODE_FADE
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.shiori_text_primary))
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.shiori_white))
        snackbar.setActionTextColor(ContextCompat.getColor(this, R.color.shiori_surface_selected))
        if (actionLabelRes != null && action != null) {
            snackbar.setAction(actionLabelRes) { action() }
        }
        snackbar.show()
    }

    private fun announceFeedback(message: String) {
        rootContainer.post { rootContainer.announceForAccessibility(message) }
    }

    private fun openLink(item: LinkCardModel) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
        } catch (_: ActivityNotFoundException) {
            showTransientFeedback(getString(R.string.message_link_open_failed))
        }
    }

    private fun loadStoredConfig(restoredScreen: Boolean) {
        savedConfig = store.readConfig()
        apiKeyInput.setText(savedConfig.apiKey)

        currentScreen = when {
            !isSavedConfigValid() -> Screen.Access
            restoredScreen -> currentScreen
            else -> Screen.Browser
        }

        render()
    }

    private fun currentDraft(): ApiAccessConfig = ApiAccessConfig(
        apiKey = apiKeyInput.text?.toString().orEmpty(),
    )

    private fun saveAccess() {
        val draft = currentDraft()
        if (!ApiAccessInputValidator.isApiKeyValidLooking(draft.apiKey)) {
            render()
            return
        }

        val normalizedConfig = ApiAccessConfig(
            apiKey = ApiAccessInputValidator.normalizeApiKey(draft.apiKey),
        )
        val configChanged = normalizedConfig != savedConfig

        store.saveConfig(normalizedConfig)
        savedConfig = normalizedConfig
        apiKeyInput.setText(savedConfig.apiKey)

        if (configChanged) {
            resetLinkStates()
        }

        accessStatusOverrideMessage = null
        render()
        openBrowser()
    }

    private fun currentLinkDraft(): CreateLinkRequest = CreateLinkRequest(
        url = addLinkUrlInput.text?.toString().orEmpty(),
        title = null,
        read = null,
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
            read = draft.read?.takeIf { it },
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
        linkQueryInput.setText("")
        val message = when {
            response.duplicate -> getString(R.string.message_link_duplicate_inbox)
            destination == LinkBrowseDestination.Archive -> getString(R.string.message_link_saved_archive)
            else -> getString(R.string.message_link_saved_inbox)
        }
        addLinkStatusMessage = null
        isSavingLink = false

        if (currentDestination != destination) {
            currentDestination = destination
        }

        showTransientFeedback(message)
        fetchLinks(destination, reset = true)
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

    private fun pruneSelectedLinks(visibleItems: List<LinkCardModel> = visibleLinkItems(currentLinkState())) {
        val visibleIds = visibleItems.mapTo(mutableSetOf()) { it.id }
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
                    addLinkStatusMessage = null
                    showTransientFeedback(
                        getString(
                        if (read) R.string.message_links_marked_read else R.string.message_links_marked_unread,
                        ),
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
                    isUpdatingLinks = false
                    addLinkStatusMessage = null
                    showTransientFeedback(
                        getString(
                        if (targetRead) R.string.message_link_read_updated else R.string.message_link_unread_updated,
                        ),
                    )
                    fetchLinks(currentDestination, reset = true)
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
                    addLinkStatusMessage = null
                    showTransientFeedback(
                        message = getString(R.string.message_link_moved_to_trash),
                        actionLabelRes = R.string.action_undo,
                    ) {
                        restoreLink(item, switchToRestoredDestination = false)
                    }
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
        restoreLink(item, switchToRestoredDestination = currentDestination == LinkBrowseDestination.Trash)
    }

    private fun restoreLink(item: LinkCardModel, switchToRestoredDestination: Boolean) {
        if (isUpdatingLinks) {
            return
        }

        isUpdatingLinks = true
        addLinkStatusMessage = getString(R.string.message_link_restoring)
        renderBrowserState()

        lifecycleScope.launch {
            when (val result = linksRepository.restoreLink(savedConfig, item.id)) {
                is ShioriApiResult.Success -> {
                    val restoredDestination = item.toBrowseDestination()
                    resetLinkState(LinkBrowseDestination.Trash)
                    resetLinkState(restoredDestination)
                    clearSelectedLinks(renderAfterClear = false)
                    isUpdatingLinks = false
                    addLinkStatusMessage = null
                    val message = getString(
                        if (restoredDestination == LinkBrowseDestination.Archive) {
                            R.string.message_link_restored_archive
                        } else {
                            R.string.message_link_restored_inbox
                        },
                    )
                    showTransientFeedback(message)
                    if (switchToRestoredDestination) {
                        currentDestination = restoredDestination
                        renderBrowserState()
                    }
                    fetchLinks(restoredDestination, reset = true)
                    fetchLinks(LinkBrowseDestination.Trash, reset = true)
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
                    val removedCount = result.value.deleted ?: currentLinkState().items.size
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
                    addLinkStatusMessage = null
                    showTransientFeedback(getString(R.string.message_trash_emptied, removedCount))
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
                val trimmedSummary = summaryInput.text?.toString()?.trim().orEmpty()
                val shouldClearSummary = clearSummaryCheckbox.isChecked || (item.summary != null && trimmedSummary.isEmpty())
                val requestedSummary = if (shouldClearSummary) {
                    null
                } else {
                    trimmedSummary.takeIf { value -> value.isNotEmpty() } ?: item.summary
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
                        clearSummary = shouldClearSummary,
                    ),
                )
            }
        }

        dialog.show()
    }

    private fun submitMetadataUpdate(itemId: String, request: UpdateLinkRequest) {
        isUpdatingLinks = true
        addLinkStatusMessage = getString(R.string.message_link_updating)
        renderBrowserState()

        lifecycleScope.launch {
            when (val result = linksRepository.updateLink(savedConfig, itemId, request)) {
                is ShioriApiResult.Success -> {
                    isUpdatingLinks = false
                    addLinkStatusMessage = null
                    showTransientFeedback(getString(R.string.message_link_metadata_updated))
                    fetchLinks(currentDestination, reset = true)
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

    private fun removeLinkFromActiveLists(id: String) {
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

    private fun applyLocalReadState(ids: List<String>, read: Boolean) {
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

    private fun openBrowser() {
        if (!isSavedConfigValid() || hasUnsavedChanges()) {
            currentScreen = Screen.Access
            render()
            return
        }

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
                if (isSavedConfigValid() && !hasUnsavedChanges() && currentScreen == Screen.Browser) {
                    ensureCurrentDestinationLoaded()
                    consumePendingSharedUrl()
                }
            }

            is IncomingLinkIntent.Supported -> {
                if (incomingLinkIntent.url == pendingSharedUrl || incomingLinkIntent.url == lastHandledSharedUrl) {
                    return
                }
                pendingBrowserStatusMessage = null
                lastHandledSharedUrl = incomingLinkIntent.url
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
                    pendingBrowserStatusMessage = getString(R.string.message_shared_link_unsupported)
                    openBrowser()
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
        browserScrollView.post { browserScrollView.smoothScrollTo(0, 0) }
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
        val isDraftApiKeyValid = ApiAccessInputValidator.isApiKeyValidLooking(draft.apiKey)
        val canContinueToLinks = isSavedConfigValid()
        val showSaveAction = !canContinueToLinks || hasUnsavedChanges
        val visibleScreen = if (currentScreen == Screen.Browser && isSavedConfigValid() && !hasUnsavedChanges) {
            Screen.Browser
        } else {
            Screen.Access
        }

        apiKeyLayout.error = when {
            draft.apiKey.isBlank() || isDraftApiKeyValid -> null
            else -> getString(R.string.error_api_key)
        }

        accessTitleText.setText(
            if (canContinueToLinks && !hasUnsavedChanges) {
                R.string.title_api_access_ready
            } else {
                R.string.title_api_access
            },
        )
        accessSubtitleText.setText(
            if (canContinueToLinks && !hasUnsavedChanges) {
                R.string.subtitle_api_access_ready
            } else {
                R.string.subtitle_api_access
            },
        )

        saveButton.visibility = if (showSaveAction) View.VISIBLE else View.GONE
        saveButton.isEnabled = isDraftApiKeyValid && hasUnsavedChanges
        continueButton.visibility = if (canContinueToLinks) View.VISIBLE else View.GONE
        continueButton.isEnabled = canContinueToLinks
        continueButton.text = getString(
            if (hasUnsavedChanges) R.string.action_discard_and_continue else R.string.action_continue_links,
        )
        styleContinueButton(primary = canContinueToLinks && !showSaveAction)
        clearButton.isEnabled = savedConfig.apiKey.isNotEmpty()

        statusText.text = when {
            accessStatusOverrideMessage != null -> accessStatusOverrideMessage
            !isSavedConfigValid() && !isDraftApiKeyValid -> getString(R.string.message_missing_access)
            hasUnsavedChanges -> getString(R.string.message_unsaved_changes)
            !isSavedConfigValid() -> getString(R.string.message_missing_access)
            else -> getString(R.string.message_saved_access)
        }

        if (lastRenderedScreen != null && lastRenderedScreen != visibleScreen && rootContainer.isLaidOut) {
            TransitionManager.beginDelayedTransition(rootContainer, AutoTransition().apply { duration = 180L })
        }

        accessScreen.visibility = if (visibleScreen == Screen.Access) View.VISIBLE else View.GONE
        browserScreen.visibility = if (visibleScreen == Screen.Browser) View.VISIBLE else View.GONE
        if (visibleScreen != Screen.Browser) {
            lastBrowserChromeState = null
        }
        lastRenderedScreen = visibleScreen

        if (browserScreen.visibility == View.VISIBLE) {
            renderBrowserState()
        }
    }

    private fun renderBrowserState() {
        if (browserScreen.visibility != View.VISIBLE) {
            return
        }

        val state = currentLinkState()
        val query = currentQuery()
        val visibleItems = visibleLinkItems(state)
        val isTrashDestination = currentDestination == LinkBrowseDestination.Trash

        pruneSelectedLinks(visibleItems)
        val hasSelection = selectedLinkIds.isNotEmpty()
        val showSelectionStatus = !isTrashDestination && hasSelection
        maybeAnimateBrowserContent(
            BrowserChromeState(
                destination = currentDestination,
                showSelectionStatus = showSelectionStatus,
                showTrashActions = isTrashDestination,
            ),
        )
        updateFilterButtonStyles()
        updateSearchButtonState(query)
        val rawLinkUrl = addLinkUrlInput.text?.toString().orEmpty()
        val isLinkUrlValid = isLinkUrlValid(rawLinkUrl)
        val linkActionsAvailable = isLinkActionAvailable()

        addLinkSection.visibility = if (isTrashDestination) View.GONE else View.VISIBLE
        linkQueryCard.visibility = if (isSearchVisible || query.isNotBlank()) View.VISIBLE else View.GONE
        browserTitleText.text = currentBrowserTitle(query)
        sectionHeaderText.text = currentSectionTitle(query)

        linkSelectionStatusText.visibility = if (showSelectionStatus) View.VISIBLE else View.GONE
        linkSelectionActionsRow.visibility = if (!isTrashDestination && hasSelection) View.VISIBLE else View.GONE
        clearSelectionButton.visibility = if (!isTrashDestination && hasSelection) View.VISIBLE else View.GONE

        addLinkUrlLayout.error = when {
            rawLinkUrl.isBlank() || isLinkUrlValid -> null
            else -> getString(R.string.error_link_url)
        }
        addLinkButton.isEnabled = !isSavingLink && isLinkUrlValid
        addLinkUrlInput.isEnabled = !isSavingLink
        addLinkStatusText.visibility = if (addLinkStatusMessage.isNullOrBlank()) View.GONE else View.VISIBLE
        addLinkStatusText.text = addLinkStatusMessage

        linkSelectionStatusText.text = when {
            !linkActionsAvailable -> getString(R.string.message_selection_disabled_in_trash)
            selectedLinkIds.isNotEmpty() -> getString(R.string.message_selection_count, selectedLinkIds.size)
            else -> getString(R.string.message_selection_idle)
        }
        trashRetentionText.visibility = if (currentDestination == LinkBrowseDestination.Trash) View.VISIBLE else View.GONE
        emptyTrashButton.visibility = if (currentDestination == LinkBrowseDestination.Trash) View.VISIBLE else View.GONE
        emptyTrashButton.isEnabled = currentDestination == LinkBrowseDestination.Trash &&
            !isUpdatingLinks &&
            state.items.isNotEmpty()
        if (isTrashDestination) {
            scrollBrowserToViewIfNeeded(emptyTrashButton)
        }
        markSelectedReadButton.isEnabled = linkActionsAvailable && !isUpdatingLinks && selectedLinkIds.isNotEmpty()
        markSelectedUnreadButton.isEnabled = linkActionsAvailable && !isUpdatingLinks && selectedLinkIds.isNotEmpty()
        clearSelectionButton.isEnabled = selectedLinkIds.isNotEmpty()

        linksAdapter.submitItems(
            newItems = visibleItems,
            selectedIds = selectedLinkIds,
            itemActionsEnabled = !isUpdatingLinks,
            selectionEnabled = linkActionsAvailable,
            destination = currentDestination,
        )

        browserProgress.visibility = if (state.isInitialLoading) View.VISIBLE else View.GONE
        linksList.visibility = if (visibleItems.isEmpty() && state.isInitialLoading) View.INVISIBLE else View.VISIBLE

        browserStateText.text = when {
            state.isInitialLoading -> getString(R.string.message_links_loading)
            query.isNotBlank() && visibleItems.isEmpty() && state.items.isNotEmpty() -> getString(R.string.message_links_search_empty)
            query.isNotBlank() && state.items.isNotEmpty() -> getString(R.string.message_links_search_count, visibleItems.size, state.items.size)
            state.items.isEmpty() && state.message != null -> state.message
            state.items.isNotEmpty() && state.message != null -> state.message
            state.items.isEmpty() -> getString(R.string.message_links_empty)
            state.total != null -> getString(R.string.message_links_count_with_total, state.items.size, state.total)
            else -> getString(R.string.message_links_count, state.items.size)
        }
        browserStateText.visibility = if (
            state.isInitialLoading ||
            query.isNotBlank() ||
            state.items.isEmpty() ||
            state.message != null
        ) {
            View.VISIBLE
        } else {
            View.GONE
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

    private fun scrollBrowserToViewIfNeeded(target: View) {
        browserScrollView.post {
            val viewportTop = browserScrollView.scrollY
            val viewportBottom = viewportTop + browserScrollView.height
            val targetTop = target.top
            val targetBottom = target.bottom
            if (targetTop < viewportTop || targetBottom > viewportBottom) {
                browserScrollView.smoothScrollTo(0, targetTop.coerceAtLeast(0))
            }
        }
    }

    private fun currentLinkState(): LinkListUiState = linkStates.getValue(currentDestination)

    private fun hasUnsavedChanges(draft: ApiAccessConfig = currentDraft()): Boolean =
        ApiAccessInputValidator.normalizeApiKey(draft.apiKey) != savedConfig.apiKey

    private fun isSavedConfigValid(): Boolean =
        ApiAccessInputValidator.isApiKeyValidLooking(savedConfig.apiKey)

    private enum class Screen {
        Access,
        Browser,
    }

    private data class BrowserChromeState(
        val destination: LinkBrowseDestination,
        val showSelectionStatus: Boolean,
        val showTrashActions: Boolean,
    )

    private companion object {
        const val PAGE_SIZE = 20
        const val KEY_SCREEN = "screen"
        const val KEY_DESTINATION = "destination"
        const val KEY_PENDING_SHARED_URL = "pending_shared_url"
        const val KEY_ACCESS_STATUS_OVERRIDE = "access_status_override"
        const val KEY_PENDING_BROWSER_STATUS = "pending_browser_status"
        const val KEY_LAST_HANDLED_INTENT = "last_handled_intent"
        const val KEY_LAST_HANDLED_SHARED_URL = "last_handled_shared_url"
        const val KEY_LINK_QUERY = "link_query"
        const val KEY_SEARCH_VISIBLE = "search_visible"
        const val MENU_EDIT_ACCESS = 1
    }
}
