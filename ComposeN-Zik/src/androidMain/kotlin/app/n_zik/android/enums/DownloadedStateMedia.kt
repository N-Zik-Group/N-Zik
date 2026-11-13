package app.n_zik.android.enums

import androidx.annotation.DrawableRes
import app.n_zik.android.R

enum class DownloadedStateMedia(
    @field:DrawableRes override val iconId: Int
): Drawable {

    CACHED( R.drawable.download ),

    CACHED_AND_DOWNLOADED( R.drawable.downloaded ),

    DOWNLOADED( R.drawable.downloaded ),

    NOT_CACHED_OR_DOWNLOADED( R.drawable.download );
}


