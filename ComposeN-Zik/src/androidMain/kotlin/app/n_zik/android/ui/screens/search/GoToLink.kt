package app.n_zik.android.ui.screens.search

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputTextField
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.components.navigation.onlinePlaylistBrowseIdFromLink
import app.n_zik.android.components.navigation.onlinePlaylistRoute
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.intents.remoteAlbumLinkRoute
import app.n_zik.android.intents.resolveRemoteTrackLink
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.parentalControlEnabledKey
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Issue #606 M12 -- `GoToLink`'s deep-link resolution used to run entirely inside
 * `coroutineScope.launch(Dispatchers.Main)`, blocking Main with 2 network calls
 * before ever reaching `navController.navigate`/
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
            val browseId = onlinePlaylistBrowseIdFromLink(playlistId)

            val route = if (playlistId.startsWith("OLAK5uy_")) {
                remoteAlbumLinkRoute(browseId)
            } else onlinePlaylistRoute(browseId)
            withContext(NzikDispatchers.UI) {
                navController.navigate(route = route)
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
            val track = resolveRemoteTrackLink(videoId) ?: return
            withContext(NzikDispatchers.UI) {
                if (track.explicit != false && appContext().preferences.getBoolean(parentalControlEnabledKey, false)) {
                    Toaster.w(R.string.parental_control_is_enabled)
                } else {
                    binder?.player?.forcePlay(track.item)
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun GoToLink(
    topContentInsetProvider: () -> Dp,
    navController: NavController,
    initialLink: String,
) {
    val binder = LocalPlayerServiceBinder.current
    var pendingLink by remember { mutableStateOf("") }

    LaunchedEffect(pendingLink, binder) {
        if (pendingLink.isBlank()) return@LaunchedEffect
        val uri = pendingLink.toUri()
        if (binder == null && (uri.pathSegments.firstOrNull() == "watch" || uri.host == "youtu.be")) {
            return@LaunchedEffect
        }
        withContext(NzikDispatchers.DATA) { resolveGoToLink(uri, navController, binder) }
        pendingLink = ""
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colorPalette().background0),
        contentPadding = rememberSearchTabContentPadding(topContentInsetProvider),
    ) {
        item(key = "header", contentType = "search_controls") {
            SearchTabHeader(R.string.search_youtube_action_open_url, R.drawable.link)
        }
        item(key = "form") {
            SettingsSectionCard(
                title = stringResource(R.string.search_youtube_url_input_hint),
                icon = R.drawable.link,
                content = {
                    InputTextField(
                        initialValue = initialLink,
                        placeholder = stringResource(R.string.search_youtube_open_url_example),
                        enabled = pendingLink.isBlank(),
                        onSubmit = { pendingLink = it },
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsDescription(
                        text = stringResource(R.string.search_youtube_url_supported_types_description),
                    )
                },
            )
        }
    }
}
