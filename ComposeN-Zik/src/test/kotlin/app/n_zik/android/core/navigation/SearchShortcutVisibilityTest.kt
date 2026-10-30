package app.n_zik.android.core.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchShortcutVisibilityTest {
    @Test
    fun `search and results omit the shortcut for bare template and concrete routes`() {
        listOf("search", "search?text={text}", "search?text=hello",
            "searchResults", "searchResults/{query}", "searchResults/hello%20world").forEach { route ->
            assertFalse(shouldShowSearchShortcut(route), route)
        }
    }

    @Test
    fun `other destinations retain the shortcut including names that merely start with search`() {
        listOf(null, "home", "settings?tab=1", "playlist/12", "searchHistory", "searchResultsArchive")
            .forEach { route -> assertTrue(shouldShowSearchShortcut(route), route) }
    }
}
