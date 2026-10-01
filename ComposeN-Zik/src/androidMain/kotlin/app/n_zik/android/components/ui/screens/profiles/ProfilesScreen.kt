package app.n_zik.android.components.ui.screens.profiles

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.it.fast4x.rimusic.enums.ColorPaletteMode
import app.it.fast4x.rimusic.ui.components.Skeleton
import app.it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import app.it.fast4x.rimusic.ui.components.themed.DefaultDialog
import app.it.fast4x.rimusic.ui.components.themed.DialogTextButton
import app.it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import app.it.fast4x.rimusic.ui.screens.settings.SettingsDescription
import app.it.fast4x.rimusic.ui.styling.Dimensions
import app.it.fast4x.rimusic.ui.styling.ModernBlackColorPalette
import app.it.fast4x.rimusic.ui.styling.PureBlackColorPalette
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.clearProfileFaceEntries
import app.it.fast4x.rimusic.utils.colorPaletteModeKey
import app.it.fast4x.rimusic.utils.currentProfileEntries
import app.it.fast4x.rimusic.utils.encryptedPreferences
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.intent
import app.it.fast4x.rimusic.utils.isAtLeastAndroid7
import app.it.fast4x.rimusic.utils.isProfileNameValid
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.preferences
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.profileLastUsed
import app.it.fast4x.rimusic.utils.readProfileIds
import app.it.fast4x.rimusic.utils.rememberPreference
import app.it.fast4x.rimusic.utils.resolveProfileDisplayName
import app.it.fast4x.rimusic.utils.saveProfileDisplayName
import app.it.fast4x.rimusic.utils.saveProfileLastUsed
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.setActiveProfile
import app.it.fast4x.rimusic.utils.takenProfileNames
import app.it.fast4x.rimusic.utils.writeProfileEntries
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.export.ExportProfileStateDialog
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.import.ImportProfileState
import app.n_zik.android.core.backup.ProfileStateArchive
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.notifications.cancelProfileNotifications
import app.n_zik.android.core.profiles.ProfileDataItem
import app.n_zik.android.core.profiles.profileSharedItems
import app.n_zik.android.core.notifications.deleteProfileChannels
import app.n_zik.android.core.notifications.ensureProfileChannels
import app.n_zik.android.core.notifications.recreateProfileChannels
import app.n_zik.android.download.services.MyDownloadService
import app.n_zik.android.playback.services.PlayerServiceModern
import app.n_zik.android.shortcuts.registerAppShortcuts
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.utils.ProfileFace
import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.io.File
import kotlin.system.exitProcess

private const val PREFERENCES_BASE_FILENAME = "preferences"
private const val SECURE_PREFERENCES_BASE_FILENAME = "secure_preferences"

/** One card row of the profiles page, resolved off the composition (DATA dispatcher). */
private data class ProfileRow(
    val id: String,
    val displayName: String,
    val face: ProfileFace,
    val lastUsed: Long?,
    val dbSizeBytes: Long?,
    val settingsSizeBytes: Long?,
    /** The profile's own data sizes (its dirs — spec-profile-data-separation). */
    val imagesSizeBytes: Long?,
    val songCacheSizeBytes: Long?,
    val downloadsSizeBytes: Long?,
    /** The items the profile shares with the base (the sharing toggles' state). */
    val sharedItems: Set<ProfileDataItem>,
)

/** Whole page state: the active profile, the base-first card rows and the export eligibility. */
private data class ProfilePageState(
    val activeId: String,
    val rows: List<ProfileRow>,
    val hasState: Boolean,
)

@Composable
fun ProfileScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberLazyListState()
    val defaultName = stringResource(R.string.profile_base_name)

    var state by remember { mutableStateOf<ProfilePageState?>(null) }
    var refresh by remember { mutableStateOf(0) }

    var showAddPopup by remember { mutableStateOf(false) }
    var showRemovePopup by remember { mutableStateOf(false) }
    var showErrorPopup by remember { mutableStateOf(false) }
    var showChangePopup by remember { mutableStateOf(false) }
    var showRenamePopup by remember { mutableStateOf(false) }
    var removingProfile by remember { mutableStateOf("") }
    var profileToSwitch by remember { mutableStateOf("") }
    var profileToRename by remember { mutableStateOf("") }

    // Profile state (list + faces) import / export — the combined .txt archive, the
    // same one the backup and the rescue screen use. On its own no restart is needed:
    // the list, the names and the faces are re-read on recomposition.
    val exportProfileState = ExportProfileStateDialog(context)
    var showImportConfirm by remember { mutableStateOf(false) }
    var crossProfilePending by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val importProfileState = ImportProfileState(
        context,
        onImportComplete = {
            refresh++
        },
    ) { tag, proceed ->
        crossProfilePending = tag to proceed
    }

    // Every page read (the names file, the faces, the database sizes) happens on the
    // DATA dispatcher, never in composition; [refresh] re-resolves everything after a
    // create / rename / delete.
    LaunchedEffect(refresh) {
        state = withContext(NzikDispatchers.DATA) {
            val ids = context.readProfileIds()
            ProfilePageState(
                activeId = getActiveProfile(context),
                hasState = ProfileStateArchive.hasState(context),
                rows = (listOf(DEFAULT_PROFILE_ID) + ids).map { id ->
                    val displayName = resolveProfileDisplayName(
                        id,
                        context.profileDisplayName(id),
                        defaultName
                    )
                    // The profile's OWN data sizes (its dirs — spec-profile-data-separation).
                    val sizes = profileDataSizes(context, id)
                    ProfileRow(
                        id = id,
                        displayName = displayName,
                        face = runCatching { loadProfileFace(context, id, defaultName) }
                            .getOrElse { ProfileFace(displayName, FaceAvatar.Initials(displayName)) },
                        lastUsed = context.profileLastUsed(id),
                        // File.length() returns 0 for a missing file — null renders
                        // "No data" instead of a misleading "0 B".
                        dbSizeBytes = runCatching {
                            context.getDatabasePath(Database.fileNameForProfile(id)).length().takeIf { it > 0L }
                        }.getOrNull(),
                        settingsSizeBytes = runCatching { profilePrefsFile(context, id).length().takeIf { it > 0L } }.getOrNull(),
                        imagesSizeBytes = sizes.imagesBytes,
                        songCacheSizeBytes = sizes.songCacheBytes,
                        downloadsSizeBytes = sizes.downloadsBytes,
                        sharedItems = context.profileSharedItems(id),
                    )
                },
            )
        }
    }

    // The user profile IDs (the base profile is implicit, never listed).
    val profileIds: List<String> =
        state?.rows?.filter { it.id != DEFAULT_PROFILE_ID }?.map { it.id }.orEmpty()

    Skeleton(
        navController,
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.profiles), R.drawable.person)

        }
    ) {
        val page = state
        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
        ) {
            item(key = "header", contentType = 0) {
                // Listen-Together-style header: the icon is shown and a centered
                // description sits under the title.
                HeaderWithIcon(
                    title = stringResource(R.string.profiles),
                    iconId = R.drawable.person,
                    enabled = false,
                    showIcon = true,
                    modifier = Modifier,
                    onClick = {}
                )
                SettingsDescription(
                    text = stringResource(R.string.profiles_description),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            if (page != null) {
                items(
                    items = page.rows,
                    // Namespaced so an ID cannot collide with the fixed "header" / "add" keys.
                    key = { "profile_${it.id}" },
                    contentType = { 1 }
                ) { row ->
                    ProfileCard(
                        face = row.face,
                        lastUsed = row.lastUsed,
                        dbSizeBytes = row.dbSizeBytes,
                        settingsSizeBytes = row.settingsSizeBytes,
                        imagesSizeBytes = row.imagesSizeBytes,
                        songCacheSizeBytes = row.songCacheSizeBytes,
                        downloadsSizeBytes = row.downloadsSizeBytes,
                        sharedItems = row.sharedItems,
                        isActive = row.id == page.activeId,
                        onSwitch = if (row.id == page.activeId) {
                            null
                        } else {
                            {
                                profileToSwitch = row.id
                                showChangePopup = true
                            }
                        },
                        onRename = {
                            profileToRename = row.id
                            showRenamePopup = true
                        },
                        // The active profile cannot be deleted: after a delete,
                        // activeProfile would point at the gone ID (a fresh empty
                        // profile with no "active" row).
                        onDelete = if (row.id == DEFAULT_PROFILE_ID || row.id == page.activeId) {
                            null
                        } else {
                            {
                                removingProfile = row.id
                                showRemovePopup = true
                            }
                        },
                        // 8dp each side → 16dp between cards, the same gap the
                        // section cards use elsewhere in the app.
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                item(key = "add", contentType = 2) {
                    // Same card style as the export / import cards below.
                    ProfileStateActionCard(
                        icon = R.drawable.add,
                        title = stringResource(R.string.add_profile),
                        description = stringResource(R.string.add_profile_description),
                        onClick = { showAddPopup = true },
                    )
                }
                item(key = "export_state", contentType = 3) {
                    ProfileStateActionCard(
                        icon = R.drawable.export_outline,
                        title = stringResource(R.string.profiles_export_profiles),
                        description = if (page.hasState) {
                            stringResource(R.string.export_accounts_description)
                        } else {
                            stringResource(R.string.rescue_no_profile_state)
                        },
                        enabled = page.hasState,
                        onClick = { exportProfileState.export() },
                    )
                }
                item(key = "import_state", contentType = 4) {
                    ProfileStateActionCard(
                        icon = R.drawable.import_outline,
                        title = stringResource(R.string.profiles_import_profiles),
                        description = stringResource(R.string.import_accounts_description),
                        onClick = { showImportConfirm = true },
                    )
                }
            }
        }

    }

    if (showAddPopup) {
        SettingsInputDialog(
            title = stringResource(R.string.add_profile),
            initialValue = "",
            placeholder = stringResource(R.string.enter_profile_name),
            onDismiss = { showAddPopup = false },
            onSetValue = { name ->
                val trimmedName = name.trim()
                if (isProfileNameValid(trimmedName, context.takenProfileNames(profileIds, defaultName)) &&
                    // The new profile was never renamed: a bare ID line in the names file.
                    context.writeProfileEntries(context.currentProfileEntries() + (trimmedName to ""))
                ) {
                    // The new profile's direct shortcut must appear in the launcher right away
                    // (spec-profile-shortcuts: the shortcut set is re-registered after every
                    // profile add / rename / delete, same call as the settings dialog).
                    registerAppShortcuts(context)
                    refresh++
                } else {
                    // An invalid name and a failed names-file write both surface the
                    // same error popup.
                    showErrorPopup = true
                }
            },
        ).apply {
            showDialog()
            Render()
        }
    }

    val renameRow = state?.rows?.firstOrNull { it.id == profileToRename }
    if (showRenamePopup && renameRow != null) {
        SettingsInputDialog(
            title = stringResource(R.string.profile_rename),
            initialValue = renameRow.displayName,
            placeholder = stringResource(R.string.enter_profile_name),
            onDismiss = { showRenamePopup = false },
            onSetValue = { name ->
                val trimmedName = name.trim()
                // Confirming the name as-is stores nothing: for the base profile the
                // dialog opens on the resolved default name, and storing that as a
                // "custom" name would make the state archive claim the base carries a
                // state (hasState) out of nothing.
                if (trimmedName != renameRow.displayName) {
                    // The profile's own display name is not a collision for itself —
                    // the same normalization as isProfileNameValid (trimmed,
                    // case-insensitive).
                    val taken = context.takenProfileNames(profileIds, defaultName)
                        .filterNot { it.trim().equals(renameRow.displayName.trim(), ignoreCase = true) }
                    if (isProfileNameValid(name, taken)) {
                        context.saveProfileDisplayName(profileToRename, name.trim())
                        // The names file is the backup format: mirror the new display
                        // name into it so a restore never carries a stale name.
                        context.writeProfileEntries(context.currentProfileEntries())
                        // The shortcut keeps its stable ID (the rename never re-creates it);
                        // only its label changes — re-register so the launcher shows the new
                        // name right away (spec-profile-shortcuts).
                        registerAppShortcuts(context)
                        // The channel labels carry the display name ("Player — Work"); Android
                        // channel names are immutable, so the rename is applied by deleting +
                        // re-creating the profile's 6 channels (a no-op for the base, whose
                        // channels keep their base name) (spec per-profile-notifications).
                        recreateProfileChannels(context, profileToRename)
                        // The initials can change: let the always-composed header re-resolve it.
                        profileFaceUpdateTrigger++
                        refresh++
                    } else {
                        showErrorPopup = true
                    }
                }
            },
        ).apply {
            showDialog()
            Render()
        }
    }

    if (showRemovePopup) {
        ConfirmationDialog(
            text = stringResource(R.string.remove_profile),
            onDismiss = { showRemovePopup = false },
            onConfirm = {
                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                    // The deletion does real file I/O (names file, prefs, database,
                    // avatar): keep it off the UI thread, like the page reads.
                    context.writeProfileEntries(context.currentProfileEntries().filterNot { it.first == removingProfile })
                    deletePreferencesForProfile(context, removingProfile)
                    deleteRoomDatabaseByName(context, "data_$removingProfile.db")
                    // The face entries (display name + last use) and the profile's own
                    // files (its custom photo) are purged with the profile.
                    context.clearProfileFaceEntries(removingProfile)
                    deleteProfileAvatar(context, removingProfile)
                    // The profile's 6 notification channels + its pending notifications are
                    // purged with the profile — no orphan channel (or notification) left
                    // behind for a profile that no longer exists.
                    deleteProfileChannels(context, removingProfile)
                    // The profile's SEPARATE data dirs + index are purged with it
                    // (spec-profile-data-separation) — the shared items stay with the
                    // base, and its sharing flags are forgotten.
                    purgeProfileData(context, removingProfile)
                    withContext(NzikDispatchers.UI) {
                        // The deleted profile's shortcut must vanish from the launcher right
                        // away (spec-profile-shortcuts: the shortcut set is re-registered after
                        // every profile add / rename / delete, same call as the settings dialog).
                        // The names file was rewritten above, so this re-registration no longer
                        // sees the deleted profile.
                        registerAppShortcuts(context)
                        refresh++
                    }
                }
            }
        )
    }

    if (showErrorPopup) {
        DefaultDialog(
            onDismiss = { showErrorPopup = false },
            modifier = Modifier
        ) {
            BasicText(
                text = stringResource(R.string.profile_name_invalid_or_taken),
                style = typography().xs.medium.center,
                modifier = Modifier
                    .padding(all = 16.dp)
            )

            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .fillMaxWidth()
            ) {

                DialogTextButton(
                    text = stringResource(R.string.confirm),
                    primary = true,
                    onClick = {
                        showErrorPopup = false
                    }
                )
            }
        }
    }
    if (showChangePopup) {
        ConfirmationDialog(
            text = stringResource(R.string.profile_restart_required),
            onDismiss = { showChangePopup = false },
            onConfirm = {
                executeProfileSwitch(profileToSwitch, context)
                // Close the app with exit 0 (no problem occurred): the new profile's stores
                // can only be loaded in a fresh process. The process exit deliberately lives
                // HERE (not in executeProfileSwitch) so the switch wiring stays pinnable
                // under Robolectric without killing the test JVM (ProfileSwitchTest).
                exitProcess(0)
            }
        )

    }

    if (showImportConfirm) {
        ConfirmationDialog(
            text = stringResource(R.string.rescue_confirm_import_accounts),
            onDismiss = { showImportConfirm = false },
            onConfirm = {
                showImportConfirm = false
                importProfileState.onShortClick()
            }
        )
    }

    // A picked file tagged with a different profile: confirm before importing it.
    crossProfilePending?.let { (tag, proceed) ->
        ConfirmationDialog(
            text = context.getString(
                R.string.rescue_cross_profile_confirm,
                tag,
                resolveProfileDisplayName(
                    getActiveProfile(context),
                    context.profileDisplayName(getActiveProfile(context)),
                    defaultName
                )
            ),
            onDismiss = { crossProfilePending = null },
            onConfirm = {
                crossProfilePending = null
                proceed()
            }
        )
    }
}


/**
 * Runs one whole profile switch (the profiles page's switch dialog target): captures the
 * profile left, cancels its pending notifications (CAP-3 — synchronously, before the switch),
 * records the switch, commits the outgoing profile's in-memory settings, stops the services
 * tied to the old profile, and ensures the incoming profile's 6 channels exist. The caller
 * exits the process afterwards (see the dialog's onConfirm — the process exit deliberately
 * stays out of this function so the wiring is pinnable under Robolectric without killing the
 * test JVM).
 *
 * internal (not private) so the switch wiring is pinnable under Robolectric (ProfileSwitchTest)
 * without composing the screen.
 */
internal fun executeProfileSwitch(profile: String, context: Context) {
    // CAP-3: the notifications pending on the channels of the profile left (rewind, sync,
    // listen-together — the non-ongoing ones survive a process death) are cancelled
    // synchronously, before the switch and the process exit below — per channel, never by
    // tag. Captured here, BEFORE setActiveProfile, so the cancel target can never drift to
    // the destination profile if the statements below are reordered.
    val leftProfile = getActiveProfile(context)
    cancelProfileNotifications(context, leftProfile)

    // The switched profile counts as used from now on (spec: written at switch).
    context.saveProfileLastUsed(profile, System.currentTimeMillis())
    setActiveProfile(profile, context)

    // Save all settings (the outgoing profile's stores, still cached under its file name).
    context.preferences.edit().commit()
    context.encryptedPreferences.edit().commit()

    context.stopService( context.intent<PlayerServiceModern>() )
    context.stopService( context.intent<MyDownloadService>() )

    // Close other activities
    (context as? Activity)?.finishAffinity()

    // The 6 channels of the incoming profile must exist before the process dies (the next
    // boot re-creates them via MainApplication as a safety net).
    ensureProfileChannels(context, profile)
}

private fun deletePreferencesForProfile(
    context: Context,
    profileName: String
): Boolean {
    // The encrypted store of a profile is `secure_preferences_<id>` (the account
    // credentials) — there is no `private_preferences_<id>` in this app.
    val plainName = PREFERENCES_BASE_FILENAME + "_$profileName"
    val secureName = SECURE_PREFERENCES_BASE_FILENAME + "_$profileName"

    val okPlain = deleteSharedPrefsByName(context, plainName)
    val okSecure = deleteSharedPrefsByName(context, secureName)

    // The cached EncryptedSharedPreferences instance still holds the deleted values in
    // memory and would re-materialize the file on its next commit: evict it.
    evictProfileSecurePrefs(profileName)

    return okPlain && okSecure
}

private fun deleteSharedPrefsByName(context: Context, prefsName: String): Boolean {
    return if (isAtLeastAndroid7) {
        context.deleteSharedPreferences(prefsName)
    } else {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .edit(commit = true) { clear() }

        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        val xml = File(dir, "$prefsName.xml")
        val xmlBak = File(dir, "$prefsName.xml.bak")

        var ok = true
        if (xml.exists()) ok = xml.delete() && ok
        if (xmlBak.exists()) ok = xmlBak.delete() && ok
        ok
    }
}

private fun deleteRoomDatabaseByName(context: Context, dbName: String): Boolean {
    val primaryOk = context.deleteDatabase(dbName)

    val dbFile = context.getDatabasePath(dbName)
    var ok = primaryOk
    listOf(
        dbFile,                                   // "…/app_db__{id}"
        File(dbFile.path + "-journal"),   // mode journal (ישן)
        File(dbFile.path + "-wal"),       // write-ahead log
        File(dbFile.path + "-shm")        // shared memory
    ).forEach { f ->
        if (f.exists()) ok = f.delete() && ok
    }
    return ok
}

/**
 * One action card of the Profiles page (import / export the profile state
 * archive, add a profile): the app card style (accent icon box, title,
 * description). When [enabled] is false the card is inert (the caller swaps
 * [description] for the reason).
 */
@Composable
private fun ProfileStateActionCard(
    icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val palette = colorPalette()
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 4.dp,
                shape = uiRoundnessShape(),
                spotColor = palette.accent.copy(alpha = 0.2f),
            )
            .clickable(enabled = enabled) { onClick() },
        shape = uiRoundnessShape(),
        colors = CardDefaults.cardColors(
            // The same rule as the other NZik cards (SettingsSectionCard): the
            // pitch-black themes need the lighter gray, or the card vanishes on
            // pure black.
            containerColor = if (colorPalette() === PureBlackColorPalette ||
                colorPalette() === ModernBlackColorPalette ||
                colorPaletteMode == ColorPaletteMode.PitchBlack
            ) {
                Color(0xFF1A1A1A) // Gray dark for pitch black themes
            } else {
                colorPalette().background1
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(palette.accent.copy(alpha = 0.1f), uiRoundnessShape()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    tint = palette.accent,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = title,
                    style = typography().s.semiBold.copy(color = palette.text),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = description,
                    style = typography().xxs.copy(color = palette.textSecondary),
                )
            }
        }
    }
}
