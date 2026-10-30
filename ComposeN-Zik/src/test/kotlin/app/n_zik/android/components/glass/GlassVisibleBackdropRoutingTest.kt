package app.n_zik.android.components.glass

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.components.MenuState
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.glass.backdrop.backdrops.layerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.LayerBackdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import app.n_zik.android.components.glass.backdrop.drawPlainBackdrop
import app.n_zik.android.components.menu.GlassDropdownMenu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlassVisibleBackdropRoutingTest {
    @get:Rule val compose = createComposeRule()

    @Before fun isolateBackdropRoutingFromModalDimming() {
        val store = GlassStore(ApplicationProvider.getApplicationContext())
        store.setFloat(glassDialogDimmingKey, 0f)
        store.setFloat(glassMenuDimmingKey, 0f)
    }

    @Test fun `menu only glass samples live completed player without recapturing covered Home`() {
        val host = GlassMenuHostState()
        var expanded by mutableStateOf(true)
        var pageColor by mutableStateOf(Color.Red)
        var playerColor by mutableStateOf(Color.Blue)
        var coverVisible by mutableStateOf(false)
        var coverColor by mutableStateOf(Color.Green)
        lateinit var player: LayerBackdrop
        compose.setContent {
            player = rememberLayerBackdrop()
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).testTag("testScene")) {
                    GlassSceneContent(host, enabled = true) {
                        Box(Modifier.fillMaxSize().graphicsLayer().background(pageColor))
                        // No GlassPlayerBackground or player material is enabled in this fixture.
                        Box(Modifier.fillMaxSize().glassPlayerSceneSource(player, expanded)) {
                            if (expanded) {
                                Box(Modifier.fillMaxSize().background(playerColor))
                                if (coverVisible) Box(Modifier.fillMaxSize().background(coverColor))
                            }
                        }
                        // Like deferred legacy menus, this owner has no player composition local.
                        GlassDialogWindow(true, {}, DialogProperties()) { Sample("isolatedPlayer") }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        assertColor("isolatedPlayer", Color.Blue)
        var rootVersion = 0
        var playerVersion = 0
        compose.runOnIdle {
            assertSame(player, host.modalBackdrop)
            rootVersion = (requireNotNull(host.sceneBackdrop) as LayerBackdrop).contentVersion
            playerVersion = player.contentVersion
            coverVisible = true
        }
        assertColor("isolatedPlayer", Color.Green)
        compose.runOnIdle { coverColor = Color.Yellow; pageColor = Color.Magenta }
        assertColor("isolatedPlayer", Color.Yellow)
        compose.runOnIdle {
            assertEquals("menu frames never record the covered activity subtree", rootVersion,
                (requireNotNull(host.sceneBackdrop) as LayerBackdrop).contentVersion)
            assertTrue("completed player keeps recording its real foreground", player.contentVersion > playerVersion)
            expanded = false
        }
        assertColor("isolatedPlayer", Color.Magenta)
        compose.runOnIdle {
            assertNull(host.foregroundBackdrop)
            assertSame(host.sceneBackdrop, host.modalBackdrop)
            pageColor = Color.Red
        }
        assertColor("isolatedPlayer", Color.Red)
    }

    @Test fun `ordinary overlays keep child layer pixels live`() {
        val host = GlassMenuHostState()
        var foreground by mutableStateOf(Color.Red)
        compose.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).testTag("testScene")) {
                    GlassSceneContent(host, enabled = true) {
                        Box(Modifier.fillMaxSize().graphicsLayer().background(foreground))
                        GlassDialogWindow(true, {}, DialogProperties()) { Sample("liveScene") }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        assertColor("liveScene", Color.Red)
        compose.runOnIdle { foreground = Color.Blue }
        assertColor("liveScene", Color.Blue)
    }

    @Test fun `open overlay follows parent pixels and conditional children without refresh keys`() {
        val host = GlassMenuHostState()
        var background by mutableStateOf(Color.Red)
        var childVisible by mutableStateOf(false)
        var dialogVisible by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).testTag("testScene")) {
                    GlassSceneContent(host, enabled = true) {
                        Box(Modifier.fillMaxSize().background(background)) {
                            if (childVisible) Box(Modifier.fillMaxSize().background(Color.Green))
                            if (dialogVisible) GlassDialogWindow(true, {}, DialogProperties()) {
                                Sample("liveParentScene")
                            }
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        fun renderScene() { compose.onNodeWithTag("testScene").captureToImage() }
        fun version(): Int = (requireNotNull(host.sceneBackdrop) as LayerBackdrop).contentVersion
        renderScene()
        compose.runOnIdle { dialogVisible = true }
        assertColor("liveParentScene", Color.Red)
        compose.runOnIdle { background = Color.Blue }
        assertColor("liveParentScene", Color.Blue)
        compose.runOnIdle { childVisible = true }
        assertColor("liveParentScene", Color.Green)
        compose.runOnIdle { childVisible = false }
        assertColor("liveParentScene", Color.Blue)
        compose.runOnIdle { dialogVisible = false }
        renderScene()
        var closedVersion = 0
        compose.runOnIdle { closedVersion = version() }
        repeat(3) {
            compose.runOnIdle { background = if (it % 2 == 0) Color.Red else Color.Blue }
            renderScene()
        }
        compose.runOnIdle {
            assertEquals("no overlay means no extra scene recording", closedVersion, version())
            dialogVisible = true
        }
        assertColor("liveParentScene", Color.Red)
    }
    @Test fun `direct dialog from bounded mini retains whole scene outside its body`() {
        val host = GlassMenuHostState()
        compose.setContent {
            val page = rememberLayerBackdrop()
            val mini = rememberLayerBackdrop()
            SideEffect { host.backdrop = page }
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).testTag("testScene")) {
                    GlassSceneContent(host, enabled = true) {
                        Box(Modifier.fillMaxSize().layerBackdrop(page).background(Color.Blue))
                        Box(Modifier.align(Alignment.BottomCenter).size(240.dp, 64.dp)
                            .layerBackdrop(mini).background(Color.Green)) {
                            CompositionLocalProvider(LocalGlassOverlayBackdrop provides glassSceneOverlayBackdrop(mini)) {
                                GlassDialogWindow(true, {}, DialogProperties()) {
                                    Box(Modifier.fillMaxSize()) {
                                        Sample("outsideMini", Modifier.align(Alignment.TopCenter))
                                        Sample("insideMini", Modifier.align(Alignment.BottomCenter))
                                    }
                                }
                            }
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        assertColor("outsideMini", Color.Blue)
        assertColor("insideMini", Color.Green)
        compose.runOnIdle { assertSame(host.sceneBackdrop, host.dialogs.entries.single().backdrop()) }
    }

    @Test fun `deferred legacy menu samples topmost scene player then mini then page at same position`() {
        val host = GlassMenuHostState()
        val menu = MenuState()
        var playerVisible by mutableStateOf(true)
        var miniVisible by mutableStateOf(true)
        compose.setContent {
            val page = rememberLayerBackdrop()
            SideEffect { host.backdrop = page; host.overlayBackdrop = page }
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).testTag("testScene")) {
                    GlassSceneContent(host, enabled = true, refreshKey = playerVisible to miniVisible) {
                        Box(Modifier.fillMaxSize().layerBackdrop(page).background(Color.Blue))
                        if (miniVisible) Box(Modifier.fillMaxSize().background(Color.Green))
                        if (playerVisible) Box(Modifier.fillMaxSize().background(Color.Red)) {
                            Box(Modifier.fillMaxSize().background(Color.Yellow))
                        }
                    }
                    // MenuState retains a bare lambda and replays it outside the player's locals.
                    if (menu.isDisplayed) menu.contentState.second()
                    GlassDialogOverlayHost(host)
                }
            }
        }
        compose.runOnIdle {
            menu.display { GlassDialogWindow(true, {}, DialogProperties()) { Sample("deferred") } }
        }
        assertColor("deferred", Color.Yellow)
        compose.runOnIdle {
            assertSame(host.sceneBackdrop, host.dialogs.entries.single().backdrop())
            playerVisible = false
        }
        assertColor("deferred", Color.Green)
        compose.runOnIdle { miniVisible = false }
        assertColor("deferred", Color.Blue)
        compose.runOnIdle { menu.hide() }
        compose.runOnIdle {
            playerVisible = true
            menu.display { GlassDialogWindow(true, {}, DialogProperties()) { Sample("deferred") } }
        }
        assertColor("deferred", Color.Yellow)
    }

    @Test fun `dropdown owner hands completed player source to global host instead of page`() {
        val preferences = ApplicationProvider.getApplicationContext<Application>().preferences
        val previousEnabled = preferences.getBoolean(glassEnabledKey, true)
        preferences.edit().putBoolean(glassEnabledKey, true).commit()
        try {
            val host = GlassMenuHostState()
            compose.setContent {
                val page = rememberLayerBackdrop()
                val player = rememberLayerBackdrop()
                val shape = RoundedCornerShape(0.dp)
                SideEffect { host.backdrop = page; host.overlayBackdrop = page }
                CompositionLocalProvider(
                    LocalGlassMenuHost provides host,
                    LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
                ) {
                    Box(Modifier.size(240.dp).testTag("testScene")) {
                        Box(Modifier.fillMaxSize().layerBackdrop(page).background(Color.Blue))
                        CompositionLocalProvider(LocalGlassOverlayBackdrop provides player) {
                            Box(Modifier.fillMaxSize().layerBackdrop(player).background(Color.Red)) {
                                Box(Modifier.fillMaxSize().background(Color.Yellow))
                                GlassDropdownMenu(true, {}, Color.Transparent, shape) { Sample("dropdown") }
                            }
                        }
                        GlassOverlayHost(host)
                    }
                }
            }
            assertColor("dropdown", Color.Yellow)
            compose.runOnIdle { assertNotSame(host.modalBackdrop, requireNotNull(host.entry).backdrop) }
        } finally {
            preferences.edit().putBoolean(glassEnabledKey, previousEnabled).commit()
        }
    }

    @Test fun `dialog opened over player samples its composed foreground and restores page source on reopen`() {
        val host = GlassMenuHostState()
        var playerVisible by mutableStateOf(true)
        var foreground by mutableStateOf(Color.Yellow)
        var dialogVisible by mutableStateOf(true)
        compose.setContent {
            val page = rememberLayerBackdrop()
            val player = rememberLayerBackdrop()
            SideEffect { host.backdrop = page; host.overlayBackdrop = page }
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp).testTag("testScene")) {
                    Box(Modifier.fillMaxSize().layerBackdrop(page).background(Color.Blue))
                    if (playerVisible) {
                        CompositionLocalProvider(LocalGlassOverlayBackdrop provides player) {
                            Box(Modifier.fillMaxSize().layerBackdrop(player).background(Color.Red)) {
                                // Real foreground obscures the red player background at the sampled position.
                                Box(Modifier.fillMaxSize().background(foreground))
                                if (dialogVisible) GlassDialogWindow(true, {}, DialogProperties()) { Sample("dialog") }
                            }
                        }
                    } else if (dialogVisible) {
                        GlassDialogWindow(true, {}, DialogProperties()) { Sample("dialog") }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        assertColor("dialog", Color.Yellow)
        compose.runOnIdle { foreground = Color.Green }
        assertColor("dialog", Color.Green)
        compose.runOnIdle { dialogVisible = false }
        compose.runOnIdle { playerVisible = false; dialogVisible = true }
        assertColor("dialog", Color.Blue)
        compose.runOnIdle { assertSame(host.modalBackdrop, host.dialogs.entries.single().backdrop()) }
    }

    @Test fun `nested dialog binds to retained visible parent composition without changing ancestor source`() {
        val host = GlassMenuHostState()
        var childVisible by mutableStateOf(false)
        compose.setContent {
            val page = rememberLayerBackdrop()
            SideEffect { host.backdrop = page }
            CompositionLocalProvider(LocalGlassMenuHost provides host) {
                Box(Modifier.size(240.dp)) {
                    Box(Modifier.fillMaxSize().layerBackdrop(page).background(Color.Blue))
                    GlassDialogWindow(true, {}, DialogProperties()) {
                        Box(Modifier.size(160.dp).background(Color.Red)) {
                            if (childVisible) GlassDialogWindow(true, {}, DialogProperties()) { Sample("child") }
                        }
                    }
                    GlassDialogOverlayHost(host)
                }
            }
        }
        compose.runOnIdle { childVisible = true }
        compose.runOnIdle {
            val entries = host.dialogs.entries
            assertEquals(2, entries.size)
            assertSame(host.modalBackdrop, entries.first().backdrop())
            assertNotSame("child uses completed parent rather than the raw page", host.modalBackdrop, entries.last().backdrop())
        }
        compose.runOnIdle { childVisible = false }
        compose.runOnIdle {
            assertEquals(1, host.dialogs.entries.size)
            assertSame(host.modalBackdrop, host.dialogs.entries.single().backdrop())
        }
    }

    @Composable
    private fun Sample(tag: String, modifier: Modifier = Modifier) {
        val backdrop = requireNotNull(LocalGlassBackdrop.current)
        Box(modifier.size(80.dp).background(Color.Magenta)
            .drawPlainBackdrop(backdrop, { RectangleShape }, effects = {})
            .testTag(tag))
    }

    private fun assertColor(tag: String, expected: Color) {
        val scene = compose.onNodeWithTag("testScene")
        val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
        val sampleBounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        // Capture the activity scene: dialog() semantics intentionally has no native dialog window.
        val pixels = scene.captureToImage().toPixelMap()
        val x = (sampleBounds.center.x - sceneBounds.left).toInt().coerceIn(0, pixels.width - 1)
        val y = (sampleBounds.center.y - sceneBounds.top).toInt().coerceIn(0, pixels.height - 1)
        val actual = pixels[x, y]
        assertEquals("red", expected.red, actual.red, .03f)
        assertEquals("green", expected.green, actual.green, .03f)
        assertEquals("blue", expected.blue, actual.blue, .03f)
    }
}
