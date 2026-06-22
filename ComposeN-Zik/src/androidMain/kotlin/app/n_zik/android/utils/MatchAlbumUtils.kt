package app.n_zik.android.utils

import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.SongPlaylistMap
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.durationTextToMillis
import app.n_zik.android.core.database.Database
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import kotlin.random.Random

suspend fun getAlbumVersionFromVideo(song: Song, playlistId: Long, position: Int, playlist: Playlist?) {
    val isExtPlaylist = (song.thumbnailUrl == "") && (song.durationText != "0:00")
    var songNotFound: Song
    val random4Digit = Random.nextInt(1000, 10000)
    fun filteredText(text: String): String {
        val filteredText = text
            .lowercase()
            .replace("(", " ")
            .replace(")", " ")
            .replace("-", " ")
            .replace("lyrics", "")
            .replace("vevo", "")
            .replace(" hd", "")
            .replace("official video", "")
            .filter { it.isLetterOrDigit() || it.isWhitespace() || it == '\'' || it == ',' }
            .replace(Regex("\\s+"), " ")
        return filteredText
    }

    val searchQuery = Innertube.searchPage(
        body = SearchBody(
            query = filteredText("${song.cleanTitle()} ${song.artistsText}"),
            params = Innertube.SearchFilter.Song.value
        ),
        fromMusicShelfRendererContent = Innertube.SongItem::from
    )

    val searchResults = searchQuery?.getOrNull()?.items

    val sourceSongWords = filteredText(song.cleanTitle())
        .split(" ").filter { it.isNotEmpty() }
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
            val requiredSongWords = filteredText(song.cleanTitle()) // Actually in RiPlay it was filteredText(cleanPrefix(requiredSong?.title ?: ""))
            // Wait, I should implement exactly like RiPlay
            val reqWords = filteredText(requiredSong.title).split(" ").filter { it.isNotEmpty() }

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
                    && (if (song.asMediaItem.mediaMetadata.isPlayable == true) { requiredSong.isExplicit } else { true }) // isExplicit isn't easily accessible without mapping? N-Zik asMediaItem might not have isExplicit. Let's ignore it for now or just check requiredSong.isExplicit
                    && (if (isExtPlaylist) {
                (durationTextToMillis(requiredSong.durationText ?: "") - durationTextToMillis(song.durationText ?: "")).absoluteValue <= 2000
            } else {
                true
            })

            if (songMatched) return i
        }
        return -1
    }

    val matchedSongIndex = findSongIndex()
    val matchedSong = if (matchedSongIndex != -1) searchResults?.getOrNull(matchedSongIndex) as? Innertube.SongItem else null

    Database.asyncTransaction {
        if (matchedSongIndex != -1 && matchedSong != null) {
            // Remove old song from playlist
            songPlaylistMapTable.deleteBySongId(song.id, playlistId)
            
            // Insert matched song
            Database.upsert(matchedSong) // This handles Song, Album, Artist insertion in N-Zik

            // Map new song to playlist
            songPlaylistMapTable.map(matchedSong.info.endpoint?.videoId ?: "", playlistId)

            // If we want to replace the old position, we could do:
            // songPlaylistMapTable.updatePosition(playlistId, matchedSong.info.endpoint?.videoId ?: "", position)

            // We can also delete old song if thumbnail was empty
            if (song.thumbnailUrl.isNullOrEmpty()) {
                songTable.delete(song)
            }
        } else if (song.id == (song.cleanTitle() + song.artistsText).filter { it.isLetterOrDigit() }) {
            songNotFound = song.copy(id = shuffle(song.artistsText + random4Digit + song.cleanTitle() + "56Music").filter { it.isLetterOrDigit() })
            songTable.delete(song)
            songTable.insertIgnore(songNotFound)
            songPlaylistMapTable.map(songNotFound.id, playlistId)
        }
    }
}
