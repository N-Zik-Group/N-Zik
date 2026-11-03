package app.it.fast4x.rimusic.enums

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import app.n_zik.android.R
import app.it.fast4x.rimusic.utils.navigationBarTypeKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.kreate.android.me.knighthat.enums.TextView


enum class NavigationBarType(
    @field:StringRes override val textId: Int
): TextView {

    IconAndText( R.string.navigation_style_icon_and_text_label ),
    IconOnly( R.string.navigation_style_icon_only_label );

    companion object {

        @Composable
        fun current(): NavigationBarType = rememberPreference( navigationBarTypeKey, NavigationBarType.IconOnly ).value

    }

    @Composable
    fun isCurrent(): Boolean = current() == this
}



