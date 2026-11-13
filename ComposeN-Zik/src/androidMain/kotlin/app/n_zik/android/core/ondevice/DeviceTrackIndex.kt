package app.n_zik.android.core.ondevice

import app.n_zik.android.MODIFIED_PREFIX
import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Format
import app.n_zik.android.models.Song
import app.n_zik.android.models.SongArtistMap
import app.n_zik.android.utils.splitArtistNames
import java.util.Locale
import java.util.UUID

internal data class IndexedDeviceTrack(
    val song: Song,
    val format: Format,
    val album: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val year: String? = null,
)

internal fun deviceTag(value: String?): String? =
    value?.trim()?.takeUnless { it.isEmpty() || it.equals("<unknown>", ignoreCase = true) }

internal fun rememberDeviceGroupOriginalName(id: String, name: String?) {
    if (name.isNullOrBlank()) return
    val prefs = deviceGroupNamePreferences()
    val key = "deviceGroupOriginalName:$id"
    if (!prefs.contains(key)) prefs.edit().putString(key, name).apply()
}

private fun deviceGroupNamePreferences() = appContext().let { profileDataPrefs(it, getActiveProfile(it)) }

private fun deviceGroupKey(name: String?): String? = deviceTag(name)?.lowercase(Locale.ROOT)

private fun Format.hasSameSourceAs(other: Format): Boolean =
    mimeType == other.mimeType && bitrate == other.bitrate &&
        contentLength == other.contentLength && lastModified == other.lastModified

internal suspend fun Database.indexDeviceTracks(
    tracks: List<IndexedDeviceTrack>,
    isCurrentOwner: () -> Boolean,
): Pair<DeviceLibrarySnapshot, List<Format>> {
    if (tracks.isEmpty()) return DeviceLibrarySnapshot() to emptyList()

    val songs = tracks.mapTo(linkedSetOf()) { it.song.id }
    val albumSongs = mutableMapOf<String, MutableSet<String>>()
    val artistSongs = mutableMapOf<String, MutableSet<String>>()
    val toProbe = mutableListOf<Format>()
    transaction {
        check(isCurrentOwner()) { "The storage profile changed during the on-device scan" }
        val originalNames by lazy { deviceGroupNamePreferences() }
        fun originalName(id: String, name: String?): String? =
            if (name?.startsWith(MODIFIED_PREFIX) == true) {
                originalNames.getString("deviceGroupOriginalName:$id", name)
            } else name
        val albums = deviceAlbumLookup(albumTable.deviceAlbumsDirect(), ::originalName)
        val artists = artistTable.deviceArtistsDirect().asReversed().associateByTo(mutableMapOf()) {
            deviceGroupKey(originalName(it.id, it.name))
        }
        val albumPairs = songAlbumMapTable.deviceAlbumPairsDirect()
            .mapTo(hashSetOf()) { it.albumId to it.songId }
        val artistPairs = songArtistMapTable.deviceArtistPairsDirect().toHashSet()
        val indexedArtistLinksBySong = artistPairs.filter { it.artistId.startsWith(DEVICE_ARTIST_PREFIX) }
            .groupBy(SongArtistMap::songId)

        for (track in tracks) {
            val song = track.song
            val existing = formatTable.findBySongIdDirect(song.id)
            if (existing == null || !existing.hasSameSourceAs(track.format)) {
                val stored = songTable.findByIdDirect(song.id)
                if (stored == null) {
                    songTable.insertIgnore(song)
                } else {
                    songTable.upsert(stored.copy(
                        title = stored.title.takeIf { it.startsWith(MODIFIED_PREFIX) } ?: song.title,
                        artistsText = stored.artistsText?.takeIf { it.startsWith(MODIFIED_PREFIX) } ?: song.artistsText,
                        durationText = song.durationText,
                        thumbnailUrl = stored.thumbnailUrl?.takeIf { it.startsWith(MODIFIED_PREFIX) } ?: song.thumbnailUrl,
                    ))
                }
                formatTable.upsert(track.format)
                toProbe.add(track.format)
            } else if (existing.codecs == null) {
                toProbe.add(track.format)
            }

            track.album?.let { title ->
                val key = deviceGroupKey(title) to deviceGroupKey(track.albumArtist ?: song.artistsText)
                val existingAlbum = albums[key] ?: albums[key.first to null]?.takeUnless {
                    it.id.startsWith(DEVICE_ALBUM_PREFIX)
                } ?: Album(
                    id = DEVICE_ALBUM_PREFIX + UUID.randomUUID(),
                    title = title,
                    year = track.year,
                    thumbnailUrl = song.thumbnailUrl,
                    authorsText = track.albumArtist ?: song.artistsText,
                    timestamp = System.currentTimeMillis(),
                ).also(albumTable::insertIgnore)
                val album = if (existingAlbum.id.startsWith(DEVICE_ALBUM_PREFIX) &&
                    existingAlbum.year.isNullOrBlank() && track.year != null
                ) {
                    existingAlbum.copy(year = track.year).also(albumTable::updateReplace)
                } else existingAlbum
                albums[key] = album
                if (albumPairs.add(album.id to song.id)) {
                    songAlbumMapTable.map(song.id, album.id, track.trackNumber ?: -1)
                }
                albumSongs.getOrPut(album.id, ::linkedSetOf).add(song.id)
            }
            val artistNames = listOfNotNull(deviceTag(song.artistsText), deviceTag(track.albumArtist))
                .flatMap { it.splitArtistNames() }
                .distinctBy(::deviceGroupKey)
            val activeArtistIds = artistNames.mapTo(hashSetOf()) { name ->
                val artist = artists.getOrPut(deviceGroupKey(name)) {
                    Artist(
                        id = DEVICE_ARTIST_PREFIX + UUID.randomUUID(),
                        name = name,
                        thumbnailUrl = song.thumbnailUrl,
                        timestamp = System.currentTimeMillis(),
                    ).also(artistTable::insertIgnore)
                }
                val pair = SongArtistMap(song.id, artist.id)
                if (artistPairs.add(pair)) songArtistMapTable.insertIgnore(pair)
                artistSongs.getOrPut(artist.id, ::linkedSetOf).add(song.id)
                artist.id
            }
            indexedArtistLinksBySong[song.id].orEmpty().forEach { link ->
                if (link.artistId !in activeArtistIds) {
                    songArtistMapTable.deletePairDirect(link.songId, link.artistId)
                }
            }
        }

        // Preserve manually associated tracks that are present in the current scan.
        albumPairs.forEach { (album, song) ->
            if (!album.startsWith(DEVICE_ALBUM_PREFIX) && song in songs) {
                albumSongs.getOrPut(album, ::linkedSetOf).add(song)
            }
        }
        artistPairs.forEach { (song, artist) ->
            if (!artist.startsWith(DEVICE_ARTIST_PREFIX) && song in songs) {
                artistSongs.getOrPut(artist, ::linkedSetOf).add(song)
            }
        }
        check(isCurrentOwner()) { "The storage profile changed during the on-device scan" }
    }
    return DeviceLibrarySnapshot(songs, albumSongs, artistSongs) to toProbe
}

private fun deviceAlbumLookup(albums: List<Album>, originalName: (String, String?) -> String?): MutableMap<Pair<String?, String?>, Album> =
    albums.sortedWith(compareBy<Album> { !it.id.startsWith(DEVICE_ALBUM_PREFIX) }.thenByDescending { it.id })
        .associateByTo(mutableMapOf()) {
            deviceGroupKey(originalName(it.id, it.title)) to deviceGroupKey(it.authorsText)
        }
