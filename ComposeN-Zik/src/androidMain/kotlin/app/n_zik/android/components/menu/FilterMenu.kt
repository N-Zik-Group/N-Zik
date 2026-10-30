package app.n_zik.android.components.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.ui.iconBadgeBackground
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource

@Composable
fun FilterMenu(
    title: String? = null,
    onDismiss: () -> Unit,
    onAll: (() -> Unit)? = null,
    onOnlineSongs: (() -> Unit)? = null,
    onYoutubeLibrary: (() -> Unit)? = null,
    onVideos: (() -> Unit)? = null,
    onLocal: (() -> Unit)? = null,
    onFavorites: (() -> Unit)? = null,
    onUnmatched: (() -> Unit)? = null,
    onDownloaded: (() -> Unit)? = null,
    onCached: (() -> Unit)? = null,
    onExplicit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    @Composable
    fun SettingIcon(@DrawableRes icon: Int) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .iconBadgeBackground(
                    color = colorPalette().accent.copy(alpha = 0.1f),
                    shape = uiRoundnessShape()
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                tint = colorPalette().accent,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    ListMenu.Menu(title = title) {
        onAll?.let {
            ListMenu.Entry(
                text = stringResource(R.string.all),
                icon = { SettingIcon(R.drawable.musical_notes) },
                onClick = {
                    onDismiss()
                    onAll()
                }
            )
        }
        onOnlineSongs?.let {
            ListMenu.Entry(
                text = stringResource(R.string.online_songs),
                icon = { SettingIcon(R.drawable.globe) },
                onClick = {
                    onDismiss()
                    onOnlineSongs()
                }
            )
        }
        onYoutubeLibrary?.let {
            ListMenu.Entry(
                text = stringResource(R.string.ytm_library),
                icon = { SettingIcon(R.drawable.ytmusic) },
                onClick = {
                    onDismiss()
                    onYoutubeLibrary()
                }
            )
        }
        onVideos?.let {
            ListMenu.Entry(
                text = stringResource(R.string.videos),
                icon = { SettingIcon(R.drawable.video) },
                onClick = {
                    onDismiss()
                    onVideos()
                }
            )
        }
        onUnmatched?.let {
            ListMenu.Entry(
                text = stringResource(R.string.unmatched),
                icon = { SettingIcon(R.drawable.alert) },
                onClick = {
                    onDismiss()
                    onUnmatched()
                }
            )
        }
        onFavorites?.let {
            ListMenu.Entry(
                text = stringResource(R.string.favorites),
                icon = { SettingIcon(R.drawable.heart) },
                onClick = {
                    onDismiss()
                    onFavorites()
                }
            )
        }
        onLocal?.let {
            ListMenu.Entry(
                text = stringResource(R.string.on_device),
                icon = { SettingIcon(R.drawable.devices) },
                onClick = {
                    onDismiss()
                    onLocal()
                }
            )
        }
        onDownloaded?.let {
            ListMenu.Entry(
                text = stringResource(R.string.downloaded),
                icon = { SettingIcon(R.drawable.downloaded) },
                onClick = {
                    onDismiss()
                    onDownloaded()
                }
            )
        }
        onCached?.let {
            ListMenu.Entry(
                text = stringResource(R.string.cached),
                icon = { SettingIcon(R.drawable.download) },
                onClick = {
                    onDismiss()
                    onCached()
                }
            )
        }
        onExplicit?.let {
            ListMenu.Entry(
                text = stringResource(R.string.explicit),
                icon = { SettingIcon(R.drawable.explicit) },
                onClick = {
                    onDismiss()
                    onExplicit()
                }
            )
        }
    }
}
