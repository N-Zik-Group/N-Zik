package app.it.fast4x.rimusic.enums

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.kreate.android.me.knighthat.enums.TextView
import app.n_zik.android.R

enum class MaxStatisticsItems: TextView {
    `10`,
    `20`,
    `30`,
    `40`,
    `50`,
    `70`,
    `90`,
    `100`,
    `150`,
    `200`,
    Custom,
    Unlimited;

    override val text: String
        @Composable
        get() = this.name

    /**
     * Resolve the effective item limit for this selection.
     * [Custom] uses [customValue] (minimum 1), [Unlimited] means no limit.
     */
    fun toInt(customValue: Int): Int = when (this) {
        Custom -> customValue.coerceAtLeast(1)
        Unlimited -> Int.MAX_VALUE
        else -> this.name.toInt()
    }

    /** Option label shown in value selector dialogs. */
    @Composable
    fun optionLabel(): String = when (this) {
        Custom -> stringResource(R.string.max_items_custom)
        Unlimited -> stringResource(R.string.max_items_unlimited)
        else -> this.name
    }

    /** Display name for settings entries (e.g. "Custom (25)"). */
    @Composable
    fun displayName(customValue: Int): String = when (this) {
        Custom -> stringResource(R.string.max_items_custom_value, customValue)
        else -> optionLabel()
    }
}



