package app.n_zik.android.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.ui.components.themed.Loader
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.secondary
import app.n_zik.android.R
import app.n_zik.android.typography

/**
 * Centered "no connection" message shown instead of an endless spinner when a
 * screen cannot load its online content while the device is offline.
 */
@Composable
fun OfflineContent(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = stringResource(R.string.unable_to_load_no_connection),
            style = typography().m.secondary.center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

/**
 * Shared offline branch for screens whose content needs the network: the
 * centered [OfflineContent] message when offline, the usual [Loader] while the
 * fetch is in flight when online. [onReconnect], when provided, is invoked once
 * on the offline→online transition so the screen can reload automatically.
 * Public (like [OfflineContent]): it must also be callable from the public
 * inline legacy composables (ItemsPage/ItemsGridPage), which Kotlin forbids
 * calling internal functions from.
 */
@Composable
fun OfflineAwareLoader(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    onReconnect: (() -> Unit)? = null
) {
    var wasOnline by remember { mutableStateOf(true) }
    LaunchedEffect(isOnline) {
        if (isOnline && !wasOnline) onReconnect?.invoke()
        wasOnline = isOnline
    }

    if (isOnline) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Loader()
        }
    } else {
        OfflineContent(modifier = modifier)
    }
}
