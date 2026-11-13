package app.n_zik.android.enums

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import app.n_zik.android.R
import app.n_zik.android.utils.navigationBarTypeKey
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.core.enums.TextView


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



