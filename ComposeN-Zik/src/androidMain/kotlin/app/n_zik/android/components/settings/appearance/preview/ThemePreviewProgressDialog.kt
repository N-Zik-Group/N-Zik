package app.n_zik.android.components.settings.appearance.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.theme.appearanceBackground
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard

/** Dialog owns another Android window, so activity-window PixelCopy never includes this UI. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ThemePreviewProgressDialog(completed: Int = 0, total: Int = 0, onCancel: () -> Unit) {
    val resources = LocalContext.current.applicationContext.resources
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val dark = colorPalette().isDark
        DisposableEffect(view, dark) {
            val window = (view.parent as DialogWindowProvider).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            onDispose { }
        }
        Box(
            Modifier.fillMaxSize().appearanceBackground().statusBarsPadding().navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().align(Alignment.Center).verticalScroll(rememberScrollState())) {
                SettingsSectionCard(
                    title = resources.getString(R.string.theme_store_generating_previews),
                    icon = R.drawable.color_palette,
                    content = {
                        BasicText(
                            if (total == 0) resources.getString(R.string.theme_store_preparing_previews)
                            else resources.getString(R.string.theme_store_preview_capture_progress, completed, total),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = typography().s.copy(color = colorPalette().text),
                        )
                        val progressModifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(8.dp).clip(uiRoundnessShape())
                        if (total == 0) LinearWavyProgressIndicator(
                            modifier = progressModifier, color = colorPalette().accent, trackColor = colorPalette().background2,
                        ) else LinearWavyProgressIndicator(
                            progress = { completed.toFloat() / total },
                            modifier = progressModifier, color = colorPalette().accent, trackColor = colorPalette().background2,
                        )
                        SettingsDescription(
                            text = resources.getString(R.string.theme_preview_keep_open),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ButtonRow(listOf(DialogButton(resources.getString(R.string.common_action_cancel), onCancel, danger = true)))
                    },
                )
            }
        }
    }
}
