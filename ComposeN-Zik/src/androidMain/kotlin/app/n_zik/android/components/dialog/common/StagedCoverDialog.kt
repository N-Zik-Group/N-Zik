package app.n_zik.android.components.dialog.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.MODIFIED_PREFIX
import app.it.fast4x.rimusic.cleanPrefix
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.song.RenameDialog
import app.n_zik.android.components.glass.GlassDialogOutlinedButton
import app.n_zik.android.components.glass.glassDialogButtonBackground
import app.n_zik.android.components.glass.glassDialogButtonBorder
import app.n_zik.android.components.image.CoverImageExport
import app.n_zik.android.components.image.CoverImageExportActivity
import app.n_zik.android.components.image.PickedImageEditor
import app.n_zik.android.components.image.PreparedImage
import app.n_zik.android.components.image.ownedCoverMutationMutex
import app.n_zik.android.components.image.restoreOwnedCover
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.profiles.coversDir
import app.n_zik.android.surfaceColor
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

internal data class CoverEditTarget(
    val id: String,
    val currentUrl: String?,
    val currentBytes: ByteArray? = null,
    val update: Database.(String) -> Unit = {},
)

/** Keeps edited gallery content temporary until the cover dialog itself is confirmed. */
abstract class StagedCoverDialog internal constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val context: Context,
    private val scope: CoroutineScope,
    private val getTarget: () -> CoverEditTarget?,
    private val save: ((Context, Uri, File, String, String) -> Uri?)? = null,
    private val applyDraft: (suspend (CoverEditTarget, Uri, () -> Boolean) -> Boolean)? = null,
    private val allowRemoteImport: Boolean = false,
) : RenameDialog(activeState, valueState) {
    private var target: CoverEditTarget? = null
    private var profile: String? = null
    private var generation = 0
    private var dialogSession by mutableStateOf(0)
    private var pickedGeneration by mutableStateOf<Int?>(null)
    private var exportInProgress by mutableStateOf(false)
    private var draft by mutableStateOf<PreparedImage?>(null)
    private var editJob by mutableStateOf<Job?>(null)
    private var saving by mutableStateOf(false)

    override val keyboardOption = KeyboardOptions.Default
    override val iconId = R.drawable.cover_edit
    override val messageId = R.string.metadata_action_change_cover
    override val menuIconTitle: String
        @Composable get() = stringResource(messageId)
    override val dialogTitle: String
        @Composable get() = menuIconTitle

    override fun showDialog() {
        if (isActive || saving) return
        discardDraft()
        target = getTarget() ?: return
        profile = getActiveProfile(context)
        value = TextFieldValue(cleanPrefix(target?.currentUrl.orEmpty()))
        errorMessage = ""
        dialogSession++
        super.showDialog()
    }

    override fun hideDialog() {
        super.hideDialog()
        discardDraft()
        target = null
        profile = null
        value = TextFieldValue(cleanPrefix(getTarget()?.currentUrl.orEmpty()))
    }

    private fun discardDraft() {
        generation++
        editJob?.cancel()
        editJob = null
        pickedGeneration = null
        exportInProgress = false
        draft?.close()
        draft = null
    }

    private fun current(token: Int, id: String, profileId: String): Boolean =
        isActive && token == generation && getTarget()?.id == id && getActiveProfile(context) == profileId

    override fun onSet(newValue: String) {
        if (!actionsEnabled) return
        val selected = target ?: return
        val profileId = profile ?: return
        val token = generation
        if (!current(token, selected.id, profileId)) {
            hideDialog()
            return
        }
        val edited = draft?.takeIf { it.uri.toString() == newValue }
        val customApply = applyDraft
        if (customApply != null) {
            val remote = if (allowRemoteImport) httpsCoverUri(newValue) else null
            if (edited == null && remote == null) return
            saving = true
            scope.launch {
                try {
                    val isCurrent = { current(token, selected.id, profileId) }
                    val applied = if (edited != null) {
                        customApply(selected, edited.uri, isCurrent)
                    } else {
                        PickedImageEditor.prepareSource(context, requireNotNull(remote)).use { source ->
                            isCurrent() && customApply(selected, source.uri, isCurrent)
                        }
                    }
                    if (applied) {
                        if (isCurrent()) hideDialog()
                    } else if (isCurrent()) {
                        Toaster.e(R.string.metadata_save_failed)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("StagedCoverDialog").e(error, "Could not apply edited cover")
                    Toaster.e(R.string.metadata_save_failed)
                } finally {
                    saving = false
                }
            }
            return
        }
        if (edited == null) {
            super.onSet(newValue)
            if (errorMessage.isNotEmpty()) return
            // A discarded draft's temporary URI must never be accepted as typed cover content.
            if (isTemporaryImageUri(context, newValue)) return
        }
        saving = true
        scope.launch {
            ownedCoverMutationMutex.withLock {
                var replacedFile: File? = null
                var previousBytes: ByteArray? = null
                var committed = false
                try {
                    val permanent = withContext(NzikDispatchers.DATA) {
                        if (!current(token, selected.id, profileId)) return@withContext null
                        if (edited == null) newValue else {
                            val directory = coversDir(context, profileId)
                            val file = File(directory, "cover_${selected.id}.jpg").canonicalFile
                            require(file.parentFile == directory.canonicalFile)
                            previousBytes = file.takeIf(File::exists)?.readBytes()
                            replacedFile = file
                            requireNotNull(save)(context, edited.uri, file, directory.name, file.name)?.toString()
                        }
                    } ?: run {
                        Toaster.e(R.string.metadata_save_failed)
                        return@withLock
                    }
                    if (!current(token, selected.id, profileId)) return@withLock
                    withContext(NonCancellable + NzikDispatchers.DATA) {
                        Database.transaction {
                            check(current(token, selected.id, profileId)) { "Cover target changed" }
                            selected.update(this, "$MODIFIED_PREFIX$permanent")
                        }
                        committed = true
                        for (quality in ImageCacheFactory.NetworkQuality.entries) {
                            ImageCacheFactory.clearCacheForKey(permanent, quality)
                            Uri.parse(permanent).path?.let { ImageCacheFactory.clearCacheForKey(it, quality) }
                        }
                    }
                    Toaster.done()
                    hideDialog()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("StagedCoverDialog").e(error, "Could not apply edited cover")
                    Toaster.e(R.string.metadata_save_failed)
                } finally {
                    if (!committed) {
                        val file = replacedFile
                        if (file != null) withContext(NonCancellable + NzikDispatchers.DATA) {
                            try {
                                restoreOwnedCover(file, previousBytes)
                                for (quality in ImageCacheFactory.NetworkQuality.entries) {
                                    ImageCacheFactory.clearCacheForKey(Uri.fromFile(file).toString(), quality)
                                    ImageCacheFactory.clearCacheForKey(file.absolutePath, quality)
                                }
                            } catch (error: Exception) {
                                Timber.tag("StagedCoverDialog").e(error, "Could not restore previous cover")
                            }
                        }
                    }
                    saving = false
                }
            }
        }
    }

    private fun startEdit(token: Int, edit: suspend () -> PreparedImage?) {
        val selected = target ?: return
        val profileId = profile ?: return
        if (!current(token, selected.id, profileId)) return
        editJob = scope.launch {
            try {
                val edited = edit() ?: return@launch
                if (!current(token, selected.id, profileId)) {
                    edited.close()
                    return@launch
                }
                draft?.close()
                draft = edited
                value = TextFieldValue(edited.uri.toString())
                errorMessage = ""
            } finally {
                if (token == generation) editJob = null
            }
        }
    }

    private val actionsEnabled: Boolean
        get() = !saving && editJob?.isActive != true && pickedGeneration == null && !exportInProgress

    private fun httpsCoverUri(text: String): Uri? = Uri.parse(text.trim()).takeIf {
        it.scheme.equals("https", ignoreCase = true) && !it.host.isNullOrBlank()
    }

    private fun currentCoverUri(): Uri? {
        val savedUrl = cleanPrefix(target?.currentUrl.orEmpty())
        val entered = value.text.trim()
        val currentDraft = draft?.uri?.takeIf { it.toString() == entered }
        if (currentDraft != null) return currentDraft
        if (allowRemoteImport) return httpsCoverUri(entered)
        val enteredUrl = Uri.parse(entered)
        if (enteredUrl.scheme.equals("https", ignoreCase = true) ||
            enteredUrl.scheme.equals("http", ignoreCase = true)
        ) return enteredUrl
        if (savedUrl.isBlank()) return null
        return if (savedUrl.startsWith(File.separator)) Uri.fromFile(File(savedUrl)) else Uri.parse(savedUrl)
    }

    internal fun editCurrentCover() {
        if (!actionsEnabled) return
        val uri = currentCoverUri()
        val bytes = target?.currentBytes
        if (uri != null) startEdit(++generation) { PickedImageEditor.edit(context, uri) }
        else if (bytes != null) startEdit(++generation) { PickedImageEditor.edit(context, bytes) }
    }

    private fun prepareExport(launchPicker: (Intent) -> Unit) {
        if (!actionsEnabled) return
        val selected = target ?: return
        val profileId = profile ?: return
        val uri = currentCoverUri()
        val bytes = selected.currentBytes
        val token = generation
        if (!current(token, selected.id, profileId)) return
        editJob = scope.launch {
            try {
                val export = when {
                    uri != null -> CoverImageExport.prepare(context, uri)
                    bytes != null -> CoverImageExport.prepare(context, bytes)
                    else -> return@launch
                }
                if (!current(token, selected.id, profileId)) {
                    export.close()
                    return@launch
                }
                exportInProgress = true
                CoverImageExportActivity.launch(context, export, profileId, launchPicker)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                exportInProgress = false
                Timber.tag("StagedCoverDialog").e(error, "Could not prepare cover export")
                Toaster.e(R.string.cover_download_failed)
            } finally {
                if (token == generation) editJob = null
            }
        }
    }

    @Composable
    override fun Buttons() = ButtonRow(
        buttons = listOf(
            DialogButton(
                text = stringResource(R.string.common_action_cancel),
                onClick = ::hideDialog,
                danger = true,
            ),
            DialogButton(
                text = submitText,
                onClick = { onSet(value.text) },
                primary = true,
                enabled = actionsEnabled && (
                    applyDraft == null || draft?.uri?.toString() == value.text ||
                        (allowRemoteImport && httpsCoverUri(value.text) != null)
                ),
            ),
        ),
    )

    @Composable
    override fun DialogBody() {
        DisposableEffect(this) { onDispose { discardDraft() } }
        key(dialogSession) { CoverActions() }
    }

    @Composable
    private fun CoverActions() {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val token = pickedGeneration
            pickedGeneration = null
            if (uri != null && token != null) startEdit(token) { PickedImageEditor.edit(context, uri) }
        }
        val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            exportInProgress = false
        }
        val canEdit = dialogInputEnabled && actionsEnabled
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (applyDraft == null || allowRemoteImport) super.DialogBody()
            if (currentCoverUri() != null || target?.currentBytes != null) {
                CoverAction(R.string.image_editor_title, R.drawable.cover_edit, canEdit, ::editCurrentCover)
                CoverAction(R.string.cover_action_download, R.drawable.download, canEdit) { prepareExport { exportLauncher.launch(it) } }
            }
            CoverAction(R.string.thumbnail_action_pick_from_gallery, R.drawable.image, canEdit) {
                if (actionsEnabled) {
                    pickedGeneration = ++generation
                    try {
                        launcher.launch("image/*")
                    } catch (error: ActivityNotFoundException) {
                        reportPickerFailure(error)
                    } catch (error: SecurityException) {
                        reportPickerFailure(error)
                    }
                }
            }
        }
    }

    private fun reportPickerFailure(error: RuntimeException) {
        pickedGeneration = null
        Timber.tag("StagedCoverDialog").w(error, "Could not open image picker")
        Toaster.e(R.string.image_editor_failed)
    }

    @Composable
    private fun CoverAction(label: Int, icon: Int, enabled: Boolean, onClick: () -> Unit) {
        GlassDialogOutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().glassDialogButtonBackground(
                backgroundColor = Color.Transparent,
                shape = uiRoundnessShape(),
                filmColor = surfaceColor(colorPalette().background2)
            ),
            shape = uiRoundnessShape(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colorPalette().text),
            border = glassDialogButtonBorder(BorderStroke(1.dp, colorPalette().textSecondary))
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, modifier = Modifier.size(24.dp).padding(end = 8.dp))
            Text(stringResource(label))
        }
    }
}

internal fun isTemporaryImageUri(context: Context, value: String): Boolean {
    val uri = Uri.parse(value)
    if (uri.scheme == "content" && uri.authority == "${context.packageName}.fileprovider") return true
    if (uri.scheme != "file") return false
    return try {
        val file = File(uri.path ?: return true).canonicalPath
        val cache = context.cacheDir.canonicalPath
        val codeCache = context.codeCacheDir.canonicalPath
        file == cache || file.startsWith(cache + File.separator) ||
            file == codeCache || file.startsWith(codeCache + File.separator)
    } catch (error: Exception) {
        Timber.tag("StagedCoverDialog").w(error, "Invalid cover URI")
        true
    }
}
