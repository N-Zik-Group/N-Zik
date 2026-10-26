package app.n_zik.android.components.settings.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.enableYouTubeSyncKey
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.useLoginForBrowseKey
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun AccountsSyncCards() {
    val useLoginForBrowseSync by rememberPreference(useLoginForBrowseKey, true)
    val isYouTubeLoginActive by rememberEncryptedPreference(enableYouTubeLoginKey, false)

    AnimatedVisibility(
        visible = useLoginForBrowseSync && isYouTubeLoginActive,
        enter = settingsCardEnter,
        exit = settingsCardExit
    ) {
        var isYouTubeSyncEnabled by rememberEncryptedPreference(enableYouTubeSyncKey, false)
        val isSyncEnabled = isYouTubeSyncEnabled && useLoginForBrowseSync

        // Re-sync state when Login for Browse changes (prefs reset externally)
        LaunchedEffect(useLoginForBrowseSync) {
            if (!useLoginForBrowseSync) {
                isYouTubeSyncEnabled = false
            }
        }

        Column {
            // Sync data toggle + Sync Now + Direction
            AccountsSyncOverviewCard(isYouTubeSyncEnabled, isSyncEnabled) { isYouTubeSyncEnabled = it }

            // Auto-Sync, Import, Push (only when sync is enabled)
            AnimatedVisibility(visible = isSyncEnabled) {
                Column {
                    // Auto-Sync (per feature)
                    AccountsAutoSyncCard()

                    // Import from YouTube
                    AccountsSyncImportCard()

                    // Push to YouTube
                    AccountsSyncPushCard()
                }
            }
        }

    }
}
