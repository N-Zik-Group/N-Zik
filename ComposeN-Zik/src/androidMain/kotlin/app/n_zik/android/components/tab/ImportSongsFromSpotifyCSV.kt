package app.n_zik.android.components.tab

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import app.n_zik.android.core.database.Database
import app.n_zik.android.R
import app.kreate.android.me.knighthat.utils.Toaster
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.models.Album
import app.it.fast4x.rimusic.models.Artist
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

class ImportSongsFromSpotifyCSV private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
): ImportFromFile(launcher), Descriptive, MenuIcon {

    companion object {
        private fun openFile(
            uri: Uri,
            beforeTransaction: (Int, Map<String, String>, String?) -> Unit = { _,_,_ -> },
            afterTransaction: ( Int, Song, Album, List<Artist> ) -> Unit = { _,_,_,_ -> }
        ) {
            val context = appContext()
            val fileName = uri.lastPathSegment ?: "Imported Playlist"
            context.applicationContext
                .contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->

                    csvReader().open(inputStream) {
                        readAllWithHeaderAsSequence().forEachIndexed { index, row: Map<String, String> ->

                            Database.asyncTransaction {
                                beforeTransaction( index, row, fileName )

                                val isSpotifyFormat = row.containsKey("Track URI")

                                val song: Song
                                val album: Album
                                val artists: List<Artist>

                                if (isSpotifyFormat) {
                                    val explicitPrefix = if (row["Explicit"] == "true") "e:" else ""
                                    val mediaId = row["Track URI"] ?: return@asyncTransaction
                                    val title = row["Track Name"] ?: return@asyncTransaction
                                    val artistsText = row["Artist Name(s)"] ?: ""
                                    val durationText = formatAsDuration(row["Duration (ms)"]?.toLong() ?: 0L)

                                    song = Song(
                                        id = mediaId,
                                        title = explicitPrefix + title,
                                        artistsText = artistsText,
                                        durationText = durationText,
                                        thumbnailUrl = null,
                                        totalPlayTimeMs = 1L
                                    )

                                    val albumTitle = row["Album Name"]
                                    album = Album(
                                        id = "",
                                        title = albumTitle
                                    )

                                    val artistNames = row["Artist Name(s)"]?.split(",")
                                    artists = artistNames?.map { name ->
                                        Artist(
                                            id = "",
                                            name = name.trim()
                                        )
                                    } ?: mutableListOf()

                                    afterTransaction( index, song, album, artists )
                                } else {
                                    val explicitPrefix = if (row["Explicit"] == "true") "e:" else ""
                                    val pseudoMediaId = (row["Track Name"].orEmpty()+row["Artist Name(s)"].orEmpty()).filter { it.isLetterOrDigit() }
                                    val mediaId = row["MediaId"] ?: pseudoMediaId
                                    if(mediaId.isEmpty()) return@asyncTransaction
                                    
                                    val title = row["Title"] ?: row["Track Name"] ?: return@asyncTransaction
                                    val artistsText = row["Artists"] ?: row["Artist Name(s)"] ?: ""

                                    val durationText = row["Duration"] ?: formatAsDuration(row["Track Duration (ms)"]?.toLong() ?: 0L)

                                    song = Song(
                                        id = mediaId,
                                        title = explicitPrefix+title,
                                        artistsText = artistsText,
                                        durationText = durationText,
                                        thumbnailUrl = row["ThumbnailUrl"] ?: "",
                                        totalPlayTimeMs = 1L
                                    )

                                    val albumId = row["AlbumId"] ?: ""
                                    val albumTitle = row["AlbumTitle"]
                                    album = Album(
                                        id = albumId,
                                        title = albumTitle
                                    )

                                    val artistNames = row["Artists"]?.split(",")
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

                                    afterTransaction( index, song, album, artists )
                                }
                            }
                        }
                    }
                }
        }

        @JvmStatic
        @Composable
        fun init(
            beforeTransaction: (Int, Map<String, String>, String?) -> Unit = { _,_,_ ->},
            afterTransaction: ( Int, Song, Album, List<Artist> ) -> Unit = { _,_,_,_ -> },
            playlistIdForMatch: Long = 0L,
            playlistName: String = "",
            doMatchAfterImport: Boolean = true
        ) = ImportSongsFromSpotifyCSV(
            rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                if( uri == null ) return@rememberLauncherForActivityResult
                
                CoroutineScope(Dispatchers.IO).launch {
                    val importedSongs = mutableListOf<Song>()
                    openFile( uri, beforeTransaction ) { index, song, album, artists ->
                        afterTransaction(index, song, album, artists)
                        importedSongs.add(song)
                    }
                    
                    if (doMatchAfterImport && playlistIdForMatch != 0L) {
                        Toaster.i("Matching songs to album versions in background...")
                        delay(2000)
                        
                        val matchedItems = importedSongs.filter { (it.thumbnailUrl?.startsWith("https://lh3.googleusercontent.com") == false) && !(it.id.startsWith(LOCAL_KEY_PREFIX)) }
                        matchedItems.forEachIndexed { index, song ->
                            getAlbumVersionFromVideo(
                                song = song,
                                playlistId = playlistIdForMatch,
                                position = index,
                                playlist = app.it.fast4x.rimusic.models.Playlist(id = playlistIdForMatch, name = playlistName)
                            )
                            delay(500)
                        }
                        Toaster.done()
                    } else {
                        Toaster.done()
                    }
                }
            }
        )
    }

    override val supportedMimes: Array<String> = arrayOf("text/csv", "text/comma-separated-values")
    override val messageId: Int = R.string.import_playlist
    override val iconId: Int = R.drawable.import_outline
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )

}
