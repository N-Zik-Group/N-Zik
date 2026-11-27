package app.n_zik.android.playback.services

import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import app.n_zik.android.R
import app.n_zik.android.appTextResources
import app.n_zik.android.enums.NotificationButtons

@UnstableApi
internal fun playbackNotificationButtons(
    first: NotificationButtons,
    second: NotificationButtons,
    allowDownload: Boolean,
    labelRes: (NotificationButtons) -> Int,
    iconRes: (NotificationButtons) -> Int,
): List<CommandButton> {
    val buttons = listOf(first, second) + NotificationButtons.entries.filterNot { it == first || it == second }
    return buttons.filter { allowDownload || it != NotificationButtons.Download }.map { button ->
        CommandButton.Builder()
            .setDisplayName(appTextResources().getString(labelRes(button)))
            .setIconResId(iconRes(button))
            .setSessionCommand(button.sessionCommand)
            .build()
    }
}

internal fun radioCommandButtonLabelRes(isRadioActive: Boolean): Int =
    if (isRadioActive) R.string.radio_action_stop else R.string.radio_action_start

internal fun radioCommandButtonIconRes(inactiveIconRes: Int, isRadioActive: Boolean): Int =
    if (isRadioActive) R.drawable.radio_stop else inactiveIconRes

internal fun discoverCommandButtonIconRes(stateIconRes: Int, isDiscoverEnabled: Boolean): Int =
    if (isDiscoverEnabled) R.drawable.discover_stop else stateIconRes

internal fun shuffleOkFlashIconRes(flashActive: Boolean, stateIconRes: Int): Int =
    if (flashActive) R.drawable.shuffle_ok else stateIconRes
