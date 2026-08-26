package app.n_zik.android.components.dialog.export

import app.n_zik.android.core.database.*
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import app.it.fast4x.rimusic.ui.components.themed.ValueSelectorDialog
import com.metrolist.music.betterlyrics.BetterLyrics
import it.fast4x.lrclib.LrcLib
import it.fast4x.kugou.KuGou
import it.fast4x.innertube.requests.lyrics
import it.fast4x.innertube.models.bodies.NextBody
import kotlin.time.Duration.Companion.milliseconds

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheSpan
import app.n_zik.android.R
import app.n_zik.android.core.database.Database
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.models.Song
import app.n_zik.android.playback.services.PlayerServiceModern
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.n_zik.android.components.dialog.export.ExportToFileDialog
import app.kreate.android.me.knighthat.utils.Toaster
import timber.log.Timber
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import java.io.ByteArrayOutputStream
import it.fast4x.innertube.requests.songInfo
import it.fast4x.innertube.Innertube
import app.it.fast4x.rimusic.models.Format
import androidx.compose.runtime.produceState
import com.arthenica.ffmpegkit.FFprobeKit
import java.nio.ByteOrder
import java.nio.ByteBuffer
import android.graphics.BitmapFactory
import android.util.Base64

class ExportCacheDialog(
    activeState: MutableState<Boolean>,
    val valueState: MutableState<TextFieldValue>,
    private val folderLauncher: ManagedActivityResultLauncher<Uri?, Uri?>,
    private val getSong: () -> Song,
    val isExporting: MutableState<Boolean> = mutableStateOf(false),
    val extension: String = "m4a",
    val lyricsType: MutableState<String?> = mutableStateOf(null)
) : app.n_zik.android.components.dialog.common.TextInputDialog(app.n_zik.android.components.dialog.common.InputDialogConstraints.ALL), MenuIcon, Descriptive {

    override val keyboardOption: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default
    override val allowEmpty: Boolean = true

    override var value: TextFieldValue
        get() = valueState.value
        set(v) { valueState.value = v }

    private val _isActiveState = activeState
    override var isActive: Boolean
        get() = _isActiveState.value
        set(v) { _isActiveState.value = v }

    companion object {
        @UnstableApi
        private fun onExport(
            uri: Uri,
            binder: PlayerServiceModern.Binder ,
            song: Song,
            isExporting: MutableState<Boolean>,
            showResultToast: Boolean = true
        ) = CoroutineScope( Dispatchers.IO ).launch {
            kotlinx.coroutines.withContext(Dispatchers.Main) { isExporting.value = true }
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
                    Toaster.i( R.string.song_must_be_cached_or_downloaded_to_export )

                    try {
                        // Attempt to delete created file
                        DocumentsContract.deleteDocument( appContext().contentResolver, uri )
                    } catch ( _: Exception ) {}

                    kotlinx.coroutines.withContext(Dispatchers.Main) { isExporting.value = false }
                    return@launch
                }

                val cacheDir = appContext().cacheDir
                val rawFile = File(cacheDir, "temp_raw_${System.currentTimeMillis()}.m4a")
                var outFile = File(cacheDir, "temp_out_${System.currentTimeMillis()}.m4a")
                var coverFile: File? = null

                try {
                    Timber.tag("ExportCache").i("Creating raw file...")
                    val spans = (if( isCached ) binder.cache else binder.downloadCache).getCachedSpans( song.id )
                    rawFile.outputStream().use { outStream ->
                        spans.mapNotNull(CacheSpan::file).forEach { fileSpan ->
                            fileSpan.inputStream().use { it.copyTo(outStream) }
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
                    val finalAlbum = album?.title ?: parsed.album
                    val finalYear = album?.year?.toString() ?: parsed.year
                    
                    Timber.tag("ExportCache").i("Parsed from description: composers=${parsed.composers}, copyright=${parsed.copyright}, genre=${parsed.genre}, descAlbum=${parsed.album}, descYear=${parsed.year}")

                    var artworkData: ByteArray? = null
                    if (!song.thumbnailUrl.isNullOrEmpty()) {
                        try {
                            val request = ImageRequest.Builder(appContext())
                                .data(song.thumbnailUrl)
                                .build()
                            val result = SingletonImageLoader.get(appContext()).execute(request)
                            if (result is SuccessResult) {
                                val bitmap = result.image.toBitmap()
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

                    commandBuilder.append("-metadata title=\"${escape(song.title)}\" ")
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
                            val flacPicBase64 = generateFlacPictureBase64(artworkData!!)
                            commandBuilder.append("-metadata METADATA_BLOCK_PICTURE=\"$flacPicBase64\" ")
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
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            Timber.tag("ExportCache").i("Toaster.done() called")
                            isExporting.value = false
                            if (showResultToast) Toaster.done()
                        }
                    } else {
                        val logs = session.allLogsAsString
                        Timber.tag("ExportCache").e("FFmpeg failed with return code $returnCode. Logs: $logs")
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            isExporting.value = false
                            if (showResultToast) Toaster.e(R.string.export_failed, "FFmpeg error: $returnCode")
                        }
                        try { DocumentsContract.deleteDocument(appContext().contentResolver, uri) } catch (_: Exception) {}
                    }

                } catch (e: Exception) {
                    Timber.tag("ExportCache").e(e, "Export overall error")
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        isExporting.value = false
                        if (showResultToast) Toaster.e(R.string.export_failed, e.message)
                    }
                    try { DocumentsContract.deleteDocument(appContext().contentResolver, uri) } catch (_: Exception) {}
                } finally {
                    rawFile.delete()
                    outFile.delete()
                    coverFile?.delete()
                }

            } catch (e: Exception) {
                Timber.tag("ExportCache").e(e, "Export init error")
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    isExporting.value = false
                    if (showResultToast) Toaster.e(R.string.export_error, e.message)
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
            val song = getSong()
            val lyricsTypeState = remember { mutableStateOf<String?>(null) }

            val fileExtension = kotlinx.coroutines.runBlocking {
                val format = Database.formatTable.findBySongId(song.id).first()
                if (format?.mimeType?.contains("webm", ignoreCase = true) == true || 
                    format?.mimeType?.contains("ogg", ignoreCase = true) == true ||
                    format?.mimeType?.contains("opus", ignoreCase = true) == true) "ogg" else "m4a"
            }

            val pendingFileName = remember( song.title ) {
                mutableStateOf( TextFieldValue("${song.title} - ${song.cleanArtistsText()}") )
            }
            
            val folderLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree()
            ) { folderUri: Uri? ->
                folderUri ?: return@rememberLauncherForActivityResult
                val currentBinder = binder ?: return@rememberLauncherForActivityResult
                val currentSong = getSong()

                try {
                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    appContext().contentResolver.takePersistableUriPermission(folderUri, takeFlags)
                } catch (_: Exception) {}

                val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, treeDocId)

                CoroutineScope(Dispatchers.IO).launch {
                    kotlinx.coroutines.withContext(Dispatchers.Main) { isExporting.value = true }
                    try {
                        val format = Database.formatTable.findBySongId(currentSong.id).first()
                        val contentLength = format?.contentLength ?: 0L
                        val isOpus = format?.mimeType?.contains("webm", ignoreCase = true) == true ||
                                format?.mimeType?.contains("ogg", ignoreCase = true) == true ||
                                format?.mimeType?.contains("opus", ignoreCase = true) == true
                        val isCached = currentBinder.cache.isCached(currentSong.id, 0, contentLength)
                        val isDownloaded = currentBinder.downloadCache.isCached(currentSong.id, 0, contentLength)
                        if (!isCached && !isDownloaded) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                isExporting.value = false
                                Toaster.i(R.string.song_must_be_cached_or_downloaded_to_export)
                            }
                            return@launch
                        }

                        val ext = if (isOpus) "ogg" else "m4a"
                        val mimeType = if (isOpus) "audio/ogg" else "audio/mp4"
                        val textValue = pendingFileName.value.text
                        val baseName = textValue.substringBeforeLast(".")
                            .ifBlank { "${currentSong.title} - ${currentSong.cleanArtistsText()}" }
                            .replace(Regex("[\\\\/:*?\"<>|]"), "_")

                        val audioUri = DocumentsContract.createDocument(
                            appContext().contentResolver, parentUri, mimeType, "$baseName.$ext"
                        )
                        if (audioUri != null) {
                            onExport(audioUri, currentBinder, currentSong, isExporting, showResultToast = false).join()
                        }

                        val selectedType = lyricsTypeState.value
                        if (selectedType != null) {
                            val lrcData = getLyricsText(currentSong, selectedType)
                            if (lrcData != null) {
                                val lrcUri = DocumentsContract.createDocument(
                                    appContext().contentResolver, parentUri,
                                    "application/octet-stream", "$baseName.lrc"
                                )
                                if (lrcUri != null) {
                                    appContext().contentResolver.openOutputStream(lrcUri)?.use { out ->
                                        out.write(lrcData.toByteArray(Charsets.UTF_8))
                                    }
                                }
                            }
                        }

                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            isExporting.value = false
                            Toaster.done()
                        }
                    } catch (e: Exception) {
                        Timber.tag("ExportCache").e(e, "Single export failed")
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            isExporting.value = false
                            Toaster.e(R.string.export_failed, e.message)
                        }
                    }
                }
            }

            return ExportCacheDialog(
                remember { mutableStateOf(false) },
                pendingFileName,
                folderLauncher,
                getSong,
                isExporting,
                fileExtension,
                lyricsTypeState
            )
        }

        @UnstableApi
        fun batchExport(
            folderUri: Uri,
            songs: List<Song>,
            binder: PlayerServiceModern.Binder,
            lyricsType: String? = null,
            onProgress: (current: Int, total: Int, songTitle: String) -> Unit,
            onComplete: (successCount: Int, failCount: Int) -> Unit
        ) = CoroutineScope(Dispatchers.IO).launch {
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
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onProgress(index + 1, songs.size, song.title)
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
                            val spans = binder.cache.getCachedSpans(song.id)
                            probeFile.outputStream().use { out ->
                                spans.mapNotNull(CacheSpan::file).forEach { fileSpan ->
                                    fileSpan.inputStream().use { it.copyTo(out) }
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
                    val baseName = "${song.title} - ${song.cleanArtistsText()}"
                        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                    val fileName = "$baseName.$ext"
                    val docUri = DocumentsContract.createDocument(cr, parentUri, mimeType, fileName)
                    if (docUri == null) {
                        Timber.tag("ExportCache").e("batchExport: Failed to create doc for ${song.title}")
                        failCount++
                        return@forEachIndexed
                    }
                    onExport(docUri, binder, song, mutableStateOf(false), showResultToast = false).join()

                    if (lyricsType != null) {
                        try {
                            val lrcData = getLyricsText(song, lyricsType!!)
                            if (lrcData != null) {
                                val lrcName = "$baseName.lrc"
                                val lrcUri = DocumentsContract.createDocument(cr, parentUri, "application/octet-stream", lrcName)
                                if (lrcUri != null) {
                                    cr.openOutputStream(lrcUri)?.use { out ->
                                        out.write(lrcData.toByteArray(Charsets.UTF_8))
                                    }
                                    Timber.tag("ExportCache").i("batchExport: Wrote .lrc for ${song.title}")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.tag("ExportCache").w(e, "batchExport: Failed to write .lrc for ${song.title}")
                        }
                    }

                    successCount++
                } catch (e: Exception) {
                    Timber.tag("ExportCache").e(e, "batchExport: Failed ${song.title}")
                    failCount++
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
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

        @UnstableApi
        internal suspend fun getLyricsText(song: Song, type: String): String? {
            Timber.tag("ExportCache").i("getLyricsText: song=${song.title}, type=$type")
            try {
                val allLyrics = Database.lyricsTable.findAllBySongId(song.id).first()
                val matching = allLyrics.find { it.type == type }
                if (matching?.data != null) {
                    Timber.tag("ExportCache").i("getLyricsText: found in DB")
                    return matching.data
                }
            } catch (e: Exception) {
                Timber.tag("ExportCache").w(e, "getLyricsText: DB lookup failed")
            }

            val artist = song.cleanArtistsText()
            val title = song.title
            val durationMs = song.durationText?.let {
                val parts = it.split(":")
                if (parts.size == 2) (parts[0].toLongOrNull() ?: 0) * 60_000 + (parts[1].toLongOrNull() ?: 0) * 1000 else 0L
            } ?: 0L
            val durationSec = (durationMs / 1000).toInt()
            Timber.tag("ExportCache").i("getLyricsText: artist=$artist, title=$title, durMs=$durationMs")

            val data = try {
                when (type) {
                    LyricsType.Karaoke.name -> {
                        val blResult = runCatching { BetterLyrics.getLyrics(title, artist, durationSec) }.getOrNull()
                        val blText = blResult?.getOrNull()
                        val hasKaraoke = blText?.lines()?.any {
                            it.trim().startsWith("<") && it.contains(":") && it.contains(">")
                        } == true
                        Timber.tag("ExportCache").i("getLyricsText: BetterLyrics karaoke=$hasKaraoke")
                        if (hasKaraoke) blText else null
                    }
                    LyricsType.Synced.name -> {
                        val blResult = runCatching { BetterLyrics.getLyrics(title, artist, durationSec) }.getOrNull()
                        val blText = blResult?.getOrNull()
                        val hasKaraoke = blText?.lines()?.any {
                            it.trim().startsWith("<") && it.contains(":") && it.contains(">")
                        } == true
                        if (!hasKaraoke && blText != null) {
                            Timber.tag("ExportCache").i("getLyricsText: BetterLyrics synced")
                            blText
                        } else {
                            val lrcResult = runCatching { LrcLib.lyrics(artist, title, durationMs.milliseconds) }.getOrNull()
                            val lrcText = lrcResult?.getOrNull()?.text
                            if (lrcText != null) {
                                Timber.tag("ExportCache").i("getLyricsText: LrcLib synced")
                                lrcText
                            } else {
                                Timber.tag("ExportCache").i("getLyricsText: trying KuGou synced")
                                KuGou.lyrics(artist, title, durationSec.toLong())?.getOrNull()?.value
                            }
                        }
                    }
                    LyricsType.Unsynced.name -> {
                        val lrcResult = runCatching { LrcLib.lyricsUnsynced(artist, title, durationMs.milliseconds) }.getOrNull()
                        val plainText = lrcResult?.getOrNull()?.plainText
                        if (!plainText.isNullOrBlank()) {
                            Timber.tag("ExportCache").i("getLyricsText: LrcLib unsynced OK")
                            plainText
                        } else {
                            Timber.tag("ExportCache").i("getLyricsText: trying Innertube unsynced")
                            val ytResult = runCatching { Innertube.lyrics(NextBody(videoId = song.id)) }.getOrNull()
                            val ytText = ytResult?.getOrNull()
                            Timber.tag("ExportCache").i("getLyricsText: Innertube result=${ytText?.take(50)}")
                            ytText
                        }
                    }
                    else -> null
                }
            } catch (e: Exception) {
                Timber.tag("ExportCache").e(e, "getLyricsText: network fetch failed")
                null
            }

            Timber.tag("ExportCache").i("getLyricsText: final result=${data?.take(50)}")
            if (!data.isNullOrBlank()) {
                try {
                    Database.lyricsTable.upsert(Lyrics(song.id, type, data))
                    Timber.tag("ExportCache").i("getLyricsText: saved to DB")
                } catch (e: Exception) {
                    Timber.tag("ExportCache").w(e, "getLyricsText: failed to save to DB")
                }
            }
            return data?.ifBlank { null }
        }
    }

    val showLyricsDialog = mutableStateOf(false)

    override fun onSet( newValue: String ) {
        super.onSet( newValue )
        if( errorMessage.isNotEmpty() ) return
    }

    override val iconId: Int = R.drawable.export_outline
    override val messageId: Int = R.string.info_export_cached_or_downloaded_song
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.title_name_your_export )
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.export_cached )

    override fun onShortClick() = showDialog()

    @Composable
    fun RenderLyricsDialog(binder: PlayerServiceModern.Binder?) {
        if (!showLyricsDialog.value) return
        val lyricsOptions = listOf(
            null to appContext().getString(R.string.no_lyrics),
            LyricsType.Synced.name to appContext().getString(R.string.lyrics_synced),
            LyricsType.Unsynced.name to appContext().getString(R.string.lyrics_unsynced),
            LyricsType.Karaoke.name to appContext().getString(R.string.lyrics_karaoke)
        )
        val selectedType = remember { mutableStateOf<String?>(null) }
        ValueSelectorDialog(
            onDismiss = { showLyricsDialog.value = false },
            title = appContext().getString(R.string.export_lyrics_choice),
            selectedValue = selectedType.value,
            values = lyricsOptions.map { it.first },
            onValueSelected = { type ->
                showLyricsDialog.value = false
                lyricsType.value = type
                binder ?: return@ValueSelectorDialog
                try {
                    folderLauncher.launch(null)
                } catch (_: Exception) {}
            },
            valueText = { type -> lyricsOptions.find { it.first == type }?.second ?: "" }
        )
    }

    fun defaultFileName(): String =
        with( getSong() ) { "$title - ${cleanArtistsText()}" }
}


