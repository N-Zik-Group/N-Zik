package app.n_zik.android.components.menu.header

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.conditional
import app.it.fast4x.rimusic.utils.disableScrollingTextKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.ui.header.clampedLabelMaxWidth

/**
 * Maintenance entry for the hamburger menu, visually identical to the standard menu
 * items (same 1:1 layout contract as [DebugLogsMenuItem]: 48dp min height, 12dp
 * horizontal / 0dp vertical padding, 24dp leading icon, 12dp icon-to-text gap). The
 * label is one line, clamped to the rendered width of the "Rescue Center" menu
 * label — no burger label may be wider than it; a longer label scrolls in a
 * marquee instead of widening the menu (honoring the global "disable scrolling
 * text" setting, which falls back to ellipsis).
 *
 * A short tap opens the Maintenance sheet (the app's state at a glance: the last
 * boot passes + the live state of each subsystem) and closes the menu; a long
 * press deep-links to the Misc settings screen directly on the Maintenance card
 * (spec "Maintenance — état de l'app en un regard").
 *
 * @param onOpen called on short tap (opens the Maintenance sheet)
 * @param onLongClick called on long press (deep link to the Maintenance card in
 * Misc settings)
 * @param onConsume called after a short tap so the caller can close the menu
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MaintenanceMenuItem(
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    onConsume: () -> Unit = {}
) {
    val isScrollingTextDisabled by rememberPreference(disableScrollingTextKey, false)
    // The "Rescue Center" label is the width reference of the burger menu: this
    // label is clamped to its rendered width and scrolls in a marquee when longer.
    val labelStyle = MaterialTheme.typography.labelLarge
    val labelMaxWidth = clampedLabelMaxWidth(stringResource(R.string.rescue_center), labelStyle)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    onOpen()
                    onConsume()
                },
                onLongClick = onLongClick
            )
            .sizeIn(minHeight = 48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.shield_checkmark),
            contentDescription = null,
            tint = colorPalette().favoritesIcon,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = stringResource(R.string.maintenance),
            style = labelStyle,
            color = colorPalette().textSecondary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 12.dp)
                .widthIn(max = labelMaxWidth)
                .conditional(!isScrollingTextDisabled) {
                    basicMarquee(iterations = Int.MAX_VALUE)
                }
        )
    }
}
