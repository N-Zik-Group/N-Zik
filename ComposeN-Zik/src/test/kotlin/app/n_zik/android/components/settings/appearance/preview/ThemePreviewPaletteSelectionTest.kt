package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import app.n_zik.android.utils.colorPaletteModeKey
import app.n_zik.android.utils.colorPaletteNameKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class ThemePreviewPaletteSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun pureBlackPreviewKeepsDynamicPaletteAndUsesFixedModeCaptures() {
        val document = """{"schemaVersion":2,"categories":{"colors":{"palette":"dynamic","mode":"pure-black"}}}"""
        for (glass in listOf(false, true)) {
            val preferences = themePreviewPreferences(document, glass).plain
            assertEquals("Dynamic", preferences[colorPaletteNameKey])
            assertEquals("PureBlack", preferences[colorPaletteModeKey])
        }
        assertFalse(themePreviewFollowsSystem(document))
        assertEquals(requiredPreviews, previewSlotsForMode(requiredPreviews, themePreviewFollowsSystem(document)))
    }

    @Test
    fun gallerySceneKeysSurvivePaletteAndGlassChanges() {
        fun previews(variant: String, ext: String) = listOf(
            StorePreview("previews/home-$variant.$ext", "home", "home"),
            StorePreview("previews/player-$variant.$ext", "player", "player"),
            StorePreview("previews/player-$variant.png", "player", "alternate"),
        )

        val normal = galleryPreviewSceneKeys(previews("light-normal", "webp"))
        val glass = galleryPreviewSceneKeys(previews("dark-glass", "jpg"))
        assertEquals(normal, glass)
        assertEquals(3, normal.toSet().size)
    }

    @Test
    fun openGalleryKeepsSceneOrderAndLatestVariantWhenAnAssetIsMissing() {
        fun scenes(vararg previews: StorePreview): List<Pair<StorePreview, String>> =
            previews.toList().let { it.zip(galleryPreviewSceneKeys(it)) }

        val lightHome = StorePreview("previews/home-light-normal.webp", "home", "light-home")
        val lightPlayer = StorePreview("previews/player-light-normal.webp", "player", "light-player")
        val darkHome = StorePreview("previews/home-dark-normal.webp", "home", "dark-home")
        val darkPlayer = StorePreview("previews/player-dark-normal.webp", "player", "dark-player")
        val glassHome = StorePreview("previews/home-dark-glass.webp", "home", "glass-home")
        val glassQueue = StorePreview("previews/queue-dark-glass.webp", "queue", "glass-queue")

        val opened = scenes(lightHome, lightPlayer)
        val dark = updateGalleryViewerScenes(opened, scenes(darkPlayer, darkHome))
        assertEquals(scenes(darkHome, darkPlayer), dark)
        assertEquals(scenes(glassHome, darkPlayer), updateGalleryViewerScenes(dark, scenes(glassQueue, glassHome)))
    }

    @Test
    fun manualGalleryChoiceSurvivesRecompositionUntilTheAppPaletteChanges() {
        val appDark = mutableStateOf(false)
        val appGlass = mutableStateOf(false)
        val themeId = mutableStateOf("stock")
        val unrelatedUpdate = mutableStateOf(0)
        lateinit var dark: MutableState<Boolean>
        lateinit var glass: MutableState<Boolean>
        compose.setContent {
            unrelatedUpdate.value
            dark = rememberGalleryVariantSelection(themeId.value, 2, appDark.value)
            glass = rememberGalleryVariantSelection(themeId.value, 2, appGlass.value)
        }
        compose.runOnIdle {
            assertFalse(dark.value)
            assertFalse(glass.value)
            dark.value = true
            glass.value = true
            unrelatedUpdate.value++
        }
        compose.runOnIdle {
            assertTrue(dark.value)
            assertTrue(glass.value)
            appDark.value = true
        }
        compose.runOnIdle {
            assertTrue(dark.value)
            assertTrue(glass.value)
            dark.value = false
            unrelatedUpdate.value++
        }
        compose.runOnIdle {
            assertFalse(dark.value)
            assertTrue(glass.value)
            appGlass.value = true
        }
        compose.runOnIdle {
            assertFalse(dark.value)
            assertTrue(glass.value)
            glass.value = false
            appDark.value = false
        }
        compose.runOnIdle {
            assertFalse(dark.value)
            assertFalse(glass.value)
            appDark.value = true
        }
        compose.runOnIdle {
            assertTrue(dark.value)
            assertFalse(glass.value)
            appGlass.value = false
        }
        compose.runOnIdle {
            assertTrue(dark.value)
            assertFalse(glass.value)
            dark.value = false
            glass.value = true
            themeId.value = "another theme"
        }
        compose.runOnIdle {
            assertTrue(dark.value)
            assertFalse(glass.value)
        }
    }
}
