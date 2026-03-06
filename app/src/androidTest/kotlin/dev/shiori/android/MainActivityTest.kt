package dev.shiori.android

import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    private lateinit var store: EncryptedApiAccessStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        store = EncryptedApiAccessStore(context)
        store.clearAll()
        AppDependencies.resetForTests()
    }

    @After
    fun tearDown() {
        store.clearAll()
        AppDependencies.resetForTests()
    }

    @Test
    fun apiKeyCanBeSavedRestoredAndCleared() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            enterAccess("https://shiori.example.com", "test-api-key")
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            onView(withId(R.id.status_text)).check(matches(withText(R.string.message_saved_access)))
            onView(withId(R.id.continue_button)).check(matches(isEnabled()))

            scenario.recreate()

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
            clickValidateButton(scenario)
            waitForStatusText(scenario, R.string.message_validation_unauthorized)
        }
    }

    @Test
    fun validationSurfacesGenericFailures() {
        AppDependencies.overrideCheckerForTests(FakeApiConnectionChecker(ApiValidationStatus.Failure))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            enterAccess("https://shiori.example.com", "test-api-key")
            onView(withId(R.id.save_button)).perform(scrollTo(), click())
            clickValidateButton(scenario)
            waitForStatusText(scenario, R.string.message_validation_failure)
        }
    }

    private fun enterAccess(serverUrl: String, apiKey: String) {
        onView(withId(R.id.server_url_input)).perform(scrollTo(), click(), replaceText(serverUrl))
        closeSoftKeyboard()
        onView(withId(R.id.api_key_input)).perform(scrollTo(), click(), replaceText(apiKey))
        closeSoftKeyboard()
    }

    private fun waitForStatusText(
        scenario: ActivityScenario<MainActivity>,
        messageResId: Int,
        timeoutMs: Long = 5_000,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val deadline = System.currentTimeMillis() + timeoutMs
        val expectedText = instrumentation.targetContext.getString(messageResId)
        var lastText = ""

        while (System.currentTimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                lastText = activity.findViewById<TextView>(R.id.status_text).text.toString()
            }

            if (lastText == expectedText) {
                return
            }

            Thread.sleep(100)
        }

        throw AssertionError("Status text did not update within ${timeoutMs}ms. Last text was '$lastText'.")
    }

    private fun clickValidateButton(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            activity.findViewById<MaterialButton>(R.id.validate_button).performClick()
        }
    }

    private class FakeApiConnectionChecker(
        private val result: ApiValidationStatus,
    ) : ApiConnectionChecker {
        override suspend fun validate(serverUrl: String, apiKey: String): ApiValidationStatus = result
    }
}
