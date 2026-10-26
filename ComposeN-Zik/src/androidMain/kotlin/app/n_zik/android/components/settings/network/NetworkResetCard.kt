package app.n_zik.android.components.settings.network

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import androidx.compose.ui.platform.LocalContext
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.settings.common.OtherSettingsEntry

@Composable
internal fun NetworkResetCard(query: String, onRestartRequired: () -> Unit) {
    val context = LocalContext.current
    val searchCtx_Reset = query.isBlank() || stringResource(R.string.settings_reset).contains(query, true) || stringResource(R.string.settings_restore_default_settings).contains(query, true)
    AnimatedVisibility(
        visible = searchCtx_Reset,
        enter = fadeIn(animationSpec = tween(1100)) + scaleIn(animationSpec = tween(1100), initialScale = 0.9f)
    ) {
        SettingsSectionCard(
            title = stringResource(R.string.settings_reset),
            icon = R.drawable.refresh,
            content = {

                if (query.isBlank() || stringResource(R.string.settings_restore_default_settings).contains(query, true) || stringResource(R.string.settings_reset).contains(query, true)) {
                    OtherSettingsEntry(
                        title = stringResource(R.string.settings_reset),
                        text = stringResource(R.string.settings_restore_default_settings),
                        icon = R.drawable.refresh,
                        onClick = {
                            applyNetworkDefaults(context)
                            onRestartRequired()
                            Toaster.done()
                        }
                    )
                }

            }
        )
    }
}
