package app.n_zik.android.components.pullrefresh

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.components.AppPullToRefreshBox
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Duration in ms of the offline engagement that keeps the pull indicator spinning. */
internal const val OFFLINE_PULL_ENGAGEMENT_MS = 600L

/**
 * Pull-to-refresh decision (spec-full-offline-mode): online runs [refresh]; offline
 * fires [onNoNetwork] once and never touches [refresh].
 */
internal suspend fun offlineGatedRefreshDecision(
    isOnline: Boolean,
    refresh: suspend () -> Unit,
    onNoNetwork: suspend () -> Unit
) {
    if (isOnline) refresh() else onNoNetwork()
}

/**
 * Shared pull-to-refresh gate of the full offline mode (spec-full-offline-mode):
 * online pulls run the real [onRefresh]; offline pulls are consumed with a "no
 * connection" toast and a brief indicator spin — no network load. The effective
 * refresh state is `isRefreshing || <offline engagement>`, as before the per-screen
 * extraction.
 */
@Composable
internal fun OfflineGatedPullRefresh(
    isOnline: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    var offlinePullRefreshing by remember { mutableStateOf(false) }
    var offlinePullJob by remember { mutableStateOf<Job?>(null) }
    val pullRefreshScope = rememberCoroutineScope()

    AppPullToRefreshBox(
        isRefreshing = isRefreshing || offlinePullRefreshing,
        onRefresh = {
            pullRefreshScope.launch {
                offlineGatedRefreshDecision(
                    isOnline = isOnline,
                    refresh = { onRefresh() },
                    onNoNetwork = {
                        // Offline: the pull is gated (no network load) with a "no
                        // connection" toast. The box indicator is engaged briefly so
                        // the pull is consumed instead of re-firing the refresh.
                        Toaster.noInternet()
                        offlinePullRefreshing = true
                        // Cancel the previous offline engagement so a stacked pull is
                        // only cleared by its own timer.
                        offlinePullJob?.cancel()
                        offlinePullJob = pullRefreshScope.launch {
                            delay(OFFLINE_PULL_ENGAGEMENT_MS)
                            offlinePullRefreshing = false
                        }
                    }
                )
            }
        }
    ) {
        content()
    }
}
