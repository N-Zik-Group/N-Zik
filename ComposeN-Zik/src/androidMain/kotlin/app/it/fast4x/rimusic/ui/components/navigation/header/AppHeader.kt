package app.it.fast4x.rimusic.ui.components.navigation.header

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.ui.components.themed.Button
import app.it.fast4x.rimusic.ui.styling.favoritesIcon
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.uiRoundnessShape
import app.it.fast4x.rimusic.utils.VoiceSearchState
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.disableNavigationBackStackKey
import app.n_zik.android.LocalTopBarOffset
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LocalContentColor

class AppHeader(
    val navController: NavController
) {

    companion object {

        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun colors(): TopAppBarColors = TopAppBarColors(
            containerColor = colorPalette().background0,
            titleContentColor = colorPalette().text,
            scrolledContainerColor = colorPalette().background0,
            navigationIconContentColor = colorPalette().background0,
            actionIconContentColor = colorPalette().text
        )
    }

    @Composable
    fun Draw() {
        val context = LocalContext.current
        val currentEntry by navController.currentBackStackEntryAsState()
        val isHome = currentEntry?.destination?.route?.let { route ->
            route.startsWith(NavRoutes.home.name) || route == NavRoutes.queue.name
        } ?: true
        val isVoiceSearchActive = VoiceSearchState.isActive
        val themeBackground = colorPalette().background0

        // We animate only the alpha for the voice search overlay.
        // This prevents `animateColorAsState` from incorrectly animating 
        // the application's theme colors (e.g. Dark to AMOLED) at cold start.
        val voiceSearchAlpha by animateFloatAsState(
            targetValue = if (isVoiceSearchActive) 0.85f else 0f,
            animationSpec = tween(200),
            label = "voiceSearchAlpha"
        )
        
        // Animate the start padding smoothly so the logo has nice spacing when home, 
        // and the back button aligns correctly when present.
        val startPadding by animateDpAsState(
            targetValue = if (isHome) 12.dp else 4.dp,
            animationSpec = tween(200)
        )

        val topBarOffsetState = LocalTopBarOffset.current

        // Custom Row layout — responds to AnimatedVisibility size changes each frame,
        // so the logo+title smoothly shifts as the back button slides in/out.
        Row(
            modifier = Modifier
                .offset { IntOffset(0, topBarOffsetState.value.roundToInt()) }
                .background(themeBackground)
                .background(Color.Black.copy(alpha = voiceSearchAlpha))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = startPadding, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Restore TopAppBar's default content coloring behavior for action buttons
            CompositionLocalProvider(
                LocalContentColor provides colorPalette().text
            ) {
                // Back button — animates in from the left, pushes title smoothly
                AnimatedVisibility(
                    visible = !isHome,
                    enter = fadeIn(animationSpec = tween(220)) +
                            slideInHorizontally(animationSpec = tween(220)) { -it } +
                            expandHorizontally(animationSpec = tween(220)),
                    exit  = fadeOut(animationSpec = tween(180)) +
                            slideOutHorizontally(animationSpec = tween(180)) { -it } +
                            shrinkHorizontally(animationSpec = tween(180))
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(uiRoundnessShape())
                            .clickable {
                                if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
                                    val disableBackStack = context.preferences.getBoolean(disableNavigationBackStackKey, false)
                                    if (disableBackStack) {
                                        navController.navigate(NavRoutes.home.name) {
                                            popUpTo(NavRoutes.home.name) { inclusive = true }
                                        }
                                    } else {
                                        navController.popBackStack()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            R.drawable.chevron_back,
                            colorPalette().favoritesIcon,
                            0.dp,
                            24.dp
                        ).Draw()
                    }
                }
    
                // Logo + Title — shifts right smoothly as back button expands
                AppTitle(navController, context)
    
                Spacer(modifier = Modifier.weight(1f))
    
                // Action icons (search, settings…)
                ActionBar(navController)
            }
        }
    }
}