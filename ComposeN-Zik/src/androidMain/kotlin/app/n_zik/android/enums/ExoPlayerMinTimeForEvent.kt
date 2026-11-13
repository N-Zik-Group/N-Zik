package app.n_zik.android.enums

import androidx.compose.runtime.Composable
import app.n_zik.android.core.enums.TextView

enum class ExoPlayerMinTimeForEvent(
    val asSeconds: Int
): TextView {

    `10s`( 10 ),

    `15s`( 15 ),

    `20s`( 20 ),

    `30s`( 30 ),

    `40s`( 40 ),

    `60s`( 60 );

    val asMillis: Long = this.asSeconds * 1000L

    override val text: String
        @Composable
        get() = this.name
}



