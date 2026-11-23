package app.n_zik.android.components.menu.player

import androidx.compose.runtime.mutableStateOf
import app.n_zik.android.R
import app.n_zik.android.ui.components.MenuState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerStatsMenuIconTest {
    @Test
    fun `closes the menu and shows the updated action when reopened`() {
        val menu = MenuState()
        val visible = mutableStateOf(false)
        val control = mutableStateOf<PlayerStatsMenuControl?>(
            PlayerStatsMenuControl(
                isVisible = { visible.value },
                onToggle = { visible.value = !visible.value },
            ),
        )
        val action = PlayerStatsMenuIcon(menu, control)
        menu.display { }

        assertTrue(action.isAvailable)
        assertEquals(R.drawable.bugs, action.iconId)
        assertEquals(R.string.player_action_show_stats_for_nerds, action.titleRes)
        action.onShortClick()
        assertFalse(menu.isDisplayed)

        menu.display { }
        assertTrue(action.active)
        assertEquals(R.drawable.bugs_stop, action.iconId)
        assertEquals(R.string.player_action_hide_stats_for_nerds, action.titleRes)
        action.onShortClick()
        assertFalse(menu.isDisplayed)

        menu.display { }
        assertFalse(action.active)
        assertEquals(R.drawable.bugs, action.iconId)
        assertEquals(R.string.player_action_show_stats_for_nerds, action.titleRes)
    }

    @Test
    fun `retained action follows the current surface and availability`() {
        val control = mutableStateOf<PlayerStatsMenuControl?>(null)
        val action = PlayerStatsMenuIcon(MenuState(), control)
        assertFalse(action.isAvailable)

        var firstVisible = true
        control.value = PlayerStatsMenuControl({ firstVisible }, { firstVisible = !firstVisible })
        assertTrue(action.isAvailable)
        assertEquals(R.drawable.bugs_stop, action.iconId)

        var secondVisible = false
        control.value = PlayerStatsMenuControl({ secondVisible }, { secondVisible = !secondVisible })
        assertEquals(R.drawable.bugs, action.iconId)
        action.onShortClick()
        assertTrue(firstVisible)
        assertTrue(secondVisible)

        control.value = null
        assertFalse(action.isAvailable)
        action.onShortClick()
        assertTrue(secondVisible)
    }
}
