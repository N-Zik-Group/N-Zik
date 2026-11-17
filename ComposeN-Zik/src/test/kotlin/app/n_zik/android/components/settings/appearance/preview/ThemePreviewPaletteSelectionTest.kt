package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
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
