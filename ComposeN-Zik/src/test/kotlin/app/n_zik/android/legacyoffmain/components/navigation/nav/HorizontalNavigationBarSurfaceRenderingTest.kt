package app.n_zik.android.legacyoffmain.components.navigation.nav

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.ui.components.navigation.nav.HorizontalNavigationBar
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.DefaultLightColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.activeProfileKey
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profilePreferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.R
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HorizontalNavigationBarSurfaceRenderingTest {
    @get:Rule val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val navController = mockk<NavController>(relaxed = true)
    private var previousProfile: String? = null

    private data class Fixture(val mode: ColorPaletteMode, val palette: ColorPalette)

    @Before
    fun isolateProfile() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("navbar_surface_rendering_test", context)
        context.preferences.edit().clear().commit()
    }

    @After
    fun restoreProfile() {
        context.preferences.edit().clear().commit()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    @Test
    fun `floating surfaces retain 95 percent opacity in pitch black normal and white`() {
        val fixtures = listOf(
            Fixture(ColorPaletteMode.PitchBlack, DefaultDarkColorPalette.copy()),
            Fixture(ColorPaletteMode.Dark, DefaultDarkColorPalette),
            Fixture(ColorPaletteMode.Light, DefaultLightColorPalette),
        )
        val positions = listOf(NavigationBarPosition.BottomFloating, NavigationBarPosition.Top, NavigationBarPosition.Bottom)
        val selected = mutableStateOf(fixtures.first() to positions.first())
        val backdrop = Color(0xFF00FF00)
        context.preferences.edit()
            .putString(colorPaletteModeKey, fixtures.first().mode.name)
            .putString(navigationBarPositionKey, positions.first().name)
            .commit()

        composeRule.setContent {
            val (fixture, position) = selected.value
            key(fixture.mode, position) {
                val appearance = Appearance(
                    colorPalette = fixture.palette,
                    typography = Typography.fallback,
                    thumbnailShape = CircleShape,
                    uiRoundnessShape = CircleShape,
                    artistThumbnailShape = CircleShape,
                )
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    Box(Modifier.size(320.dp, 160.dp).background(backdrop)) {
                        val bar = HorizontalNavigationBar(
                            tabIndex = 0,
                            onTabChanged = {},
                            navController = navController,
                            modifier = Modifier.align(Alignment.Center).fillMaxWidth().testTag("navbar"),
                        )
                        bar.add { item ->
                            item(0, "Songs", R.drawable.library)
                            item(1, "Albums", R.drawable.library)
                        }
                        bar.Draw()
                    }
                }
            }
        }

        for (fixture in fixtures) {
            for (position in positions) {
                composeRule.runOnIdle {
                    context.preferences.edit()
                        .putString(colorPaletteModeKey, fixture.mode.name)
                        .putString(navigationBarPositionKey, position.name)
                        .commit()
                    selected.value = fixture to position
                }
                val pixels = composeRule.onNodeWithTag("navbar").captureToImage().toPixelMap()
                // Between the two icons, away from the rounded edge and elevation shadow.
                val actual = pixels[pixels.width / 2, pixels.height / 3]
                val surface = if (fixture.mode == ColorPaletteMode.PitchBlack) Color(0xFF1A1A1A) else fixture.palette.background1
                val expected = if (position.isFloating) surface.copy(alpha = 0.95f).compositeOver(backdrop) else surface
                val label = "${fixture.mode}, $position"
                assertEquals("$label red", expected.red, actual.red, 2f / 255f)
                assertEquals("$label green", expected.green, actual.green, 2f / 255f)
                assertEquals("$label blue", expected.blue, actual.blue, 2f / 255f)
            }
        }
    }
}
