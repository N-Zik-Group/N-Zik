package app.it.fast4x.rimusic.ui.components.navigation.header

import android.content.Intent
import androidx.compose.ui.res.stringResource
import timber.log.Timber

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import app.n_zik.android.R
import app.n_zik.android.artistThumbnailShape
import app.n_zik.android.components.dialog.logs.CopyLogsDialog
import app.n_zik.android.components.dialog.logs.CrashLogDialog
import app.n_zik.android.components.dialog.logs.DebugLogDialog
import app.n_zik.android.components.menu.header.DebugLogsMenuItem
import app.n_zik.android.components.menu.header.MaintenanceMenuItem
import app.n_zik.android.components.maintenance.MaintenanceSheet
import app.n_zik.android.components.ui.screens.profiles.ProfileFaceAvatar
import app.n_zik.android.components.ui.screens.profiles.loadActiveProfileFace
import app.n_zik.android.components.ui.screens.profiles.profileFaceUpdateTrigger
import app.n_zik.android.components.ui.screens.rescue.RescueActivity
import app.n_zik.android.colorPalette
import app.n_zik.android.utils.ProfileFace
import app.it.fast4x.rimusic.enums.NavRoutes
import app.it.fast4x.rimusic.extensions.pip.isPipSupported
import app.it.fast4x.rimusic.extensions.pip.rememberPipHandler
import app.it.fast4x.rimusic.ui.components.themed.DropdownMenu
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.enablePictureInPictureKey
import app.it.fast4x.rimusic.utils.rememberPreference
import app.n_zik.android.shortcuts.ACTION_RESCUE
import app.n_zik.android.utils.DataStoreUtils
import app.n_zik.android.utils.rememberDataStoreBooleanPreference

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
    val defaultName = stringResource(R.string.profile_base_name)

    // Active profile face (spec-profiles-page-face): it takes the burger icon's place — and
    // the YouTube account thumbnail's, which used to replace the burger only while logged in
    // — because the profile manages its own face: the header always shows whatever the
    // active profile resolved (its photo, a logged-in account's avatar when the profile
    // picked that source, or the deterministic initials). The trigger keys re-resolve it
    // on a profile switch, an account-state change or any face edit (the header never
    // leaves composition, so a one-shot read would go stale).
    val activeProfile = getActiveProfile(context)
    val faceTrigger = app.it.fast4x.rimusic.utils.encryptedPreferencesUpdateTrigger + profileFaceUpdateTrigger
    var activeFace by remember { mutableStateOf<ProfileFace?>(null) }
    LaunchedEffect(activeProfile, faceTrigger, defaultName) {
        activeFace = withContext(NzikDispatchers.DATA) {
            runCatching { loadActiveProfileFace(context, defaultName) }
                .onFailure { Timber.tag("ActionBar").e(it, "Failed to resolve the active profile face") }
                .getOrNull()
        }
    }

    // Search Icon
    HeaderIcon( R.drawable.search) { navController.navigate(NavRoutes.search.name) }

    Box {
        val face = activeFace
        if (face != null)
            ProfileFaceAvatar(
                avatar = face.avatar,
                faceName = face.name,
                size = 32.dp,
                // The clip sits BEFORE the clickable so the click ripple follows the
                // artist shape (a square outline would otherwise flash around it) —
                // the same treatment as the old YT logo here.
                modifier = Modifier
                    .padding(end = 10.dp)
                    .clip(artistThumbnailShape())
                    .clickable { expanded = !expanded }
            )
        // The burger is the first-frame placeholder until the first face load settles
        // (the menu stays reachable either way).
        else HeaderIcon( R.drawable.burger ) { expanded = !expanded }
    
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





