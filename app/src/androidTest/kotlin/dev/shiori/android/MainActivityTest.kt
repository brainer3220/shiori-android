package dev.shiori.android

import android.content.Intent
import android.net.Uri
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.shiori.android.corenetwork.CreateLinkRequest
import dev.shiori.android.corenetwork.CreateLinkResponse
import dev.shiori.android.corenetwork.DeleteLinkResponse
import dev.shiori.android.corenetwork.EmptyTrashResponse
import dev.shiori.android.corenetwork.LinkListResponse
import dev.shiori.android.corenetwork.LinkMutationResponse
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

    @Before
    fun setUp() {
        store = FakeApiAccessStore()
        linksRepository = FakeLinksRepository()
        AppDependencies.resetForTests()
        AppDependencies.overrideStoreForTests(store)
        AppDependencies.overrideLinksRepositoryForTests(linksRepository)
    }

    @After
    fun tearDown() {
        AppDependencies.resetForTests()
    }

    @Test
    fun apiKeyCanBeSavedRestoredAndCleared() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            enterAccess(scenario, "test-api-key")
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")

            scenario.recreate()

            onView(withId(R.id.browser_screen)).check(matches(isDisplayed()))
            onView(withId(R.id.add_link_url_input)).check(matches(withHint("Save a link...")))
            clickButton(scenario, R.id.edit_access_button)
            onView(withText(R.string.action_edit_access)).perform(click())
            onView(withId(R.id.api_key_input)).check(matches(withText("test-api-key")))

            onView(withId(R.id.clear_button)).perform(scrollTo(), click())
            onView(withId(R.id.api_key_input)).check(matches(withText("")))
            onView(withId(R.id.save_button)).check(matches(not(isEnabled())))
            onView(withId(R.id.status_text)).check(matches(withText(R.string.message_missing_access)))
        }
    }

    @Test
    fun savedApiKeyOpensBrowserDirectlyOnLaunch() {
        store.saveConfig(ApiAccessConfig(apiKey = "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            onView(withId(R.id.browser_screen)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun savedApiKeyCanContinueFromAccessScreenBackToBrowser() {
        store.saveConfig(ApiAccessConfig(apiKey = "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            clickButton(scenario, R.id.edit_access_button)
            onView(withText(R.string.action_edit_access)).perform(click())
            onView(withId(R.id.continue_button)).perform(scrollTo(), click())
            onView(withId(R.id.browser_screen)).check(matches(isDisplayed()))
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
                    link(id = "1", title = "Inbox article 1", read = false),
                    link(id = "2", title = "Inbox article 2", read = false),
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
                    link(id = "2", title = "Inbox article 2 refreshed", read = false),
                    link(id = "3", title = "Inbox article 3", read = false),
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
                links = listOf(link(id = "4", title = "Archived article", read = true)),
            ),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Trash,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 1,
                links = listOf(link(id = "5", title = "Trashed article", read = true, status = "trashed")),
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
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "1", title = "Inbox article", read = false))),
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
    fun singleReadToggleReloadsCurrentListAfterDocumentedPatchResponse() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "6", title = "Toggle article", read = false))),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )
        linksRepository.updateLinkResult = ShioriApiResult.Success(
            LinkMutationResponse(success = true, message = "Link updated", linkId = "6"),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForRecyclerCount(scenario, 1)

            invokePrivateMethod(scenario, "toggleLinkReadState", cardAt(scenario, 0))

            waitForText(scenario, R.id.add_link_status_text, "Link marked as read.")
            waitForRecyclerCount(scenario, 0)
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            assertEquals(listOf("6" to UpdateLinkRequest(read = true)), linksRepository.updateRequests)
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
                    link(id = "7", title = "Bulk article 1", read = false),
                    link(id = "8", title = "Bulk article 2", read = false),
                ),
            ),
        )
        linksRepository.bulkUpdateResult = ShioriApiResult.Success(
            listOf(
                link(id = "7", title = "Bulk article 1", read = true),
                link(id = "8", title = "Bulk article 2", read = true),
            ),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForRecyclerCount(scenario, 2)

            invokePrivateMethod(scenario, "onLinkSelectionChanged", cardAt(scenario, 0), true)
            invokePrivateMethod(scenario, "onLinkSelectionChanged", cardAt(scenario, 1), true)
            clickButton(scenario, R.id.mark_selected_read_button)

            waitForText(scenario, R.id.add_link_status_text, "Selected links marked as read.")
            waitForRecyclerCount(scenario, 0)
            assertEquals(listOf(BulkUpdateRequest(ids = listOf("7", "8"), read = true)), linksRepository.bulkUpdateRequests)
        }
    }

    @Test
    fun editFlowUpdatesTitleAndClearsSummaryWithNull() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "9", title = "Editable article", read = false))),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 1,
                links = listOf(link(id = "9", title = "Edited title", read = false).copy(summary = null)),
            ),
        )
        linksRepository.updateLinkResult = ShioriApiResult.Success(
            LinkMutationResponse(success = true, message = "Link updated", linkId = "9"),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForRecyclerCount(scenario, 1)

            invokePrivateMethod(
                scenario,
                "submitMetadataUpdate",
                "9",
                UpdateLinkRequest(title = "Edited title", summary = null, clearSummary = true),
            )

            waitForText(scenario, R.id.add_link_status_text, "Link details updated.")
            assertLoadedTitles(scenario, "Edited title")
            assertEquals(
                listOf("9" to UpdateLinkRequest(title = "Edited title", summary = null, clearSummary = true)),
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
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "10", title = "Processing article", read = false))),
        )
        linksRepository.updateLinkResult = ShioriApiResult.Failure(ShioriApiError.Conflict)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForRecyclerCount(scenario, 1)

            invokePrivateMethod(scenario, "toggleLinkReadState", cardAt(scenario, 0))

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Shiori is still processing this link, so read state or metadata cannot change yet. Wait a moment, then try again.",
            )
            assertFirstCard(scenario, expectedDomain = "example.com", expectedSummary = "Summary 10", expectedStatus = "Unread  •  Ready")
            assertEquals(listOf(Request(LinkBrowseDestination.Inbox, 20, 0)), linksRepository.requests)
        }
    }

    @Test
    fun deleteActionMovesLinkOutOfActiveList() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "11", title = "Delete me", read = false))),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForRecyclerCount(scenario, 1)

            invokePrivateMethod(scenario, "deleteLink", cardAt(scenario, 0))

            waitForText(scenario, R.id.add_link_status_text, "Link moved to trash.")
            waitForRecyclerCount(scenario, 0)
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            assertEquals(listOf("11"), linksRepository.deleteRequests)
        }
    }

    @Test
    fun restoreActionReturnsTrashedReadLinkToArchiveAfterRefresh() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Archive,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 1,
                links = listOf(link(id = "12", title = "Restore me", read = true, status = "ready")),
            ),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Trash,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 1,
                links = listOf(link(id = "12", title = "Restore me", read = true, status = "trashed")),
            ),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Trash,
            0,
            page(
                limit = 20,
                offset = 0,
                total = 0,
                links = emptyList(),
            ),
        )
        linksRepository.restoreLinkResult = ShioriApiResult.Success(
            LinkMutationResponse(success = true, message = "Link restored", linkId = "12"),
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

            waitForText(scenario, R.id.add_link_status_text, "Link restored. Showing it in your archive.")
            waitForRecyclerCount(scenario, 1)
            assertLoadedTitles(scenario, "Restore me")
            assertEquals(listOf("12"), linksRepository.restoreRequests)
            assertEquals(
                listOf(
                    Request(LinkBrowseDestination.Inbox, 20, 0),
                    Request(LinkBrowseDestination.Trash, 20, 0),
                    Request(LinkBrowseDestination.Archive, 20, 0),
                    Request(LinkBrowseDestination.Trash, 20, 0),
                ),
                linksRepository.requests,
            )
        }
    }

    @Test
    fun restoreNotFoundKeepsTrashListIntact() {
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
                links = listOf(link(id = "14", title = "Missing trash item", read = false, status = "trashed")),
            ),
        )
        linksRepository.restoreLinkResult = ShioriApiResult.Failure(ShioriApiError.NotFound)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            clickButton(scenario, R.id.filter_trash_button)
            waitForRecyclerCount(scenario, 1)

            invokePrivateMethod(scenario, "restoreLink", cardAt(scenario, 0))

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Shiori could not find that link anymore. Refresh and try again.",
            )
            waitForRecyclerCount(scenario, 1)
            assertLoadedTitles(scenario, "Missing trash item")
            assertEquals(
                listOf(
                    Request(LinkBrowseDestination.Inbox, 20, 0),
                    Request(LinkBrowseDestination.Trash, 20, 0),
                ),
                linksRepository.requests,
            )
        }
    }

    @Test
    fun emptyTrashCancelKeepsItemsAndSkipsDeleteCall() {
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
                links = listOf(link(id = "15", title = "Trash item", read = true, status = "trashed")),
            ),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            clickButton(scenario, R.id.filter_trash_button)
            waitForRecyclerCount(scenario, 1)

            onView(withId(R.id.empty_trash_button)).perform(click())
            onView(withText(android.R.string.cancel)).perform(click())

            waitForRecyclerCount(scenario, 1)
            assertLoadedTitles(scenario, "Trash item")
            assertEquals(0, linksRepository.emptyTrashCalls)
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
                links = listOf(link(id = "13", title = "Trash item", read = true, status = "trashed")),
            ),
        )
        linksRepository.emptyTrashResult = ShioriApiResult.Success(EmptyTrashResponse(deleted = 1, message = "Trash emptied"))

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
    fun emptyTrashRateLimitedKeepsItemsVisible() {
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
                links = listOf(link(id = "16", title = "Rate limited trash item", read = true, status = "trashed")),
            ),
        )
        linksRepository.emptyTrashResult = ShioriApiResult.Failure(
            ShioriApiError.RateLimited(retryAfterSeconds = 30),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            clickButton(scenario, R.id.filter_trash_button)
            waitForRecyclerCount(scenario, 1)

            onView(withId(R.id.empty_trash_button)).perform(click())
            onView(withText(R.string.action_empty_trash)).perform(click())

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Shiori rate limited trash requests. Wait about 30 seconds before trying again. The documented limit is 60 per minute.",
            )
            waitForRecyclerCount(scenario, 1)
            assertLoadedTitles(scenario, "Rate limited trash item")
            assertEquals(1, linksRepository.emptyTrashCalls)
        }
    }

    @Test
    fun saveLinkShowsDuplicateFeedbackAndRefreshesInbox() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "1", title = "Inbox article", read = false))),
        )
        linksRepository.saveResult = ShioriApiResult.Success(
            CreateLinkResponse(
                success = true,
                linkId = "20",
                duplicate = true,
            ),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "20", title = "Existing article", read = false))),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")

            setText(scenario, R.id.add_link_url_input, "https://example.com/20")
            clickButton(scenario, R.id.add_link_button)

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "That link already exists. Showing the saved copy in your inbox.",
            )
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")
            assertLoadedTitles(scenario, "Existing article")
            assertEquals(
                CreateLinkRequest(
                    url = "https://example.com/20",
                    title = null,
                    read = null,
                ),
                linksRepository.savedRequests.single(),
            )
            assertEquals(
                listOf(
                    Request(LinkBrowseDestination.Inbox, 20, 0),
                    Request(LinkBrowseDestination.Inbox, 20, 0),
                ),
                linksRepository.requests,
            )
        }
    }

    @Test
    fun saveLinkRefreshesInboxAfterCreateSuccess() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "1", title = "Inbox article", read = false))),
        )
        linksRepository.saveResult = ShioriApiResult.Success(
            CreateLinkResponse(
                success = true,
                linkId = "21",
                duplicate = false,
            ),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 2, links = listOf(link(id = "21", title = "Saved article", read = false))),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")

            setText(scenario, R.id.add_link_url_input, "https://example.com/21")
            clickButton(scenario, R.id.add_link_button)

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Link saved. Showing it in your inbox.",
            )
            waitForRecyclerCount(scenario, 1)
            assertLoadedTitles(scenario, "Saved article")
            assertEquals(
                CreateLinkRequest(
                    url = "https://example.com/21",
                    title = null,
                    read = null,
                ),
                linksRepository.savedRequests.single(),
            )
            assertEquals(
                listOf(
                    Request(LinkBrowseDestination.Inbox, 20, 0),
                    Request(LinkBrowseDestination.Inbox, 20, 0),
                ),
                linksRepository.requests,
            )
        }
    }

    @Test
    fun saveLinkOmitsReadWhenUncheckedAndShowsRateLimitFailure() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "1", title = "Inbox article", read = false))),
        )
        linksRepository.saveResult = ShioriApiResult.Failure(
            ShioriApiError.RateLimited(retryAfterSeconds = 45),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")

            setText(scenario, R.id.add_link_url_input, "https://example.com/22")
            clickButton(scenario, R.id.add_link_button)

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Shiori rate limited link saves. Wait about 45 seconds before trying again. The documented limit is 30 per minute.",
            )
            assertLoadedTitles(scenario, "Inbox article")
            assertEquals(
                CreateLinkRequest(
                    url = "https://example.com/22",
                    title = null,
                    read = null,
                ),
                linksRepository.savedRequests.single(),
            )
            assertEquals(listOf(Request(LinkBrowseDestination.Inbox, 20, 0)), linksRepository.requests)
        }
    }

    @Test
    fun saveLinkShowsValidationFailureWithoutChangingLoadedItems() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "17", title = "Inbox article", read = false))),
        )
        linksRepository.saveResult = ShioriApiResult.Failure(ShioriApiError.Validation)

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")

            setText(scenario, R.id.add_link_url_input, "https://example.com/invalid")
            clickButton(scenario, R.id.add_link_button)

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Shiori rejected this link. Check the URL and try again.",
            )
            assertLoadedTitles(scenario, "Inbox article")
            assertEquals(
                CreateLinkRequest(
                    url = "https://example.com/invalid",
                    title = null,
                    read = null,
                ),
                linksRepository.savedRequests.single(),
            )
            assertEquals(listOf(Request(LinkBrowseDestination.Inbox, 20, 0)), linksRepository.requests)
        }
    }

    @Test
    fun saveLinkRejectsInvalidUrlWithoutCallingRepository() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")

            setText(scenario, R.id.add_link_url_input, "not-a-url")
            clickButton(scenario, R.id.add_link_button)

            waitForText(scenario, R.id.browser_state_text, "No links match this filter yet.")
            assertTrue(linksRepository.savedRequests.isEmpty())
            assertEquals(listOf(Request(LinkBrowseDestination.Inbox, 20, 0)), linksRepository.requests)
        }
    }

    @Test
    fun sharedSendIntentUsesExistingSaveWorkflowOnColdLaunch() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "30", title = "Shared article", read = false))),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "30", title = "Shared article", read = false))),
        )
        linksRepository.saveResult = ShioriApiResult.Success(
            CreateLinkResponse(
                success = true,
                linkId = "30",
                duplicate = false,
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
                        read = null,
                    ),
                ),
                linksRepository.savedRequests.toList(),
            )
            assertLoadedTitles(scenario, "Shared article")
        }
    }

    @Test
    fun sharedSendIntentShowsUnauthorizedSaveFeedback() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )
        linksRepository.saveResult = ShioriApiResult.Failure(ShioriApiError.Unauthorized)

        val launchIntent = Intent(Intent.ACTION_SEND).apply {
            setClassName(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java.name,
            )
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/unauthorized")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Your API key is no longer authorized. Update it in API access.",
            )
            assertEquals(
                CreateLinkRequest(
                    url = "https://example.com/unauthorized",
                    title = null,
                    read = null,
                ),
                linksRepository.savedRequests.single(),
            )
        }
    }

    @Test
    fun sharedSendIntentShowsConflictSaveFeedback() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )
        linksRepository.saveResult = ShioriApiResult.Failure(ShioriApiError.Conflict)

        val launchIntent = Intent(Intent.ACTION_SEND).apply {
            setClassName(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java.name,
            )
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/conflict")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Shiori is still processing this link. Wait for that work to finish, then try saving it again.",
            )
            assertEquals("https://example.com/conflict", linksRepository.savedRequests.single().url)
        }
    }

    @Test
    fun sharedSendIntentShowsRateLimitedSaveFeedback() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )
        linksRepository.saveResult = ShioriApiResult.Failure(
            ShioriApiError.RateLimited(retryAfterSeconds = 12),
        )

        val launchIntent = Intent(Intent.ACTION_SEND).apply {
            setClassName(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java.name,
            )
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/rate-limited")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Shiori rate limited link saves. Wait about 12 seconds before trying again. The documented limit is 30 per minute.",
            )
            assertEquals("https://example.com/rate-limited", linksRepository.savedRequests.single().url)
        }
    }

    @Test
    fun browseRateLimitUsesDocumentedResetHeaderTiming() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        val nowEpochSeconds = System.currentTimeMillis() / 1000
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            ShioriApiResult.Failure(
                ShioriApiError.RateLimited(resetAtEpochSeconds = nowEpochSeconds + 61),
            ),
        )

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(
                scenario,
                R.id.browser_state_text,
                "Shiori rate limited link requests. Wait about 2 minutes before trying again. The documented limit is 60 per minute.",
            )
            waitForText(scenario, R.id.load_more_button, activityString(R.string.action_retry_links))
        }
    }

    @Test
    fun saveLinkServerFailureStaysDistinctFromRateLimitOrAuthFailures() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "1", title = "Inbox article", read = false))),
        )
        linksRepository.saveResult = ShioriApiResult.Failure(ShioriApiError.Server(500))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForText(scenario, R.id.browser_state_text, "Loaded 1 of 1 links.")

            setText(scenario, R.id.add_link_url_input, "https://example.com/server-error")
            clickButton(scenario, R.id.add_link_button)

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Shiori hit a server error while saving this link. Wait a moment and try again.",
            )
            assertLoadedTitles(scenario, "Inbox article")
        }
    }

    @Test
    fun sharedSendIntentShowsNetworkSaveFeedback() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )
        linksRepository.saveResult = ShioriApiResult.Failure(ShioriApiError.Network(RuntimeException("offline")))

        val launchIntent = Intent(Intent.ACTION_SEND).apply {
            setClassName(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java.name,
            )
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://example.com/network")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Could not reach your Shiori server. Check the connection and try again.",
            )
            assertEquals("https://example.com/network", linksRepository.savedRequests.single().url)
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
    fun sharedSendMultipleShowsUnsupportedFeedbackWithoutSaving() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )

        val launchIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            setClassName(
                InstrumentationRegistry.getInstrumentation().targetContext,
                MainActivity::class.java.name,
            )
            type = "text/plain"
            putCharSequenceArrayListExtra(
                Intent.EXTRA_TEXT,
                arrayListOf<CharSequence>("https://example.com/one", "https://example.com/two"),
            )
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            waitForText(
                scenario,
                R.id.add_link_status_text,
                "This share did not include a supported HTTP or HTTPS URL.",
            )
            assertTrue(linksRepository.savedRequests.isEmpty())
        }
    }

    @Test
    fun sharedUrlWaitsForAccessSetupBeforeSaving() {
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )
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
            putExtra(Intent.EXTRA_TEXT, "https://example.com/pending")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            waitForText(
                scenario,
                R.id.status_text,
                "Save a valid API key before importing the shared URL.",
            )
            assertTrue(linksRepository.savedRequests.isEmpty())

            enterAccess(scenario, "test-api-key")
            onView(withId(R.id.save_button)).perform(scrollTo(), click())

            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Link saved. Showing it in your inbox.",
            )
            assertEquals("https://example.com/pending", linksRepository.savedRequests.single().url)
        }
    }

    @Test
    fun viewIntentImportsUrlWhenAppLaunchesFromDeepLink() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "40", title = "Viewed article", read = false))),
        )
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 1, links = listOf(link(id = "40", title = "Viewed article", read = false))),
        )
        linksRepository.saveResult = ShioriApiResult.Success(
            CreateLinkResponse(
                success = true,
                linkId = "40",
                duplicate = true,
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

    @Test
    fun duplicateSharedIntentIsIgnoredWhenHandledAgain() {
        store.saveConfig(ApiAccessConfig("https://shiori.example.com", "test-api-key"))
        linksRepository.enqueue(
            LinkBrowseDestination.Inbox,
            0,
            page(limit = 20, offset = 0, total = 0, links = emptyList()),
        )
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
            putExtra(Intent.EXTRA_TEXT, "https://example.com/once")
        }

        ActivityScenario.launch<MainActivity>(launchIntent).use { scenario ->
            waitForText(
                scenario,
                R.id.add_link_status_text,
                "Link saved. Showing it in your inbox.",
            )

            invokePrivateMethod(scenario, "handleIncomingIntent", launchIntent)

            assertEquals(1, linksRepository.savedRequests.size)
            assertEquals("https://example.com/once", linksRepository.savedRequests.single().url)
        }
    }

    private fun enterAccess(
        scenario: ActivityScenario<MainActivity>,
        apiKey: String,
    ) {
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
        id: String,
        title: String,
        read: Boolean,
        status: String = "ready",
    ) = LinkResponse(
        id = id,
        url = "https://example.com/$id",
        title = title,
        summary = "Summary $id",
        domain = "example.com",
        status = status,
        createdAt = "2026-03-06T10:00:00Z",
        updatedAt = "2026-03-06T11:00:00Z",
        readAt = if (read) "2026-03-06T12:00:00Z" else null,
    )

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
        val updateRequests = CopyOnWriteArrayList<Pair<String, UpdateLinkRequest>>()
        val restoreRequests = CopyOnWriteArrayList<String>()
        val deleteRequests = CopyOnWriteArrayList<String>()
        private val responses = mutableMapOf<Request, ArrayDeque<ShioriApiResult<LinkListResponse>>>()
        var saveResult: ShioriApiResult<CreateLinkResponse> = ShioriApiResult.Success(
            CreateLinkResponse(success = true, linkId = "99"),
        )
        var bulkUpdateResult: ShioriApiResult<List<LinkResponse>> = ShioriApiResult.Success(emptyList())
        var updateLinkResult: ShioriApiResult<LinkMutationResponse> = ShioriApiResult.Success(
            LinkMutationResponse(success = true, message = "Link updated", linkId = "99"),
        )
        var restoreLinkResult: ShioriApiResult<LinkMutationResponse> = ShioriApiResult.Success(
            LinkMutationResponse(success = true, message = "Link restored", linkId = "99"),
        )
        var deleteLinkResult: ShioriApiResult<DeleteLinkResponse> = ShioriApiResult.Success(
            DeleteLinkResponse(linkId = "99", message = "Link deleted"),
        )
        var emptyTrashResult: ShioriApiResult<EmptyTrashResponse> = ShioriApiResult.Success(
            EmptyTrashResponse(deleted = 0, message = "Trash emptied"),
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
            ids: List<String>,
            read: Boolean,
        ): ShioriApiResult<List<LinkResponse>> {
            bulkUpdateRequests += BulkUpdateRequest(ids = ids, read = read)
            return bulkUpdateResult
        }

        override suspend fun updateLink(
            config: ApiAccessConfig,
            id: String,
            request: UpdateLinkRequest,
        ): ShioriApiResult<LinkMutationResponse> {
            updateRequests += id to request
            return updateLinkResult
        }

        override suspend fun restoreLink(
            config: ApiAccessConfig,
            id: String,
        ): ShioriApiResult<LinkMutationResponse> {
            restoreRequests += id
            return restoreLinkResult
        }

        override suspend fun deleteLink(
            config: ApiAccessConfig,
            id: String,
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
        val ids: List<String>,
        val read: Boolean,
    )
}
