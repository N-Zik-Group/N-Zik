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
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.resolveDisplayName
import app.n_zik.android.ytAccountName
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
    val defaultName = stringResource(R.string.display_name_default)

    LaunchedEffect(baseMessage) {
        withContext(NzikDispatchers.DATA) {
            // The greeting follows the chosen display name (custom (guest) or YouTube):
            // the YouTube account is only consulted when the stored source is YouTube,
            // and nothing chosen falls back to the default app name
            runCatching {
                val source = DataStoreUtils.getString(
                    appContext(),
                    DataStoreUtils.KEY_DISPLAY_NAME_SOURCE,
                    DataStoreUtils.DISPLAY_NAME_SOURCE_CUSTOM
                )
                val useYouTubeName = source == DataStoreUtils.DISPLAY_NAME_SOURCE_YOUTUBE
                val name = resolveDisplayName(
                    source = source,
                    ytLoggedIn = useYouTubeName && isYouTubeLoggedIn(),
                    ytName = if (useYouTubeName) ytAccountName() else "",
                    customName = DataStoreUtils.getString(appContext(), DataStoreUtils.KEY_USERNAME, ""),
                    default = defaultName
                )
                if (name.isNotBlank()) {
                    message = "$baseMessage, $name"
                }
            }.onFailure {
                Timber.tag("WelcomeMessage").e(it, "Failed to resolve the greeting display name")
            }
        }
    }

    TitleMiniSection(
        title = message,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}


