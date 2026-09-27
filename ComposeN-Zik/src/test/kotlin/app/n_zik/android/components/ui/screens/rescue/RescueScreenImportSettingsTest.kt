package app.n_zik.android.components.ui.screens.rescue

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Compose UI test for the "Import settings" action of [RescueScreen].
 *
 * Regression: the document picker was launched filtered to text/csv + text/plain. A
 * settings file the provider reports under another MIME (e.g. text/comma-separated-values,
 * which the app's ImportSettings accepts) was hidden or greyed out in the picker and could
 * not be selected. The rescue picker must accept the same MIME types as the app's
 * ImportSettings — copied here (the :rescue process stays independent of components.import).
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine on
 * the JUnit 5 platform. The rule launches the real [RescueActivity]. No alive marker is
 * planted: with the marker absent the process guard releases immediately, so the click
 * reaches the confirmation dialog instead of the "main process running" warning (and
 * [RescueActivity.onCreate] records no kill request — a dead process has none).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RescueScreenImportSettingsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<RescueActivity>()

    // Resolved in setUp(): under Robolectric the app's binary resources are bound when the
    // activity launches (the rule's before()), so an app-resource lookup in a property
    // initializer (which runs before the rule) hits a resource table without the app package.
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `import settings launches the document picker with the app's mime filter`() {
        val importTitle = context.getString(R.string.rescue_import_settings)
        val confirmText = context.getString(R.string.rescue_confirm_import_settings)

        // No alive marker on disk: the process guard is released, so the click surfaces the
        // confirmation dialog (if the guard were still active, the dialog would never appear).
        composeRule.onNodeWithText(importTitle).performScrollTo()
        composeRule.onNodeWithText(importTitle).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(confirmText).assertIsDisplayed()

        // Confirm with the platform "OK" button: matched case-insensitively because the
        // platform string capitalization is not pinned by the app.
        composeRule.onNodeWithText("ok", substring = true, ignoreCase = true).performClick()
        composeRule.waitForIdle()

        val started =
            Shadows.shadowOf(composeRule.activity).peekNextStartedActivityForResult()?.intent
        assertNotNull("the confirmation must launch the document picker", started)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, started!!.action)
        // The OpenDocument contract always sets the type to */* and carries the filter in
        // EXTRA_MIME_TYPES: that list must match the app's ImportSettings picker, so a file
        // the app can import is selectable here too.
        assertEquals("*/*", started.type)
        // The contract stores the filter as a String[] extra (putExtra(String, String[])
        // overload), so it must be read back with getStringArrayExtra — the parcelable
        // variant is always null here.
        val mimeFilter = started.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)
        assertEquals(
            "the rescue picker must accept the same MIME types as the app's ImportSettings " +
                "(the old text/csv + text/plain list hid files reported as " +
                "text/comma-separated-values, making them unselectable)",
            listOf(
                "text/csv",
                "text/comma-separated-values",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            ),
            mimeFilter?.toList()
        )
    }
}
