package app.n_zik.android.components.player

import androidx.compose.material3.SwipeToDismissBoxValue

/** What a swipe on the mini-player does. */
enum class MiniPlayerSwipeAction {
    /** Cycles the like state of the song playing when the swipe happens. */
    LikeCurrentSong,
    SeekToPrevious,
    SeekToNext,
}

/** Whether the mini-player's own swipe is on: only while it sits at its original position (the sheet is not being opened) and no action has just run. */
fun isMiniPlayerSwipeEnabled(actionFired: Boolean, sheetProgress: Float): Boolean =
    !actionFired && sheetProgress == 0f

/**
 * Whether the full player's horizontal swipe (skip to the previous or next song) is on: only
 * once the sheet is completely open. A half-open sheet, whether it is animating or the finger
 * stopped in the middle of the opening, must not turn a horizontal drag into a skip.
 */
fun isPlayerHorizontalSwipeEnabled(disabledByUser: Boolean, sheetExpanded: Boolean): Boolean =
    !disabledByUser && sheetExpanded

/**
 * Maps a swipe direction to its action.
 *
 * @param isEssentialType The Essential mini-player has no previous button, so its right swipe
 *        likes the song instead of going back.
 * @return null while the box is at rest.
 */
fun miniPlayerSwipeAction(
    direction: SwipeToDismissBoxValue,
    isEssentialType: Boolean,
): MiniPlayerSwipeAction? = when (direction) {
    SwipeToDismissBoxValue.StartToEnd ->
        if (isEssentialType) MiniPlayerSwipeAction.LikeCurrentSong else MiniPlayerSwipeAction.SeekToPrevious

    SwipeToDismissBoxValue.EndToStart -> MiniPlayerSwipeAction.SeekToNext
    SwipeToDismissBoxValue.Settled -> null
}
