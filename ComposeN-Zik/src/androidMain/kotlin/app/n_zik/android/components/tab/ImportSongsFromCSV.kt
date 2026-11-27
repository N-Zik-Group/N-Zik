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
import app.n_zik.android.exception.InvalidHeaderException
import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.n_zik.android.core.rewind.RewindPlaylists
import app.n_zik.android.core.rewind.RewindPlaylists.portablePlaylistName
import app.n_zik.android.models.Playlist
import app.n_zik.android.models.Song
import app.n_zik.android.ui.components.tab.toolbar.Descriptive
import app.n_zik.android.ui.components.tab.toolbar.MenuIcon
import app.n_zik.android.utils.formatAsDuration
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.playlist.importCsvSongs
import app.n_zik.android.utils.playlist.saveImportedPlaylistSort
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import app.n_zik.android.components.ImportFromFile
import app.n_zik.android.core.utils.DurationUtils
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.core.utils.csv.SongCSV
import java.io.InputStream
import timber.log.Timber

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

                          // An older CSV format uses "PlaylistBrowseId" as playlistId,
                          // this is wrong and must be correct to empty string before inserting
//
                          // to the database
                          var browseId = row["PlaylistBrowseId"].orEmpty()
                          if( browseId.toLongOrNull() != null )
                              browseId = ""

                          // For backward compatibility, one CSV format exports duration
                          // in human-readable format "00:00" while another format exports it
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

        internal suspend fun importFile(
            input: InputStream,
            targetPlaylistId: Long = 0L,
            sourceSuffix: String = "",
            likeImported: Boolean = false,
        ): Boolean {
            val straySongs = mutableListOf<Song>()
            val playlists = linkedMapOf<Playlist, List<Song>>()
            processSongs(parseFromCsvFile(input), likeImported).forEach { (identity, songs) ->
                val (name, sourceId) = identity
                if (name.isBlank()) {
                    straySongs.addAll(songs)
                } else {
                    val importedSource = sourceId.takeUnless(RewindPlaylists::isGeneratedSourceId).orEmpty()
                    val browseId = if (sourceSuffix.isEmpty()) importedSource else "${importedSource}_$sourceSuffix"
                    val playlist = Playlist(name = appContext().portablePlaylistName(name), browseId = browseId)
                    playlists[playlist] = playlists[playlist].orEmpty() + songs
                }
            }
            if (straySongs.isEmpty() && playlists.isEmpty()) return false
            val preferences = appContext().preferences
            val createdPlaylists = Database.awaitWriteBatch { importCsvSongs(targetPlaylistId, straySongs, playlists) }
            preferences.saveImportedPlaylistSort(createdPlaylists)
            return true
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
                    try {
                        val imported = checkNotNull(appContext().contentResolver.openInputStream(uri)).use { input ->
                            importFile(input, targetPlaylistId, sourceSuffix, likeImported)
                        }
                        if (!imported) {
                            Toaster.w(R.string.player_no_song_found)
                            return@launch
                        }
                        Toaster.done()
                        onImportComplete?.invoke()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch ( e: Exception ) {
                        Timber.tag("Import").w(e, "N-Zik CSV import failed")
                        when( e ) {
                            is InvalidHeaderException   -> Toaster.e( R.string.playlist_import_unsupported_format )
                            else                        -> Toaster.e( R.string.playlist_import_failed )
                        }
                    }
                }
            }
        )
    }

    override val supportedMimes: Array<String> = arrayOf("text/csv", "text/comma-separated-values")
    override val iconId: Int = R.drawable.import_outline
    override val messageId: Int = R.string.playlist_action_import
    override val menuIconTitle: String
        @Composable
        get() = stringResource( messageId )
}


