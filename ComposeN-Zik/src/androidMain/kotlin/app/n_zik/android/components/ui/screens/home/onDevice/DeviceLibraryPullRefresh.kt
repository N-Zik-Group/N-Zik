package app.n_zik.android.components.ui.screens.home.onDevice

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.components.AppPullToRefreshBox
import app.n_zik.android.components.pullrefresh.OfflineGatedPullRefresh
import app.n_zik.android.core.ondevice.scanDeviceLibrary
import app.n_zik.android.utils.getActiveProfile
import kotlinx.coroutines.launch

@Composable
internal fun DeviceLibraryPullRefresh(
    enabled: Boolean,
    online: Boolean = true,
    otherRefreshing: Boolean = false,
    otherRefresh: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val profile = getActiveProfile(context)
    val scope = rememberCoroutineScope()
    var refreshing by remember(profile) { mutableStateOf(false) }
    val refresh: () -> Unit = {
        if (!enabled) {
            otherRefresh?.invoke()
        } else if (!refreshing) {
                refreshing = true
                scope.launch {
                    try {
                        context.scanDeviceLibrary(refresh = true)
                    } finally {
                        refreshing = false
                    }
                }
        }
    }
    if (otherRefresh == null) {
        AppPullToRefreshBox(modifier = Modifier.fillMaxSize(), isRefreshing = refreshing, onRefresh = refresh) { content() }
    } else {
        OfflineGatedPullRefresh(enabled || online, refreshing || otherRefreshing, refresh, content)
    }
}
