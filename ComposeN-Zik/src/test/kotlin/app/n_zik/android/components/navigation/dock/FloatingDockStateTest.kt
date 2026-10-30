package app.n_zik.android.components.navigation.dock

import androidx.compose.runtime.mutableStateOf
import app.it.fast4x.rimusic.enums.NavigationBarPosition
import app.it.fast4x.rimusic.enums.PlayerPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FloatingDockStateTest {
    @Test
    fun `floating navigation follows fold clock with no attached mini while classics keep their attachment policy`() {
        val state = FloatingDockState().apply { foldTarget = 1f }
        val clock = mutableStateOf(.6f)
        for (hasBottomMini in listOf(false, true)) {
            for (attached in listOf(false, true)) {
                val dock = FloatingDockEnvironment(state, "home", true, clock,
                    hasBottomMini, attachedMini = attached, floating = true)
                assertEquals(.6f, dock.navigationProgress)
                assertTrue(dock.navigationFolded)
                val classic = dock.copy(floating = false, classic = true)
                assertEquals(if (attached) .6f else 0f, classic.navigationProgress)
                assertEquals(attached, classic.navigationFolded)
            }
        }
    }

    @Test
    fun `navigation folds only when media and mini share its top or bottom edge`() {
        val state = FloatingDockState().apply { foldTarget = 1f }
        val clock = mutableStateOf(1f)
        for (navigation in NavigationBarPosition.entries) {
            for (player in PlayerPosition.entries) {
                for (hasMedia in listOf(false, true)) {
                    val expected = hasMedia && ((navigation == NavigationBarPosition.Top && player == PlayerPosition.Top) ||
                        (navigation.isBottomType && player == PlayerPosition.Bottom))
                    val attached = dockHasAttachedMini(hasMedia, navigation, player)
                    val dock = FloatingDockEnvironment(state, "home", true, clock,
                        hasBottomMini = hasMedia && player == PlayerPosition.Bottom, attachedMini = attached)
                    assertEquals(expected, dock.hasAttachedMini)
                    assertEquals(if (expected) 1f else 0f, dock.navigationProgress)
                    assertEquals(expected, dock.navigationFolded)
                    assertEquals(1f, clock.value)
                }
            }
        }
    }
    @Test
    fun `navigation folds only with a bottom mini without changing the shared mini clock`() {
        val state = FloatingDockState().apply { foldTarget = 1f }
        val clock = mutableStateOf(1f)
        listOf(false, true).forEach { classic ->
            val dock = FloatingDockEnvironment(state, "home", true, clock, true, classic = classic)
            assertEquals(1f, dock.navigationProgress)
            assertTrue(dock.navigationFolded)
            val standalone = dock.copy(hasBottomMini = false)
            assertEquals(0f, standalone.navigationProgress)
            assertFalse(standalone.navigationFolded)
            assertEquals(1f, standalone.progress.value)
            assertEquals(1f, state.foldTarget)
        }
    }

    @Test
    fun `page supplied presence prevents header shortcut flashing before registration`() {
        val state = FloatingDockState()
        val dock = FloatingDockEnvironment(state, "home", true, mutableStateOf(0f), true, pagePresence = true)
        assertEquals(null, state.registeredPresence("home"))
        assertTrue(dock.present)
        assertFalse(dock.copy(enabled = false).present)
        state.register("home", Any(), 1, true)
        assertEquals(false, state.registeredPresence("home"))
        assertFalse(dock.copy(pagePresence = false).present)
    }

    @Test
    fun `only BottomFloating with at least two visible registered tabs is eligible`() {
        NavigationBarPosition.entries.forEach { position ->
            val enabled = position == NavigationBarPosition.BottomFloating
            assertEquals(enabled, floatingDockEligible(enabled, buttonCount = 2, landscapeHidden = false), position.name)
            assertFalse(floatingDockEligible(enabled, buttonCount = 1, landscapeHidden = false), position.name)
            assertFalse(floatingDockEligible(enabled, buttonCount = 5, landscapeHidden = true), position.name)
        }
    }

    @Test
    fun `disposing outgoing owner cannot clear replacement registration for same page`() {
        val dock = FloatingDockState()
        val outgoing = Any()
        val incoming = Any()
        dock.register("home-entry", outgoing, buttonCount = 2, visible = true)
        dock.register("home-entry", incoming, buttonCount = 4, visible = true)
        dock.unregister("home-entry", outgoing)
        assertTrue(dock.hasDock("home-entry"))
        dock.unregister("home-entry", incoming)
        assertFalse(dock.hasDock("home-entry"))
    }

    @Test
    fun `current page alone controls presence while outgoing animated page remains registered`() {
        val dock = FloatingDockState()
        dock.register("home-entry", Any(), buttonCount = 4, visible = true)
        dock.register("detail-entry", Any(), buttonCount = 1, visible = true)
        assertTrue(dock.hasDock("home-entry"))
        assertFalse(dock.hasDock("detail-entry"))
        assertFalse(dock.hasDock("unregistered-entry"))
        assertFalse(dock.hasDock(null))
    }

    @Test
    fun `landscape visibility changes remove presence without resurrecting registered tabs`() {
        val dock = FloatingDockState()
        val owner = Any()
        dock.register("home", owner, buttonCount = 4, visible = true)
        assertTrue(dock.hasDock("home"))
        dock.register("home", owner, buttonCount = 4, visible = false)
        dock.onScroll(-500f, 100f)
        assertFalse(dock.hasDock("home"))
        dock.register("home", owner, buttonCount = 4, visible = true)
        assertTrue(dock.hasDock("home"))
    }

    @Test
    fun `Square scroll threshold triggers complete folds without partial targets`() {
        val dock = FloatingDockState()
        dock.onScroll(-30f, 50f)
        dock.onScroll(-19f, 50f)
        assertEquals(0f, dock.foldTarget)
        dock.onScroll(-1f, 50f)
        assertEquals(1f, dock.foldTarget)
        dock.onScroll(49f, 50f)
        assertEquals(1f, dock.foldTarget)
        dock.onScroll(1f, 50f)
        assertEquals(0f, dock.foldTarget, .0001f)
    }

    @Test
    fun `direction changes discard accumulated travel and small alternating scrolls never jitter`() {
        val dock = FloatingDockState()
        repeat(20) {
            dock.onScroll(-40f, 50f)
            dock.onScroll(2f, 50f)
            assertEquals(0f, dock.foldTarget)
        }
        dock.onScroll(-49f, 50f)
        assertEquals(0f, dock.foldTarget)
        dock.onScroll(-1f, 50f)
        assertEquals(1f, dock.foldTarget)
        dock.onScroll(40f, 50f)
        dock.onScroll(-2f, 50f)
        dock.onScroll(49f, 50f)
        assertEquals(1f, dock.foldTarget)
        dock.onScroll(1f, 50f)
        assertEquals(0f, dock.foldTarget)
    }

    @Test
    fun `Home clears pending downward travel before the next gesture`() {
        val dock = FloatingDockState()
        dock.onScroll(-40f, 50f)
        dock.expand()
        dock.onScroll(-10f, 50f)
        assertEquals(0f, dock.foldTarget)
        dock.onScroll(-40f, 50f)
        assertEquals(1f, dock.foldTarget)
    }

    @Test
    fun `overscroll resets after each transition and Home expands from every folded position`() {
        val dock = FloatingDockState()
        dock.onScroll(-1000f, 100f)
        assertEquals(1f, dock.foldTarget)
        dock.onScroll(1000f, 100f)
        assertEquals(0f, dock.foldTarget)
        listOf(.1f, .5f, 1f).forEach { progress ->
            dock.foldTarget = progress
            dock.expand()
            assertEquals(0f, dock.foldTarget)
        }
    }

    @Test
    fun `invalid scroll samples do not corrupt the fold clock`() {
        val dock = FloatingDockState()
        dock.foldTarget = .4f
        listOf(0f, -10f, Float.NaN, Float.POSITIVE_INFINITY).forEach { range ->
            dock.onScroll(-10f, range)
            assertEquals(.4f, dock.foldTarget)
        }
        dock.onScroll(Float.NaN, 100f)
        dock.onScroll(Float.NEGATIVE_INFINITY, 100f)
        assertEquals(.4f, dock.foldTarget)
    }

    @Test
    fun `header Search moves only for a present current-page BottomFloating dock`() {
        val dock = FloatingDockState()
        val clock = mutableStateOf(0f)
        val owner = Any()
        dock.register("home", owner, buttonCount = 4, visible = true)
        val eligible = FloatingDockEnvironment(dock, "home", enabled = true, progress = clock, hasBottomMini = true)
        assertTrue(eligible.present)
        clock.value = 1f
        assertTrue(eligible.present)
        assertFalse(eligible.copy(pageKey = "detail").present)
        assertFalse(eligible.copy(enabled = false).present)
        dock.register("home", owner, buttonCount = 4, visible = false)
        assertFalse(eligible.present)
    }
}
