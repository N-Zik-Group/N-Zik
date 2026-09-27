package app.n_zik.android.components.menu.header

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [MaintenanceMenuItem], the "Maintenance" entry of the hamburger
 * menu.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 *
 * Covers the burger-item contract: a short tap opens the Maintenance sheet AND closes
 * the menu (onOpen + onConsume), a long press deep-links to the Maintenance card in
 * the Misc settings (onLongClick, no consume).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class MaintenanceMenuItemTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val label: String get() = context.getString(R.string.maintenance)

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private fun showItem(
        onOpen: () -> Unit,
        onLongClick: () -> Unit,
        onConsume: () -> Unit,
    ) {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                MaintenanceMenuItem(
                    onOpen = onOpen,
                    onLongClick = onLongClick,
                    onConsume = onConsume,
                )
            }
        }
    }

    @Test
    fun itemShowsItsLabel() {
        showItem(onOpen = {}, onLongClick = {}, onConsume = {})

        composeRule.onNodeWithText(label).assertIsDisplayed()
    }

    @Test
    fun shortTapOpensTheSheetAndClosesTheMenu() {
        var opened = false
        var consumed = false
        var longClicked = false
        showItem(
            onOpen = { opened = true },
            onLongClick = { longClicked = true },
            onConsume = { consumed = true },
        )

        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()

        assertTrue("a short tap should open the sheet", opened)
        assertTrue("a short tap should consume the menu (close it)", consumed)
        assertFalse("a short tap must not be reported as a long press", longClicked)
    }

    @Test
    fun longPressDeepLinksToTheMaintenanceCard() {
        var opened = false
        var consumed = false
        var longClicked = false
        showItem(
            onOpen = { opened = true },
            onLongClick = { longClicked = true },
            onConsume = { consumed = true },
        )

        // The ui-test API of this Compose version has no performLongClick(): inject a real
        // long-press touch gesture (held past the long-press timeout) on the node center.
        composeRule.onNodeWithText(label).performTouchInput { longClick() }
        composeRule.waitForIdle()

        assertTrue("a long press should deep-link to the Maintenance card", longClicked)
        assertFalse("a long press must not open the sheet", opened)
        assertFalse("a long press must not consume the menu", consumed)
    }
}
