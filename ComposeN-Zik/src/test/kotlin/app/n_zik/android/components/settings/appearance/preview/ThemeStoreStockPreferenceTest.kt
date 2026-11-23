package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.utils.m3eAlwaysShowShapesKey
import app.n_zik.android.utils.rememberPreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ThemeStoreStockPreferenceTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun mountedShapePreferenceFollowsStockResetAndUndo() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("stock-mounted-shapes", Context.MODE_PRIVATE)
        val glass = GlassProfilePreferences.preferences(context, "stock-mounted-shapes")
        val previewContext = ThemePreviewContext(context, prefs)
        val saved = bundledDefaultTheme(context)
        val document = saved.document
        val stock = StoreAppearance(document, document.length, parseThemeAppearance(document).toStoreGroups())
        val settings = requireNotNull(stock.selectedSettings(
            requireNotNull(saved.theme), 100, setOf(StoreGroup.Animations),
        ))
        lateinit var setEnabled: (Boolean) -> Unit
        lateinit var undo: () -> Unit
        var displayed = false
        compose.setContent {
            CompositionLocalProvider(LocalContext provides previewContext) {
                val (enabled, setter) = rememberPreference(m3eAlwaysShowShapesKey, false)
                displayed = enabled
                setEnabled = setter
            }
        }

        compose.runOnIdle {
            assertFalse(displayed)
            assertFalse(prefs.contains(m3eAlwaysShowShapesKey))
            setEnabled(true)
        }
        compose.runOnIdle {
            assertTrue(displayed)
            assertTrue(prefs.getBoolean(m3eAlwaysShowShapesKey, false))
            undo = settings.applyTo(prefs, glass)
        }
        compose.runOnIdle {
            assertFalse(displayed)
            assertFalse(prefs.contains(m3eAlwaysShowShapesKey))
            undo()
        }
        compose.runOnIdle {
            assertTrue(displayed)
            assertTrue(prefs.getBoolean(m3eAlwaysShowShapesKey, false))
        }
    }
}
