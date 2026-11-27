package app.n_zik.android.components.menu

import android.app.Application
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.enums.MenuStyle
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.ColorPalette
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.Typography
import app.n_zik.android.utils.activeProfileKey
import app.n_zik.android.utils.menuStyleKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.putEnum
import app.n_zik.android.utils.profilePreferences
import app.n_zik.android.utils.setActiveProfile
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.GlassTintSource
import app.n_zik.android.components.glass.LocalGlassPreviewSettings
import app.n_zik.android.components.glass.TextContrast
import app.n_zik.android.components.glass.contrastAgainst
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.glassFilmKey
import app.n_zik.android.components.glass.glassTintStrengthKey
import app.n_zik.android.components.menu.player.AudioDevice
import app.n_zik.android.components.menu.player.AudioDeviceMenuOptions
import app.n_zik.android.components.menu.player.AudioDeviceType
import app.n_zik.android.components.menu.song.SongMenuSurface
import app.n_zik.android.components.dialog.playlist.ImportPlaylistFormatDialog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
class MenuSheetSurfaceTest {
    @get:Rule val composeRule = createComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var previousProfile: String? = null

    @Before
    fun isolateProfile() {
        previousProfile = context.profilePreferences.getString(activeProfileKey, null)
        setActiveProfile("media_menu_sheet_surface_test", context)
        context.preferences.edit().clear().commit()
        GlassStore(context).reset()
    }

    @After
    fun restoreProfile() {
        context.preferences.edit().clear().commit()
        GlassStore(context).reset()
        context.profilePreferences.edit().putString(activeProfileKey, previousProfile).commit()
    }

    @Test
    fun `song sheet tints both native sections and owns the header material`() {
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        GlassStore(context).apply {
            setColor(Color.Red.toArgb())
            setFloat(glassTintStrengthKey, 1f)
            setFloat(glassFilmKey, 1f)
        }
        var headerHasSurface = false
        var bodyHasSurface = false
        var density = 1f
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(
            colorPalette = ColorPalette.fallback, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                density = LocalDensity.current.density
                Box(Modifier.size(300.dp).background(Color.Green).testTag("song-sheet")) {
                    SongMenuSurface(header = {
                        headerHasSurface = LocalMenuSheetSurface.current
                        Box(Modifier.fillMaxWidth().height(60.dp))
                    }) {
                        bodyHasSurface = LocalMenuSheetSurface.current
                        Box(Modifier.fillMaxWidth().height(180.dp))
                    }
                }
            }
        }
        composeRule.runOnIdle {
            assertEquals(true, headerHasSurface)
            assertEquals(true, bodyHasSurface)
        }
        val pixels = composeRule.onNodeWithTag("song-sheet").captureToImage().toPixelMap()
        for (y in listOf(30, 120)) {
            assertEquals("native section at y=$y follows global tint", Color.Red.toArgb(),
                pixels[pixels.width / 2, (y * density).toInt()].toArgb())
        }
    }

    @Test
    fun `custom media header and either action layout share one glass surface`() {
        val selected = mutableStateOf(true to true)
        val backdrop = Color(0xFF00C853)
        val palette = ColorPalette.fallback.copy(
            background0 = Color(0xFF391D47), background1 = Color(0xFF693875),
        )
        val shape = RoundedCornerShape(32.dp)
        val appearance = Appearance(
            colorPalette = palette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )
        var density = 1f
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        composeRule.setContent {
            val (grid, glass) = selected.value
            key(grid, glass) {
                CompositionLocalProvider(LocalAppearance provides appearance) {
                    density = LocalDensity.current.density
                    Box(Modifier.size(300.dp).background(backdrop).testTag("media-menu")) {
                        MenuSheetSurface {
                            Box(Modifier.fillMaxWidth().height(60.dp)
                                .background(menuSheetSectionColor(palette.background1)))
                            HorizontalDivider(Modifier.height(1.dp), color = Color.Red)
                            if (grid) GridMenu.Menu(showDragHandle = false) {}
                            else ListMenu.Menu(showDragHandle = false) {}
                        }
                    }
                }
            }
        }
        for (glass in listOf(true, false)) {
            for (grid in listOf(true, false)) {
                composeRule.runOnIdle {
                    context.preferences.edit().putBoolean(glassEnabledKey, glass).commit()
                    selected.value = grid to glass
                }
                val pixels = composeRule.onNodeWithTag("media-menu").captureToImage().toPixelMap()
                val x = pixels.width / 2
                val header = pixels[x, (30 * density).toInt()].toArgb()
                val body = pixels[x, (120 * density).toInt()].toArgb()
                assertNotEquals("grid=$grid: themed header and body stay distinct", header, body)
                assertEquals("grid=$grid: original header divider stays visible",
                    Color.Red.toArgb(), pixels[x, (60 * density).toInt()].toArgb())
                if (glass) {
                    assertNotEquals("header must not mask the shared glass", palette.background1.toArgb(), header)
                    assertNotEquals("actions must not mask the shared glass", palette.background0.toArgb(), body)
                    assertEquals("the outer shape starts above the media header", backdrop.toArgb(), pixels[1, 1].toArgb())
                    val nearLeftEdge = (8 * density).toInt()
                    assertEquals("actions must not restart a clipped or bordered glass surface",
                        pixels[nearLeftEdge, (120 * density).toInt()].toArgb(),
                        pixels[nearLeftEdge, (64 * density).toInt()].toArgb())
                } else {
                    assertEquals(palette.background1.toArgb(), header)
                    assertEquals(palette.background0.toArgb(), body)
                }
            }
        }
    }

    @Test
    @Config(sdk = [32])
    fun `media and song headers adapt their text to a custom film`() {
        val customGlass = mutableStateOf(false)
        val shape = RoundedCornerShape(24.dp)
        val appearance = Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape)
        var mediaText = Color.Unspecified
        var songText = Color.Unspecified
        composeRule.setContent {
            CompositionLocalProvider(
                LocalAppearance provides appearance,
                LocalGlassPreviewSettings provides GlassSettings(
                    enabled = customGlass.value,
                    source = GlassTintSource.Custom,
                    customColor = Color.White.toArgb(),
                    tintStrength = 1f,
                    film = 1f,
                    rim = 0f,
                ),
            ) {
                MenuSheetSurface {
                    MenuMediaHeader { mediaText = colorPalette().text }
                }
                SongMenuSurface(header = { songText = colorPalette().text }, content = {})
            }
        }
        composeRule.runOnIdle {
            assertEquals(appearance.colorPalette.text, mediaText)
            assertEquals(appearance.colorPalette.text, songText)
            customGlass.value = true
        }
        composeRule.runOnIdle {
            assertTrue(contrastAgainst(mediaText, Color.White) >= TextContrast)
            assertTrue(contrastAgainst(songText, Color.White) >= TextContrast)
        }
    }

    @Test
    fun importFormatDialogRetainsFormatsAndCancelDoesNotAccept() {
        val shape = RoundedCornerShape(32.dp)
        val appearance = Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape)
        val menuState = MenuState()
        var accepted = 0
        var imported = 0
        val dialog = ImportPlaylistFormatDialog({ imported++ }, {}, {}, {})
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance, LocalMenuState provides menuState) {
                dialog.Render()
            }
        }
        composeRule.runOnIdle { dialog.open { accepted++ } }
        listOf(
            R.string.playlist_import_format_nzik_csv,
            R.string.playlist_import_format_exportify_csv,
            R.string.playlist_import_format_riplay_csv,
            R.string.settings_youtube_music_title,
        ).forEach { composeRule.onNodeWithText(context.getString(it)).assertIsDisplayed() }
        composeRule.onNodeWithText(context.getString(R.string.common_action_cancel)).performClick()
        composeRule.runOnIdle {
            assertEquals(0, accepted)
            dialog.open { accepted++ }
        }
        composeRule.onNodeWithText(context.getString(R.string.playlist_import_format_nzik_csv)).performClick()
        composeRule.runOnIdle {
            assertEquals(1, accepted)
            assertEquals(1, imported)
        }
    }

    @Test
    // Exercise real blur and menu geometry without Robolectric's API 33 RuntimeShader compiler.
    @Config(sdk = [32])
    fun `audio discovery failure keeps local volume quality choices and retry in both menu layouts`() {
        val error = mutableStateOf<String?>(null)
        val style = mutableStateOf(MenuStyle.List)
        val volume = mutableFloatStateOf(5f)
        val backdrop = Color(0xFF00C853)
        val shape = RoundedCornerShape(32.dp)
        val appearance = Appearance(
            colorPalette = ColorPalette.fallback,
            typography = Typography.fallback,
            thumbnailShape = shape,
            uiRoundnessShape = shape,
            artistThumbnailShape = shape,
        )
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        GlassStore(context).setAreaMode(GlassArea.ActionButtons, GlassAreaMode.Disabled)
        composeRule.setContent {
            CompositionLocalProvider(LocalAppearance provides appearance) {
                Box(Modifier.size(300.dp, 390.dp).background(backdrop).testTag("audio-device-sheet")) {
                    AudioDeviceMenuOptions(
                        menuStyle = style.value,
                        isLoading = error.value == null,
                        error = error.value,
                        shownDevices = listOf(AudioDevice(name = "Stale device", type = AudioDeviceType.PC, isConnected = true)),
                        isCarProjectionActive = false,
                        currentVolume = volume.floatValue,
                        maxVolume = 10f,
                        selectDevice = { throw AssertionError("Stale device became selectable") },
                        onRetry = { error.value = null },
                        onVolumeChange = { volume.floatValue = it },
                        onVolumeChangeComplete = {},
                    )
                }
            }
        }
        val title = composeRule.onNodeWithText(context.getString(R.string.audio_devices))
        val loadingPlaceholder = SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate,
        )
        val spinner = composeRule.onNode(loadingPlaceholder)
        spinner.assertIsDisplayed()
        for (message in listOf(null, "Bluetooth permission denied")) {
            composeRule.runOnIdle { error.value = message }
            title.assertIsDisplayed()
            val pixels = composeRule.onNodeWithTag("audio-device-sheet").captureToImage().toPixelMap()
            assertEquals(backdrop.toArgb(), pixels[1, 1].toArgb())
            assertNotEquals(backdrop.toArgb(), pixels[pixels.width / 4, 20].toArgb())
        }
        composeRule.onNodeWithText("Bluetooth permission denied").assertIsDisplayed()
        composeRule.onNodeWithText("Stale device").assertDoesNotExist()
        val volumeSlider = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        volumeSlider.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(7f) }
        composeRule.runOnIdle { assertEquals(7f, volume.floatValue, 0f) }
        val high = context.getString(R.string.audio_quality_format_high)
        composeRule.onNodeWithText(high).performScrollTo().performClick().assertIsSelected()

        composeRule.runOnIdle { style.value = MenuStyle.Grid }
        composeRule.onNodeWithText("Bluetooth permission denied").assertIsDisplayed()
        composeRule.onNodeWithText("Stale device").assertDoesNotExist()
        val grid = composeRule.onNode(hasScrollAction())
        val volumeTile = hasText(context.getString(R.string.player_statistics_volume_label)) and hasClickAction()
        grid.performScrollToNode(volumeTile)
        composeRule.onNode(volumeTile).performClick()
        volumeSlider.performSemanticsAction(SemanticsActions.SetProgress) { it(8f) }
        composeRule.runOnIdle { assertEquals(8f, volume.floatValue, 0f) }
        composeRule.onNodeWithText(context.getString(R.string.common_action_close)).performClick()
        grid.performScrollToNode(hasText(high))
        composeRule.onNodeWithText(high).assertIsSelected()
        val low = context.getString(R.string.audio_quality_format_low)
        grid.performScrollToNode(hasText(low))
        composeRule.onNodeWithText(low).performClick().assertIsSelected()

        val retry = context.getString(R.string.common_action_retry)
        grid.performScrollToNode(hasText(retry))
        composeRule.onNodeWithText(retry).performClick()
        grid.performScrollToNode(loadingPlaceholder)
        spinner.assertIsDisplayed()
        title.assertIsDisplayed()
    }
}
