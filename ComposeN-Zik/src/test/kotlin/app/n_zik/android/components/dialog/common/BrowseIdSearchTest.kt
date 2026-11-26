package app.n_zik.android.components.dialog.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseIdSearchTest {
    @Test
    fun `failed search ends loading and can be retried with the same query`() = runTest {
        var attempt = 0
        val search = BrowseIdSearch("Album", this, { false }) {
            if (++attempt == 1) Result.failure(IllegalStateException("Network unavailable"))
            else Result.success(listOf("result"))
        }

        search.submit()
        assertTrue(search.isSearching)
        runCurrent()
        assertTrue(search.failed)
        assertFalse(search.isSearching)
        assertNull(search.results)

        search.submit()
        runCurrent()
        assertFalse(search.failed)
        assertEquals(listOf("result"), search.results)
    }

    @Test
    fun `an older request cannot replace the latest search outcome`() = runTest {
        val older = CompletableDeferred<Result<List<String>>>()
        val search = BrowseIdSearch("First", this, { false }) { query ->
            if (query == "First") older.await() else Result.success(emptyList())
        }
        search.submit()
        runCurrent()
        search.query = "Second"
        assertNull(search.results)
        search.submit()
        runCurrent()
        older.complete(Result.failure(IllegalStateException("Old failure")))
        runCurrent()

        assertFalse(search.failed)
        assertEquals(emptyList<String>(), search.results)
    }

    @Test
    fun `full offline mode reports failure without fetching remotely`() = runTest {
        val search = BrowseIdSearch<String>("Album", this, { true }) { error("Unexpected remote fetch") }

        search.submit()
        runCurrent()

        assertTrue(search.failed)
        assertFalse(search.isSearching)
        assertNull(search.results)
    }
}
