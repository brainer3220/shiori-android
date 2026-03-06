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
import dev.shiori.android.corenetwork.LinkListResponse
import dev.shiori.android.corenetwork.LinkResponse
import dev.shiori.android.corenetwork.ShioriApiResult
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
            enterAccess("https://shiori.example.com", "test-api-key")
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
        AppDependencies.overrideCheckerForTests(FakeApiConnectionChecker(ApiValidationStatus.Unauthorized))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            enterAccess("https://shiori.example.com", "test-api-key")
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            clickButton(scenario, R.id.validate_button)
            waitForText(scenario, R.id.status_text, activityString(R.string.message_validation_unauthorized))
        }
    }

    @Test
    fun validationSurfacesGenericFailures() {
        AppDependencies.overrideCheckerForTests(FakeApiConnectionChecker(ApiValidationStatus.Failure))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            enterAccess("https://shiori.example.com", "test-api-key")
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            clickButton(scenario, R.id.validate_button)
            waitForText(scenario, R.id.status_text, activityString(R.string.message_validation_failure))
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

    private fun enterAccess(serverUrl: String, apiKey: String) {
        onView(withId(R.id.server_url_input)).perform(scrollTo(), click(), replaceText(serverUrl))
        closeSoftKeyboard()
        onView(withId(R.id.api_key_input)).perform(scrollTo(), click(), replaceText(apiKey))
        closeSoftKeyboard()
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
        expectedSummary: String,
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

    private class FakeApiConnectionChecker(
        private val result: ApiValidationStatus,
    ) : ApiConnectionChecker {
        override suspend fun validate(serverUrl: String, apiKey: String): ApiValidationStatus = result
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
        private val responses = mutableMapOf<Request, ArrayDeque<ShioriApiResult<LinkListResponse>>>()
        var saveResult: ShioriApiResult<CreateLinkResponse> = ShioriApiResult.Success(
            CreateLinkResponse(link = LinkResponse(id = 99, url = "https://example.com/99", read = false)),
        )

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
    }

    private data class Request(
        val destination: LinkBrowseDestination,
        val limit: Int,
        val offset: Int,
    )
}
