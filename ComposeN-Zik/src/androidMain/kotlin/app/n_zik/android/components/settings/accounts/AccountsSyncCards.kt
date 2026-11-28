package app.n_zik.android.components.settings.accounts

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.n_zik.android.components.settings.common.SettingsEntryVisibility
import app.n_zik.android.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.media3.common.util.UnstableApi
import app.n_zik.android.components.menu.menuPageInteraction
import app.n_zik.android.utils.enableYouTubeLoginKey
import app.n_zik.android.utils.enableYouTubeSyncKey
import app.n_zik.android.utils.encryptedPreferences
import app.n_zik.android.utils.encryptedPreferencesUpdateTrigger
import app.n_zik.android.utils.rememberEncryptedPreference
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.useLoginForBrowseKey
import app.n_zik.android.utils.ytCookieExpiredKey
import app.n_zik.android.utils.ytCookieKey
import app.n_zik.android.components.settings.common.SettingsCardSlot

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
internal fun AccountsSyncCards(
    selectedCards: Set<Int>? = null,
    cardModifier: @Composable (Int) -> Modifier = { Modifier },
) {
    if (selectedCards != null && selectedCards.none {
        it == R.string.sync_data_with_ytm_account || it == R.string.sync_imported_playlists_automatic_title ||
            it == R.string.sync_import_from_youtube || it == R.string.sync_push_to_youtube
    }) return

    val useLoginForBrowseSync by rememberPreference(useLoginForBrowseKey, true)
    val isYouTubeLoginActive by key(encryptedPreferencesUpdateTrigger) {
        rememberEncryptedPreference(enableYouTubeLoginKey, false)
    }
    val isSyncBuild = isYouTubeSyncBuild()
    val cookieExpired by rememberPreference(ytCookieExpiredKey, false)
    val cookie = LocalContext.current.encryptedPreferences.getString(ytCookieKey, null)
    val isLoggedIn = isUsableYouTubeSession(cookie, cookieExpired)
    val canEnableSync = isSyncBuild && isYouTubeLoginActive && isLoggedIn && useLoginForBrowseSync
    val disabledReason = when {
        !isSyncBuild -> R.string.account_youtube_sync_build_required
        !isYouTubeLoginActive || !isLoggedIn -> R.string.settings_youtube_connection_required_description
        !useLoginForBrowseSync -> R.string.account_youtube_sync_browse_required
        else -> null
    }

    Column {
        var isYouTubeSyncEnabled by key(encryptedPreferencesUpdateTrigger) {
            rememberEncryptedPreference(enableYouTubeSyncKey, false)
        }
        val isSyncEnabled = isYouTubeSyncEnabled && canEnableSync

        Column {
            if (selectedCards == null || R.string.sync_data_with_ytm_account in selectedCards) {
                SettingsCardSlot(R.string.sync_data_with_ytm_account, order = 2, modifier = { cardModifier(R.string.sync_data_with_ytm_account) }) {
                    AccountsSyncOverviewCard(isSyncEnabled, disabledReason) { isYouTubeSyncEnabled = it }
                }
            }

            SettingsEntryVisibility(visible = isSyncEnabled) {
                Column {
                    // The organized Settings page renders these slots outside this animation.
                    if (selectedCards == null || R.string.sync_imported_playlists_automatic_title in selectedCards) {
                        SettingsCardSlot(R.string.sync_imported_playlists_automatic_title, order = 3, modifier = {
                            cardModifier(R.string.sync_imported_playlists_automatic_title).menuPageInteraction(isSyncEnabled)
                        }) {
                            AccountsAutoSyncCard()
                        }
                    }
                    if (selectedCards == null || R.string.sync_import_from_youtube in selectedCards) {
                        SettingsCardSlot(R.string.sync_import_from_youtube, order = 4, modifier = {
                            cardModifier(R.string.sync_import_from_youtube).menuPageInteraction(isSyncEnabled)
                        }) {
                            AccountsSyncImportCard()
                        }
                    }
                    if (selectedCards == null || R.string.sync_push_to_youtube in selectedCards) {
                        SettingsCardSlot(R.string.sync_push_to_youtube, order = 5, modifier = {
                            cardModifier(R.string.sync_push_to_youtube).menuPageInteraction(isSyncEnabled)
                        }) {
                            AccountsSyncPushCard()
                        }
                    }
                }
            }
        }
    }
}
