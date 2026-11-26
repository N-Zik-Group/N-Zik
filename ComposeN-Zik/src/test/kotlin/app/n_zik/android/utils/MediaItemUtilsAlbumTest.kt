package app.n_zik.android.utils

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.models.Album
import app.n_zik.android.core.database.AlbumTable
import app.n_zik.android.core.database.Database
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [albumTitleOrDb] — the album source the Discord RPC relies on
 * (metadata first, then the DB). Also pins the manager-side contract: a blank or
 * literal "null" result must end up null after the `.takeIf` guard, so the
 * template renderer applies the localized "Unknown Album" fallback for
 * `{album.name}` instead of showing an empty value or the literal "null".
 */
class MediaItemUtilsAlbumTest {

    private val albumTable = mockk<AlbumTable>()

    private fun mediaItem(albumTitle: String?): MediaItem =
        MediaItem.Builder()
            .setMediaId("song1")
            .setMediaMetadata(MediaMetadata.Builder().setAlbumTitle(albumTitle).build())
            .build()

    /** The exact expression DiscordPresenceManager applies to the utility result. */
    private suspend fun MediaItem.albumNameForRpc(): String? =
        albumTitleOrDb().takeIf { it.isNotBlank() && it != "null" }

    @BeforeEach
    fun setUp() {
        mockkObject(Database)
        every { Database.albumTable } returns albumTable
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(Database)
    }

    @Test
    fun restoringAnExplicitReleaseUsesOnlyItsOwnManualOverride() {
        val chosen = Album("modified:MPREchosen")
        assertEquals(chosen, editedPlaybackAlbumFor("MPREoriginal", listOf(chosen)) { "MPREoriginal" })
        assertNull(editedPlaybackAlbumFor("MPREdeluxe", listOf(chosen)) { "MPREoriginal" })
    }

    @Test
    fun `explicit release survives exact track metadata and stored alternatives`() {
        val release = "MPREb_yde4zc2mIz2"
        val deluxe = "MPREb_TINpB57piQG"
        val single = "MPREb_kg3qpcMo2vB"

        assertEquals(release, selectOnlinePlaybackAlbumId(release, single, listOf(deluxe, single)))
        assertEquals(single, selectOnlinePlaybackAlbumId(null, single, listOf(deluxe, release)))
        assertNull(selectOnlinePlaybackAlbumId(null, null, listOf(deluxe, release)))
    }

    @Test
    fun `manual album link and unlink are respected when there is no explicit release`() {
        val album = "MPREb_TINpB57piQG"
        val modified = "modified:MPREb_yde4zc2mIz2"
        val unlinked = "LOCAL_ALBUM_local"

        assertEquals(modified, selectOnlinePlaybackAlbumId(null, album, listOf(album, modified)))
        assertEquals(unlinked, selectOnlinePlaybackAlbumId(null, album, listOf(album, unlinked)))
    }

    @Test
    fun `album from metadata wins when present`() = runTest {
        val item = mediaItem("My Album")

        assertEquals("My Album", item.albumTitleOrDb())
        assertEquals("My Album", item.albumNameForRpc())
        // Fork prefixes on the metadata album are stripped, same as title/artist.
        assertEquals("My Album", mediaItem("e:My Album").albumNameForRpc())
    }

    @Test
    fun `album from DB is used when metadata is missing`() = runTest {
        coEvery { albumTable.findBySongIdDirect("song1") } returns Album(id = "album1", title = "DB Album")

        assertEquals("DB Album", mediaItem(null).albumTitleOrDb())
        assertEquals("DB Album", mediaItem(null).albumNameForRpc())
    }

    @Test
    fun `literal null metadata falls through to the DB`() = runTest {
        coEvery { albumTable.findBySongIdDirect("song1") } returns Album(id = "album1", title = "DB Album")

        assertEquals("DB Album", mediaItem("null").albumNameForRpc())
    }

    @Test
    fun `unknown album resolves to null so the renderer applies the localized fallback`() = runTest {
        coEvery { albumTable.findBySongIdDirect("song1") } returns null

        assertNull(mediaItem(null).albumNameForRpc())
        assertNull(mediaItem("").albumNameForRpc())
        assertNull(mediaItem("null").albumNameForRpc())
    }

    @Test
    fun `resolveAlbumTitle prefers the metadata value`() {
        assertEquals("My Album", resolveAlbumTitle("My Album", Album(id = "a1", title = "DB Album")))
    }

    @Test
    fun `resolveAlbumTitle falls back to the DB record when the metadata is missing`() {
        assertEquals("DB Album", resolveAlbumTitle(null, Album(id = "a1", title = "DB Album")))
        assertEquals("DB Album", resolveAlbumTitle("", Album(id = "a1", title = "DB Album")))
        assertEquals("DB Album", resolveAlbumTitle("null", Album(id = "a1", title = "DB Album")))
    }

    @Test
    fun `resolveAlbumTitle returns null when both sources are unknown`() {
        assertNull(resolveAlbumTitle(null, null))
        assertNull(resolveAlbumTitle("null", null))
        assertNull(resolveAlbumTitle(null, Album(id = "a1", title = null)))
        assertNull(resolveAlbumTitle(null, Album(id = "a1", title = "null")))
    }

    @Test
    fun `resolveAlbumTitle strips the fork prefixes from both sources`() {
        assertEquals("My Album", resolveAlbumTitle("e:My Album", null))
        assertEquals("DB Album", resolveAlbumTitle(null, Album(id = "a1", title = "modified:DB Album")))
        assertEquals("DB Album", resolveAlbumTitle("", Album(id = "a1", title = "pinned:DB Album")))
    }
}
