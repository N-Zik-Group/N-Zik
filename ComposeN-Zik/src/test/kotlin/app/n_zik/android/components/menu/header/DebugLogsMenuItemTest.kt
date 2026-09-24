package app.n_zik.android.components.menu.header

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.edit
import androidx.compose.foundation.shape.CircleShape
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.logDebugEnabledKey
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.R
import app.n_zik.android.utils.debug.debugLogFiles
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Compose UI tests for [DebugLogsMenuItem], the "Enable debug logs" entry of the
 * hamburger menu.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 *
 * Covers the spec's burger-item matrix rows: the label follows the live state (enable
 * off / disable on, no restart), the tap toggles debug logging with the same side
 * effects as the header badge (log files purged on disable) and flips the label.
 *
 * The width clamp of the label (capped at the rendered "Rescue Center" label width,
 * `clampedLabelMaxWidth`) is shared with the header badges and covered through
 * [app.n_zik.android.components.ui.header.HeaderVersionBadgeTest]; it is not
 * observable through this item's own semantics because the clickable Row merges its
 * descendants (material3 convention) and Robolectric's text measurement is degenerate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DebugLogsMenuItemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs: SharedPreferences get() = context.preferences
    private val logsDir: File get() = File(context.filesDir, "logs")

    private val enableLabel: String get() = context.getString(R.string.enable_log_debug)
    private val disableLabel: String get() = context.getString(R.string.disable_log_debug)

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @After
    fun tearDown() {
        debugLogFiles(logsDir).forEach { it.delete() }
        prefs.edit { remove(logDebugEnabledKey) }
    }

    private fun showItem() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                DebugLogsMenuItem(onLongClick = {})
            }
        }
    }

    @Test
    fun itemShowsEnableLabelWhenOff() {
        showItem()

        composeRule.onNodeWithText(enableLabel).assertIsDisplayed()
        composeRule.onNodeWithText(disableLabel).assertDoesNotExist()
    }

    @Test
    fun labelFollowsTheDebugLoggingStateLive() {
        // Seeded from disk before composition (cold start scenario)
        prefs.edit { putBoolean(logDebugEnabledKey, true) }
        showItem()
        composeRule.onNodeWithText(disableLabel).assertIsDisplayed()

        // Flip from outside the menu (settings / badge path) — no restart
        prefs.edit { putBoolean(logDebugEnabledKey, false) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(enableLabel).assertIsDisplayed()

        prefs.edit { putBoolean(logDebugEnabledKey, true) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(disableLabel).assertIsDisplayed()
    }

    @Test
    fun tapDisablesDebugLoggingAndPurgesLogFiles() {
        logsDir.mkdirs()
        debugLogFiles(logsDir).forEach { it.writeText("log") }
        prefs.edit { putBoolean(logDebugEnabledKey, true) }
        showItem()
        composeRule.onNodeWithText(disableLabel).assertIsDisplayed()

        composeRule.onNodeWithText(disableLabel).performClick()
        composeRule.waitForIdle()

        assertFalse("tap should switch the preference off", prefs.getBoolean(logDebugEnabledKey, true))
        debugLogFiles(logsDir).forEach { file ->
            assertFalse("log file should be purged on disable: $file", file.exists())
        }
        // The label flips back to the enable state live
        composeRule.onNodeWithText(enableLabel).assertIsDisplayed()
        composeRule.onNodeWithText(disableLabel).assertDoesNotExist()
    }
}
