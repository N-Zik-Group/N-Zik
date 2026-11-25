package app.n_zik.android.components.settings.appearance.preview

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.glass.GlassProfilePreferences
import app.n_zik.android.components.settings.ui.ToolbarAction
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.utils.m3eAlwaysShowShapesKey
import app.n_zik.android.utils.miniPlayerButtonOrderKey
import app.n_zik.android.utils.playerActionBarButtonOrderKey
import app.n_zik.android.utils.rememberPreference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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

    @Test
    fun stockToolbarResetUsesNewDefaultsAndRespectsSelectedSections() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("stock-toolbar-reset", Context.MODE_PRIVATE)
        val glass = GlassProfilePreferences.preferences(context, "stock-toolbar-reset")
        val saved = bundledDefaultTheme(context)
        val document = saved.document
        val stock = StoreAppearance(document, document.length, parseThemeAppearance(document).toStoreGroups())
        val album = ToolbarSurface.ALBUM_ONLINE
        val playerTop = ToolbarSurface.PLAYER_TOP
        val queue = ToolbarSurface.QUEUE
        val toolbarOrder = """["download","shuffle"]"""
        val legacyToolbar = stockLibraryToolbarKeys().first()
        prefs.edit().putString(album.orderKey, toolbarOrder)
            .putBoolean(album.visibilityKey(ToolbarAction.SHUFFLE), false)
            .putString(legacyToolbar, """["search"]""")
            .putString(miniPlayerButtonOrderKey, """["queue"]""")
            .putString(playerActionBarButtonOrderKey, """["lyrics"]""")
            .putString(playerTop.orderKey, """["audioVideo"]""")
            .putString(queue.orderKey, """["search"]""").commit()

        val reset = requireNotNull(stock.selectedSettings(requireNotNull(saved.theme), 100, setOf(StoreGroup.Navigation)))
        val undo = reset.applyTo(prefs, glass)
        assertEquals(album.actions, album.readVisibleActions(prefs))
        assertFalse(prefs.contains(legacyToolbar))
        assertEquals("""["queue"]""", prefs.getString(miniPlayerButtonOrderKey, null))
        undo()
        assertEquals(toolbarOrder, prefs.getString(album.orderKey, null))
        assertFalse(ToolbarAction.SHUFFLE in album.readVisibleActions(prefs))
        assertTrue(prefs.contains(legacyToolbar))

        val playerReset = requireNotNull(stock.selectedSettings(requireNotNull(saved.theme), 100, setOf(StoreGroup.PlayerAndQueue)))
        val undoPlayerReset = playerReset.applyTo(prefs, glass)
        assertFalse(prefs.contains(playerTop.orderKey))
        assertFalse(prefs.contains(queue.orderKey))
        assertFalse(prefs.contains(miniPlayerButtonOrderKey))
        assertFalse(prefs.contains(playerActionBarButtonOrderKey))
        assertEquals(toolbarOrder, prefs.getString(album.orderKey, null))
        undoPlayerReset()
        assertEquals("""["lyrics"]""", prefs.getString(playerActionBarButtonOrderKey, null))
        assertEquals("""["queue"]""", prefs.getString(miniPlayerButtonOrderKey, null))
    }
}
