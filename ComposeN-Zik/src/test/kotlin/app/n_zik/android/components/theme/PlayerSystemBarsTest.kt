package app.n_zik.android.components.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerSystemBarsTest {
    @Test fun `an activity scrim can change Android icons and restore the page`() {
        val owner = SystemBarAppearanceOwner()
        assertEquals(SystemBarAppearance(false, false), owner.updatePage(isDark = false))
        assertEquals(SystemBarAppearance(true, true), owner.updatePage(isDark = true))
        assertEquals(SystemBarAppearance(false, false), owner.updatePage(isDark = false))
    }

    @Test fun `Android icon polarity follows each visible surface during the palette fade`() {
        val old = resolvePlayerSystemBarAppearance(Color.Black, Color.White)
        val middle = resolvePlayerSystemBarAppearance(Color(140, 140, 140), Color.White)
        val target = resolvePlayerSystemBarAppearance(Color.White, Color.Black)
        assertEquals(SystemBarAppearance(statusIsDark = true, navigationIsDark = false), old)
        assertEquals(SystemBarAppearance(statusIsDark = false, navigationIsDark = false), middle)
        assertEquals(SystemBarAppearance(statusIsDark = false, navigationIsDark = true), target)
    }

    @Test fun `system bars restore the page update after the player releases ownership`() {
        val owner = SystemBarAppearanceOwner()
        val player = resolvePlayerSystemBarAppearance(Color.White, Color.Black)
        assertEquals(player, owner.updatePlayer(player))
        assertEquals(null, owner.updatePage(isDark = false))
        assertEquals(SystemBarAppearance(statusIsDark = false, navigationIsDark = false), owner.updatePlayer(null))
    }
}
