package app.n_zik.android.components.settings.other

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.parentalControlEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

@UnstableApi
@Composable
internal fun OtherParentalControlCard(search: Search) {
    var parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.parental_control),
        icon = R.drawable.shield_checkmark,
        content = {
            if (search.inputValue.isBlank() || stringResource(R.string.parental_control).contains(search.inputValue, true) || stringResource(R.string.info_prevent_play_songs_with_age_limitation).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.parental_control),
                    text = stringResource(R.string.info_prevent_play_songs_with_age_limitation),
                    isChecked = parentalControlEnabled,
                    onCheckedChange = { parentalControlEnabled = it },
                    icon = R.drawable.parental_control
                )
            }
        }
    )
}
