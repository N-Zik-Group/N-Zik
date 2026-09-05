package app.it.fast4x.rimusic.enums

import app.n_zik.android.R

enum class SyncDirection {
    TWO_WAY,
    APP_TO_YT,
    YT_TO_APP;

    val stringResource: Int
        get() = when (this) {
            TWO_WAY -> R.string.sync_direction_two_way
            APP_TO_YT -> R.string.sync_direction_app_to_yt
            YT_TO_APP -> R.string.sync_direction_yt_to_app
        }
}
