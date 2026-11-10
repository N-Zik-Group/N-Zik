package app.n_zik.android.components.settings.data

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.typography

@Composable
internal fun DataStorageInfoDialog(
    onDismiss: () -> Unit,
    sectionTitle: String?,
    otherVolumes: List<StorageBreakdown> = emptyList(),
    selectedProfile: ProfileStorageIdentity? = null,
) {
    val context = LocalContext.current
    val isProfile = selectedProfile != null
    DialogFrame(
        onDismiss = onDismiss,
        title = stringResource(if (sectionTitle != null) R.string.storage_breakdown_info_title else R.string.storage_breakdown_secondary_volumes),
        footer = {
            ButtonRow(listOf(DialogButton(stringResource(R.string.common_action_close), onDismiss, primary = true)))
        },
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (otherVolumes.isNotEmpty()) {
                otherVolumes.forEachIndexed { index, volume ->
                    val label = volume.volumeLabel ?: stringResource(R.string.storage_breakdown_other_volume, index + 1)
                    val chart = volume.appChartFor(selectedProfile).displayChart(includeDevice = false)
                    Column(modifier = Modifier.testTag("data.storage.secondary.$index")) {
                        BasicText(
                            text = stringResource(
                                R.string.storage_breakdown_secondary_volume_summary,
                                label,
                                Formatter.formatShortFileSize(context, chart.nzikBytes),
                                Formatter.formatShortFileSize(context, volume.bytes.getValue(StorageCategory.FREE)),
                                Formatter.formatShortFileSize(context, volume.totalBytes),
                            ),
                            style = typography().xs.semiBold.copy(color = colorPalette().text),
                        )
                        for ((category, bytes) in chart.bytes) {
                            val categoryLabel = stringResource(storageCategoryLabel(category, selectedProfile != null))
                            StorageAmountRow(
                                categoryLabel,
                                bytes,
                                category.color,
                                modifier = Modifier.testTag("data.storage.secondary.$index.${category.name.lowercase()}"),
                            )
                        }
                    }
                }
            }
            if (sectionTitle != null) {
                StorageInfoSection(sectionTitle, storageAppCategories(isProfile), isProfile)
                if (!isProfile) {
                    StorageInfoSection(
                        stringResource(R.string.storage_breakdown_device_section),
                        listOf(StorageCategory.OTHER_USED, StorageCategory.FREE),
                        isProfile = false,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageInfoSection(title: String, categories: List<StorageCategory>, isProfile: Boolean, modifier: Modifier = Modifier) {
    val palette = colorPalette()
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BasicText(title, style = typography().xs.semiBold.copy(color = palette.text))
        for (category in categories) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                BasicText(
                    stringResource(storageCategoryLabel(category, isProfile)),
                    style = typography().xs.semiBold.copy(color = palette.text),
                )
                BasicText(
                    stringResource(category.description),
                    style = typography().xs.copy(color = palette.textSecondary),
                )
            }
        }
    }
}
