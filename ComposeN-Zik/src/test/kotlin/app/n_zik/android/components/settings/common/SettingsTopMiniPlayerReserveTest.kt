package app.n_zik.android.components.settings.common

import org.junit.Assert.assertEquals

import androidx.compose.ui.unit.Density

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.foundation.layout.PaddingValues

import app.n_zik.android.LocalPlayerAwareWindowInsets

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTopPositionInRootIsEqualTo
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.PlayerPosition
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.playerPositionKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.LocalTopBarOffset
import app.n_zik.android.components.navigation.LocalSideRailContentInsets
import app.n_zik.android.components.glass.GlassArea
import app.n_zik.android.components.glass.GlassAreaMode
import app.n_zik.android.components.glass.GlassStore
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.playback.services.PlayerServiceModern
import io.mockk.every
import io.mockk.mockk
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class SettingsTopMiniPlayerReserveTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private val offset = mutableStateOf(0f)

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear()
            .putBoolean(glassEnabledKey, false)
            .putString(navigationBarPositionKey, NavigationBarPosition.BottomFloating.name).commit()
    }

    @Test fun `top mini clears the first title without shortening the scroll viewport`() {
        render(PlayerPosition.Top, hasMedia = true)
        compose.onNodeWithTag("settingsTitle").assertTopPositionInRootIsEqualTo(88.dp)
        assertFullViewport()
        compose.runOnIdle { offset.value = -1000f }
        // Chrome travel must not shrink content padding and move rows away from the finger.
        compose.onNodeWithTag("settingsTitle").assertTopPositionInRootIsEqualTo(88.dp)
        assertFullViewport()
        compose.onNodeWithTag("settingsViewport").performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
            scrollBy(0f, with(compose.density) { 88.dp.toPx() })
        }
        compose.onNodeWithTag("settingsTitle").assertTopPositionInRootIsEqualTo(0.dp)
        assertFullViewport()
    }

    @Test fun `side rail clears settings controls inside the complete scroll viewport`() {
        render(PlayerPosition.Bottom, hasMedia = false, sideRailLeft = 64.dp)
        compose.onNodeWithTag("settingsTitle").assertLeftPositionInRootIsEqualTo(64.dp)
        assertFullViewport()
        compose.onNodeWithTag("settingsTarget").performScrollTo().assertIsDisplayed()
        assertFullViewport()
    }

    @Test fun `bottom mini adds no top settings content inset`() {
        context.preferences.edit().putString(navigationBarPositionKey, NavigationBarPosition.Top.name).commit()
        render(PlayerPosition.Bottom, hasMedia = true)
        compose.onNodeWithTag("settingsTitle").assertTopPositionInRootIsEqualTo(0.dp)
        assertFullViewport()
    }

    @Test fun `top navigation preference adds no ghost bar above the settings mini`() {
        context.preferences.edit().putString(navigationBarPositionKey, NavigationBarPosition.Top.name).commit()
        render(PlayerPosition.Top, hasMedia = true)
        compose.onNodeWithTag("settingsTitle").assertTopPositionInRootIsEqualTo(81.dp)
        assertFullViewport()
    }

    @Test fun `top preference without media leaves no empty title band`() {
        context.preferences.edit().putString(navigationBarPositionKey, NavigationBarPosition.Top.name).commit()
        render(PlayerPosition.Top, hasMedia = false)
        compose.onNodeWithTag("settingsTitle").assertTopPositionInRootIsEqualTo(0.dp)
        assertFullViewport()
    }

    @Test fun `Glass top navigation preference adds no ghost bar on settings`() {
        context.preferences.edit().putString(navigationBarPositionKey, NavigationBarPosition.Top.name)
            .putBoolean(glassEnabledKey, true).commit()
        GlassStore(context).setAreaMode(GlassArea.Nav, GlassAreaMode.GlassAndContour)
        render(PlayerPosition.Top, hasMedia = true)
        compose.onNodeWithTag("settingsTitle").assertTopPositionInRootIsEqualTo(81.dp)
        assertFullViewport()
    }

    @Test fun `vertical settings search target scrolls into the complete viewport`() {
        render(PlayerPosition.Top, hasMedia = true)
        compose.onNodeWithTag("settingsTarget").performScrollTo().assertIsDisplayed()
        assertFullViewport()
    }

    @Test fun `lazy settings padding belongs to scroll content and search targets remain reachable`() {
        render(PlayerPosition.Top, hasMedia = true, lazy = true)
        compose.onNodeWithTag("settingsTitle").assertTopPositionInRootIsEqualTo(88.dp)
        assertFullViewport()
        compose.onNodeWithTag("settingsViewport").performScrollToNode(hasTestTag("settingsTarget"))
        compose.onNodeWithTag("settingsTarget").assertIsDisplayed()
        assertFullViewport()
    }

    @Test fun `vertical settings reserve a larger bottom player once inside the scroll`() =
        assertBottomClearance(lazy = false)

    @Test fun `lazy settings reserve a larger bottom player once inside the scroll`() =
        assertBottomClearance(lazy = true)

    private fun assertBottomClearance(lazy: Boolean) {
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalPlayerAwareWindowInsets provides WindowInsets(bottom = 140.dp),
                LocalSideRailContentInsets provides WindowInsets(left = 50.dp),
            ) {
                Box(Modifier.size(300.dp, 400.dp).testTag("bottom-root")) {
                    if (lazy) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("bottom-scroll"),
                            contentPadding = rememberSettingsContentPadding(PaddingValues(bottom = 100.dp)),
                        ) {
                            items(20) { index ->
                                Box(Modifier.size(100.dp, 40.dp).testTag("bottom-row-$index"))
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().testTag("bottom-scroll")
                            .verticalScroll(rememberScrollState()).settingsTopContentPadding()) {
                            repeat(20) { index ->
                                Box(Modifier.size(100.dp, 40.dp).testTag("bottom-row-$index"))
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("bottom-scroll").performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 100000f)
        }
        val viewport = compose.onNodeWithTag("bottom-root").fetchSemanticsNode().boundsInRoot
        val scroll = compose.onNodeWithTag("bottom-scroll").fetchSemanticsNode().boundsInRoot
        val row = compose.onNodeWithTag("bottom-row-19").fetchSemanticsNode().boundsInRoot
        assertEquals(viewport, scroll)
        assertEquals(viewport.bottom - 140f, row.bottom, 0f)
        assertEquals(viewport.left + 50f, row.left, 0f)
    }

    private fun assertFullViewport() {
        compose.onNodeWithTag("settingsViewport").assertTopPositionInRootIsEqualTo(0.dp)
            .assertHeightIsEqualTo(400.dp)
    }

    private fun render(position: PlayerPosition, hasMedia: Boolean, lazy: Boolean = false, sideRailLeft: Dp = 0.dp) {
        context.preferences.edit().putString(playerPositionKey, position.name).commit()
        val player = mockk<Player>(relaxed = true)
        every { player.currentMediaItem } returns if (hasMedia) MediaItem.Builder().setMediaId("settings-mini").build() else null
        val binder = mockk<PlayerServiceModern.Binder>(relaxed = true)
        every { binder.player } returns player
        compose.setContent {
            CompositionLocalProvider(
                LocalPlayerAwareWindowInsets provides WindowInsets(0, 0, 0, 0),
                LocalPlayerServiceBinder provides binder,
                LocalTopBarOffset provides offset,
                LocalSideRailContentInsets provides WindowInsets(left = sideRailLeft),
            ) {
                Box(Modifier.size(300.dp, 400.dp)) {
                    if (lazy) {
                        LazyColumn(Modifier.fillMaxSize().testTag("settingsViewport"),
                            contentPadding = rememberSettingsContentPadding()) {
                            item { Box(Modifier.size(100.dp, 40.dp).testTag("settingsTitle")) }
                            items(20) { index -> Box(Modifier.size(100.dp, 40.dp)
                                .testTag(if (index == 15) "settingsTarget" else "item$index")) }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().testTag("settingsViewport")
                            .verticalScroll(rememberScrollState()).settingsTopContentPadding()) {
                            Box(Modifier.size(100.dp, 40.dp).testTag("settingsTitle"))
                            Spacer(Modifier.height(600.dp))
                            Box(Modifier.size(100.dp, 40.dp).testTag("settingsTarget"))
                            Spacer(Modifier.height(500.dp))
                        }
                    }
                }
            }
        }
    }
}
