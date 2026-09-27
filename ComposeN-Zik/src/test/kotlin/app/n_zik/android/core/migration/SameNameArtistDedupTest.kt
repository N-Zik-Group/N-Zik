package app.n_zik.android.core.migration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.models.SongArtistMap
import app.n_zik.android.core.database.DatabaseInitializer
import app.n_zik.android.core.maintenance.DedupGroupStatus
import app.n_zik.android.core.maintenance.DedupSkipReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.requests.artistPage
import it.fast4x.innertube.requests.searchPage
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 * network (a fallback-judged composition also carries the selected songs —
 * a changed selection re-opens the group), and offline / no-match / failed
 * groups are left for the next launch (no per-launch cap — searches are only
 * rate-limited). When the
 * primary artist search finds nothing, the song-based fallback proves each
 * row by its own songs: a merge needs the unanimous agreement of every row on
 * one single browse id; a non-unanimous or unprovable group is skipped with
 * its composition remembered, a pure network failure is not remembered, and
 * the fallback is never tried when the artist search matched.
 *
 * Runs the real core against an in-memory Room database with a fake resolver,
 * so the merge primitives (de-duplicate link, re-link, follow carry-over,
 * delete) are exercised end to end, and pins the real
 * [InnertubeSongTruthResolver]'s throw-vs-null semantics against mocked
 * Innertube statics (a failed search/artist-page Result propagates; an empty
 * page, a missing byline or a mismatched page name answer null).
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
        initial: Map<String, RememberedComposition> = emptyMap(),
    ) : SkipStore {
        val memory = initial.toMutableMap()

        override fun rememberedComposition(groupName: String): RememberedComposition? = memory[groupName]

        override fun remember(groupName: String, composition: RememberedComposition?) {
            if (composition == null) memory.remove(groupName) else memory[groupName] = composition
        }
    }

    /**
     * Fake [SongTruthResolver] answering from a fixed row-id -> candidate table;
     * [calls] records which rows were proven, [requestedSongs] the songs each
     * row was given (empty rows get an empty list).
     */
    private class FakeSongTruthResolver(
        private val candidates: Map<String, ResolvedArtist?>,
        private val failures: Set<String> = emptySet(),
    ) : SongTruthResolver {
        val calls = mutableListOf<String>()
        val requestedSongs = mutableMapOf<String, List<String>>()

        override suspend fun resolveSongs(groupName: String, row: Artist, songs: List<Song>): ResolvedArtist? {
            calls.add(row.id)
            requestedSongs[row.id] = songs.map { it.id }
            if (row.id in failures) throw IllegalStateException("simulated network failure")
            return candidates[row.id]
        }
    }

    private suspend fun run(
        resolver: ArtistSearchResolver,
        songTruthResolver: SongTruthResolver = FakeSongTruthResolver(emptyMap()),
        online: Boolean = true,
        skipStore: SkipStore? = null,
    ): DedupResult = SameNameArtistDedup.runDedup(
        artistTable = db.artistTable,
        mapTable = db.songArtistMapTable,
        resolver = resolver,
        songTruthResolver = songTruthResolver,
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

        // Last launch judged this exact composition (by the primary search:
        // rows only) and flagged its rows
        val store = FakeSkipStore(mapOf("Getty" to RememberedComposition(setOf("IMG_A", "IMG_B"))))
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
        val store = FakeSkipStore(mapOf("Getty" to RememberedComposition(setOf("ROW_A"))))
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
        // launch skips the network search (primary verdict: rows only)
        assertEquals(2, result.flagged)
        assertEquals(0, result.merged)
        assertEquals(RememberedComposition(setOf("ROW_A", "ROW_B")), store.memory["Ice"])
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

    @Test
    fun primaryArtistSearchAnswersNullOnAFailedResult() = runTest {
        mockInnertubeStatics()
        stubArtistSearch(Result.failure(RuntimeException("network down")))

        // A failed PRIMARY artist search answers null WITHOUT throwing: that
        // is the contract that routes the group into the song fallback — a
        // regression to propagation would silently disable the fallback on
        // flaky networks, undetected
        assertNull(InnertubeArtistSearchResolver().searchArtist("Darby"))
    }

    // ---- song-based fallback (the artist search found nothing) ----

    @Test
    fun songFallbackUnanimousWithForeignBrowseIdCreatesAndMergesIntoIt() = runTest {
        // Titled songs (not placeholders, whose blank title would be filtered
        // out): the per-row hand-off to the resolver is observable below
        db.songTable.upsert(Song(id = "s1", title = "Song One", durationText = null, thumbnailUrl = null))
        db.songTable.upsert(Song(id = "s2", title = "Song Two", durationText = null, thumbnailUrl = null))
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")

        // The unanimous truth is a THIRD browse id, outside the group's rows:
        // the canonical row is created and both rows merge into it.
        val songResolver = FakeSongTruthResolver(
            mapOf(
                "ROW_A" to resolved("Z", "Darby", "PAGE_T"),
                "ROW_B" to resolved("Z", "Darby", "PAGE_T"),
            )
        )
        val result = run(FakeResolver(emptyMap()), songResolver)

        assertEquals(1, result.songResolved)
        assertEquals(0, result.resolved)
        assertEquals(2, result.merged)
        assertNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNull(db.artistTable.findByIdDirect("ROW_B"))
        val canonical = db.artistTable.findByIdDirect("Z")
        assertNotNull(canonical)
        assertEquals("modified:Darby", canonical?.name)
        assertEquals("PAGE_T", canonical?.thumbnailUrl)
        assertEquals(setOf("s1", "s2"), linksOf("Z").map { it.songId }.toSet())
        // Every row was proven with ITS OWN songs only — a regression that
        // proved every row with the first row's songs cannot pass
        assertEquals(listOf("s1"), songResolver.requestedSongs["ROW_A"])
        assertEquals(listOf("s2"), songResolver.requestedSongs["ROW_B"])
        assertEquals(listOf("ROW_A", "ROW_B"), songResolver.calls)
    }

    @Test
    fun songFallbackUnanimousWithTruthRowKeepsIt() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("ROW_A", "Eagle", thumbnail = "T")
        insertArtist("ROW_B", "Eagle", thumbnail = "T")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")

        // The unanimous browse id IS one of the rows: that row is kept as
        // truth (pinned), the other merges into it.
        val songResolver = FakeSongTruthResolver(
            mapOf(
                "ROW_A" to resolved("ROW_B", "Eagle"),
                "ROW_B" to resolved("ROW_B", "Eagle"),
            )
        )
        val result = run(FakeResolver(emptyMap()), songResolver)

        assertEquals(0, result.resolved)
        assertEquals(1, result.songResolved)
        assertEquals(1, result.merged)
        assertNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNotNull(db.artistTable.findByIdDirect("ROW_B"))
        assertEquals("modified:Eagle", db.artistTable.findByIdDirect("ROW_B")?.name)
        assertEquals(setOf("s1", "s2"), linksOf("ROW_B").map { it.songId }.toSet())
    }

    @Test
    fun songFallbackDisagreementSkipsAndRemembers() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertSong("s3")
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")
        insertArtist("ROW_C", "Darby", thumbnail = "T")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")
        insertLink("s3", "ROW_C")

        // Homonyms: the second row's songs point to a DIFFERENT artist page
        val songResolver = FakeSongTruthResolver(
            mapOf(
                "ROW_A" to resolved("Z", "Darby"),
                "ROW_B" to resolved("W", "Darby"),
                "ROW_C" to resolved("Z", "Darby"),
            )
        )
        val store = FakeSkipStore()
        val result = run(FakeResolver(emptyMap()), songResolver, skipStore = store)

        // Disagreement: nothing moves, nothing merges, the composition is
        // remembered so the next launch skips the group without network
        assertEquals(0, result.merged)
        assertEquals(0, result.songResolved)
        assertEquals(1, result.skipped)
        assertNotNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNotNull(db.artistTable.findByIdDirect("ROW_B"))
        assertNotNull(db.artistTable.findByIdDirect("ROW_C"))
        assertEquals(setOf("s1"), linksOf("ROW_A").map { it.songId }.toSet())
        assertEquals(setOf("s2"), linksOf("ROW_B").map { it.songId }.toSet())
        assertEquals(setOf("s3"), linksOf("ROW_C").map { it.songId }.toSet())
        // Fallback verdict: rows + selected songs (the placeholder songs are
        // filtered out, so the selection is empty)
        assertEquals(RememberedComposition(setOf("ROW_A", "ROW_B", "ROW_C"), emptySet()), store.memory["Darby"])
        // The rows are proven one by one: once ROW_B disagrees with ROW_A, the
        // proof stops and ROW_C's songs never reach the resolver
        assertEquals(listOf("ROW_A", "ROW_B"), songResolver.calls)
    }

    @Test
    fun songFallbackRowWithoutCandidateSkipsAndRemembers() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")

        // ROW_B's songs are unattributed UGC (no byline entry names the group)
        // -> no candidate for that row -> the whole group is skipped
        val songResolver = FakeSongTruthResolver(mapOf("ROW_A" to resolved("Z", "Darby")))
        val store = FakeSkipStore()
        val result = run(FakeResolver(emptyMap()), songResolver, skipStore = store)

        assertEquals(0, result.merged)
        assertEquals(0, result.songResolved)
        assertEquals(1, result.skipped)
        assertNotNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNotNull(db.artistTable.findByIdDirect("ROW_B"))
        // Fallback verdict: rows + selected songs (the placeholder songs are
        // filtered out, so the selection is empty)
        assertEquals(RememberedComposition(setOf("ROW_A", "ROW_B"), emptySet()), store.memory["Darby"])
    }

    @Test
    fun songFallbackRowNullAfterAgreementSkipsAndRemembers() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertSong("s3")
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")
        insertArtist("ROW_C", "Darby", thumbnail = "T")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")
        insertLink("s3", "ROW_C")

        // A and B agree on the same browse id; C's songs are unattributed UGC
        // -> no candidate for C -> the whole group is skipped
        val songResolver = FakeSongTruthResolver(
            mapOf(
                "ROW_A" to resolved("Z", "Darby"),
                "ROW_B" to resolved("Z", "Darby"),
            )
        )
        val store = FakeSkipStore()
        val result = run(FakeResolver(emptyMap()), songResolver, skipStore = store)

        assertEquals(0, result.merged)
        assertEquals(0, result.songResolved)
        assertEquals(1, result.skipped)
        // All three rows reached the resolver: unlike the disagreement shape
        // (which breaks at the disagreeing row), C is resolved (to null)
        // before the loop stops
        assertEquals(listOf("ROW_A", "ROW_B", "ROW_C"), songResolver.calls)
        assertEquals(
            RememberedComposition(setOf("ROW_A", "ROW_B", "ROW_C"), emptySet()),
            store.memory["Darby"],
        )
    }

    @Test
    fun rowWithoutAnyUsableSongSkipsTheGroupAndRemembers() = runTest {
        // Neither row is linked to any song: ROW_A's fallback list is empty,
        // it can never be proven, and the whole group is skipped with its
        // composition remembered
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")

        val songResolver = FakeSongTruthResolver(mapOf("ROW_B" to resolved("Z", "Darby")))
        val store = FakeSkipStore()
        val result = run(FakeResolver(emptyMap()), songResolver, skipStore = store)

        assertEquals(0, result.merged)
        assertEquals(0, result.songResolved)
        assertEquals(1, result.skipped)
        assertNotNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNotNull(db.artistTable.findByIdDirect("ROW_B"))
        // Fallback verdict: rows + selected songs (the placeholder songs are
        // filtered out, so the selection is empty)
        assertEquals(RememberedComposition(setOf("ROW_A", "ROW_B"), emptySet()), store.memory["Darby"])
    }

    @Test
    fun songFallbackIsNotTriedWhenTheArtistSearchMatches() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("ROW_A", "Aoi", thumbnail = "T")
        insertArtist("ROW_B", "Aoi", thumbnail = "T")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")

        val songResolver = FakeSongTruthResolver(mapOf("ROW_A" to resolved("Z", "Aoi")))
        val result = run(FakeResolver(mapOf("Aoi" to resolved("ROW_A", "Aoi", "T"))), songResolver)

        // The primary resolver is the arbiter: the fallback never runs
        assertEquals(1, result.resolved)
        assertEquals(0, result.songResolved)
        assertEquals(0, songResolver.calls.size)
    }

    @Test
    fun songFallbackOfflineMakesNoCalls() = runTest {
        insertArtist("ROW_A", "Darby")
        insertArtist("ROW_B", "Darby")

        val songResolver = FakeSongTruthResolver(mapOf("ROW_A" to resolved("Z", "Darby")))
        val result = run(FakeResolver(emptyMap()), songResolver, online = false)

        assertEquals(1, result.skipped)
        assertEquals(0, result.songResolved)
        assertEquals(0, songResolver.calls.size)
    }

    @Test
    fun songFallbackNetworkFailureIsSkippedWithoutMemory() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")

        // A pure network failure is skipped WITHOUT being remembered: the
        // group retries on the next launch (no entry in the skip store)
        val songResolver = FakeSongTruthResolver(
            candidates = mapOf("ROW_A" to resolved("Z", "Darby")),
            failures = setOf("ROW_B"),
        )
        val store = FakeSkipStore()
        val result = run(FakeResolver(emptyMap()), songResolver, skipStore = store)

        assertEquals(1, result.skipped)
        assertEquals(0, result.songResolved)
        assertNull(store.memory["Darby"])
        assertNotNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNotNull(db.artistTable.findByIdDirect("ROW_B"))
    }

    @Test
    fun rememberedSongFallbackCompositionIsDeferredWithoutNetworkCalls() = runTest {
        // Titled songs: the fallback selection picks them up, so the
        // remembered songs (non-empty) actually participate in the comparison
        db.songTable.upsert(Song(id = "s1", title = "Song One", durationText = null, thumbnailUrl = null))
        db.songTable.upsert(Song(id = "s2", title = "Song Two", durationText = null, thumbnailUrl = null))
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")

        // A previous launch judged this group by the fallback and remembered
        // the rows + the selected songs — both parts are unchanged: the
        // songs are not re-searched
        val store = FakeSkipStore(
            mapOf("Darby" to RememberedComposition(setOf("ROW_A", "ROW_B"), setOf("s1", "s2")))
        )
        val songResolver = FakeSongTruthResolver(mapOf("ROW_A" to resolved("Z", "Darby")))
        val result = run(FakeResolver(emptyMap()), songResolver, skipStore = store)

        assertEquals(1, result.deferred)
        assertEquals(0, result.skipped)
        assertEquals(0, result.songResolved)
        assertEquals(0, songResolver.calls.size)
    }

    @Test
    fun rememberedSongFallbackFailureWithNewRowRerunsAndMerges() = runTest {
        // ROW_C appeared since the previous verdict (no songs in the old
        // selection): the composition changed, so the fallback runs again —
        // and this time the rows agree
        val store = FakeSkipStore(mapOf("Darby" to RememberedComposition(setOf("ROW_A", "ROW_B"), emptySet())))
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")
        insertArtist("ROW_C", "Darby", thumbnail = "T")

        val songResolver = FakeSongTruthResolver(
            mapOf(
                "ROW_A" to resolved("Z", "Darby"),
                "ROW_B" to resolved("Z", "Darby"),
                "ROW_C" to resolved("Z", "Darby"),
            )
        )
        val result = run(FakeResolver(emptyMap()), songResolver, skipStore = store)

        assertEquals(0, result.deferred)
        assertEquals(1, result.songResolved)
        assertEquals(3, result.merged)
        assertNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNull(db.artistTable.findByIdDirect("ROW_B"))
        assertNull(db.artistTable.findByIdDirect("ROW_C"))
        assertNotNull(db.artistTable.findByIdDirect("Z"))
        // Fully resolved: the remembered entry is forgotten
        assertNull(store.memory["Darby"])
    }

    @Test
    fun rememberedSongFallbackCompositionWithChangedSongsIsRejudged() = runTest {
        // A previous launch judged this group by the fallback and remembered
        // the rows + the selected songs. The rows are unchanged but the
        // selection changed (ROW_A's first qualifying song is a different one
        // now): new evidence -> the fallback runs again
        db.songTable.upsert(Song(id = "NEW_SONG", title = "New First Song", durationText = null, thumbnailUrl = null))
        db.songTable.upsert(Song(id = "s2", title = "Song Two", durationText = null, thumbnailUrl = null))
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")
        insertLink("NEW_SONG", "ROW_A")
        insertLink("s2", "ROW_B")

        val store = FakeSkipStore(
            mapOf("Darby" to RememberedComposition(setOf("ROW_A", "ROW_B"), setOf("OLD_SONG", "s2")))
        )
        val songResolver = FakeSongTruthResolver(
            mapOf(
                "ROW_A" to resolved("Z", "Darby"),
                "ROW_B" to resolved("Z", "Darby"),
            )
        )
        val result = run(FakeResolver(emptyMap()), songResolver, skipStore = store)

        // Not deferred: the selected songs changed, the fallback re-runs and
        // the rows agree -> merged
        assertEquals(0, result.deferred)
        assertEquals(1, result.songResolved)
        assertEquals(2, result.merged)
        assertEquals(listOf("ROW_A", "ROW_B"), songResolver.calls)
        assertNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNull(db.artistTable.findByIdDirect("ROW_B"))
        // Fully resolved: the remembered entry is forgotten
        assertNull(store.memory["Darby"])
    }

    @Test
    fun primaryRememberedGroupKeepsRowsOnlySemanticsWhenSongsChange() = runTest {
        // A group judged by the PRIMARY artist search remembers rows only
        // (songs == null): a changed song selection is not new evidence for
        // that verdict -> still deferred, no re-search
        db.songTable.upsert(Song(id = "NEW_SONG", title = "New First Song", durationText = null, thumbnailUrl = null))
        insertArtist("ROW_A", "Getty", thumbnail = "T")
        insertArtist("ROW_B", "Getty", thumbnail = "T")
        insertLink("NEW_SONG", "ROW_A")

        val store = FakeSkipStore(mapOf("Getty" to RememberedComposition(setOf("ROW_A", "ROW_B"))))
        val resolver = FakeResolver(mapOf("Getty" to resolved("TRUTH_ID", "Getty", "SEARCH_THUMB")))
        val result = run(resolver, skipStore = store)

        assertEquals(1, result.deferred)
        assertEquals(0, result.resolved)
        assertEquals(0, resolver.calls)
        assertNotNull(db.artistTable.findByIdDirect("ROW_A"))
        assertNotNull(db.artistTable.findByIdDirect("ROW_B"))
    }

    @Test
    fun fallbackCompositionWithALocalRowCarriesOnlyTheYouTubeRowsSongs() = runTest {
        // Titled songs: the local row's song must NOT leak into the remembered
        // songs (the songs part tracks the YouTube rows only), while the local
        // row IS part of the rows part
        db.songTable.upsert(Song(id = "LOCAL_SONG", title = "Local Song", durationText = null, thumbnailUrl = null))
        db.songTable.upsert(Song(id = "s1", title = "Song One", durationText = null, thumbnailUrl = null))
        db.songTable.upsert(Song(id = "s2", title = "Song Two", durationText = null, thumbnailUrl = null))
        insertArtist("LOCAL_ARTIST_x1", "uma")
        insertArtist("ROW_A", "uma", thumbnail = "T")
        insertArtist("ROW_B", "uma", thumbnail = "T")
        insertLink("LOCAL_SONG", "LOCAL_ARTIST_x1")
        insertLink("s1", "ROW_A")
        insertLink("s2", "ROW_B")

        val store = FakeSkipStore()
        val songResolver = FakeSongTruthResolver(emptyMap())
        val first = run(FakeResolver(emptyMap()), songResolver, skipStore = store)

        // Fallback verdict: rows = ALL rows (the local one included), songs =
        // the YouTube rows' selected songs only — LOCAL_SONG is not in it
        assertEquals(1, first.skipped)
        assertEquals(1, first.localKept)
        assertEquals(
            RememberedComposition(setOf("LOCAL_ARTIST_x1", "ROW_A", "ROW_B"), setOf("s1", "s2")),
            store.memory["uma"],
        )

        // Second run, unchanged composition: deferred, with zero new
        // song-truth-resolver calls (pins the split at BOTH the remember site
        // and the defer check)
        val callsAfterFirstRun = songResolver.calls.size
        val second = run(FakeResolver(emptyMap()), songResolver, skipStore = store)
        assertEquals(1, second.deferred)
        assertEquals(0, second.skipped)
        assertEquals(0, second.songResolved)
        assertEquals(callsAfterFirstRun, songResolver.calls.size)
    }

    @Test
    fun songFallbackTriesAtMostThreeSongsWithNonBlankCleanedTitles() = runTest {
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")
        // The filter is the non-blank CLEANED title only — the isYoutubeSong
        // flag (never true in production) must not exclude songs: LOCAL_1 is
        // a non-YouTube-source song and still reaches the resolver. The
        // blank-title and prefix-only titles are filtered (both clean to ""),
        // and the cap of 3 in Song.ROWID order drops YT3.
        db.songTable.upsert(Song(id = "LOCAL_1", title = "Local", durationText = null, thumbnailUrl = null))
        db.songTable.upsert(Song(id = "YT_BLANK", title = "   ", durationText = null, thumbnailUrl = null))
        db.songTable.upsert(Song(id = "E_PREFIX_ONLY", title = "e:", durationText = null, thumbnailUrl = null))
        db.songTable.upsert(Song(id = "YT1", title = "Song One", durationText = null, thumbnailUrl = null))
        db.songTable.upsert(Song(id = "YT2", title = "Song Two", durationText = null, thumbnailUrl = null))
        db.songTable.upsert(Song(id = "YT3", title = "Song Three", durationText = null, thumbnailUrl = null))
        insertLink("LOCAL_1", "ROW_A")
        insertLink("YT_BLANK", "ROW_A")
        insertLink("E_PREFIX_ONLY", "ROW_A")
        insertLink("YT1", "ROW_A")
        insertLink("YT2", "ROW_A")
        insertLink("YT3", "ROW_A")

        val songResolver = FakeSongTruthResolver(emptyMap())
        run(FakeResolver(emptyMap()), songResolver)

        // Song.ROWID order kept, blank/prefix-only cleaned titles filtered out,
        // non-YouTube-source songs included, capped at 3
        assertEquals(listOf("LOCAL_1", "YT1", "YT2"), songResolver.requestedSongs["ROW_A"])
        // ROW_A yields no candidate, so the proof stops: ROW_B's songs are
        // never even fetched for the resolver
        assertNull(songResolver.requestedSongs["ROW_B"])
    }

    // ---- resolver helpers (same-song confirmation, byline, page name) ----

    private fun song(id: String, title: String, thumbnail: String? = null) =
        Song(id = id, title = title, durationText = null, thumbnailUrl = thumbnail, isYoutubeSong = true)

    private fun songItem(
        videoId: String,
        name: String,
        thumb: String?,
        authors: List<Pair<String, String>> = emptyList(),
    ) = Innertube.SongItem(
        info = Innertube.Info(
            name = name,
            endpoint = NavigationEndpoint.Endpoint.Watch(videoId = videoId),
        ),
        authors = authors.map { (name, browseId) ->
            Innertube.Info(
                name = name,
                endpoint = NavigationEndpoint.Endpoint.Browse(browseId = browseId),
            )
        },
        album = null,
        durationText = null,
        thumbnail = thumb?.let { Thumbnail(url = it, height = null, width = null) },
    )

    @Test
    fun pickConfirmedSongConfirmsByPerfectVideoIdMatch() {
        // A perfect video id match confirms even when the stored title differs
        // (the byline is still evaluated by the caller for the navigation)
        val song = song("abc123", "Some Other Stored Title", "https://i.ytimg.com/vi/abc123/mqdefault.jpg")
        val item = songItem("abc123", "Lost Hearts", "https://i.ytimg.com/vi/abc123/hqdefault.jpg")

        val match = pickConfirmedSong(song, listOf(item))

        assertEquals("abc123", match?.info?.endpoint?.videoId)
    }

    @Test
    fun pickConfirmedSongRequiresExactTitleAndSameImage() {
        val song = song("abc123", "Lost Hearts", "https://i.ytimg.com/vi/abc123/mqdefault.jpg")
        // Different video, similar-but-not-exact title, no shared image
        val nearMiss = songItem("def456", "Lost Heartss", "https://i.ytimg.com/vi/def456/mqdefault.jpg")
        // Different video, exact title (case-insensitive) but a different image
        val wrongImage = songItem("def456", "lost hearts", "https://i.ytimg.com/vi/def456/mqdefault.jpg")
        // Different video, exact title AND the full thumbnail URL is identical
        // (thumbnails are deterministic per video, so this is the same picture)
        val sameThumb = songItem("def456", "lost hearts", "https://i.ytimg.com/vi/abc123/mqdefault.jpg")

        assertNull(pickConfirmedSong(song, listOf(nearMiss)))
        assertNull(pickConfirmedSong(song, listOf(wrongImage)))
        assertNotNull(pickConfirmedSong(song, listOf(sameThumb)))
        // Never by title similarity alone
        assertNull(pickConfirmedSong(song, emptyList()))
    }

    @Test
    fun pickConfirmedSongWithNullLocalThumbnailNeedsTitleOnly() {
        // Local thumbnail null -> title + byline suffice (the byline is
        // checked by the caller); the image can no longer contradict
        val song = song("abc123", "Lost Hearts")
        val item = songItem("def456", "lost hearts", "https://i.ytimg.com/vi/def456/mqdefault.jpg")

        assertNotNull(pickConfirmedSong(song, listOf(item)))
    }

    @Test
    fun sameImageComparesTheEmbeddedVideoIdOrTheFullUrl() {
        // Same video id embedded in differently sized crops
        assertTrue(
            sameImage(
                "https://i.ytimg.com/vi/abc12345678/mqdefault.jpg",
                "https://i.ytimg.com/vi/abc12345678/maxresdefault.jpg",
            )
        )
        // Different video ids: different pictures
        assertFalse(
            sameImage(
                "https://i.ytimg.com/vi/abc12345678/mqdefault.jpg",
                "https://i.ytimg.com/vi/def12345678/mqdefault.jpg",
            )
        )
        // No embedded video id on either side: the full URLs are compared
        assertTrue(sameImage("https://example.com/img/1.jpg", "https://example.com/img/1.jpg"))
        assertFalse(sameImage("https://example.com/img/1.jpg", "https://example.com/img/2.jpg"))
        // Exactly one side embeds a video id: the full-URL comparison fires and
        // is false (the pictures are then different videos)
        assertFalse(
            sameImage(
                "https://i.ytimg.com/vi/abc12345678/mqdefault.jpg",
                "https://cdn.example.com/thumb/xyz.jpg",
            )
        )
        // A null local thumbnail is neutral; a null result thumbnail is not
        assertTrue(sameImage(null, "https://i.ytimg.com/vi/abc12345678/mqdefault.jpg"))
        assertFalse(sameImage("https://i.ytimg.com/vi/abc12345678/mqdefault.jpg", null))
    }

    @Test
    fun bylineBrowseIdRequiresAByNameEntryOfTheGroup() {
        // The byline names the group (case-insensitive): its browse id is the
        // navigation handle to the artist page
        val withGroup = songItem("abc123", "Lost Hearts", null, authors = listOf("darby" to "Z", "Other" to "W"))
        assertEquals("Z", bylineBrowseId(withGroup, "Darby"))
        // A byline that only names other artists (unattributed UGC) yields
        // no candidate
        val withoutGroup = songItem("abc123", "Lost Hearts", null, authors = listOf("Other" to "W"))
        assertNull(bylineBrowseId(withoutGroup, "Darby"))
        assertNull(bylineBrowseId(songItem("abc123", "Lost Hearts", null), "Darby"))
    }

    @Test
    fun artistPageMatchesGroupOnlyOnTheExactName() {
        // The page name is the arbiter: topic pages and homonym channels are
        // rejected, case differences are tolerated
        assertTrue(artistPageMatchesGroup("Darby", "Darby"))
        assertTrue(artistPageMatchesGroup("darby", "Darby"))
        assertFalse(artistPageMatchesGroup("Darby - Topic", "Darby"))
        assertFalse(artistPageMatchesGroup("Darby Official", "Darby"))
        assertFalse(artistPageMatchesGroup(null, "Darby"))
    }

    // ---- the real InnertubeSongTruthResolver (throw-vs-null semantics) ----

    private val truthRow = Artist(id = "ROW_A", name = "Darby")

    private val truthSong = song("abc123", "Lost Hearts", "https://i.ytimg.com/vi/abc123/mqdefault.jpg")

    /** A search result confirmed as [truthSong] (perfect video id match), whose byline names the group. */
    private fun confirmedSongItem(browseId: String) =
        songItem("abc123", "Lost Hearts", "https://i.ytimg.com/vi/abc123/mqdefault.jpg", authors = listOf("Darby" to browseId))

    private fun artistInfoPage(name: String?, thumb: String? = null) = Innertube.ArtistInfoPage(
        name = name,
        description = null,
        subscriberCountText = null,
        thumbnail = thumb?.let { Thumbnail(url = it, height = null, width = null) },
        shuffleEndpoint = null,
        radioEndpoint = null,
        songs = null,
        songsEndpoint = null,
        albums = null,
        albumsEndpoint = null,
        singles = null,
        singlesEndpoint = null,
        playlists = null,
    )

    private fun mockInnertubeStatics() {
        mockkStatic("it.fast4x.innertube.requests.SearchPageKt")
        mockkStatic("it.fast4x.innertube.requests.ArtistInfoPageKt")
    }

    private fun stubSongSearch(page: Result<Innertube.ItemsPage<Innertube.SongItem>?>) {
        coEvery {
            Innertube.searchPage<Innertube.SongItem>(
                query = any(),
                params = any(),
                fromMusicShelfRendererContent = any(),
            )
        } returns resultAnswerFor(page)
    }

    private fun stubArtistPage(result: Result<Innertube.ArtistInfoPage>) {
        coEvery { Innertube.artistPage(any()) } returns resultAnswerFor(result)
    }

    private fun stubArtistSearch(page: Result<Innertube.ItemsPage<Innertube.ArtistItem>?>) {
        coEvery {
            Innertube.searchPage<Innertube.ArtistItem>(
                query = any(),
                params = any(),
                fromMusicShelfRendererContent = any(),
            )
        } returns resultAnswerFor(page)
    }

    /**
     * MockK's suspend-mocking support unwraps one `Result` level from a
     * `returns` value (sugar meant for `returns Result.failure(x)` ==
     * `throws x`); since `Innertube.searchPage` / `Innertube.artistPage`
     * themselves declare a `Result<T>?` return type, the intended value must
     * be wrapped in one extra `Result.success(...)` layer to survive that
     * single unwrap (same trick as VideoOrSongInfoScreenLoadOffMainTest).
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T> resultAnswerFor(value: Result<T>): Result<T> =
        Result.success(value) as Result<T>

    @Test
    fun realResolverPropagatesAFailedSearchResult() = runTest {
        mockInnertubeStatics()
        stubSongSearch(Result.failure(RuntimeException("network down")))

        var thrown: RuntimeException? = null
        try {
            InnertubeSongTruthResolver().resolveSongs("Darby", truthRow, listOf(truthSong))
        } catch (e: RuntimeException) {
            thrown = e
        }

        // A failed search Result is a pure network failure: it PROPAGATES so
        // the group is skipped without being remembered (retried next launch)
        assertNotNull(thrown)
        assertEquals("network down", thrown?.message)
        coVerify(exactly = 0) { Innertube.artistPage(any()) }
    }

    @Test
    fun realResolverAnswersNullForAnEmptySearchPage() = runTest {
        mockInnertubeStatics()
        stubSongSearch(Result.success(Innertube.ItemsPage<Innertube.SongItem>(items = emptyList(), continuation = null)))

        // A successful page with no items is a legitimate "no candidate"
        // answer for the song: no exception, no navigation
        assertNull(InnertubeSongTruthResolver().resolveSongs("Darby", truthRow, listOf(truthSong)))
        coVerify(exactly = 0) { Innertube.artistPage(any()) }
    }

    @Test
    fun realResolverPropagatesAFailedArtistPageResult() = runTest {
        mockInnertubeStatics()
        stubSongSearch(Result.success(Innertube.ItemsPage<Innertube.SongItem>(items = listOf(confirmedSongItem("Z")), continuation = null)))
        stubArtistPage(Result.failure(RuntimeException("page down")))

        var thrown: RuntimeException? = null
        try {
            InnertubeSongTruthResolver().resolveSongs("Darby", truthRow, listOf(truthSong))
        } catch (e: RuntimeException) {
            thrown = e
        }

        // The song was confirmed and the byline was found, but the artist
        // page fetch failed: that is also a pure network failure -> PROPAGATES
        assertNotNull(thrown)
        assertEquals("page down", thrown?.message)
    }

    @Test
    fun realResolverMakesNoNetworkCallWithoutSongs() = runTest {
        mockInnertubeStatics()

        // No song to try: the row is unprovable without a single network call
        assertNull(InnertubeSongTruthResolver().resolveSongs("Darby", truthRow, emptyList()))
        coVerify(exactly = 0) {
            Innertube.searchPage<Innertube.SongItem>(query = any(), params = any(), fromMusicShelfRendererContent = any())
        }
        coVerify(exactly = 0) { Innertube.artistPage(any()) }
    }

    @Test
    fun realResolverYieldsNoCandidateWhenTheArtistPageNameDiffers() = runTest {
        mockInnertubeStatics()
        stubSongSearch(Result.success(Innertube.ItemsPage<Innertube.SongItem>(items = listOf(confirmedSongItem("Z")), continuation = null)))
        stubArtistPage(Result.success(artistInfoPage("Darby - Topic")))

        // The confirmed song's byline leads to a topic page, not the group:
        // no candidate, no exception
        assertNull(InnertubeSongTruthResolver().resolveSongs("Darby", truthRow, listOf(truthSong)))
        coVerify(exactly = 1) { Innertube.artistPage("Z") }
    }

    @Test
    fun realResolverYieldsTheArtistPageAsCandidate() = runTest {
        mockInnertubeStatics()
        stubSongSearch(Result.success(Innertube.ItemsPage<Innertube.SongItem>(items = listOf(confirmedSongItem("Z")), continuation = null)))
        stubArtistPage(Result.success(artistInfoPage("darby", "PAGE_T")))

        // The candidate carries the artist page's truth (browse id + name + image)
        val candidate = InnertubeSongTruthResolver().resolveSongs("Darby", truthRow, listOf(truthSong))

        assertEquals(resolved("Z", "darby", "PAGE_T"), candidate)
        // Query = cleaned title + group name (the title does not carry the
        // group name); params = the Song filter
        coVerify(exactly = 1) {
            Innertube.searchPage<Innertube.SongItem>(
                query = "Lost Hearts Darby",
                params = Innertube.SearchFilter.Song.value,
                fromMusicShelfRendererContent = any(),
            )
        }
    }

    @Test
    fun realResolverRetriesWithTheNextSongWhenTheFirstPageHasNoConfirmedItem() = runTest {
        mockInnertubeStatics()
        val secondSong = song("def456", "e:Darby - Live", "https://i.ytimg.com/vi/def456/mqdefault.jpg")
        // The first song's page is successful but EMPTY: it yields no candidate
        // for the first song, and the proof continues with the second one
        coEvery {
            Innertube.searchPage<Innertube.SongItem>(
                query = any(),
                params = any(),
                fromMusicShelfRendererContent = any(),
            )
        } returns resultAnswerFor(
            Result.success(Innertube.ItemsPage<Innertube.SongItem>(items = emptyList(), continuation = null))
        ) andThen resultAnswerFor(
            Result.success(
                Innertube.ItemsPage<Innertube.SongItem>(
                    items = listOf(
                        songItem(
                            "def456", "Darby - Live",
                            "https://i.ytimg.com/vi/def456/mqdefault.jpg",
                            authors = listOf("Darby" to "W"),
                        )
                    ),
                    continuation = null,
                )
            )
        )
        stubArtistPage(Result.success(artistInfoPage("Darby", "PAGE_T")))

        // The candidate comes from the SECOND song: its byline entry (browse
        // id "W") is the navigation handle, used exactly once
        val candidate = InnertubeSongTruthResolver().resolveSongs("Darby", truthRow, listOf(truthSong, secondSong))

        assertEquals(resolved("W", "Darby", "PAGE_T"), candidate)
        coVerify(exactly = 1) { Innertube.artistPage("W") }
        // Query: cleaned title + group name when the title does not carry the
        // group name (first song); cleaned title alone when it does —
        // "e:Darby - Live" cleans to "Darby - Live", which carries it.
        // Params are always the Song filter.
        coVerify(exactly = 1) {
            Innertube.searchPage<Innertube.SongItem>(
                query = "Lost Hearts Darby",
                params = Innertube.SearchFilter.Song.value,
                fromMusicShelfRendererContent = any(),
            )
        }
        coVerify(exactly = 1) {
            Innertube.searchPage<Innertube.SongItem>(
                query = "Darby - Live",
                params = Innertube.SearchFilter.Song.value,
                fromMusicShelfRendererContent = any(),
            )
        }
    }

    @Test
    fun realResolverStopsAfterTheFirstConfirmedSong() = runTest {
        mockInnertubeStatics()
        val secondSong = song("def456", "Second Song", "https://i.ytimg.com/vi/def456/mqdefault.jpg")
        stubSongSearch(
            Result.success(Innertube.ItemsPage<Innertube.SongItem>(items = listOf(confirmedSongItem("Z")), continuation = null))
        )
        stubArtistPage(Result.success(artistInfoPage("Darby")))

        val candidate = InnertubeSongTruthResolver().resolveSongs("Darby", truthRow, listOf(truthSong, secondSong))

        // The first song already yields a candidate: the second is never
        // searched, the artist page is navigated exactly once
        assertEquals(resolved("Z", "Darby"), candidate)
        coVerify(exactly = 1) {
            Innertube.searchPage<Innertube.SongItem>(query = any(), params = any(), fromMusicShelfRendererContent = any())
        }
        coVerify(exactly = 1) { Innertube.artistPage("Z") }
    }

    @Test
    fun realResolverYieldsNoCandidateWhenThePageHoldsOtherSongs() = runTest {
        mockInnertubeStatics()
        // A non-empty page, but NO item is the same song: different video id,
        // remix-style title, no shared image -> nothing is confirmed
        val remix = songItem(
            "def456", "Lost Hearts (Remix)",
            "https://i.ytimg.com/vi/def456/mqdefault.jpg",
            authors = listOf("Darby" to "Z"),
        )
        stubSongSearch(Result.success(Innertube.ItemsPage<Innertube.SongItem>(items = listOf(remix), continuation = null)))

        assertNull(InnertubeSongTruthResolver().resolveSongs("Darby", truthRow, listOf(truthSong)))
        coVerify(exactly = 0) { Innertube.artistPage(any()) }
    }

    @Test
    fun realResolverYieldsNoCandidateWhenTheBylineNamesOnlyOtherArtists() = runTest {
        mockInnertubeStatics()
        // The song IS confirmed (perfect video id match), but its byline names
        // only OTHER artists (unattributed UGC): no group-named entry -> no
        // navigation handle -> no candidate, no artist page fetch
        val otherByline = songItem(
            "abc123", "Lost Hearts",
            "https://i.ytimg.com/vi/abc123/mqdefault.jpg",
            authors = listOf("Some Other Artist" to "W"),
        )
        stubSongSearch(Result.success(Innertube.ItemsPage<Innertube.SongItem>(items = listOf(otherByline), continuation = null)))

        assertNull(InnertubeSongTruthResolver().resolveSongs("Darby", truthRow, listOf(truthSong)))
        coVerify(exactly = 0) { Innertube.artistPage(any()) }
    }

    // ---- per-group records (maintenance persistence, spec-maintenance-dialog) ----

    @Test
    fun resolvedGroupRecordCarriesMergedAndFlaggedCounts() = runTest {
        insertSong("s1")
        insertSong("s2")
        insertArtist("MATCHES_TRUTH", "Getty", thumbnail = "T")
        insertArtist("DIFFERS_FROM_TRUTH", "Getty", thumbnail = "OTHER")
        insertArtist("TRUTH_ID", "Getty", thumbnail = "T")
        insertLink("s1", "MATCHES_TRUTH")
        insertLink("s2", "DIFFERS_FROM_TRUTH")

        val result = run(FakeResolver(mapOf("Getty" to resolved("TRUTH_ID", "Getty", "SEARCH_THUMB"))))

        val record = result.records.single()
        assertEquals("Getty", record.name)
        assertEquals(DedupGroupStatus.RESOLVED, record.status)
        assertEquals(1, record.mergedRows)
        assertEquals(1, record.flaggedRows)
        assertNull(record.skipReason)
    }

    @Test
    fun songResolvedGroupRecordCarriesMergedCount() = runTest {
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")

        val songResolver = FakeSongTruthResolver(
            mapOf(
                "ROW_A" to resolved("Z", "Darby"),
                "ROW_B" to resolved("Z", "Darby"),
            )
        )
        val result = run(FakeResolver(emptyMap()), songResolver)

        assertEquals(1, result.songResolved)
        val record = result.records.single()
        assertEquals("Darby", record.name)
        assertEquals(DedupGroupStatus.SONG_RESOLVED, record.status)
        assertEquals(2, record.mergedRows)
        assertEquals(0, record.flaggedRows)
        assertNull(record.skipReason)
    }

    @Test
    fun customNameGroupRecordCarriesTheCustomNameReason() = runTest {
        insertArtist("ROW_A", "${MODIFIED_PREFIX}Camellia")
        insertArtist("ROW_B", "${MODIFIED_PREFIX}Camellia")

        val result = run(FakeResolver(emptyMap()))

        val record = result.records.single()
        assertEquals("${MODIFIED_PREFIX}Camellia", record.name)
        assertEquals(DedupGroupStatus.SKIPPED, record.status)
        assertEquals(DedupSkipReason.CUSTOM_NAME, record.skipReason)
    }

    @Test
    fun offlineGroupRecordCarriesTheOfflineReason() = runTest {
        insertArtist("ROW_A", "Darby")
        insertArtist("ROW_B", "Darby")

        val result = run(FakeResolver(emptyMap()), online = false)

        val record = result.records.single()
        assertEquals("Darby", record.name)
        assertEquals(DedupGroupStatus.SKIPPED, record.status)
        assertEquals(DedupSkipReason.OFFLINE, record.skipReason)
    }

    @Test
    fun deferredGroupRecordHasNoReasonNorCounters() = runTest {
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")

        val store = FakeSkipStore(mapOf("Darby" to RememberedComposition(setOf("ROW_A", "ROW_B"))))
        val result = run(FakeResolver(emptyMap()), skipStore = store)

        assertEquals(1, result.deferred)
        val record = result.records.single()
        assertEquals("Darby", record.name)
        assertEquals(DedupGroupStatus.DEFERRED, record.status)
        assertNull(record.skipReason)
        assertEquals(0, record.mergedRows)
        assertEquals(0, record.flaggedRows)
    }

    @Test
    fun unprovableRowRecordCarriesTheNoUnanimousReason() = runTest {
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")

        // ROW_A proves a candidate, ROW_B no song can prove (absent from the
        // fake's table): no unanimity -> skipped with its composition remembered
        val songResolver = FakeSongTruthResolver(mapOf("ROW_A" to resolved("Z", "Darby")))
        val store = FakeSkipStore()
        val result = run(FakeResolver(emptyMap()), songResolver, skipStore = store)

        assertEquals(1, result.skipped)
        val record = result.records.single()
        assertEquals("Darby", record.name)
        assertEquals(DedupGroupStatus.SKIPPED, record.status)
        assertEquals(DedupSkipReason.NO_UNANIMOUS_CANDIDATE, record.skipReason)
    }

    @Test
    fun networkFailureGroupRecordCarriesTheNetworkFailureReason() = runTest {
        insertArtist("ROW_A", "Darby", thumbnail = "T")
        insertArtist("ROW_B", "Darby", thumbnail = "T")

        val result = run(FakeResolver(emptyMap(), failures = setOf("Darby")))

        val record = result.records.single()
        assertEquals("Darby", record.name)
        assertEquals(DedupGroupStatus.SKIPPED, record.status)
        assertEquals(DedupSkipReason.NETWORK_FAILURE, record.skipReason)
    }

    @Test
    fun runWithoutDedupableGroupsYieldsNoRecords() = runTest {
        // A lone row is not a group: nothing is judged, nothing is recorded
        insertArtist("ALONE", "Camellia")

        val result = run(FakeResolver(emptyMap()))

        assertEquals(0, result.groups)
        assertTrue(result.records.isEmpty())
    }

    @Test
    fun oneRecordPerJudgedGroupCoversEveryTerminalBranch() = runTest {
        // One group per terminal branch of the sweep loop, in a single run:
        // the records must hold one entry per judged group with the matching
        // status/reason (the groups' iteration order is not part of the contract)
        insertArtist("ALPHA_A", "Alpha", thumbnail = "T")
        insertArtist("ALPHA_B", "Alpha", thumbnail = "T")
        insertArtist("BETA_A", "Beta", thumbnail = "T")
        insertArtist("BETA_B", "Beta", thumbnail = "T")
        insertArtist("GAMMA_A", "Gamma", thumbnail = "T")
        insertArtist("GAMMA_B", "Gamma", thumbnail = "T")
        insertArtist("DELTA_A", "Delta", thumbnail = "T")
        insertArtist("DELTA_B", "Delta", thumbnail = "T")
        insertArtist("ECHO_A", "${MODIFIED_PREFIX}Echo")
        insertArtist("ECHO_B", "${MODIFIED_PREFIX}Echo")
        insertArtist("FOXTROT_A", "Foxtrot", thumbnail = "T")
        insertArtist("FOXTROT_B", "Foxtrot", thumbnail = "T")

        val store = FakeSkipStore(mapOf("Foxtrot" to RememberedComposition(setOf("FOXTROT_A", "FOXTROT_B"))))
        val songResolver = FakeSongTruthResolver(
            mapOf(
                "BETA_A" to resolved("Z_B", "Beta"),
                "BETA_B" to resolved("Z_B", "Beta"),
                "GAMMA_A" to resolved("Z_G", "Gamma"),
            )
        )
        val result = run(
            FakeResolver(
                mapOf("Alpha" to resolved("Z_A", "Alpha", "T")),
                failures = setOf("Delta"),
            ),
            songResolver,
            skipStore = store,
        )

        // Six judged groups, one record each
        assertEquals(6, result.records.size)
        assertEquals(
            setOf("Alpha", "Beta", "Gamma", "Delta", "${MODIFIED_PREFIX}Echo", "Foxtrot"),
            result.records.map { it.name }.toSet(),
        )
        val byName = result.records.associateBy { it.name }
        assertEquals(DedupGroupStatus.RESOLVED, byName["Alpha"]?.status)
        assertEquals(2, byName["Alpha"]?.mergedRows)
        assertEquals(DedupGroupStatus.SONG_RESOLVED, byName["Beta"]?.status)
        assertEquals(2, byName["Beta"]?.mergedRows)
        assertEquals(DedupSkipReason.NO_UNANIMOUS_CANDIDATE, byName["Gamma"]?.skipReason)
        assertEquals(DedupSkipReason.NETWORK_FAILURE, byName["Delta"]?.skipReason)
        assertEquals(DedupSkipReason.CUSTOM_NAME, byName["${MODIFIED_PREFIX}Echo"]?.skipReason)
        assertEquals(DedupGroupStatus.DEFERRED, byName["Foxtrot"]?.status)
    }
}
