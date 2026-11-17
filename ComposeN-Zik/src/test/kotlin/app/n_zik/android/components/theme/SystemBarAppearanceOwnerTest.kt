package app.n_zik.android.components.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemBarAppearanceOwnerTest {
    @Test fun `player owns status and navigation independently and closing restores latest page`() {
        val owner = SystemBarAppearanceOwner()
        assertEquals(SystemBarAppearance(false, false), owner.updatePage(false))
        assertEquals(SystemBarAppearance(true, false), owner.updatePlayer(SystemBarAppearance(true, false)))
        assertNull(owner.updatePage(true))
        assertEquals(SystemBarAppearance(true, true), owner.updatePlayer(null))
        assertNull(owner.updatePlayer(null))
    }

    @Test fun `page theme refresh cannot overwrite visible player and duplicate updates are skipped`() {
        val owner = SystemBarAppearanceOwner()
        owner.updatePage(true)
        assertEquals(SystemBarAppearance(false, true), owner.updatePlayer(SystemBarAppearance(false, true)))
        assertNull(owner.updatePage(false))
        assertNull(owner.updatePlayer(SystemBarAppearance(false, true)))
        assertEquals(SystemBarAppearance(false, false), owner.updatePlayer(null))
    }
}
