package app.n_zik.android.ui.screens.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.LocalPlayerServiceBinder
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassRippleProvider
import app.n_zik.android.components.glass.rememberGlassSettings
import app.n_zik.android.components.menu.MenuSheetDragHandle
import app.n_zik.android.components.menu.song.SongMenuSurface
import app.n_zik.android.components.search.UnifiedSearchBar
import app.n_zik.android.components.theme.AppearanceMenuSheet
import app.n_zik.android.listentogether.rememberListenTogetherGuestLock
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.ui.items.VideoItem
import app.n_zik.android.ui.items.VideoItemPlaceholder
import app.n_zik.android.utils.asMediaItem
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.disableScrollingTextKey
import app.n_zik.android.utils.forcePlay
import app.n_zik.android.utils.rememberPreference
import app.n_zik.innertube.Innertube
import app.n_zik.innertube.requests.searchPage
import app.n_zik.innertube.requests.searchPageContinuation
import app.n_zik.innertube.utils.from
import kotlinx.coroutines.withContext

/** Opens the standard menu surface at the expanded anchor. */
@OptIn(ExperimentalMaterial3Api::class)
@UnstableApi
@Composable
internal fun ManualVideoSearchSheet(query: String?, onDismiss: () -> Unit) {
    var lastQuery by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) { if (query != null) lastQuery = query }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    AppearanceMenuSheet(
        showSheet = query != null,
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        expandOnShow = true,
    ) {
        (query ?: lastQuery)?.let { initialQuery ->
            ManualVideoSearch(initialQuery, onDismiss, Modifier.fillMaxWidth().fillMaxHeight())
        }
    }
}

/** Opens an editable video-only search from the player. */
@UnstableApi
@Composable
private fun ManualVideoSearch(
    initialQuery: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val binder = LocalPlayerServiceBinder.current
    val videoSession = LocalPlayerMusicVideo.current
    val guestLocked = rememberListenTogetherGuestLock()
    val focusManager = LocalFocusManager.current
    val palette = colorPalette()
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)
    var input by rememberSaveable(initialQuery, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initialQuery, selection = TextRange(initialQuery.length)))
    }
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.trim()) }
    var results by remember(query) { mutableStateOf(emptyList<Innertube.VideoItem>()) }
    var continuation by remember(query) { mutableStateOf<String?>(null) }
    var requestedPage by remember(query) { mutableStateOf<String?>(null) }
    var loading by remember(query) { mutableStateOf(query.isNotBlank()) }
    val submitSearch = {
        if (input.text.isNotBlank()) {
            query = input.text.trim()
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(query, requestedPage) {
        if (query.isBlank()) return@LaunchedEffect
        val pageToken = requestedPage
        loading = true
        val page = withContext(NzikDispatchers.DATA) {
            if (pageToken == null) {
                Innertube.searchPage(
                    query = query,
                    params = Innertube.SearchFilter.Video.value,
                    fromMusicShelfRendererContent = Innertube.VideoItem::from,
                )
            } else {
                Innertube.searchPageContinuation(
                    continuation = pageToken,
                    fromMusicShelfRendererContent = Innertube.VideoItem::from,
                )
            }
        }?.getOrNull()
        results = (results + page?.items.orEmpty()).distinctBy { it.key }
        continuation = page?.continuation?.takeUnless { it == pageToken }
        loading = false
    }

    val glass = rememberGlassSettings()
    val navigationPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    GlassRippleProvider(glass) {
        SongMenuSurface(
            modifier = modifier,
            opaqueWhenNonGlass = true,
            header = {
                MenuSheetDragHandle()
                UnifiedSearchBar(
                    input = input,
                    onInputChanged = { input = it },
                    placeholderRes = R.string.search_title,
                    onClear = {
                        input = TextFieldValue()
                        query = ""
                    },
                    onSearch = submitSearch,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            },
        ) {
            LazyColumn(
                Modifier.fillMaxWidth().fillMaxHeight().clipToBounds(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = navigationPadding + 24.dp,
                ),
            ) {
                items(results, key = { it.key }) { result ->
                    VideoItem(
                        video = result,
                        thumbnailWidthDp = 128.dp,
                        thumbnailHeightDp = 72.dp,
                        modifier = Modifier.clip(uiRoundnessShape()).clickable(enabled = binder != null && !guestLocked) {
                            val playerBinder = binder ?: return@clickable
                            playerBinder.stopRadio()
                            playerBinder.player.forcePlay(result.asMediaItem)
                            videoSession?.chooseMode(true)
                            onDismiss()
                        },
                        disableScrollingText = disableScrollingText,
                    )
                }
                if (loading) {
                    items(if (results.isEmpty()) 8 else 3) {
                        VideoItemPlaceholder(thumbnailHeightDp = 72.dp, thumbnailWidthDp = 128.dp)
                    }
                } else if (results.isEmpty() && query.isNotBlank()) {
                    item {
                        Text(
                            stringResource(R.string.search_no_results_message),
                            modifier = Modifier.padding(16.dp),
                            color = palette.text,
                        )
                    }
                } else {
                    val nextPage = continuation
                    if (nextPage != null) {
                        item(key = "manual-video-next-page") {
                            LaunchedEffect(nextPage) { requestedPage = nextPage }
                        }
                    }
                }
            }
        }
    }
}
