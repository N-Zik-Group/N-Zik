package app.n_zik.android.components.tab

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import app.n_zik.android.components.ui.UiIconButton as IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.colorPalette
import app.n_zik.android.components.glass.GlassToolbarIcon
import app.n_zik.android.components.glass.GlassToolbarLoading
import app.it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import app.it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import app.it.fast4x.rimusic.utils.isRecommendationEnabledKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor

class SmartShuffle private constructor(
    private val isRecommendationEnabled: () -> Boolean,
    private val isRecommendationsLoading: () -> Boolean,
    private val onToggleRecommendation: () -> Unit
): MenuIcon, Descriptive, DynamicColor {

    override var isFirstColor: Boolean
        get() = isRecommendationEnabled()
        set(value) {}

    companion object {
        @Composable
        operator fun invoke(
            isRecommendationEnabled: () -> Boolean,
            isRecommendationsLoading: () -> Boolean,
            onToggleRecommendation: () -> Unit
        ): SmartShuffle = SmartShuffle(
            isRecommendationEnabled,
            isRecommendationsLoading,
            onToggleRecommendation
        )
    }

    override val iconId: Int = R.drawable.smart_shuffle
    override val messageId: Int = R.string.info_smart_recommendation
    override val menuIconTitle: String
        @Composable
        get() = stringResource(R.string.info_smart_recommendation)

    override fun onShortClick() {
        onToggleRecommendation()
    }

    @Composable
    override fun ToolBarButton() {
        if (isRecommendationsLoading()) {
            if (GlassToolbarLoading()) return
        } else if (GlassToolbarIcon(
            painterResource(iconId),
            if (isRecommendationEnabled()) colorPalette().text else colorPalette().textDisabled,
            32.dp, true, onClick = ::onShortClick, contentDescription = menuIconTitle,
        )) return
        Box(
            modifier = Modifier.size(48.dp), // Standard IconButton size
            contentAlignment = Alignment.Center
        ) {
            if (isRecommendationsLoading()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colorPalette().text,
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = { onToggleRecommendation() }, shape = uiRoundnessShape()) {
                    Icon(
                        painter = painterResource(R.drawable.smart_shuffle),
                        contentDescription = stringResource(R.string.info_smart_recommendation),
                        tint = if (isRecommendationEnabled()) colorPalette().text else colorPalette().textDisabled
                    )
                }
            }
        }
    }
} 

