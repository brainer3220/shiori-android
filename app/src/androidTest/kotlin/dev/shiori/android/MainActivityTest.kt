package dev.shiori.android

import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.shiori.android.corenetwork.CreateLinkRequest
import dev.shiori.android.corenetwork.CreateLinkResponse
import dev.shiori.android.corenetwork.DeleteLinkResponse
import dev.shiori.android.corenetwork.EmptyTrashResponse
import dev.shiori.android.corenetwork.LinkListResponse
import dev.shiori.android.corenetwork.LinkResponse
import dev.shiori.android.corenetwork.ShioriApiError
import dev.shiori.android.corenetwork.ShioriApiResult
import dev.shiori.android.corenetwork.UpdateLinkRequest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private lateinit var store: FakeApiAccessStore
    private lateinit var linksRepository: FakeLinksRepository
    private lateinit var checker: RecordingApiConnectionChecker

    @Before
    fun setUp() {
        store = FakeApiAccessStore()
        linksRepository = FakeLinksRepository()
        checker = RecordingApiConnectionChecker(ApiValidationStatus.Success)
        AppDependencies.resetForTests()
        AppDependencies.overrideStoreForTests(store)
        AppDependencies.overrideCheckerForTests(checker)
        AppDependencies.overrideLinksRepositoryForTests(linksRepository)
    }

    @After
    fun tearDown() {
        AppDependencies.resetForTests()
    }

    @Test
    fun apiKeyCanBeSavedRestoredAndCleared() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            enterAccess(scenario, "https://shiori.example.com", "test-api-key")
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            onView(withId(R.id.status_text)).check(matches(withText(R.string.message_saved_access)))
            onView(withId(R.id.continue_button)).check(matches(isEnabled()))

            clickButton(scenario, R.id.continue_button)
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")

            scenario.recreate()

            onView(withId(R.id.browser_screen)).check(matches(isDisplayed()))
            clickButton(scenario, R.id.edit_access_button)
            onView(withId(R.id.server_url_input)).check(matches(withText("https://shiori.example.com")))
            onView(withId(R.id.api_key_input)).check(matches(withText("test-api-key")))

            onView(withId(R.id.clear_button)).perform(scrollTo(), click())
            onView(withId(R.id.api_key_input)).check(matches(withText("")))
            onView(withId(R.id.continue_button)).check(matches(not(isEnabled())))
            onView(withId(R.id.status_text)).check(matches(withText(R.string.message_missing_access)))
        }
    }

    @Test
    fun validationSurfacesUnauthorizedResponses() {
        AppDependencies.overrideCheckerForTests(RecordingApiConnectionChecker(ApiValidationStatus.Unauthorized))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            enterAccess(scenario, "https://shiori.example.com", "test-api-key")
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            clickButton(scenario, R.id.validate_button)
            waitForText(scenario, R.id.status_text, activityString(R.string.message_validation_unauthorized))
        }
    }

    @Test
    fun validationSurfacesGenericFailures() {
        AppDependencies.overrideCheckerForTests(RecordingApiConnectionChecker(ApiValidationStatus.Failure))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            enterAccess(scenario, "https://shiori.example.com", "test-api-key")
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            clickButton(scenario, R.id.validate_button)
            waitForText(scenario, R.id.status_text, activityString(R.string.message_validation_failure))
        }
    }

    @Test
    fun savedAccessIsValidatedBeforeBrowserOpensOnLaunch() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            assertEquals(1, checker.calls.size)
            assertEquals(ApiAccessConfig("https://shiori.example.com", "test-api-key"), checker.calls.single())
        }
    }

    @Test
    fun savedAccessStaysOnAccessScreenWhenLaunchValidationFails() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        AppDependencies.overrideCheckerForTests(RecordingApiConnectionChecker(ApiValidationStatus.Unauthorized))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.status_text, activityString(R.string.message_validation_unauthorized))
            onView(withId(R.id.access_screen)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun browseFiltersAndPaginationWithoutDuplicates() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 3,
                links = listOf(
                    link(id = 1, title = "Inbox article 1", read = false),
                    link(id = 2, title = "Inbox article 2", read = false),
                ),
            ),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            2,
            page(
                limit = 20,
                offset = 2,
                total = 3,
                links = listOf(
                    link(id = 2, title = "Inbox article 2 refreshed", read = false),
                    link(id = 3, title = "Inbox article 3", read = false),
                ),
            ),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Archive,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 1,
                links = listOf(link(id = 4, title = "Archived article", read = true)),
            ),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Trash,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 1,
                links = listOf(link(id = 5, title = "Trashed article", read = true, status = "trashed")),
            ),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "Loaded 2 of 3 links.")
            waitForRecyclerCount(scenario, 2)
            assertFirstCard(scenario, expectedDomain = "example.com", expectedSummary = "Summary 1", expectedStatus = "Unread  •  Ready")

            clickButton(scenario, R.id.load_more_button)
            waitForRecyclerCount(scenario, 3)
            assertLoadedTitles(scenario, "Inbox article 1", "Inbox article 2 refreshed", "Inbox article 3")

            clickButton(scenario, R.id.filter_archive_button)
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")
            assertLoadedTitles(scenario, "Archived article")

            clickButton(scenario, R.id.filter_trash_button)
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")
            assertLoadedTitles(scenario, "Trashed article")

            clickButton(scenario, R.id.filter_inbox_button)
            waitForRecyclerCount(scenario, 3)
            assertLoadedTitles(scenario, "Inbox article 1", "Inbox article 2 refreshed", "Inbox article 3")

            assertEquals(
                listOf(
                    Request(LinkBrowseDestination.Inbox, 20, 0),
                    Request(LinkBrowseDestination.Inbox, 20, 2),
                    Request(LinkBrowseDestination.Archive, 20, 0),
                    Request(LinkBrowseDestination.Trash, 20, 0),
                ),
                linksRepository.requests,
            )
        }
    }

    @Test
    fun emptyStateIsShownWhenSelectedFilterHasNoLinks() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 1, title = "Inbox article", read = false))),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Archive,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")
            clickButton(scenario, R.id.filter_archive_button)
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
        }
    }

    @Test
    fun singleReadToggleUpdatesCurrentListImmediately() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 6, title = "Toggle article", read = false))),
        )
        linksRepository.updateLinkResult = ShioriApiResult.Success(
            link(id = 6, title = "Toggle article", read = true),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForRecyclerCount(scenario, 1)

            invokePrivateMethod(scenario, "toggleLinkReadState", cardAt(scenario, 0))

            waitForText(scenario, R.id.add_link_status_text, "Link marked as read.")
            waitForRecyclerCount(scenario, 0)
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            assertEquals(listOf(6L to UpdateLinkRequest(read = true)), linksRepository.updateRequests)
        }
    }

    @Test
    fun bulkReadUpdateUsesPatchLinksForSelectedIds() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 2,
                links = listOf(
                    link(id = 7, title = "Bulk article 1", read = false),
                    link(id = 8, title = "Bulk article 2", read = false),
                ),
            ),
        )
        linksRepository.bulkUpdateResult = ShioriApiResult.Success(
            listOf(
                link(id = 7, title = "Bulk article 1", read = true),
                link(id = 8, title = "Bulk article 2", read = true),
            ),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForRecyclerCount(scenario, 2)

            invokePrivateMethod(scenario, "onLinkSelectionChanged", cardAt(scenario, 0), true)
            invokePrivateMethod(scenario, "onLinkSelectionChanged", cardAt(scenario, 1), true)
            clickButton(scenario, R.id.mark_selected_read_button)

            waitForText(scenario, R.id.add_link_status_text, "Selected links marked as read.")
            waitForRecyclerCount(scenario, 0)
            assertEquals(listOf(BulkUpdateRequest(ids = listOf(7L, 8L), read = true)), linksRepository.bulkUpdateRequests)
        }
    }

    @Test
    fun editFlowUpdatesTitleAndClearsSummaryWithNull() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 9, title = "Editable article", read = false))),
        )
        linksRepository.updateLinkResult = ShioriApiResult.Success(
            link(id = 9, title = "Edited title", read = false).copy(summary = null),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForRecyclerCount(scenario, 1)

            invokePrivateMethod(
                scenario,
                "submitMetadataUpdate",
                9L,
                UpdateLinkRequest(title = "Edited title", summary = null, clearSummary = true),
            )

            waitForText(scenario, R.id.add_link_status_text, "Link details updated.")
            assertLoadedTitles(scenario, "Edited title")
            assertEquals(
                listOf(9L to UpdateLinkRequest(title = "Edited title", summary = null, clearSummary = true)),
                linksRepository.updateRequests,
            )
            assertFirstCard(scenario, expectedDomain = "example.com", expectedSummary = null, expectedStatus = "Unread  •  Ready")
        }
    }

    @Test
    fun conflictResponsesShowSpecificProcessingMessage() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 10, title = "Processing article", read = false))),
        )
        linksRepository.updateLinkResult = ShioriApiResult.Failure(ShioriApiError.Conflict)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForRecyclerCount(scenario, 1)

            invokePrivateMethod(scenario, "toggleLinkReadState", cardAt(scenario, 0))

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Shiori is still processing this link, so read state or metadata cannot change yet. Try again in a moment.",
            )
        }
    }

    @Test
    fun deleteActionMovesLinkOutOfActiveList() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 11, title = "Delete me", read = false))),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForRecyclerCount(scenario, 1)

            invokePrivateMethod(scenario, "deleteLink", cardAt(scenario, 0))

            waitForText(scenario, R.id.add_link_status_text, "Link moved to trash.")
            waitForRecyclerCount(scenario, 0)
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            assertEquals(listOf(11L), linksRepository.deleteRequests)
        }
    }

    @Test
    fun restoreActionReturnsTrashedLinkToInbox() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Trash,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 1,
                links = listOf(link(id = 12, title = "Restore me", read = false, status = "trashed")),
            ),
        )
        linksRepository.restoreLinkResult = ShioriApiResult.Success(
            link(id = 12, title = "Restore me", read = false, status = "ready"),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            clickButton(scenario, R.id.filter_trash_button)
            waitForRecyclerCount(scenario, 1)
            waitForText(
                scenario,
                R.id.trash_retention_text,
                "Items stay in trash for 7 days before Shiori deletes them automatically.",
            )

            invokePrivateMethod(scenario, "restoreLink", cardAt(scenario, 0))

            waitForText(scenario, R.id.add_link_status_text, "Link restored. Showing it in your inbox.")
            waitForRecyclerCount(scenario, 1)
            assertLoadedTitles(scenario, "Restore me")
            assertEquals(listOf(12L), linksRepository.restoreRequests)
        }
    }

    @Test
    fun emptyTrashRequiresConfirmationAndClearsItems() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Trash,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 1,
                links = listOf(link(id = 13, title = "Trash item", read = true, status = "trashed")),
            ),
        )
        linksRepository.emptyTrashResult = ShioriApiResult.Success(EmptyTrashResponse(removedCount = 1, message = "Trash emptied"))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            clickButton(scenario, R.id.filter_trash_button)
            waitForRecyclerCount(scenario, 1)

            onView(withId(R.id.empty_trash_button)).perform(click())
            onView(withText("This permanently deletes every trashed link right away. This action cannot be undone."))
                .check(matches(isDisplayed()))
            onView(withText(R.string.action_empty_trash)).perform(click())

            waitForText(scenario, R.id.add_link_status_text, "Trash emptied. Removed 1 links.")
            waitForRecyclerCount(scenario, 0)
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            assertEquals(1, linksRepository.emptyTrashCalls)
        }
    }

    @Test
    fun saveLinkShowsDuplicateFeedbackAndRefreshesArchive() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 1, title = "Inbox article", read = false))),
        )
        linksRepository.saveResult = ShioriApiResult.Success(
            CreateLinkResponse(
                duplicate = true,
                link = link(id = 20, title = "Existing article", read = true),
            ),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Archive,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 20, title = "Existing article", read = true))),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")

            setText(scenario, R.id.add_link_url_input, "https://example.com/20")
            setText(scenario, R.id.add_link_title_input, "Existing article")
            setChecked(scenario, R.id.add_link_read_checkbox, true)
            clickButton(scenario, R.id.add_link_button)

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "That link already exists. Showing the saved copy in your archive.",
            )
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")
            assertLoadedTitles(scenario, "Existing article")
            assertEquals(
                CreateLinkRequest(
                    url = "https://example.com/20",
                    title = "Existing article",
                    read = true,
                ),
                linksRepository.savedRequests.single(),
            )
            assertEquals(
                listOf(
                    Request(LinkBrowseDestination.Inbox, 20, 0),
                    Request(LinkBrowseDestination.Archive, 20, 0),
                ),
                linksRepository.requests,
            )
        }
    }

    @Test
    fun sharedSendIntentUsesExistingSaveWorkflowOnColdLaunch() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 30, title = "Shared article", read = false))),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 30, title = "Shared article", read = false))),
        )
        linksRepository.saveResult = ShioriApiResult.Success(
            CreateLinkResponse(
                duplicate = false,
                link = link(id = 30, title = "Shared article", read = false),
            ),
        )

        val launchIntent = Intent(Intent.ACTION_SEND).apply {
            setClassName(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java.name,
            )
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/shared")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Link saved. Showing it in your inbox.",
            )
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")

            assertEquals(
                listOf(
                    CreateLinkRequest(
                        url = "https://example.com/shared",
                        title = null,
                        read = false,
                    ),
                ),
                linksRepository.savedRequests.toList(),
            )
            assertLoadedTitles(scenario, "Shared article")
        }
    }

    @Test
    fun invalidSharedContentShowsGracefulFeedback() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )

        val launchIntent = Intent(Intent.ACTION_SEND).apply {
            setClassName(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java.name,
            )
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "shared note without a url")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            waitForText(
                scenario,
                R.id.add_link_status_text,
                "This share did not include a supported HTTP or HTTPS URL.",
            )
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            assertTrue(linksRepository.savedRequests.isEmpty())
        }
    }

    @Test
    fun viewIntentImportsUrlWhenAppLaunchesFromDeepLink() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 40, title = "Viewed article", read = false))),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = 40, title = "Viewed article", read = false))),
        )
        linksRepository.saveResult = ShioriApiResult.Success(
            CreateLinkResponse(
                duplicate = true,
                link = link(id = 40, title = "Viewed article", read = false),
            ),
        )

        val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/viewed"))
            .setClassName(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java.name,
            )

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            waitForText(
                scenario,
                R.id.add_link_status_text,
                "That link already exists. Showing the saved copy in your inbox.",
            )

            assertEquals(
                "https://example.com/viewed",
                linksRepository.savedRequests.single().url,
            )
        }
    }

    private fun enterAccess(
        scenario: ActivityScenario<MainActivity>,
        serverUrl: String,
        apiKey: String,
    ) {
        setText(scenario, R.id.server_url_input, serverUrl)
        setText(scenario, R.id.api_key_input, apiKey)
    }

    private fun waitForText(
        scenario: ActivityScenario<MainActivity>,
        viewId: Int,
        expectedText: String,
        timeoutMs: Long = 5_000,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastText = ""

        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                lastText = activity.findViewById<TextView?>(viewId)?.text?.toString().orEmpty()
            }

            if (lastText == expectedText) {
                return
            }

            Thread.sleep(100)
        }

        throw AssertionError("Text for view $viewId did not update within ${timeoutMs}ms. Last text was '$lastText'.")
    }

    private fun clickButton(
        scenario: ActivityScenario<MainActivity>,
        viewId: Int,
    ) {
        scenario.onActivity { activity ->
            activity.findViewById<android.view.View>(viewId).performClick()
        }
    }

    private fun setText(
        scenario: ActivityScenario<MainActivity>,
        viewId: Int,
        value: String,
    ) {
        scenario.onActivity { activity ->
            activity.findViewById<com.google.android.material.textfield.TextInputEditText>(viewId).setText(value)
        }
    }

    private fun setChecked(
        scenario: ActivityScenario<MainActivity>,
        viewId: Int,
        checked: Boolean,
    ) {
        scenario.onActivity { activity ->
            activity.findViewById<android.widget.CheckBox>(viewId).isChecked = checked
        }
    }

    private fun cardAt(
        scenario: ActivityScenario<MainActivity>,
        position: Int,
    ): LinkCardModel {
        var card: LinkCardModel? = null
        scenario.onActivity { activity ->
            card = (activity.findViewById<RecyclerView>(R.id.links_list).adapter as LinkListAdapter)
                .currentItems()[position]
        }
        return requireNotNull(card)
    }

    private fun invokePrivateMethod(
        scenario: ActivityScenario<MainActivity>,
        methodName: String,
        vararg args: Any,
    ) {
        scenario.onActivity { activity ->
            val parameterTypes = args.map {
                when (it) {
                    is Boolean -> Boolean::class.javaPrimitiveType!!
                    is Long -> Long::class.javaPrimitiveType!!
                    is Int -> Int::class.javaPrimitiveType!!
                    else -> it::class.java
                }
            }.toTypedArray()
            val method = MainActivity::class.java.getDeclaredMethod(methodName, *parameterTypes)
            method.isAccessible = true
            method.invoke(activity, *args)
        }
    }

    private fun waitForRecyclerCount(
        scenario: ActivityScenario<MainActivity>,
        expectedCount: Int,
        timeoutMs: Long = 5_000,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastCount = -1

        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                lastCount = activity.findViewById<RecyclerView>(R.id.links_list).adapter?.itemCount ?: -1
            }

            if (lastCount == expectedCount) {
                return
            }

            Thread.sleep(100)
        }

        throw AssertionError("RecyclerView count did not update within ${timeoutMs}ms. Last count was $lastCount.")
    }

    private fun assertFirstCard(
        scenario: ActivityScenario<MainActivity>,
        expectedDomain: String,
        expectedSummary: String?,
        expectedStatus: String,
    ) {
        val latch = CountDownLatch(1)
        var error: AssertionError? = null

        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.links_list)
            val items = (recyclerView.adapter as LinkListAdapter).currentItems()
            val firstItem = items.firstOrNull()
            if (firstItem == null) {
                error = AssertionError("Expected at least one loaded card.")
            } else if (firstItem.domain != expectedDomain || firstItem.summary != expectedSummary || firstItem.readState + "  •  " + firstItem.status != expectedStatus) {
                error = AssertionError(
                    "Expected card '$expectedDomain'/'$expectedSummary'/'$expectedStatus' but was '${firstItem.domain}'/'${firstItem.summary}'/'${firstItem.readState}  •  ${firstItem.status}'.",
                )
            }
            latch.countDown()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        error?.let { throw it }
    }

    private fun assertLoadedTitles(
        scenario: ActivityScenario<MainActivity>,
        vararg expectedTitles: String,
    ) {
        val latch = CountDownLatch(1)
        var error: AssertionError? = null

        scenario.onActivity { activity ->
            val recyclerView = activity.findViewById<RecyclerView>(R.id.links_list)
            val titles = (recyclerView.adapter as LinkListAdapter).currentItems().map { it.title }
            if (titles != expectedTitles.toList()) {
                error = AssertionError("Expected titles ${expectedTitles.toList()} but was $titles.")
            }
            latch.countDown()
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS))
        error?.let { throw it }
    }

    private fun activityString(resId: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun page(
        limit: Int,
        offset: Int,
        total: Int,
        links: List<LinkResponse>,
    ) = ShioriApiResult.Success(LinkListResponse(links = links, limit = limit, offset = offset, total = total))

    private fun link(
        id: Long,
        title: String,
        read: Boolean,
        status: String = "ready",
    ) = LinkResponse(
        id = id,
        url = "https://example.com/$id",
        title = title,
        summary = "Summary $id",
        domain = "example.com",
        read = read,
        status = status,
        createdAt = "2026-03-06T10:00:00Z",
        updatedAt = "2026-03-06T11:00:00Z",
    )

    private class RecordingApiConnectionChecker(
        private val result: ApiValidationStatus,
    ) : ApiConnectionChecker {
        val calls = CopyOnWriteArrayList<ApiAccessConfig>()

        override suspend fun validate(serverUrl: String, apiKey: String): ApiValidationStatus {
            calls += ApiAccessConfig(serverUrl, apiKey)
            return result
        }
    }

    private class FakeApiAccessStore(
        private var config: ApiAccessConfig = ApiAccessConfig(),
    ) : ApiAccessStore {
        override fun readConfig(): ApiAccessConfig = config

        override fun saveConfig(config: ApiAccessConfig) {
            this.config = config
        }

        override fun clearApiKey() {
            config = config.copy(apiKey = "")
        }

        override fun clearAll() {
            config = ApiAccessConfig()
        }
    }

    private class FakeLinksRepository : LinksRepository {
        val requests = CopyOnWriteArrayList<Request>()
        val savedRequests = CopyOnWriteArrayList<CreateLinkRequest>()
        val bulkUpdateRequests = CopyOnWriteArrayList<BulkUpdateRequest>()
        val updateRequests = CopyOnWriteArrayList<Pair<Long, UpdateLinkRequest>>()
        val restoreRequests = CopyOnWriteArrayList<Long>()
        val deleteRequests = CopyOnWriteArrayList<Long>()
        private val responses = mutableMapOf<Request, ArrayDeque<ShioriApiResult<LinkListResponse>>>()
        var saveResult: ShioriApiResult<CreateLinkResponse> = ShioriApiResult.Success(
            CreateLinkResponse(link = LinkResponse(id = 99, url = "https://example.com/99", read = false)),
        )
        var bulkUpdateResult: ShioriApiResult<List<LinkResponse>> = ShioriApiResult.Success(emptyList())
        var updateLinkResult: ShioriApiResult<LinkResponse> = ShioriApiResult.Success(
            LinkResponse(id = 99, url = "https://example.com/99", read = false),
        )
        var restoreLinkResult: ShioriApiResult<LinkResponse> = ShioriApiResult.Success(
            LinkResponse(id = 99, url = "https://example.com/99", read = false),
        )
        var deleteLinkResult: ShioriApiResult<DeleteLinkResponse> = ShioriApiResult.Success(
            DeleteLinkResponse(deleted = true, message = "Link deleted"),
        )
        var emptyTrashResult: ShioriApiResult<EmptyTrashResponse> = ShioriApiResult.Success(
            EmptyTrashResponse(removedCount = 0, message = "Trash emptied"),
        )
        var emptyTrashCalls: Int = 0

        fun enqueue(
            destination: LinkBrowseDestination,
            offset: Int,
            response: ShioriApiResult<LinkListResponse>,
        ) {
            val request = Request(destination, 20, offset)
            val queue = responses.getOrPut(request) { ArrayDeque() }
            queue.addLast(response)
        }

        override suspend fun loadLinks(
            config: ApiAccessConfig,
            destination: LinkBrowseDestination,
            limit: Int,
            offset: Int,
        ): ShioriApiResult<LinkListResponse> {
            val request = Request(destination, limit, offset)
            requests += request
            val queue = responses[request]
            return when {
                queue == null || queue.isEmpty() -> ShioriApiResult.Success(
                    LinkListResponse(
                        links = emptyList(),
                        limit = limit,
                        offset = offset,
                        total = 0,
                    ),
                )

                else -> queue.removeFirst()
            }
        }

        override suspend fun saveLink(
            config: ApiAccessConfig,
            request: CreateLinkRequest,
        ): ShioriApiResult<CreateLinkResponse> {
            savedRequests += request
            return saveResult
        }

        override suspend fun updateReadState(
            config: ApiAccessConfig,
            ids: List<Long>,
            read: Boolean,
        ): ShioriApiResult<List<LinkResponse>> {
            bulkUpdateRequests += BulkUpdateRequest(ids = ids, read = read)
            return bulkUpdateResult
        }

        override suspend fun updateLink(
            config: ApiAccessConfig,
            id: Long,
            request: UpdateLinkRequest,
        ): ShioriApiResult<LinkResponse> {
            updateRequests += id to request
            return updateLinkResult
        }

        override suspend fun restoreLink(
            config: ApiAccessConfig,
            id: Long,
        ): ShioriApiResult<LinkResponse> {
            restoreRequests += id
            return restoreLinkResult
        }

        override suspend fun deleteLink(
            config: ApiAccessConfig,
            id: Long,
        ): ShioriApiResult<DeleteLinkResponse> {
            deleteRequests += id
            return deleteLinkResult
        }

        override suspend fun emptyTrash(config: ApiAccessConfig): ShioriApiResult<EmptyTrashResponse> {
            emptyTrashCalls += 1
            return emptyTrashResult
        }
    }

    private data class Request(
        val destination: LinkBrowseDestination,
        val limit: Int,
        val offset: Int,
    )

    private data class BulkUpdateRequest(
        val ids: List<Long>,
        val read: Boolean,
    )
}
