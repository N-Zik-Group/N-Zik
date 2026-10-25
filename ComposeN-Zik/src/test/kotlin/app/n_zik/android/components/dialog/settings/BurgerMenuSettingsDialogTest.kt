package app.n_zik.android.components.dialog.settings

import app.n_zik.android.utils.DataStoreUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure logic of the burger menu customizer (spec-customizable-burger-menu): defensive order
 * parsing (blank/corrupt -> default, unknown ids dropped, missing appended, multiple
 * separator instances kept in position up to the cap) and the visible-id resolution where
 * the locked set always survives a stored toggle while the shared separator toggle masks
 * every instance.
 */
class BurgerMenuSettingsDialogTest {

    private val defaultOrder = DataStoreUtils.DEFAULT_BURGER_MENU_ORDER

    @Test
    fun `parseOrder with no stored value gives the default order`() {
        assertEquals(defaultOrder, BurgerMenuSettingsDialog.parseOrder(""))
        assertEquals(defaultOrder, BurgerMenuSettingsDialog.parseOrder("   "))
    }

    @Test
    fun `parseOrder keeps the stored order and appends missing ids`() {
        val order = BurgerMenuSettingsDialog.parseOrder(serialize("history", "profiles", "settings"))

        assertEquals("history", order.first())
        assertEquals("profiles", order[1])
        assertEquals("settings", order[2])
        assertTrue(order.containsAll(defaultOrder))
        assertEquals(defaultOrder.size, order.size)
    }

    @Test
    fun `parseOrder drops unknown ids`() {
        val order = BurgerMenuSettingsDialog.parseOrder(serialize("history", "nope", "settings"))

        assertFalse("nope" in order)
        assertEquals(defaultOrder.size, order.size)
    }

    @Test
    fun `parseOrder de-duplicates repeated ids`() {
        val order = BurgerMenuSettingsDialog.parseOrder(serialize("history", "history", "settings"))

        assertEquals(1, order.count { it == "history" })
        assertEquals(defaultOrder.size, order.size)
    }

    @Test
    fun `parseOrder falls back to the default on a corrupt value`() {
        assertEquals(defaultOrder, BurgerMenuSettingsDialog.parseOrder("not a json array"))
        assertEquals(defaultOrder, BurgerMenuSettingsDialog.parseOrder("{\"broken\":true}"))
    }

    @Test
    fun `parseOrder falls back to the default on a non-string array`() {
        assertEquals(defaultOrder, BurgerMenuSettingsDialog.parseOrder("[1,2,3]"))
    }

    @Test
    fun `serializeOrder round-trips through parseOrder`() {
        val order = listOf("settings", "history", "separator", "profiles") +
            defaultOrder.filter { it !in setOf("settings", "history", "separator", "profiles") }

        assertEquals(order, BurgerMenuSettingsDialog.parseOrder(BurgerMenuSettingsDialog.serializeOrder(order)))
    }

    @Test
    fun `the toggle key is namespaced per item in the global store`() {
        assertEquals("burger_menu_toggle_rewind", DataStoreUtils.burgerMenuToggleKey("rewind"))
    }

    @Test
    fun `the locked set is exactly the settings entry`() {
        // G5 renegotiated: the separator is maskable (shared toggle) and only Settings is locked.
        assertEquals(setOf("settings"), DataStoreUtils.LOCKED_BURGER_MENU_ITEMS)
    }

    @Test
    fun `the separator cap is five`() {
        assertEquals(5, DataStoreUtils.MAX_BURGER_MENU_SEPARATORS)
    }

    @Test
    fun `parseOrder keeps multiple separator instances in their positions`() {
        val order = BurgerMenuSettingsDialog.parseOrder(
            serialize("separator", "history", "separator", "profiles")
        )

        assertEquals(2, order.count { it == "separator" })
        assertEquals("separator", order[0])
        assertEquals("history", order[1])
        assertEquals("separator", order[2])
        assertEquals("profiles", order[3])
        // 13 fixed ids + one extra separator instance
        assertEquals(defaultOrder.size + 1, order.size)
    }

    @Test
    fun `parseOrder appends a default separator when none is stored`() {
        val order = BurgerMenuSettingsDialog.parseOrder(serialize("history", "settings"))

        assertEquals(1, order.count { it == "separator" })
        assertTrue("separator" in order)
        assertEquals(defaultOrder.size, order.size)
    }

    @Test
    fun `parseOrder trims separator instances above the cap keeping the first positions`() {
        val order = BurgerMenuSettingsDialog.parseOrder(
            serialize("separator", "history", "separator", "settings", "separator", "separator", "separator", "separator")
        )

        // 6 instances stored, only the first 5 survive - in their positions
        assertEquals(DataStoreUtils.MAX_BURGER_MENU_SEPARATORS, order.count { it == "separator" })
        assertEquals("separator", order[0])
        assertEquals("history", order[1])
        assertEquals("separator", order[2])
        assertEquals("settings", order[3])
        assertEquals("separator", order[4])
        assertEquals("separator", order[5])
        assertEquals("separator", order[6])
        assertFalse(
            order[7] == "separator",
            "the 6th stored instance is the one trimmed"
        )
    }

    @Test
    fun `resolveVisibleIds hides a masked item`() {
        val toggles = defaultOrder.associateWith { it != "rewind" }

        assertEquals(
            defaultOrder.filter { it != "rewind" },
            BurgerMenuSettingsDialog.resolveVisibleIds(defaultOrder, toggles)
        )
    }

    @Test
    fun `resolveVisibleIds never hides a locked item even when its toggle is off`() {
        val toggles = defaultOrder.associateWith { false }

        val visible = BurgerMenuSettingsDialog.resolveVisibleIds(defaultOrder, toggles)

        DataStoreUtils.LOCKED_BURGER_MENU_ITEMS.forEach { id ->
            assertTrue(id in visible, "$id must survive a stored off toggle")
        }
        assertEquals(defaultOrder.filter { it in DataStoreUtils.LOCKED_BURGER_MENU_ITEMS }, visible)
    }

    @Test
    fun `resolveVisibleIds hides the maskable items whose toggle is off`() {
        val toggles = defaultOrder.associateWith { it !in setOf("rewind", "pip") }

        val visible = BurgerMenuSettingsDialog.resolveVisibleIds(defaultOrder, toggles)

        assertFalse("rewind" in visible)
        assertFalse("pip" in visible)
        assertTrue("separator" in visible, "the separator toggle is on")
        assertTrue("listen_together" in visible)
    }

    @Test
    fun `resolveVisibleIds hides all separator instances when the separator toggle is off`() {
        // G5 renegotiated: one shared toggle masks every stored separator instance
        val order = listOf("history", "separator", "settings", "separator", "pip")
        val toggles = mapOf("separator" to false, "history" to true, "settings" to true, "pip" to true)

        val visible = BurgerMenuSettingsDialog.resolveVisibleIds(order, toggles)

        assertEquals(listOf("history", "settings", "pip"), visible)
    }

    @Test
    fun `resolveVisibleIds treats a missing toggle as visible`() {
        assertEquals(defaultOrder, BurgerMenuSettingsDialog.resolveVisibleIds(defaultOrder, emptyMap()))
    }

    @Test
    fun `resolveVisibleIds hides history statistics and rescue center when masked`() {
        // G3 renegotiated: these items are now maskable - masking them only removes
        // them from the menu, their settings entries stay reachable.
        val toggles = defaultOrder.associateWith { it !in setOf("history", "statistics", "rescue_center") }

        val visible = BurgerMenuSettingsDialog.resolveVisibleIds(defaultOrder, toggles)

        assertFalse("history" in visible)
        assertFalse("statistics" in visible)
        assertFalse("rescue_center" in visible)
        assertTrue("settings" in visible, "the locked items survive any masking")
        assertTrue("separator" in visible, "the separator toggle is on")
    }

    private fun serialize(vararg ids: String): String =
        BurgerMenuSettingsDialog.serializeOrder(ids.toList())
}
