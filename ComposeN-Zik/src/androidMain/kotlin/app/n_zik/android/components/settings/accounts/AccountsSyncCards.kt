package app.n_zik.android.components.settings.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import app.n_zik.android.R
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
import app.n_zik.android.components.settings.common.SettingsCardSlot
import app.n_zik.android.components.settings.common.settingsCardEnter
import app.n_zik.android.components.settings.common.settingsCardExit

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun AccountsSyncCards(
    selectedCards: Set<Int>? = null,
    cardModifier: @Composable (Int) -> Modifier = { Modifier },
) {
    if (selectedCards != null && selectedCards.none {
        it == R.string.sync_data_with_ytm_account || it == R.string.autosync ||
            it == R.string.sync_import_from_youtube || it == R.string.sync_push_to_youtube
    }) return

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
            if (selectedCards == null || R.string.sync_data_with_ytm_account in selectedCards) {
                SettingsCardSlot(R.string.sync_data_with_ytm_account, order = 2, modifier = { cardModifier(R.string.sync_data_with_ytm_account) }) {
                    AccountsSyncOverviewCard(isYouTubeSyncEnabled, isSyncEnabled) { isYouTubeSyncEnabled = it }
                }
            }

            AnimatedVisibility(visible = isSyncEnabled) {
                Column {
                    if (selectedCards == null || R.string.autosync in selectedCards) {
                        SettingsCardSlot(R.string.autosync, order = 3, modifier = { cardModifier(R.string.autosync) }) {
                            AccountsAutoSyncCard()
                        }
                    }
                    if (selectedCards == null || R.string.sync_import_from_youtube in selectedCards) {
                        SettingsCardSlot(R.string.sync_import_from_youtube, order = 4, modifier = { cardModifier(R.string.sync_import_from_youtube) }) {
                            AccountsSyncImportCard()
                        }
                    }
                    if (selectedCards == null || R.string.sync_push_to_youtube in selectedCards) {
                        SettingsCardSlot(R.string.sync_push_to_youtube, order = 5, modifier = { cardModifier(R.string.sync_push_to_youtube) }) {
                            AccountsSyncPushCard()
                        }
                    }
                }
            }
        }
    }
}
