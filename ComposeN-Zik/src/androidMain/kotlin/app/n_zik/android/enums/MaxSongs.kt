package app.n_zik.android.enums

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.core.enums.TextView

enum class MaxSongs: TextView {

    `50`,
    `100`,
    `200`,
    `300`,
    `500`,
    `1000`,
    `2000`,
    `3000`,
    Unlimited;

    override val text: String
        @Composable
        get() = when( this ) {
            Unlimited -> stringResource( R.string.common_limit_unlimited_label)
            else -> this.name
        }

    fun toInt(): Int = when( this ) {
        Unlimited -> 1_000_000      // YES! This is a valid format in Kotlin
        else -> this.name.toInt()
    }
}



