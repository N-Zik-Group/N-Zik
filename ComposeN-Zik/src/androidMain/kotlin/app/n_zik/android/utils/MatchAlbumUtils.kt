package app.n_zik.android.utils

import kotlin.math.absoluteValue
import kotlin.random.Random

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

import app.n_zik.android.appContext
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.database.canEditStoredPlaylistMembership
import app.n_zik.android.core.utils.PropUtils
import app.n_zik.android.extensions.audiobar.utils.WaveformExtractor
import app.n_zik.android.models.Album
import app.n_zik.android.models.Artist
import app.n_zik.android.models.Song
import app.n_zik.android.models.SongArtistMap
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.utils.from

private suspend fun requireMatchLibrary(canUseLibrary: () -> Boolean) {
    currentCoroutineContext().ensureActive()
    if (!canUseLibrary()) throw CancellationException("Song matching no longer owns the library")
}

internal sealed interface SongMatchOutcome {
    data object Matched : SongMatchOutcome
    data object Merged : SongMatchOutcome
    data object Skipped : SongMatchOutcome
    data class Unmatched(val songId: String) : SongMatchOutcome
}

private suspend fun commitSongMatch(
    source: Song,
    profileId: String,
    canUseLibrary: () -> Boolean,
    block: suspend Database.(Song) -> SongMatchOutcome,
): SongMatchOutcome {
    val caller = currentCoroutineContext()
    // Keep the committed result observable if Cancel arrives while Room returns to the caller.
    return withContext(NonCancellable) {
        var outcome: SongMatchOutcome = SongMatchOutcome.Skipped
        Database.transaction {
            caller.ensureActive()
            requireMatchLibrary(canUseLibrary)
            val latest = songTable.findByIdDirect(source.id) ?: return@transaction
            if (latest.cleanTitle() != source.cleanTitle() || latest.artistsText != source.artistsText ||
                latest.durationText != source.durationText || latest.thumbnailUrl.isNullOrEmpty() != source.thumbnailUrl.isNullOrEmpty() ||
                (source.id.length == 11 && source.durationText == "00:00" &&
                    (latest.totalPlayTimeMs == 1L) != (source.totalPlayTimeMs == 1L))
            ) {
                return@transaction
            }
            caller.ensureActive()
            outcome = block(latest)
            requireMatchLibrary(canUseLibrary)
        }
        val replaced = when (val committed = outcome) {
            SongMatchOutcome.Matched, SongMatchOutcome.Merged -> true
            is SongMatchOutcome.Unmatched -> committed.songId != source.id
            SongMatchOutcome.Skipped -> false
        }
        if (replaced && canUseLibrary()) {
            try {
                WaveformExtractor.deleteWaveformForProfile(appContext(), source.id, profileId)
            } catch (error: RuntimeException) {
                Timber.tag("MatchAlbumUtils").w(error, "Could not clear obsolete waveform")
            }
        }
        outcome
    }
}

internal suspend fun getAlbumVersionFromVideoGlobal(
    song: Song,
    profileId: String,
    canUseLibrary: () -> Boolean,
): SongMatchOutcome {
    requireMatchLibrary(canUseLibrary)
    Timber.tag("MatchGlobal").d("START match for '${song.title}' (id='${song.id}', duration='${song.durationText}')")

    fun filteredText(text: String): String = text
        .lowercase()
        .replace("(", " ").replace(")", " ").replace("-", " ")
        .replace("lyrics", "").replace("vevo", "").replace(" hd", "")
        .replace("official video", "")
        .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '\'' || it == ',' }
        .replace(Regex("\\s+"), " ")

    delay(Random.nextLong(2000, 8000))
    requireMatchLibrary(canUseLibrary)

    val searchQuery = runCatching {
        Innertube.searchPage<Innertube.SongItem>(
            query = filteredText("${song.cleanTitle()} ${song.artistsText}"),
            params = Innertube.SearchFilter.Song.value,
            fromMusicShelfRendererContent = { content -> Innertube.SongItem.from(content) }
        )
    }.onFailure { Timber.tag("MatchAlbumUtils").e(it, "MatchGlobal: search failed") }.getOrNull()

    var searchResults = searchQuery?.getOrNull()?.items

    if (searchResults.isNullOrEmpty()) {
        val simpleQuery = filteredText(song.cleanTitle())
        Timber.tag("MatchAlbumUtils").d("MatchGlobal: fallback query='$simpleQuery'")
        delay(Random.nextLong(2000, 8000))
        requireMatchLibrary(canUseLibrary)
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

    return commitSongMatch(song, profileId, canUseLibrary) { currentSong ->
        val dbPosition = importSongTable.getPositionGlobal(currentSong.id) ?: currentSong.position
        Timber.tag("MatchGlobal").d("DB position for '${currentSong.title}' = $dbPosition")
        if (bestMatch != null) {
            val newSong = bestMatch.asSong
            Timber.tag("MatchGlobal").d("BDD: newSong from YouTube - id='${newSong.id}', duration='${newSong.durationText}', title='${newSong.title}'")

            // Check if a song with this YouTube ID already exists (duplicate match)
            val existingSong = songTable.findByIdDirect(newSong.id)
            if (existingSong != null && existingSong.id != currentSong.id) {
                Timber.tag("MatchGlobal").d("MERGE: '${currentSong.title}' (id='${currentSong.id}') -> existing id='${existingSong.id}'")
                // Merge: transfer references from old song to existing
                val playlistMappings = songPlaylistMapTable.getAllForSong(currentSong.id)
                Timber.tag("MatchGlobal").d("MERGE: transferring ${playlistMappings.size} playlist mappings")
                playlistMappings.forEach { mapping ->
                    songPlaylistMapTable.mapAtPosition(existingSong.id, mapping.playlistId, mapping.position)
                }
                val purgedArtistPairs = songArtistMapTable.clearConflictingPairs(currentSong.id, existingSong.id)
                val purgedAlbumPairs = songAlbumMapTable.clearConflictingPairs(currentSong.id, existingSong.id)
                Timber.tag("MatchGlobal").d("MERGE: purged $purgedArtistPairs artist + $purgedAlbumPairs album conflicting pairs before redirect")
                songArtistMapTable.updateSongId(currentSong.id, existingSong.id)
                songAlbumMapTable.updateSongId(currentSong.id, existingSong.id)
                eventTable.updateSongId(currentSong.id, existingSong.id)
                if (existingSong.likedAt == null && currentSong.likedAt != null) {
                    songTable.upsert(existingSong.copy(likedAt = currentSong.likedAt))
                    Timber.tag("MatchGlobal").d("MERGE: transferred likedAt")
                }
                songTable.delete(currentSong)
                Timber.tag("MatchGlobal").d("MERGED '${currentSong.title}' into '${existingSong.id}'")
                return@commitSongMatch SongMatchOutcome.Merged
            }

            // Save playlist mappings before delete
            val playlistMappings = songPlaylistMapTable.getAllForSong(currentSong.id)
            Timber.tag("MatchGlobal").d("BDD: saving ${playlistMappings.size} playlist mappings before delete")
            // Delete old song
            songTable.delete(currentSong)
            Timber.tag("MatchGlobal").d("BDD: deleted old song id='${currentSong.id}'")
            // Insert with new YouTube ID and pre-calculated position
            songTable.upsert(newSong.copy(
                title = PropUtils.retainIfModified(currentSong.title, newSong.title).orEmpty(),
                artistsText = PropUtils.retainIfModified(currentSong.artistsText, newSong.artistsText),
                thumbnailUrl = PropUtils.retainIfModified(currentSong.thumbnailUrl, newSong.thumbnailUrl),
                likedAt = currentSong.likedAt,
                totalPlayTimeMs = currentSong.totalPlayTimeMs,
                position = dbPosition
            ))
            Timber.tag("MatchGlobal").d("BDD: ADDED new song id='${newSong.id}', duration='${newSong.durationText}', title='${newSong.title}'")
            // Re-insert playlist mappings
            playlistMappings.forEach { mapping ->
                songPlaylistMapTable.mapAtPosition(newSong.id, mapping.playlistId, mapping.position)
            }
            // Update other references
            songAlbumMapTable.updateSongId(currentSong.id, newSong.id)
            songArtistMapTable.updateSongId(currentSong.id, newSong.id)
            eventTable.updateSongId(currentSong.id, newSong.id)
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
            Timber.tag("MatchGlobal").d("DONE '${currentSong.title}' -> '${newSong.id}' (pos=$dbPosition)")
            return@commitSongMatch SongMatchOutcome.Matched
        }
        SongMatchOutcome.Unmatched(currentSong.id)
    }
}

internal suspend fun getAlbumVersionFromVideo(
    song: Song,
    playlistId: Long,
    profileId: String,
    canUseLibrary: () -> Boolean,
): SongMatchOutcome {
    requireMatchLibrary(canUseLibrary)
    Timber.tag("MatchPlaylist").d("START match for '${song.title}' (id='${song.id}', duration='${song.durationText}', playlistId=$playlistId)")
    val isExtPlaylist = (song.thumbnailUrl.isNullOrEmpty()) && (song.durationText != "0:00")

    fun filteredText(text: String): String = text
        .lowercase()
        .replace("(", " ").replace(")", " ").replace("-", " ")
        .replace("lyrics", "").replace("vevo", "").replace(" hd", "")
        .replace("official video", "")
        .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '\'' || it == ',' }
        .replace(Regex("\\s+"), " ")

    delay(Random.nextLong(2000, 8000))
    requireMatchLibrary(canUseLibrary)

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
        requireMatchLibrary(canUseLibrary)
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

    fun findSongIndex(): Int {
        for (i in 0..4) {
            val requiredSong = searchResults?.getOrNull(i) ?: continue
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
    val matchedSong = if (matchedSongIndex != -1) searchResults?.getOrNull(matchedSongIndex) else null

    if (matchedSong != null) {
        Timber.tag("MatchPlaylist").d("MATCHED '${matchedSong.info?.name}' -> '${matchedSong.key}' (ytDuration='${matchedSong.durationText}')")
    } else {
        Timber.tag("MatchPlaylist").w("NOT FOUND '${song.title}' (${searchResults?.size ?: 0} results)")
    }

    return commitSongMatch(song, profileId, canUseLibrary) { currentSong ->
        if (!canEditStoredPlaylistMembership(playlistId)) return@commitSongMatch SongMatchOutcome.Skipped
        val oldPosition = songPlaylistMapTable.findPositionOf(currentSong.id, playlistId)
        if (oldPosition == -1) return@commitSongMatch SongMatchOutcome.Skipped
        val dbPosition = importSongTable.getPosition(currentSong.id, playlistId) ?: currentSong.position

        if (matchedSongIndex != -1 && matchedSong != null) {
            val newSong = matchedSong.asSong
            Timber.tag("MatchPlaylist").d("BDD: newSong from YouTube - id='${newSong.id}', duration='${newSong.durationText}'")

            val existingSong = songTable.findByIdDirect(newSong.id)
            if (existingSong != null && existingSong.id != currentSong.id) {
                Timber.tag("MatchPlaylist").d("MERGE: '${currentSong.title}' (id='${currentSong.id}') -> existing id='${existingSong.id}'")
                val playlistMappings = songPlaylistMapTable.getAllForSong(currentSong.id)
                Timber.tag("MatchPlaylist").d("MERGE: transferring ${playlistMappings.size} playlist mappings")
                playlistMappings.forEach { mapping ->
                    songPlaylistMapTable.mapAtPosition(existingSong.id, mapping.playlistId, mapping.position)
                }
                val purgedArtistPairs = songArtistMapTable.clearConflictingPairs(currentSong.id, existingSong.id)
                val purgedAlbumPairs = songAlbumMapTable.clearConflictingPairs(currentSong.id, existingSong.id)
                Timber.tag("MatchPlaylist").d("MERGE: purged $purgedArtistPairs artist + $purgedAlbumPairs album conflicting pairs before redirect")
                songArtistMapTable.updateSongId(currentSong.id, existingSong.id)
                songAlbumMapTable.updateSongId(currentSong.id, existingSong.id)
                eventTable.updateSongId(currentSong.id, existingSong.id)
                if (existingSong.likedAt == null && currentSong.likedAt != null) {
                    songTable.upsert(existingSong.copy(likedAt = currentSong.likedAt))
                    Timber.tag("MatchPlaylist").d("MERGE: transferred likedAt")
                }
                songTable.delete(currentSong)
                Timber.tag("MatchPlaylist").d("MERGED '${currentSong.title}' into '${existingSong.id}'")
                return@commitSongMatch SongMatchOutcome.Merged
            }

            val playlistMappings = songPlaylistMapTable.getAllForSong(currentSong.id)
            Timber.tag("MatchPlaylist").d("BDD: saving ${playlistMappings.size} playlist mappings before delete")
            songTable.delete(currentSong)
            Timber.tag("MatchPlaylist").d("BDD: deleted old song id='${currentSong.id}'")
            songTable.upsert(newSong.copy(
                title = PropUtils.retainIfModified(currentSong.title, newSong.title).orEmpty(),
                artistsText = PropUtils.retainIfModified(currentSong.artistsText, newSong.artistsText),
                thumbnailUrl = PropUtils.retainIfModified(currentSong.thumbnailUrl, newSong.thumbnailUrl),
                likedAt = currentSong.likedAt,
                totalPlayTimeMs = currentSong.totalPlayTimeMs,
                position = dbPosition
            ))
            Timber.tag("MatchPlaylist").d("BDD: ADDED new song id='${newSong.id}', duration='${newSong.durationText}'")
            playlistMappings.forEach { mapping ->
                songPlaylistMapTable.mapAtPosition(newSong.id, mapping.playlistId, mapping.position)
            }
            songAlbumMapTable.updateSongId(currentSong.id, newSong.id)
            songArtistMapTable.updateSongId(currentSong.id, newSong.id)
            eventTable.updateSongId(currentSong.id, newSong.id)
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
            songPlaylistMapTable.updatePosition(playlistId, newSong.id, oldPosition)
            Timber.tag("MatchPlaylist").d("DONE '${currentSong.title}' -> '${newSong.id}' (pos=$dbPosition)")
            return@commitSongMatch SongMatchOutcome.Matched
        }
        SongMatchOutcome.Unmatched(currentSong.id)
    }
}
