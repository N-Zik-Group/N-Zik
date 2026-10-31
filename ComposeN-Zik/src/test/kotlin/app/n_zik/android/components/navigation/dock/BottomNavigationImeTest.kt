package app.n_zik.android.components.navigation.dock

import app.it.fast4x.rimusic.enums.NavigationBarPosition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BottomNavigationImeTest {
    @Test fun `only bottom navigation clears the keyboard beyond its existing system inset`() {
        for (position in NavigationBarPosition.entries) {
            val bottom = position == NavigationBarPosition.Bottom || position == NavigationBarPosition.BottomFloating
            assertEquals(if (bottom) 280 else 0, bottomNavigationImeLiftPx(position, 304, 24))
            assertEquals(0, bottomNavigationImeLiftPx(position, 0, 24))
            assertEquals(0, bottomNavigationImeLiftPx(position, 12, 24))
            assertEquals(if (bottom) 304 else 0, bottomNavigationImeLiftPx(position, 304, 0))
        }
    }
}
