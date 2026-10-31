package app.n_zik.android.components.tab

import app.it.fast4x.rimusic.ui.components.MenuState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatefulMenuIconTest {
    @Test fun `toggle retains open menu and updates live state on repeated clicks`() {
        val menu = MenuState()
        var active = false
        val action = StatefulMenuIcon(menu, 0, { active }, { 0 }, { 0 }, { active = !active })
        menu.display { }
        action.onShortClick()
        assertTrue(active)
        assertTrue(action.active)
        assertTrue(menu.isDisplayed)
        action.onShortClick()
        assertFalse(active)
        assertFalse(action.active)
        assertTrue(menu.isDisplayed)
    }

    @Test fun `one shot action can explicitly close its menu`() {
        val menu = MenuState()
        var calls = 0
        val action = StatefulMenuIcon(menu, 0, { false }, { 0 }, { 0 }, { calls++ }, closeMenuAfterAction = true)
        menu.display { }
        action.onShortClick()
        assertEquals(1, calls)
        assertFalse(menu.isDisplayed)
    }
}
