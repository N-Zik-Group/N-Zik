package app.n_zik.android.playback.services.automotive.browse.handlers

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import app.n_zik.android.playback.services.automotive.models.AutoAccessPolicy
import app.n_zik.android.components.dialog.settings.HomeTabsSettingsDialog
import app.n_zik.android.components.dialog.settings.buildHomeTabDefs
import app.n_zik.android.components.ui.screens.home.activeHomeTabIds
import app.n_zik.android.core.database.Database
import app.n_zik.android.download.utils.MyDownloadHelper
import app.n_zik.android.playback.services.automotive.session.AutoSessionConstants
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.browsableMediaItem
import app.n_zik.android.playback.services.automotive.models.AutoMediaItemMapper.drawableUri
import app.n_zik.android.utils.enableQuickPicksPageKey
import app.n_zik.android.utils.homeTabsOrderKey
import app.n_zik.android.utils.preferences

class RootBrowseHandler : BrowseHandler {
    override fun handles(parentId: String): Boolean = parentId == PlayerServiceModern.ROOT

    override suspend fun getChildren(
        parentId: String,
        context: Context,
        database: Database,
        downloadHelper: MyDownloadHelper,
        binder: PlayerServiceModern.Binder?
    ): List<MediaItem> {
        val prefs = context.preferences
        val categories = buildHomeTabDefs()
        val active = activeHomeTabIds(
            HomeTabsSettingsDialog.parseOrder(prefs.getString(homeTabsOrderKey, "").orEmpty()).distinct(),
            quickPicks = prefs.getBoolean(enableQuickPicksPageKey, true),
            songs = prefs.getBoolean("hometab_songs_enabled", true),
            artists = prefs.getBoolean("hometab_artists_enabled", true),
            albums = prefs.getBoolean("hometab_albums_enabled", true),
            playlists = prefs.getBoolean("hometab_playlists_enabled", true),
            offlineMode = !AutoAccessPolicy(context, database, downloadHelper, binder).online,
        )
        return active.map { tab ->
            val (id, type) = when (tab) {
                "quickpicks" -> AutoSessionConstants.ID_QUICK_PICKS to MediaMetadata.MEDIA_TYPE_FOLDER_MIXED
                "artists" -> PlayerServiceModern.ARTIST to MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS
                "albums" -> PlayerServiceModern.ALBUM to MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS
                "playlists" -> PlayerServiceModern.PLAYLIST to MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS
                else -> PlayerServiceModern.SONG to MediaMetadata.MEDIA_TYPE_PLAYLIST
            }
            val category = categories.getValue(tab)
            browsableMediaItem(id, context.getString(category.labelRes), null, drawableUri(context, category.iconRes), type)
        }
    }
}
