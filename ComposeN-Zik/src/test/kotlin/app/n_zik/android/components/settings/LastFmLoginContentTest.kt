package app.n_zik.android.components.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose UI tests for [LastFmLoginContent], the body of the Last.fm login sheet
 * (username/password fields + login button).
 *
 * JUnit 4 + [RobolectricTestRunner], executed through the project's junit-vintage-engine
 * on the JUnit 5 platform (the `createComposeRule()` rule only works with JUnit 4),
 * with the plain [Application] so the app's heavy init (DI, Room, player) is skipped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LastFmLoginContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val usernameLabel: String = context.getString(R.string.lastfm_username)
    private val passwordLabel: String = context.getString(R.string.lastfm_password)
    private val loginLabel: String = context.getString(R.string.lastfm_login)

    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    private fun loginContent() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                LastFmLoginContent(onConnected = { _, _ -> })
            }
        }
    }

    @Test
    fun usernameAndPasswordFieldsAreRendered() {
        loginContent()

        composeRule.onNodeWithContentDescription(usernameLabel)
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(passwordLabel)
            .assertIsDisplayed()
        composeRule.onNodeWithText(loginLabel)
            .assertIsDisplayed()
    }

    @Test
    fun loginButtonDisabledWhenFieldsEmptyAndEnabledWhenFilled() {
        loginContent()

        val loginButton = composeRule.onNodeWithText(loginLabel)
        loginButton.assertIsNotEnabled()

        composeRule.onNodeWithContentDescription(usernameLabel)
            .performTextInput("alice")
        loginButton.assertIsNotEnabled()

        composeRule.onNodeWithContentDescription(passwordLabel)
            .performTextInput("s3cret")
        loginButton.assertIsEnabled()
    }
}
