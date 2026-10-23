package app.n_zik.android.components.ui.screens.rescue

import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.n_zik.android.R
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI test for the profile selection dialog of [RescueScreen]
 * (gh-896 — the dialog renders on the shared canon frame).
 *
 * The OK pill must stay inert until a profile is checked: on the shared
 * [ButtonRow] the disabled pill stays visible (dimmed) but never fires the
 * reset. Regression guard for the migration: a pill that is always enabled
 * — or always disabled — would slip past the visual check.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine on
 * the JUnit 5 platform. The rule launches the real [RescueActivity]. No alive marker is
 * planted: with the marker absent the process guard releases immediately, so the click
 * reaches the selection dialog. [SeededApplication] (RescueScreenResetSettingsTargetTest)
 * plants a second profile before the activity composes, so the dialog offers two rows
 * (the base + "work").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SeededApplication::class)
class RescueScreenProfilesSelectionTest {

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
    fun `reset profiles ok pill stays disabled until a profile is checked`() {
        composeRule.waitForIdle()

        // Open the "Reset profiles" selection dialog (ALL category — the default).
        composeRule
            .onNodeWithText(context.getString(R.string.rescue_reset_profiles))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        val okPill = composeRule.onNodeWithText(context.getString(android.R.string.ok))
        val cancelPill = composeRule.onNodeWithText(context.getString(android.R.string.cancel))

        // Nothing is checked yet: the OK pill is visible but inert, cancel is armed.
        okPill.assertIsNotEnabled()
        cancelPill.assertIsEnabled()

        // Checking the base row arms the OK pill. The checkbox carries a test tag:
        // this resolved Compose version registers no role on clickable nodes

        // (see YtSearchAccountToggleTest), so the checkbox is selected by tag.
        composeRule.onNodeWithTag("resetProfilesCheckbox_$DEFAULT_PROFILE_ID").performClick()
        composeRule.waitForIdle()
        okPill.assertIsEnabled()
    }
}
