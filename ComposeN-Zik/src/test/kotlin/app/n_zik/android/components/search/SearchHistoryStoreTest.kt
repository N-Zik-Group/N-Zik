package app.n_zik.android.components.search

import android.content.SharedPreferences
import app.it.fast4x.rimusic.utils.pauseSearchHistoryKey
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchHistoryStoreTest {
    private val page = SearchHistoryStore.PAGE_LOCAL

    // In-memory SharedPreferences: the editor writes into the map, the read
    // falls back to the default when a key is absent
    private fun fakePrefs(): Pair<SharedPreferences, MutableMap<String, String>> {
        val values = mutableMapOf<String, String>()
        val editor = mockk<SharedPreferences.Editor>(relaxed = true)
        val prefs = mockk<SharedPreferences> {
            every { edit() } returns editor
            every { getString(any(), any()) } answers {
                values[firstArg()] ?: secondArg()
            }
            every { getBoolean(any(), any()) } answers {
                values[firstArg()]?.toBooleanStrictOrNull() ?: secondArg()
            }
        }
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg<String>())
            editor
        }
        return prefs to values
    }

    @Test
    fun `disabled page cannot record and keeps its old entries`() {
        val (prefs, values) = fakePrefs()
        SearchHistoryStore.record(prefs, page, "kept")
        values[SearchHistoryStore.enabledKey(page)] = "false"
        assertTrue(SearchHistoryStore.record(prefs, page, "blocked").isEmpty())
        assertEquals(listOf("kept"), SearchHistoryStore.all(prefs, page))
        assertEquals(listOf("other"), SearchHistoryStore.record(prefs, SearchHistoryStore.PAGE_ONLINE, "other"))
        values[SearchHistoryStore.enabledKey(page)] = "true"
        assertTrue(SearchHistoryStore.isEnabled(prefs, page))
        assertEquals(listOf("kept"), SearchHistoryStore.all(prefs, page))
    }

    @Test
    fun `global pause overrides page choices without changing them`() {
        val (prefs, values) = fakePrefs()
        values[pauseSearchHistoryKey] = "true"
        assertTrue(SearchHistoryStore.record(prefs, page, "blocked").isEmpty())
        values[pauseSearchHistoryKey] = "false"
        assertTrue(SearchHistoryStore.isEnabled(prefs, page))
    }

    @Test
    fun `clear all deletes actual page histories and preserves preferences`() {
        val (prefs, values) = fakePrefs()
        SearchHistoryStore.pages.forEach { SearchHistoryStore.record(prefs, it, "query") }
        values[SearchHistoryStore.enabledKey(page)] = "false"
        values["unrelated"] = "keep"
        assertEquals(SearchHistoryStore.pages.size, SearchHistoryStore.countAll(prefs))
        SearchHistoryStore.clearAll(prefs)
        assertEquals(0, SearchHistoryStore.countAll(prefs))
        assertEquals("false", values[SearchHistoryStore.enabledKey(page)])
        assertEquals("keep", values["unrelated"])
    }

    @Test
    fun `record should add the query at the front`() {
        val (prefs, _) = fakePrefs()

        val result = SearchHistoryStore.record(prefs, page, "hello")

        assertEquals(listOf("hello"), result)
    }

    @Test
    fun `record should move an existing query to the front instead of duplicating it`() {
        val (prefs, _) = fakePrefs()

        SearchHistoryStore.record(prefs, page, "a")
        SearchHistoryStore.record(prefs, page, "b")
        val result = SearchHistoryStore.record(prefs, page, "a")

        assertEquals(listOf("a", "b"), result)
    }

    @Test
    fun `record should trim the query before storing it`() {
        val (prefs, _) = fakePrefs()

        val result = SearchHistoryStore.record(prefs, page, "  beatles  ")

        assertEquals(listOf("beatles"), result)
    }

    @Test
    fun `record should keep the newest entries up to the cap`() {
        val (prefs, _) = fakePrefs()

        (1..(SearchHistoryStore.CAP + 5)).forEach { SearchHistoryStore.record(prefs, page, "q$it") }
        val result = SearchHistoryStore.all(prefs, page)

        assertEquals(SearchHistoryStore.CAP, result.size)
        assertEquals("q${SearchHistoryStore.CAP + 5}", result.first())
        assertEquals("q6", result.last())
    }

    @Test
    fun `record should ignore blank queries`() {
        val (prefs, _) = fakePrefs()

        SearchHistoryStore.record(prefs, page, "a")
        val result = SearchHistoryStore.record(prefs, page, "   ")

        assertEquals(listOf("a"), result)
    }

    @Test
    fun `delete should remove the matching entry only`() {
        val (prefs, _) = fakePrefs()

        SearchHistoryStore.record(prefs, page, "a")
        SearchHistoryStore.record(prefs, page, "b")
        val result = SearchHistoryStore.delete(prefs, page, "a")

        assertEquals(listOf("b"), result)
    }

    @Test
    fun `deleteAll should clear the page history`() {
        val (prefs, _) = fakePrefs()

        SearchHistoryStore.record(prefs, page, "a")
        val result = SearchHistoryStore.deleteAll(prefs, page)

        assertTrue(result.isEmpty())
        assertTrue(SearchHistoryStore.all(prefs, page).isEmpty())
    }

    @Test
    fun `all should return an empty list when the stored value is corrupted`() {
        val (prefs, values) = fakePrefs()

        values["searchHistory_$page"] = "{not json"

        assertTrue(SearchHistoryStore.all(prefs, page).isEmpty())
    }

    @Test
    fun `all should keep pages separate`() {
        val (prefs, _) = fakePrefs()

        SearchHistoryStore.record(prefs, SearchHistoryStore.PAGE_LOCAL, "local-query")
        SearchHistoryStore.record(prefs, SearchHistoryStore.PAGE_ONLINE, "online-query")

        assertEquals(listOf("local-query"), SearchHistoryStore.all(prefs, SearchHistoryStore.PAGE_LOCAL))
        assertEquals(listOf("online-query"), SearchHistoryStore.all(prefs, SearchHistoryStore.PAGE_ONLINE))
        assertTrue(SearchHistoryStore.all(prefs, SearchHistoryStore.PAGE_HISTORY).isEmpty())
    }
}
