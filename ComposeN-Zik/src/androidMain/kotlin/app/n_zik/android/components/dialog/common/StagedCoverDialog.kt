package app.n_zik.android.components.dialog.common

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val update: Database.(String) -> Unit,
)

/** Keeps edited gallery content temporary until the cover dialog itself is confirmed. */
abstract class StagedCoverDialog internal constructor(
    activeState: MutableState<Boolean>,
    valueState: MutableState<TextFieldValue>,
    private val context: Context,
    private val scope: CoroutineScope,
    private val getTarget: () -> CoverEditTarget?,
    private val save: (Context, Uri, File, String, String) -> Uri?,
) : RenameDialog(activeState, valueState) {
    private var target: CoverEditTarget? = null
    private var profile: String? = null
    private var generation = 0
    private var pickedGeneration = 0
    private var draft: PreparedImage? = null
    private var editJob: Job? = null
    private var saving by mutableStateOf(false)

    override val keyboardOption = KeyboardOptions.Default
    override val iconId = R.drawable.cover_edit
    override val messageId = R.string.metadata_action_change_cover
    override val menuIconTitle: String
        @Composable get() = stringResource(messageId)
    override val dialogTitle: String
        @Composable get() = menuIconTitle

    override fun showDialog() {
        discardDraft()
        target = getTarget() ?: return
        profile = getActiveProfile(context)
        value = TextFieldValue(cleanPrefix(target?.currentUrl.orEmpty()))
        errorMessage = ""
        super.showDialog()
    }

    override fun hideDialog() {
        super.hideDialog()
        discardDraft()
        value = TextFieldValue(cleanPrefix(getTarget()?.currentUrl.orEmpty()))
    }

    private fun discardDraft() {
        generation++
        editJob?.cancel()
        editJob = null
        draft?.close()
        draft = null
    }

    private fun current(token: Int, id: String, profileId: String): Boolean =
        isActive && token == generation && getTarget()?.id == id && getActiveProfile(context) == profileId

    override fun onSet(newValue: String) {
        if (saving || editJob?.isActive == true) return
        val selected = target ?: return
        val profileId = profile ?: return
        val token = generation
        if (!current(token, selected.id, profileId)) {
            hideDialog()
            return
        }
        val edited = draft?.takeIf { it.uri.toString() == newValue }
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
                            save(context, edited.uri, file, directory.name, file.name)?.toString()
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

    @Composable
    override fun DialogBody() {
        DisposableEffect(this) { onDispose { discardDraft() } }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val selected = target
            val profileId = profile
            val token = pickedGeneration
            if (uri != null && selected != null && profileId != null && current(token, selected.id, profileId)) {
                editJob?.cancel()
                editJob = scope.launch {
                    val edited = PickedImageEditor.edit(context, uri) ?: return@launch
                    if (!current(token, selected.id, profileId)) {
                        edited.close()
                        return@launch
                    }
                    draft?.close()
                    draft = edited
                    value = TextFieldValue(edited.uri.toString())
                    errorMessage = ""
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            super.DialogBody()
            GlassDialogOutlinedButton(
                onClick = {
                    if (!saving) {
                        editJob?.cancel()
                        pickedGeneration = ++generation
                        launcher.launch("image/*")
                    }
                },
                modifier = Modifier.fillMaxWidth().glassDialogButtonBackground(
                    backgroundColor = Color.Transparent,
                    shape = uiRoundnessShape(),
                    filmColor = surfaceColor(colorPalette().background2)
                ),
                shape = uiRoundnessShape(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colorPalette().text),
                border = glassDialogButtonBorder(BorderStroke(1.dp, colorPalette().textSecondary))
            ) {
                Icon(painter = icon, contentDescription = null, modifier = Modifier.size(24.dp).padding(end = 8.dp))
                Text(stringResource(R.string.thumbnail_action_pick_from_gallery))
            }
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
