package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class MenuStyle(
    @field:StringRes override val textId: Int
): TextView {

    List( R.string.library_layout_list_label ),
    Grid( R.string.library_layout_grid_label );
}




