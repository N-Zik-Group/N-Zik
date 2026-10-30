package app.n_zik.android.components.glass

import android.app.Application
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.ui.styling.Appearance
import app.it.fast4x.rimusic.ui.styling.DefaultDarkColorPalette
import app.it.fast4x.rimusic.ui.styling.LocalAppearance
import app.it.fast4x.rimusic.ui.styling.Typography
import app.it.fast4x.rimusic.ui.styling.BoundedCornerSize
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.components.menu.GlassDropdownMenu
import app.n_zik.android.components.glass.backdrop.Backdrop
import app.n_zik.android.components.glass.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class GlassOverlayHostTest {
    @Test fun `middle trigger centers its menu while left rail trigger leaves a small gap`() {
        val window = IntSize(360, 640)
        val menu = IntSize(160, 200)
        val middle = placeGlassMenu(Rect(140f, 20f, 188f, 68f), menu, window, 8,
            anchorAligned = true, anchorFraction = .5f)
        assertEquals(IntOffset(84, 68), middle.offset)
        val left = placeGlassMenu(Rect(4f, 20f, 48f, 68f), menu, window, 8,
            anchorAligned = true, anchorFraction = 0f, anchorOffsetX = 48)
        assertEquals(IntOffset(52, 68), left.offset)
    }
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun TestTheme(content: @Composable () -> Unit) {
        val shape = RoundedCornerShape(16.dp)
        CompositionLocalProvider(LocalAppearance provides Appearance(
            colorPalette = DefaultDarkColorPalette, typography = Typography.fallback,
            thumbnailShape = shape, uiRoundnessShape = shape, artistThumbnailShape = shape,
        )) {
            MaterialTheme(content = content)
        }
    }

    @Test
    @Config(sdk = [33])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `native dropdown shares anchored motion and keeps opaque theme when glass is disabled`() {
        assertNativeDropdown(globalGlass = false)
    }

    @Test
    @Config(sdk = [33])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `disabled dropdown area keeps native theme even while global glass is enabled`() {
        assertNativeDropdown(globalGlass = true)
    }

    private fun assertNativeDropdown(globalGlass: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val preferences = context.preferences
        val glassPreferences = context.getSharedPreferences("nzik_square_glass_v2", android.content.Context.MODE_PRIVATE)
        val previousEnabled = preferences.getBoolean(glassEnabledKey, false)
        val hadDropdowns = glassPreferences.contains(glassDropdownsKey)
        val previousDropdowns = glassPreferences.getBoolean(glassDropdownsKey, true)
        val previousMode = glassPreferences.getString(GlassArea.Dropdowns.preferenceKey, null)
        preferences.edit().putBoolean(glassEnabledKey, globalGlass).commit()
        glassPreferences.edit().putBoolean(glassDropdownsKey, false)
            .putString(GlassArea.Dropdowns.preferenceKey, GlassAreaMode.Disabled.name).commit()
        try {
            val host = GlassMenuHostState()
            val trigger = GlassMenuTriggerState()
            val expanded = mutableStateOf(false)
            val themeColor = Color(0xFF71498B)
            var actions = 0
            compose.setContent {
                TestTheme {
                    CompositionLocalProvider(LocalGlassMenuHost provides host) {
                        Box(Modifier.requiredSize(320.dp, 400.dp)) {
                            Box(Modifier.size(48.dp).glassMenuTrigger(trigger)) {
                                GlassDropdownMenu(expanded.value, { expanded.value = false }, themeColor, RectangleShape,
                                    trigger = trigger) {
                                    Box(Modifier.size(120.dp, 64.dp).testTag("native-menu-body").clickable {
                                        actions++
                                        expanded.value = false
                                    })
                                }
                            }
                            GlassOverlayHost(host)
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.runOnIdle {
                expanded.value = true
                Snapshot.sendApplyNotifications()
            }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(GLASS_MENU_OPEN_DURATION_MS / 2L)
            compose.waitForIdle()
            compose.runOnIdle {
                val entry = requireNotNull(host.entry)
                assertFalse(entry.settings.usesGlassMaterial(GlassArea.Dropdowns))
                assertEquals(themeColor, entry.containerColor)
                assertEquals(trigger.bounds, entry.anchor)
                assertSame(trigger, entry.trigger)
                val moving = requireNotNull(trigger.menuBounds)
                assertTrue("native pane is expanding from its trigger", moving.width > trigger.bounds.width)
            }
            compose.mainClock.advanceTimeBy(GLASS_MENU_OPEN_DURATION_MS.toLong())
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("native-menu-body").captureToImage().toPixelMap()
            val actual = pixels[pixels.width / 2, pixels.height / 2]
            assertEquals(themeColor.red, actual.red, .01f)
            assertEquals(themeColor.green, actual.green, .01f)
            assertEquals(themeColor.blue, actual.blue, .01f)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("native-menu-body").performClick()
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(1, actions)
                assertNull(host.entry)
                assertFalse(trigger.isTransferred)
            }
        } finally {
            compose.mainClock.autoAdvance = true
            preferences.edit().putBoolean(glassEnabledKey, previousEnabled).commit()
            glassPreferences.edit().apply {
                if (hadDropdowns) putBoolean(glassDropdownsKey, previousDropdowns) else remove(glassDropdownsKey)
                if (previousMode != null) putString(GlassArea.Dropdowns.preferenceKey, previousMode)
                else remove(GlassArea.Dropdowns.preferenceKey)
            }.commit()
        }
    }

    @Test
    @Config(sdk = [33])
    fun `overlay menu is constrained above dispatched IME insets`() {
        val host = GlassMenuHostState()
        var view: android.view.View? = null
        var viewport = IntSize.Zero
        compose.setContent {
            TestTheme {
                view = androidx.compose.ui.platform.LocalView.current
                Box(Modifier.onSizeChanged { viewport = it }) {
                    GlassOverlayHost(host)
                }
            }
        }
        compose.runOnIdle {
            host.show(GlassMenuEntry(
                id = "ime-menu", anchor = Rect(20f, 620f, 80f, 680f),
                settings = GlassSettings(enabled = true), onDismiss = {}, shape = RectangleShape,
                content = { Box(Modifier.size(120.dp, 300.dp).testTag("ime-options")) },
            ))
        }
        compose.waitForIdle()
        val keyboardHeight = (viewport.height * .6f).toInt()
        val keyboardTop = viewport.height - keyboardHeight
        compose.runOnIdle {
            androidx.core.view.ViewCompat.dispatchApplyWindowInsets(requireNotNull(view),
                androidx.core.view.WindowInsetsCompat.Builder()
                    .setInsets(androidx.core.view.WindowInsetsCompat.Type.ime(),
                        androidx.core.graphics.Insets.of(0, 0, 0, keyboardHeight))
                    .setVisible(androidx.core.view.WindowInsetsCompat.Type.ime(), true).build())
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("ime-options").fetchSemanticsNode().boundsInRoot
        assertTrue("menu options remain above keyboard: $bounds / $keyboardTop", bounds.bottom <= keyboardTop + 1f)
    }

    @Test
    fun `real dropdown preserves caller locals while host supplies its sampled backdrop`() {
        val preferences = ApplicationProvider.getApplicationContext<Application>().preferences
        val previousEnabled = preferences.getBoolean(glassEnabledKey, false)
        preferences.edit().putBoolean(glassEnabledKey, true).commit()
        val ownerValue = androidx.compose.runtime.staticCompositionLocalOf { "host" }
        var capturedValue = ""
        var capturedBackdrop: Backdrop? = null
        var callerBackdrop: Backdrop? = null
        try {
            val host = GlassMenuHostState()
            compose.setContent {
                TestTheme {
                    val page = rememberLayerBackdrop()
                    callerBackdrop = page
                    CompositionLocalProvider(LocalGlassMenuHost provides host) {
                        Box {
                            CompositionLocalProvider(ownerValue provides "owner", LocalGlassBackdrop provides page) {
                                GlassDropdownMenu(true, {}, Color.Transparent, RectangleShape) {
                                    capturedValue = ownerValue.current
                                    capturedBackdrop = LocalGlassBackdrop.current
                                    Text("Owner content")
                                }
                            }
                            GlassOverlayHost(host)
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.onNodeWithText("Owner content").assertIsDisplayed()
            compose.runOnIdle {
                assertEquals("owner", capturedValue)
                assertTrue("host dimmed source must override captured owner source", capturedBackdrop !== callerBackdrop)
                assertTrue(capturedBackdrop != null)
            }
        } finally {
            preferences.edit().putBoolean(glassEnabledKey, previousEnabled).commit()
        }
    }

    @Test
    fun `menu follows Material start alignment and opens above when below cannot fit`() {
        val below = placeGlassMenu(Rect(280f, 20f, 328f, 68f), IntSize(244, 200), IntSize(360, 640), 48)
        assertEquals(84, below.offset.x)
        assertEquals(68, below.offset.y)
        assertEquals(0f, below.pivot.pivotFractionY, 0f)
        val above = placeGlassMenu(Rect(280f, 560f, 328f, 608f), IntSize(244, 200), IntSize(360, 640), 48)
        assertEquals(360, above.offset.y)
        assertEquals(1f, above.pivot.pivotFractionY, 0f)
        val left = placeGlassMenu(Rect(20f, 20f, 68f, 68f), IntSize(112, 64), IntSize(360, 640), 48)
        assertEquals(20, left.offset.x)
        val rtl = placeGlassMenu(Rect(160f, 20f, 208f, 68f), IntSize(112, 64), IntSize(360, 640), 48, LayoutDirection.Rtl)
        assertEquals(96, rtl.offset.x)
    }

    @Test
    fun `placement margin does not shrink or clip a tall menu`() {
        val placement = placeGlassMenu(Rect(-20f, -10f, 28f, 38f), IntSize(320, 480), IntSize(320, 480), 48)
        assertEquals(0, placement.offset.x)
        assertEquals(0, placement.offset.y)
        val almostFull = placeGlassMenu(Rect(100f, 10f, 148f, 58f), IntSize(112, 420), IntSize(320, 480), 48)
        assertEquals(30, almostFull.offset.y)
        assertTrue(placement.pivot.pivotFractionX in 0f..1f)
        assertTrue(placement.pivot.pivotFractionY in 0f..1f)
    }

    @Test
    fun `shared surface opens below the trigger with viewport fallback`() {
        val anchor = Rect(280f, 20f, 328f, 68f)
        val shared = placeGlassMenu(anchor, IntSize(153, 200), IntSize(360, 640), 48, anchorAligned = true)
        assertEquals(175, shared.offset.x)
        assertEquals(68, shared.offset.y)
        val constrained = placeGlassMenu(anchor, IntSize(153, 640), IntSize(360, 640), 48, anchorAligned = true)
        assertEquals(0, constrained.offset.y)
    }

    @Test
    fun `surface deforms between trigger and menu with coordinated refraction`() {
        val anchor = Rect(280f, 20f, 328f, 68f)
        val target = Rect(176f, 68f, 328f, 500f)
        val closed = glassMenuFrame(anchor, target, 0f)
        val halfway = glassMenuFrame(anchor, target, .5f)
        val open = glassMenuFrame(anchor, target, 1f)
        assertEquals(anchor, closed.bounds)
        assertEquals(target, open.bounds)
        val widthFraction = (halfway.bounds.width - anchor.width) / (target.width - anchor.width)
        val heightFraction = (halfway.bounds.height - anchor.height) / (target.height - anchor.height)
        assertTrue("width leads height so the bubble does not become a rigid column", widthFraction > heightFraction)
        assertTrue(halfway.refractionPulse > closed.refractionPulse)
        assertEquals(0f, open.refractionPulse, .0001f)
        val settling = glassMenuFrame(anchor, target, 1.02f)
        assertTrue(settling.bounds.width > open.bounds.width)
        assertTrue(settling.bounds.height > open.bounds.height)
        assertEquals(0f, glassMenuContentAlpha(.45f), 0f)
        assertEquals(0f, glassMenuSourceAlpha(.5f), 0f)
        assertEquals(1f, glassMenuContentAlpha(1f), 0f)
        assertEquals(1f, glassMenuSourceAlpha(0f), 0f)
    }

    @Test
    fun `closing pane returns to relocated sidebar trigger without moving its open target`() {
        val trigger = GlassMenuTriggerState()
        val initial = Rect(280f, 20f, 328f, 68f)
        val relocated = Rect(280f, 100f, 328f, 148f)
        trigger.bounds = initial
        val entry = GlassMenuEntry(Any(), initial, GlassSettings(), {}, {}, trigger = trigger, shape = RectangleShape)
        val menuSize = IntSize(153, 300)
        val window = IntSize(360, 640)
        val placement = placeGlassMenu(entry.anchor, menuSize, window, 48, anchorAligned = true)
        val target = Rect(placement.offset.x.toFloat(), placement.offset.y.toFloat(),
            (placement.offset.x + menuSize.width).toFloat(), (placement.offset.y + menuSize.height).toFloat())
        trigger.bounds = relocated
        assertEquals(initial, glassMenuReturnAnchor(entry))
        val closingAnchor = glassMenuReturnAnchor(entry.copy(visible = false))
        assertEquals(relocated, closingAnchor)
        assertEquals(target, glassMenuFrame(closingAnchor, target, 1f, window).bounds)
        assertEquals(relocated, glassMenuFrame(closingAnchor, target, 0f, window).bounds)
        assertEquals(placement, placeGlassMenu(entry.anchor, menuSize, window, 48, anchorAligned = true))
        trigger.bounds = Rect.Zero
        assertEquals(initial, glassMenuReturnAnchor(entry.copy(visible = false)))
    }

    @Test
    fun `spatial arc shifts aligned corners independently of size and stays inside viewport`() {
        val anchor = Rect(280f, 20f, 328f, 68f)
        val target = Rect(175f, 20f, 328f, 400f)
        val viewport = IntSize(360, 640)
        val middle = glassMenuFrame(anchor, target, .5f, viewport).bounds
        assertTrue("the right edge sways left instead of remaining pinned", middle.right < anchor.right)
        assertTrue("the top edge follows the downwards arc", middle.top > anchor.top)
        assertTrue(middle.width > (anchor.width + target.width) / 2)
        assertTrue(middle.height < (anchor.height + target.height) / 2)
        assertEquals(anchor, glassMenuFrame(anchor, target, 0f, viewport).bounds)
        assertEquals(target, glassMenuFrame(anchor, target, 1f, viewport).bounds)
        val edgeTarget = Rect(0f, 0f, 360f, 640f)
        listOf(-.02f, 0f, .1f, .5f, .9f, 1f, 1.02f).forEach { progress ->
            val bounds = glassMenuFrame(anchor, edgeTarget, progress, viewport).bounds
            assertTrue(bounds.left >= 0f && bounds.top >= 0f)
            assertTrue(bounds.right <= viewport.width && bounds.bottom <= viewport.height)
        }
    }

    @Test
    fun `motion eases into the bubble and settles before handing the source back`() {
        listOf(GlassMenuOpenEasing, GlassMenuCloseEasing).forEach { easing ->
            assertEquals(0f, easing.transform(0f), 0f)
            assertEquals(1f, easing.transform(1f), 0f)
            val start = easing.transform(.1f) - easing.transform(0f)
            val expansion = easing.transform(.4f) - easing.transform(.3f)
            val settle = kotlin.math.abs(easing.transform(1f) - easing.transform(.9f))
            assertTrue("the initial bubble accelerates gradually", start < expansion)
            assertTrue("arrival has a soft settling tail", settle < expansion / 4f)
        }
        assertEquals(0f, glassMenuSourceAlpha(.2f), 0f)
        assertEquals(0f, glassMenuContentAlpha(.2f), 0f)
        assertTrue(glassMenuSourceAlpha(.1f) > 0f)
    }

    @Test
    fun `source remains visible until the measured overlay can take ownership of its pixels`() {
        val trigger = GlassMenuTriggerState()
        val host = GlassMenuHostState()
        val id = Any()
        trigger.bounds = Rect(280f, 80f, 328f, 128f)
        host.show(GlassMenuEntry(id, trigger.bounds, GlassSettings(), {}, {}, trigger = trigger, shape = RectangleShape))
        assertFalse("claiming an unmeasured overlay must not blank the source", trigger.isTransferred)
        trigger.updateMenuBounds(id, trigger.bounds)
        assertTrue(trigger.isTransferred)
        host.hide(id)
        assertTrue("closing keeps one owner until the bubble has returned", trigger.isTransferred)
        host.clear(id)
        assertFalse(trigger.isTransferred)
        assertNull(trigger.menuBounds)
    }

    @Test
    fun `neighbor pull follows only its owner and survives close reopen until released`() {
        val host = GlassMenuHostState()
        val trigger = GlassMenuTriggerState()
        val id = Any()
        val neighbor = Rect(220f, 20f, 268f, 68f)
        trigger.bounds = Rect(280f, 20f, 328f, 68f)
        val entry = GlassMenuEntry(id, trigger.bounds, GlassSettings(), {}, {}, trigger = trigger, shape = RectangleShape)
        host.show(entry)
        val frame = glassMenuFrame(trigger.bounds, Rect(175f, 20f, 328f, 400f), .5f).bounds
        trigger.updateMenuBounds(id, frame)
        val displacement = glassMenuNeighborOffset(neighbor, trigger.bounds, trigger.menuBounds)
        assertTrue(displacement < 0)
        host.hide(id)
        host.show(entry)
        trigger.updateMenuBounds(Any(), Rect.Zero)
        assertEquals(displacement, glassMenuNeighborOffset(neighbor, trigger.bounds, trigger.menuBounds))
        host.remove(id)
        trigger.updateMenuBounds(id, frame)
        assertEquals(0, glassMenuNeighborOffset(neighbor, trigger.bounds, trigger.menuBounds))
        assertEquals(-220, glassMenuNeighborOffset(neighbor, trigger.bounds, Rect(-100f, 20f, 328f, 400f)))
    }

    @Test
    fun `neighbor search hit target and semantic bounds move with the pulled rendering`() {
        val trigger = GlassMenuTriggerState()
        val id = Any()
        var clicks = 0
        compose.setContent {
            Box(Modifier.requiredSize(320.dp, 180.dp).testTag("neighbor-root")) {
                Box(
                    Modifier.absoluteOffset(x = 180.dp, y = 20.dp)
                        .glassMenuNeighbor(trigger).size(48.dp)
                        .testTag("neighbor-search").clickable { clicks++ },
                )
            }
        }
        val root = compose.onNodeWithTag("neighbor-root").fetchSemanticsNode().boundsInRoot
        val original = compose.onNodeWithTag("neighbor-search").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            trigger.bounds = Rect(original.right + 8f, original.top, original.right + 8f + original.width, original.bottom)
            trigger.claim(id)
            trigger.updateMenuBounds(id, Rect(original.left - 16f, original.top, trigger.bounds.right, original.bottom + 100f))
        }
        compose.waitForIdle()
        val shifted = compose.onNodeWithTag("neighbor-search").fetchSemanticsNode().boundsInRoot
        assertTrue(shifted.right < original.left)
        compose.onNodeWithTag("neighbor-root").performTouchInput { click(original.center - root.topLeft) }
        compose.runOnIdle { assertEquals("the old location must not remain clickable", 0, clicks) }
        compose.onNodeWithTag("neighbor-root").performTouchInput { click(shifted.center - root.topLeft) }
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.runOnIdle { trigger.release(id) }
        compose.waitForIdle()
        assertEquals(original, compose.onNodeWithTag("neighbor-search").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("neighbor-root").performTouchInput { click(original.center - root.topLeft) }
        compose.runOnIdle { assertEquals(2, clicks) }
    }

    @Test
    fun `menu morph follows zero small and large user corners with native dimension limits`() {
        val density = Density(2f)
        val sourceSize = Size(96f, 96f)
        val targetSize = Size(400f, 600f)
        listOf(0f, 8f, 96f).forEach { radiusDp ->
            val source = RoundedCornerShape(BoundedCornerSize(radiusDp.dp, .5f))
            val target = RoundedCornerShape(BoundedCornerSize(radiusDp.dp, .4f))
            assertSame(source, glassMenuShape(source, target, sourceSize, targetSize, 0f, density))
            assertSame(target, glassMenuShape(source, target, sourceSize, targetSize, 1f, density))
            val middle = glassMenuShape(source, target, sourceSize, targetSize, .5f, density) as RoundedCornerShape
            val expectedSource = minOf(radiusDp * 2f, 48f)
            val expectedTarget = minOf(radiusDp * 2f, 160f)
            val middleSize = Size(248f, 348f)
            listOf(middle.topStart, middle.topEnd, middle.bottomEnd, middle.bottomStart).forEach { corner ->
                assertEquals((expectedSource + expectedTarget) / 2, corner.toPx(middleSize, density), .001f)
            }
        }
        val source = RoundedCornerShape(topStart = 4.dp, topEnd = 8.dp, bottomEnd = 12.dp, bottomStart = 16.dp)
        val target = RoundedCornerShape(topStart = 20.dp, topEnd = 24.dp, bottomEnd = 28.dp, bottomStart = 32.dp)
        val middle = glassMenuShape(source, target, sourceSize, targetSize, .5f, Density(1f)) as RoundedCornerShape
        listOf(middle.topStart, middle.topEnd, middle.bottomEnd, middle.bottomStart).forEachIndexed { index, corner ->
            assertEquals(12f + index * 4f, corner.toPx(Size(248f, 348f), Density(1f)), .001f)
        }
    }

    @Test
    fun `dropdown keeps exact themed container color and alpha with native unspecified default`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val preferences = context.preferences
        val previousEnabled = preferences.getBoolean(glassEnabledKey, false)
        preferences.edit().putBoolean(glassEnabledKey, true).commit()
        try {
            val host = GlassMenuHostState()
            val themedColor = Color(0xFF71498B).copy(alpha = .9f)
            val requested = mutableStateOf(themedColor)
            var nativeDefault = Color.Unspecified
            compose.setContent {
                TestTheme {
                    nativeDefault = MenuDefaults.containerColor
                    CompositionLocalProvider(LocalGlassMenuHost provides host) {
                        Box(Modifier.size(48.dp)) {
                            GlassDropdownMenu(
                                expanded = true,
                                onDismissRequest = {},
                                containerColor = requested.value,
                                shape = RectangleShape,
                            ) { }
                        }
                    }
                }
            }
            compose.runOnIdle {
                assertEquals(themedColor, requireNotNull(host.entry).containerColor)
                requested.value = Color.Transparent
            }
            compose.runOnIdle {
                assertEquals(Color.Transparent, requireNotNull(host.entry).containerColor)
                requested.value = Color.Unspecified
            }
            compose.runOnIdle { assertEquals(nativeDefault, requireNotNull(host.entry).containerColor) }
        } finally {
            preferences.edit().putBoolean(glassEnabledKey, previousEnabled).commit()
        }
    }

    @Test
    fun `exit completion cannot clear a newly opened menu or a later exit of the same menu`() {
        val host = GlassMenuHostState()
        val old = Any()
        val newer = Any()
        fun entry(id: Any) = GlassMenuEntry(id, Rect.Zero, GlassSettings(), {}, {}, shape = RectangleShape)
        host.show(entry(old))
        host.hide(old)
        val oldGeneration = host.exitGeneration
        assertFalse(requireNotNull(host.entry).visible)
        host.show(entry(newer))
        host.clear(old, oldGeneration)
        assertEquals(newer, host.entry?.id)
        host.hide(newer)
        host.clear(newer)
        assertNull(host.entry)
        host.show(entry(old))
        host.hide(old)
        host.show(entry(old))
        host.hide(old)
        host.clear(old, oldGeneration)
        assertFalse(requireNotNull(host.entry).visible)
        host.clear(old)
        assertNull(host.entry)
    }

    @Test
    fun `visible nested dropdown source survives while replacement uses its retained underlying scene`() {
        val host = GlassMenuHostState()
        val id = Any()
        lateinit var scene: Backdrop
        lateinit var completedMenu: Backdrop
        compose.setContent {
            scene = rememberLayerBackdrop()
            completedMenu = rememberLayerBackdrop()
        }
        compose.runOnIdle {
            val entry = GlassMenuEntry(id, Rect.Zero, GlassSettings(), {}, {}, shape = RectangleShape, backdrop = scene)
            host.show(entry)
            val source = GlassDropdownBackdropOwner(host, id, scene)
            assertSame(completedMenu, source.resolve(completedMenu))
            host.hide(id)
            assertSame("a successor never samples the animated dropdown being removed", scene, source.resolve(completedMenu))
            host.clear(id)
            assertSame("disposing the dropdown cannot invalidate the successor's source", scene, source.resolve(completedMenu))
            host.show(entry)
            assertSame(completedMenu, source.resolve(completedMenu))
        }
    }

    @Test
    fun `pane matches native intrinsic bounds keeps content size during morph and scrolls to last action`() {
        val native = mutableStateOf(true)
        val progress = mutableStateOf(.35f)
        var nativeSize = IntSize.Zero
        var finalSize = IntSize.Zero
        var animatedSize = IntSize.Zero
        var lastAction = false
        compose.setContent {
            TestTheme {
                Box(Modifier.requiredSize(320.dp, 200.dp)) {
                    if (native.value) {
                        DropdownMenu(
                            expanded = true,
                            onDismissRequest = {},
                            modifier = Modifier.heightIn(max = 200.dp).onSizeChanged { nativeSize = it },
                        ) {
                            repeat(12) { index ->
                                DropdownMenuItem(text = { Text("Item $index") }, onClick = {})
                            }
                        }
                    } else {
                        GlassMenuPane(
                            progress = progress.value,
                            anchor = Rect(0f, 0f, 48f, 48f),
                            modifier = Modifier.onSizeChanged { animatedSize = it },
                            onFinalSize = { finalSize = it },
                        ) {
                            repeat(12) { index ->
                                DropdownMenuItem(text = { Text("Item $index") }, onClick = { if (index == 11) lastAction = true })
                            }
                        }
                    }
                }
            }
        }
        compose.runOnIdle {
            assertTrue(nativeSize.width > 0)
            native.value = false
        }
        compose.runOnIdle {
            assertEquals(nativeSize, finalSize)
            assertTrue(animatedSize.height < finalSize.height)
            progress.value = 1f
        }
        compose.runOnIdle {
            assertEquals(nativeSize, finalSize)
            assertEquals(finalSize, animatedSize)
        }
        compose.onNodeWithText("Item 11").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(lastAction) }
    }

    @Test
    fun `returning source icon stays centered and moves with the shrinking bubble`() {
        val progress = mutableStateOf(.16f)
        var paneBounds = Rect.Zero
        var iconBounds = Rect.Zero
        compose.setContent {
            TestTheme {
                Box(Modifier.requiredSize(320.dp, 400.dp)) {
                    GlassMenuPane(
                        progress = progress.value,
                        anchor = Rect(0f, 0f, 48f, 48f),
                        modifier = Modifier.onGloballyPositioned { paneBounds = it.boundsInRoot() },
                        triggerContent = {
                            Box(Modifier.size(8.dp).onGloballyPositioned { iconBounds = it.boundsInRoot() })
                        },
                    ) {
                        repeat(3) { DropdownMenuItem(text = { Text("Action $it") }, onClick = {}) }
                    }
                }
            }
        }
        var expandedIconBounds = Rect.Zero
        compose.runOnIdle {
            assertTrue(glassMenuSourceAlpha(progress.value) > 0f)
            assertEquals(paneBounds.center.x, iconBounds.center.x, 1f)
            assertEquals(paneBounds.center.y, iconBounds.center.y, 1f)
            expandedIconBounds = iconBounds
            progress.value = .05f
        }
        compose.runOnIdle {
            assertEquals(paneBounds.center.x, iconBounds.center.x, 1f)
            assertEquals(paneBounds.center.y, iconBounds.center.y, 1f)
            assertTrue(iconBounds.center.x < expandedIconBounds.center.x)
            assertTrue(iconBounds.center.y < expandedIconBounds.center.y)
        }
    }

    @Test
    fun `source glyph starts at own button and follows the same pane arc back home`() {
        val progress = mutableStateOf(0f)
        var iconBounds = Rect.Zero
        val anchor = Rect(200f, 40f, 248f, 88f)
        val target = Rect(100f, 88f, 248f, 280f)
        compose.setContent {
            TestTheme {
                Box(Modifier.requiredSize(320.dp, 400.dp)) {
                    val frame = glassMenuFrame(anchor, target, progress.value).bounds
                    GlassMenuSource(progress.value, anchor, frame) {
                        Box(Modifier.size(8.dp).onGloballyPositioned { iconBounds = it.boundsInRoot() })
                    }
                }
            }
        }
        var initialIcon = Rect.Zero
        compose.runOnIdle {
            assertEquals(anchor.center.x, iconBounds.center.x, 1f)
            assertEquals(anchor.center.y, iconBounds.center.y, 1f)
            initialIcon = iconBounds
            progress.value = .16f
        }
        compose.runOnIdle {
            val frame = glassMenuFrame(anchor, target, .16f).bounds
            assertEquals(frame.center.x, iconBounds.center.x, 1f)
            assertEquals(frame.center.y, iconBounds.center.y, 1f)
            assertTrue(iconBounds.center.x < initialIcon.center.x)
            assertTrue(iconBounds.center.y > initialIcon.center.y)
            progress.value = 0f
        }
        compose.runOnIdle {
            assertEquals(initialIcon, iconBounds)
        }
    }

    @Test
    fun `menu starts at source bounds even when safe viewport excludes its edges`() {
        val anchor = Rect(-8f, -12f, 40f, 36f)
        val target = Rect(0f, 36f, 148f, 228f)
        assertEquals(anchor, glassMenuFrame(anchor, target, 0f, IntSize(320, 400)).bounds)
    }

    @Test
    fun `tall transferred menu scrolls below its own button instead of moving above it`() {
        val host = GlassMenuHostState()
        val trigger = GlassMenuTriggerState()
        var viewport = IntSize.Zero
        compose.setContent {
            TestTheme {
                Box(Modifier.requiredSize(320.dp, 400.dp).onSizeChanged { viewport = it }) {
                    GlassOverlayHost(host)
                }
            }
        }
        compose.runOnIdle {
            trigger.bounds = Rect(200f, 40f, 248f, 88f)
            host.show(GlassMenuEntry(
                id = "tall-transferred", anchor = trigger.bounds, trigger = trigger,
                settings = GlassSettings(), onDismiss = {}, shape = RectangleShape,
                modifier = Modifier.testTag("below-options"),
                content = { repeat(30) { DropdownMenuItem(text = { Text("Option $it") }, onClick = {}) } },
            ))
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag("below-options").fetchSemanticsNode().boundsInRoot
        assertEquals(trigger.bounds.bottom, bounds.top, 1f)
        assertTrue("the menu scrolls within the remaining viewport", bounds.bottom <= viewport.height + 1f)
    }

    @Test
    fun `animated close remains mounted and reopening cancels its removal`() {
        val host = GlassMenuHostState()
        val source = GlassMenuTriggerState()
        val entry = GlassMenuEntry(Any(), Rect(20f, 20f, 68f, 68f), GlassSettings(), {}, {
            DropdownMenuItem(text = { Text("Action") }, onClick = {})
        }, trigger = source, shape = RectangleShape)
        host.show(entry)
        compose.setContent {
            TestTheme { GlassOverlayHost(host) }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        fun applyStateChange(change: () -> Unit) {
            compose.runOnIdle {
                change()
                Snapshot.sendApplyNotifications()
            }
            // Recompose and launch the animation before advancing its own clock.
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        applyStateChange { host.hide(entry.id) }
        compose.mainClock.advanceTimeBy(32)
        compose.waitForIdle()
        compose.runOnIdle {
            assertFalse(requireNotNull(host.entry).visible)
            assertEquals(entry.id, source.owner)
        }
        applyStateChange { host.show(entry) }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(GLASS_MENU_OPEN_DURATION_MS.toLong())
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(requireNotNull(host.entry).visible)
            assertEquals(entry.id, source.owner)
        }
        applyStateChange { host.hide(entry.id) }
        // Animatable's first frame establishes time zero before the finite close/settle duration.
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(GLASS_MENU_CLOSE_DURATION_MS.toLong())
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(host.entry)
            assertNull(source.owner)
        }
    }

    @Test
    fun `disabled motion removes a dismissed pane immediately`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            val host = GlassMenuHostState()
            val id = Any()
            host.show(GlassMenuEntry(id, Rect(20f, 20f, 68f, 68f), GlassSettings(), {}, {
                DropdownMenuItem(text = { Text("Action") }, onClick = {})
            }, shape = RectangleShape))
            compose.setContent { TestTheme { GlassOverlayHost(host) } }
            compose.runOnIdle { host.hide(id) }
            compose.runOnIdle { assertNull(host.entry) }
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `only the owning source transfers to the menu and closing restores its unchanged hit target`() {
        val host = GlassMenuHostState()
        val source = GlassMenuTriggerState()
        val other = GlassMenuTriggerState()
        val id = Any()
        var clicks = 0
        val sourceShape = RoundedCornerShape(7.dp)
        source.shape = sourceShape
        source.content = { Text("Transferred source") }
        compose.setContent {
            TestTheme {
                Box {
                    Column {
                        Box(Modifier.size(48.dp).glassMenuTrigger(source).clickable { clicks++ }) { Text("Source") }
                        Box(Modifier.size(48.dp).glassMenuTrigger(other)) { Text("Other") }
                    }
                    GlassOverlayHost(host)
                }
            }
        }
        var initialBounds = Rect.Zero
        compose.runOnIdle {
            initialBounds = source.bounds
            host.show(GlassMenuEntry(id, source.bounds, GlassSettings(), {}, {
                DropdownMenuItem(text = { Text("Shared action") }, onClick = {})
            }, trigger = source, shape = RectangleShape))
            assertEquals(id, source.owner)
            assertNull(other.owner)
        }
        compose.waitForIdle()
        compose.onNodeWithText("Source").assertDoesNotExist()
        compose.onNodeWithText("Other").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(initialBounds, source.bounds)
            host.hide(id)
            assertEquals("the surface stays transferred throughout the exit", id, source.owner)
            host.clear(Any())
            assertEquals(id, source.owner)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertNull(host.entry)
            assertNull(source.owner)
            assertEquals(initialBounds, source.bounds)
            assertSame(sourceShape, source.shape)
        }
        compose.onNodeWithText("Source").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun `Square sampled image saturation and resolution mappings are exact`() {
        assertEquals(1.6f, glassSaturation(1.2f), 0f)
        assertEquals(1f, glassSaturation(0f), 0f)
        assertEquals(2f, glassSaturation(2f), 0f)
        assertEquals(.825f, glassResolutionScale(2f), .0001f)
        assertEquals(.3f, glassResolutionScale(24f), .0001f)
        assertEquals(1f, glassResolutionScale(0f), 0f)
    }
}
