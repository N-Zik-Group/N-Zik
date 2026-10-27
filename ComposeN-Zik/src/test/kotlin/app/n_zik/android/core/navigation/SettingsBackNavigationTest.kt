package app.n_zik.android.core.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsBackNavigationTest {
    @Test
    fun `back only consumes actions while an active Settings host owns the route`() {
        var route = "settings?tab={tab}&focus={focus}"
        val router = SettingsBackNavigation { route }
        val owner = Any()
        var calls = 0
        assertFalse(router.handleBack())
        router.register(owner) { calls++ }
        assertTrue(router.handleBack())
        route = "updater"
        assertFalse(router.handleBack())
        route = "settings"
        assertTrue(router.handleBack())
        router.unregister(owner)
        assertFalse(router.handleBack())
        assertEquals(2, calls)
    }

    @Test
    fun `disposing an outgoing host cannot unregister its replacement`() {
        val router = SettingsBackNavigation { "settings" }
        val oldOwner = Any()
        val newOwner = Any()
        var calls = 0
        router.register(oldOwner) { error("Disposed host must not receive back") }
        router.register(newOwner) { calls++ }
        router.unregister(oldOwner)
        assertTrue(router.handleBack())
        assertEquals(1, calls)
    }
    @Test
    fun `nested back ownership follows stack and active route and clears on dispose`() {
        var route = "settings"
        var nested = false
        val router = SettingsBackNavigation { route }
        val owner = Any()
        router.register(owner, canNavigateBack = { nested }) { }
        assertFalse(router.hasNestedBackStack)
        nested = true
        assertTrue(router.hasNestedBackStack)
        route = "album"
        assertFalse(router.hasNestedBackStack)
        route = "settings"
        assertTrue(router.hasNestedBackStack)
        router.unregister(owner)
        assertFalse(router.hasNestedBackStack)
    }
}
