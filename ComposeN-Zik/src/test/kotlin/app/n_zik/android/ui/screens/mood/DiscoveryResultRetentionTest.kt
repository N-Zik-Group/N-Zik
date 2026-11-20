package app.n_zik.android.ui.screens.mood

import java.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class DiscoveryResultRetentionTest {
    @Test
    fun `failed revalidation keeps the loaded page`() {
        val current = Result.success(listOf("album"))
        val failed = Result.failure<List<String>>(IOException("offline"))

        assertEquals(current, retainLastDiscoveryResult(current, failed))
    }

    @Test
    fun `initial failure is reported and the next success replaces it`() {
        val error = IOException("offline")
        val failed = Result.failure<List<String>>(error)
        val loaded = Result.success(listOf("album"))

        assertSame(error, retainLastDiscoveryResult(null, failed).exceptionOrNull())
        assertEquals(loaded, retainLastDiscoveryResult(failed, loaded))
    }

    @Test
    fun `a successful empty page replaces stale items`() {
        val current = Result.success(listOf("old album"))
        val empty = Result.success(emptyList<String>())

        assertEquals(empty, retainLastDiscoveryResult(current, empty))
    }
}
