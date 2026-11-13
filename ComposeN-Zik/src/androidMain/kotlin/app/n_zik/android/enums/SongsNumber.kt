package app.n_zik.android.enums

import androidx.compose.runtime.Composable
import app.n_zik.android.core.enums.TextView

enum class SongsNumber: TextView {
    `1`,
    `2`,
    `3`,
    `4`,
    `5`,
    `6`,
    `7`,
    `8`,
    `9`;

    override val text: String
        @Composable
        get() = this.name

    fun toInt(): Int = this.name.toInt()
}



