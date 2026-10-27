package app.n_zik.android.components.dialog.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.preferredStreamClientKey
import app.it.fast4x.rimusic.utils.semiBold
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.components.dialog.common.ContentFramedDialog
import app.n_zik.android.components.dialog.common.DialogFrame
import app.it.fast4x.rimusic.utils.streamClientRestartNeededKey
import app.n_zik.android.playback.services.clearStreamCaches
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.kreate.android.me.knighthat.utils.Toaster
import android.content.Context
import androidx.media3.datasource.cache.Cache
import kotlinx.coroutines.launch

object PreferredStreamClientDialog : ContentFramedDialog {

    override val dialogTitle: String
        @Composable
        get() = stringResource(R.string.preferred_stream_client_title)

    override var isActive: Boolean by mutableStateOf(false)

    @Composable
    override fun DialogBody() {
        val context = LocalContext.current
        val binder = LocalPlayerServiceBinder.current
        val prefs = remember { context.getSharedPreferences("preferences", Context.MODE_PRIVATE) }
        val savedPref = remember { prefs.getString(preferredStreamClientKey, "WEB_REMIX") ?: "WEB_REMIX" }

        // Local state - not saved until OK is clicked
        var selectedClient by remember { mutableStateOf(savedPref) }

        val options = listOf(
            Triple("ANDROID_VR", "Android VR", stringResource(R.string.client_android_vr_desc)),
            Triple("WEB_REMIX", "Web Remix", stringResource(R.string.client_youtube_music_web_desc))
        )

        DialogFrame(onDismiss = ::hideDialog, title = dialogTitle, bodyPadding = PaddingValues(0.dp),
        footer = {
            ButtonRow(
                buttons = listOf(
                    // Reset button (grey) - only changes local state
                    DialogButton(
                        text = stringResource(R.string.reset),
                        enabled = selectedClient != "WEB_REMIX",
                        textColor = colorPalette().textDisabled,
                        onClick = {
                            selectedClient = "WEB_REMIX"
                        }
                    ),
                    // Cancel button (red) - closes without saving
                    DialogButton(
                        text = stringResource(R.string.cancel),
                        onClick = { hideDialog() },
                        danger = true
                    ),
                    // OK button (accent filled) - saves only if changed
                    DialogButton(
                        text = stringResource(R.string.dialog_apply),
                        onClick = {
                            if (selectedClient != savedPref) {
                                prefs.edit().putString(preferredStreamClientKey, selectedClient).apply()
                                prefs.edit().putBoolean(streamClientRestartNeededKey, true).apply()
                                clearStreamCaches()
                                // Clear audio cache
                                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                    clearAudioCache(binder?.cache)
                                }
                                Toaster.i(R.string.preferred_stream_client_changed)
                                Toaster.w(R.string.stream_client_redownload_recommendation)
                            }
                            hideDialog()
                        },
                        primary = true
                    )
                )
            )
        }) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()).padding(24.dp)
                ) {
                    options.forEachIndexed { index, (value, title, description) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(uiRoundnessShape())
                                .clickable { selectedClient = value }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedClient == value,
                                onClick = { selectedClient = value },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colorPalette().text,
                                    unselectedColor = colorPalette().textSecondary
                                ),
                                modifier = Modifier.size(20.dp)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = typography().xs.semiBold,
                                    color = colorPalette().text
                                )
                                Text(
                                    text = description,
                                    style = typography().xxs,
                                    color = colorPalette().textSecondary
                                )
                            }
                        }
                    }
                }

            }
        }
    }

    fun reset(context: Context) {
        val prefs = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
        prefs.edit().putString(preferredStreamClientKey, "WEB_REMIX").apply()
    }
}

/**
 * Purges the full streaming audio cache (issue #606 H3) — one `removeResource` per key.
 * Callers dispatch this on a background dispatcher; a null [cache] is a safe no-op and
 * individual removal failures are already tolerated by the cache itself.
 */
internal suspend fun clearAudioCache(cache: Cache?) {
    cache?.keys?.forEach { key ->
        cache.removeResource(key)
    }
}
