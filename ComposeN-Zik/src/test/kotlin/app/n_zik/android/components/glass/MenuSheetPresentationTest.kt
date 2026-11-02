package app.n_zik.android.components.glass

import kotlin.math.roundToInt

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.n_zik.android.components.glass.backdrop.backdrops.rememberCanvasBackdrop
import app.n_zik.android.components.menu.GlassDropdownMenu
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.menu.straightBottomMenuShape

@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class, qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuSheetPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `menu normalization preserves asymmetric and percentage upper corners in both directions`() {
        val size = Size(240f, 160f)
        val density = Density(1f)
        for (source in listOf(
            RoundedCornerShape(topStart = 12.dp, topEnd = 28.dp, bottomStart = 18.dp, bottomEnd = 32.dp),
            RoundedCornerShape(topStartPercent = 15, topEndPercent = 25, bottomStartPercent = 20, bottomEndPercent = 30),
        )) {
            val normalized = straightBottomMenuShape(source) as CornerBasedShape
            assertEquals(source.topStart, normalized.topStart)
            assertEquals(source.topEnd, normalized.topEnd)
            for (direction in LayoutDirection.entries) {
                val original = (source.createOutline(size, direction, density) as Outline.Rounded).roundRect
                val menu = (normalized.createOutline(size, direction, density) as Outline.Rounded).roundRect
                assertEquals(original.topLeftCornerRadius, menu.topLeftCornerRadius)
                assertEquals(original.topRightCornerRadius, menu.topRightCornerRadius)
                assertEquals(0f, menu.bottomLeftCornerRadius.x, 0f)
                assertEquals(0f, menu.bottomRightCornerRadius.x, 0f)
            }
        }
    }

    @Test fun `custom modal shared sheet centers handle and keeps lower corners square`() =
        assertCustomModalPresentation(shared = true)

    @Test fun `custom modal native sheet centers handle and keeps lower corners square`() =
        assertCustomModalPresentation(shared = false)

    @Test fun `native glass film and open bottom rim follow changing bottom insets`() {
        val bottomInset = mutableStateOf(0)
        lateinit var sheetState: SheetState
        lateinit var nativeView: View
        compose.setContent {
            Fixture(shared = false, direction = LayoutDirection.Ltr) {
                CompositionLocalProvider(LocalGlassPreviewSettings provides GlassSettings(
                    film = 1f, tintStrength = 0f, blur = 0f, vibrancy = 0f,
                    depth = 0f, amount = 0f, rim = 1f,
                )) {
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    CustomModalBottomSheet(
                        showSheet = true, onDismissRequest = {}, sheetState = sheetState,
                        shape = RoundedCornerShape(32.dp), containerColor = Color.DarkGray,
                        scrimColor = Color.Transparent, dragHandle = null,
                        contentWindowInsets = { WindowInsets(bottom = bottomInset.value) },
                    ) {
                        nativeView = LocalView.current
                        // Transparent content exposes the actual native material and its rim.
                        Box(Modifier.fillMaxWidth().height(120.dp).testTag("sheet.material.body"))
                    }
                }
            }
        }
        var restingTop = Float.NaN
        // Exercise navigation-bar padding, a larger IME inset, and keyboard dismissal
        // without recreating the sheet or its material.
        for (inset in listOf(0, 32, 96, 0)) {
            compose.runOnIdle { bottomInset.value = inset }
            settleSheetMotion()
            compose.runOnIdle { assertEquals(SheetValue.Expanded, sheetState.currentValue) }
            val body = compose.onNodeWithTag("sheet.material.body").fetchSemanticsNode().boundsInRoot
            // Select the native window root, not the activity root or the caller's
            // modifier: the material must move with the native sheet's offset.
            val root = compose.onNode(isRoot() and hasAnyDescendant(hasTestTag("sheet.material.body")),
                useUnmergedTree = true)
            val rootBounds = root.fetchSemanticsNode().boundsInRoot
            // Draw the dialog's own Compose view. A root semantics screenshot can
            // resolve the activity surface instead of the separate native window.
            val bitmap = compose.runOnIdle {
                Bitmap.createBitmap(nativeView.width, nativeView.height, Bitmap.Config.ARGB_8888).also {
                    nativeView.draw(Canvas(it))
                }
            }
            val pixels = bitmap.asImageBitmap().toPixelMap()
            val left = (body.left - rootBounds.left).roundToInt()
            val right = (body.right - rootBounds.left).roundToInt() - 1
            val top = (body.top - rootBounds.top).roundToInt()
            val bottom = (body.bottom - rootBounds.top).roundToInt() + inset
            val center = (left + right) / 2
            val context = "inset=$inset body=$body root=$rootBounds"
            if (restingTop.isNaN()) restingTop = body.top
            assertEquals("Body stays 120dp tall: $context", 120f, body.height, .5f)
            assertEquals("Sheet moves up with its padding: $context", restingTop - inset, body.top, .5f)
            assertEquals("Material reaches the native window bottom: $context", pixels.height, bottom)

            fun brightness(x: Int, y: Int): Float = pixels[x, y].let {
                (it.red + it.green + it.blue) / 3f * it.alpha
            }
            val film = brightness(center, top + 60)
            assertTrue("Native film is visibly painted: $context", film > .05f)
            assertTrue("Material does not leak at the window top: $context", brightness(center, 0) < .01f)
            assertTrue("Material starts at the moving sheet: $context", brightness(center, top - 3) < .01f)
            assertTrue("Upper left remains rounded: $context", brightness(left + 2, top + 2) < .01f)
            assertTrue("Upper right remains rounded: $context", brightness(right - 2, top + 2) < .01f)
            assertTrue("Top rim is visible: $context", brightness(center, top) > film + .1f)
            for (x in listOf(left, right)) {
                assertTrue("Side rim is visible at x=$x: $context", brightness(x, top + 60) > film + .1f)
                assertEquals("Lower corners retain film at x=$x: $context", film, brightness(x, bottom - 1), .02f)
            }
            assertEquals("Film covers the bottom inset: $context", film, brightness(center, bottom - 2), .02f)
            assertEquals("No horizontal bottom rim: $context", film, brightness(center, bottom - 1), .02f)
            if (inset > 0) {
                val paddingMiddle = bottom - inset / 2
                assertEquals("Film fills inset interior: $context", film, brightness(center, paddingMiddle), .02f)
                assertTrue("Side rim extends through inset: $context", brightness(left, paddingMiddle) > film + .1f)
            }
        }
    }

    private fun assertCustomModalPresentation(shared: Boolean) {
        val scenario = mutableStateOf(LayoutDirection.Ltr to Color.Transparent)
        lateinit var host: GlassMenuHostState
        lateinit var sheetState: SheetState
        compose.setContent {
            key(scenario.value) {
                var visible by remember { mutableStateOf(false) }
                Fixture(shared, scenario.value.first, onHost = { host = it }) {
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    Text("Open sheet", Modifier.clickable { visible = true })
                    CustomModalBottomSheet(
                        showSheet = visible, onDismissRequest = { visible = false },
                        sheetState = sheetState,
                        shape = RoundedCornerShape(32.dp),
                        containerColor = scenario.value.second,
                        contentWindowInsets = { WindowInsets(0) },
                        dragHandle = { Handle() },
                    ) { Body() }
                }
            }
        }
        for (direction in LayoutDirection.entries) for (color in listOf(Color.Transparent, Color.DarkGray)) {
            compose.runOnIdle { scenario.value = direction to color }
            compose.onNodeWithText("Open sheet").performClick()
            settleSheetMotion()
            compose.runOnIdle {
                assertEquals("Sheet must finish entering before measurement", SheetValue.Expanded, sheetState.currentValue)
                assertEquals("Fixture must exercise the requested sheet host", shared, host.dialogs.hasDialogs)
            }
            assertCenteredHandle("shared=$shared direction=$direction color=$color offset=${sheetState.requireOffset()}")
            // The white content reaches the physical lower corners of the supplied
            // all-rounded sheet only when its actual clipping shape is normalized.
            val bodyNode = compose.onNodeWithTag("sheet.body")
            val pixels = if (shared) {
                // Shared overlays carry dialog semantics but draw in the activity.
                val scene = compose.onNodeWithTag("sheet.scene")
                val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
                val bodyBounds = bodyNode.fetchSemanticsNode().boundsInRoot
                scene.captureToImage().toPixelMap(
                    startX = (bodyBounds.left - sceneBounds.left).roundToInt(),
                    startY = (bodyBounds.top - sceneBounds.top).roundToInt(),
                    width = bodyBounds.width.roundToInt(), height = bodyBounds.height.roundToInt(),
                )
            } else bodyNode.captureToImage().toPixelMap()
            for (x in listOf(1, pixels.width - 2)) {
                val corner = pixels[x, pixels.height - 2]
                assertTrue("Square lower corner shared=$shared direction=$direction color=$color: $corner",
                    corner.red > .95f && corner.green > .95f && corner.blue > .95f)
            }
            if (!shared) {
                val nativeHandle = compose.onNode(hasClickAction() and
                    SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
                if (color == Color.Transparent) nativeHandle.performSemanticsAction(SemanticsActions.Dismiss) { it() }
                else nativeHandle.performClick()
                settleSheetMotion()
                compose.runOnIdle { assertEquals(SheetValue.Hidden, sheetState.currentValue) }
                compose.onNodeWithTag("sheet.body").assertDoesNotExist()
            }
        }
    }

    @Test fun `native handle retains accessible expand collapse and dismiss actions`() {
        lateinit var state: SheetState
        var dismissals = 0
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            Fixture(shared = false, direction = LayoutDirection.Ltr) {
                state = rememberModalBottomSheetState(skipPartiallyExpanded = false)
                CustomModalBottomSheet(
                    showSheet = visible, onDismissRequest = { dismissals++; visible = false },
                    sheetState = state, containerColor = Color.DarkGray,
                    contentWindowInsets = { WindowInsets(0) }, dragHandle = { Handle() },
                ) { Box(Modifier.fillMaxWidth().height(500.dp).background(Color.White)) }
            }
        }
        settleSheetMotion()
        compose.runOnIdle { assertEquals(SheetValue.PartiallyExpanded, state.currentValue) }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Expand))
            .performSemanticsAction(SemanticsActions.Expand) { it() }
        settleSheetMotion()
        compose.runOnIdle { assertEquals(SheetValue.Expanded, state.currentValue) }
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Collapse))
            .performSemanticsAction(SemanticsActions.Collapse) { it() }
        settleSheetMotion()
        compose.runOnIdle { assertEquals(SheetValue.PartiallyExpanded, state.currentValue) }
        compose.onNode(hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss))
            .performSemanticsAction(SemanticsActions.Dismiss) { it() }
        settleSheetMotion()
        compose.runOnIdle {
            assertEquals(SheetValue.Hidden, state.currentValue)
            assertEquals(1, dismissals)
        }
    }

    private fun settleSheetMotion() {
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    @Test fun `height limited shared sheet centers its manual handle in both directions`() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            key(direction.value) {
                Fixture(shared = true, direction = direction.value) {
                    SharedGlassMenuSheet(
                        showSheet = true, onDismissRequest = {},
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        maxSheetHeight = 124.dp,
                        shape = RoundedCornerShape(32.dp),
                        dragHandle = { Handle() },
                    ) { Body() }
                }
            }
        }
        for (layout in LayoutDirection.entries) {
            compose.runOnIdle { direction.value = layout }
            compose.waitForIdle()
            assertCenteredHandle()
            val handle = compose.onNodeWithTag("sheet.handle", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val body = compose.onNodeWithTag("sheet.body").fetchSemanticsNode().boundsInRoot
            assertTrue("Height cap includes the manual handle", body.bottom - handle.top <= 124.5f)
        }
    }

    private fun assertCenteredHandle(scenario: String = "shared height limited") {
        val handleNode = compose.onNodeWithTag("sheet.handle", useUnmergedTree = true).fetchSemanticsNode()
        val bodyNode = compose.onNodeWithTag("sheet.body").fetchSemanticsNode()
        val handle = handleNode.boundsInRoot
        val body = bodyNode.boundsInRoot
        val context = "$scenario handle=$handle raw=${handleNode.size}@${handleNode.positionInRoot} " +
            "body=$body raw=${bodyNode.size}@${bodyNode.positionInRoot}"
        assertTrue("Handle must have visible bounds: $context", handle.width > 0f && handle.height > 0f)
        assertTrue("Sheet content must have visible bounds: $context", body.width > 100f && body.height > 100f)
        assertEquals("Handle is centered against full sheet width", body.center.x, handle.center.x, .5f)
        assertTrue("Handle remains above the sheet content", handle.bottom <= body.top + .5f)
    }

    @Test fun `common visible handle is centered inside start aligned content in both directions`() {
        val direction = mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction.value) {
                Column(Modifier.width(300.dp).background(Color.Black).testTag("handle.container")) {
                    MenuSheetDragHandle()
                }
            }
        }
        for (layout in LayoutDirection.entries) {
            compose.runOnIdle { direction.value = layout }
            val pixels = compose.onNodeWithTag("handle.container").captureToImage().toPixelMap()
            val visible = (0 until pixels.width).filter { x ->
                (0 until pixels.height).any { y -> pixels[x, y].red > .9f }
            }
            assertTrue("Handle must remain visible", visible.isNotEmpty())
            assertEquals("Handle is centered in $layout", (pixels.width - 1) / 2f,
                (visible.first() + visible.last()) / 2f, .5f)
        }
    }

    @Test fun `dropdowns normalize lower corners in both native and shared hosts`() {
        val shared = mutableStateOf(false)
        lateinit var host: GlassMenuHostState
        compose.setContent {
            key(shared.value) {
                Fixture(shared.value, LayoutDirection.Ltr, onHost = { host = it }) {
                    Box {
                        GlassDropdownMenu(
                            expanded = true, onDismissRequest = {}, containerColor = Color.DarkGray,
                            shape = RoundedCornerShape(32.dp),
                        ) {
                            Box(Modifier.size(200.dp, 100.dp).background(Color.White).testTag("dropdown.body"))
                        }
                    }
                    if (shared.value) GlassOverlayHost(host)
                }
            }
        }
        for (useShared in listOf(false, true)) {
            compose.runOnIdle { shared.value = useShared }
            compose.waitForIdle()
            if (useShared) compose.runOnIdle {
                val shape = requireNotNull(host.entry).shape as CornerBasedShape
                assertEquals(0f, shape.bottomStart.toPx(Size(200f, 100f), Density(1f)), 0f)
                assertEquals(0f, shape.bottomEnd.toPx(Size(200f, 100f), Density(1f)), 0f)
            }
            val body = compose.onNodeWithTag("dropdown.body")
            val pixels = if (useShared) {
                val scene = compose.onNodeWithTag("sheet.scene")
                val sceneBounds = scene.fetchSemanticsNode().boundsInRoot
                val bounds = body.fetchSemanticsNode().boundsInRoot
                scene.captureToImage().toPixelMap(
                    startX = (bounds.left - sceneBounds.left).roundToInt(),
                    startY = (bounds.top - sceneBounds.top).roundToInt(),
                    width = bounds.width.roundToInt(), height = bounds.height.roundToInt(),
                )
            } else body.captureToImage().toPixelMap()
            for (x in listOf(1, pixels.width - 2)) {
                assertTrue("Dropdown lower corner shared=$useShared", pixels[x, pixels.height - 2].red > .95f)
            }
        }
    }

    @Composable private fun Handle() {
        Box(Modifier.size(40.dp, 4.dp).background(Color.Magenta).testTag("sheet.handle"))
    }

    @Composable private fun Body() {
        Box(Modifier.fillMaxWidth().height(120.dp).background(Color.White).testTag("sheet.body"))
    }

    @Composable private fun Fixture(
        shared: Boolean,
        direction: LayoutDirection,
        onHost: (GlassMenuHostState) -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        val shape = RoundedCornerShape(32.dp)
        val root = LocalView.current.rootView
        val backdrop = rememberCanvasBackdrop { drawRect(Color.Black) }
        val host = remember(root, backdrop) {
            GlassMenuHostState().apply {
                this.backdrop = backdrop
                dialogs.windowRoot = root
            }
        }
        onHost(host)
        val settings = GlassSettings(film = 0f, blur = 0f, vibrancy = 0f,
            depth = 0f, amount = 0f, rim = 0f)
        CompositionLocalProvider(
            LocalAppearance provides Appearance(DefaultDarkColorPalette, Typography.fallback, shape, shape, shape),
            LocalGlassPreviewSettings provides settings,
            LocalGlassMenuHost provides if (shared) host else null,
            LocalGlassBackdrop provides backdrop,
            LocalLayoutDirection provides direction,
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black).testTag("sheet.scene")) {
                content()
                if (shared) GlassDialogOverlayHost(host)
            }
        }
    }
}
