package app.n_zik.android.utils

import android.os.Build
import androidx.annotation.RequiresApi
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.LOCAL_KEY_PREFIX
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongArtistMap
import app.it.fast4x.rimusic.models.SongPlaylistMap
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.asSong
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.n_zik.android.core.database.Database
import app.kreate.android.me.knighthat.utils.PropUtils
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.absoluteValue
import kotlin.random.Random
import timber.log.Timber
import app.it.fast4x.rimusic.models.Artist
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.appContext
import java.util.concurrent.atomic.AtomicInteger

@RequiresApi(Build.VERSION_CODES.O)
suspend fun getAlbumVersionFromVideoGlobal(song: Song, mergedCounter: AtomicInteger? = null) {
    Timber.tag("MatchGlobal").d("START match for '${song.title}' (id='${song.id}', duration='${song.durationText}')")
    val random4Digit = Random.nextInt(1000, 10000)

    fun filteredText(text: String): String = text
        .lowercase()
        .replace("(", " ").replace(")", " ").replace("-", " ")
        .replace("lyrics", "").replace("vevo", "").replace(" hd", "")
        .replace("official video", "")
        .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '\'' || it == ',' }
        .replace(Regex("\\s+"), " ")

    fun shuffle(word: String): String {
        val chars = word.toCharArray()
        for (i in chars.indices) {
            val randomIndex = Random.nextInt(chars.size)
            chars[i] = chars[randomIndex]
        }
        return String(chars)
    }

    delay(Random.nextLong(2000, 8000))

    val searchQuery = runCatching {
        Innertube.searchPage<Innertube.SongItem>(
            query = filteredText("${song.cleanTitle()} ${song.artistsText}"),
            params = Innertube.SearchFilter.Song.value,
            fromMusicShelfRendererContent = { content -> Innertube.SongItem.from(content) }
        )
    }.onFailure { Timber.tag("MatchAlbumUtils").e(it, "MatchGlobal: search failed") }.getOrNull()

    var searchResults = searchQuery?.getOrNull()?.items
    val sourceSongWords = filteredText(song.cleanTitle()).split(" ").filter { it.isNotEmpty() }

    if (searchResults.isNullOrEmpty()) {
        val simpleQuery = filteredText(song.cleanTitle())
        Timber.tag("MatchAlbumUtils").d("MatchGlobal: fallback query='$simpleQuery'")
        delay(Random.nextLong(2000, 8000))
        val fallbackQuery = runCatching {
            Innertube.searchPage<Innertube.SongItem>(
                query = simpleQuery,
                params = Innertube.SearchFilter.Song.value,
                fromMusicShelfRendererContent = { content -> Innertube.SongItem.from(content) }
            )
        }.onFailure { Timber.tag("MatchAlbumUtils").e(it, "MatchGlobal: fallback search failed") }.getOrNull()
        searchResults = fallbackQuery?.getOrNull()?.items
    }

    fun scoreCandidate(candidate: Innertube.SongItem): Int {
        val candidateTitle = filteredText(candidate.info?.name ?: "")
        val candidateArtist = filteredText(candidate.authors?.firstOrNull()?.name ?: "")
        val sourceArtist = filteredText(song.artistsText ?: "")
        var score = 0
        if (candidateTitle.contains(filteredText(song.cleanTitle()))) score += 10
        if (candidateArtist.contains(sourceArtist) || sourceArtist.contains(candidateArtist)) score += 5
        val durationMs = song.durationText?.let { durationTextToMillis(it) } ?: 0L
        val candidateDuration = candidate.durationText?.let { durationTextToMillis(it) } ?: 0L
        if (durationMs > 0 && kotlin.math.abs(durationMs - candidateDuration) < 5000) score += 5
        return score
    }

    val bestMatch = searchResults
        ?.filter { scoreCandidate(it) >= 10 }
        ?.maxByOrNull { scoreCandidate(it) }

    if (bestMatch != null) {
        Timber.tag("MatchGlobal").d("MATCHED '${bestMatch.info?.name}' -> '${bestMatch.key}' (ytDuration='${bestMatch.durationText}')")
    } else {
        Timber.tag("MatchGlobal").w("NOT FOUND '${song.title}' (${searchResults?.size ?: 0} results)")
    }

    // Get position from import table (fallback to song.position)
    val dbPosition = Database.importSongTable.getPositionGlobal(song.id) ?: song.position
    Timber.tag("MatchGlobal").d("DB position for '${song.title}' = $dbPosition")

    Database.transaction {
        if (bestMatch != null) {
            val newSong = bestMatch.asSong
            Timber.tag("MatchGlobal").d("BDD: newSong from YouTube - id='${newSong.id}', duration='${newSong.durationText}', title='${newSong.title}'")

            // Check if a song with this YouTube ID already exists (duplicate match)
            val existingSong = songTable.findByIdDirect(newSong.id)
            if (existingSong != null && existingSong.id != song.id) {
                Timber.tag("MatchGlobal").d("MERGE: '${song.title}' (id='${song.id}') -> existing id='${existingSong.id}'")
                // Merge: transfer references from old song to existing
                val playlistMappings = songPlaylistMapTable.getAllForSong(song.id)
                Timber.tag("MatchGlobal").d("MERGE: transferring ${playlistMappings.size} playlist mappings")
                playlistMappings.forEach { mapping ->
                    songPlaylistMapTable.mapAtPosition(existingSong.id, mapping.playlistId, mapping.position)
                }
                songArtistMapTable.updateSongId(song.id, existingSong.id)
                songAlbumMapTable.updateSongId(song.id, existingSong.id)
                eventTable.updateSongId(song.id, existingSong.id)
                if (existingSong.likedAt == null && song.likedAt != null) {
                    songTable.upsert(existingSong.copy(likedAt = song.likedAt))
                    Timber.tag("MatchGlobal").d("MERGE: transferred likedAt")
                }
                songTable.delete(song)
                WaveformExtractor.deleteWaveform(appContext(), song.id)
                mergedCounter?.incrementAndGet()
                Timber.tag("MatchGlobal").d("MERGED '${song.title}' into '${existingSong.id}'")
                return@transaction
            }

            // Save playlist mappings before delete
            val playlistMappings = songPlaylistMapTable.getAllForSong(song.id)
            Timber.tag("MatchGlobal").d("BDD: saving ${playlistMappings.size} playlist mappings before delete")
            // Delete old song
            songTable.delete(song)
            WaveformExtractor.deleteWaveform(appContext(), song.id)
            Timber.tag("MatchGlobal").d("BDD: deleted old song id='${song.id}'")
            // Insert with new YouTube ID and pre-calculated position
            songTable.upsert(newSong.copy(
                title = PropUtils.retainIfModified(song.title, newSong.title).orEmpty(),
                artistsText = PropUtils.retainIfModified(song.artistsText, newSong.artistsText),
                thumbnailUrl = PropUtils.retainIfModified(song.thumbnailUrl, newSong.thumbnailUrl),
                likedAt = song.likedAt,
                totalPlayTimeMs = song.totalPlayTimeMs,
                position = dbPosition
            ))
            Timber.tag("MatchGlobal").d("BDD: ADDED new song id='${newSong.id}', duration='${newSong.durationText}', title='${newSong.title}'")
            // Re-insert playlist mappings
            playlistMappings.forEach { mapping ->
                songPlaylistMapTable.mapAtPosition(newSong.id, mapping.playlistId, mapping.position)
            }
            // Update other references
            songAlbumMapTable.updateSongId(song.id, newSong.id)
            songArtistMapTable.updateSongId(song.id, newSong.id)
            eventTable.updateSongId(song.id, newSong.id)
            // Create album mapping
            bestMatch.album?.let { albumInfo ->
                val albumId = albumInfo.endpoint?.browseId ?: return@let
                albumTable.insertIgnore(Album(id = albumId, title = albumInfo.name))
                songAlbumMapTable.map(newSong.id, albumId)
                Timber.tag("MatchGlobal").d("BDD: ADDED album mapping id='$albumId'")
            }
            bestMatch.authors?.forEach { author ->
                val browseId = author.endpoint?.browseId ?: return@forEach
                artistTable.insertIgnore(Artist(id = browseId, name = author.name, thumbnailUrl = null))
                songArtistMapTable.insertIgnore(SongArtistMap(newSong.id, browseId))
            }
            Timber.tag("MatchGlobal").d("DONE '${song.title}' -> '${newSong.id}' (pos=$dbPosition)")
        } else {
            if (song.id == (song.cleanTitle() + song.artistsText).filter { it.isLetterOrDigit() }) {
                val notFound = song.copy(
                    id = shuffle(song.artistsText + random4Digit + song.cleanTitle() + "56Music").filter { it.isLetterOrDigit() },
                    position = song.position
                )
                val oldId = song.id
                songTable.insertIgnore(notFound)
                songPlaylistMapTable.updateSongId(oldId, notFound.id)
                songAlbumMapTable.updateSongId(oldId, notFound.id)
                songArtistMapTable.updateSongId(oldId, notFound.id)
                eventTable.updateSongId(oldId, notFound.id)
                songTable.delete(song)
                WaveformExtractor.deleteWaveform(appContext(), song.id)
                Timber.tag("MatchGlobal").d("NOT FOUND - shuffled to id='${notFound.id}'")
            }
        }
    }
}

suspend fun getAlbumVersionFromVideo(song: Song, playlistId: Long, position: Int, playlist: Playlist?, mergedCounter: AtomicInteger? = null) {
    Timber.tag("MatchPlaylist").d("START match for '${song.title}' (id='${song.id}', duration='${song.durationText}', playlistId=$playlistId)")
    val isExtPlaylist = (song.thumbnailUrl.isNullOrEmpty()) && (song.durationText != "0:00")
    var songNotFound: Song
    val random4Digit = Random.nextInt(1000, 10000)

    fun filteredText(text: String): String = text
        .lowercase()
        .replace("(", " ").replace(")", " ").replace("-", " ")
        .replace("lyrics", "").replace("vevo", "").replace(" hd", "")
        .replace("official video", "")
        .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '\'' || it == ',' }
        .replace(Regex("\\s+"), " ")

    delay(Random.nextLong(2000, 8000))

    val searchQuery = runCatching {
        Innertube.searchPage<Innertube.SongItem>(
            query = filteredText("${song.cleanTitle()} ${song.artistsText}"),
            params = Innertube.SearchFilter.Song.value,
            fromMusicShelfRendererContent = { content -> Innertube.SongItem.from(content) }
        )
    }.onFailure { Timber.tag("MatchAlbumUtils").e(it, "MatchPlaylist: search failed") }.getOrNull()

    var searchResults = searchQuery?.getOrNull()?.items

    if (searchResults.isNullOrEmpty()) {
        val simpleQuery = filteredText(song.cleanTitle())
        Timber.tag("MatchAlbumUtils").d("MatchPlaylist: fallback query='$simpleQuery'")
        delay(Random.nextLong(2000, 8000))
        val fallbackQuery = runCatching {
            Innertube.searchPage<Innertube.SongItem>(
                query = simpleQuery,
                params = Innertube.SearchFilter.Song.value,
                fromMusicShelfRendererContent = { content -> Innertube.SongItem.from(content) }
            )
        }.onFailure { Timber.tag("MatchAlbumUtils").e(it, "MatchPlaylist: fallback search failed") }.getOrNull()
        searchResults = fallbackQuery?.getOrNull()?.items
    }

    val sourceSongWords = filteredText(song.cleanTitle()).split(" ").filter { it.isNotEmpty() }
    val lofi = sourceSongWords.contains("lofi")
    val rock = sourceSongWords.contains("rock")
    val reprise = sourceSongWords.contains("reprise")
    val unplugged = sourceSongWords.contains("unplugged")
    val instrumental = sourceSongWords.contains("instrumental")
    val remix = sourceSongWords.contains("remix")
    val acapella = sourceSongWords.contains("acapella")
    val acoustic = sourceSongWords.contains("acoustic")
    val live = sourceSongWords.contains("live")
    val concert = sourceSongWords.contains("concert")
    val tour = sourceSongWords.contains("tour")
    val redux = sourceSongWords.contains("redux")

    fun shuffle(word: String): String {
        val chars = word.toCharArray()
        for (i in chars.indices) {
            val randomIndex = Random.nextInt(chars.size)
            chars[i] = chars[randomIndex]
        }
        return String(chars)
    }

    fun findSongIndex(): Int {
        for (i in 0..4) {
            val requiredSong = searchResults?.getOrNull(i) as? Innertube.SongItem ?: continue
            val reqWords = filteredText(requiredSong.title ?: "").split(" ").filter { it.isNotEmpty() }
            val songMatched = (reqWords.any { it in sourceSongWords })
                    && (if (lofi) (reqWords.any { it == "lofi" }) else reqWords.all { it != "lofi" })
                    && (if (rock) (reqWords.any { it == "rock" }) else reqWords.all { it != "rock" })
                    && (if (reprise) (reqWords.any { it == "reprise" }) else reqWords.all { it != "reprise" })
                    && (if (unplugged) (reqWords.any { it == "unplugged" }) else reqWords.all { it != "unplugged" })
                    && (if (instrumental) (reqWords.any { it == "instrumental" }) else reqWords.all { it != "instrumental" })
                    && (if (remix) (reqWords.any { it == "remix" }) else reqWords.all { it != "remix" })
                    && (if (acapella) (reqWords.any { it == "acapella" }) else reqWords.all { it != "acapella" })
                    && (if (acoustic) (reqWords.any { it == "acoustic" }) else reqWords.all { it != "acoustic" })
                    && (if (live) (reqWords.any { it == "live" }) else reqWords.all { it != "live" })
                    && (if (concert) (reqWords.any { it == "concert" }) else reqWords.all { it != "concert" })
                    && (if (tour) (reqWords.any { it == "tour" }) else reqWords.all { it != "tour" })
                    && (if (redux) (reqWords.any { it == "redux" }) else reqWords.all { it != "redux" })
                    && (if (isExtPlaylist) {
                (durationTextToMillis(requiredSong.durationText ?: "") - durationTextToMillis(song.durationText ?: "")).absoluteValue <= 7000
            } else { true })
            if (songMatched) return i
        }
        return -1
    }

    val matchedSongIndex = findSongIndex()
    val matchedSong = if (matchedSongIndex != -1) searchResults?.getOrNull(matchedSongIndex) as? Innertube.SongItem else null

    if (matchedSong != null) {
        Timber.tag("MatchPlaylist").d("MATCHED '${matchedSong.info?.name}' -> '${matchedSong.key}' (ytDuration='${matchedSong.durationText}')")
    } else {
        Timber.tag("MatchPlaylist").w("NOT FOUND '${song.title}' (${searchResults?.size ?: 0} results)")
    }

    // Get position from import table (fallback to song.position)
    val dbPosition = Database.importSongTable.getPosition(song.id, playlistId) ?: song.position
    Timber.tag("MatchPlaylist").d("DB position for '${song.title}' = $dbPosition")

    Database.transaction {
        val oldPosition = songPlaylistMapTable.findPositionOf(song.id, playlistId)

        if (matchedSongIndex != -1 && matchedSong != null) {
            val newSong = matchedSong.asSong
            Timber.tag("MatchPlaylist").d("BDD: newSong from YouTube - id='${newSong.id}', duration='${newSong.durationText}'")

            val existingSong = songTable.findByIdDirect(newSong.id)
            if (existingSong != null && existingSong.id != song.id) {
                Timber.tag("MatchPlaylist").d("MERGE: '${song.title}' (id='${song.id}') -> existing id='${existingSong.id}'")
                val playlistMappings = songPlaylistMapTable.getAllForSong(song.id)
                Timber.tag("MatchPlaylist").d("MERGE: transferring ${playlistMappings.size} playlist mappings")
                playlistMappings.forEach { mapping ->
                    songPlaylistMapTable.mapAtPosition(existingSong.id, mapping.playlistId, mapping.position)
                }
                songArtistMapTable.updateSongId(song.id, existingSong.id)
                songAlbumMapTable.updateSongId(song.id, existingSong.id)
                eventTable.updateSongId(song.id, existingSong.id)
                if (existingSong.likedAt == null && song.likedAt != null) {
                    songTable.upsert(existingSong.copy(likedAt = song.likedAt))
                    Timber.tag("MatchPlaylist").d("MERGE: transferred likedAt")
                }
                songTable.delete(song)
                WaveformExtractor.deleteWaveform(appContext(), song.id)
                mergedCounter?.incrementAndGet()
                Timber.tag("MatchPlaylist").d("MERGED '${song.title}' into '${existingSong.id}'")
                return@transaction
            }

            val playlistMappings = songPlaylistMapTable.getAllForSong(song.id)
            Timber.tag("MatchPlaylist").d("BDD: saving ${playlistMappings.size} playlist mappings before delete")
            songTable.delete(song)
            WaveformExtractor.deleteWaveform(appContext(), song.id)
            Timber.tag("MatchPlaylist").d("BDD: deleted old song id='${song.id}'")
            songTable.upsert(newSong.copy(
                title = PropUtils.retainIfModified(song.title, newSong.title).orEmpty(),
                artistsText = PropUtils.retainIfModified(song.artistsText, newSong.artistsText),
                thumbnailUrl = PropUtils.retainIfModified(song.thumbnailUrl, newSong.thumbnailUrl),
                likedAt = song.likedAt,
                totalPlayTimeMs = song.totalPlayTimeMs,
                position = dbPosition
            ))
            Timber.tag("MatchPlaylist").d("BDD: ADDED new song id='${newSong.id}', duration='${newSong.durationText}'")
            playlistMappings.forEach { mapping ->
                songPlaylistMapTable.mapAtPosition(newSong.id, mapping.playlistId, mapping.position)
            }
            songAlbumMapTable.updateSongId(song.id, newSong.id)
            songArtistMapTable.updateSongId(song.id, newSong.id)
            eventTable.updateSongId(song.id, newSong.id)
            matchedSong.album?.let { albumInfo ->
                val albumId = albumInfo.endpoint?.browseId ?: return@let
                albumTable.insertIgnore(Album(id = albumId, title = albumInfo.name))
                songAlbumMapTable.map(newSong.id, albumId)
                Timber.tag("MatchPlaylist").d("BDD: ADDED album mapping id='$albumId'")
            }
            matchedSong.authors?.forEach { author ->
                val browseId = author.endpoint?.browseId ?: return@forEach
                artistTable.insertIgnore(Artist(id = browseId, name = author.name, thumbnailUrl = null))
                songArtistMapTable.insertIgnore(SongArtistMap(newSong.id, browseId))
            }
            if (oldPosition != -1) {
                songPlaylistMapTable.updatePosition(playlistId, newSong.id, oldPosition)
            }
            Timber.tag("MatchPlaylist").d("DONE '${song.title}' -> '${newSong.id}' (pos=$dbPosition)")
        } else if (song.id == (song.cleanTitle() + song.artistsText).filter { it.isLetterOrDigit() }) {
            songNotFound = song.copy(id = shuffle(song.artistsText + random4Digit + song.cleanTitle() + "56Music").filter { it.isLetterOrDigit() })
            songTable.insertIgnore(songNotFound)
            songPlaylistMapTable.map(songNotFound.id, playlistId)
            if (oldPosition != -1) {
                songPlaylistMapTable.updatePosition(playlistId, songNotFound.id, oldPosition)
            }
            Timber.tag("MatchPlaylist").d("NOT FOUND - shuffled to id='${songNotFound.id}'")
        }
    }
}
