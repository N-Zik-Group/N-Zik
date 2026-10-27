package app.n_zik.android.components.tab

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.EXPLICIT_PREFIX
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.formatAsDuration
import app.it.fast4x.rimusic.utils.splitArtistNames
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.appContext
import app.n_zik.android.components.ImportFromFile
import app.n_zik.android.core.database.Database
import app.n_zik.android.R
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.playlist.importServiceSongsToPlaylist
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class ImportSongsFromServices private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher), Descriptive, MenuIcon {

    companion object {
        private data class ImportedRow(val song: Song, val album: Album, val artists: List<Artist>)

        internal suspend fun openFile(
            uri: Uri,
            targetPlaylistId: Long = 0L,
            source: String? = null,
            likeImported: Boolean = false,
            beforeTransaction: (Int, Map<String, String>, String?) -> Unit = { _,_,_ -> },
            afterTransaction: ( Int, Song, Album, List<Artist> ) -> Unit = { _,_,_,_ -> },
            onImportComplete: ((Long) -> Unit)? = null,
        ) {
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
            var targetPlaylist = if (targetPlaylistId > 0L) {
                Database.playlistTable.findById(targetPlaylistId).first()
                    ?: error("Import target playlist no longer exists")
            } else null
            val importedRows = mutableListOf<ImportedRow>()
            val beforeActions = mutableListOf<() -> Unit>()

            context.applicationContext
                .contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->

                    csvReader().open(inputStream) {
                        readAllWithHeaderAsSequence().forEachIndexed { index, row: Map<String, String> ->
                            
                            val isSpotifyFormat = row.containsKey("Track URI")
                            val isRiplayFormat = row.containsKey("AlbumId") || row.containsKey("ArtistIds")
                            if (activePlaylistId == 0L && (isSpotifyFormat || isRiplayFormat) && !playlistCreated) {
                                playlistCreated = true
                                val cleanName = fileName.substringBeforeLast(".")
                                val newPlaylist = Playlist(name = cleanName, browseId = source)
                                // Insert playlist directly to get ID, we are already in an IO thread
                                activePlaylistId = Database.playlistTable.insert(newPlaylist)
                                targetPlaylist = newPlaylist.copy(id = activePlaylistId)
                            }

                            beforeActions += { beforeTransaction(index, row, fileName) }
                            runCatching parseRow@{

                                val song: Song
                                val album: Album
                                val artists: List<Artist>

                                if (isSpotifyFormat) {
                                    val explicitPrefix = if (row["Explicit"] == "true") EXPLICIT_PREFIX else ""
                                    val mediaId = row["Track URI"]
                                    if (mediaId == null) {
                                        return@parseRow
                                    }
                                    val title = row["Track Name"]
                                    if (title == null) {
                                        return@parseRow
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

                                    val artistNames = row["Artist Name(s)"]?.splitArtistNames()
                                    artists = artistNames?.map { name ->
                                        Artist(
                                            id = "",
                                            name = name.trim()
                                        )
                                    } ?: mutableListOf()

                                } else {
                                    val explicitPrefix = if (row["Explicit"] == "true") "e:" else ""
                                    val pseudoMediaId = (row["Track Name"].orEmpty()+row["Artist Name(s)"].orEmpty()).filter { it.isLetterOrDigit() }
                                    val mediaId = row["MediaId"] ?: pseudoMediaId
                                    if(mediaId.isEmpty()) return@parseRow
                                    
                                    val title = row["Title"] ?: row["Track Name"] ?: return@parseRow
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

                                    val artistNames = row["Artists"]?.splitArtistNames()
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

                                importedRows += ImportedRow(song, album, artists)
                            }.onFailure { Timber.tag("Import").w(it, "Skipping invalid service import row %d", index) }
                        }
                        
                    }
                }
            Database.asyncTransaction {
                syncTransaction {
                    beforeActions.forEach { it() }
                    importServiceSongsToPlaylist(targetPlaylist, importedRows.map { it.song }) { index, position ->
                        val row = importedRows[index]
                        afterTransaction(position, row.song, row.album, row.artists)
                    }
                }
                onImportComplete?.invoke(activePlaylistId)
                Toaster.done()
            }
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
                    
                    coroutineScope.launch(NzikDispatchers.DATA) {
                        openFile(uri, playlistIdForMatch, source, likeImported, beforeTransaction,
                            afterTransaction, onImportComplete)
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
