package app.it.fast4x.rimusic.ui.screens.search

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.requests.song
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.n_zik.android.typography
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.n_zik.android.components.dialog.common.InputTextField
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.utils.asMediaItem
import app.it.fast4x.rimusic.utils.forcePlay
import app.it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.n_zik.android.utils.coroutines.NzikDispatchers
import android.net.Uri
import app.n_zik.android.playback.services.PlayerServiceModern
import timber.log.Timber

/**
 * Issue #606 M12 -- `GoToLink`'s deep-link resolution used to run entirely inside
 * `coroutineScope.launch(Dispatchers.Main)`, blocking Main with 2 network calls
 * (`Innertube.playlistPage`, `Innertube.song`) before ever reaching `navController.navigate`/
 * `forcePlay`. Extracted here, unchanged, so the composable can launch this on
 * `NzikDispatchers.DATA` while each `navController.navigate`/`forcePlay` call stays individually
 * wrapped in `withContext(NzikDispatchers.UI)`, and so it is unit-testable without instantiating
 * the composable. `internal` (not `private`) purely so `GoToLinkResolveOffMainTest` can call it
 * directly -- it adds no new public legacy API.
 */
internal suspend fun resolveGoToLink(
    uri: Uri,
    navController: NavController,
    binder: PlayerServiceModern.Binder?,
) {
    Timber.tag("GoToLink").d("channelId: ${uri.pathSegments}")
    when (val path = uri.pathSegments.firstOrNull()) {
        "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
            val browseId = "VL$playlistId"

            if (playlistId.startsWith("OLAK5uy_")) {
                Innertube.playlistPage(browseId = browseId)
                    ?.getOrNull()?.let {
                        it.songsPage?.items?.firstOrNull()?.album?.endpoint?.browseId?.let { browseId ->
                            //albumRoute.ensureGlobal(browseId)
                            withContext(NzikDispatchers.UI) {
                                navController.navigate(route = "${NavRoutes.album.name}/$browseId")
                            }
                        }
                    }
            } else {
                withContext(NzikDispatchers.UI) {
                    navController.navigate(route = "${NavRoutes.playlist.name}/$browseId")
                }
            }
        }

        "channel", "c" -> uri.lastPathSegment?.let { channelId ->
            withContext(NzikDispatchers.UI) {
                navController.navigate(route = "${NavRoutes.artist.name}/$channelId")
            }
        }

        "search" -> uri.getQueryParameter("q")?.let { query ->
                withContext(NzikDispatchers.UI) {
                    navController.navigate(route = "${NavRoutes.searchResults.name}/$query")
                }
        }

        else -> when {
            path == "watch" -> uri.getQueryParameter("v")
            uri.host == "youtu.be" -> path
            else -> null
        }?.let { videoId ->
            Innertube.song(videoId)?.getOrNull()?.let { song ->
                val resolvedBinder = snapshotFlow { binder }.filterNotNull().first()
                withContext(NzikDispatchers.UI) {
                    resolvedBinder.player.forcePlay(song.asMediaItem)
                }
            }
        }
    }
}

@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun GoToLink(
    navController: NavController,
    textFieldValue: TextFieldValue,
    onTextFieldValueChanged: (TextFieldValue) -> Unit,
    decorationBox: @Composable (@Composable () -> Unit) -> Unit,
    onAction1: () -> Unit,
    onAction2: () -> Unit,
    onAction3: () -> Unit,
    onAction4: () -> Unit,
) {
    val binder = LocalPlayerServiceBinder.current
    val coroutineScope = NzikDispatchers.fireAndForget(NzikDispatchers.DATA)

    val lazyListState = rememberLazyListState()

    var textLink by remember {
        mutableStateOf("")
    }

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth()
    ) {

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
            modifier = Modifier
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    HeaderWithIcon(
                        title = stringResource(R.string.go_to_link),
                        iconId = R.drawable.query_stats,
                        enabled = true,
                        showIcon = true,
                        modifier = Modifier
                            .padding(bottom = 8.dp),
                        onClick = {}
                    )

                }

                /*
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onAction1,
                        icon = R.drawable.globe,
                        color = colorPalette().favoritesIcon,
                        modifier = Modifier
                            .size(24.dp)
                    )
                    IconButton(
                        onClick = onAction2,
                        icon = R.drawable.library,
                        color = colorPalette().favoritesIcon,
                        modifier = Modifier
                            .size(24.dp)
                    )
                    IconButton(
                        onClick = onAction3,
                        icon = R.drawable.link,
                        color = colorPalette().favoritesIcon,
                        modifier = Modifier
                            .size(24.dp)
                    )
                    /*
                    IconButton(
                        onClick = onAction4,
                        icon = R.drawable.chevron_back,
                        color = colorPalette().favoritesIcon,
                        modifier = Modifier
                            .size(24.dp)
                    )
                     */
                }
                */
                InputTextField(
                    onDismiss = { },
                    title = stringResource(R.string.paste_or_type_a_valid_url),
                    value = textFieldValue.text,
                    placeholder = "https://........",
                    setValue = { textLink = it }
                )

                BasicText(
                    text = stringResource(R.string.you_can_put_a_complete_link),
                    style = typography().s.semiBold,
                    modifier = Modifier
                        .padding(vertical = 8.dp, horizontal = 24.dp)
                )

                if (textLink.isNotEmpty()) {

                    val uri = textLink.toUri()

                    LaunchedEffect(Unit) {
                        coroutineScope.launch(NzikDispatchers.DATA) {
                            resolveGoToLink(uri, navController, binder)
                        }
                    }
                }

            }

        }

    }
}



