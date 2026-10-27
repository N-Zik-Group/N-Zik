package app.n_zik.android.components.settings.appearance

import app.n_zik.android.R
import app.n_zik.android.components.tab.Search
import androidx.compose.ui.test.isEditable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import android.app.Application
import android.content.Context
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.SettingsTargetRegistry
import app.it.fast4x.rimusic.utils.showTotalTimeQueueKey
import app.it.fast4x.rimusic.utils.showRemainingSongTimeKey
import app.it.fast4x.rimusic.utils.showSkipTimeButtonsKey
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
@OptIn(ExperimentalAnimationApi::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AppearanceSettingsWiringTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    private fun show() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                AppearanceSettings(navController = mockk(relaxed = true))
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun selectedContentDoesNotNestThePageShell() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppearanceSettingsContent(
                        search = Search(initialVisible = true),
                        navController = mockk(relaxed = true),
                        selectedCards = setOf(R.string.notification_player),
                        cardModifier = { Modifier.testTag("appearance.selected") },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("appearance.selected").assertExists()
        composeRule.onAllNodes(isEditable()).assertCountEquals(0)
        composeRule.onNodeWithTag("appearance.card.notification", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("appearance.card.presets", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("appearance.card.reset", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun durationAndSkipControlsBelongToTimelineAndKeepTheirPreferences() {
        val selectedCards = mutableStateOf<Set<Int>?>(setOf(R.string.colors_and_background))
        val registry = SettingsTargetRegistry()
        val controls = listOf(
            "show_total_time_of_queue" to showTotalTimeQueueKey,
            "show_remaining_song_time" to showRemainingSongTimeKey,
            "skip_time_buttons" to showSkipTimeButtonsKey,
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalSettingsTargets provides registry,
            ) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AppearanceSettingsContent(
                        search = Search(initialVisible = true),
                        navController = mockk(relaxed = true),
                        selectedCards = selectedCards.value,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        controls.forEach { (id, _) ->
            composeRule.onNodeWithTag("settings.target.appearance.colors_and_background.$id")
                .assertDoesNotExist()
        }
        composeRule.runOnIdle { selectedCards.value = setOf(R.string.timeline_and_controls) }
        composeRule.waitForIdle()
        controls.forEach { (id, key) ->
            val row = composeRule.onNodeWithTag("settings.target.appearance.colors_and_background.$id")
            row.performScrollTo().performClick()
            composeRule.runOnIdle {
                assertEquals("Default true must toggle to false for $key", false, context.preferences.all[key])
            }
            row.performClick()
            composeRule.runOnIdle { assertEquals(true, context.preferences.all[key]) }
        }
        composeRule.runOnIdle { selectedCards.value = null }
        composeRule.waitForIdle()
        controls.forEach { (id, _) ->
            composeRule.onAllNodes(SemanticsMatcher.expectValue(
                SemanticsProperties.TestTag,
                "settings.target.appearance.colors_and_background.$id",
            )).assertCountEquals(1)
        }
    }

    @Test fun cardsUseAvailableWidthForFloatingAndSideNavigation() {
        show()
        fun assertFullWidth() {
            val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
            val card = composeRule.onNodeWithTag("appearance.card.presets", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(root.left, card.left, 1f)
            assertEquals(root.right, card.right, 1f)
        }
        assertFullWidth()
        composeRule.runOnIdle {
            context.preferences.edit()
                .putString(navigationBarPositionKey, NavigationBarPosition.Right.name).commit()
        }
        composeRule.waitForIdle()
        assertFullWidth()
    }

    @Test fun cardsUseLogicalOrderAndKeepConditionalLyricsVisibility() {
        context.preferences.edit().putBoolean(showlyricsthumbnailKey, false).commit()
        show()
        val cards = composeRule.onAllNodes(SemanticsMatcher("Appearance cards") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("appearance.card.") == true
        }, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(listOf("presets", "cover", "colors", "timeline", "actionBar", "behavior", "lyrics", "fullscreenLyrics", "notification", "reset"),
            cards.map { it.config[SemanticsProperties.TestTag].removePrefix("appearance.card.") })
        composeRule.runOnIdle { context.preferences.edit().putBoolean(showlyricsthumbnailKey, true).commit() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("appearance.card.fullscreenLyrics", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("appearance.card.lyrics", useUnmergedTree = true).assertExists()
    }
    @Test fun likeIconHasOneControlAndRemainsSearchableInTimeline() {
        val query = mutableStateOf("")
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    val search = Search(initialVisible = false)
                    search.input = androidx.compose.ui.text.input.TextFieldValue(query.value)
                    AppearanceSettingsContent(
                        search = search,
                        navController = mockk(relaxed = true),
                        selectedCards = setOf(R.string.player_presets_and_layout, R.string.timeline_and_controls),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(context.getString(R.string.icon_like_button), useUnmergedTree = true)
            .assertCountEquals(1)
        composeRule.runOnIdle { query.value = context.getString(R.string.icon_like_button) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(context.getString(R.string.icon_like_button), useUnmergedTree = true)
            .assertCountEquals(1)
    }

}
