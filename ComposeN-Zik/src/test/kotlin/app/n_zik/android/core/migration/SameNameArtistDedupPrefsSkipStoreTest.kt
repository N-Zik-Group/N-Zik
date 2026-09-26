package app.n_zik.android.core.migration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract of the production [PrefsSkipStore] (the SharedPreferences/JSON
 * implementation the sweep wires in [SameNameArtistDedup.run]) - the core
 * tests only ever exercise an in-memory fake:
 * remember -> read-back (including the song-aware composition: songs round-trip,
 * songs == null stays null), forget on null, cross-instance persistence on the
 * same prefs file, legacy rows-only array entries read as rows + unknown (empty)
 * songs — one re-judgment, never the rows-only "primary" semantics that would
 * defer them forever — and silent degradation to empty memory on a corrupted
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
    fun rememberThenReadBackReturnsTheComposition() {
        val store = PrefsSkipStore(context)
        assertNull(store.rememberedComposition("Ice"))
        // Fallback-judged composition: the selected songs round-trip
        store.remember("Ice", RememberedComposition(setOf("A", "B"), setOf("S1", "S2")))
        assertEquals(
            RememberedComposition(setOf("A", "B"), setOf("S1", "S2")),
            store.rememberedComposition("Ice"),
        )
        // Primary-judged composition: songs == null round-trips as null
        store.remember("Laur", RememberedComposition(setOf("C")))
        assertEquals(RememberedComposition(setOf("C"), null), store.rememberedComposition("Laur"))
    }

    @Test
    fun rememberNullForgetsTheEntry() {
        val store = PrefsSkipStore(context)
        store.remember("Ice", RememberedComposition(setOf("A", "B")))
        store.remember("Ice", null)
        assertNull(store.rememberedComposition("Ice"))
    }

    @Test
    fun aSecondInstanceOnTheSamePrefsFileSeesTheMemory() {
        PrefsSkipStore(context).remember("Ice", RememberedComposition(setOf("A")))
        assertEquals(RememberedComposition(setOf("A")), PrefsSkipStore(context).rememberedComposition("Ice"))
    }

    @Test
    fun independentGroupsAreStoredSeparately() {
        val store = PrefsSkipStore(context)
        store.remember("Ice", RememberedComposition(setOf("A")))
        store.remember("Laur", RememberedComposition(setOf("B")))
        assertEquals(RememberedComposition(setOf("A")), store.rememberedComposition("Ice"))
        assertEquals(RememberedComposition(setOf("B")), store.rememberedComposition("Laur"))
    }

    @Test
    fun legacyArrayEntryReadsAsRowsWithUnknownSongsAndIsRewrittenInTheObjectFormat() {
        // The pre-song-aware format stored a plain JSON array of row ids per
        // group; such an entry was judged by a build that did not track songs,
        // so its song state is UNKNOWN: it must read as rows + songs = emptySet()
        // (the group is re-judged once, then rewritten) — never as songs = null,
        // the "primary-judged" semantics that would defer it forever
        context.getSharedPreferences("same_name_artist_dedup", Context.MODE_PRIVATE)
            .edit()
            .putString("remembered_groups", "{\"Ice\":[\"A\",\"B\"]}")
            .apply()

        val store = PrefsSkipStore(context)
        assertEquals(
            RememberedComposition(setOf("A", "B"), emptySet()),
            store.rememberedComposition("Ice"),
        )
        // and it is rewritten in the object format on the next remember
        store.remember("Ice", RememberedComposition(setOf("A", "B")))
        val json = JSONObject(
            context.getSharedPreferences("same_name_artist_dedup", Context.MODE_PRIVATE)
                .getString("remembered_groups", "{}") ?: "{}",
        )
        assertTrue(json.get("Ice") is JSONObject)
    }

    @Test
    fun presentButMalformedSongsKeyReadsAsUnknownNotPrimary() {
        // A "songs" key that is PRESENT but not a valid JSON array (here: a
        // plain string) is UNKNOWN song state -> rows + songs = emptySet()
        // (the group is re-judged once, then rewritten) — never songs = null,
        // the "primary-judged" semantics that would defer it forever
        context.getSharedPreferences("same_name_artist_dedup", Context.MODE_PRIVATE)
            .edit()
            .putString("remembered_groups", "{\"Ice\":{\"rows\":[\"A\",\"B\"],\"songs\":\"corrupt\"}}")
            .apply()

        val store = PrefsSkipStore(context)
        assertEquals(
            RememberedComposition(setOf("A", "B"), emptySet()),
            store.rememberedComposition("Ice"),
        )
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
        assertNull(store.rememberedComposition("Ice"))
        // and the store stays usable: a fresh remember rewrites the blob
        store.remember("Ice", RememberedComposition(setOf("A")))
        assertEquals(RememberedComposition(setOf("A")), PrefsSkipStore(context).rememberedComposition("Ice"))
    }
}
