package app.n_zik.android.components.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.faceAvatarSource
import app.it.fast4x.rimusic.utils.faceNameSource
import app.it.fast4x.rimusic.utils.isProfileNameValid
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.readProfileIds
import app.it.fast4x.rimusic.utils.resolveProfileDisplayName
import app.it.fast4x.rimusic.utils.saveFaceAvatarSource
import app.it.fast4x.rimusic.utils.saveFaceNameSource
import app.it.fast4x.rimusic.utils.saveProfileDisplayName
import app.it.fast4x.rimusic.utils.takenProfileNames
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.components.dialog.common.InputDialog
import app.n_zik.android.components.ui.screens.profiles.AccountLoginState
import app.n_zik.android.components.ui.screens.profiles.ProfileFaceAvatar
import app.n_zik.android.components.ui.screens.profiles.faceSourceIcon
import app.n_zik.android.components.ui.screens.profiles.faceSourceLabel
import app.n_zik.android.components.ui.screens.profiles.loadAccountLoginState
import app.n_zik.android.components.ui.screens.profiles.profileAvatarSource
import app.n_zik.android.components.ui.screens.profiles.removeProfileAvatarFile
import app.n_zik.android.components.ui.screens.profiles.saveProfileAvatarFromUri
import app.n_zik.android.typography
import app.n_zik.android.uiRoundnessShape
import app.n_zik.android.utils.FACE_SOURCE_DISCORD
import app.n_zik.android.utils.FACE_SOURCE_LASTFM
import app.n_zik.android.utils.FACE_SOURCE_PROFILE
import app.n_zik.android.utils.FACE_SOURCE_YOUTUBE
import app.n_zik.android.utils.ProfileFace
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.resolveFaceAvatar
import app.n_zik.android.utils.resolveFaceName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * First-composition behavior of the profile step, as a pure mapping (unit-tested in
 * [OnboardingProfileStepActionTest]).
 */
enum class OnboardingProfileStepAction {
    /** Show the step as usual — the import restored no profile and the base has no custom name. */
    SHOW,

    /** Auto-advance — the import settled the clone's identity (restored profiles and/or base name). */
    AUTO_ADVANCE
}

/**
 * Profiles restored by the import step (a non-empty profile list — the state file
 * carries it), or a custom base name already stored (restored by the import), mean
 * the clone's identity is settled by the import: the step advances on its own, the
 * restored names and face sources are kept as-is, never silently overwritten (the
 * base without a custom name keeps the default name, renamable on the profiles
 * page). A database-only import restores no profile, so the step is still shown.
 */
fun profileStepInitialAction(
    baseHasCustomName: Boolean,
    importedProfilesPresent: Boolean
): OnboardingProfileStepAction =
    if (baseHasCustomName || importedProfilesPresent) OnboardingProfileStepAction.AUTO_ADVANCE
    else OnboardingProfileStepAction.SHOW

/**
 * Last step of the first-launch onboarding flow: the identity of the base clone —
 * its name, the source of the face name shown in the app, its photo and the source
 * of the face avatar (a full mirror of the face card of the settings). The accounts
 * step comes before this one, so the account face sources can be unlocked by a
 * login made there (or a cookie restored by the import).
 *
 * The step writes only into the profile store: the base display name
 * (`profile_preferences.displayName_default`), the face name source
 * (`preferences.faceNameSource`) and the face avatar source
 * (`preferences.faceAvatarSource`, plain prefs — the active profile is the base
 * during onboarding) and the photo file (`profiles/default/avatar.jpg`, the same
 * helpers as the face card). The no-op rule of the rename flows applies: a name
 * identical to the resolved base name stores nothing; a face source is only
 * written when it differs from the default "profil".
 *
 * The face sources are the same four as the settings face card (same labels and
 * icons); an account source is locked while that account is not logged in (the
 * state is a one-shot read on the DATA dispatcher — the secure prefs are
 * keystore-backed, never built in composition). The face preview applies the same
 * pure resolvers as the face chain to the state as typed.
 *
 * Auto-skip: when the import step settled the identity (restored profiles or a
 * custom base name), the step advances on its own ([profileStepInitialAction]).
 *
 * @param onComplete continue pressed (or the auto-skip fired) — the flow
 *   completes (this is the last step)
 */
@Composable
fun OnboardingProfileScreen(
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val defaultName = stringResource(R.string.profile_base_name)

    // The resolved base name: the field prefill and the no-op guard — a name
    // identical to the resolved name is not stored as a custom name (the resolved
    // name of the base comes from profile_base_name, never from the store)
    val resolvedBaseName = remember {
        resolveProfileDisplayName(DEFAULT_PROFILE_ID, app.profileDisplayName(DEFAULT_PROFILE_ID), defaultName)
    }
    // rememberSaveable: a rotation must keep the typed name and the chosen
    // sources (the same rule as the import dialogs)
    var name by rememberSaveable { mutableStateOf(resolvedBaseName) }
    var nameSource by rememberSaveable { mutableStateOf(app.faceNameSource) }
    var avatarSource by rememberSaveable { mutableStateOf(app.faceAvatarSource) }
    var invalidName by remember { mutableStateOf(false) }
    // Bumped when a photo is saved or removed: re-reads the photo file state and
    // re-resolves the face preview (the same version-bump pattern as the settings
    // face card)
    var photoVersion by rememberSaveable { mutableStateOf(0) }

    // The account login state + the captured account names / avatars drive the
    // source locks and the face preview (one-shot read — the accounts step comes
    // before this one, so a login made there is already reflected). Off the main
    // thread: the secure prefs are keystore-backed, never built in composition.
    var account by remember { mutableStateOf<AccountLoginState?>(null) }
    LaunchedEffect(Unit) {
        account = withContext(NzikDispatchers.DATA) { loadAccountLoginState(app, DEFAULT_PROFILE_ID) }
    }
    val lockedSources = remember(account) {
        val acc = account
        buildSet {
            if (acc == null || !acc.youtube) add(FACE_SOURCE_YOUTUBE)
            if (acc == null || !acc.discord) add(FACE_SOURCE_DISCORD)
            if (acc == null || !acc.lastfm) add(FACE_SOURCE_LASTFM)
        }
    }

    // The base clone's photo file — re-read only when a pick/delete happened
    // (the same guarded remember as the settings face card)
    val profilePhoto = remember(photoVersion) { app.profileAvatarSource(DEFAULT_PROFILE_ID) }

    // The face preview: the pure resolvers (the same ones the face chain uses)
    // applied to the state AS TYPED — the name is not stored until continue, and
    // the photo only exists on disk once picked. Until the account state loads,
    // the face renders the deterministic initials of the typed name (the face
    // always renders something).
    val accountState = account
    val previewFace = ProfileFace(
        name = resolveFaceName(
            source = nameSource,
            profileName = name.ifBlank { defaultName },
            ytLoggedIn = accountState?.youtube ?: false,
            ytName = accountState?.ytName.orEmpty(),
            discordLoggedIn = accountState?.discord ?: false,
            discordName = accountState?.discordName.orEmpty(),
            lastfmLoggedIn = accountState?.lastfm ?: false,
            lastfmName = accountState?.lastfmName.orEmpty(),
            defaultName = defaultName,
        ),
        avatar = resolveFaceAvatar(
            source = avatarSource,
            profileName = name.ifBlank { defaultName },
            profilePhoto = profilePhoto,
            ytLoggedIn = accountState?.youtube ?: false,
            ytAvatar = accountState?.ytAvatar.orEmpty(),
            discordLoggedIn = accountState?.discord ?: false,
            discordAvatar = accountState?.discordAvatar.orEmpty(),
            lastfmLoggedIn = accountState?.lastfm ?: false,
            lastfmAvatar = accountState?.lastfmAvatar.orEmpty(),
        ),
    )

    // The import settling the identity (restored profiles or a custom base name)
    // skips the step; the restored names, sources and photos are kept as-is,
    // never silently overwritten — the base without a custom name keeps the
    // default.
    val baseHasCustomName = remember { app.profileDisplayName(DEFAULT_PROFILE_ID)?.isNotBlank() == true }
    val importedProfilesPresent = remember { app.readProfileIds().isNotEmpty() }
    LaunchedEffect(Unit) {
        if (
            profileStepInitialAction(baseHasCustomName, importedProfilesPresent) ==
            OnboardingProfileStepAction.AUTO_ADVANCE
        ) {
            Timber.tag("Onboarding").i("Identity settled by the import, auto-advancing the profile step")
            onComplete()
        }
    }

    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // The same helper as the settings face card: decode, downsize to at
            // most 512px, re-encode JPEG, write profiles/default/avatar.jpg
            val saved = withContext(NzikDispatchers.DATA) {
                saveProfileAvatarFromUri(app, DEFAULT_PROFILE_ID, uri)
            }
            if (saved) {
                photoVersion++
                Timber.tag("Onboarding").i("Base clone photo saved")
            } else {
                Toaster.e(R.string.face_photo_failed)
            }
        }
    }

    fun finishProfile() {
        val candidate = name.trim()
        // A cleared field keeps the resolved name: the preview renders the resolved
        // face for a blank field, so blank is a no-op, not an invalid name
        val nameChanged = candidate.isNotBlank() && candidate != resolvedBaseName
        // Same rules as the rename flows: unique among every profile's display
        // names except the base's own (the name being edited) + file-name safety +
        // no line breaks
        if (
            nameChanged && !isProfileNameValid(
                candidate,
                app.takenProfileNames(app.readProfileIds(), defaultName)
                    .filterNot { it.trim().equals(resolvedBaseName.trim(), ignoreCase = true) }
            )
        ) {
            invalidName = true
            return
        }
        if (nameChanged) {
            app.saveProfileDisplayName(DEFAULT_PROFILE_ID, candidate)
            Timber.tag("Onboarding").i("Base clone named: $candidate")
        }
        // The default "profil" source needs no write: the plain prefs read it back
        // when the key is absent (the same no-op rule for the avatar source)
        if (nameSource != FACE_SOURCE_PROFILE) {
            app.saveFaceNameSource(nameSource)
            Timber.tag("Onboarding").i("Face name source -> $nameSource")
        }
        if (avatarSource != FACE_SOURCE_PROFILE) {
            app.saveFaceAvatarSource(avatarSource)
            Timber.tag("Onboarding").i("Face avatar source -> $avatarSource")
        }
        onComplete()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colorPalette().background0)
            // The flow replaces AppNavigation here: keep the app's edge-to-edge
            // behavior — background full-bleed, content clear of the system bars
            .statusBarsPadding()
            .navigationBarsPadding()
            // Small screens / enlarged fonts: header + the card (preview + name
            // field + the two source selectors + the photo + the button) can
            // exceed the viewport — the whole step scrolls instead of clipping
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            // drawable (PNG) instead of mipmap: the adaptive-icon XML wins on device and
            // Compose painterResource only supports vectors/rasters
            painter = painterResource(R.drawable.ic_launcher),
            contentDescription = null,
            tint = null,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.onboard_profile_title),
            style = typography().l,
            fontWeight = FontWeight.SemiBold,
            color = colorPalette().text
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboard_profile_desc),
            style = typography().s,
            color = colorPalette().textSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OnboardingActionCard(
            icon = R.drawable.person,
            title = stringResource(R.string.face_profile_name),
            description = "",
            extraContent = {
                // Face preview: the resolved face as it will be saved on
                // continue — the same treatment as the settings face card
                // (avatar + "Shown as" + name)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileFaceAvatar(
                        avatar = previewFace.avatar,
                        faceName = previewFace.name,
                        size = 48.dp
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.display_name_shown_as),
                            style = typography().xxs.copy(color = colorPalette().textSecondary)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = previewFace.name,
                            style = typography().s.copy(color = colorPalette().text),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // stringResource is @Composable: hoisted out of the non-composable
                // semantics lambda
                val fieldHint = stringResource(R.string.onboard_profile_field_hint)
                TextField(
                    value = name,
                    onValueChange = {
                        name = it
                        invalidName = false
                    },
                    singleLine = true,
                    placeholder = { Text(fieldHint) },
                    colors = InputDialog.defaultTextFieldColors(),
                    shape = uiRoundnessShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .semantics {
                            contentDescription = fieldHint
                        }
                )

                if (invalidName) {
                    Text(
                        text = stringResource(R.string.profile_name_invalid_or_taken),
                        style = typography().xxs,
                        color = Color.Red,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }

                // Face-name source selector: the same four options as the settings
                // face card (same labels, same icons); an account source is locked
                // while that account is not logged in
                Text(
                    text = stringResource(R.string.face_name_source),
                    style = typography().xxs,
                    color = colorPalette().textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    faceSources.forEach { option ->
                        FaceSourceOptionRow(
                            option = option,
                            selected = nameSource == option,
                            locked = option in lockedSources,
                            onSelect = { nameSource = option }
                        )
                    }
                }

                // Face-avatar source selector: the same four options + the same
                // locks as the name source (one selector per face property, like
                // the settings face card)
                Text(
                    text = stringResource(R.string.face_avatar_source),
                    style = typography().xxs,
                    color = colorPalette().textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    faceSources.forEach { option ->
                        FaceSourceOptionRow(
                            option = option,
                            selected = avatarSource == option,
                            locked = option in lockedSources,
                            onSelect = { avatarSource = option }
                        )
                    }
                }

                // The base clone's photo: pick from the gallery (the same helper
                // as the settings face card) or remove it — the face preview
                // follows live
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .clip(uiRoundnessShape())
                        .clickable {
                            photoPicker.launch(
                                PickVisualMediaRequest(
                                    mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.image),
                        contentDescription = null,
                        tint = colorPalette().text,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = stringResource(R.string.face_photo),
                        style = typography().s,
                        color = colorPalette().text,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = stringResource(
                            if (profilePhoto != null) R.string.face_photo_set else R.string.face_photo_not_set
                        ),
                        style = typography().xxs.copy(color = colorPalette().textSecondary)
                    )

                    if (profilePhoto != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                // File IO off the main thread — the photo file is
                                // removed alone (never the whole profile files
                                // dir), then the preview re-resolves
                                scope.launch {
                                    withContext(NzikDispatchers.DATA) {
                                        removeProfileAvatarFile(app, DEFAULT_PROFILE_ID)
                                    }
                                    photoVersion++
                                    Timber.tag("Onboarding").i("Base clone photo removed")
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
                }

                Button(
                    onClick = {
                        finishProfile()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorPalette().accent,
                        contentColor = colorPalette().textSecondary
                    ),
                    shape = uiRoundnessShape(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                ) {
                    // Single line: the button is full width, so an enlarged font must not
                    // wrap the label onto a second line
                    Text(
                        stringResource(R.string.onboard_profile_continue),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        )
    }
}

/** The four face sources, in selector order — the name source and the avatar
 * source share the same options, labels and icons as the settings face card. */
private val faceSources = listOf(
    FACE_SOURCE_PROFILE,
    FACE_SOURCE_YOUTUBE,
    FACE_SOURCE_DISCORD,
    FACE_SOURCE_LASTFM,
)

/**
 * One row of the face-source selectors (the name source and the avatar source
 * render the same rows; an account source is locked while that account is not
 * logged in, faded and unclickable — the same rule as the settings face card).
 */
@Composable
private fun FaceSourceOptionRow(
    option: String,
    selected: Boolean,
    locked: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(uiRoundnessShape())
            .clickable(enabled = !locked) { onSelect() }
            .alpha(if (locked) 0.4f else 1f)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        RadioButton(
            selected = selected,
            enabled = !locked,
            onClick = onSelect,
            colors = RadioButtonDefaults.colors(
                selectedColor = colorPalette().accent,
                unselectedColor = colorPalette().textSecondary
            )
        )

        Spacer(modifier = Modifier.width(8.dp))

        Icon(
            painter = painterResource(faceSourceIcon(option)),
            contentDescription = null,
            tint = colorPalette().text,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = faceSourceLabel(option),
            style = typography().s,
            color = colorPalette().text,
            modifier = Modifier.weight(1f)
        )

        if (locked) {
            Text(
                text = stringResource(R.string.face_source_locked),
                style = typography().xxs.copy(color = colorPalette().textSecondary)
            )
        }
    }
}
