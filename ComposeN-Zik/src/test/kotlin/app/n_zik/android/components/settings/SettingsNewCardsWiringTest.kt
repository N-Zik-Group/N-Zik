package app.n_zik.android.components.settings

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.BurgerMenuSettingsDialog
import app.n_zik.android.components.ui.screens.rescue.RescueActivity
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.shortcuts.ACTION_RESCUE
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Wiring tests for the settings cards added with the customizable burger menu
 * (spec-customizable-burger-menu, goal B): the Offline Mode card's entry persists the
 * per-profile full-offline flag the burger item also flips, the Rescue Center card's
 * entry launches the Rescue Activity with the rescue action (same intent as the burger
 * item and the launcher shortcut), and the Burger Menu card opens the customizer dialog
 * and - through its DisposableEffect - closes it again when the card leaves composition
 * (a stale isActive must not re-open the dialog on the next visit).
 *
 * JUnit 4 + [RobolectricTestRunner] through the project's junit-vintage-engine on the JUnit 5
 * platform (`createComposeRule()` only works with JUnit 4), with the plain [Application] so
 * the app's heavy init is skipped (same harness as [RewindSettingsCardLayoutTest]). The
 * cards' card title and entry label share the same text, so the entry is clicked through
 * its unique description text. The Compose clock is frozen: the dialog's rows run an
 * infinite marquee on overflow, which would keep a self-advancing clock from ever
 * settling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SettingsNewCardsWiringTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Application = ApplicationProvider.getApplicationContext()

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Before
    fun setUp() {
        // MainApplication is final, so Dependencies.application is a mock wired to this
        // Robolectric app (the dialog host resolves app-wide state through the locator).
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns context
        Dependencies.init(mainApplication)
    }

    @After
    fun tearDown() {
        unmockkAll()
        // The dialog is a process-wide singleton: never carry a stale isActive into the
        // next test.
        BurgerMenuSettingsDialog.hideDialog()
        context.preferences.edit().remove(fullOfflineModeKey).commit()
    }

    private fun content(block: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                block()
            }
        }
    }

    @Test
    fun theOfflineModeCardEntryPersistsThePerProfileFlag() {
        content { OfflineModeSettingsCard() }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(context.getString(R.string.offline_mode_description))
            .performClick()
        composeRule.waitForIdle()

        assertTrue(
            "the card entry must persist the per-profile full offline flag",
            context.preferences.getBoolean(fullOfflineModeKey, false)
        )
    }

    @Test
    fun theRescueCenterCardEntryLaunchesTheRescueActivity() {
        content { RescueCenterSettingsCard() }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(context.getString(R.string.rescue_center_description))
            .performClick()
        composeRule.waitForIdle()

        val intent = Shadows.shadowOf(context).getNextStartedActivity()
        assertEquals("the entry must start the rescue activity with the rescue action", ACTION_RESCUE, intent.action)
        assertEquals("the entry must target the Rescue Activity", RescueActivity::class.java.name, intent.component?.className)
    }

    @Test
    fun theBurgerMenuCardOpensTheDialogAndClosesItWhenItLeavesComposition() {
        // The rule's setContent is single-shot (Compose 1.12), so the card stays behind
        // an observed test-side flag a helper button flips after the first assertions.
        var showCard by mutableStateOf(true)
        content {
            Column {
                if (showCard) BurgerMenuSettingsCard()
                TextButton(onClick = { showCard = false }) {
                    Text("hide card")
                }
            }
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText(context.getString(R.string.burger_menu_customize))
            .performClick()
        composeRule.waitForIdle()
        assertTrue("the card entry must open the customizer dialog", BurgerMenuSettingsDialog.isActive)

        // In this harness the helper button lays out zero-size (the card's animated
        // section consumes the row), so its click is dispatched through the semantics
        // action instead of coordinates (same pattern as ArtistRoundnessSearchGateTest).
        composeRule
            .onNode(hasClickAction() and hasText("hide card"))
            .performSemanticsAction(SemanticsActions.OnClick)
        // The frozen clock runs no recomposition until it is advanced, and the card
        // removal (with the DisposableEffect that closes the dialog) is exactly that
        // recomposition: pump it in. Bounded so the dialog's infinite marquee cannot hang
        // the loop (the reason the clock is frozen in this class).
        val deadline = System.currentTimeMillis() + 15_000
        while (BurgerMenuSettingsDialog.isActive && System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(250)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
        }
        assertFalse(
            "removing the card from composition must close the dialog (the onDispose contract)",
            BurgerMenuSettingsDialog.isActive
        )
    }
}
