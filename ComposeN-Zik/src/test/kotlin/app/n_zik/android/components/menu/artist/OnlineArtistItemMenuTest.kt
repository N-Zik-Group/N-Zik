package app.n_zik.android.components.menu.artist

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.models.Artist
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import app.n_zik.innertube.models.Thumbnail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OnlineArtistItemMenuTest {
    @Test
    fun `changing the destination rejects stale title and artwork until exact metadata is known`() {
        val original = remoteArtist("UCOriginal", "Original artist", "original-art")
        val target = remoteArtist("UCSelected", "Selected artist", "selected-art")
        val destination = "${MODIFIED_PREFIX}UCSelected"
        val migrated = Artist(id = destination, name = "Original artist", thumbnailUrl = "original-art")

        val pending = onlineArtistMenuEntity(destination, migrated, original, original)
        assertEquals(destination, pending.id)
        assertNull(pending.name)
        assertNull(pending.thumbnailUrl)

        val resolved = onlineArtistMenuEntity(destination, migrated, original, target)
        assertEquals("Selected artist", resolved.name)
        assertEquals("selected-art", resolved.thumbnailUrl)
        assertEquals("UCSelected", onlineArtistMenuBrowseId(resolved.id))
    }

    @Test
    fun `explicit title and artwork survive a destination change`() {
        val destination = "${MODIFIED_PREFIX}UCSelected"
        val stored = Artist(
            id = destination,
            name = "${MODIFIED_PREFIX}My artist",
            thumbnailUrl = "${MODIFIED_PREFIX}custom-art",
            bookmarkedAt = 1L,
        )
        val original = remoteArtist("UCOriginal", "Original artist", "original-art")
        val target = remoteArtist("UCSelected", "Selected artist", "selected-art")

        assertEquals(stored, onlineArtistMenuEntity(destination, stored, original, target))
    }

    @Test
    fun `an unlinked artist can retain stored metadata without a YouTube destination`() {
        val stored = Artist(id = "LOCAL_ARTIST_unlinked", name = "My artist")
        val original = remoteArtist("UCOriginal", "Original artist", "original-art")

        assertEquals(stored, onlineArtistMenuEntity(stored.id, stored, original, null))
        assertNull(onlineArtistMenuBrowseId(stored.id))
    }

    private fun remoteArtist(browseId: String, name: String, artwork: String) = Innertube.ArtistItem(
        info = Innertube.Info(name, NavigationEndpoint.Endpoint.Browse(browseId = browseId)),
        subscribersCountText = null,
        thumbnail = Thumbnail(artwork, null, null),
    )
}
