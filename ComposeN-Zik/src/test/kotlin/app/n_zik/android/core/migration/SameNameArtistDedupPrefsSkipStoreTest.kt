package app.n_zik.android.core.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract of the production [PrefsSkipStore] (the SharedPreferences/JSON
 * implementation the sweep wires in [SameNameArtistDedup.run]) - the core
 * tests only ever exercise an in-memory fake:
 * remember -> read-back, forget on null, cross-instance persistence on the
 * same prefs file, and silent degradation to empty memory on a corrupted
 * blob (no throw, store stays usable).
 *
 * Robolectric provides the SharedPreferences backing (same pattern as
 * [SameNameArtistDedupTest]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SameNameArtistDedupPrefsSkipStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun rememberThenReadBackReturnsTheIds() {
        val store = PrefsSkipStore(context)
        assertNull(store.rememberedRows("Ice"))
        store.remember("Ice", setOf("A", "B"))
        assertEquals(setOf("A", "B"), store.rememberedRows("Ice"))
    }

    @Test
    fun rememberNullForgetsTheEntry() {
        val store = PrefsSkipStore(context)
        store.remember("Ice", setOf("A", "B"))
        store.remember("Ice", null)
        assertNull(store.rememberedRows("Ice"))
    }

    @Test
    fun aSecondInstanceOnTheSamePrefsFileSeesTheMemory() {
        PrefsSkipStore(context).remember("Ice", setOf("A"))
        assertEquals(setOf("A"), PrefsSkipStore(context).rememberedRows("Ice"))
    }

    @Test
    fun independentGroupsAreStoredSeparately() {
        val store = PrefsSkipStore(context)
        store.remember("Ice", setOf("A"))
        store.remember("Laur", setOf("B"))
        assertEquals(setOf("A"), store.rememberedRows("Ice"))
        assertEquals(setOf("B"), store.rememberedRows("Laur"))
    }

    @Test
    fun corruptedBlobDegradesToEmptyMemoryWithoutThrowing() {
        // Pref names mirror PrefsSkipStore's private constants (same package
        // test, no access to the private companion object).
        context.getSharedPreferences("same_name_artist_dedup", Context.MODE_PRIVATE)
            .edit()
            .putString("remembered_groups", "{ not json")
            .apply()

        val store = PrefsSkipStore(context)
        assertNull(store.rememberedRows("Ice"))
        // and the store stays usable: a fresh remember rewrites the blob
        store.remember("Ice", setOf("A"))
        assertEquals(setOf("A"), PrefsSkipStore(context).rememberedRows("Ice"))
    }
}
