package app.n_zik.android.components.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.it.fast4x.rimusic.ui.screens.settings.OtherSettingsEntry
import app.it.fast4x.rimusic.utils.rememberPreference
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ConfirmDialog
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.enums.DownloadQualityFormat
import app.n_zik.android.enums.downloadQualityFormatKey
import app.n_zik.android.typography
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Download quality setting, independent from the streaming audio quality.
 * Renders a settings entry showing the currently selected [DownloadQualityFormat]
 * and a value selector dialog to change it.
 *
 * @param onQualityChanged invoked whenever a value is selected, so the screen can surface
 * the same "restart player service" prompt as the audio quality change
 */
@Composable
fun DownloadQualityEntry(onQualityChanged: () -> Unit = {}) {
    var dialogVisible by rememberSaveable { mutableStateOf(false) }
    var downloadQualityFormat by rememberPreference(downloadQualityFormatKey, DownloadQualityFormat.Auto)

    OtherSettingsEntry(
        title = stringResource(R.string.download_quality_format),
        text = when (downloadQualityFormat) {
            DownloadQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
            DownloadQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
            DownloadQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
        },
        icon = R.drawable.download,
        onClick = { dialogVisible = true }
    )

    if (dialogVisible) {
        ValueSelectorDialog(
            title = stringResource(R.string.download_quality_format),
            values = DownloadQualityFormat.values().toList(),
            selectedValue = downloadQualityFormat,
            onValueSelected = {
                downloadQualityFormat = it
                onQualityChanged()
                dialogVisible = false
            },
            onDismiss = { dialogVisible = false },
            valueText = {
                when (it) {
                    DownloadQualityFormat.Auto -> stringResource(R.string.audio_quality_automatic)
                    DownloadQualityFormat.High -> stringResource(R.string.audio_quality_format_high)
                    DownloadQualityFormat.Low -> stringResource(R.string.audio_quality_format_low)
                }
            }
        )
    }
}

/**
 * "Update downloads" entry: re-downloads every completed download that does not match the
 * selected [DownloadQualityFormat]. Shows a toast when nothing is non-compliant, otherwise a
 * confirmation dialog with the number of affected downloads.
 */
@UnstableApi
@Composable
fun UpdateDownloadsButton() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dialogVisible = rememberSaveable { mutableStateOf(false) }
    val dialogCount = remember { mutableIntStateOf(0) }
    var isChecking by remember { mutableStateOf(false) }

    OtherSettingsEntry(
        title = stringResource(R.string.update_downloads),
        text = stringResource(R.string.update_downloads_description),
        icon = R.drawable.download,
        onClick = {
            if (isChecking) return@OtherSettingsEntry
            isChecking = true
            coroutineScope.launch {
                val count = runCatching { MyDownloadHelper.countNonCompliantDownloads() }.getOrDefault(0)
                isChecking = false
                if (count == 0) {
                    Toaster.i(R.string.update_downloads_nothing)
                } else {
                    dialogCount.value = count
                    dialogVisible.value = true
                }
            }
        }
    )

    if (dialogVisible.value) {
        val dialog = remember(dialogCount.value) {
            UpdateDownloadsDialog(
                activeState = dialogVisible,
                count = dialogCount.value,
                onConfirmAction = {
                    NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                        runCatching {
                            MyDownloadHelper.updateDownloads(context)
                        }.onFailure { e ->
                            Timber.tag("UpdateDownloads").e(e, "updateDownloads failed")
                            Toaster.e(R.string.error_an_unknown_playback_error_has_occurred)
                        }
                    }
                }
            )
        }
        dialog.Render()
    }
}

/**
 * Confirmation dialog shown before re-downloading all downloads whose recorded quality
 * does not match the selected [DownloadQualityFormat].
 */
@UnstableApi
private class UpdateDownloadsDialog(
    activeState: MutableState<Boolean>,
    private val count: Int,
    private val onConfirmAction: () -> Unit
) : ConfirmDialog {

    override var isActive: Boolean by activeState

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.update_downloads_count, count)

    override fun onConfirm() {
        onConfirmAction()
        hideDialog()
    }

    @Composable
    override fun DialogBody() {
        BasicText(
            text = stringResource(R.string.update_downloads_description),
            style = typography().xs.copy(color = colorPalette().text),
            modifier = Modifier.padding(vertical = 20.dp)
        )
    }
}
