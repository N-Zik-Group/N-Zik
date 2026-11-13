package app.n_zik.android.components.dialog.song

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import app.n_zik.android.compose.persist.findActivityNullable
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.appTextResources
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.DialogActionList
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogPresence
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.dialog.common.InputDialogConstraints
import app.n_zik.android.components.dialog.common.TextInputDialog
import app.n_zik.android.components.dialog.common.dialogInputEnabled
import app.n_zik.android.components.player.lyrics.ImportedLyrics
import app.n_zik.android.components.player.lyrics.LyricsFileCodec
import app.n_zik.android.core.database.Database
import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers

/** Offers manual editing and local Lyricsfile transfer for one song. */
class EditLyricsDialog private constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val mediaId: String,
    private val getLyricsType: () -> LyricsType,
    private val getActiveMode: () -> LyricsType,
    private val getLyrics: () -> Lyrics?,
    private val getTitle: () -> String?,
    private val getArtist: () -> String?,
    private val getEnsureSongInserted: () -> (() -> Unit),
    pendingFileActionState: MutableState<PendingFileAction?>,
) : TextInputDialog(InputDialogConstraints.ALL) {

    private data class PendingFileAction(
        val songId: String,
        val stagedExportName: String? = null,
        val documentUri: String? = null,
    )

    companion object {
        private const val TAG = "EditLyricsDialog"
        private const val LYRICSFILE_MIME_TYPE = "application/yaml"
        private const val EXPORT_STAGING_FOLDER = "lyricsfile-export"
        private const val MAX_STAGED_EXPORTS = 16
        private const val MAX_STAGING_AGE_MS = 24 * 60 * 60 * 1000L
        private val invalidFilenameCharacters = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
        private val stagingNamePattern = Regex("lyrics-[a-f0-9-]{36}\\.yaml")
        private val exportStagingLock = Any()
        private val pendingFileActionSaver = listSaver<MutableState<PendingFileAction?>, String>(
            save = { state ->
                state.value?.let { listOf(it.songId, it.stagedExportName.orEmpty(), it.documentUri.orEmpty()) }
                    .orEmpty()
            },
            restore = { values ->
                mutableStateOf(
                    if (values.size == 3) {
                        PendingFileAction(
                            songId = values[0],
                            stagedExportName = values[1].ifEmpty { null },
                            documentUri = values[2].ifEmpty { null }
                        )
                    } else {
                        null
                    }
                )
            }
        )

        /** Row saved when the user confirms an edit; always flagged so automatic fetches keep it (gh-765). */
        internal fun editedLyrics(mediaId: String, lyricsType: LyricsType, newValue: String): Lyrics =
            Lyrics(
                songId = mediaId,
                type = lyricsType.name,
                data = newValue,
                isEdited = true
            )

        @Composable
        operator fun invoke(
            mediaId: String,
            lyricsType: LyricsType,
            getLyrics: () -> Lyrics?,
            ensureSongInserted: () -> Unit,
            title: String? = null,
            artist: String? = null,
            activeMode: LyricsType = lyricsType,
        ): EditLyricsDialog {
            val currentLyricsType = rememberUpdatedState(lyricsType)
            val currentMode = rememberUpdatedState(activeMode)
            val currentLyrics = rememberUpdatedState(getLyrics)
            val currentEnsureSongInserted = rememberUpdatedState(ensureSongInserted)
            val currentTitle = rememberUpdatedState(title)
            val currentArtist = rememberUpdatedState(artist)
            val pendingAction = key(mediaId) {
                rememberSaveable(saver = pendingFileActionSaver) { mutableStateOf<PendingFileAction?>(null) }
            }
            return remember(mediaId) {
                EditLyricsDialog(
                    activeState = mutableStateOf(false),
                    valueState = mutableStateOf(TextFieldValue()),
                    mediaId = mediaId,
                    getLyricsType = { currentLyricsType.value },
                    getActiveMode = { currentMode.value },
                    getLyrics = { currentLyrics.value() },
                    getTitle = { currentTitle.value },
                    getArtist = { currentArtist.value },
                    getEnsureSongInserted = { currentEnsureSongInserted.value },
                    pendingFileActionState = pendingAction,
                )
            }
        }

        /** Reads a bounded UTF-8 document without relying on a reported content length. */
        internal fun readLyricsFile(input: InputStream): String {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(count <= LyricsFileCodec.MAX_FILE_BYTES - output.size()) {
                    "Lyricsfile exceeds the input limit"
                }
                output.write(buffer, 0, count)
            }
            return output.toByteArray().decodeToString(throwOnInvalidSequence = true)
        }

        private fun stagedExportFile(context: Context, fileName: String): File {
            require(stagingNamePattern.matches(fileName))
            return File(File(context.cacheDir, EXPORT_STAGING_FOLDER), fileName)
        }

        private fun stageExport(file: File, contents: ByteArray) {
            val directory = checkNotNull(file.parentFile)
            synchronized(exportStagingLock) {
                check(directory.isDirectory || directory.mkdirs())
                val cutoff = System.currentTimeMillis() - MAX_STAGING_AGE_MS
                val oldFiles = directory.listFiles().orEmpty().filter { stagingNamePattern.matches(it.name) }
                val remaining = oldFiles.count { it.lastModified() >= cutoff || !it.delete() }
                check(remaining < MAX_STAGED_EXPORTS)
                file.writeBytes(contents)
            }
        }

        private fun deleteStagedExport(context: Context, fileName: String?) {
            if (fileName == null || !stagingNamePattern.matches(fileName)) return
            val appContext = context.applicationContext
            NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                stagedExportFile(appContext, fileName).delete()
            }
        }

        private fun modeLabel(type: LyricsType): Int = when (type) {
            LyricsType.Auto -> R.string.lyrics_mode_automatic_label
            LyricsType.Karaoke -> R.string.lyrics_mode_karaoke_label
            LyricsType.Synced -> R.string.lyrics_mode_synchronized_label
            LyricsType.Unsynced -> R.string.lyrics_mode_unsynchronized_label
        }
    }

    private enum class Stage { Actions, Editor }

    private var showActions by mutableStateOf(false)
    private var editorLyricsType by mutableStateOf(getLyricsType())
    private var isImportPreview by mutableStateOf(false)
    private var stagingExport = false
    private var pendingFileAction by pendingFileActionState
    private val fileActionInProgress: Boolean get() = stagingExport || pendingFileAction != null

    override var value: TextFieldValue by valueState
    override var isActive: Boolean by activeState

    override val keyboardOption: KeyboardOptions = KeyboardOptions.Default
    override val dialogTitle: String
        @Composable
        get() = if (isImportPreview) {
            val format = when (editorLyricsType) {
                LyricsType.Karaoke -> R.string.lyrics_provider_format_word_timed
                LyricsType.Synced -> R.string.lyrics_provider_format_line_timed
                LyricsType.Unsynced -> R.string.lyrics_provider_format_plain_text
                LyricsType.Auto -> R.string.lyrics_mode_automatic_label
            }
            stringResource(R.string.lyrics_editor_import_preview, stringResource(format))
        } else {
            stringResource(R.string.lyrics_editor_title)
        }

    /** Opens the three editing actions for the current song. */
    fun onShortClick() {
        if (!isActive && !fileActionInProgress) showActions = true
    }

    override fun showDialog() {
        if (fileActionInProgress) return
        openEditor(getLyricsType(), currentLyrics()?.data.orEmpty())
    }

    private fun currentLyrics(): Lyrics? = getLyrics()?.takeIf { it.songId == mediaId }

    private fun openEditor(type: LyricsType, text: String, importPreview: Boolean = false) {
        showActions = false
        editorLyricsType = type
        isImportPreview = importPreview
        value = TextFieldValue(text)
        errorMessage = ""
        super.showDialog()
    }

    @Composable
    override fun Render() = key(mediaId) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val activity = remember(context) { context.findActivityNullable() }

        DisposableEffect(activity) {
            onDispose {
                if (activity?.isChangingConfigurations != true) {
                    deleteStagedExport(context, pendingFileAction?.stagedExportName)
                }
            }
        }

        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            val action = pendingFileAction ?: return@rememberLauncherForActivityResult
            if (action.songId != mediaId || action.stagedExportName != null) return@rememberLauncherForActivityResult
            pendingFileAction = uri?.let { action.copy(documentUri = it.toString()) }
        }
        val exportLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(LYRICSFILE_MIME_TYPE)
        ) { uri: Uri? ->
            val action = pendingFileAction ?: return@rememberLauncherForActivityResult
            val stagedName = action.stagedExportName ?: return@rememberLauncherForActivityResult
            if (action.songId != mediaId) return@rememberLauncherForActivityResult
            if (uri == null) deleteStagedExport(context, stagedName)
            pendingFileAction = uri?.let { action.copy(documentUri = it.toString()) }
        }

        LaunchedEffect(pendingFileAction) {
            val action = pendingFileAction ?: return@LaunchedEffect
            val uri = action.documentUri?.let(Uri::parse) ?: return@LaunchedEffect
            val stagedName = action.stagedExportName
            try {
                if (action.songId == mediaId) {
                    if (stagedName == null) {
                        val imported: ImportedLyrics = withContext(NzikDispatchers.DATA) {
                            checkNotNull(context.contentResolver.openInputStream(uri)).use {
                                LyricsFileCodec.decode(readLyricsFile(it))
                            }
                        }
                        openEditor(imported.type, imported.text, importPreview = true)
                    } else {
                        withContext(NzikDispatchers.DATA) {
                            val stagedFile = stagedExportFile(context, stagedName)
                            check(stagedFile.isFile && stagedFile.length() in 1L..LyricsFileCodec.MAX_FILE_BYTES.toLong())
                            stagedFile.inputStream().use { input ->
                                checkNotNull(context.contentResolver.openOutputStream(uri, "wt"))
                                    .use { output -> input.copyTo(output) }
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                // A recreated activity can resume the same saved action.
                throw cancelled
            } catch (failure: Exception) {
                Timber.tag(TAG).w("Lyricsfile transfer failed (%s)", failure.javaClass.simpleName)
                val errorMessage = if (stagedName == null) {
                    R.string.lyrics_editor_import_error
                } else {
                    R.string.lyrics_editor_export_error
                }
                Toaster.e(context.getString(errorMessage))
            }
            deleteStagedExport(context, stagedName)
            pendingFileAction = null
        }

        val stage = when {
            isActive -> Stage.Editor
            showActions -> Stage.Actions
            else -> null
        }
        DialogPresence(target = stage) { visibleStage ->
            when (visibleStage) {
                Stage.Editor -> super.Render()
                Stage.Actions -> ActionDialog(
                    onImport = openPicker@{
                        if (fileActionInProgress) return@openPicker
                        showActions = false
                        pendingFileAction = PendingFileAction(mediaId)
                        try {
                            importLauncher.launch(arrayOf("*/*"))
                        } catch (failure: Exception) {
                            pendingFileAction = null
                            Timber.tag(TAG).w("Lyricsfile picker failed (%s)", failure.javaClass.simpleName)
                            Toaster.e(context.getString(R.string.lyrics_editor_import_error))
                        }
                    },
                    onExport = {
                        val row = currentLyrics()
                        if (!fileActionInProgress && row != null && !row.data.isNullOrBlank()) {
                            val title = getTitle()
                            val artist = getArtist()
                            showActions = false
                            stagingExport = true
                            scope.launch(NzikDispatchers.UI) {
                                val stagedName = "lyrics-${UUID.randomUUID()}.yaml"
                                var pickerLaunched = false
                                try {
                                    withContext(NzikDispatchers.DATA) {
                                        val encoded = LyricsFileCodec.encode(row, title, artist).encodeToByteArray()
                                        require(encoded.size <= LyricsFileCodec.MAX_FILE_BYTES)
                                        stageExport(stagedExportFile(context, stagedName), encoded)
                                    }
                                    pendingFileAction = PendingFileAction(mediaId, stagedExportName = stagedName)
                                    val baseName = title.orEmpty().replace(invalidFilenameCharacters, "_")
                                        .take(100).trim(' ', '.').ifBlank { "lyrics" }
                                    exportLauncher.launch("$baseName.lyricsfile.yaml")
                                    pickerLaunched = true
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    Timber.tag(TAG).w("Lyricsfile export failed (%s)", failure.javaClass.simpleName)
                                    Toaster.e(context.getString(R.string.lyrics_editor_export_error))
                                } finally {
                                    if (!pickerLaunched) {
                                        pendingFileAction = null
                                        deleteStagedExport(context, stagedName)
                                    }
                                    stagingExport = false
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    @Composable
    private fun ActionDialog(onImport: () -> Unit, onExport: () -> Unit) {
        DialogActionList(
            onDismiss = { showActions = false },
            title = stringResource(R.string.lyrics_action_edit),
            actions = listOf(
                DialogButton(stringResource(R.string.lyrics_editor_modify_text), ::showDialog),
                DialogButton(stringResource(R.string.lyrics_editor_import_file), onImport),
                DialogButton(
                    stringResource(R.string.lyrics_editor_export_file),
                    onExport,
                    enabled = !currentLyrics()?.data.isNullOrBlank()
                )
            )
        )
    }

    @Composable
    override fun DialogBody() {
        Column {
            if (isImportPreview) {
                Text(
                    text = stringResource(R.string.lyrics_editor_import_preview_note),
                    style = typography().xs.copy(color = colorPalette().textSecondary),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            TextField(
                enabled = dialogInputEnabled,
                value = value,
                onValueChange = {
                    value = it
                    errorMessage = ""
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.lyrics_editor_title),
                        color = colorPalette().textDisabled
                    )
                },
                maxLines = Int.MAX_VALUE,
                singleLine = false,
                keyboardOptions = keyboardOption,
                modifier = Modifier.fillMaxWidth().heightIn(
                    max = (LocalConfiguration.current.screenHeightDp * 0.4f).dp
                ),
                colors = InputDialog.defaultTextFieldColors(),
                isError = errorMessage.isNotEmpty()
            )
            if (errorMessage.isNotEmpty()) {
                Text(text = errorMessage, style = typography().xs.copy(color = colorPalette().text))
            }
        }
    }

    override fun onSet(newValue: String) {
        super.onSet(newValue)
        if (errorMessage.isNotEmpty()) return
        val insertSong = getEnsureSongInserted()
        val row = editedLyrics(mediaId, editorLyricsType, newValue)
        val importedModeLabel = modeLabel(editorLyricsType).takeIf {
            isImportPreview && editorLyricsType != getActiveMode()
        }
        Database.asyncTransaction {
            insertSong()
            Database.lyricsTable.upsert(row)
            if (importedModeLabel != null) {
                NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
                    val strings = appTextResources()
                    Toaster.s(strings.getString(
                        R.string.lyrics_editor_import_saved_other_mode,
                        strings.getString(importedModeLabel)
                    ))
                }
            }
        }
        hideDialog()
    }
}
