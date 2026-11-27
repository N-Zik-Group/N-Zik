package app.n_zik.android.components.menu.album

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.models.NavigationEndpoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlbumMenuArtistCreditsTest {
    private val officialAlbumArtist = Artist("UCJ97ksybdciBBv7xsFSK6HA", "Grand Theft Auto VI")
    private val firstTrackArtist = Artist("UCJustice", "Justice")

    @Test
    fun `online single card already provides an actionable artist credit`() {
        val artist = Artist("UCojO2c5IzksdA6xt1QCN74g", "Martina Vince")
        val credits = listOf(Innertube.Info(artist.name, NavigationEndpoint.Endpoint.Browse(browseId = artist.id)))
        assertEquals(
            listOf(artist),
            selectAlbumMenuArtists(null, credits.toAlbumCreditArtists(), emptyList()) { null },
        )
    }

    @Test
    fun `clicking the listed credit keeps a browse ID replaced after the menu opened`() {
        val replacement = officialAlbumArtist.copy(id = "${MODIFIED_PREFIX}UCReplacement")
        val resolved = selectRefreshedAlbumMenuArtist(officialAlbumArtist.id, listOf(replacement, firstTrackArtist)) { id ->
            officialAlbumArtist.id.takeIf { id == replacement.id }
        }
        assertEquals(replacement, resolved)
        assertEquals(null, selectRefreshedAlbumMenuArtist(officialAlbumArtist.id, listOf(firstTrackArtist)) { null })
    }

    @Test
    fun `official header wins over a saved track performer`() {
        val artists = selectAlbumMenuArtists(
            storedCredits = firstTrackArtist.name,
            headerArtists = listOf(officialAlbumArtist),
            knownArtists = listOf(firstTrackArtist),
            previousId = { null }
        )

        assertEquals(listOf(officialAlbumArtist), artists)
    }

    @Test
    fun `a manually changed ID for the official artist wins over the header`() {
        val replacement = officialAlbumArtist.copy(id = "${MODIFIED_PREFIX}UCReplacement")
        val artists = selectAlbumMenuArtists(
            storedCredits = officialAlbumArtist.name,
            headerArtists = listOf(officialAlbumArtist),
            knownArtists = listOf(officialAlbumArtist, replacement, firstTrackArtist),
            previousId = { id -> officialAlbumArtist.id.takeIf { id == replacement.id } }
        )

        assertEquals(listOf(replacement), artists)
    }

    @Test
    fun `equivalent original and replacement credits collapse to a single edited artist`() {
        val replacement = officialAlbumArtist.copy(id = "${MODIFIED_PREFIX}UCReplacement")
        val artists = selectAlbumMenuArtists(
            storedCredits = officialAlbumArtist.name,
            headerArtists = listOf(officialAlbumArtist, replacement, firstTrackArtist),
            knownArtists = listOf(officialAlbumArtist, replacement, firstTrackArtist),
            previousId = { id -> officialAlbumArtist.id.takeIf { id == replacement.id } },
        )
        assertEquals(listOf(replacement, firstTrackArtist), artists)
    }

    @Test
    fun `a manually edited album credit resolves its own artist`() {
        val artists = selectAlbumMenuArtists(
            storedCredits = "${MODIFIED_PREFIX}${firstTrackArtist.name}",
            headerArtists = listOf(officialAlbumArtist),
            knownArtists = listOf(officialAlbumArtist, firstTrackArtist),
            previousId = { null }
        )

        assertEquals(listOf(firstTrackArtist), artists)
    }

    @Test
    fun `an ambiguous offline credit leaves navigation unavailable`() {
        val artists = selectAlbumMenuArtists(
            storedCredits = officialAlbumArtist.name,
            headerArtists = emptyList(),
            knownArtists = listOf(officialAlbumArtist, officialAlbumArtist.copy(id = "UCHomonym")),
            previousId = { null }
        )

        assertEquals(emptyList<Artist>(), artists)
    }

    @Test
    fun `a renamed artist covers the original header credit without suppressing unlinked credits`() {
        val renamed = officialAlbumArtist.copy(name = "${MODIFIED_PREFIX}My artist label")
        val header = listOf(officialAlbumArtist)
        val storedCredits = "${officialAlbumArtist.name}, Unlinked Artist"
        val linked = selectAlbumMenuArtists(storedCredits, header, listOf(renamed)) { null }

        assertEquals(listOf(renamed), linked)
        assertEquals(listOf("Unlinked Artist"), albumMenuArtistFallbackNames(storedCredits, header, linked))
    }

    @Test
    fun `a custom credit can still be searched when the source header uses the same name`() {
        val customCredits = "${MODIFIED_PREFIX}${officialAlbumArtist.name}"
        assertEquals(
            listOf(officialAlbumArtist.name),
            albumMenuArtistFallbackNames(customCredits, listOf(officialAlbumArtist), emptyList()),
        )
    }

    @Test
    fun `unlinked album credits omit duplicate and unknown artists`() {
        assertEquals(
            listOf("Alice", "Bob"),
            albumMenuArtistFallbackNames(
                storedCredits = "Alice, alice, Unknown Artist, Bob, Justice",
                headerArtists = emptyList(),
                linkedArtists = listOf(firstTrackArtist),
            ),
        )
    }

    @Test
    fun `online album menu uses the replacement metadata while retaining its saved state`() {
        val stored = Album(
            id = "${MODIFIED_PREFIX}MPREbNew",
            title = "Old title",
            thumbnailUrl = "old-cover",
            authorsText = "Old credit",
            year = "2010",
            shareUrl = "old-link",
            bookmarkedAt = 123L,
            dislikedAt = 456L,
            isYoutubeAlbum = true,
            position = 3,
        )
        val display = onlineAlbumMenuDisplay(stored, "New title", "new-cover", "2020", "New credit")
        assertEquals(
            stored.copy(title = "New title", thumbnailUrl = "new-cover", year = "2020", authorsText = "New credit"),
            display,
        )
    }

    @Test
    fun `online album menu retains manually edited title and cover`() {
        val manualCredits = "${MODIFIED_PREFIX}My credit"
        val stored = Album(
            id = "MPREbAlbum",
            title = "${MODIFIED_PREFIX}My title",
            thumbnailUrl = "${MODIFIED_PREFIX}My cover",
            authorsText = manualCredits,
            year = "2010",
        )
        val display = onlineAlbumMenuDisplay(stored, "Source title", "source-cover", "2020", manualCredits)
        assertEquals(stored.copy(year = "2020"), display)
    }
}
