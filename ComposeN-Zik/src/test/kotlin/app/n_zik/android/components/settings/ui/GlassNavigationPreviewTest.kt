package app.n_zik.android.components.settings.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.homeScreenTabIndexKey
import app.it.fast4x.rimusic.utils.homeTabsOrderKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.LocalGlassBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassProfileDefaults
import app.n_zik.android.core.network.fullOfflineModeKey
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class GlassNavigationPreviewTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val appearance = Appearance(
        colorPalette = DefaultDarkColorPalette,
        typography = typographyOf(Color.White, true, false, FontType.Rubik),
        thumbnailShape = RoundedCornerShape(12.dp),
        uiRoundnessShape = RoundedCornerShape(16.dp),
        artistThumbnailShape = RoundedCornerShape(12.dp),
    )
    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    @Before fun setUp() {
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    private fun showPreview() {
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                GlassNavigationPreview(glassProfileDefaults(GlassQuality.Performance).copy(enabled = false))
            }
        }
    }

    @Test
    @Config(sdk = [32])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `scaled navigation preview samples matching stage pixels after source and scale changes`() {
        val startColor = mutableStateOf(Color.Red)
        val endColor = mutableStateOf(Color.Blue)
        val scale = mutableStateOf(.5f)
        var previewView: View? = null
        composeRule.setContent {
            val stage = rememberLayerBackdrop()
            val view = LocalView.current
            SideEffect { previewView = view }
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalAppearance provides appearance.copy(colorPalette = DefaultDarkColorPalette.copy(
                    background1 = Color.Green, text = Color.Transparent, textDisabled = Color.Transparent,
                )),
                LocalGlassBackdrop provides stage,
            ) {
                // Match GlassPreviewPane: the source is stage-sized and native controls scale above it.
                Box(Modifier.size(360.dp * scale.value, 166.dp * scale.value).testTag("stage"),
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().layerBackdrop(stage)
                        .background(Brush.horizontalGradient(listOf(startColor.value, endColor.value))))
                    Column(Modifier.requiredWidth(360.dp).height(166.dp)
                        .graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                        verticalArrangement = Arrangement.Center) {
                        GlassNavigationPreview(
                            GlassSettings(enabled = true, film = .2f, blur = 0f, rim = 0f,
                                tintStrength = 0f, vibrancy = 0f, depth = 0f, amount = 0f,
                                indicatorOpacity = 0f),
                            Modifier.testTag("navigation"),
                        )
                    }
                }
            }
        }
        fun assertStageAlignment() {
            val stage = composeRule.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
            val navigation = composeRule.onNodeWithTag("navigation").fetchSemanticsNode().boundsInRoot
            composeRule.runOnIdle {
                val view = requireNotNull(previewView)
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                try {
                    listOf(.2f, .45f, .8f).forEach { fraction ->
                        val x = (stage.left + stage.width * fraction).toInt()
                        val y = (navigation.top + navigation.height * .8f).toInt()
                        val source = Color(bitmap.getPixel(x, (stage.top + 2f).toInt()))
                        val expected = Color.Green.copy(alpha = .2f).compositeOver(source)
                        val actual = Color(bitmap.getPixel(x, y))
                        val label = "stage fraction=$fraction scale=${scale.value}: $actual"
                        assertEquals("$label red", expected.red, actual.red, .04f)
                        assertEquals("$label green", expected.green, actual.green, .04f)
                        assertEquals("$label blue", expected.blue, actual.blue, .04f)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
        }
        assertStageAlignment()
        composeRule.runOnIdle { startColor.value = Color.Yellow; endColor.value = Color.Cyan }
        assertStageAlignment()
        composeRule.runOnIdle { scale.value = .75f }
        assertStageAlignment()
    }

    @Test fun configuredOrderAndVisibilityAreUsedAndSelectionStaysLocal() {
        context.preferences.edit()
            .putString(homeTabsOrderKey, "[\"playlists\",\"albums\",\"songs\",\"artists\",\"quickpicks\"]")
            .putBoolean("hometab_artists_enabled", false)
            .putString(navigationBarTypeKey, NavigationBarType.IconAndText.name)
            .putInt(homeScreenTabIndexKey, 3)
            .commit()
        showPreview()

        val tabs = composeRule.onAllNodes(isTab).fetchSemanticsNodes().sortedBy { it.positionInRoot.x }
        assertEquals(
            listOf(R.string.library_playlists_title, R.string.library_albums_title, R.string.library_songs_title, R.string.recommendations_title).map(context::getString),
            tabs.map { it.config[SemanticsProperties.ContentDescription].single() },
        )
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_artists_title)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_playlists_title)).assertIsSelected()
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_songs_title)).performClick().assertIsSelected()
        assertEquals(3, context.preferences.getInt(homeScreenTabIndexKey, -1))
        assertFalse(context.preferences.getBoolean(glassEnabledKey, false))
    }

    @Test fun offlineChangesRemoveQuickPicksAndKeepThePreviewSelectionValid() {
        showPreview()
        composeRule.onNodeWithContentDescription(context.getString(R.string.recommendations_title)).assertIsSelected()
        composeRule.runOnIdle {
            context.preferences.edit().putBoolean(fullOfflineModeKey, true).commit()
        }
        composeRule.onNodeWithContentDescription(context.getString(R.string.recommendations_title)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(context.getString(R.string.library_songs_title)).assertIsSelected()
        composeRule.onAllNodes(isTab).assertCountEquals(4)
    }

    @Test fun singleVisibleTabHidesTheBarLikeHomeNavigation() {
        context.preferences.edit()
            .putBoolean(fullOfflineModeKey, true)
            .putBoolean("hometab_artists_enabled", false)
            .putBoolean("hometab_albums_enabled", false)
            .putBoolean("hometab_playlists_enabled", false)
            .commit()
        showPreview()
        composeRule.onAllNodes(isTab).assertCountEquals(0)
    }
}
