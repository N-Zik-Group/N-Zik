package app.n_zik.android.components.dialog.song

import android.content.ContentUris
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.InteractiveDialog
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.colorPalette
import app.n_zik.android.core.database.Database
import app.n_zik.android.playback.services.LOCAL_KEY_PREFIX
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.models.Song
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.kreate.android.me.knighthat.utils.Toaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import app.n_zik.android.appContext
import app.n_zik.android.components.dialog.export.ExportCacheDialog
import androidx.compose.foundation.BorderStroke
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.material3.ButtonDefaults
import androidx.activity.result.IntentSenderRequest
import com.arthenica.ffmpegkit.ReturnCode
import android.app.Activity
import com.arthenica.ffmpegkit.FFprobeKit
import java.nio.ByteOrder
import android.app.RecoverableSecurityException
import java.nio.ByteBuffer
import com.arthenica.ffmpegkit.FFmpegKit
import android.content.Context
import android.util.Base64

class EditMetadataDialog private constructor(
    activeState: MutableState<Boolean>,
    private val getSong: () -> Song?,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope
) : InteractiveDialog, InputDialog, MenuIcon, Descriptive {

    override var isActive: Boolean by activeState

    private var fields = mutableStateListOf<EditableField>()
    private var isLoading by mutableStateOf(false)
    private var filePath: String? = null
    private var coverArtBytes: ByteArray? = null

    private var writePermissionLauncher: ActivityResultLauncher<IntentSenderRequest>? = null
    private var pendingTempFile: File? = null
    private var pendingMediaStoreUri: Uri? = null
    private var pendingSong: Song? = null

    override val iconId: Int = R.drawable.cover_edit
    override val messageId: Int = R.string.edit_metadata
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)
    override val dialogTitle: String
        @Composable
        get() = stringResource(messageId)

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override var value: TextFieldValue
        get() = fields.firstOrNull()?.value ?: TextFieldValue("")
        set(newValue) { if (fields.isNotEmpty()) fields[0] = fields[0].copy(value = newValue) }

    override fun onValueChanged(newValue: String): Boolean = true
    override fun onSet(newValue: String) {}
    override fun onShortClick() = showDialog()

    data class EditableField(
        val key: String,
        val rawId: String,
        val value: TextFieldValue,
        val isStandardTag: Boolean,
        val isCoverArt: Boolean = false
    )

    companion object {
        val STANDARD_TAGS = listOf(
            "TITLE", "ARTIST", "ALBUM", "GENRE", "DATE", "TRACK", "DISC_NUMBER",
            "ALBUM_ARTIST", "COMPOSER", "COPYRIGHT", "COMMENT", "ENCODER",
            "BPM", "ISRC", "PUBLISHER", "LYRICIST", "CONDUCTOR", "REMIXER", "EXPORTED"
        )

        val FFMPEG_KEY_MAP = mapOf(
            "TITLE" to "title",
            "ARTIST" to "artist",
            "ALBUM" to "album",
            "GENRE" to "genre",
            "DATE" to "date",
            "TRACK" to "track",
            "DISC_NUMBER" to "disc",
            "ALBUM_ARTIST" to "album_artist",
            "COMPOSER" to "composer",
            "COPYRIGHT" to "copyright",
            "COMMENT" to "comment",
            "ENCODER" to "encoder",
            "BPM" to "bpm",
            "ISRC" to "isrc",
            "PUBLISHER" to "publisher",
            "LYRICIST" to "lyricist",
            "CONDUCTOR" to "conductor",
            "REMIXER" to "remixer",
            "EXPORTED" to "EXPORTED"
        )

        private val LABEL_TO_STRING_RES = mapOf(
            "TITLE" to R.string.metadata_title,
            "ARTIST" to R.string.metadata_artist,
            "ALBUM" to R.string.metadata_album,
            "GENRE" to R.string.metadata_genre,
            "DATE" to R.string.metadata_year,
            "TRACK" to R.string.metadata_track,
            "DISC_NUMBER" to R.string.metadata_disc_number,
            "ALBUM_ARTIST" to R.string.metadata_album_artist,
            "COMPOSER" to R.string.metadata_composer,
            "COPYRIGHT" to R.string.metadata_copyright,
            "COMMENT" to R.string.metadata_comment,
            "ENCODER" to R.string.metadata_encoder,
            "BPM" to R.string.metadata_bpm,
            "ISRC" to R.string.metadata_isrc,
            "PUBLISHER" to R.string.metadata_publisher,
            "LYRICIST" to R.string.metadata_lyricist,
            "CONDUCTOR" to R.string.metadata_conductor,
            "REMIXER" to R.string.metadata_remixer,
            "COVERART" to R.string.metadata_cover,
            "EXPORTED" to R.string.metadata_exported
        )

        @Composable
        private fun displayLabel(key: String): String {
            val resId = LABEL_TO_STRING_RES[key]
            return if (resId != null) stringResource(resId) else key
        }

        @Composable
        operator fun invoke(getSong: () -> Song?): EditMetadataDialog {
            val coroutineScope = rememberCoroutineScope()
            return EditMetadataDialog(remember { mutableStateOf(false) }, getSong, coroutineScope)
        }
    }

    private fun resolveFilePath(context: Context, songId: String): String? {
        val mediaStoreId = songId.substringAfter(LOCAL_KEY_PREFIX).toLongOrNull() ?: return null
        val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
        context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val path = cursor.getString(0)
                if (!path.isNullOrEmpty()) return path
            }
        }
        return null
    }

    override fun showDialog() { super<InteractiveDialog>.showDialog() }
    override fun hideDialog() { isActive = false }

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val song = remember(isActive) { if (isActive) getSong() else null }

        val pickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        coverArtBytes = bytes
                        val idx = fields.indexOfFirst { it.isCoverArt }
                        if (idx >= 0) fields[idx] = fields[idx].copy(value = TextFieldValue("New cover selected"))
                    }
                } catch (e: Exception) {
                    Timber.tag("EditMetadata").e(e, "Failed to read cover image")
                }
            }
        }

        val writePermLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                performWriteToMediaStore(context)
            }
        }
        writePermissionLauncher = writePermLauncher

        LaunchedEffect(isActive) {
            if (!isActive || song == null) return@LaunchedEffect
            isLoading = true
            fields.clear()
            coverArtBytes = null

            val result = withContext(Dispatchers.IO) { readTagsFromFile(context, song) }

            filePath = result.path
            coverArtBytes = result.coverBytes
            fields.clear()
            fields.addAll(result.fields)
            isLoading = false
        }

        val scrollState = rememberScrollState()

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .heightIn(max = 500.dp)
                .verticalScroll(scrollState)
        ) {
            if (isLoading) {
                Text(stringResource(R.string.metadata_loading), modifier = Modifier.fillMaxWidth().padding(8.dp))
            } else {
                val sortedFields = remember {
                    derivedStateOf {
                        fields.sortedBy { f ->
                            when {
                                f.isCoverArt -> -2
                                f.value.text.isNotBlank() -> {
                                    val idx = STANDARD_TAGS.indexOf(f.key)
                                    if (idx >= 0) idx else Int.MAX_VALUE - 2
                                }
                                else -> Int.MAX_VALUE - 1
                            }
                        }
                    }
                }.value
                sortedFields.forEach { field ->
                    val index = fields.indexOfFirst { it.key == field.key }
                    if (field.isCoverArt) {
                        CoverArtField(
                            coverBytes = coverArtBytes,
                            onPickImage = { pickerLauncher.launch("image/*") }
                        )
                    } else {
                        EditField(
                            label = displayLabel(field.key),
                            fieldValue = field.value,
                            onValueChange = { newValue ->
                                fields[index] = fields[index].copy(value = newValue)
                            }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CoverArtField(coverBytes: ByteArray?, onPickImage: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(uiRoundnessShape())
                .background(colorPalette().background1)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (coverBytes != null) {
                val bitmap = remember(coverBytes) {
                    BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Cover art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            } else {
                Canvas(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colorPalette().background2)
                ) {}
            }

            Text(
                text = stringResource(R.string.pick_from_gallery),
                style = typography().xs,
                color = colorPalette().text,
                modifier = Modifier.weight(1f)
            )

            OutlinedButton(
                onClick = onPickImage,
                shape = uiRoundnessShape(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colorPalette().accent
                ),
                border = BorderStroke(1.dp, colorPalette().accent),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(stringResource(R.string.pick_from_gallery))
            }
        }
    }

    @Composable
    private fun EditField(label: String, fieldValue: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
        TextField(
            value = fieldValue,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = keyboardOption,
            modifier = Modifier.fillMaxWidth(),
            colors = InputDialog.defaultTextFieldColors()
        )
    }

    @Composable
    override fun Buttons() = Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp)
    ) {
        InteractiveDialog.CancelButton(
            modifier = InteractiveDialog.ButtonModifier()
                .weight(1f).fillMaxWidth(.98f)
                .border(2.dp, androidx.compose.ui.graphics.Color(android.graphics.Color.RED).copy(alpha = .3f), uiRoundnessShape())
                .padding(vertical = 10.dp),
            onCancel = ::hideDialog
        )
        InteractiveDialog.ConfirmButton(
            modifier = InteractiveDialog.ButtonModifier()
                .weight(1f).fillMaxWidth(.98f)
                .background(colorPalette().accent)
                .padding(vertical = 10.dp),
            onConfirm = { saveToFile() }
        )
    }

    private fun saveToFile() {
        val path = filePath ?: run { Toaster.e("Cannot resolve file path"); return }
        val song = getSong() ?: return

        coroutineScope.launch(Dispatchers.IO) {
            val context = appContext()
            val cacheDir = context.cacheDir
            val originalFile = File(path)
            val ext = originalFile.extension
            Timber.tag("EditMetadata").i("saveToFile: song=${song.title}, path=$path, ext=$ext")

            val tempExt = if (ext.equals("opus", ignoreCase = true) || ext.equals("ogg", ignoreCase = true)) "ogg" else ext
            var tempFile = File(cacheDir, "edit_meta_${song.id.hashCode()}.$tempExt")
            val mediaStoreId = song.id.substringAfter(LOCAL_KEY_PREFIX).toLongOrNull()
            val mediaStoreUri = mediaStoreId?.let {
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
            }

            var coverFile: File? = null

            try {
                val commandBuilder = StringBuilder()
                var isOpus = ext.equals("opus", ignoreCase = true) || ext.equals("ogg", ignoreCase = true)

                if (!isOpus) {
                    try {
                        val probeSession = FFprobeKit.getMediaInformation(originalFile.absolutePath)
                        val codec = probeSession.mediaInformation?.streams?.firstOrNull()?.codec
                        Timber.tag("EditMetadata").i("Probed codec: $codec")
                        if (codec?.contains("opus", ignoreCase = true) == true) {
                            isOpus = true
                            tempFile = File(cacheDir, "edit_meta_${song.id.hashCode()}.ogg")
                            Timber.tag("EditMetadata").w("Extension mismatch: file contains Opus but ext=$ext. Overriding, tempFile=${tempFile.name}")
                        }
                    } catch (e: Exception) {
                        Timber.tag("EditMetadata").w(e, "Failed to probe file codec, using extension")
                    }
                }

                Timber.tag("EditMetadata").i("isOpus=$isOpus, hasCover=${coverArtBytes != null}, mediaStoreUri=$mediaStoreUri")
                
                commandBuilder.append("-y -nostdin -i \"${originalFile.absolutePath}\" ")
                
                if (coverArtBytes != null && !isOpus) {
                    val bytes = coverArtBytes ?: return@launch
                    val imgOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, imgOpts)
                    val imgCodec = when {
                        imgOpts.outMimeType?.contains("png") == true -> "png"
                        imgOpts.outMimeType?.contains("webp") == true -> "webp"
                        else -> "mjpeg"
                    }
                    coverFile = File(cacheDir, "temp_cover_${System.currentTimeMillis()}.jpg")
                    coverFile.writeBytes(bytes)
                    Timber.tag("EditMetadata").i("Cover file created: ${coverFile.absolutePath} (${bytes.size} bytes, codec=$imgCodec)")
                    commandBuilder.append("-i \"${coverFile.absolutePath}\" -map 0:a -map 1:v ")
                    commandBuilder.append("-c:v $imgCodec -disposition:v attached_pic ")
                } else {
                    commandBuilder.append("-map 0:a ")
                }

                commandBuilder.append("-map_metadata -1 -map_metadata:s:a -1 -c:a copy ")

                fun escape(str: String): String {
                    return str.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", " ")
                        .replace("\r", "")
                        .replace("\u0000", "")
                        .replace(Regex("[\\x00-\\x08\\x0E-\\x1F]"), "")
                }

                fields.forEach { field ->
                    val value = field.value.text.trim()
                    if (value.isNotEmpty() && field.key != "COVERART") {
                        val ffmpegKey = FFMPEG_KEY_MAP[field.key] ?: field.key.lowercase()
                        Timber.tag("EditMetadata").d("Setting tag: $ffmpegKey = ${value.take(80)}")
                        commandBuilder.append("-metadata $ffmpegKey=\"${escape(value)}\" ")
                    }
                }

                if (isOpus && coverArtBytes != null) {
                    try {
                        val bytes = coverArtBytes ?: return@launch
                        val flacPicBase64 = ExportCacheDialog.generateFlacPictureBase64(bytes)
                        commandBuilder.append("-metadata METADATA_BLOCK_PICTURE=\"$flacPicBase64\" ")
                    } catch (e: Exception) {
                        Timber.tag("EditMetadata").e(e, "Failed to build FlacPicture for Opus")
                    }
                }

                commandBuilder.append("\"${tempFile.absolutePath}\"")
                
                val command = commandBuilder.toString()
                Timber.tag("EditMetadata").i("Executing FFmpeg: $command")

                val session = FFmpegKit.execute(command)
                val returnCode = session.returnCode
                if (!ReturnCode.isSuccess(returnCode)) {
                    val logs = session.allLogsAsString
                    Timber.tag("EditMetadata").e("FFmpeg failed with return code $returnCode. Logs: $logs")
                    throw Exception("FFmpeg failed to write tags: $logs")
                }
                Timber.tag("EditMetadata").i("FFmpeg success! Tags written to ${tempFile.absolutePath}")

                if (mediaStoreUri != null) {
                    Timber.tag("EditMetadata").i("Writing to MediaStore: $mediaStoreUri")
                    try {
                        val outputStream = context.contentResolver.openOutputStream(mediaStoreUri)
                        if (outputStream == null) {
                            throw Exception("Failed to open output stream for MediaStore URI")
                        }
                        outputStream.use { out ->
                            tempFile.inputStream().use { inp -> inp.copyTo(out) }
                        }
                        Timber.tag("EditMetadata").i("MediaStore write successful")
                        onWriteSuccess(song, tempFile)
                    } catch (e: RecoverableSecurityException) {
                        Timber.tag("EditMetadata").w("RecoverableSecurityException, requesting permission")
                        pendingTempFile = tempFile
                        pendingMediaStoreUri = mediaStoreUri
                        pendingSong = song
                        withContext(Dispatchers.Main) {
                            writePermissionLauncher?.launch(
                                IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                            )
                        }
                    }
                } else {
                    Timber.tag("EditMetadata").i("No MediaStore URI, writing directly to original file: $path")
                    originalFile.outputStream().use { out ->
                        tempFile.inputStream().use { inp -> inp.copyTo(out) }
                    }
                    Timber.tag("EditMetadata").i("Direct write successful")
                    onWriteSuccess(song, tempFile)
                }
            } catch (e: Exception) {
                Timber.tag("EditMetadata").e(e, "Failed to write tags")
                withContext(Dispatchers.Main) { Toaster.e("Failed to save: ${e.message}") }
            } finally {
                if (pendingTempFile == null) {
                    tempFile.delete()
                }
                coverFile?.delete()
            }
        }
    }

    private fun performWriteToMediaStore(context: Context) {
        val tempFile = pendingTempFile ?: return
        val uri = pendingMediaStoreUri ?: return
        val song = pendingSong ?: return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    tempFile.inputStream().use { inp -> inp.copyTo(out) }
                }
                onWriteSuccess(song, tempFile)
            } catch (e: Exception) {
                Timber.tag("EditMetadata").e(e, "Failed to write after permission")
                tempFile.delete()
                withContext(Dispatchers.Main) { Toaster.e("Failed to save: ${e.message}") }
            } finally {
                pendingTempFile = null
                pendingMediaStoreUri = null
                pendingSong = null
            }
        }
    }

    private suspend fun onWriteSuccess(song: Song, tempFile: File) {
        tempFile.delete()

        Database.asyncTransaction {
            songTable.insertIgnore(song)
            val newTitle = fields.firstOrNull { it.key == "TITLE" }?.value?.text?.trim() ?: ""
            val newArtist = fields.firstOrNull { it.key == "ARTIST" }?.value?.text?.trim() ?: ""
            if (newTitle.isNotEmpty()) songTable.updateTitle(song.id, "$MODIFIED_PREFIX$newTitle")
            if (newArtist.isNotEmpty()) songTable.updateArtists(song.id, "$MODIFIED_PREFIX$newArtist")
        }

        withContext(Dispatchers.Main) { Toaster.done(); hideDialog() }
    }

    private data class ReadResult(
        val path: String?,
        val coverBytes: ByteArray?,
        val fields: List<EditableField>
    )

    private fun readTagsFromFile(context: Context, song: Song): ReadResult {
        val path = resolveFilePath(context, song.id)
            ?: return ReadResult(null, null, listOf(
                EditableField("TITLE", "TITLE", TextFieldValue(cleanPrefix(song.title)), true),
                EditableField("ARTIST", "ARTIST", TextFieldValue(song.artistsText?.let { cleanPrefix(it) } ?: ""), true)
            ))

        Timber.tag("EditMetadata").i("readTagsFromFile: song=${song.title}, path=$path")

        try {
            val result = mutableListOf<EditableField>()
            var cover: ByteArray? = null
            val seen = mutableSetOf<String>()

            try {
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(path)
                    cover = retriever.embeddedPicture
                }
                Timber.tag("EditMetadata").i("Cover art: ${if (cover != null) "${cover!!.size} bytes" else "none"}")
            } catch (e: Exception) {
                Timber.tag("EditMetadata").w(e, "Failed to read cover via MediaMetadataRetriever")
            }

            try {
                val session = FFprobeKit.getMediaInformation(path)
                val info = session.mediaInformation
                val tags = mutableMapOf<String, String>()

                info?.tags?.let { json ->
                    Timber.tag("EditMetadata").d("Global tags: ${json.length()} entries")
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        tags[key] = json.optString(key, "")
                    }
                }

                info?.streams?.forEach { stream ->
                    stream.tags?.let { json ->
                        Timber.tag("EditMetadata").d("Stream tags: ${json.length()} entries")
                        val keys = json.keys()
                        while (keys.hasNext()) {
                            val key = keys.next()
                            tags[key] = json.optString(key, "")
                        }
                    }
                }

                Timber.tag("EditMetadata").i("Total raw tags from FFprobeKit: ${tags.size}")
                tags.forEach { (key, value) ->
                    if (value.length < 200) {
                        Timber.tag("EditMetadata").d("  [$key] = ${value.take(80)}")
                    } else {
                        Timber.tag("EditMetadata").d("  [$key] = <${value.length} chars, skipped>")
                    }
                }

                val COVER_ART_TAG_KEYS = setOf("METADATA_BLOCK_PICTURE", "COVR", "APIC", "COVERART")
                tags.forEach { (key, value) ->
                    val upperKey = key.uppercase()
                    if (upperKey in COVER_ART_TAG_KEYS) return@forEach
                    val tagKey = when (upperKey) {
                        "TITLE" -> "TITLE"
                        "ARTIST" -> "ARTIST"
                        "ALBUM" -> "ALBUM"
                        "DATE", "YEAR" -> "DATE"
                        "TRACK", "TRACKNUMBER" -> "TRACK"
                        "DISCNUMBER", "TPOS", "DISK" -> "DISC_NUMBER"
                        "ALBUMARTIST", "ALBUM_ARTIST", "TPE2" -> "ALBUM_ARTIST"
                        "GENRE" -> "GENRE"
                        "COMPOSER" -> "COMPOSER"
                        "COPYRIGHT" -> "COPYRIGHT"
                        "COMMENT" -> "COMMENT"
                        "ENCODER" -> "ENCODER"
                        "EXPORTED" -> "EXPORTED"
                        "BPM", "TBPM", "TMPO" -> "BPM"
                        "ISRC", "TSRC" -> "ISRC"
                        "PUBLISHER", "LABEL", "TPUB" -> "PUBLISHER"
                        "LYRICIST", "TEXT" -> "LYRICIST"
                        "CONDUCTOR", "TPE3" -> "CONDUCTOR"
                        "REMIXER", "TPE4" -> "REMIXER"
                        else -> upperKey
                    }
                    if (tagKey !in seen && value.isNotEmpty()) {
                        seen += tagKey
                        result.add(EditableField(tagKey, tagKey, TextFieldValue(value), true))
                    }
                }
            } catch (e: Exception) {
                Timber.tag("EditMetadata").e(e, "FFprobeKit failed to read tags")
            }

            if (cover == null) {
                try {
                    val isOpusPath = path.endsWith(".opus", ignoreCase = true) || path.endsWith(".ogg", ignoreCase = true)
                    if (isOpusPath) {
                        val session = FFprobeKit.getMediaInformation(path)
                        val mbpTag = session.mediaInformation?.tags?.optString("METADATA_BLOCK_PICTURE", null)
                            ?: session.mediaInformation?.streams?.firstOrNull()?.tags?.optString("METADATA_BLOCK_PICTURE", null)
                        if (mbpTag != null) {
                            val decoded = Base64.decode(mbpTag, Base64.DEFAULT)
                            val buf = ByteBuffer.wrap(decoded).order(ByteOrder.BIG_ENDIAN)
                            if (buf.remaining() < 32) throw Exception("METADATA_BLOCK_PICTURE too short")
                            buf.getInt() // picture type
                            val mimeLen = buf.getInt()
                            if (buf.remaining() < mimeLen + 4) throw Exception("Truncated MIME in METADATA_BLOCK_PICTURE")
                            buf.position(buf.position() + mimeLen) // skip MIME
                            val descLen = buf.getInt()
                            if (buf.remaining() < descLen + 20) throw Exception("Truncated description in METADATA_BLOCK_PICTURE")
                            buf.position(buf.position() + descLen) // skip description
                            buf.getInt() // width
                            buf.getInt() // height
                            buf.getInt() // color depth
                            buf.getInt() // indexed colors
                            val dataLen = buf.getInt()
                            if (buf.remaining() < dataLen) throw Exception("Truncated image data in METADATA_BLOCK_PICTURE")
                            val imgData = ByteArray(dataLen)
                            buf.get(imgData)
                            cover = imgData
                            Timber.tag("EditMetadata").i("Cover art from METADATA_BLOCK_PICTURE: ${imgData.size} bytes")
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("EditMetadata").w(e, "Failed to extract cover from METADATA_BLOCK_PICTURE")
                }
            }

            result.add(EditableField("COVERART", "covr", TextFieldValue(""), true, isCoverArt = true))

            STANDARD_TAGS.forEach { defaultKey ->
                if (defaultKey !in seen) {
                    val fallback = when (defaultKey) {
                        "TITLE" -> cleanPrefix(song.title)
                        "ARTIST" -> song.artistsText?.let { cleanPrefix(it) } ?: ""
                        "EXPORTED" -> "N-Zik"
                        else -> ""
                    }
                    result.add(EditableField(defaultKey, defaultKey, TextFieldValue(fallback), true))
                }
            }

            Timber.tag("EditMetadata").i("Total fields: ${result.size}")
            result.forEach { Timber.tag("EditMetadata").d("  [${it.key}] ${it.value.text.take(80)}") }

            return ReadResult(path, cover, result)
        } catch (e: Exception) {
            Timber.tag("EditMetadata").e(e, "Failed to read tags")
            return ReadResult(path, null, listOf(
                EditableField("TITLE", "TITLE", TextFieldValue(cleanPrefix(song.title)), true),
                EditableField("ARTIST", "ARTIST", TextFieldValue(song.artistsText?.let { cleanPrefix(it) } ?: ""), true)
            ))
        }
    }

}
