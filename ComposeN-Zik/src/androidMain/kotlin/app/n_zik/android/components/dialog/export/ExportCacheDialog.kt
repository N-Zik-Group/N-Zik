package app.n_zik.android.components.dialog.export

import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.FfmpegPreparation
import app.n_zik.android.components.dialog.common.InProgressDialog
import app.n_zik.android.components.dialog.common.DialogActionList
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.player.lyrics.KaraokeLine
import app.n_zik.android.components.player.lyrics.LyricsFileCodec
import app.n_zik.android.components.player.lyrics.parseKaraokeLrc
import app.n_zik.android.components.player.lyrics.parseSyncedSentences
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import com.metrolist.music.betterlyrics.BetterLyrics
import it.fast4x.lrclib.LrcLib
import it.fast4x.kugou.KuGou
import it.fast4x.innertube.requests.lyrics
import kotlin.time.Duration.Companion.milliseconds
import android.net.Uri
import android.content.ContentResolver
import android.provider.DocumentsContract
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheSpan
import app.n_zik.android.playback.services.StreamingCacheReaders
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.cleanPrefix
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.modules.FfmpegModule
import app.n_zik.android.modules.ModuleState
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import app.n_zik.android.core.coil.ImageCacheFactory
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import it.fast4x.innertube.requests.songInfo
import it.fast4x.innertube.Innertube
import com.arthenica.ffmpegkit.FFprobeKit
import java.nio.ByteOrder
import java.nio.ByteBuffer
import android.graphics.BitmapFactory
import android.util.Base64
import java.util.Locale

/** Lyrics sidecar to accompany the cached audio. */
internal enum class CachedLyricsExport(val label: Int, val legacyType: LyricsType? = null) {
    None(R.string.lyrics_empty),
    Synced(R.string.lyrics_synced, LyricsType.Synced),
    Unsynced(R.string.lyrics_unsynced, LyricsType.Unsynced),
    Karaoke(R.string.lyrics_karaoke, LyricsType.Karaoke),
    Lyricsfile(R.string.export_cached_songs_lyricsfile);
}

@Composable
internal fun CachedLyricsExportDialog(onDismiss: () -> Unit, onChoice: (CachedLyricsExport) -> Unit) {
    DialogActionList(
        title = stringResource(R.string.export_cached_songs_lyrics_confirmation),
        onDismiss = onDismiss,
        actions = CachedLyricsExport.entries.map { choice ->
            DialogButton(stringResource(choice.label), onClick = {
                onDismiss()
                onChoice(choice)
            })
        }
    )
}

class ExportCacheDialog private constructor(
    activeState: MutableState<Boolean>,
    private val folderLauncher: ManagedActivityResultLauncher<Uri?, Uri?>,
    private val getSong: () -> Song,
    val isExporting: MutableState<Boolean> = mutableStateOf(false),
    val extension: String = "m4a",
    private val lyricsChoice: MutableState<CachedLyricsExport>,
    private val ffmpegPreparation: FfmpegPreparation,
) : app.n_zik.android.components.dialog.common.Dialog, MenuIcon, Descriptive {

    private val _isActiveState = activeState
    override var isActive: Boolean
        get() = _isActiveState.value
        set(v) { _isActiveState.value = v }

    companion object {
        private fun hasInternalTimedLineTag(text: String): Boolean = text.lineSequence().any { line ->
            val timestampEnd = line.indexOf(']')
            if (!line.startsWith('[') || timestampEnd < 2) {
                false
            } else {
                val content = line.substring(timestampEnd + 1).trimStart()
                content.startsWith("{lyricsfile-line:") || content.startsWith("{agent:") || content.startsWith("{bg}")
            }
        }

        // Shared scope for this dialog's fire-and-forget export work (issue #606):
        // SupervisorJob (via NzikDispatchers.fireAndForget) so a failed export

        // (onExport or batchExport) never prevents a later export from running
        // on the same scope.
        internal val scope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)

        @UnstableApi
        private fun onExport(
            uri: Uri,
            binder: PlayerServiceModern.Binder ,
            song: Song,
            isExporting: MutableState<Boolean>,
            showResultToast: Boolean = true
        ) = scope.launch {
            kotlinx.coroutines.withContext(NzikDispatchers.UI) { isExporting.value = true }
            try {
                Timber.tag("ExportCache").i("onExport triggered for song: ${song.title}")
                val format = Database.formatTable.findBySongId( song.id ).first()
                val contentLength = format?.contentLength ?: 0L
                val isOpus = format?.mimeType?.contains("webm", ignoreCase = true) == true || 
                             format?.mimeType?.contains("ogg", ignoreCase = true) == true ||
                             format?.mimeType?.contains("opus", ignoreCase = true) == true

                val isCached = binder.cache.isCached( song.id, 0, contentLength )
                val isDownloaded = binder.downloadCache.isCached( song.id, 0, contentLength )
                Timber.tag("ExportCache").i("isCached: $isCached, isDownloaded: $isDownloaded, contentLength: $contentLength")

                if( !isCached && !isDownloaded ) {
                    Toaster.i( R.string.export_song_unavailable_message )

                    try {
                        // Attempt to delete created file
                        DocumentsContract.deleteDocument( appContext().contentResolver, uri )
                    } catch ( _: Exception ) {}

                    kotlinx.coroutines.withContext(NzikDispatchers.UI) { isExporting.value = false }
                    return@launch
                }

                val cacheDir = appContext().cacheDir
                val rawFile = File(cacheDir, "temp_raw_${System.currentTimeMillis()}.m4a")
                var outFile = File(cacheDir, "temp_out_${System.currentTimeMillis()}.m4a")
                var coverFile: File? = null

                try {
                    Timber.tag("ExportCache").i("Creating raw file...")
                    val cache = if (isCached) binder.cache else binder.downloadCache
                    StreamingCacheReaders.acquire(cache, song.id).use {
                        val spans = cache.getCachedSpans(song.id)
                        rawFile.outputStream().use { outStream ->
                            spans.mapNotNull(CacheSpan::file).forEach { fileSpan ->
                                fileSpan.inputStream().use { it.copyTo(outStream) }
                            }
                        }
                    }
                    Timber.tag("ExportCache").i("Raw file created. Size: ${rawFile.length()} bytes")

                    // Detect actual codec from raw file to handle mismatched DB mimeType
                    var actualIsOpus = isOpus
                    if (!isOpus) {
                        try {
                            val probeSession = FFprobeKit.getMediaInformation(rawFile.absolutePath)
                            val codec = probeSession.mediaInformation?.streams?.firstOrNull()?.codec
                            Timber.tag("ExportCache").i("Detected codec from raw file: $codec")
                            if (codec?.contains("opus", ignoreCase = true) == true) {
                                actualIsOpus = true
                                Timber.tag("ExportCache").w("DB mimeType mismatch: raw file contains opus but format table says non-opus. Overriding.")
                            }
                        } catch (e: Exception) {
                            Timber.tag("ExportCache").w(e, "Failed to probe raw file codec, using DB mimeType")
                        }
                    }

                    // Always set correct output extension based on actual codec
                    if (actualIsOpus) {
                        outFile = File(cacheDir, "temp_out_${System.currentTimeMillis()}.ogg")
                    }

                    val album = Database.songAlbumMapTable.findAlbumOf(song.id).first()
                    val trackPosition = Database.songAlbumMapTable.findPositionOf(song.id).first()
                    Timber.tag("ExportCache").i("Album: ${album?.title}, year: ${album?.year}, position: $trackPosition")

                    // Try to fetch song description for extra metadata (offline-safe)
                    var description: String? = null
                    try {
                        val songInfo = Innertube.songInfo(song.id)?.getOrNull()
                        description = songInfo?.description
                        Timber.tag("ExportCache").i("Fetched description: ${description?.take(200)}")
                    } catch (e: Exception) {
                        Timber.tag("ExportCache").w(e, "Failed to fetch song info (offline?)")
                    }

                    val parsed = parseDescription(description)
                    val finalAlbum = (album?.title ?: parsed.album)?.let(::cleanPrefix)
                    val finalYear = album?.year?.toString() ?: parsed.year
                    
                    Timber.tag("ExportCache").i("Parsed from description: composers=${parsed.composers}, copyright=${parsed.copyright}, genre=${parsed.genre}, descAlbum=${parsed.album}, descYear=${parsed.year}")

                    var artworkData: ByteArray? = null
                    if (!song.thumbnailUrl.isNullOrEmpty()) {
                        try {
                            val bitmap = ImageCacheFactory.loadBitmap(song.thumbnailUrl)
                            if (bitmap != null) {
                                val stream = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                                artworkData = stream.toByteArray()
                            }
                        } catch (e: Exception) {
                            Timber.tag("ExportCache").w(e, "Failed to download artwork for export")
                        }
                    }

                    val commandBuilder = StringBuilder()
                    commandBuilder.append("-y -nostdin -i \"${rawFile.absolutePath}\" ")
                    
                    if (artworkData != null && !actualIsOpus) {
                        val imgOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size, imgOpts)
                        val imgCodec = when {
                            imgOpts.outMimeType?.contains("png") == true -> "png"
                            imgOpts.outMimeType?.contains("webp") == true -> "webp"
                            else -> "mjpeg"
                        }
                        coverFile = File(cacheDir, "temp_cover_${System.currentTimeMillis()}.jpg")
                        coverFile.writeBytes(artworkData)
                        commandBuilder.append("-i \"${coverFile.absolutePath}\" -map 0:a -map 1:v ")
                        commandBuilder.append("-c:v $imgCodec -disposition:v attached_pic ")
                    } else {
                        commandBuilder.append("-map 0:a ")
                    }

                    commandBuilder.append("-map_metadata -1 -map_metadata:s:a -1 -c:a copy ")

                    fun escape(str: String?): String {
                        if (str == null) return ""
                        return str.replace("\\", "\\\\")
                            .replace("\"", "\\\"")
                            .replace("\n", " ")
                            .replace("\r", "")
                            .replace("\u0000", "")
                            .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
                    }

                    commandBuilder.append("-metadata title=\"${escape(song.cleanTitle())}\" ")
                    commandBuilder.append("-metadata artist=\"${escape(song.cleanArtistsText())}\" ")
                    finalAlbum?.let { commandBuilder.append("-metadata album=\"${escape(it)}\" ") }
                    finalYear?.let { commandBuilder.append("-metadata date=\"${escape(it)}\" ") }
                    trackPosition?.let { if (it >= 0) commandBuilder.append("-metadata track=\"${it + 1}\" ") }
                    parsed.genre?.let { commandBuilder.append("-metadata genre=\"${escape(it)}\" ") }
                    parsed.copyright?.let { commandBuilder.append("-metadata copyright=\"${escape(it)}\" ") }
                    if (parsed.composers.isNotEmpty()) {
                        commandBuilder.append("-metadata composer=\"${escape(parsed.composers.joinToString(", "))}\" ")
                    }
                    commandBuilder.append("-metadata EXPORTED=\"N-Zik\" ")

                    if (actualIsOpus && artworkData != null) {
                        try {
                            artworkData?.let { data ->
                                val flacPicBase64 = generateFlacPictureBase64(data)
                                commandBuilder.append("-metadata METADATA_BLOCK_PICTURE=\"$flacPicBase64\" ")
                            }
                        } catch (e: Exception) {
                            Timber.tag("ExportCache").e(e, "Failed to build FlacPicture for Opus")
                        }
                    }

                    commandBuilder.append("\"${outFile.absolutePath}\"")

                    val command = commandBuilder.toString()
                    Timber.tag("ExportCache").i("Executing FFmpeg: $command")

                    val session = FFmpegKit.execute(command)
                    val returnCode = session.returnCode

                    if (ReturnCode.isSuccess(returnCode)) {
                        Timber.tag("ExportCache").i("FFmpeg success! Transmuxed and tagged successfully.")

                        if (!outFile.exists() || outFile.length() == 0L) {
                            throw Exception("FFmpeg returned success but output file is missing or empty")
                        }

                        // Copy the tagged file to the destination SAF URI
                        val outputStream = appContext().contentResolver.openOutputStream(uri)
                        if (outputStream == null) {
                            Timber.tag("ExportCache").e("Failed to open output stream for URI")
                            throw Exception("Failed to open output stream for destination URI")
                        }
                        outputStream.use { outStream ->
                            outFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                            Timber.tag("ExportCache").i("Toaster.done() called")
                            isExporting.value = false
                            if (showResultToast) Toaster.done()
                        }
                    } else {
                        val logs = session.allLogsAsString
                        Timber.tag("ExportCache").e("FFmpeg failed with return code $returnCode. Logs: $logs")
                        kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                            isExporting.value = false
                            if (showResultToast) Toaster.e(R.string.export_cached_songs_generic_error)
                        }
                        try { DocumentsContract.deleteDocument(appContext().contentResolver, uri) } catch (_: Exception) {}
                    }

                } catch (e: Exception) {
                    Timber.tag("ExportCache").e(e, "Export overall error")
                    kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                        isExporting.value = false
                        if (showResultToast) Toaster.e(R.string.export_cached_songs_generic_error)
                    }
                    try { DocumentsContract.deleteDocument(appContext().contentResolver, uri) } catch (_: Exception) {}
                } finally {
                    rawFile.delete()
                    outFile.delete()
                    coverFile?.delete()
                }

            } catch (e: Exception) {
                Timber.tag("ExportCache").e(e, "Export init error")
                kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                    isExporting.value = false
                    if (showResultToast) Toaster.e(R.string.export_cached_songs_generic_error)
                }
                try { DocumentsContract.deleteDocument(appContext().contentResolver, uri) } catch (_: Exception) {}
            }
        }

        @UnstableApi
        @Composable
        operator fun invoke(
            binder: PlayerServiceModern.Binder?,
            getSong: () -> Song
        ): ExportCacheDialog {
            val isExporting = remember { mutableStateOf(false) }
            val ffmpegPreparation = remember { FfmpegPreparation() }
            val song = getSong()
            val coroutineScope = rememberCoroutineScope()
            val lyricsChoiceState = remember { mutableStateOf(CachedLyricsExport.None) }

            var fileExtension by remember { mutableStateOf("m4a") }
            LaunchedEffect(song.id) {
                fileExtension = withContext(NzikDispatchers.DATA) {
                    val format = Database.formatTable.findBySongId(song.id).first()
                    if (format?.mimeType?.contains("webm", ignoreCase = true) == true || 
                        format?.mimeType?.contains("ogg", ignoreCase = true) == true ||
                        format?.mimeType?.contains("opus", ignoreCase = true) == true) "ogg" else "m4a"
                }
            }
            
            val folderLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { folderUri: Uri? ->
                folderUri ?: return@rememberLauncherForActivityResult
                val currentBinder = binder ?: return@rememberLauncherForActivityResult
                val currentSong = getSong()
                val selectedLyrics = lyricsChoiceState.value

                try {
                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    appContext().contentResolver.takePersistableUriPermission(folderUri, takeFlags)
                } catch (_: Exception) {}

                val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, treeDocId)

                coroutineScope.launch(NzikDispatchers.DATA) {
                    kotlinx.coroutines.withContext(NzikDispatchers.UI) { isExporting.value = true }
                    try {
                        val format = Database.formatTable.findBySongId(currentSong.id).first()
                        val contentLength = format?.contentLength ?: 0L
                        val isOpus = format?.mimeType?.contains("webm", ignoreCase = true) == true ||
                                format?.mimeType?.contains("ogg", ignoreCase = true) == true ||
                                format?.mimeType?.contains("opus", ignoreCase = true) == true
                        val isCached = currentBinder.cache.isCached(currentSong.id, 0, contentLength)
                        val isDownloaded = currentBinder.downloadCache.isCached(currentSong.id, 0, contentLength)
                        if (!isCached && !isDownloaded) {
                            kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                                isExporting.value = false
                                Toaster.i(R.string.export_song_unavailable_message)
                            }
                            return@launch
                        }

                        val moduleState = ffmpegPreparation.awaitReady() ?: return@launch
                        if (moduleState !is ModuleState.READY) {
                            withContext(NzikDispatchers.UI) {
                                Toaster.e(R.string.export_cached_songs_generic_error)
                            }
                            return@launch
                        }

                        val ext = if (isOpus) "ogg" else "m4a"
                        val mimeType = if (isOpus) "audio/ogg" else "audio/mp4"
                        val baseName = "${currentSong.cleanTitle()} - ${currentSong.cleanArtistsText()}"
                            .replace(Regex("[\\\\/:*?\"<>|]"), "_")

                        val audioUri = DocumentsContract.createDocument(
                            appContext().contentResolver, parentUri, mimeType, "$baseName.$ext"
                        )
                        if (audioUri != null) {
                            onExport(audioUri, currentBinder, currentSong, isExporting, showResultToast = false).join()
                        }

                        writeLyricsSidecar(
                            appContext().contentResolver, parentUri, baseName, currentSong, selectedLyrics
                        )

                        kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                            isExporting.value = false
                            Toaster.done()
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag("ExportCache").e(e, "Single export failed")
                        kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                            isExporting.value = false
                            Toaster.e(R.string.export_cached_songs_generic_error)
                        }
                    } finally {
                        withContext(NonCancellable + NzikDispatchers.UI) { isExporting.value = false }
                    }
                }
            }

            return ExportCacheDialog(
                remember { mutableStateOf(false) },
                folderLauncher,
                getSong,
                isExporting,
                fileExtension,
                lyricsChoiceState,
                ffmpegPreparation,
            )
        }

        @UnstableApi
        internal fun batchExport(
            folderUri: Uri,
            songs: List<Song>,
            binder: PlayerServiceModern.Binder,
            lyricsChoice: CachedLyricsExport = CachedLyricsExport.None,
            prepareFfmpeg: suspend () -> ModuleState? = FfmpegModule::ensureReady,
            onProgress: (current: Int, total: Int, songTitle: String) -> Unit,
            onComplete: (successCount: Int, failCount: Int) -> Unit
        ) = scope.launch {
            // The FFmpeg module (spec-ffmpeg-extension-module) must be ready before
            // ANY FFmpegKit/FFprobeKit touch; onComplete() still runs so the caller's
            // UI state resets
            val moduleState = try {
                prepareFfmpeg()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag("ExportCache").e(e, "Failed to prepare FFmpeg for batch export")
                withContext(NzikDispatchers.UI) { onComplete(0, songs.size) }
                return@launch
            } ?: return@launch
            if (moduleState !is ModuleState.READY) {
                withContext(NzikDispatchers.UI) {
                    if (moduleState is ModuleState.FAILED) {
                        Timber.tag("ExportCache").w("FFmpeg module unavailable: %s", moduleState.message)
                        Toaster.e(R.string.export_cached_songs_generic_error)
                    }
                    onComplete(0, 0)
                }
                return@launch
            }
            val cr = appContext().contentResolver
            var successCount = 0
            var failCount = 0

            try {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                cr.takePersistableUriPermission(folderUri, takeFlags)
            } catch (e: Exception) {
                Timber.tag("ExportCache").w(e, "batchExport: Failed to take persistable URI permission")
            }

            val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, treeDocId)

            songs.forEachIndexed { index, song ->
                kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                    onProgress(index + 1, songs.size, song.cleanTitle())
                }
                Timber.tag("ExportCache").i("batchExport: [${index + 1}/${songs.size}] ${song.title}")
                try {
                    val format = Database.formatTable.findBySongId(song.id).first()
                    val contentLength = format?.contentLength ?: 0L
                    val isOpus = format?.mimeType?.contains("webm", ignoreCase = true) == true ||
                            format?.mimeType?.contains("ogg", ignoreCase = true) == true ||
                            format?.mimeType?.contains("opus", ignoreCase = true) == true
                    val isCached = binder.cache.isCached(song.id, 0, contentLength)
                    val isDownloaded = binder.downloadCache.isCached(song.id, 0, contentLength)
                    if (!isCached && !isDownloaded) {
                        Timber.tag("ExportCache").w("batchExport: Song not cached/downloaded: ${song.id}")
                        failCount++
                        return@forEachIndexed
                    }
                    var actualIsOpus = isOpus
                    if (!isOpus) {
                        val cacheDir = appContext().cacheDir
                        val probeFile = File(cacheDir, "temp_probe_${song.id}_${System.currentTimeMillis()}.m4a")
                        try {
                            val cache = binder.cache
                            StreamingCacheReaders.acquire(cache, song.id).use {
                                val spans = cache.getCachedSpans(song.id)
                                probeFile.outputStream().use { out ->
                                    spans.mapNotNull(CacheSpan::file).forEach { fileSpan ->
                                        fileSpan.inputStream().use { it.copyTo(out) }
                                    }
                                }
                            }
                            val probeSession = FFprobeKit.getMediaInformation(probeFile.absolutePath)
                            val codec = probeSession.mediaInformation?.streams?.firstOrNull()?.codec
                            if (codec?.contains("opus", ignoreCase = true) == true) {
                                actualIsOpus = true
                            }
                        } catch (_: Exception) {}
                        finally { probeFile.delete() }
                    }
                    val ext = if (actualIsOpus) "ogg" else "m4a"
                    val mimeType = if (actualIsOpus) "audio/ogg" else "audio/mp4"
                    val baseName = "${song.cleanTitle()} - ${song.cleanArtistsText()}"
                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val fileName = "$baseName.$ext"
                    val docUri = DocumentsContract.createDocument(cr, parentUri, mimeType, fileName)
                    if (docUri == null) {
                        Timber.tag("ExportCache").e("batchExport: Failed to create doc for ${song.title}")
                        failCount++
                        return@forEachIndexed
                    }
                    onExport(docUri, binder, song, mutableStateOf(false), showResultToast = false).join()

                    try {
                        writeLyricsSidecar(cr, parentUri, baseName, song, lyricsChoice)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        Timber.tag("ExportCache").w(failure, "Could not write lyrics sidecar")
                    }

                    successCount++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Timber.tag("ExportCache").e(e, "batchExport: Failed ${song.title}")
                    failCount++
                }
            }
            kotlinx.coroutines.withContext(NzikDispatchers.UI) {
                onComplete(successCount, failCount)
            }
        }

        private data class ParsedMetadata(
            val album: String?,
            val year: String?,
            val composers: List<String>,
            val copyright: String?,
            val genre: String?
        )

        private fun parseDescription(description: String?): ParsedMetadata {
            val composers = mutableListOf<String>()
            var copyright: String? = null
            var genre: String? = null
            var descAlbum: String? = null
            var descYear: String? = null

            val descLines = description?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
            if (descLines.firstOrNull()?.startsWith("Provided to YouTube by") == true) {
                if (descLines.size >= 4) {
                    descAlbum = descLines[2]
                    val pLine = descLines[3]
                    if (pLine.startsWith("℗") || pLine.startsWith("(P)")) {
                        val yearMatch = Regex("""\d{4}""").find(pLine)
                        descYear = yearMatch?.value
                    }
                }
            }

            description?.lines()?.forEach { line ->
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("Composer:", ignoreCase = true) -> {
                        composers.add(trimmed.removePrefix("Composer:").trim())
                    }
                    trimmed.startsWith("℗", ignoreCase = false) || trimmed.startsWith("(P)", ignoreCase = true) -> {
                        copyright = trimmed.removePrefix("℗").removePrefix("(P)").trim()
                        if (descYear == null) {
                            val yearMatch = Regex("""\d{4}""").find(trimmed)
                            descYear = yearMatch?.value
                        }
                    }
                    trimmed.startsWith("Genre:", ignoreCase = true) -> {
                        genre = trimmed.removePrefix("Genre:").trim()
                    }
                }
            }
            return ParsedMetadata(descAlbum, descYear, composers, copyright, genre)
        }

        internal fun generateFlacPictureBase64(imageData: ByteArray): String {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
            val width = options.outWidth.takeIf { it > 0 } ?: 0
            val height = options.outHeight.takeIf { it > 0 } ?: 0
            val mimeType = options.outMimeType ?: "image/jpeg"
            
            val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
            val descBytes = ByteArray(0)
            
            val size = 4 + 4 + mimeBytes.size + 4 + descBytes.size + 4 + 4 + 4 + 4 + 4 + imageData.size
            val buffer = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
            
            buffer.putInt(3) // Picture Type: 3 = Front Cover
            buffer.putInt(mimeBytes.size)
            buffer.put(mimeBytes)
            buffer.putInt(descBytes.size)
            buffer.put(descBytes)
            buffer.putInt(width)
            buffer.putInt(height)
            buffer.putInt(24) // Color depth
            buffer.putInt(0)  // Indexed colors
            buffer.putInt(imageData.size)
            buffer.put(imageData)
            
            return Base64.encodeToString(buffer.array(), Base64.NO_WRAP)
        }

        private suspend fun writeLyricsSidecar(
            resolver: ContentResolver,
            parentUri: Uri,
            baseName: String,
            song: Song,
            choice: CachedLyricsExport,
        ) {
            if (choice == CachedLyricsExport.None) return
            val isLyricsfile = choice == CachedLyricsExport.Lyricsfile
            val text = if (isLyricsfile) {
                getSavedLyricsfile(song)
            } else {
                val type = checkNotNull(choice.legacyType)
                getLyricsText(song, type.name)?.let { toLrcSidecar(type, it) }
            } ?: return

            currentCoroutineContext().ensureActive()
            val extension = if (isLyricsfile) "lyricsfile.yaml" else "lrc"
            val mimeType = if (isLyricsfile) "application/yaml" else "application/octet-stream"
            val uri = checkNotNull(DocumentsContract.createDocument(resolver, parentUri, mimeType, "$baseName.$extension"))
            try {
                checkNotNull(resolver.openOutputStream(uri, "wt")).use { output ->
                    output.write(text.toByteArray(Charsets.UTF_8))
                }
            } catch (failure: Exception) {
                try {
                    DocumentsContract.deleteDocument(resolver, uri)
                } catch (cleanupFailure: Exception) {
                    Timber.tag("ExportCache").w("Could not remove incomplete lyrics sidecar (%s)", cleanupFailure.javaClass.simpleName)
                }
                throw failure
            }
        }

        /** Uses saved lyrics only, following Auto's format preference. */
        private suspend fun getSavedLyricsfile(song: Song): String? {
            val saved = Database.lyricsTable.findAllBySongId(song.id).first()
            for (type in listOf(LyricsType.Karaoke, LyricsType.Synced, LyricsType.Unsynced)) {
                val row = saved.firstOrNull { it.type == type.name && !it.data.isNullOrBlank() } ?: continue
                try {
                    return LyricsFileCodec.encode(row, song.cleanTitle(), song.cleanArtistsText())
                } catch (failure: IllegalArgumentException) {
                    Timber.tag("ExportCache").w("Could not encode saved %s lyrics", type.name)
                }
            }
            return null
        }

        /** Existing standard LRC is copied as-is. Internal timings use portable Enhanced LRC tags. */
        internal fun toLrcSidecar(type: LyricsType, text: String): String? = when (type) {
            LyricsType.Synced -> if (hasInternalTimedLineTag(text)) {
                parseSyncedSentences(text, preserveText = true).drop(1)
                    .joinToString("\n") { "[${lrcTimestamp(it.timeMs)}]${it.text}" }
                    .ifBlank { null }
            } else {
                text.ifBlank { null }
            }
            LyricsType.Karaoke -> {
                val lines = parseKaraokeLrc(text, preserveText = true)
                if (hasInternalTimedLineTag(text) || lines.any { it.words.isNotEmpty() }) {
                    lines.joinToString("\n") { line ->
                        "[${lrcTimestamp(line.timeMs)}]${enhancedLrcText(line)}"
                    }.ifBlank { null }
                } else {
                    text.ifBlank { null }
                }
            }
            LyricsType.Unsynced -> text.ifBlank { null }
            LyricsType.Auto -> null
        }

        private fun enhancedLrcText(line: KaraokeLine): String {
            if (line.words.isEmpty()) return line.text
            var offset = 0
            val wordStarts = line.words.map { word ->
                val index = line.text.indexOf(word.text, startIndex = offset)
                if (index < 0) return line.text
                offset = index + word.text.length
                index
            }
            return buildString {
                append(line.text.substring(0, wordStarts.first()))
                for ((index, word) in line.words.withIndex()) {
                    val start = wordStarts[index]
                    val end = wordStarts.getOrElse(index + 1) { line.text.length }
                    append('<').append(lrcTimestamp(word.startMs)).append('>')
                    append(line.text.substring(start, end))
                }
                val lastWord = line.words.last()
                val endTime = if (lastWord.hasExplicitEnd) lastWord.endMs else line.endMs
                endTime?.takeIf { it >= lastWord.startMs }?.let {
                    append('<').append(lrcTimestamp(it)).append('>')
                }
            }
        }

        private fun lrcTimestamp(timeMs: Long): String {
            val nonnegativeTime = timeMs.coerceAtLeast(0)
            return String.format(
                Locale.ROOT, "%02d:%02d.%03d", nonnegativeTime / 60_000,
                nonnegativeTime / 1000 % 60, nonnegativeTime % 1000
            )
        }

        private fun <T> Result<T>.lyricsResultOrNull(): T? = getOrElse { failure ->
            if (failure is CancellationException) throw failure
            null
        }

        internal suspend fun getLyricsText(song: Song, type: String): String? {
            try {
                val matching = Database.lyricsTable.findBySongIdAndType(song.id, type).first()
                if (matching != null && (matching.data != null || matching.isEdited)) return matching.data
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.tag("ExportCache").w("Could not read saved lyrics (%s)", failure.javaClass.simpleName)
            }

            val artist = song.cleanArtistsText()
            val title = song.title
            val durationMs = song.durationText?.let {
                val parts = it.split(":")
                if (parts.size == 2) (parts[0].toLongOrNull() ?: 0) * 60_000 + (parts[1].toLongOrNull() ?: 0) * 1000 else 0L
            } ?: 0L
            val durationSec = (durationMs / 1000).toInt()
            val data = try {
                when (type) {
                    LyricsType.Karaoke.name -> {
                        val blResult = runCatching { BetterLyrics.getLyrics(title, artist, durationSec) }.lyricsResultOrNull()
                        val blText = blResult?.lyricsResultOrNull()
                        val hasKaraoke = blText?.lines()?.any {
                            it.trim().startsWith("<") && it.contains(":") && it.contains(">")
                        } == true
                        Timber.tag("ExportCache").i("getLyricsText: BetterLyrics karaoke=$hasKaraoke")
                        if (hasKaraoke) blText else null
                    }
                    LyricsType.Synced.name -> {
                        val blResult = runCatching { BetterLyrics.getLyrics(title, artist, durationSec) }.lyricsResultOrNull()
                        val blText = blResult?.lyricsResultOrNull()
                        val hasKaraoke = blText?.lines()?.any {
                            it.trim().startsWith("<") && it.contains(":") && it.contains(">")
                        } == true
                        if (!hasKaraoke && blText != null) {
                            Timber.tag("ExportCache").i("getLyricsText: BetterLyrics synced")
                            blText
                        } else {
                            val lrcResult = runCatching { LrcLib.lyrics(artist, title, durationMs.milliseconds) }.lyricsResultOrNull()
                            val lrcText = lrcResult?.lyricsResultOrNull()?.text
                            if (lrcText != null) {
                                Timber.tag("ExportCache").i("getLyricsText: LrcLib synced")
                                lrcText
                            } else {
                                Timber.tag("ExportCache").i("getLyricsText: trying KuGou synced")
                                KuGou.lyrics(artist, title, durationSec.toLong())?.lyricsResultOrNull()?.value
                            }
                        }
                    }
                    LyricsType.Unsynced.name -> {
                        val lrcResult = runCatching { LrcLib.lyricsUnsynced(artist, title, durationMs.milliseconds) }.lyricsResultOrNull()
                        val plainText = lrcResult?.lyricsResultOrNull()?.let { lyrics -> lyrics.plainText.ifBlank { lyrics.text } }
                        if (!plainText.isNullOrBlank()) {
                            Timber.tag("ExportCache").i("getLyricsText: LrcLib unsynced OK")
                            plainText
                        } else {
                            Timber.tag("ExportCache").i("getLyricsText: trying Innertube unsynced")
                            val ytResult = runCatching { Innertube.lyrics(videoId = song.id) }.lyricsResultOrNull()
                            ytResult?.lyricsResultOrNull()
                        }
                    }
                    else -> null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.tag("ExportCache").w("Lyrics request failed (%s)", failure.javaClass.simpleName)
                null
            }

            if (data.isNullOrBlank()) return null
            var exportText: String? = data
            try {
                Database.transaction {
                    val stored = lyricsTable.findBySongIdAndType(song.id, type).first()
                    if (stored?.isEdited == true || !stored?.data.isNullOrBlank()) {
                        exportText = stored?.data
                    } else {
                        lyricsTable.upsert(Lyrics(song.id, type, data, lastFetchedAt = System.currentTimeMillis()))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Timber.tag("ExportCache").w("Could not store fetched lyrics (%s)", failure.javaClass.simpleName)
            }
            return exportText?.ifBlank { null }
        }
    }

    @Composable
    override fun DialogBody() {}

    override val iconId: Int = R.drawable.export_outline
    override val messageId: Int = R.string.export_cached_song_action
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.export_file_name_dialog_title )
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.export_cached_songs_action )

    override fun onShortClick() = showDialog()

    @Composable
    internal fun RenderProgress() {
        if (ffmpegPreparation.isPreparing) {
            ffmpegPreparation.Render()
        } else {
            DialogPresence(visible = isExporting.value) {
                InProgressDialog(
                    total = 0,
                    done = 0,
                    text = stringResource(R.string.export_running_status),
                )
            }
        }
    }

    @Composable
    override fun Render() {
        DialogPresence(visible = isActive, inherit = true) {

        CachedLyricsExportDialog(
            onDismiss = { hideDialog() },
            onChoice = { choice ->
                lyricsChoice.value = choice
                try { folderLauncher.launch(null) } catch (_: Exception) {}
            }
        )
        }
    }

    fun defaultFileName(): String =
        with( getSong() ) { "$title - ${cleanArtistsText()}" }
}


