package app.n_zik.android.components.settings

import app.n_zik.android.components.settings.common.settingsSearchTarget

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
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
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

/** Download quality selector with the optional effective automatic quality supplied by its host. */
@Composable
fun DownloadQualityEntry(automaticQualityText: String? = null, onQualityChanged: () -> Unit = {}) {
    var dialogVisible by rememberSaveable { mutableStateOf(false) }
    var downloadQualityFormat by rememberPreference(downloadQualityFormatKey, DownloadQualityFormat.Auto)

    OtherSettingsEntry(
        modifier = Modifier.settingsSearchTarget("embedded.network.download_quality_format"),
        title = stringResource(R.string.download_quality_format),
        text = stringResource(R.string.settings_desc_embedded_network_download_quality_format),
        selectedValueText = when (downloadQualityFormat) {
            DownloadQualityFormat.Auto -> automaticQualityText ?: stringResource(R.string.audio_quality_automatic)
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

/** Confirms re-downloading completed downloads that do not match the selected quality. */
@UnstableApi
@Composable
fun UpdateDownloadsButton() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dialogVisible = rememberSaveable { mutableStateOf(false) }
    val dialogCount = remember { mutableIntStateOf(0) }
    var isChecking by remember { mutableStateOf(false) }

    OtherSettingsEntry(
        modifier = Modifier.settingsSearchTarget("embedded.network.update_downloads"),
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

/** Confirmation dialog shown before re-downloading all downloads whose recorded quality does not match the selected [DownloadQualityFormat]. */
@UnstableApi
private class UpdateDownloadsDialog(
    activeState: MutableState<Boolean>,
    private val count: Int,
    private val onConfirmAction: () -> Unit
) : ConfirmDialog {

    override var isActive: Boolean by activeState

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.update_downloads)

    override val confirmText: String
        @Composable get() = stringResource(R.string.download)

    override fun onConfirm() {
        onConfirmAction()
        hideDialog()
    }

    @Composable
    override fun DialogBody() {
        BasicText(
            text = stringResource(R.string.update_downloads_count, count) + "\n\n" + stringResource(R.string.update_downloads_description),
            style = typography().xs.copy(color = colorPalette().text),
        )
    }
}
