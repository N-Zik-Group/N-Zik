package app.n_zik.android.components.settings.other

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.it.fast4x.rimusic.utils.extraspaceKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import app.n_zik.android.components.settings.common.OtherSwitchSettingEntry
import app.n_zik.android.components.settings.common.SettingsSectionCard
import app.n_zik.android.components.tab.Search

@UnstableApi
@Composable
internal fun OtherHeadUnitCard(search: Search) {
    var extraspace by rememberPreference(extraspaceKey, false)

    SettingsSectionCard(
        title = stringResource(R.string.androidheadunit),
        icon = R.drawable.car,
        content = {
            if (search.inputValue.isBlank() || stringResource(R.string.extra_space).contains(search.inputValue, true)) {
                OtherSwitchSettingEntry(
                    title = stringResource(R.string.extra_space),
                    text = "",
                    isChecked = extraspace,
                    onCheckedChange = { extraspace = it },
                    icon = R.drawable.space
                )
            }
        }
    )
}
