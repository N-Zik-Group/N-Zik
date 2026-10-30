package app.n_zik.android.components.navigation.header

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.it.fast4x.rimusic.utils.encryptedPreferencesUpdateTrigger
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.n_zik.android.R
import app.n_zik.android.components.ui.screens.profiles.loadActiveProfileFace
import app.n_zik.android.components.ui.screens.profiles.profileFaceUpdateTrigger
import app.n_zik.android.utils.FaceAvatar
import app.n_zik.android.utils.ProfileFace
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/** The root retains a resolved avatar while side headers are replaced during navigation. */
internal val LocalHeaderProfileFace = compositionLocalOf<ProfileFace?> { null }

internal fun initialHeaderProfileFace(defaultName: String): ProfileFace =
    ProfileFace(defaultName, FaceAvatar.Initials(defaultName))

@Composable
internal fun rememberHeaderProfileFace(): ProfileFace {
    val context = LocalContext.current
    val defaultName = stringResource(R.string.profile_base_name)
    val activeProfile = getActiveProfile(context)
    val faceTrigger = encryptedPreferencesUpdateTrigger + profileFaceUpdateTrigger
    var face by remember(activeProfile, defaultName) { mutableStateOf(initialHeaderProfileFace(defaultName)) }
    LaunchedEffect(activeProfile, faceTrigger, defaultName) {
        val resolved = withContext(NzikDispatchers.DATA) {
            runCatching { loadActiveProfileFace(context, defaultName) }
                .onFailure { Timber.tag("ActionBar").e(it, "Failed to resolve the active profile face") }
                .getOrNull()
        }
        if (resolved != null) face = resolved
    }
    return face
}
