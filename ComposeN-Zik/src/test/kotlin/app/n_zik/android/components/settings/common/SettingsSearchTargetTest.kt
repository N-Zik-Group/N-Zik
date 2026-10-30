package app.n_zik.android.components.settings.common

import androidx.compose.foundation.layout.WindowInsets

import app.n_zik.android.LocalPlayerAwareWindowInsets

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.PlayerBackgroundColors
import app.it.fast4x.rimusic.enums.PlayerType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.playerBackgroundColorsKey
import app.it.fast4x.rimusic.utils.playerTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.showlyricsthumbnailKey
import app.it.fast4x.rimusic.utils.showthumbnailKey
import app.it.fast4x.rimusic.utils.showvisthumbnailKey
import app.it.fast4x.rimusic.utils.skipSilenceKey
import app.it.fast4x.rimusic.utils.thumbnailpauseKey
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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

    private val messages = mutableListOf<String>()

    private fun show(category: String, target: String, parent: String) {
        composeRule.setContent {
            CompositionLocalProvider(LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0), LocalAppearance provides appearance, LocalSettingsSearchMessage provides { messages += it }) {
                SettingsCategoryPage(category, mockk(relaxed = true), target, parent)
            }
        }
    }

    private fun pumpUntil(label: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            composeRule.mainClock.advanceTimeBy(50)
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            if (condition()) return
        }
        assertTrue(label, condition())
    }

    private fun highlighted(id: String, expected: Boolean): Boolean = runCatching {
        composeRule.onNodeWithTag("settings.target.$id", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SettingsTargetHighlighted, expected))
    }.isSuccess

    @Test fun hiddenSettingFallsBackToItsCardAndHighlightExpiresWithoutWrites() {
        context.preferences.edit().putBoolean(skipSilenceKey, false).commit()
        val before = context.preferences.all.toMap()
        val target = "general.audio_effects.minimum_silence_length"
        val parent = "card.0.audio_effects"
        show("audio", target, parent)
        pumpUntil("hidden target must fall back to its card") { highlighted(parent, true) }
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

    @Test fun farOffscreenCardIsComposedBeforeExactTargetAvailabilityIsChecked() {
        val before = context.preferences.all.toMap()
        val target = "about.contributors.translators"
        show("about", target, "card.8.contributors")
        pumpUntil("the offscreen contributors row must be composed and highlighted") { highlighted(target, true) }
        assertTrue("an uncomposed lazy card must not be reported unavailable", messages.isEmpty())
        assertEquals(before, context.preferences.all)
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
}
