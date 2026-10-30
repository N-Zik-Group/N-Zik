package app.n_zik.android.components.ui.screens.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeTopMiniPlayerAnchorTest {
    @Test fun `side mini keeps its gap below the moving toolbar in both orientations`() {
        var visible = 180.dp
        val state = HomeTopMiniPlayerAnchorState(
            mutableStateOf<(() -> Dp)?>({ 24.dp }), mutableStateOf(0.dp),
        )
        state.attach(Any()) { visible }
        val inset = state.miniTopInsetProvider(panelGap = 8.dp)
        assertEquals(212.dp, inset())
        visible = 90.dp
        assertEquals(122.dp, inset())
        visible = 0.dp
        assertEquals(32.dp, inset())
    }
    @Test fun `late header measurement changes placement key while scroll keeps it stable`() {
        var height = 0.dp
        var offset = 0.dp
        val state = HomeTopMiniPlayerAnchorState(
            mutableStateOf<(() -> Dp)?>({ 96.dp }), mutableStateOf(8.dp),
        )
        state.attach(Any(), measuredHeight = { height }) { height + offset }
        val unmeasured = state.placementKey()
        height = 200.dp
        val measured = state.placementKey()
        assertNotEquals(unmeasured, measured)
        assertEquals(296.dp, state.miniTopInset())
        offset = (-75).dp
        assertEquals(measured, state.placementKey())
        assertEquals(221.dp, state.miniTopInset())
        state.attach(Any(), measuredHeight = { height }) { height + offset }
        assertNotEquals(measured, state.placementKey())
    }

    @Test fun `landscape hidden bars place top mini before home toolbar`() {
        assertTrue(miniPrecedesHomeHeader(retainTopNavigation = false, landscapeBarsHidden = true))
        assertTrue(miniPrecedesHomeHeader(retainTopNavigation = true, landscapeBarsHidden = false))
        assertFalse(miniPrecedesHomeHeader(retainTopNavigation = false, landscapeBarsHidden = false))

        val state = HomeTopMiniPlayerAnchorState(
            mutableStateOf<(() -> Dp)?>({ 24.dp }), mutableStateOf(0.dp), mutableStateOf(64.dp),
        )
        assertEquals(88.dp, state.headerTopInset())
    }

    @Test fun `top navigation keeps mini before opaque home toolbar`() {
        val miniHeight = mutableStateOf<Dp?>(64.dp)
        val state = HomeTopMiniPlayerAnchorState(
            mutableStateOf<(() -> Dp)?>({ 160.dp }), mutableStateOf(8.dp), miniHeight,
        )
        state.attach(Any()) { 180.dp }
        assertEquals(232.dp, state.headerTopInset())
        miniHeight.value = null
        assertEquals(152.dp, state.headerTopInset())
    }

    @Test fun `mini follows the visible panel then returns to the existing dock anchor`() {
        var base = 96.dp
        var offset = 0f
        val state = HomeTopMiniPlayerAnchorState(
            mutableStateOf<(() -> Dp)?>({ base }), mutableStateOf(8.dp),
        )
        state.attach(Any()) { visibleHomeHeaderHeightPx(200, offset).dp }
        assertEquals(88.dp, state.headerTopInset())
        assertEquals(296.dp, state.miniTopInset())
        offset = -75f
        assertEquals(221.dp, state.miniTopInset())
        base = 32.dp
        assertEquals(157.dp, state.miniTopInset())
        offset = -200f
        assertEquals(32.dp, state.miniTopInset())
        offset = -240f
        assertEquals(32.dp, state.miniTopInset())
    }

    @Test fun `bottom player or absent media leaves header and mini routing untouched`() {
        val state = HomeTopMiniPlayerAnchorState(mutableStateOf(null), mutableStateOf(8.dp))
        state.attach(Any()) { 200.dp }
        assertNull(state.headerTopInset())
        assertNull(state.miniTopInset())
    }

    @Test fun `landscape zero gap and tab disposal preserve the current header owner`() {
        val state = HomeTopMiniPlayerAnchorState(
            mutableStateOf<(() -> Dp)?>({ 24.dp }), mutableStateOf(0.dp),
        )
        val oldTab = Any()
        val currentTab = Any()
        state.attach(oldTab) { 200.dp }
        state.attach(currentTab) { 112.dp }
        state.detach(oldTab)
        assertEquals(24.dp, state.headerTopInset())
        assertEquals(136.dp, state.miniTopInset())
        state.detach(currentTab)
        assertNull(state.miniTopInset())
    }
}
