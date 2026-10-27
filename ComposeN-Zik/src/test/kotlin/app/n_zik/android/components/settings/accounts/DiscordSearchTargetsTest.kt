package app.n_zik.android.components.settings.accounts

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.SettingsTargetRegistry
import app.n_zik.android.components.tab.Search
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
class DiscordSearchTargetsTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val secure = context.getSharedPreferences("discord-target-test", Context.MODE_PRIVATE)
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape, uiRoundnessShape = CircleShape, artistThumbnailShape = CircleShape,
    )

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        secure.edit().clear().commit()
        mockkStatic("app.it.fast4x.rimusic.utils.EncryptedPreferencesKt")
        every { any<Context>().encryptedPreferences } returns secure
        val app = mockk<MainApplication>()
        every { app.applicationContext } returns context
        Dependencies.init(app)
    }

    @After fun tearDown() { unmockkAll() }

    @Test fun enabledButtonsKeepToggleLabelAndUrlAsSeparateSearchTargets() {
        val registry = SettingsTargetRegistry()
        val before = secure.all.toMap()
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalSettingsTargets provides registry) {
                val search = Search(initialVisible = false)
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    DiscordAdvancedSection(search)
                }
            }
        }
        composeRule.waitForIdle()
        val ids = (1..2).flatMap { button ->
            val prefix = "accounts.social_discord.discord_advanced_button$button"
            listOf(prefix, "$prefix.label", "$prefix.url")
        }
        ids.forEach { id ->
            composeRule.onAllNodesWithTag("settings.target.$id", useUnmergedTree = true).assertCountEquals(1)
        }
        assertEquals(ids.toSet(), registry.targets.keys.filter { it in ids }.toSet())
        assertEquals("Opening search must not change Discord preferences", before, secure.all)
    }
}
