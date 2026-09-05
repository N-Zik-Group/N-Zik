package app.it.fast4x.rimusic.ui.components.navigation.header

import app.n_zik.android.uiRoundnessShape

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.extensions.pip.isPipSupported
import app.it.fast4x.rimusic.extensions.pip.rememberPipHandler
import app.it.fast4x.rimusic.ui.components.themed.DropdownMenu
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.it.fast4x.rimusic.utils.enablePictureInPictureKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.ytAccountThumbnail
import androidx.compose.ui.draw.clip
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import it.fast4x.innertube.utils.parseCookieString

@Composable
private fun HamburgerMenu(
    expanded: Boolean,
    onItemClick: (NavRoutes) -> Unit,
    onDismissRequest: () -> Unit
) {
    val enablePictureInPicture by rememberPreference(enablePictureInPictureKey, false)
    val pipHandler = rememberPipHandler()

    val menu = DropdownMenu(
        expanded = expanded,
        containerColor = colorPalette().background0.copy(0.90f),
        onDismissRequest = onDismissRequest
    )
    // History button
    menu.add(
        DropdownMenu.Item(
            R.drawable.history,
            R.string.history
        ) { onItemClick( NavRoutes.history ) }
    )
    // Statistics button
    menu.add(
        DropdownMenu.Item(
            R.drawable.stats_chart,
            R.string.statistics
        ) { onItemClick( NavRoutes.statistics ) }
    )
    // Picture in picture button
    if (isPipSupported && enablePictureInPicture)
        menu.add(
            DropdownMenu.Item(
                R.drawable.images_sharp,
                R.string.menu_go_to_picture_in_picture
            ) { pipHandler.enterPictureInPictureMode() }
        )
    menu.add { HorizontalDivider() }
    // Settings button
    menu.add(
        DropdownMenu.Item(
            R.drawable.settings,
            R.string.settings
        ) { onItemClick( NavRoutes.settings ) }
    )
    menu.Draw()
}

// START
@Composable
fun ActionBar(
    navController: NavController,
) {
    var expanded by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val trigger = app.it.fast4x.rimusic.utils.encryptedPreferencesUpdateTrigger
    val prefs = context.encryptedPreferences
    val cookie = remember(trigger) { prefs.getString(app.it.fast4x.rimusic.utils.ytCookieKey, "") ?: "" }
    val isLoginEnabled = remember(trigger) { prefs.getBoolean(app.it.fast4x.rimusic.utils.enableYouTubeLoginKey, false) }
    val isLoggedIn = remember(cookie, isLoginEnabled) {
        isLoginEnabled && ("SAPISID" in parseCookieString(cookie))
    }
    val accountThumbnail = remember(trigger) { prefs.getString(app.it.fast4x.rimusic.utils.ytAccountThumbnailKey, "") ?: "" }

    // Search Icon
    HeaderIcon( R.drawable.search) { navController.navigate(NavRoutes.search.name) }

    Box {
        if (isLoggedIn) {
            if (accountThumbnail.isNotEmpty())
                ImageCacheFactory.AsyncImage(
                    thumbnailUrl = accountThumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(32.dp)
                        .clip(uiRoundnessShape())
                        .clickable { expanded = !expanded }
                )
            else HeaderIcon( R.drawable.ytmusic, size = 30.dp ) { expanded = !expanded }
        } else HeaderIcon( R.drawable.burger ) { expanded = !expanded }
    
        // Define actions for when item inside menu clicked,
        // and when user clicks on places other than the menu (dismiss)
        val onItemClick: (NavRoutes) -> Unit = {
            expanded = false
            navController.navigate(it.name)
        }
        val onDismissRequest: () -> Unit = { expanded = false }
    
        // Hamburger menu
        HamburgerMenu(
            expanded = expanded,
            onItemClick = onItemClick,
            onDismissRequest = onDismissRequest
        )
    }
// END
}





