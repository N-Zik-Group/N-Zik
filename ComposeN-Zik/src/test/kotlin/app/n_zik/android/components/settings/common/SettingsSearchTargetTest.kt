package app.n_zik.android.components.settings.common

import androidx.compose.foundation.layout.WindowInsets

import app.n_zik.android.LocalPlayerAwareWindowInsets

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.ColorPaletteName
import app.n_zik.android.enums.AnimatedGradient
import app.n_zik.android.enums.FontType
import app.n_zik.android.enums.PlayerBackgroundColors
import app.n_zik.android.enums.PlayerType
import app.n_zik.android.privacy.JournalPermission
import app.n_zik.android.privacy.PermissionEventKind
import app.n_zik.android.privacy.PermissionFeature
import app.n_zik.android.privacy.PermissionUsageJournal
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.autoDownloadSongKey
import app.n_zik.android.utils.animatedGradientKey
import app.n_zik.android.utils.colorPaletteNameKey
import app.n_zik.android.utils.disablePlayerHorizontalSwipeKey
import app.n_zik.android.utils.playerBackgroundColorsKey
import app.n_zik.android.utils.enablePictureInPictureKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.holdToSpeedUpKey
import app.n_zik.android.utils.playerTypeKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.utils.showNextSongsInPlayerKey
import app.n_zik.android.utils.showCoverThumbnailAnimationKey
import app.n_zik.android.utils.showTipsKey
import app.n_zik.android.utils.showlyricsthumbnailKey
import app.n_zik.android.utils.showthumbnailKey
import app.n_zik.android.utils.showvisthumbnailKey
import app.n_zik.android.utils.skipSilenceKey
import app.n_zik.android.utils.thumbnailpauseKey
import app.n_zik.android.utils.visualizerEnabledKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.dialog.settings.HomeSongsSortSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeSongsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.HomeArtistsToolbarSettingsDialog
import app.n_zik.android.components.dialog.settings.QuickPicksContentSettingsDialog
import app.n_zik.android.components.dialog.settings.PlayerActionBarSettingsDialog
import app.n_zik.android.components.dialog.settings.BurgerMenuSettingsDialog
import app.n_zik.android.components.navigation.LocalAppHeaderContentInset
import app.n_zik.android.components.navigation.LocalAppHeaderOverlayInset
import app.n_zik.android.themed.n_zik.screen.player.playerActionBarTopSlotsKey
import app.n_zik.android.utils.DataStoreUtils
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class SettingsSearchTargetTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().putString("target-test-sentinel", "unchanged").commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
        composeRule.mainClock.autoAdvance = false
    }

    @After fun closeSearchDialog() {
        HomeSongsSortSettingsDialog.hideDialog()
        HomeSongsToolbarSettingsDialog.hideDialog()
        HomeArtistsToolbarSettingsDialog.hideDialog()
        QuickPicksContentSettingsDialog.hideDialog()
        PlayerActionBarSettingsDialog.hideDialog()
        BurgerMenuSettingsDialog.hideDialog()
    }

    private val messages = mutableListOf<String>()

    private fun show(
        category: String,
        target: String,
        parent: String,
        chrome: Dp = 0.dp,
        restoration: StateRestorationTester? = null,
    ) {
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalAppearance provides appearance,
                LocalSettingsSearchMessage provides { messages += it },
                LocalAppHeaderContentInset provides { chrome },
                LocalAppHeaderOverlayInset provides { chrome },
            ) {
                SettingsCategoryPage(category, mockk(relaxed = true), target, parent)
            }
        }
        if (restoration != null) restoration.setContent(content) else composeRule.setContent(content)
    }

    private fun pumpUntil(label: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(50)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            if (condition()) return
        }
        assertTrue("$label; messages=$messages", condition())
    }

    private fun highlighted(id: String, expected: Boolean): Boolean = runCatching {
        composeRule.onNode(SemanticsMatcher.expectValue(SettingsTargetId, id), useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SettingsTargetHighlighted, expected))
    }.isSuccess

    @Test fun hiddenSilenceDurationHighlightsItsSwitchAndExpiresWithoutWrites() {
        context.preferences.edit().putBoolean(skipSilenceKey, false).commit()
        val before = context.preferences.all.toMap()
        val target = "general.audio_effects.minimum_silence_length"
        val parent = "general.audio_effects.skip_silence"
        show("audio", target, parent = "")
        pumpUntil("hidden duration must highlight its enable switch") { highlighted(parent, true) }
        composeRule.onNodeWithTag("settings.target.$target", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag("settings.target.unavailable").assertDoesNotExist()
        assertEquals(listOf(context.getString(app.n_zik.android.R.string.settings_target_unavailable)), messages)
        assertEquals(before, context.preferences.all)
        repeat(4) { pulse ->
            if (pulse > 0) pumpUntil("next highlight pulse must appear") { highlighted(parent, true) }
            pumpUntil("highlight pulse must turn off") { highlighted(parent, false) }
        }
        composeRule.mainClock.advanceTimeBy(1000)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertTrue("there must be no fifth pulse", highlighted(parent, false))
        assertEquals("fallback message must be emitted only once", 1, messages.size)
        assertEquals("target focus and highlight must never apply preferences", before, context.preferences.all)
    }

    @Test fun playlistDownloadSearchRevealsItsToggleWithAutomaticDownloadsOff() {
        context.preferences.edit().putBoolean(autoDownloadSongKey, false).commit()
        val before = context.preferences.all.toMap()
        val entry = AllSettingsEntryTargets.single {
            it.id == "network.download.settings_enable_autodownload_song_when_playlist_bookmarked"
        }
        show(entry.parentCard().categoryId(), entry.id, settingsCardTargetId(context, entry.tab, entry.cardTitleRes))
        pumpUntil("The playlist download toggle must be highlighted with automatic downloads off") {
            highlighted(entry.id, true)
        }
        composeRule.onNodeWithTag("settings.target.${entry.id}", useUnmergedTree = true)
            .assertIsDisplayed().assertIsNotEnabled()
        assertTrue(messages.isEmpty())
        assertEquals(before, context.preferences.all)
    }

    @Test fun `search for disabled gain highlights the normalization switch`() {
        assertSearchHighlightsPrerequisite(
            category = "audio",
            target = "general.audio_effects.settings_loudness_base_gain",
            prerequisite = "general.audio_effects.loudness_normalization",
        )
    }

    @Test fun `search for disabled picture in picture content highlights its switch`() {
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "general.picture_in_picture.settings_pip_module",
            prerequisite = "general.picture_in_picture.settings_enable_pip",
        )
    }

    @Test fun `search for hidden hold to speed rate explains why and highlights its switch`() {
        context.preferences.edit().putBoolean(holdToSpeedUpKey, false).commit()
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.cover_and_thumbnail.hold_to_speed_up_rate",
            prerequisite = "appearance.cover_and_thumbnail.hold_to_speed_up",
        )
    }

    @Test fun `search for hidden artwork animation style highlights the animation switch`() {
        context.preferences.edit().putBoolean(showCoverThumbnailAnimationKey, false).commit()
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.cover_and_thumbnail.cover_thumbnail_animation_type",
            prerequisite = "appearance.cover_and_thumbnail.show_cover_thumbnail_animation",
        )
    }

    @Test fun `search for artwork animation style leads to the cover switch when artwork is hidden`() {
        context.preferences.edit()
            .putString(playerBackgroundColorsKey, PlayerBackgroundColors.BlurredCoverColor.name)
            .putBoolean(showthumbnailKey, false)
            .commit()
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.cover_and_thumbnail.cover_thumbnail_animation_type",
            prerequisite = "appearance.cover_and_thumbnail.show_thumbnail",
        )
    }

    @Test fun hiddenShrinkWhenPausedRoutesToCoverVisibility() {
        assertHiddenCoverControlPrerequisite(PlayerType.Essential, "thumbnailpause")
    }

    @Test fun hiddenFadingEdgeRoutesToCoverVisibility() {
        assertHiddenCoverControlPrerequisite(PlayerType.Modern, "fadingedge")
    }

    private fun assertHiddenCoverControlPrerequisite(playerType: PlayerType, target: String) {
        context.preferences.edit()
            .putString(playerTypeKey, playerType.name)
            .putString(playerBackgroundColorsKey, PlayerBackgroundColors.BlurredCoverColor.name)
            .putBoolean(showthumbnailKey, false)
            .commit()
        val id = "appearance.cover_and_thumbnail.$target"
        val parentCard = AllSettingsEntryTargets.single { it.id == id }.parentCard("player")
        val parent = settingsCardTargetId(context, parentCard.tab, parentCard.titleRes)
        show("player", id, parent)
        pumpUntil("Show thumbnail is highlighted; settings target: $id") {
            highlighted("appearance.cover_and_thumbnail.show_thumbnail", true)
        }
        composeRule.onNodeWithTag("settings.target.$id", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun `search for hidden fullscreen lyrics controls leads to the cover lyrics switch`() {
        context.preferences.edit().putBoolean(showlyricsthumbnailKey, true).commit()
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.full_screen_lyrics_components.controls",
            prerequisite = "appearance.txt_lyrics.show_lyrics_thumbnail",
        )
    }

    @Test fun `search for hidden lyrics background leads to the cover lyrics switch`() {
        context.preferences.edit().putBoolean(showlyricsthumbnailKey, false).commit()
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.txt_lyrics.show_background_in_lyrics",
            prerequisite = "appearance.txt_lyrics.show_lyrics_thumbnail",
        )
    }

    @Test fun `hidden cover routes the lyrics artwork result to cover visibility`() {
        context.preferences.edit()
            .putString(playerBackgroundColorsKey, PlayerBackgroundColors.BlurredCoverColor.name)
            .putBoolean(showthumbnailKey, false)
            .putBoolean(showlyricsthumbnailKey, true)
            .commit()
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.txt_lyrics.show_lyrics_thumbnail",
            prerequisite = "appearance.cover_and_thumbnail.show_thumbnail",
        )
    }

    @Test fun `hidden gradient style routes to the background selector`() {
        context.preferences.edit().putString(playerBackgroundColorsKey, PlayerBackgroundColors.BlurredCoverColor.name).commit()
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.colors_and_background.gradienttype",
            prerequisite = "appearance.colors_and_background.background_colors",
        )
    }

    @Test fun `hidden M3E shapes route to the visible gradient selector`() {
        context.preferences.edit()
            .putString(playerBackgroundColorsKey, PlayerBackgroundColors.AnimatedGradient.name)
            .putString(animatedGradientKey, AnimatedGradient.Linear.name)
            .commit()
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.colors_and_background.m3e_always_show_shapes",
            prerequisite = "appearance.colors_and_background.gradienttype",
        )
    }

    @Test fun `search for the inactive swipe variant highlights the swipe switch actually shown`() {
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.player_behavior_and_visuals.disable_vertical_swipe",
            prerequisite = "appearance.player_behavior_and_visuals.disable_horizontal_swipe",
        )
    }

    @Test fun `search for hidden swipe sensitivity highlights the active swipe switch`() {
        context.preferences.edit().putBoolean(disablePlayerHorizontalSwipeKey, true).commit()
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.player_behavior_and_visuals.player_swipe_sensitivity",
            prerequisite = "appearance.player_behavior_and_visuals.disable_horizontal_swipe",
        )
    }

    @Test fun `search for custom visualizer color highlights the visible source choice`() {
        context.preferences.edit().putBoolean(visualizerEnabledKey, true).commit()
        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.player_behavior_and_visuals.color_custom",
            prerequisite = "appearance.player_behavior_and_visuals.visualizer_white_color_option",
        )
    }

    @Test fun `search highlights the clear history button without opening confirmation or changing the journal`() {
        assertTrue(PermissionUsageJournal.record(
            context, PermissionFeature.VOICE_SEARCH, JournalPermission.MICROPHONE, PermissionEventKind.REQUESTED,
        ))
        composeRule.waitUntil(5_000) { PermissionUsageJournal.read(context.preferences).size == 1 }
        val before = PermissionUsageJournal.read(context.preferences)
        val query = context.getString(R.string.permissions_journal_clear)
        val entry = searchSettingsEntries(context, query).first()
        assertEquals("nested.privacy.permissions.open.0.clear", entry.id)

        show("privacy", entry.id, parent = "")

        pumpUntil("clear history button highlighted") { highlighted(entry.id, true) }
        composeRule.onNodeWithTag("permission_journal_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("settings.target.${entry.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText(context.getString(R.string.permissions_journal_clear_confirm)).assertDoesNotExist()
        assertTrue(messages.isEmpty())
        assertEquals(before, PermissionUsageJournal.read(context.preferences))
    }

    @Test fun `search opens the exact storage cleanup option without selecting it`() {
        val target = "data.storage.cleanup.themes"
        val before = context.preferences.all.toMap()
        show("data", target, parent = "")
        pumpUntil("The theme cache option must be visible and highlighted") { highlighted(target, true) }
        composeRule.onNodeWithTag("settings.target.$target", useUnmergedTree = true).assertIsDisplayed().assertIsOff()
        assertTrue(messages.isEmpty())
        assertEquals(before, context.preferences.all)
    }

    @Test fun `search for local folders highlights the disabled on-device library switch`() {
        val prerequisite = "other.on_device.enabled"
        assertSearchHighlightsPrerequisite(
            category = "data",
            target = "other.on_device.folders",
            prerequisite = prerequisite,
        )
        val switch = hasAnyAncestor(hasTestTag("settings.target.$prerequisite")) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)
        composeRule.onNode(switch, useUnmergedTree = true).assertIsOff()
    }

    @Test fun `search for YouTube sign in highlights the disabled connection switch`() {
        val accountPrefs = context.getSharedPreferences("search-target-youtube", Context.MODE_PRIVATE)
        accountPrefs.edit().clear().commit()
        mockkStatic("app.n_zik.android.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns accountPrefs
        try {
            assertSearchHighlightsPrerequisite(
                category = "youtube",
                target = "accounts.youtube_music.account_login",
                prerequisite = "accounts.youtube_music.enable_youtube_music_login",
            )
            assertTrue(accountPrefs.all.isEmpty())
        } finally {
            unmockkStatic("app.n_zik.android.utils.EncryptedPreferencesKt")
        }
    }

    @Test fun `search for a hidden custom palette color highlights the palette selector`() {
        context.preferences.edit().putString(colorPaletteNameKey, ColorPaletteName.Dynamic.name).commit()

        assertSearchHighlightsPrerequisite(
            category = "interface",
            target = "ui.theme.color_background_1.dark",
            prerequisite = "ui.theme.theme",
        )
    }

    @Test fun `search for keep dark mode points to the dynamic theme`() {
        context.preferences.edit().putString(colorPaletteNameKey, ColorPaletteName.Default.name).commit()

        assertSearchHighlightsPrerequisite(
            category = "interface",
            target = "ui.theme.respect_brightness",
            prerequisite = "ui.theme.theme",
            requiredChoice = ColorPaletteName.Dynamic.textId,
        )
    }

    @Test fun `search for custom colors points to the custom colors theme`() {
        context.preferences.edit().putString(colorPaletteNameKey, ColorPaletteName.Dynamic.name).commit()

        assertSearchHighlightsPrerequisite(
            category = "interface",
            target = "ui.theme.customcolor",
            prerequisite = "ui.theme.theme",
            requiredChoice = ColorPaletteName.CustomColor.textId,
        )
    }

    @Test fun `search for a hidden queue preview count highlights the enable switch`() {
        context.preferences.edit().putBoolean(showNextSongsInPlayerKey, false).commit()

        assertSearchHighlightsPrerequisite(
            category = "player",
            target = "appearance.player_behavior_and_visuals.songs_number_to_show",
            prerequisite = "appearance.player_behavior_and_visuals.show_next_songs_in_player",
        )
    }

    @Test fun `search for disabled Quick Picks content highlights its availability switch`() {
        context.preferences.edit().putBoolean(enableQuickPicksPageKey, false).commit()

        assertSearchHighlightsPrerequisite(
            category = "ai",
            target = "ai.quick_picks.quick_picks_content",
            prerequisite = "ai.tab_general.enable_quick_picks_page",
        )
    }

    @Test fun `search for the disabled Quick Picks card highlights its availability switch`() {
        context.preferences.edit().putBoolean(enableQuickPicksPageKey, false).commit()
        assertSearchHighlightsPrerequisite(
            category = "ai",
            target = settingsCardTargetId(context, 3, R.string.recommendations_title),
            prerequisite = "ai.tab_general.enable_quick_picks_page",
        )
    }

    @Test fun `search for hidden For You settings highlights the Content visibility option`() {
        context.preferences.edit().putBoolean(showTipsKey, false).commit()
        val before = context.preferences.all.toMap()
        val prerequisite = SettingsNestedEntryTargets.single {
            it.nestedDestination?.openerId == "ai.quick_picks.quick_picks_content" &&
                it.nestedDestination.itemId == "tips"
        }
        show("ai", "ai.quick_picks.tips", parent = "")

        pumpUntil("For You visibility in the Content dialog must be highlighted") {
            highlighted(prerequisite.id, true)
        }
        assertEquals(before, context.preferences.all)
    }

    private fun assertSearchHighlightsPrerequisite(
        category: String,
        target: String,
        prerequisite: String,
        requiredChoice: Int? = null,
    ) {
        val before = context.preferences.all.toMap()
        show(category, target, parent = "")
        pumpUntil("The setting that reveals the hidden result must be highlighted") {
            highlighted(prerequisite, true)
        }
        composeRule.onNode(SemanticsMatcher.expectValue(SettingsTargetId, prerequisite), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag("settings.target.$target", useUnmergedTree = true).assertDoesNotExist()
        val expectedMessage = if (requiredChoice != null) {
            val label = context.getString(R.string.common_color_source_theme)
            val choice = context.getString(requiredChoice)
            context.getString(R.string.settings_target_prerequisite, "$label › $choice")
        } else {
            context.getString(R.string.settings_target_unavailable)
        }
        assertEquals(listOf(expectedMessage), messages)
        assertEquals(before, context.preferences.all)
    }

    @Test fun farOffscreenCardIsComposedBeforeExactTargetAvailabilityIsChecked() {
        val before = context.preferences.all.toMap()
        val target = "about.contributors.translators"
        show("about", target, "card.8.contributors")
        pumpUntil("the offscreen contributors row must be composed and highlighted") { highlighted(target, true) }
        assertTrue("an uncomposed lazy card must not be reported unavailable", messages.isEmpty())
        assertEquals(before, context.preferences.all)
    }

    @Test fun storageCardSearchLeavesItsInfoActionBelowChrome() {
        val chrome = 64.dp
        val target = settingsCardTargetId(context, 4, R.string.storage_breakdown_title)
        show("data", target, "", chrome)
        pumpUntil("Storage card must be positioned and highlighted") { highlighted(target, true) }
        val info = composeRule.onNodeWithContentDescription(context.getString(R.string.storage_breakdown_info_title))
        info.assertIsDisplayed().assertHasClickAction()
        assertTrue(info.fetchSemanticsNode().boundsInRoot.top >= with(composeRule.density) { chrome.toPx() })
        assertTrue(messages.isEmpty())
    }

    @Test fun openPipSearchResultHighlightsItsRowAfterScrolling() {
        Shadows.shadowOf(context.packageManager)
            .setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        context.preferences.edit().putBoolean(enablePictureInPictureKey, true).commit()
        val target = "general.picture_in_picture.open"
        val entry = AllSettingsEntryTargets.single { it.id == target }
        val chrome = 64.dp
        show("player", target, settingsCardTargetId(context, entry.tab, entry.cardTitleRes), chrome)
        pumpUntil("PiP search must reach the exact Open row") { highlighted(target, true) }
        val row = composeRule.onNodeWithTag("settings.target.$target", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(row.fetchSemanticsNode().boundsInRoot.top >= with(composeRule.density) { chrome.toPx() })
        assertTrue(messages.isEmpty())
    }

    @Test fun playerTargetNavigationDoesNotNormalizeStoredThumbnailPreferences() {
        context.preferences.edit()
            .putString(playerTypeKey, PlayerType.Modern.name)
            .putString(playerBackgroundColorsKey, PlayerBackgroundColors.AnimatedGradient.name)
            .putBoolean(showthumbnailKey, false)
            .putBoolean(showlyricsthumbnailKey, true)
            .putBoolean(showvisthumbnailKey, true)
            .putBoolean(thumbnailpauseKey, true)
            .commit()
        val before = context.preferences.all.toMap()
        val target = "card.2.player_presets_and_layout"
        show("player", target, target)
        pumpUntil("player target must be measured and highlighted") { highlighted(target, true) }
        composeRule.onNodeWithTag("settings.target.unavailable").assertDoesNotExist()
        assertTrue(messages.isEmpty())
        assertEquals("opening a search destination must not normalize player settings", before, context.preferences.all)
    }

    @Test fun `search opens the exact choice for an unassigned top player action without assigning it`() {
        context.preferences.edit().putString(playerActionBarTopSlotsKey, """["collapsePlayer","none","menu"]""").commit()
        val before = context.preferences.all.toMap()
        val entry = SettingsNestedEntryTargets.single {
            it.nestedDestination?.itemId == "top.action.audioVideo"
        }

        show("player", entry.id, parent = "")

        pumpUntil("the unassigned action is shown in the selector") { highlighted(entry.id, true) }
        composeRule.onNodeWithTag("valueSelectorList").assertIsDisplayed()
        composeRule.onNodeWithTag("settings.target.${entry.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
        assertTrue(messages.isEmpty())
        assertEquals(before, context.preferences.all)
    }

    @Test fun `search highlights the add separator button without changing the menu`() {
        val before = DataStoreUtils.prefs(context).all.toMap()
        val query = context.getString(R.string.settings_navigation_menu_action_add_separator)
        val entry = searchSettingsEntries(context, query).first()
        assertEquals("nested.embedded.burger.burger_menu.0.addSeparator", entry.id)

        show("interface", entry.id, parent = "")

        pumpUntil("add separator button is highlighted") { highlighted(entry.id, true) }
        composeRule.onNodeWithTag("settings.target.${entry.id}", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertHasClickAction()
        assertTrue(messages.isEmpty())
        assertEquals(before, DataStoreUtils.prefs(context).all)
    }

    @Test fun `nested search opens the correct tab and scrolls to the last option without applying preferences`() {
        val entry = AllSettingsEntryTargets.first {
            it.nestedDestination?.let { destination ->
                destination.dialogId == "HomeSongsSortSettingsDialog" &&
                    destination.tabIndex == 1 && destination.itemId == "favs_Custom"
            } == true
        }
        val before = context.preferences.all.toMap()
        show(entry.parentCard().categoryId(), entry.id, settingsCardTargetId(context, entry.tab, entry.cardTitleRes))
        pumpUntil("the nested last option must be visible and highlighted") { highlighted(entry.id, true) }
        composeRule.onNode(SemanticsMatcher.expectValue(SettingsTargetId, entry.id), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("settingsDialogTab1").assertIsSelected()
        assertTrue("search must open the customization dialog", HomeSongsSortSettingsDialog.isActive)
        assertTrue("a valid nested target must not report unavailable", messages.isEmpty())
        assertEquals("opening and scrolling must never apply dialog preferences", before, context.preferences.all)
    }

    @Test fun `a dismissed nested search does not reopen after restoring its destination`() {
        val entry = AllSettingsEntryTargets.first {
            it.nestedDestination?.let { destination ->
                destination.dialogId == "HomeSongsSortSettingsDialog" &&
                    destination.tabIndex == 1 && destination.itemId == "favs_Custom"
            } == true
        }
        val restoration = StateRestorationTester(composeRule)
        show(entry.parentCard().categoryId(), entry.id, settingsCardTargetId(context, entry.tab, entry.cardTitleRes),
            restoration = restoration)
        pumpUntil("the target dialog was opened") { highlighted(entry.id, true) }
        composeRule.runOnUiThread { HomeSongsSortSettingsDialog.hideDialog() }
        pumpUntil("the target dialog finished dismissing") {
            composeRule.onAllNodesWithTag("settingsDialogTab0").fetchSemanticsNodes().isEmpty()
        }

        restoration.emulateSavedInstanceStateRestore()
        repeat(10) {
            composeRule.mainClock.advanceTimeBy(350)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(350))
            composeRule.waitForIdle()
        }
        assertTrue("the dismissed search must not reopen the dialog", !HomeSongsSortSettingsDialog.isActive)
        composeRule.onNodeWithTag("settingsDialogTab0").assertDoesNotExist()
    }

    @Test fun `dismissed nested search cannot select tabs or highlight manually opened dialogs`() {
        val entry = AllSettingsEntryTargets.first {
            it.nestedDestination?.let { destination ->
                destination.dialogId == "HomeSongsToolbarSettingsDialog" &&
                    destination.tabIndex == 1 && destination.itemId == "favs_search"
            } == true
        }
        val before = context.preferences.all.toMap()
        show(entry.parentCard().categoryId(), entry.id, settingsCardTargetId(context, entry.tab, entry.cardTitleRes))
        pumpUntil("the requested Songs Favorites option must be highlighted") { highlighted(entry.id, true) }
        composeRule.onNodeWithTag("settingsDialogTab1").assertIsSelected()
        composeRule.runOnUiThread { HomeSongsToolbarSettingsDialog.hideDialog() }
        pumpUntil("the search dialog must finish dismissing") {
            composeRule.onAllNodesWithTag("settingsDialogTab0").fetchSemanticsNodes().isEmpty()
        }
        composeRule.runOnUiThread { HomeArtistsToolbarSettingsDialog.showDialog() }
        pumpUntil("a manually opened Artists dialog must retain its default tab") {
            runCatching { composeRule.onNodeWithTag("settingsDialogTab0").assertIsSelected() }.isSuccess
        }
        composeRule.onNode(SemanticsMatcher.expectValue(SettingsTargetId, entry.id), useUnmergedTree = true).assertDoesNotExist()
        composeRule.runOnUiThread { HomeArtistsToolbarSettingsDialog.hideDialog() }
        pumpUntil("the Artists dialog must finish dismissing") {
            composeRule.onAllNodesWithTag("settingsDialogTab0").fetchSemanticsNodes().isEmpty()
        }
        composeRule.runOnUiThread { HomeSongsToolbarSettingsDialog.showDialog() }
        pumpUntil("manually reopening Songs must also retain its default tab") {
            runCatching { composeRule.onNodeWithTag("settingsDialogTab0").assertIsSelected() }.isSuccess
        }
        composeRule.onNode(SemanticsMatcher.expectValue(SettingsTargetId, entry.id), useUnmergedTree = true).assertDoesNotExist()
        assertEquals("dialog focus must never apply preferences", before, context.preferences.all)
    }
}
