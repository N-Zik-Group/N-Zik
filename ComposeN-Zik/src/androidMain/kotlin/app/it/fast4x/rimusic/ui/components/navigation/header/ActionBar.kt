package app.it.fast4x.rimusic.ui.components.navigation.header

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.components.dialog.logs.CopyLogsDialog
import app.n_zik.android.components.dialog.logs.CrashLogDialog
import app.n_zik.android.components.dialog.logs.DebugLogDialog
import app.n_zik.android.components.menu.header.DebugLogsMenuItem
import app.n_zik.android.components.menu.header.MaintenanceMenuItem
import app.n_zik.android.components.maintenance.MaintenanceSheet
import app.n_zik.android.components.ui.screens.rescue.RescueActivity
import app.n_zik.android.core.coil.ImageCacheFactory
import app.n_zik.android.colorPalette
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.extensions.pip.isPipSupported
import app.it.fast4x.rimusic.extensions.pip.rememberPipHandler
import app.it.fast4x.rimusic.ui.components.themed.DropdownMenu
import app.it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import app.it.fast4x.rimusic.utils.enablePictureInPictureKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.shortcuts.ACTION_RESCUE
import app.n_zik.android.ytAccountThumbnail
import androidx.compose.ui.draw.clip
import app.n_zik.android.thumbnailShape
import app.it.fast4x.rimusic.utils.ytAccountThumbnailKey
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.it.fast4x.rimusic.utils.enableYouTubeLoginKey
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.rememberEncryptedPreference
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.rememberDataStoreBooleanPreference
import it.fast4x.innertube.utils.parseCookieString

@Composable
private fun HamburgerMenu(
    expanded: Boolean,
    navController: NavController,
    onItemClick: (NavRoutes) -> Unit,
    onDismissRequest: () -> Unit,
    onItemConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    val enablePictureInPicture by rememberPreference(enablePictureInPictureKey, false)
    val pipHandler = rememberPipHandler()
    // Rewind master switch (spec GH-275): while off, the menu item below is hidden —
    // same conditional pattern as the PiP item. Live preference read: this header stays
    // composed across the whole session, so a one-shot remember read would keep a stale
    // value after a flip in the settings
    val rewindEnabled by rememberDataStoreBooleanPreference(DataStoreUtils.KEY_REWIND_ENABLED, true)
    // Maintenance sheet (spec-maintenance-dialog): the state lives at the HamburgerMenu
    // body level (NOT inside the DropdownMenu content) because opening the sheet calls
    // onItemConsumed() which dismisses the menu — a remember inside the menu content
    // could be destroyed on dismissal. The header stays composed for the whole session.
    var showMaintenanceSheet by remember { mutableStateOf(false) }

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
    // Rewind button (Cubic-style yearly listening recap, issue #275); hidden while the
    // feature is disabled in the settings (spec GH-275). Uses the monochrome logo
    // (ic_launcher_monochrome — the black & white one the player's media notification
    // uses) instead of a generic icon.
    if (rewindEnabled)
        menu.add(
            DropdownMenu.Item(
                R.drawable.ic_launcher_monochrome,
                R.string.rewind
            ) { onItemClick( NavRoutes.rewindHome ) }
        )
    // Listen Together button (spec-listen-together)
    menu.add(
        DropdownMenu.Item(
            R.drawable.people,
            R.string.listen_together
        ) { onItemClick( NavRoutes.listenTogether ) }
    )
    // PC server button
    menu.add(
        DropdownMenu.Item(
            R.drawable.devices,
            R.string.bridge_server
        ) { onItemClick( NavRoutes.bridgeServer ) }
    )
    // Profiles button
    menu.add(
        DropdownMenu.Item(
            R.drawable.person,
            R.string.profiles
        ) { onItemClick( NavRoutes.profiles ) }
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
    // Debug button (same design as the other items: short tap toggles the debug logs,
    // long press opens the Misc settings directly on the Debug card)
    menu.add {
        DebugLogsMenuItem(
            onLongClick = {
                navController.navigate("${NavRoutes.settings.name}?tab=7&focus=debug")
                onItemConsumed()
            },
            onConsume = onItemConsumed
        )
    }
    // Maintenance button (spec-maintenance-dialog): short tap opens the app-state
    // sheet, long press opens the Misc settings directly on the Maintenance card
    menu.add {
        MaintenanceMenuItem(
            onOpen = { showMaintenanceSheet = true },
            onLongClick = {
                navController.navigate("${NavRoutes.settings.name}?tab=7&focus=maintenance")
                onItemConsumed()
            },
            onConsume = onItemConsumed
        )
    }
    // Rescue Center button (opens the :rescue process activity, same intent as the launcher shortcut)
    menu.add(
        DropdownMenu.Item(
            R.drawable.shortcut_rescue,
            R.string.rescue_center
        ) {
            context.startActivity(
                Intent(context, RescueActivity::class.java).setAction(ACTION_RESCUE)
            )
            onItemConsumed()
        }
    )
    menu.Draw()
    // Maintenance sheet (spec-maintenance-dialog): composed as a SIBLING of the
    // Popup (never inside the menu content), driven by the body-level state above.
    // renderLogsDialog = false: the single CopyLogsDialog.Render() host for the whole
    // app lives just below — this header stays composed for the entire session (it is
    // the AppNavigation topBar), so it is alive on every screen and both sheet entry
    // points (burger + Misc settings card) share it. Render() is a singleton: a second
    // Render() would stack a second dialog on top of the first.
    MaintenanceSheet(
        showSheet = showMaintenanceSheet,
        onDismissRequest = { showMaintenanceSheet = false },
        renderLogsDialog = false,
    )
    CopyLogsDialog.Render()
    // Crash + debug log dialog hosts (spec-maintenance-dialog): same singleton-host
    // rule as CopyLogsDialog above — this persistent header is the single Render()
    // host for the whole app, so both sheet entry points share it.
    CrashLogDialog.Render()
    DebugLogDialog.Render()
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
    var cookie by remember { mutableStateOf("") }
    var isLoginEnabled by remember { mutableStateOf(false) }
    var accountThumbnail by remember { mutableStateOf("") }

    LaunchedEffect(trigger) {
        withContext(NzikDispatchers.DATA) {
            cookie = prefs.getString(app.it.fast4x.rimusic.utils.ytCookieKey, "") ?: ""
            isLoginEnabled = prefs.getBoolean(app.it.fast4x.rimusic.utils.enableYouTubeLoginKey, false)
            accountThumbnail = prefs.getString(app.it.fast4x.rimusic.utils.ytAccountThumbnailKey, "") ?: ""
        }
    }

    val isLoggedIn = remember(cookie, isLoginEnabled) {
        isLoginEnabled && ("SAPISID" in parseCookieString(cookie))
    }

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
            navController = navController,
            onItemClick = onItemClick,
            onDismissRequest = onDismissRequest,
            onItemConsumed = { expanded = false }
        )
    }
// END
}





