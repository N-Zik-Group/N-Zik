package app.n_zik.android.components.ui.screens.rescue

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.profiles.profileSecurePrefs
import es.dmoral.toasty.Toasty
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Seeds the second profile before the activity composes (the application onCreate
 * runs before the rule's activity launch). Public on purpose: Robolectric
 * instantiates the application reflectively, so a private class is inaccessible. */
class SeededApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        File(filesDir, "Profiles_names.txt").writeText("work\n")
    }
}

/**
 * Compose UI test for the per-profile settings actions of [RescueScreen]
 * (spec-profiles-page-face, second review pass).
 *
 * Regression: the credentials behind the settings export / import / reset were
 * resolved from the ACTIVE profile's encrypted store, so a "Reset settings" aimed
 * at another profile cleared the active profile's credentials instead (with no
 * backup of them) while leaving the target's store untouched. The secure store
 * must be keyed on the selected rescue target.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine on
 * the JUnit 5 platform. The rule launches the real [RescueActivity]. No alive marker is
 * planted: with the marker absent the process guard releases immediately, so the click
 * reaches the confirmation dialog. [SeededApplication] plants a second profile before
 * the activity composes, so the target selector offers it from the first read. The
 * keystore-backed [profileSecurePrefs] is the only stub (a JVM has no keystore).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = SeededApplication::class)
class RescueScreenResetSettingsTargetTest {

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

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `reset settings on a non-active target clears the target secure store not the active one`() {
        val workEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        val workSecure = mockk<SharedPreferences>()
        every { workSecure.edit() } returns workEditor
        // A relaxed mock would answer clear() with a fresh child mock, and the
        // subsequent commit() would land on that child instead of workEditor.
        every { workEditor.clear() } returns workEditor
        mockkStatic("app.n_zik.android.components.ui.screens.profiles.ProfileSecurePrefsKt")
        every { profileSecurePrefs(any(), "work") } returns workSecure
        // The success toast resumes on the compose test dispatcher's worker thread
        // (no Looper prepared there): stub it out — the store clearing is the assertion.
        mockkStatic("es.dmoral.toasty.Toasty")
        every {
            Toasty.success(any<Context>(), any<CharSequence>(), any<Int>(), any<Boolean>())
        } returns mockk<Toast>(relaxed = true)

        // The screen opens on the All category and "Reset settings" lives in the Per-profile
        // category: switch to it, select the "work" target, then reset. The selector options
        // load on the real DATA pool: wait for the chip before clicking it.
        composeRule
            .onNodeWithText(context.getString(R.string.rescue_category_profile))
            .performClick()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            if (runCatching { composeRule.onNodeWithText("work").assertIsDisplayed() }.isSuccess) break
            Thread.sleep(50)
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("work").assertIsDisplayed()
        composeRule.onNodeWithText("work").performClick()
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(context.getString(R.string.rescue_reset_settings))
            .performScrollTo()
        composeRule
            .onNodeWithText(context.getString(R.string.rescue_reset_settings))
            .performClick()
        composeRule.waitForIdle()
        // Confirm with the platform "OK" button: matched case-insensitively because the
        // platform string capitalization is not pinned by the app.
        composeRule.onNodeWithText("ok", substring = true, ignoreCase = true).performClick()

        // The reset runs on the compose scope with a hop to the real DATA pool: poll until
        // the target secure store has been cleared.
        val resetDeadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < resetDeadline) {
            if (runCatching { verify { workEditor.clear() } }.isSuccess) break
            Thread.sleep(50)
        }
        verify { workEditor.clear() }
        verify { workEditor.commit() }
        // The active profile's secure store must never be opened by an action aimed at
        // another profile.
        verify(exactly = 0) { profileSecurePrefs(any(), "default") }
    }
}
