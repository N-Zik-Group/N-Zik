package app.n_zik.android.components.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.ui.screens.settings.OtherSwitchSettingEntry
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.R
import it.fast4x.innertube.Innertube

/** SharedPreferences key of the "Use account for searches" toggle (Accounts tab). */
const val useLoginForSearchKey = "useLoginForSearch"

/**
 * "Use account for searches" row of the Accounts tab, embedded directly under the
 * "Login for Browse" row.
 *
 * ON (default): searches are sent with the connected account's credentials (the
 * pre-existing behavior). OFF: every search (the automatic metadata lookups and the
 * user's online search alike) is sent as a guest — no account cookie, no auth headers,
 * no dataSyncId — so it stays out of the account's YouTube search history and
 * suggestions.
 *
 * The row is hidden when the YouTube login is disabled, and shown greyed out (with a
 * "connect first" hint) when the login is enabled but no account is connected —
 * exactly like the "Login for Browse" row above it.
 *
 * @param isYouTubeLoginEnabled whether the YouTube login is enabled at all; renders
 *   nothing when false.
 * @param isLoggedIn whether a YouTube account is currently connected; the row is
 *   disabled (and never writes the preference) when false.
 * @param searchQuery the Accounts tab search filter — hides the row when it matches
 *   neither the title nor the description.
 */
@Composable
fun YtSearchAccountToggle(
    isYouTubeLoginEnabled: Boolean,
    isLoggedIn: Boolean,
    searchQuery: String,
) {
    if (!isYouTubeLoginEnabled) return

    var useLoginForSearch by rememberPreference(useLoginForSearchKey, true)

    val title = stringResource(R.string.use_account_for_searches)
    val description = stringResource(R.string.use_account_for_searches_description)
    // Trim the query so a stray edge space cannot hide the row on a near-match.
    val query = searchQuery.trim()
    if (
        query.isNotEmpty() &&
        !title.contains(query, true) &&
        !description.contains(query, true)
    ) return

    OtherSwitchSettingEntry(
        title = title,
        text = if (!isLoggedIn) stringResource(R.string.youtube_connect_first) else description,
        isChecked = isLoggedIn && useLoginForSearch,
        enabled = isLoggedIn,
        onCheckedChange = {
            // OtherSwitchSettingEntry's inner Switch does not receive `enabled`, so a
            // semantic (accessibility) click can still fire this on the greyed-out row
            // — never write the preference (or the in-memory flag) while logged out.
            if (isLoggedIn) {
                useLoginForSearch = it
                Innertube.useLoginForSearch = it
            }
        },
        icon = R.drawable.person
    )
}

/**
 * Restores [Innertube.useLoginForSearch] from the persisted preference at startup —
 * called from [MainApplication.onCreate] next to the `useLoginForBrowse` restore
 * (the flag only lives in memory while the app runs).
 */
fun applyUseLoginForSearch(context: Context) {
    Innertube.useLoginForSearch = context.preferences.getBoolean(useLoginForSearchKey, true)
}
