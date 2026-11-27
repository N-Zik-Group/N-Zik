package app.n_zik.android.components.menu

import app.n_zik.android.ui.components.MenuState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MenuActionStateTest {
    @Test
    fun siblingMenuRejectsPendingActionFromPoppedMenu() {
        val state = MenuState()
        state.display {}
        state.display {}
        val previous = state.transitionKey

        state.pop()
        state.display {}

        assertFalse(state.isCurrentMenu(previous))
        assertTrue(state.isCurrentMenu(state.transitionKey))
    }

    @Test
    fun nestedMenuRevealsTheQueueDestinationOnlyAfterNavigating() {
        val state = MenuState()
        val events = mutableListOf<String>()
        state.display(onDestinationOpened = { events += "reveal" }) {}
        state.display {}

        assertTrue(state.openDestination { events += "navigate" })
        assertEquals(listOf("navigate", "reveal"), events)

        state.hide()
        state.display {}
        assertTrue(state.openDestination { events += "unrelated" })
        assertEquals(listOf("navigate", "reveal", "unrelated"), events)
    }

    @Test
    fun failedQueueNavigationKeepsTheNestedMenuAndItsParent() {
        val state = MenuState()
        var reveals = 0
        var failures = 0
        state.display(onDestinationOpened = { reveals++ }) {}
        state.display {}
        val menuKey = state.transitionKey

        assertFalse(state.openDestination(reportFailure = { failures++ }) {
            throw IllegalArgumentException("Unknown route")
        })
        assertEquals(0, reveals)
        assertEquals(1, failures)
        assertTrue(state.isCurrentMenu(menuKey))
        assertTrue(state.hasPrevious)
    }
}
