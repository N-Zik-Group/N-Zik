package app.n_zik.android.components.tab

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import app.n_zik.android.R
import app.kreate.android.exception.InvalidHeaderException
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.models.Playlist
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.formatAsDuration
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.playlist.importSongsToPlaylist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.n_zik.android.components.ImportFromFile
import app.kreate.android.me.knighthat.utils.DurationUtils
import app.kreate.android.me.knighthat.utils.Toaster
import app.kreate.android.me.knighthat.utils.csv.SongCSV
import java.io.InputStream

/**
 * Import songs from custom CSV file.
 *
 * If song is assigned to a playlist,
 * this will handle adding that song
 * to corresponding playlist upon process.
 *
 * This is the template of CSV file:
 *
 * | PlaylistBrowseId | PlaylistName | MediaId | Title | Artists | Duration | ThumbnailUrl |
 * | --- | --- | --- | --- | --- | --- | --- |
 * | 1 | Awesome Playlist | dQw4w9WgXcQ | A song | Anonymous | 212 | https://... |
 */
class ImportSongsFromCSV(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
) : ImportFromFile(launcher), MenuIcon, Descriptive {

    companion object {
        private fun parseFromCsvFile( inputStream: InputStream ): List<SongCSV> =
            csvReader { skipEmptyLine = true }
                      .readAllWithHeader(inputStream)
                      .also { rows ->       // Verify all headers
                          val validHeaders = setOf( "PlaylistBrowseId", "Duration", "PlaylistName", "MediaId", "Title", "Artists", "ThumbnailUrl" )
                          // Check if headers in this CSV file contains at least all [validHeaders]
                          if( !rows.firstOrNull()?.keys.orEmpty().containsAll( validHeaders ) )
                              throw InvalidHeaderException( "Unsupported format" )
                      }
                      .fastMap { row ->      // Experimental, revert back to [map] if needed

                          // Previous version of Kreate uses "PlaylistBrowseId" as playlistId,
                          // this is wrong and must be correct to empty string before inserting
//
                          // to the database
                          var browseId = row["PlaylistBrowseId"].orEmpty()
                          if( browseId.toLongOrNull() != null )
                              browseId = ""

                          // For backward compatibility, RiMusic exports duration
                          // in human-readable format "00:00" while Kreate exports
//
                          // in seconds.
                          val rawDuration = row["Duration"].orEmpty()
                          val convertedDuration =
                              if( rawDuration.isBlank() )
                                  "0"
                              else if( !DurationUtils.isHumanReadable( rawDuration ) )
                                  formatAsDuration( rawDuration.toLong().times( 1000 ) )
                              else
                                  rawDuration

                          SongCSV(
                              playlistBrowseId = browseId,
                              playlistName = row["PlaylistName"].orEmpty(),
                              songId = row["MediaId"].orEmpty(),
                              title = row["Title"].orEmpty(),
                              artists = row["Artists"].orEmpty(),
                              thumbnailUrl = row["ThumbnailUrl"].orEmpty(),
                              duration = convertedDuration
                          )
                      }
                      .toList()        // Make it immutable

        private fun processSongs( songs: List<SongCSV>, likeImported: Boolean = false ): Map<Pair<String, String>, List<Song>> =
            songs.fastFilter { it.songId.isNotBlank() }
                 .groupBy { it.playlistName to it.playlistBrowseId }
                 .mapValues { (_, songs) ->
                     songs.fastMap {
                         Song(
                             id = it.songId,
                             title = it.title,
                             artistsText = it.artists,
                             thumbnailUrl = it.thumbnailUrl,
                             durationText = it.duration,
                             totalPlayTimeMs = 1L,       // Bypass hidden song checker
                             likedAt = if (likeImported) System.currentTimeMillis() else null
                         )
                     }
                 }

        @Composable
        operator fun invoke(targetPlaylistId: Long = 0L, sourceSuffix: String = "", likeImported: Boolean = false, onImportComplete: (() -> Unit)? = null) = ImportSongsFromCSV(
            rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri ->
                // [uri] must be non-null (meaning path exists) in order to work
                uri ?: return@rememberLauncherForActivityResult

                // Run in background to prevent UI thread
                // from freezing due to large file.
                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                    // Songs with no playlist
                    val straySongs = mutableListOf<Song>()
                    val combos = mutableMapOf<Playlist, List<Song>>()

                    try {
                        appContext().contentResolver
                                    .openInputStream( uri )
                                    ?.use( ::parseFromCsvFile )       // Use [use] because it closes stream on exit
                                    ?.let { processSongs(it, likeImported) }
                                    ?.forEach { (playlist, songs) ->
                                        if( playlist.first.isNotBlank() ) {
                                            val browseId = if (sourceSuffix.isNotEmpty()) "${playlist.second}_$sourceSuffix" else playlist.second
                                            val realPlaylist = Playlist(name = playlist.first, browseId = browseId)
                                            combos[realPlaylist] = songs
                                        } else
                                            straySongs.addAll( songs )
                                    }

                        val targetPlaylist = if (targetPlaylistId > 0L) {
                            Database.playlistTable.findById(targetPlaylistId).first()
                                ?: error("Import target playlist no longer exists")
                        } else null
                        Database.asyncTransaction {
                            if (targetPlaylist != null) {
                                // One batch preserves the previous stray/group order when prepending.
                                importSongsToPlaylist(targetPlaylist, straySongs + combos.values.flatten())
                            } else {
                                songTable.upsert(straySongs)
                                combos.forEach { (playlist, songs) ->
                                    songTable.upsert(songs)
                                    mapIgnore( playlist, *songs.toTypedArray() )
                                }
                            }

                            // Show message when it's done
                            Toaster.done()
                            onImportComplete?.invoke()
                        }
                    } catch ( e: Exception ) {
                        when( e ) {
                            is InvalidHeaderException   -> Toaster.e( R.string.error_message_unsupported_local_playlist )
                            else                        -> Toaster.e( R.string.error_message_import_local_playlist_failed )
                        }
                    }
                }
            }
        )
    }

    override val supportedMimes: Array<String> = arrayOf("text/csv", "text/comma-separated-values")
    override val iconId: Int = R.drawable.import_outline
    override val messageId: Int = R.string.import_playlist
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
}


