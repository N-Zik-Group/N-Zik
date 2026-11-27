package app.n_zik.android.components.menu.lyrics

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.getEnum
import app.n_zik.android.utils.lyricsTypeKey
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.setActiveProfile
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@UnstableApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "en-w400dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricsSettingsMenuTest {
    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val previousProfile = getActiveProfile(context)

    @Before
    fun setUp() {
        setActiveProfile("lyrics_settings_menu_test", context)
        context.preferences.edit().clear()
            .putBoolean(glassEnabledKey, false)
            .putBoolean(disableScrollingTextKey, true)
            .commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    @After
    fun tearDown() {
        context.preferences.edit().clear().commit()
        setActiveProfile(previousProfile, context)
    }

    @Test
    fun `list exposes lyrics mode and persists the selected option`() {
        verifyLyricsModeSelector(MenuStyle.List)
    }

    @Test
    fun `grid exposes lyrics mode and persists the selected option`() {
        verifyLyricsModeSelector(MenuStyle.Grid)
    }

    private fun verifyLyricsModeSelector(style: MenuStyle) {
        context.preferences.edit().apply {
            putEnum(menuStyleKey, style)
            putEnum(lyricsTypeKey, LyricsType.Synced)
        }.commit()
        val shape = RoundedCornerShape(16.dp)
        val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)
        val translateEnabled = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                LyricsSettingsMenu(
                    isLandscape = false,
                    translateEnabled = translateEnabled,
                    canRefetchLyrics = { false },
                    actualLyricsType = { null },
                    onShowLyricsSizeDialog = {},
                    onEditLyrics = {},
                    canCopyLyrics = { false },
                    onCopyLyrics = {},
                    onSearchLyricsOnline = {},
                    onFetchLyricsAgain = {},
                    onPickFromLrcLib = {},
                    onShowProviders = {},
                    onShowOffsetDialog = {},
                ).MenuComponent()
            }
        }

        val title = hasText("Lyrics mode")
        val inDialog = hasAnyAncestor(isDialog())
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        val synced = hasText(context.getString(R.string.lyrics_mode_synchronized_label))
        val karaoke = hasText(context.getString(R.string.lyrics_mode_karaoke_label))
        compose.onNode(title and synced and hasClickAction()).performClick()
        compose.onNode(title and inDialog).assertExists()
        compose.onAllNodes(radio and inDialog).assertCountEquals(4)
        compose.onNode(synced and radio and inDialog).assertIsSelected()
        compose.onNode(karaoke and radio and inDialog).performClick()
        compose.runOnIdle {
            assertEquals(LyricsType.Karaoke, context.preferences.getEnum(lyricsTypeKey, LyricsType.Auto))
        }
        compose.onNode(title and karaoke and hasClickAction()).performClick()
        compose.onNode(karaoke and radio and inDialog).assertIsSelected()
    }
}
