package app.it.fast4x.rimusic.utils

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.it.fast4x.rimusic.ui.components.themed.TitleMiniSection
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.n_zik.android.ytAccountName
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

    LaunchedEffect(baseMessage) {
        withContext(Dispatchers.IO) {
            if (isYouTubeLoggedIn()) {
                val name = ytAccountName()
                if (!name.isNullOrBlank()) {
                    message = "$baseMessage, $name"
                }
            }
        }
    }

    TitleMiniSection(
        title = message,
        modifier = Modifier.padding(horizontal = 12.dp)
    )
}


