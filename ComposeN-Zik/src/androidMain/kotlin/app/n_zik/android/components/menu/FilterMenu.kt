package app.n_zik.android.components.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.glass.explicitBadgeIconId
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
        if (icon == R.drawable.explicit && explicitBadgeIconId() == R.drawable.explicit_glass_glyph) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                app.n_zik.android.components.ExplicitBadge(
                    color = colorPalette().accent, modifier = Modifier.size(18.dp),
                )
            }
            return
        }
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
                painter = painterResource(if (icon == R.drawable.explicit) explicitBadgeIconId() else icon),
                tint = colorPalette().accent,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    ListMenu.Menu(title = title) {
        onAll?.let {
            ListMenu.Entry(
                text = stringResource(R.string.common_option_all),
                icon = { SettingIcon(R.drawable.musical_notes) },
                onClick = {
                    onDismiss()
                    onAll()
                }
            )
        }
        onOnlineSongs?.let {
            ListMenu.Entry(
                text = stringResource(R.string.library_filter_online_songs_label),
                icon = { SettingIcon(R.drawable.globe) },
                onClick = {
                    onDismiss()
                    onOnlineSongs()
                }
            )
        }
        onYoutubeLibrary?.let {
            ListMenu.Entry(
                text = stringResource(R.string.library_youtube_music_title),
                icon = { SettingIcon(R.drawable.ytmusic) },
                onClick = {
                    onDismiss()
                    onYoutubeLibrary()
                }
            )
        }
        onVideos?.let {
            ListMenu.Entry(
                text = stringResource(R.string.search_videos_title),
                icon = { SettingIcon(R.drawable.video) },
                onClick = {
                    onDismiss()
                    onVideos()
                }
            )
        }
        onUnmatched?.let {
            ListMenu.Entry(
                text = stringResource(R.string.library_filter_unmatched_songs_label),
                icon = { SettingIcon(R.drawable.alert) },
                onClick = {
                    onDismiss()
                    onUnmatched()
                }
            )
        }
        onFavorites?.let {
            ListMenu.Entry(
                text = stringResource(R.string.library_favorites_title),
                icon = { SettingIcon(R.drawable.heart) },
                onClick = {
                    onDismiss()
                    onFavorites()
                }
            )
        }
        onLocal?.let {
            ListMenu.Entry(
                text = stringResource(R.string.library_on_device_title),
                icon = { SettingIcon(R.drawable.devices) },
                onClick = {
                    onDismiss()
                    onLocal()
                }
            )
        }
        onDownloaded?.let {
            ListMenu.Entry(
                text = stringResource(R.string.library_downloaded_title),
                icon = { SettingIcon(R.drawable.downloaded) },
                onClick = {
                    onDismiss()
                    onDownloaded()
                }
            )
        }
        onCached?.let {
            ListMenu.Entry(
                text = stringResource(R.string.library_cached_title),
                icon = { SettingIcon(R.drawable.download) },
                onClick = {
                    onDismiss()
                    onCached()
                }
            )
        }
        onExplicit?.let {
            ListMenu.Entry(
                text = stringResource(R.string.common_content_explicit_label),
                icon = { SettingIcon(R.drawable.explicit) },
                onClick = {
                    onDismiss()
                    onExplicit()
                }
            )
        }
    }
}
