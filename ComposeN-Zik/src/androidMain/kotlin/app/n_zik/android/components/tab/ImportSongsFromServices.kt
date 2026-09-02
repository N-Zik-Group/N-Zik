package app.n_zik.android.components.tab

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import app.n_zik.android.core.database.Database
import app.n_zik.android.R
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.formatAsDuration
import app.n_zik.android.utils.getAlbumVersionFromVideo
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import app.n_zik.android.components.ImportFromFile
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.n_zik.android.core.database.ImportSong
import timber.log.Timber
import android.provider.OpenableColumns

class ImportSongsFromServices private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher), Descriptive, MenuIcon {

    companion object {
        private fun openFile(
            uri: Uri,
            targetPlaylistId: Long = 0L,
            source: String? = null,
            likeImported: Boolean = false,
            beforeTransaction: (Int, Map<String, String>, String?) -> Unit = { _,_,_ -> },
            afterTransaction: ( Int, Song, Album, List<Artist> ) -> Unit = { _,_,_,_ -> }
        ): Long {
            val context = appContext()
            var fileName = uri.lastPathSegment ?: context.getString(R.string.imported_playlist)
            context.applicationContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            var activePlaylistId = targetPlaylistId
            var playlistCreated = false

            context.applicationContext
                .contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->

                    var basePosition = -1

                    csvReader().open(inputStream) {
                        var totalRows = 0
                        var importedCount = 0
                        var skippedCount = 0
                        var spotifyCount = 0
                        var otherCount = 0

                        readAllWithHeaderAsSequence().forEachIndexed { index, row: Map<String, String> ->
                            totalRows++
                            
                            val isSpotifyFormat = row.containsKey("Track URI")
                            val isRiplayFormat = row.containsKey("AlbumId") || row.containsKey("ArtistIds")
                            if (activePlaylistId == 0L && (isSpotifyFormat || isRiplayFormat) && !playlistCreated) {
                                playlistCreated = true
                                val cleanName = fileName.substringBeforeLast(".")
                                val newPlaylist = Playlist(name = cleanName, browseId = source)
                                // Insert playlist directly to get ID, we are already in an IO thread
                                activePlaylistId = Database.playlistTable.insert(newPlaylist)
                            }
                            
                            val currentPlaylistId = activePlaylistId

                            if (index == 0 && currentPlaylistId != 0L) {
                                basePosition = Database.songPlaylistMapTable.getMaxPosition(currentPlaylistId)
                            }
                            
                            val finalPosition = basePosition + 1 + index

                            Database.asyncTransaction {
                                beforeTransaction( index, row, fileName )

                                val song: Song
                                val album: Album
                                val artists: List<Artist>

                                if (isSpotifyFormat) {
                                    spotifyCount++
                                    val explicitPrefix = if (row["Explicit"] == "true") EXPLICIT_PREFIX else ""
                                    val mediaId = row["Track URI"]
                                    if (mediaId == null) {
                                        skippedCount++
                                        return@asyncTransaction
                                    }
                                    val title = row["Track Name"]
                                    if (title == null) {
                                        skippedCount++
                                        return@asyncTransaction
                                    }
                                    val artistsText = row["Artist Name(s)"] ?: ""
                                    val durationText = formatAsDuration(row["Duration (ms)"]?.toLong() ?: 0L)

                                    val thumbnailUrl = row["Album Image URL"] ?: row["Image URL"] ?: row["Track Preview URL"]

                                    song = Song(
                                        id = mediaId,
                                        title = explicitPrefix + title,
                                        artistsText = artistsText,
                                        durationText = durationText,
                                        thumbnailUrl = thumbnailUrl,
                                        totalPlayTimeMs = 1L,
                                        likedAt = if (likeImported) System.currentTimeMillis() else null
                                    )

                                    val albumTitle = row["Album Name"]
                                    album = Album(
                                        id = "",
                                        title = albumTitle
                                    )

                                    val artistNames = row["Artist Name(s)"]?.split(",", "&")
                                    artists = artistNames?.map { name ->
                                        Artist(
                                            id = "",
                                            name = name.trim()
                                        )
                                    } ?: mutableListOf()

                                } else {
                                    otherCount++
                                    val explicitPrefix = if (row["Explicit"] == "true") "e:" else ""
                                    val pseudoMediaId = (row["Track Name"].orEmpty()+row["Artist Name(s)"].orEmpty()).filter { it.isLetterOrDigit() }
                                    val mediaId = row["MediaId"] ?: pseudoMediaId
                                    if(mediaId.isEmpty()) return@asyncTransaction
                                    
                                    val title = row["Title"] ?: row["Track Name"] ?: return@asyncTransaction
                                    val artistsText = row["Artists"] ?: row["Artist Name(s)"] ?: ""

                                    val durationText = row["Duration"]?.takeIf { it.isNotBlank() } ?: "00:00"

                                    song = Song(
                                        id = mediaId,
                                        title = explicitPrefix+title,
                                        artistsText = artistsText,
                                        durationText = durationText,
                                        thumbnailUrl = row["ThumbnailUrl"] ?: "",
                                        totalPlayTimeMs = 1L,
                                        likedAt = if (likeImported) System.currentTimeMillis() else null
                                    )

                                    val albumId = row["AlbumId"] ?: ""
                                    val albumTitle = row["AlbumTitle"]
                                    album = Album(
                                        id = albumId,
                                        title = albumTitle
                                    )

                                    val artistNames = row["Artists"]?.split(",", "&")
                                    val artistIds = row["ArtistIds"]?.split(",")
                                    val mutableArtists = mutableListOf<Artist>()
                                    if (artistIds != null && (artistNames?.size == artistIds.size)) {
                                        for(idx in artistIds.indices){
                                            val artistName = artistNames.getOrNull(idx)
                                            val artistId = artistIds.getOrNull(idx)
                                            if(artistId!=null){
                                                mutableArtists.add(Artist(id = artistId, name = artistName))
                                            }
                                        }
                                    }
                                    artists = mutableArtists
                                }

                                // Insert the song directly here (within the active transaction)
                                songTable.insertIgnore( song )
                                importedCount++
                                Timber.tag("Import").d("ADDED song '${song.title}' id='${song.id}' duration='${song.durationText}' (isRiplayFormat=$isRiplayFormat)")

                                // Save import position for match system
                                Database.importSongTable.insert(
                                    ImportSong(
                                        originalId = song.id,
                                        position = finalPosition,
                                        playlistId = currentPlaylistId.takeIf { it > 0L }
                                    )
                                )
                                Timber.tag("Import").d("ImportSong entry created: originalId='${song.id}', position=$finalPosition, playlistId=${currentPlaylistId.takeIf { it > 0L }}")

                                // If a target playlist is set, map immediately
                                if (currentPlaylistId > 0L) {
                                    songPlaylistMapTable.mapAtPosition( song.id, currentPlaylistId, finalPosition )
                                }

                                if (afterTransaction != null) {
                                    afterTransaction( finalPosition, song, album, artists )
                                }
                            }
                        }
                        
                    }
                }
            return activePlaylistId
        }

        @JvmStatic
        @Composable
        fun init(
            beforeTransaction: (Int, Map<String, String>, String?) -> Unit = { _,_,_ ->},
            afterTransaction: ( Int, Song, Album, List<Artist> ) -> Unit = { _,_,_,_ -> },
            playlistIdForMatch: Long = 0L,
            playlistName: String = "",
            source: String? = null,
            likeImported: Boolean = false,
            onImportComplete: ((Long) -> Unit)? = null
        ): ImportSongsFromServices {
            val coroutineScope = rememberCoroutineScope()
            return ImportSongsFromServices(
                rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if( uri == null ) return@rememberLauncherForActivityResult
                    
                    coroutineScope.launch(Dispatchers.IO) {
                        val importedSongs = mutableListOf<Song>()
                        val finalPlaylistId = openFile( uri, playlistIdForMatch, source, likeImported, beforeTransaction ) { index, song, album, artists ->
                            afterTransaction(index, song, album, artists)
                            importedSongs.add(song)
                        }
                        
                        onImportComplete?.invoke(finalPlaylistId)
                        Toaster.done()
                    }
                }
            )
        }
    }

    override val supportedMimes: Array<String> = arrayOf("text/csv", "text/comma-separated-values")
    override val messageId: Int = R.string.import_playlist
    override val iconId: Int = R.drawable.import_outline
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

}
