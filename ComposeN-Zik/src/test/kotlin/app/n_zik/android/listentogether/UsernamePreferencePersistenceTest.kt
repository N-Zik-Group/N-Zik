package app.n_zik.android.listentogether

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.utils.DataStoreUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression test for the "username saves nothing" report: the Listen Together
 * username is persisted through [DataStoreUtils.saveString]/[getString] (plain
 * SharedPreferences), and the settings card reads it back after each save
 * (bumping a remembered version so the entry text refreshes). This pins the
 * round-trip contract the card and the client rely on, including the
 * empty-value reset path (saving "" must clear the stored value).
 *
 * Robolectric is required (not a plain JVM unit test) because [DataStoreUtils]
 * reads `Context.getSharedPreferences`, which needs a real Android environment
 * to shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UsernamePreferencePersistenceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = "listen_together_username"

    @Test
    fun `saved username is read back on the next read`() {
        DataStoreUtils.saveString(context, key, "alice")
        assertEquals("alice", DataStoreUtils.getString(context, key))
    }

    @Test
    fun `saving an empty value resets the stored username`() {
        DataStoreUtils.saveString(context, key, "alice")
        DataStoreUtils.saveString(context, key, "")
        assertEquals("", DataStoreUtils.getString(context, key))
    }

    @Test
    fun `missing username falls back to the default`() {
        assertEquals("fallback", DataStoreUtils.getString(context, key, "fallback"))
    }
}
