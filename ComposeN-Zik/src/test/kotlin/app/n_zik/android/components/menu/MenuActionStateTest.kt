package app.n_zik.android.components.menu

import app.n_zik.android.ui.components.MenuState
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
}
