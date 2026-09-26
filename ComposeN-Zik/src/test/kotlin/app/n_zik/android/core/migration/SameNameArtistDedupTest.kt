package app.n_zik.android.core.migration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.core.database.DatabaseInitializer
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Contract for the same-name artist dedup ([SameNameArtistDedup]): the YTM
 * search browse id is the arbiter, duplicate rows are re-linked before they
 * are deleted (no orphans), the user's follow/dislike move onto the truth row,
 * images of the STORED rows are compared against each other (never against the
 * search thumbnail — ties and lone rows without image are flagged, identical
 * or matching pictures are merged), `LOCAL_ARTIST_` rows are untouched,
 * already-judged groups with an unchanged composition are skipped without
 * network, and offline / no-match / failed groups are left for the next
 * launch (no per-launch cap — searches are only rate-limited).
 *
 * Runs the real core against an in-memory Room database with a fake resolver,
 * so the merge primitives (de-duplicate link, re-link, follow carry-over,
 * delete) are exercised end to end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SameNameArtistDedupTest {

    private lateinit var db: DatabaseInitializer

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DatabaseInitializer::class.java)
            .allowMainThreadQueries()
            .build()
        // Redirect the lazy singleton to the in-memory database so
        // [SameNameArtistDedup.runDedup] runs its real code (including its
        // transactions) against the DAOs under test.
        mockkObject(DatabaseInitializer.Companion)
        every { DatabaseInitializer.Instance } returns db
    }

    @After
    fun closeDb() {
        unmockkAll()
        db.close()
    }

    // ---- fixtures ----

    private fun insertSong(id: String) {
        db.songTable.upsert(Song.makePlaceholder(id))
    }

    private fun insertArtist(
        id: String,
        name: String,
        thumbnail: String? = null,
        bookmarkedAt: Long? = null,
        dislikedAt: Long? = null,
    ) {
        db.artistTable.upsert(
            Artist(
                id = id,
                name = name,
                thumbnailUrl = thumbnail,
                bookmarkedAt = bookmarkedAt,
                dislikedAt = dislikedAt,
                isYoutubeArtist = true,
            )
        )
    }

    private fun insertLink(songId: String, artistId: String) {
        db.songArtistMapTable.insertIgnore(SongArtistMap(songId, artistId))
    }

    private fun linksOf(artistId: String): List<SongArtistMap> =
        db.songArtistMapTable.allPairsDirect().filter { it.artistId == artistId }

    /** Resolver answering from a fixed table; [calls] records the search count. */
    private class FakeResolver(
        private val results: Map<String, ResolvedArtist?>,
        private val failures: Set<String> = emptySet(),
    ) : ArtistSearchResolver {
        var calls = 0

        override suspend fun searchArtist(name: String): ResolvedArtist? {
            calls++
            if (name in failures) throw IllegalStateException("simulated network failure")
            return results[name]
        }
    }

    /** In-memory [SkipStore] for tests; [memory] is inspectable after a run. */
    private class FakeSkipStore(
        initial: Map<String, Set<String>> = emptyMap(),
    ) : SkipStore {
        val memory = initial.toMutableMap()

        override fun rememberedRows(groupName: String): Set<String>? = memory[groupName]

        override fun remember(groupName: String, rowIds: Set<String>?) {
            if (rowIds == null) memory.remove(groupName) else memory[groupName] = rowIds
        }
    }

    private suspend fun run(
        resolver: ArtistSearchResolver,
        online: Boolean = true,
        skipStore: SkipStore? = null,
    ): DedupResult = SameNameArtistDedup.runDedup(
        artistTable = db.artistTable,
        mapTable = db.songArtistMapTable,
        resolver = resolver,
        skipStore = skipStore,
        online = online,
    )

    private fun resolved(id: String, name: String, thumb: String? = null) =
        ResolvedArtist(browseId = id, name = name, thumbnailUrl = thumb)

    // ---- truth row & merges ----

    @Test
    fun rowWithSearchBrowseIdIsKeptAndOthersMerged() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertSong("s3")
        insertArtist("TOPIC_A", "Camellia", thumbnail = "T")
        insertArtist("TRUTH_ID", "Camellia", thumbnail = "T")
        insertLink("s1", "TOPIC_A")
        insertLink("s2", "TOPIC_A")
        insertLink("s3", "TRUTH_ID")

        val result = run(FakeResolver(mapOf("Camellia" to resolved("TRUTH_ID", "Camellia", "T"))))

        assertEquals(1, result.merged)
        assertNull(db.artistTable.findByIdDirect("TOPIC_A"))
        assertNotNull(db.artistTable.findByIdDirect("TRUTH_ID"))
        // The truth row is pinned to the group name, marked custom
        assertEquals("modified:Camellia", db.artistTable.findByIdDirect("TRUTH_ID")?.name)
        // The topic row's songs were re-linked onto the truth row before deletion
        assertEquals(setOf("s1", "s2", "s3"), linksOf("TRUTH_ID").map { it.songId }.toSet())
    }

    @Test
    fun canonicalRowIsCreatedWhenNoLocalRowMatches() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("OLD_A", "Aoi", thumbnail = "T")
        insertArtist("OLD_B", "Aoi", thumbnail = "T")
        insertLink("s1", "OLD_A")
        insertLink("s2", "OLD_B")

        val result = run(FakeResolver(mapOf("Aoi" to resolved("CANON_ID", "Aoi", "T"))))

        assertEquals(2, result.merged)
        assertNull(db.artistTable.findByIdDirect("OLD_A"))
        assertNull(db.artistTable.findByIdDirect("OLD_B"))
        val canonical = db.artistTable.findByIdDirect("CANON_ID")
        assertNotNull(canonical)
        assertEquals("modified:Aoi", canonical?.name)
        assertEquals("T", canonical?.thumbnailUrl)
        assertEquals(setOf("s1", "s2"), linksOf("CANON_ID").map { it.songId }.toSet())
    }

    @Test
    fun songAlreadyLinkedToTruthKeepsSingleLink() = runTest {
        // s1 is linked to BOTH the row being merged and the truth row
        insertSong("s1")
        insertArtist("TOPIC_A", "Camellia", thumbnail = "T")
        insertArtist("TRUTH_ID", "Camellia", thumbnail = "T")
        insertLink("s1", "TOPIC_A")
        insertLink("s1", "TRUTH_ID")

        val result = run(FakeResolver(mapOf("Camellia" to resolved("TRUTH_ID", "Camellia", "T"))))

        assertEquals(1, result.merged)
        assertEquals(
            listOf(SongArtistMap("s1", "TRUTH_ID")),
            db.songArtistMapTable.pairsBySongIdDirect("s1")
        )
    }

    // ---- image safety ----

    @Test
    fun identicalStoredImagesAreMergedEvenWhenSearchThumbDiffers() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("ROW_A", "Camellia", thumbnail = "STORED")
        insertArtist("ROW_B", "Camellia", thumbnail = "STORED")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")

        // The search thumbnail is a differently cropped photo of the SAME
        // artist: it must not flag rows that share the stored picture
        // (device false positive, capture 2026-09-26).
        val result = run(
            FakeResolver(mapOf("Camellia" to resolved("TRUTH_ID", "Camellia", "SEARCH_THUMB")))
        )

        assertEquals(2, result.merged)
        assertEquals(0, result.flagged)
        assertNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNull(db.artistTable.findByIdDirect("ROW_B"))
        assertEquals(setOf("s1", "s2"), linksOf("TRUTH_ID").map { it.songId }.toSet())
    }

    @Test
    fun truthRowImageIsTheReference() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("MATCHES_TRUTH", "Getty", thumbnail = "T")
        insertArtist("DIFFERS_FROM_TRUTH", "Getty", thumbnail = "OTHER")
        insertArtist("TRUTH_ID", "Getty", thumbnail = "T")
        insertLink("s1", "MATCHES_TRUTH")
        insertLink("s2", "DIFFERS_FROM_TRUTH")

        val result = run(
            FakeResolver(mapOf("Getty" to resolved("TRUTH_ID", "Getty", "SEARCH_THUMB")))
        )

        // The truth row carries a picture: it is the reference
        assertEquals(1, result.merged)
        assertEquals(1, result.flagged)
        assertNull(db.artistTable.findByIdDirect("MATCHES_TRUTH"))
        // The homonym suspect stays, with its own link
        assertNotNull(db.artistTable.findByIdDirect("DIFFERS_FROM_TRUTH"))
        assertEquals(setOf("s2"), linksOf("DIFFERS_FROM_TRUTH").map { it.songId }.toSet())
    }

    @Test
    fun majorityImageMergedMinorityFlagged() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertSong("s3")
        insertArtist("ROW_A", "Getty", thumbnail = "T")
        insertArtist("ROW_B", "Getty", thumbnail = "T")
        insertArtist("ROW_C", "Getty", thumbnail = "OTHER")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")
        insertLink("s3", "ROW_C")

        // Truth row is newly created: the strict-majority picture is the reference
        val result = run(
            FakeResolver(mapOf("Getty" to resolved("TRUTH_ID", "Getty", "SEARCH_THUMB")))
        )

        assertEquals(2, result.merged)
        assertEquals(1, result.flagged)
        assertNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNull(db.artistTable.findByIdDirect("ROW_B"))
        assertNotNull(db.artistTable.findByIdDirect("ROW_C"))
    }

    @Test
    fun tiedDifferentImagesAreAllFlagged() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("IMG_A", "Getty", thumbnail = "T")
        insertArtist("IMG_B", "Getty", thumbnail = "OTHER")
        insertLink("s1", "IMG_A")
        insertLink("s2", "IMG_B")

        // No truth row, two different pictures, no strict majority: doubt
        val result = run(
            FakeResolver(mapOf("Getty" to resolved("TRUTH_ID", "Getty", "SEARCH_THUMB")))
        )

        assertEquals(0, result.merged)
        assertEquals(2, result.flagged)
        assertNotNull(db.artistTable.findByIdDirect("IMG_A"))
        assertNotNull(db.artistTable.findByIdDirect("IMG_B"))
        assertEquals(setOf("s1"), linksOf("IMG_A").map { it.songId }.toSet())
        assertEquals(setOf("s2"), linksOf("IMG_B").map { it.songId }.toSet())
    }

    @Test
    fun nullImageInConflictingGroupIsFlagged() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertSong("s3")
        insertArtist("ROW_A", "Ice", thumbnail = "T")
        insertArtist("ROW_B", "Ice", thumbnail = "OTHER")
        insertArtist("ROW_C", "Ice")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")
        insertLink("s3", "ROW_C")

        // Two different pictures plus one row without any: doubt for everyone
        val result = run(
            FakeResolver(mapOf("Ice" to resolved("TRUTH_ID", "Ice", "SEARCH_THUMB")))
        )

        assertEquals(0, result.merged)
        assertEquals(3, result.flagged)
        assertNotNull(db.artistTable.findByIdDirect("ROW_C"))
        assertEquals(setOf("s3"), linksOf("ROW_C").map { it.songId }.toSet())
    }

    @Test
    fun rowWithoutImageIsMergedAnyway() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("NO_IMG", "Aoi")
        insertArtist("TRUTH_ID", "Aoi", thumbnail = "T")
        insertLink("s1", "NO_IMG")
        insertLink("s2", "TRUTH_ID")

        // The truth row carries a picture, the row does not: no contradicting
        // evidence, the row is neutral and merges
        val result = run(FakeResolver(mapOf("Aoi" to resolved("TRUTH_ID", "Aoi", "T"))))

        assertEquals(1, result.merged)
        assertEquals(0, result.flagged)
        assertNull(db.artistTable.findByIdDirect("NO_IMG"))
    }

    // ---- follow / dislike carry-over ----

    @Test
    fun followMovesToTruthRowWhenMergedRowIsFollowed() = runTest {
        insertSong("s1")
        insertArtist("FOLLOWED_TOPIC", "Camellia", bookmarkedAt = 1234L)
        insertArtist("TRUTH_ID", "Camellia")
        insertLink("s1", "FOLLOWED_TOPIC")

        val result = run(FakeResolver(mapOf("Camellia" to resolved("TRUTH_ID", "Camellia"))))

        assertEquals(1, result.merged)
        assertEquals(1234L, db.artistTable.findByIdDirect("TRUTH_ID")?.bookmarkedAt)
    }

    @Test
    fun dislikeMovesToTruthRowWhenMergedRowIsDisliked() = runTest {
        insertSong("s1")
        insertArtist("DISLIKED_ROW", "Ado", dislikedAt = 5678L)
        insertArtist("TRUTH_ID", "Ado")
        insertLink("s1", "DISLIKED_ROW")

        run(FakeResolver(mapOf("Ado" to resolved("TRUTH_ID", "Ado"))))

        assertEquals(5678L, db.artistTable.findByIdDirect("TRUTH_ID")?.dislikedAt)
    }

    @Test
    fun existingFollowOnTruthIsNotClobbered() = runTest {
        insertSong("s1")
        insertArtist("TOPIC_A", "Camellia", bookmarkedAt = 111L)
        insertArtist("TRUTH_ID", "Camellia", bookmarkedAt = 999L)
        insertLink("s1", "TOPIC_A")

        run(FakeResolver(mapOf("Camellia" to resolved("TRUTH_ID", "Camellia"))))

        assertEquals(999L, db.artistTable.findByIdDirect("TRUTH_ID")?.bookmarkedAt)
    }

    // ---- LOCAL_ARTIST_ exclusion ----

    @Test
    fun localArtistRowsAreNeverMerged() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("LOCAL_ARTIST_x1", "uma")
        insertArtist("TOPIC_A", "uma", thumbnail = "T")
        insertArtist("TRUTH_ID", "uma", thumbnail = "T")
        insertLink("s1", "LOCAL_ARTIST_x1")
        insertLink("s2", "TOPIC_A")

        val result = run(FakeResolver(mapOf("uma" to resolved("TRUTH_ID", "uma", "T"))))

        assertEquals(1, result.merged)
        assertEquals(1, result.localKept)
        // The local row keeps its id and its links
        assertNotNull(db.artistTable.findByIdDirect("LOCAL_ARTIST_x1"))
        assertEquals(setOf("s1"), linksOf("LOCAL_ARTIST_x1").map { it.songId }.toSet())
    }

    @Test
    fun groupOfOnlyLocalRowsIsSkippedWithoutNetwork() = runTest {
        insertArtist("LOCAL_ARTIST_a", "solo")
        insertArtist("LOCAL_ARTIST_b", "solo")

        val resolver = FakeResolver(mapOf("solo" to resolved("ID", "solo")))
        val result = run(resolver)

        assertEquals(0, result.merged)
        assertEquals(0, resolver.calls)
        assertNotNull(db.artistTable.findByIdDirect("LOCAL_ARTIST_a"))
        assertNotNull(db.artistTable.findByIdDirect("LOCAL_ARTIST_b"))
    }

    // ---- skip / safety cases ----

    @Test
    fun noSearchMatchLeavesGroupUntouched() = runTest {
        insertSong("s1")
        insertArtist("A", "Nobody")
        insertArtist("B", "Nobody")
        insertLink("s1", "A")

        val result = run(FakeResolver(emptyMap()))

        assertEquals(1, result.skipped)
        assertEquals(0, result.merged)
        assertNotNull(db.artistTable.findByIdDirect("A"))
        assertNotNull(db.artistTable.findByIdDirect("B"))
        assertEquals(setOf("s1"), linksOf("A").map { it.songId }.toSet())
    }

    @Test
    fun offlineSkipsResolution() = runTest {
        insertArtist("A", "Aoi")
        insertArtist("B", "Aoi")

        val resolver = FakeResolver(mapOf("Aoi" to resolved("ID", "Aoi")))
        val result = run(resolver, online = false)

        assertEquals(1, result.skipped)
        assertEquals(0, resolver.calls)
        assertNotNull(db.artistTable.findByIdDirect("A"))
        assertNotNull(db.artistTable.findByIdDirect("B"))
    }

    @Test
    fun failedGroupIsSkippedAndOthersContinue() = runTest {
        insertArtist("A1", "Aaa"); insertArtist("A2", "Aaa")
        insertSong("s1")
        insertArtist("B1", "Bbb")
        insertArtist("TRUTH_B", "Bbb")
        insertLink("s1", "B1")

        val resolver = FakeResolver(
            results = mapOf("Bbb" to resolved("TRUTH_B", "Bbb")),
            failures = setOf("Aaa"),
        )
        val result = run(resolver)

        assertEquals(1, result.merged)
        assertEquals(1, result.skipped)
        assertNotNull(db.artistTable.findByIdDirect("A1"))
        assertNotNull(db.artistTable.findByIdDirect("A2"))
    }

    @Test
    fun modifiedGroupNamesAreUntouchable() = runTest {
        insertArtist("A", "modified:Custom")
        insertArtist("B", "modified:Custom")

        val resolver = FakeResolver(mapOf("modified:Custom" to resolved("T", "Custom")))
        val result = run(resolver)

        assertEquals(1, result.skipped)
        assertEquals(0, resolver.calls)
        assertNotNull(db.artistTable.findByIdDirect("A"))
        assertNotNull(db.artistTable.findByIdDirect("B"))
    }

    // ---- idempotency ----

    @Test
    fun secondRunIsIdempotent() = runTest {
        insertSong("s1")
        insertArtist("TOPIC_A", "Camellia", thumbnail = "T")
        insertArtist("TRUTH_ID", "Camellia", thumbnail = "T")
        insertLink("s1", "TOPIC_A")

        val first = run(FakeResolver(mapOf("Camellia" to resolved("TRUTH_ID", "Camellia", "T"))))
        assertEquals(1, first.merged)

        val second = run(FakeResolver(mapOf("Camellia" to resolved("TRUTH_ID", "Camellia", "T"))))
        // The survivor carries the custom name: no same-name group remains
        assertEquals(0, second.groups)
        assertEquals(0, second.merged)
    }

    // ---- skip store (remembered groups) ----

    @Test
    fun rememberedUnchangedGroupIsSkippedWithoutNetwork() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("IMG_A", "Getty", thumbnail = "T")
        insertArtist("IMG_B", "Getty", thumbnail = "OTHER")
        insertLink("s1", "IMG_A")
        insertLink("s2", "IMG_B")

        // Last launch judged this exact composition and flagged its rows
        val store = FakeSkipStore(mapOf("Getty" to setOf("IMG_A", "IMG_B")))
        val resolver = FakeResolver(mapOf("Getty" to resolved("TRUTH_ID", "Getty", "SEARCH_THUMB")))
        val result = run(resolver, skipStore = store)

        // Composition unchanged since the verdict: no search, no merge
        assertEquals(1, result.deferred)
        assertEquals(0, result.resolved)
        assertEquals(0, result.merged)
        assertEquals(0, resolver.calls)
        assertNotNull(db.artistTable.findByIdDirect("IMG_A"))
        assertNotNull(db.artistTable.findByIdDirect("IMG_B"))
    }

    @Test
    fun rememberedChangedGroupIsRechecked() = runTest {
        insertSong("s1")
        insertArtist("ROW_A", "Getty", thumbnail = "T")
        insertArtist("ROW_B", "Getty", thumbnail = "T")
        insertLink("s1", "ROW_A")

        // Remembered with only ROW_A; ROW_B appeared since
        val store = FakeSkipStore(mapOf("Getty" to setOf("ROW_A")))
        val resolver = FakeResolver(mapOf("Getty" to resolved("ROW_A", "Getty", "SEARCH_THUMB")))
        val result = run(resolver, skipStore = store)

        // The composition changed: the group is searched again and resolved
        assertEquals(0, result.deferred)
        assertEquals(1, result.resolved)
        assertEquals(1, result.merged)
        assertNull(db.artistTable.findByIdDirect("ROW_B"))
        // Fully resolved: the entry is forgotten
        assertNull(store.memory["Getty"])
    }

    @Test
    fun flaggedGroupKeepsItsRemainingComposition() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("ROW_A", "Ice", thumbnail = "T")
        insertArtist("ROW_B", "Ice", thumbnail = "OTHER")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")

        val store = FakeSkipStore()
        val result = run(
            FakeResolver(mapOf("Ice" to resolved("TRUTH_ID", "Ice", "SEARCH_THUMB"))),
            skipStore = store,
        )

        // Tie: both rows stay, and their composition is remembered so the next
        // launch skips the network search
        assertEquals(2, result.flagged)
        assertEquals(0, result.merged)
        assertEquals(setOf("ROW_A", "ROW_B"), store.memory["Ice"])
    }

    // ---- image rule: conflict with a pictured truth row ----

    @Test
    fun nullImageRowIsFlaggedInConflictingGroupWhenTruthHasImage() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertSong("s3")
        insertArtist("TRUTH_ID", "Ice", thumbnail = "T")
        insertArtist("ROW_B", "Ice", thumbnail = "OTHER")
        insertArtist("ROW_C", "Ice")
        insertLink("s1", "TRUTH_ID")
        insertLink("s2", "ROW_B")
        insertLink("s3", "ROW_C")

        // The truth row carries a picture, the group conflicts (two distinct
        // stored images): ROW_B contradicts the truth picture, ROW_C has none
        // to check against - both are doubt, neither may merge (D4: "flag si
        // le groupe est en conflit", whatever the truth row carries).
        val result = run(FakeResolver(mapOf("Ice" to resolved("TRUTH_ID", "Ice"))))

        assertEquals(0, result.merged)
        assertEquals(2, result.flagged)
        assertNotNull(db.artistTable.findByIdDirect("ROW_B"))
        assertNotNull(db.artistTable.findByIdDirect("ROW_C"))
    }

    // ---- truth row already in the DB under another name ----

    @Test
    fun truthRowUnderADifferentNameIsPinnedAndMerged() = runTest {
        insertSong("s1")
        insertSong("s2")
        // The real channel row exists outside the same-name group, under its
        // real name: it is the truth by id, not by group membership.
        insertArtist("TRUTH_ID", "Deadmau5 Official")
        insertArtist("DUP_A", "deadmau5", thumbnail = "T")
        insertArtist("DUP_B", "deadmau5", thumbnail = "T")
        insertLink("s1", "DUP_A")
        insertLink("s2", "DUP_B")

        val result = run(FakeResolver(mapOf("deadmau5" to resolved("TRUTH_ID", "deadmau5"))))

        // Both duplicate rows merge into the out-of-group truth row...
        assertEquals(2, result.merged)
        assertNull(db.artistTable.findByIdDirect("DUP_A"))
        assertNull(db.artistTable.findByIdDirect("DUP_B"))
        assertEquals(setOf("s1", "s2"), linksOf("TRUTH_ID").map { it.songId }.toSet())
        // ...and the truth row is pinned to the canonical modified: name, so
        // downstream sweeps (StreamResolver fetch, DbCleanup) protect it.
        assertEquals(
            "modified:deadmau5",
            db.artistTable.findByIdDirect("TRUTH_ID")?.name,
        )
    }

    // ---- resolver selection (strict exact match only) ----

    private fun artistItem(name: String, browseId: String) = Innertube.ArtistItem(
        info = Innertube.Info(
            name = name,
            endpoint = NavigationEndpoint.Endpoint.Browse(browseId = browseId),
        ),
        subscribersCountText = null,
        thumbnail = null,
    )

    @Test
    fun pickExactArtistMatchReturnsTheExactNameMatchNotTheTopResult() {
        val items = listOf(
            artistItem("Camellia Official", "UC_TOP"), // the top result: not an exact match
            artistItem("camellia", "UC_EXACT"),        // case-insensitive exact match
        )

        val picked = pickExactArtistMatch(items, "Camellia")

        assertEquals("UC_EXACT", picked?.info?.endpoint?.browseId)
    }

    @Test
    fun pickExactArtistMatchReturnsNullWhenNothingMatchesExactly() {
        // Similar-but-not-exact names (homonyms, suffixed topic pages): doubt
        // -> null -> the group is skipped, never merged onto a foreign artist.
        val items = listOf(
            artistItem("Camellia Official", "UC_TOP"),
            artistItem("Camellia - Topic", "UC_TOPIC"),
            artistItem("Camellia X", "UC_OTHER"),
        )

        assertNull(pickExactArtistMatch(items, "Camellia"))
        assertNull(pickExactArtistMatch(null, "Camellia"))
    }
}
