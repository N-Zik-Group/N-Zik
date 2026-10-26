package app.n_zik.android.components.settings.general

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.enums.Languages
import app.it.fast4x.rimusic.utils.languageAppKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ValueSelectorDialog
import app.n_zik.android.components.settings.common.OtherSettingsEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** Language card: app language entry, dialog title shows the current system locale. */
@Composable
fun GeneralLanguageCard(
    visible: Boolean,
) {
    var languageApp by rememberPreference(languageAppKey, Languages.System)
    val systemLocale = LocaleListCompat.getDefault().get(0).toString()

    var showLanguageDialog by remember { mutableStateOf(false) }

    SettingsSectionCard(
        title = stringResource(R.string.languages),
        icon = R.drawable.discover,
        visible = visible,
        content = {
            OtherSettingsEntry(
                title = stringResource(R.string.app_language),
                text = languageApp.text,
                onClick = { showLanguageDialog = true },
                icon = R.drawable.translate
            )

            if (showLanguageDialog) {
                ValueSelectorDialog(
                    title = stringResource(R.string.app_language) + ": $systemLocale",
                    selectedValue = languageApp,
                    onValueSelected = { languageApp = it },
                    valueText = { it.text },
                    values = Languages.values().toList(),
                    onDismiss = { showLanguageDialog = false }
                )
            }
        }
    )
}
