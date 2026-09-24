package app.n_zik.android.components.import

import app.n_zik.android.core.database.*

import android.content.Context
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.core.database.Database
import app.it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import app.it.fast4x.rimusic.enums.ExoPlayerDiskCacheMaxSize
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.utils.exoPlayerCacheLocationKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.exoPlayerDiskDownloadCacheMaxSizeKey
import app.it.fast4x.rimusic.utils.getEnum
import app.it.fast4x.rimusic.utils.preferences
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.components.ImportFromFile
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.core.rewind.RewindPostImportRegenerationWorker
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import kotlin.io.path.createTempDirectory

class ImportMigration private constructor(
    launcher: ManagedActivityResultLauncher<Array<String>, Uri?>
) : ImportFromFile(launcher) {

    companion object {
        @UnstableApi
        @Composable
        operator fun invoke( context: Context, binder: PlayerServiceModern.Binder? ): ImportMigration =
            ImportMigration(
                rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    // [uri] must be non-null (meaning path exists) in order to work
                    uri ?: return@rememberLauncherForActivityResult
                    // Same thing with binder
                    binder ?: return@rememberLauncherForActivityResult

                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        runGuardedImport(
                            onFailure = { e ->
                                Timber.tag("ImportMigration").e(e, "Import failed")
                                withContext(NzikDispatchers.UI) {
                                    Toaster.e("Import failed: ${e.message}")
                                }
                            },
                            isDatabaseClosed = { Database.isClosed },
                            showRestartPrompt = { RestartAppDialog.showDialog() }
                        ) {
                            context.contentResolver
                                   .openInputStream( uri )
                                   ?.use { inStream ->         // Use [use] because it closes stream on exit
                                       ZipInputStream( inStream ).use { zipIn ->
                                           var entry: ZipEntry? = zipIn.nextEntry

                                           val cacheDir = when( context.preferences.getEnum( exoPlayerDiskCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB` ) ) {
                                               // Temporary directory deletes itself after close
                                               // It means songs remain on device as long as it's open
                                               ExoPlayerDiskCacheMaxSize.Disabled -> createTempDirectory( PlayerServiceModern.CACHE_DIRNAME ).toFile()

                                               else                               ->
                                                   // Looks a bit ugly but what it does is
                                                   // check location set by user and return
                                                   // appropriate path with [CACHE_DIRNAME] appended.
                                                   when( context.preferences.getEnum( exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System ) ) {
                                                       ExoPlayerCacheLocation.System -> context.cacheDir
                                                       ExoPlayerCacheLocation.Private -> context.filesDir
                                                   }.resolve( PlayerServiceModern.CACHE_DIRNAME )
                                           }
                                           // Ensure folder is empty
                                           cacheDir.listFiles()?.forEach( File::deleteRecursively )

                                           val downloadDir = when( context.preferences.getEnum( exoPlayerDiskDownloadCacheMaxSizeKey, ExoPlayerDiskCacheMaxSize.`2GB` ) ) {
                                               // Temporary directory deletes itself after close
                                               // It means songs remain on device as long as it's open
                                               ExoPlayerDiskCacheMaxSize.Disabled -> createTempDirectory( MyDownloadHelper.CACHE_DIRNAME ).toFile()

                                               else                               ->
                                                   // Looks a bit ugly but what it does is
                                                   // check location set by user and return
                                                   // appropriate path with [CACHE_DIRNAME] appended.
                                                   when( context.preferences.getEnum( exoPlayerCacheLocationKey, ExoPlayerCacheLocation.System ) ) {
                                                       ExoPlayerCacheLocation.System -> context.cacheDir
                                                       ExoPlayerCacheLocation.Private -> context.filesDir
                                                   }.resolve( MyDownloadHelper.CACHE_DIRNAME )
                                           }
                                           // Ensure folder is empty
                                           downloadDir.listFiles()?.forEach( File::deleteRecursively )

                                           while( entry != null ) {
                                               //<editor-fold desc="Import cached songs">
                                               if( !entry.isDirectory && entry.name.startsWith( "cached/", true ) ) {
                                                   val relPath = entry.name.substringAfter( "cached/" )

                                                   val dest = File(cacheDir, relPath)
                                                   dest.parentFile?.mkdirs()

                                                   FileOutputStream(dest).use { fileOut ->
                                                       zipIn.copyTo( fileOut )
                                                   }
                                               }

                                               if( !entry.isDirectory && entry.name.startsWith( "downloaded/", true ) ) {
                                                   val relPath = entry.name.substringAfter( "downloaded/" )

                                                   val dest = File(downloadDir, relPath)
                                                   dest.parentFile?.mkdirs()

                                                   FileOutputStream(dest).use { fileOut ->
                                                       zipIn.copyTo( fileOut )
                                                   }
                                               }
                                               //</editor-fold>

                                               //<editor-fold desc="Import databases">
                                               if( entry.name.equals( "database.db", true ) ) {
                                                   Database.checkpoint()
                                                   Database.close()

                                                   val dbFile = context.getDatabasePath( Database.FILE_NAME )
                                                   FileOutputStream(dbFile).use { dbOut ->
                                                       zipIn.copyTo( dbOut )
                                                   }
                                               }

                                               if( entry.name.equals( "exoplayer_internal.db", true ) )
                                                   FileOutputStream(context.getDatabasePath( "exoplayer_internal.db" )).use { dbOut ->
                                                       zipIn.copyTo( dbOut )
                                                   }
                                               //</editor-fold>

                                               if( entry.name.equals( "settings.csv", true ) ) {
                                                   // CsvWriter closes ZipInputStream after use.
                                                   // Must create a copy to prevent stream close prematurely
                                                   val settingsFile = kotlin.io.path.createTempFile( "settings", "csv" ).toFile()
                                                   FileOutputStream(settingsFile).use { fileOut ->
                                                       zipIn.copyTo( fileOut )
                                                   }

                                                   FileInputStream(settingsFile).use { fileIn ->
                                                       ImportSettings.onImport( context, fileIn )
                                                   }
                                               }

                                               entry = zipIn.nextEntry
                                           }
                                       }
                                   }

                            // The full-backup import replaced the database (and the app is
                            // about to restart): recompute every existing rewind-* playlist
                            // from the imported history (one-shot job, silent, not gated — spec 2)
                            RewindPostImportRegenerationWorker.schedule(context)

                            RestartAppDialog.showDialog()
                        }
                    }
                }
            )
    }

    override val supportedMimes: Array<String> = arrayOf(
        "application/zip"
    )
}



