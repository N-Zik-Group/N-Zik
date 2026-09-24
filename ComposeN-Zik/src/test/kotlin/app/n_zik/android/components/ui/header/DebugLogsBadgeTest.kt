package app.n_zik.android.components.ui.header

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.utils.debug.debugLogFiles
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Compose UI tests for [DebugLogsBadge], the reactive "DEBUG" badge of the app header.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 *
 * Covers the spec's I/O matrix rows: ON_DISPLAY, TAP_DISABLE, OFF_HIDDEN,
 * LONG_TITLE (marquee clamp in tight space) and COLD_START (disk-seeded state).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DebugLogsBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs: SharedPreferences get() = context.preferences
    private val logsDir: File get() = File(context.filesDir, "logs")

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Before
    fun setUp() {
        debugLogFiles(logsDir).forEach { it.delete() }
        prefs.edit { putBoolean(logDebugEnabledKey, false) }
    }

    @After
    fun tearDown() {
        debugLogFiles(logsDir).forEach { it.delete() }
        prefs.edit { remove(logDebugEnabledKey) }
    }

    private fun showBadge() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                DebugLogsBadge()
            }
        }
    }

    @Test
    fun badgeIsShownWhenDebugLoggingIsEnabled() {
        // Seeded from disk before composition (cold start scenario)
        prefs.edit { putBoolean(logDebugEnabledKey, true) }
        showBadge()

        composeRule.onNodeWithText("DEBUG").assertIsDisplayed()
    }

    @Test
    fun badgeIsHiddenWhenDebugLoggingIsDisabled() {
        showBadge()

        composeRule.onNodeWithText("DEBUG").assertDoesNotExist()
    }

    @Test
    fun tapDisablesDebugLoggingAndPurgesLogFiles() {
        logsDir.mkdirs()
        debugLogFiles(logsDir).forEach { it.writeText("log") }
        prefs.edit { putBoolean(logDebugEnabledKey, true) }
        showBadge()

        composeRule.onNodeWithText("DEBUG").performClick()
        composeRule.waitForIdle()

        assertFalse("tap should switch the preference off", prefs.getBoolean(logDebugEnabledKey, true))
        debugLogFiles(logsDir).forEach { file ->
            assertFalse("log file should be purged on disable: $file", file.exists())
        }
        composeRule.onNodeWithText("DEBUG").assertDoesNotExist()
    }

    @Test
    fun badgeFollowsPreferenceChangesLive() {
        prefs.edit { putBoolean(logDebugEnabledKey, true) }
        showBadge()
        composeRule.onNodeWithText("DEBUG").assertIsDisplayed()

        // Flip from outside the header (settings / burger menu path) — no restart
        prefs.edit { putBoolean(logDebugEnabledKey, false) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("DEBUG").assertDoesNotExist()

        prefs.edit { putBoolean(logDebugEnabledKey, true) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("DEBUG").assertIsDisplayed()
    }

    @Test
    fun badgeIsClampedInTightHeaderSpace() {
        // LONG_TITLE matrix row: a title wider than the space left in the header must be
        // clamped in place instead of growing the badge (header stays intact). The
        // marquee itself is draw-only (BasicMarquee animates the drawing offset, not the
        // layout), so only the clamp is observable in a unit test.
        prefs.edit { putBoolean(logDebugEnabledKey, true) }
        // No frame is pumped for the marquee: the width assertion needs no animation.
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(modifier = Modifier.width(40.dp)) {
                    DebugLogsBadge()
                }
            }
        }

        val badgeContainer = composeRule.onNodeWithText("DEBUG").onParent()
        // The badge is clamped to the 40dp container — it never pushes the header layout.
        badgeContainer.assertWidthIsEqualTo(40.dp)
    }
}
