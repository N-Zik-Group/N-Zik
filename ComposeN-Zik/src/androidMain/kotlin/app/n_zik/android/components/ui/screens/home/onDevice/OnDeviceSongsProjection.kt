package app.n_zik.android.components.ui.screens.home.onDevice

import app.n_zik.android.enums.OnDeviceSongSortBy
import app.n_zik.android.enums.SortOrder
import app.n_zik.android.models.Song

internal data class OnDeviceSongsProjectionScope(
    val profile: String,
    val sortBy: OnDeviceSongSortBy,
    val sortOrder: SortOrder,
    val showFolders: Boolean,
    val startFolder: String,
    val parentalControlEnabled: Boolean,
)

internal data class OnDeviceSongsProjection(
    val scope: OnDeviceSongsProjectionScope,
    val revision: Long?,
    val query: String,
    val path: String,
    val hasDeviceSongs: Boolean,
    val folders: List<String>,
    val songs: List<Song>,
) {
    fun canRestore(
        scope: OnDeviceSongsProjectionScope,
        revision: Long,
        query: String,
        permissionGranted: Boolean,
    ): Boolean = permissionGranted && this.scope == scope && this.revision == revision && this.query == query
}
