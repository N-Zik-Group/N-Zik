package app.n_zik.android.utils

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.components.settings.appearance.preview.LocalThemePreview
import app.n_zik.android.ui.components.themed.TitleMiniSection
import app.n_zik.android.components.ui.screens.profiles.loadActiveProfileFace
import app.n_zik.android.components.ui.screens.profiles.profileFaceUpdateTrigger
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun WelcomeMessage() {
    val hour =
        remember {
            val date = Calendar.getInstance().time
            val formatter = SimpleDateFormat( "HH", Locale.getDefault() )
            formatter.format(date).toInt()
        }

    val baseMessage = when (hour) {
        in 6..12 -> {
            stringResource(R.string.home_greeting_morning)
        }
        in 13..17 -> {
            stringResource(R.string.home_greeting_afternoon)
        }
        in 18..23 -> {
            stringResource(R.string.home_greeting_evening)
        }
        else -> {
            stringResource(R.string.home_greeting_night)
        }
    }

    val isPreview = LocalThemePreview.current != null
    var profileName by remember(isPreview) { mutableStateOf("") }
    // When no name was ever chosen, the greeting falls back to the default app name
    val defaultName = stringResource(R.string.profile_base_name)

    // Generic captures must never resolve the active profile or account identity.
    if (!isPreview) {
        LaunchedEffect(baseMessage, defaultName, encryptedPreferencesUpdateTrigger + profileFaceUpdateTrigger) {
            withContext(NzikDispatchers.DATA) {
                runCatching {
                    val name = loadActiveProfileFace(appContext(), defaultName).name
                    if (name.isNotBlank()) {
                        profileName = name
                    }
                }.onFailure {
                    Timber.tag("WelcomeMessage").e(it, "Failed to resolve the greeting face name")
                }
            }
        }
    }

    val greetingName = if (isPreview) defaultName else profileName
    TitleMiniSection(
        title = if (greetingName.isBlank()) {
            baseMessage
        } else {
            stringResource(R.string.home_greeting_with_profile_format, baseMessage, greetingName)
        },
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}


