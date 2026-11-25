package app.n_zik.android.ui.components.tab.toolbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import kotlin.random.Random

interface Randomizer<T>: MenuIcon {

    companion object {
        /** To ensure true randomness, [Random] must not be a constant variable. */
        fun nextInt( until: Int ) =
            Random( System.currentTimeMillis() ).nextInt( until )
    }

    fun getItems(): List<T>

    fun onClick( index: Int )

    override val iconId: Int get() = R.drawable.dice
    override val menuIconTitle: String
        @Composable get() = stringResource(R.string.library_action_randomize)
    override val isEnabled: Boolean get() = getItems().isNotEmpty()
    override val color: Color
        @Composable get() = if (isEnabled) colorPalette().text else colorPalette().textDisabled

    override fun onShortClick() {
        val size = getItems().size
        if (size > 0) onClick(nextInt(size))
    }
}


