package app.n_zik.android.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Contract for the profile-aware database file name (Profiles feature):
 *
 * - [Database.fileNameForProfile] derives `data.db` for the default profile and
 *   `data_<profile>.db` for every other profile — pinned on the pure function so
 *   the derivation cannot regress regardless of test-JVM state (the suite runs
 *   in a single shared JVM where [Database.FILE_NAME] is cached on first access);
 * - the [Database] object must still initialize without an initialized
 *   application and resolve [Database.FILE_NAME] to the default profile file
 *   (regression: the eager profile lookup used to fail the static
 *   initialization and poisoned the shared test sandbox).
 */
class DatabaseFileNameTest {

    @Test
    fun `default profile keeps the plain database file name`() {
        assertEquals("data.db", Database.fileNameForProfile("default"))
    }

    @Test
    fun `other profiles get a suffixed database file name`() {
        assertEquals("data_work.db", Database.fileNameForProfile("work"))
    }

    @Test
    fun `file name resolves to the default profile database without an initialized application`() {
        assertEquals("data.db", Database.FILE_NAME)
    }
}
