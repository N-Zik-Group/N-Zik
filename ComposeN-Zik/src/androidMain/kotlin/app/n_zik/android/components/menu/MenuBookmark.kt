package app.n_zik.android.components.menu

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.core.sync.FavoriteAction
import app.n_zik.android.ui.styling.favoritesIcon

@Composable
internal fun BoxScope.MenuBookmarkBadge(bookmarked: Boolean) {
    val palette = colorPalette()
    Icon(
        painter = painterResource(if (bookmarked) R.drawable.bookmark else R.drawable.bookmark_slash),
        contentDescription = null,
        tint = if (bookmarked) palette.favoritesIcon else palette.red,
        modifier = Modifier.align(Alignment.BottomStart)
            .absoluteOffset(x = (-8).dp)
            .padding(2.dp)
            .size(12.dp),
    )
}

@Composable
internal fun menuLikeActionDescription(
    state: Boolean?,
    dislikesEnabled: Boolean,
    @StringRes add: Int,
    @StringRes remove: Int,
): String {
    val action = if (dislikesEnabled) FavoriteAction.Rotate else FavoriteAction.Toggle
    return stringResource(
        when (action.next(state)) {
            true -> add
            false -> R.string.library_dislikes_action_add
            null -> if (state == false) R.string.library_dislikes_action_remove else remove
        }
    )
}
