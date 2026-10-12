package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.screens.settings.AppearanceSettings
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.n_zik.android.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the "Cover & Thumbnail" search gate (`searchCtx_0_2`) in
 * [AppearanceSettings].
 *
 * The section gate sits UPSTREAM of the per-row filter: the `AnimatedVisibility` wrapping the
 * whole card removes it from composition before the artist-roundness row's own `contains`
 * condition is ever evaluated. If `R.string.artist_roundness` is missing from the gate, a
 * query equal to the row's displayed label filters the card out and the row never composes —
 * the row-level filter fix alone would be behaviorally inert for exactly that query.
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@OptIn(ExperimentalAnimationApi::class)
class ArtistRoundnessSearchGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val artistRoundnessLabel: String = context.getString(R.string.artist_roundness)

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    /**
     * The app reads `playerType` from the per-profile preferences file; with the default
     * profile (`getActiveProfile` → "default") the file name is "preferences" and the enum
     * is stored as its name (see `putEnum`).
     */
    private fun appearanceSettings(playerType: String) {
        context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
            .edit()
            .putString(playerTypeKey, playerType)
            .apply()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                AppearanceSettings(navController = rememberNavController())
            }
        }
    }

    @Test
    fun artistRoundnessRowIsComposedWhenSearchMatchesItsLabel() {
        appearanceSettings("Modern")

        // The search bar starts hidden; its toggle is the first enabled clickable on the
        // screen (the header icon is disabled and the description is plain text).
        composeRule.onAllNodes(hasClickAction() and isEnabled())[0].performSemanticsAction(SemanticsActions.OnClick)

        // Type the row's displayed label into the search field (the only editable node).
        composeRule.onNode(isEditable()).performTextInput(artistRoundnessLabel)

        // The label now matches twice: the typed text in the search field (editable) and
        // the row title (plain text). If the gate — or the row-level filter — regresses,
        // the row title disappears and only the search field remains.
        // `fetchSemanticsNodes()` (not a display assertion): the row is deep in the
        // settings screen, outside the Robolectric test viewport — the behavior under
        // test is composition itself.
        val matches = composeRule.onAllNodesWithText(artistRoundnessLabel).fetchSemanticsNodes()
        assertEquals(2, matches.size)
    }

    @Test
    fun artistRoundnessRowIsRemovedWhenSearchMatchesNoCardLabel() {
        appearanceSettings("Modern")

        composeRule.onAllNodes(hasClickAction() and isEnabled())[0].performSemanticsAction(SemanticsActions.OnClick)

        // A query that is a substring of no label in the card's gate (and of no row
        // filter): the card gate removes the whole card from composition, so the row
        // title never composes. If the gate — or the row-level filter — regressed to
        // "always visible", the row title would appear and the count would be 1.
        composeRule.onNode(isEditable()).performTextInput("zzz_no_match_query")

        val matches = composeRule.onAllNodesWithText(artistRoundnessLabel).fetchSemanticsNodes()
        assertEquals(0, matches.size)
    }
}
