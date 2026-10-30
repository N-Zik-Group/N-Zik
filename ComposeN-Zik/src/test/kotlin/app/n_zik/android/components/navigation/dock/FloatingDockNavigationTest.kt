package app.n_zik.android.components.navigation.dock

import android.app.Application
import android.content.Context
import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.enums.FontType
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.typographyOf
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.n_zik.android.R
import app.n_zik.android.components.glass.glassEnabledKey
import app.n_zik.android.components.glass.GlassSettings
import app.n_zik.android.components.navigation.nav.GlassNavigationTabs
import app.n_zik.android.utils.toast.BottomNavigationToastPlacement
import app.n_zik.android.utils.toast.trackToastObstruction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w360dp-h800dp")
class FloatingDockNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var context: Context
    private val state = FloatingDockState()
    private val clock = mutableStateOf(0f)
    private var searches = 0
    private var tabCompositions = 0
    private var tabDisposals = 0
    private val measuredTabWidths = mutableListOf<Int>()
    private val selectedTab = mutableStateOf(0)
    private val tabChanges = mutableListOf<Int>()
    private val showTrackedMini = mutableStateOf(false)
    private val bottomMiniOverride = mutableStateOf<Boolean?>(null)
    private lateinit var hostView: View
    private var dockTop = 0f
    private var miniTop = 0f

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        setActiveProfile("default", context)
        context.preferences.edit().clear().putBoolean(glassEnabledKey, false).commit()
    }

    @Test fun floatingWithoutBottomMiniShrinksAndCentersHomeOnTheSharedScrollClock() {
        render(bottomMini = false, showSearch = false, floating = true)
        val expanded = compose.onNodeWithTag("dockHost").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.onScroll(-80f, 50f) }
        for (progress in listOf(.25f, .5f, .75f, 1f)) {
            compose.runOnIdle { clock.value = progress }
            compose.mainClock.advanceTimeByFrame()
            val bounds = compose.onNodeWithTag("dockHost").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.height < expanded.height)
        }
        compose.onNodeWithText("Registered songs tab").assertDoesNotExist()
        val home = compose.onNodeWithContentDescription(context.getString(R.string.home)).fetchSemanticsNode().boundsInRoot
        assertEquals(expanded.center.x, home.center.x, 1f)
        compose.onNodeWithContentDescription(context.getString(R.string.home)).performClick()
        compose.runOnIdle { assertEquals(0f, state.foldTarget) }
    }

    @Test fun expandedAndCompactSearchUseSameActionAndHomeUnfolds() {
        render(bottomMini = true)
        compose.onNodeWithTag("dockHost").assertHeightIsEqualTo(61.dp)
        compose.onNodeWithText("Registered songs tab").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.search)).performClick()
        compose.runOnIdle {
            clock.value = 1f
            state.foldTarget = 1f
        }
        compose.onNodeWithText("Registered songs tab").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.search)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.home))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle {
            assertEquals(2, searches)
            assertEquals(0f, state.foldTarget)
        }
    }

    @Test fun `top mini or absent media folds navigation into centered Home and can unfold`() {
        render(bottomMini = false, realTabs = true, showSearch = false)
        val dockHost = compose.onNodeWithTag("dockHost")
        dockHost.assertHeightIsEqualTo(61.dp)
        compose.runOnIdle { clock.value = .5f; state.foldTarget = 1f }
        compose.runOnIdle { clock.value = 1f }
        dockHost.assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("Tab 1").assertDoesNotExist()
        val home = compose.onNodeWithContentDescription(context.getString(R.string.home))
        assertEquals(dockHost.fetchSemanticsNode().boundsInRoot.center.x,
            home.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        home.performClick()
        compose.runOnIdle { clock.value = 0f }
        compose.onNodeWithText("Tab 1").assertIsDisplayed().performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, selectedTab.value); assertEquals(0f, state.foldTarget) }
    }

    @Test fun foldedSearchAndHomeMatchSquare48DpControls() {
        clock.value = 1f
        state.foldTarget = 1f
        render(bottomMini = true)
        compose.onNodeWithContentDescription(context.getString(R.string.home))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        compose.onNodeWithContentDescription(context.getString(R.string.search))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
    }

    @Test fun `search page has no duplicate shortcut expanded or folded with a bottom mini`() {
        render(bottomMini = true, showSearch = false)
        compose.onNodeWithText("Registered songs tab").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.search)).assertDoesNotExist()
        compose.runOnIdle { clock.value = 1f; state.foldTarget = 1f }
        compose.onNodeWithContentDescription(context.getString(R.string.search)).assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.home))
            .assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).performClick()
        compose.runOnIdle { assertEquals(0f, state.foldTarget); assertEquals(0, searches) }
    }

    @Test fun `search page without bottom mini folds into Home without a redundant search control`() {
        render(bottomMini = false, showSearch = false)
        compose.onNodeWithContentDescription(context.getString(R.string.search)).assertDoesNotExist()
        compose.runOnIdle { clock.value = 1f; state.foldTarget = 1f }
        compose.onNodeWithContentDescription(context.getString(R.string.home)).assertWidthIsEqualTo(48.dp)
        compose.onNodeWithTag("dockHost").assertHeightIsEqualTo(48.dp)
        compose.onNodeWithText("Registered songs tab").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.search)).assertDoesNotExist()
    }

    @Test fun `removing a bottom mini centers Home without resetting the shared fold clock`() {
        clock.value = 1f
        state.foldTarget = 1f
        render(bottomMini = true, showSearch = false)
        val home = compose.onNodeWithContentDescription(context.getString(R.string.home))
        home.assertWidthIsEqualTo(48.dp)
        val attachedX = home.fetchSemanticsNode().boundsInRoot.center.x
        compose.runOnIdle { bottomMiniOverride.value = false }
        compose.waitForIdle()
        home.assertWidthIsEqualTo(48.dp)
        val hostBounds = compose.onNodeWithTag("dockHost").fetchSemanticsNode().boundsInRoot
        assertEquals(hostBounds.center.x, home.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        compose.onNodeWithText("Registered songs tab").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1f, clock.value); assertEquals(1f, state.foldTarget) }
        compose.runOnIdle { bottomMiniOverride.value = true }
        compose.waitForIdle()
        assertEquals(attachedX, home.fetchSemanticsNode().boundsInRoot.center.x, 1f)
        compose.runOnIdle { assertEquals(1, tabCompositions); assertEquals(0, tabDisposals) }
    }

    @Test fun foldingKeepsTabsComposedAndMeasuredAtTheirRestingWidth() {
        render(bottomMini = true)
        listOf(.25f, .5f, .75f, 1f, .5f, 0f).forEach { progress ->
            compose.runOnIdle {
                clock.value = progress
                state.foldTarget = if (progress == 0f) 0f else 1f
            }
            compose.runOnIdle {
                assertEquals("Folding must retain tab drag and selection state", 1, tabCompositions)
                assertEquals("Folding must not dispose tabs", 0, tabDisposals)
                assertTrue(measuredTabWidths.isNotEmpty())
                assertTrue(measuredTabWidths.first() > 0)
                assertEquals("Only the surface shrinks, not the tab layout", 1, measuredTabWidths.distinct().size)
            }
        }
        compose.onNodeWithText("Registered songs tab").assertIsDisplayed()
    }

    @Test fun `dragging the selected Glass tab through the real expanded dock selects the next tab`() {
        context.preferences.edit().putBoolean(glassEnabledKey, true).commit()
        render(bottomMini = true, realTabs = true, glassTabs = true)
        compose.onNodeWithTag("dockTabs").performTouchInput {
            swipe(Offset(width / 8f, center.y), Offset(width * 3f / 8f, center.y), 500)
        }
        compose.runOnIdle {
            assertEquals(1, selectedTab.value)
            assertEquals(listOf(1), tabChanges)
            assertEquals(0f, state.foldTarget)
        }
    }

    @Test fun `native tabs remain physically clickable through the expanded dock`() {
        render(bottomMini = true, realTabs = true)
        compose.onNodeWithText("Tab 1").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, selectedTab.value)
            assertEquals(listOf(1), tabChanges)
        }
    }

    @Test fun `toast obstruction follows actual dock bounds and yields to a higher mini player`() {
        render(bottomMini = false, fullWindow = true)
        compose.runOnIdle {
            // Match the visible-window fixture used by BottomNavigationToastPlacementTest.
            val attachInfo = ReflectionHelpers.getField<Any>(hostView, "mAttachInfo")
            ReflectionHelpers.setField(attachInfo, "mWindowVisibility", View.VISIBLE)
        }
        listOf(0f, 1f).forEach { progress ->
            compose.runOnIdle {
                clock.value = progress
                state.foldTarget = progress
            }
            compose.runOnIdle {
                assertTrue("The dock must be bottom aligned in a real window", dockTop > 0f)
                assertEquals(dockTop, requireNotNull(BottomNavigationToastPlacement.topInWindow()), 1f)
            }
        }
        compose.runOnIdle { showTrackedMini.value = true }
        compose.runOnIdle {
            assertTrue(miniTop < dockTop)
            assertEquals(miniTop, requireNotNull(BottomNavigationToastPlacement.topInWindow()), 1f)
        }
        compose.runOnIdle { showTrackedMini.value = false }
        compose.runOnIdle {
            assertEquals(dockTop, requireNotNull(BottomNavigationToastPlacement.topInWindow()), 1f)
        }
    }

    @Test fun `mini action reveal changes occupied width continuously while preserving its handler`() {
        val fraction = mutableStateOf(0f)
        var creations = 0
        var disposals = 0
        var clicks = 0
        compose.setContent {
            Box(Modifier.testTag("actionSlot")) {
                FloatingMiniActionSlot(fraction.value) {
                    DisposableEffect(Unit) {
                        creations++
                        onDispose { disposals++ }
                    }
                    Box(Modifier.width(48.dp).height(48.dp).clickable { clicks++ }
                        .testTag("actionContent"))
                }
            }
        }
        compose.onNodeWithTag("actionSlot").assertWidthIsEqualTo(0.dp)
        listOf(.25f to 12.dp, .5f to 24.dp, .75f to 36.dp, 1f to 48.dp).forEach { (progress, width) ->
            compose.runOnIdle { fraction.value = progress }
            compose.onNodeWithTag("actionSlot").assertWidthIsEqualTo(width)
        }
        compose.onNodeWithTag("actionSlot").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, clicks)
            fraction.value = .25f
        }
        compose.onNodeWithTag("actionSlot").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals("A mostly hidden action must not receive taps", 1, clicks)
            fraction.value = 0f
        }
        compose.onNodeWithTag("actionSlot").assertWidthIsEqualTo(0.dp)
        compose.runOnIdle {
            assertEquals(1, creations)
            assertEquals(0, disposals)
        }
    }

    private fun render(bottomMini: Boolean, realTabs: Boolean = false, glassTabs: Boolean = false,
        fullWindow: Boolean = false, showSearch: Boolean = true, floating: Boolean = true) {
        compose.setContent {
            hostView = LocalView.current
            val shape = RoundedCornerShape(12.dp)
            val palette = DefaultDarkColorPalette
            CompositionLocalProvider(LocalAppearance provides Appearance(
                colorPalette = palette,
                typography = typographyOf(palette.text, true, false, FontType.Rubik),
                thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
            )) {
                Box(if (fullWindow) Modifier.fillMaxSize() else Modifier.width(360.dp)) {
                    if (showTrackedMini.value) {
                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)) {
                            Box(Modifier.fillMaxWidth().height(56.dp).trackToastObstruction()
                                .onGloballyPositioned { miniTop = it.boundsInWindow().top })
                        }
                    }
                    Box(Modifier.align(Alignment.BottomCenter).width(360.dp).testTag("dockHost")
                        .onGloballyPositioned { dockTop = it.boundsInWindow().top }) {
                    FloatingDockNavigation(
                        dock = FloatingDockEnvironment(state, "home", true, clock, bottomMiniOverride.value ?: bottomMini, floating = floating),
                        expandedHeight = 61.dp,
                        bottomPadding = 0.dp,
                        onSearch = { searches++ },
                        showSearch = showSearch,
                        tabsCount = if (realTabs) 4 else 5,
                        selectedIndex = selectedTab.value,
                        tabs = { modifier, backdrop ->
                            DisposableEffect(Unit) {
                                tabCompositions++
                                onDispose { tabDisposals++ }
                            }
                            if (realTabs) GlassNavigationTabs(
                                selectedIndex = selectedTab.value,
                                indices = listOf(0, 1, 2, 3),
                                onTabChanged = { selectedTab.value = it; tabChanges += it },
                                settings = GlassSettings(enabled = glassTabs),
                                vertical = false,
                                modifier = modifier.testTag("dockTabs"),
                                drawContainer = false,
                                externalSurfaceBackdrop = backdrop,
                            ) {
                                repeat(4) { index ->
                                    Box(Modifier.fillMaxSize().clickable {
                                        selectedTab.value = index
                                        tabChanges += index
                                    }, contentAlignment = Alignment.Center) { Text("Tab $index") }
                                }
                            } else Row(modifier.onSizeChanged { measuredTabWidths += it.width }) {
                                Text("Registered songs tab", Modifier.weight(1f))
                                if (showSearch) Box(Modifier.width(61.dp).fillMaxHeight()
                                    .semantics { contentDescription = context.getString(R.string.search) }
                                    .clickable { searches++ })
                            }
                        },
                    )
                    }
                }
            }
        }
    }
}
