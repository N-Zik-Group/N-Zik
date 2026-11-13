package app.n_zik.android.enums

import androidx.annotation.StringRes
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class PipModule(
    @field:StringRes override val textId: Int
): TextView {

    Cover( R.string.settings_picture_in_picture_artwork_module_label )
}



