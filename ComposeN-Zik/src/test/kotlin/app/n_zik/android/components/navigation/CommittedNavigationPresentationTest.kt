package app.n_zik.android.components.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommittedNavigationPresentationTest {
    @Test fun `outgoing presentation stays disabled until incoming composition commits and never returns afterwards`() {
        val state = CommittedNavigationPresentation<String>()
        val pages = mutableMapOf("home" to "Home tabs")
        val lookup: (String?) -> String? = { pages[it] }
        assertEquals("Home tabs", state.resolve("home", true, lookup).registration)
        val pending = state.resolve("search", true, lookup)
        assertEquals("Home tabs", pending.registration)
        assertFalse(pending.interactive)
        pages["search"] = "Search tabs"
        val committed = state.resolve("search", true, lookup)
        assertEquals("Search tabs", committed.registration)
        assertTrue(committed.interactive)
        pages.remove("search")
        assertNull(state.resolve("search", true, lookup).registration)
    }

    @Test fun `known barless destination clears the committed body without waiting for a registration`() {
        val state = CommittedNavigationPresentation<String>()
        val pages = mutableMapOf("home" to "Home tabs")
        val lookup: (String?) -> String? = { pages[it] }
        state.resolve("home", true, lookup)
        assertNull(state.resolve("settings", false, lookup).registration)
        assertNull(state.resolve("search", true, lookup).registration)
    }
}
