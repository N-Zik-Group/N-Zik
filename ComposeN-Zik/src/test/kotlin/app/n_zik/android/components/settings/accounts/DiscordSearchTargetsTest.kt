package app.n_zik.android.components.settings.accounts

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.LocalSettingsTargets
import app.n_zik.android.components.settings.common.SettingsTargetRegistry
import app.n_zik.android.components.tab.Search
import app.n_zik.android.extensions.discord.discordAdvancedShowSmallImageKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        mockkStatic("app.n_zik.android.utils.EncryptedPreferencesKt")
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

    @Test fun appLogoIsOneLabeledSwitchAndUpdatesPresencePreference() {
        val title = context.getString(R.string.discord_advanced_show_small_image)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                val input = remember { mutableStateOf(TextFieldValue(title)) }
                DiscordAdvancedSection(Search(initialVisible = false, inputState = input))
            }
        }

        val switchRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        composeRule.onAllNodes(switchRole).assertCountEquals(1)
        composeRule.onNode(switchRole and hasText(title)).assertIsOn().performClick().assertIsOff()
        composeRule.runOnIdle {
            assertFalse(secure.getBoolean(discordAdvancedShowSmallImageKey, true))
        }
    }
}
