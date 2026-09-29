package app.n_zik.android.components.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import it.fast4x.innertube.utils.parseCookieString
import app.it.fast4x.rimusic.ui.components.themed.DefaultDialog
import app.it.fast4x.rimusic.ui.components.themed.DialogTextButton
import app.it.fast4x.rimusic.ui.screens.settings.OtherSettingsEntry
import app.it.fast4x.rimusic.ui.screens.settings.SettingsSectionCard
import app.it.fast4x.rimusic.utils.center
import app.it.fast4x.rimusic.utils.discordPersonalAccessTokenKey
import app.it.fast4x.rimusic.utils.faceAvatarSource
import app.it.fast4x.rimusic.utils.faceNameSource
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.isProfileNameValid
import app.it.fast4x.rimusic.utils.medium
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.readProfileIds
import app.it.fast4x.rimusic.utils.resolveProfileDisplayName
import app.it.fast4x.rimusic.utils.semiBold
import app.it.fast4x.rimusic.utils.saveFaceAvatarSource
import app.it.fast4x.rimusic.utils.saveFaceNameSource
import app.it.fast4x.rimusic.utils.saveProfileDisplayName
import app.it.fast4x.rimusic.utils.takenProfileNames
import app.it.fast4x.rimusic.utils.ytCookieKey
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.settings.SettingsInputDialog
import app.n_zik.android.components.ui.screens.profiles.profileAvatarSource
import app.n_zik.android.components.ui.screens.profiles.ProfileFaceAvatar
import app.n_zik.android.components.ui.screens.profiles.profileSecurePrefs
import app.n_zik.android.components.ui.screens.profiles.profileFaceUpdateTrigger
import app.n_zik.android.components.ui.screens.profiles.removeProfileAvatarFile
import app.n_zik.android.components.ui.screens.profiles.saveProfileAvatarFromUri
import app.n_zik.android.extensions.lastfm.lastfmSessionKey
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.FACE_SOURCE_DISCORD
import app.n_zik.android.utils.FACE_SOURCE_LASTFM
import app.n_zik.android.utils.FACE_SOURCE_PROFILE
import app.n_zik.android.utils.FACE_SOURCE_YOUTUBE
import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.utils.ProfileFace
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.components.ui.screens.profiles.loadProfileFace
import app.kreate.android.me.knighthat.utils.Toaster

/** Login state of the active profile's accounts (one-shot read, per-profile secure prefs). */
private data class AccountLoginState(
    val youtube: Boolean,
    val discord: Boolean,
    val lastfm: Boolean,
)

/**
 * One-shot read of the active profile's account login state (the source dialog locks
 * the account options while the account is not logged in). Every chunk is guarded — a
 * failing read just locks its source, the profile source is never locked.
 */
private fun loadAccountLoginState(app: Context, profileId: String): AccountLoginState {
    val secure = runCatching { profileSecurePrefs(app, profileId) }.getOrNull()
    return AccountLoginState(
        youtube = runCatching {
            parseCookieString(secure?.getString(ytCookieKey, "") ?: "").contains("SAPISID")
        }.getOrDefault(false),
        discord = secure?.getString(discordPersonalAccessTokenKey, "")?.isNotBlank() == true,
        lastfm = secure?.getString(lastfmSessionKey, "")?.isNotBlank() == true,
    )
}

/** Label shown for a face source (dialog rows + the "source" entries). */
@Composable
private fun sourceLabel(source: String): String = when (source) {
    FACE_SOURCE_PROFILE -> stringResource(R.string.face_source_profile)
    FACE_SOURCE_YOUTUBE -> stringResource(R.string.display_name_source_youtube)
    FACE_SOURCE_DISCORD -> stringResource(R.string.face_source_discord)
    FACE_SOURCE_LASTFM -> stringResource(R.string.face_source_lastfm)
    else -> stringResource(R.string.face_source_profile)
}

/** Icon of a face source entry. */
private fun sourceIcon(source: String): Int = when (source) {
    FACE_SOURCE_PROFILE -> R.drawable.person
    FACE_SOURCE_YOUTUBE -> R.drawable.logo_youtube
    FACE_SOURCE_DISCORD -> R.drawable.logo_discord
    FACE_SOURCE_LASTFM -> R.drawable.logo_lastfm
    else -> R.drawable.person
}

/**
 * Face source selector of the Accounts face card: the four sources
 * (profil / YouTube / Discord / Last.fm) as a radio list, with the sources of accounts
 * that are not logged in greyed out, unclickable and labelled "Not logged in"
 * (the "Profil" source is never locked).
 */
@Composable
private fun FaceSourceSelectorDialog(
    onDismiss: () -> Unit,
    title: String,
    selectedValue: String,
    lockedSources: Set<String>,
    onValueSelected: (String) -> Unit,
) {
    val palette = colorPalette()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = uiRoundnessShape(),
                colors = CardDefaults.cardColors(
                    containerColor = palette.background1
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Header
                    BasicText(
                        text = title,
                        style = typography().l.semiBold.copy(
                            color = palette.text
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sources list
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .weight(1f, false)
                    ) {
                        listOf(
                            FACE_SOURCE_PROFILE,
                            FACE_SOURCE_YOUTUBE,
                            FACE_SOURCE_DISCORD,
                            FACE_SOURCE_LASTFM
                        ).forEach { source ->
                            val locked = source in lockedSources
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(0.dp),
                                modifier = Modifier
                                    .clip(uiRoundnessShape())
                                    .clickable(enabled = !locked) {
                                        onDismiss()
                                        onValueSelected(source)
                                    }
                                    .alpha(if (locked) 0.4f else 1f)
                                    .padding(vertical = 0.dp, horizontal = 16.dp)
                                    .fillMaxWidth()
                                    .clip(uiRoundnessShape())
                            ) {
                                RadioButton(
                                    selected = selectedValue == source,
                                    enabled = !locked,
                                    onClick = {
                                        onDismiss()
                                        onValueSelected(source)
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = palette.accent,
                                        unselectedColor = palette.textSecondary
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                BasicText(
                                    text = sourceLabel(source),
                                    style = typography().s.copy(color = palette.text),
                                    modifier = Modifier.weight(1f)
                                )

                                if (locked) {
                                    BasicText(
                                        text = stringResource(R.string.face_source_locked),
                                        style = typography().xxs.copy(color = palette.textSecondary)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cancel button
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.background2,
                            contentColor = palette.text
                        ),
                        shape = uiRoundnessShape()
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = typography().s.semiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * The face card at the top of the Accounts tab: the active profile's face preview
 * (avatar + the name shown in-app), the name / avatar source selectors (an account
 * source is locked while that account is not logged in), the profile name (renamed
 * with the same rules as the Profiles page) and the profile photo (gallery pick via
 * PhotoPicker, removable).
 *
 * The account state and the face are loaded once per profile / source change on the
 * DATA dispatcher (the secure prefs are keystore-backed — never built in composition);
 * the contract is "reflected after the next login / profile switch". The face
 * sources live in the active profile's plain prefs (`preferences`), so a profile
 * switch shows its own face on the next card composition.
 */
@Composable
fun ProfileFaceCard(modifier: Modifier = Modifier) {
    SettingsSectionCard(
        title = stringResource(R.string.profile_face),
        icon = R.drawable.person,
        description = stringResource(R.string.profile_face_description),
        modifier = modifier,
        content = {
            val context = LocalContext.current
            val app = context.applicationContext
            val palette = colorPalette()
            val defaultName = stringResource(R.string.profile_base_name)
            val activeId = remember { getActiveProfile(app) }

            // The stored face sources; the states mirror the saved values and key the
            // face re-resolution (a pick re-reads the prefs through [loadProfileFace]).
            var nameSource by remember { mutableStateOf(app.faceNameSource) }
            var avatarSource by remember { mutableStateOf(app.faceAvatarSource) }
            var faceVersion by remember { mutableStateOf(0) }
            var photoVersion by remember { mutableStateOf(0) }

            // The account login state, the display name and the resolved face come from
            // a one-shot load on the DATA dispatcher — the secure prefs are
            // keystore-backed and must never be built or read in composition. Until the
            // load settles, the account sources stay locked and the face renders the
            // deterministic initials (the face always renders something).
            var account by remember { mutableStateOf<AccountLoginState?>(null) }
            var profileName by remember { mutableStateOf(defaultName) }
            var face by remember { mutableStateOf<ProfileFace?>(null) }
            // The face update trigger covers an external face change (a profile state
            // import) while this card stays composed — the card's own edits already
            // bump faceVersion / photoVersion / the sources.
            LaunchedEffect(activeId, nameSource, avatarSource, faceVersion, photoVersion, profileFaceUpdateTrigger) {
                val loaded = withContext(NzikDispatchers.DATA) {
                    Triple(
                        loadAccountLoginState(app, activeId),
                        resolveProfileDisplayName(activeId, app.profileDisplayName(activeId), defaultName),
                        runCatching { loadProfileFace(app, activeId, defaultName) }.getOrNull(),
                    )
                }
                account = loaded.first
                profileName = loaded.second
                face = loaded.third
            }
            val lockedSources = remember(account) {
                val acc = account
                buildSet {
                    if (acc == null || !acc.youtube) add(FACE_SOURCE_YOUTUBE)
                    if (acc == null || !acc.discord) add(FACE_SOURCE_DISCORD)
                    if (acc == null || !acc.lastfm) add(FACE_SOURCE_LASTFM)
                }
            }
            val hasPhoto = remember(photoVersion) { app.profileAvatarSource(activeId) != null }
            val shownFace = face ?: ProfileFace(profileName, FaceAvatar.Initials(profileName))

            var showNameSourceDialog by remember { mutableStateOf(false) }
            var showAvatarSourceDialog by remember { mutableStateOf(false) }
            var showRenameDialog by remember { mutableStateOf(false) }
            var showErrorPopup by remember { mutableStateOf(false) }

            val scope = rememberCoroutineScope()
            val photoPicker = rememberLauncherForActivityResult(
                ActivityResultContracts.PickVisualMedia()
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                scope.launch {
                    val saved = withContext(NzikDispatchers.DATA) {
                        saveProfileAvatarFromUri(app, activeId, uri)
                    }
                    if (saved) {
                        photoVersion++
                        faceVersion++
                    } else {
                        Toaster.e(R.string.face_photo_failed)
                    }
                }
            }

            // Preview: the face avatar + the name shown in-app.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileFaceAvatar(
                    avatar = shownFace.avatar,
                    faceName = shownFace.name,
                    size = 40.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    BasicText(
                        text = stringResource(R.string.display_name_shown_as),
                        style = typography().xxs.copy(color = palette.textSecondary)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        text = shownFace.name,
                        style = typography().s.copy(color = palette.text),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OtherSettingsEntry(
                title = stringResource(R.string.face_name_source),
                text = sourceLabel(nameSource),
                icon = sourceIcon(nameSource),
                onClick = { showNameSourceDialog = true }
            )

            OtherSettingsEntry(
                title = stringResource(R.string.face_avatar_source),
                text = sourceLabel(avatarSource),
                icon = sourceIcon(avatarSource),
                onClick = { showAvatarSourceDialog = true }
            )

            OtherSettingsEntry(
                title = stringResource(R.string.face_profile_name),
                text = profileName,
                icon = R.drawable.pencil,
                onClick = { showRenameDialog = true }
            )

            OtherSettingsEntry(
                title = stringResource(R.string.face_photo),
                text = stringResource(
                    if (hasPhoto) R.string.face_photo_set else R.string.face_photo_not_set
                ),
                icon = R.drawable.image,
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(
                            mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                trailingContent = if (hasPhoto) {
                    {
                        IconButton(
                            onClick = {
                                // File IO off the main thread — the photo file is
                                // removed alone (never the whole profile files dir).
                                scope.launch {
                                    withContext(NzikDispatchers.DATA) {
                                        removeProfileAvatarFile(app, activeId)
                                    }
                                    photoVersion++
                                    faceVersion++
                                    Timber.tag("ProfileFace").i("Profile photo removed for %s", activeId)
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.trash),
                                contentDescription = stringResource(R.string.face_photo_remove),
                                tint = Color.Red,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                } else {
                    null
                }
            )

            if (showNameSourceDialog) {
                FaceSourceSelectorDialog(
                    onDismiss = { showNameSourceDialog = false },
                    title = stringResource(R.string.face_name_source),
                    selectedValue = nameSource,
                    lockedSources = lockedSources,
                    onValueSelected = { value ->
                        nameSource = value
                        app.saveFaceNameSource(value)
                        // The resolved face name can change: let the header re-resolve it.
                        profileFaceUpdateTrigger++
                        Timber.tag("ProfileFace").i("Face name source -> $value")
                    }
                )
            }

            if (showAvatarSourceDialog) {
                FaceSourceSelectorDialog(
                    onDismiss = { showAvatarSourceDialog = false },
                    title = stringResource(R.string.face_avatar_source),
                    selectedValue = avatarSource,
                    lockedSources = lockedSources,
                    onValueSelected = { value ->
                        avatarSource = value
                        app.saveFaceAvatarSource(value)
                        // The resolved face avatar can change: let the header re-resolve it.
                        profileFaceUpdateTrigger++
                        Timber.tag("ProfileFace").i("Face avatar source -> $value")
                    }
                )
            }

            if (showRenameDialog) {
                SettingsInputDialog(
                    title = stringResource(R.string.profile_rename),
                    initialValue = profileName,
                    placeholder = stringResource(R.string.enter_profile_name),
                    onDismiss = { showRenameDialog = false },
                    onSetValue = { name ->
                        // Same rules as the Profiles page: unique among every profile's
                        // display names (its own is excluded with the same trimmed,
                        // case-insensitive normalization) + the reserved "default"
                        // + file-name safety.
                        val trimmedName = name.trim()
                        // Confirming the name as-is stores nothing: the dialog opens on
                        // the resolved display name, and storing it (the base default
                        // name for the base profile) would make the state archive claim
                        // a state out of nothing.
                        if (trimmedName != profileName) {
                            val taken = app.takenProfileNames(app.readProfileIds(), defaultName)
                                .filterNot { it.trim().equals(profileName.trim(), ignoreCase = true) }
                            if (isProfileNameValid(name, taken)) {
                                app.saveProfileDisplayName(activeId, name.trim())
                                faceVersion++
                                // The initials can change: let the header re-resolve the face.
                                profileFaceUpdateTrigger++
                                Timber.tag("ProfileFace").i("Profile display name renamed for %s", activeId)
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

            if (showErrorPopup) {
                DefaultDialog(
                    onDismiss = { showErrorPopup = false },
                    modifier = Modifier
                ) {
                    BasicText(
                        text = stringResource(R.string.this_profile_alreaty_exist),
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
        }
    )
}
