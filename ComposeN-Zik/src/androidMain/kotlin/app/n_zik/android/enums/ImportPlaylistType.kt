package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R

enum class ImportPlaylistType(@StringRes val titleId: Int) {
    RiMusic(R.string.import_playlist_nzik),
    Exportify(R.string.import_playlist_exportify_net),
    RiPlay(R.string.import_playlist_riplay)
}
