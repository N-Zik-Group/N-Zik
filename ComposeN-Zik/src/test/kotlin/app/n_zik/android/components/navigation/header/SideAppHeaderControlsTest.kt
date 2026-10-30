package app.n_zik.android.components.navigation.header

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.components.ui.header.TestApplication
import app.n_zik.android.components.ui.header.VerticalAppBrand
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Rule
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Test as ComposeTest

class SideAppHeaderControlsTest {
    @Test
    fun `home queue and initial empty stack do not expose the exit back action`() {
        assertFalse(sideHeaderShowsBack(null))
        assertFalse(sideHeaderShowsBack("home"))
        assertFalse(sideHeaderShowsBack("home?tab=2"))
        assertFalse(sideHeaderShowsBack("queue"))
    }

    @Test
    fun `search and nested screens retain the app back action`() {
        assertTrue(sideHeaderShowsBack("search"))
        assertTrue(sideHeaderShowsBack("searchResults/query"))
        assertTrue(sideHeaderShowsBack("settings?tab=1"))
        assertTrue(sideHeaderShowsBack("album/id"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SideAppHeaderBrandTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val navController = mockk<NavController>(relaxed = true)
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = CircleShape,
        uiRoundnessShape = CircleShape,
        artistThumbnailShape = CircleShape,
    )

    @Before
    fun initDependencies() {
        val app = ApplicationProvider.getApplicationContext<TestApplication>()
        val mainApplication = mockk<MainApplication>()
        every { mainApplication.applicationContext } returns app
        Dependencies.init(mainApplication)
    }

    @ComposeTest
    fun `rail branding keeps vertical text below the logo and navigates home`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.size(48.dp, 220.dp)) {
                    VerticalAppBrand(navController, LocalContext.current)
                }
            }
        }

        composeRule.onNodeWithTag("sideAppBrandLogo").assertIsDisplayed()
        composeRule.onNodeWithText("N-ZIK").assertIsDisplayed()
        val logo = composeRule.onNodeWithTag("sideAppBrandLogo").fetchSemanticsNode().boundsInRoot
        val text = composeRule.onNodeWithTag("sideAppBrandText").fetchSemanticsNode().boundsInRoot
        assertTrue(text.top >= logo.bottom, "The title belongs below the logo")
        assertTrue(
            text.height > text.width,
            "The title occupies the vertical axis of the narrow rail: title=$text, logo=$logo, " +
                "glyphs=${composeRule.onNodeWithText("N-ZIK").fetchSemanticsNode().boundsInRoot}",
        )

        composeRule.onNodeWithText("N-ZIK").performClick()
        verify { navController.navigate("home") }
    }
}
