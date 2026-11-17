package app.n_zik.android.utils

import android.app.Application
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewScene
import app.n_zik.android.components.settings.appearance.preview.ThemePreviewSession
import app.n_zik.android.components.ui.screens.profiles.loadActiveProfileFace
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class WelcomeMessagePreviewTest {
    @get:Rule val composeRule = createComposeRule()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `preview greeting renders the generic name without loading a personal profile`() {
        mockkStatic("app.n_zik.android.components.ui.screens.profiles.ProfileCardKt")
        every { loadActiveProfileFace(any(), any()) } returns
            ProfileFace("Private account name", FaceAvatar.Initials("Private account name"))
        val context = ApplicationProvider.getApplicationContext<Application>()
        val session = ThemePreviewSession(ThemePreviewScene.HOME, mockk(relaxed = true))
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalThemePreview provides session, LocalAppearance provides appearance) {
                WelcomeMessage()
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.profile_base_name), substring = true).assertExists()
        composeRule.onNodeWithText("Private account name", substring = true).assertDoesNotExist()
        composeRule.waitForIdle()
        verify(exactly = 0) { loadActiveProfileFace(any(), any()) }
    }

    @Test
    fun `normal greeting loads and renders the personal profile name`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        mockkStatic(::appContext)
        every { appContext() } returns context
        mockkStatic("app.n_zik.android.components.ui.screens.profiles.ProfileCardKt")
        every { loadActiveProfileFace(context, context.getString(R.string.profile_base_name)) } returns
            ProfileFace("Private account name", FaceAvatar.Initials("Private account name"))
        val appearance = Appearance(
            colorPalette = DefaultDarkColorPalette,
            typography = typographyOf(Color.White, true, false, FontType.Rubik),
            thumbnailShape = CircleShape,
            uiRoundnessShape = CircleShape,
            artistThumbnailShape = CircleShape,
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                WelcomeMessage()
            }
        }

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Private account name", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Private account name", substring = true).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.profile_base_name), substring = true).assertDoesNotExist()
        verify(exactly = 1) { loadActiveProfileFace(context, context.getString(R.string.profile_base_name)) }
    }
}
