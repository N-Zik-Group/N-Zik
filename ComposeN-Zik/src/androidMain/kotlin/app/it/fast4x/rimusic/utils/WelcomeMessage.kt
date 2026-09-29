package app.it.fast4x.rimusic.utils

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.it.fast4x.rimusic.ui.components.themed.TitleMiniSection
import app.n_zik.android.components.ui.screens.profiles.loadActiveProfileFace
import app.n_zik.android.components.ui.screens.profiles.profileFaceUpdateTrigger
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun WelcomeMessage(){
    val hour =
        remember {
            val date = Calendar.getInstance().time
            val formatter = SimpleDateFormat( "HH", Locale.getDefault() )
            formatter.format(date).toInt()
        }

    val baseMessage = when (hour) {
        in 6..12 -> {
            stringResource(R.string.good_morning)
        }
        in 13..17 -> {
            stringResource(R.string.good_afternoon)
        }
        in 18..23 -> {
            stringResource(R.string.good_evening)
        }
        else -> {
            stringResource(R.string.good_night)
        }
    }

    var message by remember { mutableStateOf(baseMessage) }
    // When no name was ever chosen, the greeting falls back to the default app name
    val defaultName = stringResource(R.string.profile_base_name)

    // The greeting re-resolves on every face change, like the header and the
    // accounts card (the same trigger sum they key on) — not only on the
    // time-of-day bucket change.
    LaunchedEffect(baseMessage, encryptedPreferencesUpdateTrigger + profileFaceUpdateTrigger) {
        withContext(NzikDispatchers.DATA) {
            // The greeting follows the active profile's face name
            // (spec-profiles-page-face): its display name or a logged-in account
            // source, with the legacy custom name and the default app name as
            // fallbacks — the name is never blank, so the greeting always renders.
            runCatching {
                val name = loadActiveProfileFace(appContext(), defaultName).name
                if (name.isNotBlank()) {
                    message = "$baseMessage, $name"
                }
            }.onFailure {
                Timber.tag("WelcomeMessage").e(it, "Failed to resolve the greeting face name")
            }
        }
    }

    TitleMiniSection(
        title = message,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}


