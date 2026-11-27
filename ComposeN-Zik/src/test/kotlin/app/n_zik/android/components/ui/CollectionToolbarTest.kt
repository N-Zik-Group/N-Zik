package app.n_zik.android.components.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.settings.ui.ToolbarAction
import app.n_zik.android.components.settings.ui.ToolbarSurface
import app.n_zik.android.components.ui.screens.album.OnlineAlbumToolbar
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.enums.FontType
import app.n_zik.android.ui.components.LocalMenuState
import app.n_zik.android.ui.components.MenuState
import app.n_zik.android.ui.components.tab.toolbar.Button
import app.n_zik.android.ui.components.navigation.header.TabToolBar
import app.n_zik.android.ui.screens.artist.artistLibraryToolbarAction
import app.n_zik.android.ui.styling.Appearance
import app.n_zik.android.ui.styling.DefaultDarkColorPalette
import app.n_zik.android.ui.styling.LocalAppearance
import app.n_zik.android.ui.styling.typographyOf
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassQuality
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.GlassToolbarIcon
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.utils.preferences
import app.n_zik.android.utils.setActiveProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CollectionToolbarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `one trailing More seat retains configured order on wide and narrow bars`() {
        val buttons = listOf("search", "playNext", "shuffle", "favorites", "enqueue")
        assertEquals(
            listOf("search", "playNext", "shuffle") to listOf("favorites", "enqueue"),
            collectionToolbarPlacement(buttons, capacity = 4),
        )
        assertEquals(
            listOf("search") to listOf("playNext", "shuffle", "favorites", "enqueue"),
            collectionToolbarPlacement(buttons, capacity = 2),
        )
        assertEquals(emptyList<String>() to buttons, collectionToolbarPlacement(buttons, capacity = 1))
        assertEquals(buttons to emptyList<String>(), collectionToolbarPlacement(buttons, capacity = 5))
    }

    @Test fun `last playlist heart stays visible and clickable in native and Glass at narrow width`() {
        verifyToolbar(320, 8)
    }

    @Test fun `podcast actions use the same compact spacing and centered separate bookmark`() {
        verifyToolbar(400, 7)
    }

    @Test fun `album shares the playlist width limit in native and Glass`() {
        verifyAvailableWidth(40, 12)
    }

    @Test fun `artist fits Follow inside the playlist width limit`() {
        verifyAvailableWidth(100, 7)
    }

    @Test fun `48 dp action targets move the remaining buttons to More in both renderers`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val actions = listOf(ToolbarAction.SEARCH, ToolbarAction.SHUFFLE, ToolbarAction.ENQUEUE, ToolbarAction.DOWNLOAD)
        val labels = actions.map { application.getString(it.label) }
        val moreLabel = application.getString(R.string.common_menu_label)
        val menuState = MenuState()
        val glass = mutableStateOf(false)
        val width = mutableStateOf(248)
        compose.setContent {
            AppearanceProvider {
                CompositionLocalProvider(LocalMenuState provides menuState) {
                    Box(Modifier.requiredWidth(width.value.dp).testTag("available")) {
                        CollectionButtonToolbar(
                            bookmark = { TabToolBar.Icon(R.drawable.bookmark, onClick = {}) },
                            buttons = actions.map { collectionToolbarAction(it) },
                            settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance),
                        )
                    }
                }
            }
        }

        for (material in listOf(false, true)) {
            compose.runOnIdle {
                glass.value = material
                width.value = 248
                menuState.hide()
            }
            compose.onNodeWithContentDescription(moreLabel).assertDoesNotExist()
            compose.onAllNodes(hasClickAction()).assertCountEquals(5)
            compose.runOnIdle { width.value = 247 }
            val available = compose.onNodeWithTag("available").fetchSemanticsNode().boundsInRoot
            val targetSize = available.width / 247f * 48f
            val bounds = compose.onAllNodes(hasClickAction()).assertCountEquals(4)
                .fetchSemanticsNodes().map { it.boundsInRoot }
            assertTrue(bounds.all { it.width >= targetSize - 1f && it.height >= targetSize - 1f })
            assertEquals(available.center.x, (bounds.minOf { it.left } + bounds.maxOf { it.right }) / 2, 1f)
            labels.take(2).forEach { compose.onNodeWithContentDescription(it).assertIsEnabled() }
            labels.drop(2).forEach { compose.onNodeWithContentDescription(it).assertDoesNotExist() }
            compose.onNodeWithContentDescription(moreLabel).assertIsEnabled().performTouchInput { click() }
            compose.runOnIdle { assertTrue(menuState.isDisplayed) }
        }
    }

    @Test fun `overflow keeps its seat but only opens once collection actions load`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val label = application.getString(R.string.common_menu_label)
        val menuState = MenuState()
        val glass = mutableStateOf(false)
        val loaded = mutableStateOf(false)
        val actions = listOf(ToolbarAction.ADD_TO_PLAYLIST, ToolbarAction.SHUFFLE, ToolbarAction.ENQUEUE)
        compose.setContent {
            AppearanceProvider {
                CompositionLocalProvider(LocalMenuState provides menuState) {
                    CollectionButtonToolbar(
                        bookmark = { TabToolBar.Icon(R.drawable.bookmark, onClick = {}) },
                        buttons = actions.map { artistLibraryToolbarAction(it) },
                        modifier = Modifier.requiredWidth(210.dp),
                        settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance),
                        bookmarkWidth = 100.dp,
                        enabled = loaded.value,
                    )
                }
            }
        }

        for (material in listOf(false, true)) {
            compose.runOnIdle {
                glass.value = material
                loaded.value = false
                menuState.hide()
            }
            val overflow = compose.onNodeWithContentDescription(label)
            overflow.assertIsNotEnabled().performTouchInput { click() }
            val loadingBounds = overflow.fetchSemanticsNode().boundsInRoot
            compose.runOnIdle {
                assertFalse(menuState.isDisplayed)
                loaded.value = true
            }
            overflow.assertIsEnabled().performTouchInput { click() }
            assertEquals(loadingBounds, overflow.fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle { assertTrue(menuState.isDisplayed) }
        }
    }

    @Test fun `online album renders its own toolbar preferences and spreads remaining controls`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        setActiveProfile("default", application)
        val prefs = application.preferences
        val online = ToolbarSurface.ALBUM_ONLINE
        val device = ToolbarSurface.ALBUM_DEVICE
        prefs.edit().putBoolean(glassEnabledKey, false)
            .putBoolean(fullOfflineModeKey, false)
            .putString(online.orderKey, """["enqueue","download","shuffle"]""")
            .putBoolean(online.visibilityKey(ToolbarAction.SHUFFLE), false)
            .putBoolean(device.visibilityKey(ToolbarAction.ENQUEUE), false)
            .commit()
        compose.setContent {
            AppearanceProvider {
                val actions = listOf(ToolbarAction.ENQUEUE, ToolbarAction.DOWNLOAD, ToolbarAction.SHUFFLE, ToolbarAction.RADIO).associateWith { action ->
                    object : Button {
                        @Composable override fun ToolBarButton() {
                            GlassToolbarIcon(painterResource(action.icon), Color.White, 20.dp, true,
                                contentDescription = action.id, onClick = {})
                        }
                    }
                }
                OnlineAlbumToolbar(
                    bookmark = {
                        GlassToolbarIcon(painterResource(R.drawable.bookmark), Color.White, 20.dp, true,
                            contentDescription = "album-bookmark", onClick = {})
                    },
                    actions = actions,
                    modifier = Modifier.requiredWidth(356.dp),
                )
            }
        }

        fun centerX(label: String) = compose.onNodeWithContentDescription(label, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.x
        val primary = centerX("album-bookmark")
        val enqueue = centerX("enqueue")
        val download = centerX("download")
        assertTrue(primary < enqueue && enqueue < download)
        compose.onNodeWithContentDescription("shuffle", useUnmergedTree = true).assertDoesNotExist()
        compose.runOnIdle { prefs.edit().putBoolean(online.visibilityKey(ToolbarAction.SHUFFLE), true).apply() }
        assertEquals(primary, centerX("album-bookmark"), 1f)
        assertTrue(centerX("enqueue") < enqueue && centerX("download") < download)
        assertTrue(centerX("download") < centerX("shuffle"))
        compose.onNodeWithContentDescription("radio", useUnmergedTree = true).assertIsEnabled()

        compose.runOnIdle { prefs.edit().putBoolean(fullOfflineModeKey, true).apply() }
        compose.onNodeWithContentDescription("download", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("radio", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(centerX("enqueue") < centerX("shuffle"))

        compose.runOnIdle { prefs.edit().putBoolean(fullOfflineModeKey, false).apply() }
        assertTrue(centerX("enqueue") < centerX("download"))
        compose.onNodeWithContentDescription("radio", useUnmergedTree = true).assertIsEnabled()
    }

    @Test fun `hiding the bookmark centers the remaining actions in native and Glass`() {
        val glass = mutableStateOf(false)
        val actionCount = mutableStateOf(3)
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.requiredWidth(360.dp).testTag("available")) {
                    CollectionToolbar(
                        bookmark = {
                            GlassToolbarIcon(painterResource(R.drawable.bookmark), Color.White, 32.dp, true,
                                contentDescription = "bookmark", onClick = {})
                        },
                        actions = List(actionCount.value) { index ->
                            @Composable {
                                GlassToolbarIcon(painterResource(R.drawable.play), Color.White, 32.dp, true,
                                    contentDescription = "action.$index", onClick = {})
                            }
                        },
                        showBookmark = false,
                        settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance),
                    )
                }
            }
        }

        for (material in listOf(false, true)) {
            for (count in listOf(1, 3)) {
                compose.runOnIdle {
                    glass.value = material
                    actionCount.value = count
                }
                compose.onNodeWithContentDescription("bookmark").assertDoesNotExist()
                val row = compose.onNodeWithTag("available").fetchSemanticsNode().boundsInRoot
                val controls = compose.onAllNodes(hasClickAction()).assertCountEquals(count)
                    .fetchSemanticsNodes().map { it.boundsInRoot }
                assertEquals(row.center.x, (controls.minOf { it.left } + controls.maxOf { it.right }) / 2, 1f)
            }
        }
    }

    private fun verifyAvailableWidth(bookmarkWidth: Int, actionCount: Int) {
        val width = mutableStateOf(320)
        val glass = mutableStateOf(false)
        val buttons = List(actionCount) {
            object : Button {
                @Composable override fun ToolBarButton() {
                    TabToolBar.Icon(painterResource(R.drawable.play), onClick = {})
                }
            }
        }
        compose.setContent {
            AppearanceProvider {
                CollectionButtonToolbar(
                    bookmark = { TabToolBar.Icon(painterResource(R.drawable.bookmark), onClick = {}) },
                    buttons = buttons,
                    bookmarkWidth = bookmarkWidth.dp,
                    settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance),
                    modifier = Modifier.requiredWidth(width.value.dp),
                )
            }
        }
        for (available in listOf(240, 320, 352, 400, 600)) {
            for (material in listOf(false, true)) {
                compose.runOnIdle {
                    width.value = available
                    glass.value = material
                }
                val capacity = (minOf(available, 356) - maxOf(bookmarkWidth, 48) - 8) / 48
                compose.onAllNodes(hasClickAction()).assertCountEquals(1 + minOf(capacity, actionCount))
            }
        }
    }

    private fun verifyToolbar(width: Int, count: Int) {
        val glass = mutableStateOf(false)
        var heartClicks = 0
        compose.setContent {
            AppearanceProvider {
                Box(Modifier.requiredWidth(width.dp).testTag("available")) {
                    val actions: List<@Composable () -> Unit> = (0 until count).map { index -> @Composable {
                        GlassToolbarIcon(painterResource(R.drawable.heart), Color.White, 32.dp, true,
                            // Real legacy callers supply their own padding. It must not widen the cell.
                            modifier = Modifier.padding(horizontal = 5.dp), modifierOnContainer = true,
                            contentDescription = "action.$index", onClick = { heartClicks++ })
                    } }
                    CollectionToolbar(bookmark = {
                        GlassToolbarIcon(painterResource(R.drawable.bookmark), Color.White, 32.dp, true,
                            contentDescription = "bookmark", onClick = {})
                    }, actions = actions,
                        settings = GlassSettings(enabled = glass.value, quality = GlassQuality.Performance))
                }
            }
        }
        var nativeCenters: List<Float>? = null
        for (material in listOf(false, true)) {
            compose.runOnIdle { glass.value = material }
            compose.waitForIdle()
            compose.onAllNodes(hasClickAction()).assertCountEquals(count + 1)
            val bounds = compose.onNodeWithTag("available").fetchSemanticsNode().boundsInRoot
            val controls = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { it.boundsInRoot }
            controls.forEach {
                assertTrue("Every action fits within the row", it.left >= bounds.left && it.right <= bounds.right)
            }
            val left = controls.minOf { it.left }
            val right = controls.maxOf { it.right }
            assertEquals(bounds.center.x, (left + right) / 2, 1f)
            val centers = controls.map { it.center.x }
            if (material) assertEquals(nativeCenters, centers) else nativeCenters = centers
            val glyph = compose.onNodeWithContentDescription("action.${count - 1}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val previousGlyph = compose.onNodeWithContentDescription("action.${count - 2}", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            assertEquals(20f * bounds.width / width, glyph.width, .1f)
            assertEquals(glyph.width, previousGlyph.width, .1f)
            compose.onAllNodes(hasClickAction())[count].performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(2, heartClicks) }
    }

    @Composable private fun AppearanceProvider(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(DefaultDarkColorPalette,
            typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape), content = content)
    }
}
