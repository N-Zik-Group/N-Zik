package app.n_zik.android.enums

import androidx.compose.runtime.Composable
import app.n_zik.android.core.enums.TextView

enum class FontType: TextView {
    Rubik,
    Poppins;

    override val text: String
        @Composable
        get() = this.name
}



