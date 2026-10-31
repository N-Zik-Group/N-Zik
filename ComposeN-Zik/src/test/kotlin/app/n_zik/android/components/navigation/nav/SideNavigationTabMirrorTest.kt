package app.n_zik.android.components.navigation.nav

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.navigation.NavController
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.NavigationBarType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.ColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.navigationBarPositionKey
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.Dependencies
import app.n_zik.android.MainApplication
import app.n_zik.android.R
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.navigation.dock.FloatingDockEnvironment
import app.n_zik.android.components.navigation.dock.FloatingDockState
import app.n_zik.android.components.navigation.dock.LocalFloatingDock
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
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
class SideNavigationTabMirrorTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear().commit()
        val application = mockk<MainApplication>()
        every { application.applicationContext } returns context
        Dependencies.init(application)
    }

    @Test fun `physical side mirrors icon and vertical label across material direction and roundness`() {
        var side by mutableStateOf(NavigationBarPosition.Left)
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var glass by mutableStateOf(false)
        var rounded by mutableStateOf(false)
        var clicks = 0
        compose.setContent {
            val shape = RoundedCornerShape(if (rounded) 24.dp else 0.dp)
            CompositionLocalProvider(
                LocalLayoutDirection provides direction,
                LocalAppearance provides Appearance(ColorPalette.fallback, Typography.fallback, shape, shape, shape),
            ) {
                Box(Modifier.size(64.dp, 136.dp)) {
                    // Selecting an inactive section must dispatch once. The active
                    // Glass puck also handles gestures and ignores reselecting itself.
                    GlassNavigationTabs(1, listOf(0, 1), { clicks++ }, GlassSettings(enabled = glass), true, Modifier.fillMaxSize()) {
                        GlassNavigationTab("Library", R.drawable.search, false, true, Color.White, side) { clicks++ }
                        Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
        for (material in listOf(false, true)) for (rtl in listOf(false, true)) {
            for (roundness in listOf(false, true)) for (position in listOf(NavigationBarPosition.Left, NavigationBarPosition.Right)) {
                compose.runOnIdle {
                    context.preferences.edit().putBoolean(glassEnabledKey, material).commit()
                    glass = material
                    direction = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    rounded = roundness
                    side = position
                }
                val icon = compose.onNodeWithTag("sideNavigationTabIcon", useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val label = compose.onNodeWithTag("sideNavigationTabLabel", useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val scenario = "side=$position glass=$material rtl=$rtl rounded=$roundness icon=$icon label=$label"
                assertTrue(scenario, label.height > label.width && label.width > 0f)
                assertTrue(scenario, if (position == NavigationBarPosition.Right) label.right <= icon.left else icon.right <= label.left)
                val previousClicks = clicks
                compose.onNodeWithContentDescription("Library").performClick()
                compose.runOnIdle { assertEquals(scenario, previousClicks + 1, clicks) }
            }
        }
    }

    @Test fun `default horizontal tabs keep labels below icons and icon only hides labels`() {
        var showLabel by mutableStateOf(true)
        var side by mutableStateOf<NavigationBarPosition?>(null)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, Typography.fallback, shape, shape, shape,
            )) {
                Box(Modifier.size(80.dp, 64.dp)) {
                    GlassNavigationTabs(0, listOf(0), {}, GlassSettings(enabled = false), false, Modifier.fillMaxSize()) {
                        GlassNavigationTab("Library", R.drawable.search, true, showLabel, Color.White, side) {}
                    }
                }
            }
        }
        val label = compose.onNodeWithText("Library", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val tab = compose.onNodeWithContentDescription("Library").fetchSemanticsNode().boundsInRoot
        assertTrue(label.width > label.height)
        assertTrue(label.top > tab.center.y)
        for (position in listOf(NavigationBarPosition.Left, NavigationBarPosition.Right)) {
            compose.runOnIdle { showLabel = false; side = position }
            compose.onNodeWithText("Library", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithTag("sideNavigationTabLabel", useUnmergedTree = true).assertDoesNotExist()
            compose.onNodeWithContentDescription("Library").assertIsDisplayed().performClick()
        }
    }

    @Test fun `top navigation puts labels above icons in native and Glass without changing tab order`() {
        context.preferences.edit()
            .putString(navigationBarPositionKey, NavigationBarPosition.Top.name)
            .putString(navigationBarTypeKey, NavigationBarType.IconAndText.name)

            .putBoolean(glassEnabledKey, false).commit()
        val controller = mockk<NavController>(relaxed = true)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(LocalAppearance provides Appearance(
                ColorPalette.fallback, typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape,
            )) {
                Box(Modifier.size(320.dp, 72.dp)) {
                    val bar = HorizontalNavigationBar(0, {}, controller, Modifier.fillMaxSize())
                    bar.add { item ->
                        item(0, "Library", R.drawable.library)
                        item(1, "Albums", R.drawable.library)
                    }
                    bar.Draw()
                }
            }
        }
        for (glass in listOf(false, true)) {
            compose.runOnIdle { context.preferences.edit().putBoolean(glassEnabledKey, glass).commit() }
            for (text in listOf("Library", "Albums")) {
                val label = compose.onNodeWithText(text, useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val icon = compose.onNode(
                    hasTestTag("horizontalNavigationTabIcon") and hasAnyAncestor(hasContentDescription(text)),
                    useUnmergedTree = true,
                ).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("Top label above icon: glass=$glass text=$text label=$label icon=$icon", label.bottom <= icon.top)
            }
            val library = compose.onNodeWithContentDescription("Library").fetchSemanticsNode().boundsInRoot
            val albums = compose.onNodeWithContentDescription("Albums").fetchSemanticsNode().boundsInRoot
            assertTrue("Top retains horizontal section order: glass=$glass", library.left < albums.left)
        }
    }

    @Test fun `expanded floating labels retain their full line in native and Glass including many tabs`() {
        context.preferences.edit()
            .putString(navigationBarPositionKey, NavigationBarPosition.BottomFloating.name)
            .putString(navigationBarTypeKey, NavigationBarType.IconAndText.name)

            .putBoolean(glassEnabledKey, false).commit()
        val controller = mockk<NavController>(relaxed = true)
        val dock = FloatingDockEnvironment(FloatingDockState(), "home", true, mutableStateOf(0f), false)
        var many by mutableStateOf(false)
        compose.setContent {
            val shape = RoundedCornerShape(12.dp)
            CompositionLocalProvider(
                LocalFloatingDock provides dock,
                LocalAppearance provides Appearance(ColorPalette.fallback,
                    typographyOf(Color.White, true, false, FontType.Rubik), shape, shape, shape),
            ) {
                Box(Modifier.size(360.dp, 100.dp)) {
                    val bar = HorizontalNavigationBar(0, {}, controller)
                    bar.add { item ->
                        val labels = if (many) listOf("Quick picks", "Songs", "Artists", "Albums", "Playlists", "Local")
                            else listOf("Quick picks", "Songs", "Artists")
                        labels.forEachIndexed { index, label -> item(index, label, R.drawable.library) }
                    }
                    bar.Draw()
                }
            }
        }
        for (glass in listOf(false, true)) for (moreTabs in listOf(false, true)) {
            compose.runOnIdle {
                context.preferences.edit().putBoolean(glassEnabledKey, glass).commit()
                many = moreTabs
            }
            for (text in listOf("Quick picks", "Songs", "Artists")) {
                val node = compose.onNodeWithText(text, useUnmergedTree = true).assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val label = node.fetchSemanticsNode().boundsInRoot
                val tab = compose.onNodeWithContentDescription(text).fetchSemanticsNode().boundsInRoot
                val scenario = "glass=$glass many=$moreTabs text=$text label=$label tab=$tab " +
                    "textHeight=${layouts.firstOrNull()?.size?.height} fullLineHeight=${layouts.firstOrNull()?.multiParagraph?.height}"
                assertEquals(scenario, 1, layouts.size)
                // didOverflowHeight also flags width ellipsis after maxLines=1.
                // Compare the actual full paragraph and measured/drawn height instead.
                assertEquals(scenario, 1, layouts.single().lineCount)
                assertTrue(scenario, layouts.single().size.height + 1f >= layouts.single().multiParagraph.height)
                assertTrue(scenario, label.height + 1f >= layouts.single().multiParagraph.height)
                assertTrue(scenario, label.top >= tab.top && label.bottom <= tab.bottom)
            }
        }
    }

}
