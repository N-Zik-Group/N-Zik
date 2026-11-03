package app.it.fast4x.rimusic.enums

import android.provider.MediaStore
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.n_zik.android.R
import app.kreate.android.me.knighthat.enums.TextView
import org.intellij.lang.annotations.MagicConstant

enum class OnDeviceSongSortBy(
    @field:MagicConstant(valuesFromClass = MediaStore.Audio.Media::class)
    val value: String,
    @field:StringRes override val textId: Int,
    @field:DrawableRes override val iconId: Int,
): TextView, Drawable {

    Title( MediaStore.Audio.Media.TITLE, R.string.library_sort_title, R.drawable.text ),

    DateAdded( MediaStore.Audio.Media.DATE_ADDED, R.string.library_sort_date_added, R.drawable.calendar ),

    Artist( MediaStore.Audio.Media.ARTIST, R.string.library_sort_artist, R.drawable.artist ),

    Duration( MediaStore.Audio.Media.DURATION, R.string.library_sort_duration, R.drawable.time ),

    Album( MediaStore.Audio.Media.ALBUM, R.string.library_sort_album, R.drawable.album );
}




