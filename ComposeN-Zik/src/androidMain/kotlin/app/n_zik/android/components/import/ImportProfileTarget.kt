package app.n_zik.android.components.import

import android.content.Context
import android.net.Uri
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID
import app.it.fast4x.rimusic.utils.currentProfileEntries
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.profileDisplayName
import app.it.fast4x.rimusic.utils.readProfileIds
import app.it.fast4x.rimusic.utils.resolveProfileDisplayName
import app.it.fast4x.rimusic.utils.writeProfileEntries
import app.n_zik.android.MainApplication
import app.kreate.android.me.knighthat.utils.Toaster
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.RestartAppDialog
import app.n_zik.android.core.database.Database
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * One row of the "import into which profile?" dialog: the stable [id], the name to
 * show, whether it is the active profile, and whether it only exists in the picked
 * file(s) — a profile carried by the import with no local profile yet.
 */
data class ProfileTargetOption(
    val id: String,
    val displayName: String,
    val isActive: Boolean,
    val fromFileTag: Boolean,
)

/**
 * Builds the import target list: every local profile (the base one first), plus every
 * profile carried by the picked file(s) that does not exist locally yet (offered as its
 * own row so the bundle can be imported as-is). [importedProfiles] is the `(id, display
 * name)` list read from the files being imported — the single database/settings tag for a
 * single/both import, or the full profile list of the state file for an all import — each
 * id already validated or absent ([RescueFiles.backupProfileTagOf] /
 * [app.n_zik.android.core.backup.ProfileStateArchive.readArchive]).
 */
fun buildProfileTargetOptions(
    context: Context,
    importedProfiles: List<Pair<String, String>>,
    defaultName: String
): List<ProfileTargetOption> {
    val localIds = (listOf(DEFAULT_PROFILE_ID) + context.readProfileIds()).distinct()
    val known = localIds.toMutableSet()
    // The base is always local, so it never gets its own "from the file" row — but when
    // the file explicitly carries the base's renamed name, the base row adopts it (that
    // is exactly what the base will be named after the import). The raw tag pair of a
    // single/both import (`id` offered as its own name) is excluded.
    val baseNameFromFile = importedProfiles
        .firstOrNull { it.first == DEFAULT_PROFILE_ID }
        ?.second
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != DEFAULT_PROFILE_ID }
    val options = localIds.mapTo(mutableListOf<ProfileTargetOption>()) { id ->
        ProfileTargetOption(
            id = id,
            displayName = if (id == DEFAULT_PROFILE_ID)
                baseNameFromFile ?: resolveProfileDisplayName(id, context.profileDisplayName(id), defaultName)
            else
                resolveProfileDisplayName(id, context.profileDisplayName(id), defaultName),
            isActive = id == getActiveProfile(context),
            fromFileTag = false,
        )
    }
    importedProfiles.forEach { (id, name) ->
        // `known.add` is true only for a profile not seen before (neither local nor
        // already offered), so duplicates are dropped and local rows are never shadowed.
        if (known.add(id)) {
            options += ProfileTargetOption(
                id = id,
                displayName = if (name.isNotBlank()) name else id,
                isActive = false,
                fromFileTag = true,
            )
        }
    }
    return options
}

/**
 * "Import into which profile?" — rendered as a proper NZik dialog
 * ([app.n_zik.android.components.dialog.backup.ImportTargetProfileDialog]): a radio
 * list of the local profiles (the active one marked) plus the file-tag row when it is
 * unknown. The import button stays disabled until a profile is picked, so an import
 * can never run without an explicit target.
 */

/**
 * Runs the deferred per-profile import chain (database -> settings -> profile state)
 * into the profile chosen in [app.n_zik.android.components.dialog.backup.ImportTargetProfileDialog].
 *
 * The parts that are null are simply skipped. When the database or the settings import
 * fails, the chain stops there — nothing else is written, the host stays open for a
 * retry ([onCompleted] is never reached), and the restart prompt only appears when the
 * live database was already closed by the failed replacement. A profile-state failure
 * stops the chain the same way. On success [onCompleted] closes the host flow, then the
 * restart prompt appears when the target is the active profile and the database or the
 * settings were replaced (the running app must re-read them); for any other target a
 * confirmation toast is shown instead.
 */
object ImportChainRunner {

    fun start(
        context: Context,
        target: String,
        databaseUri: Uri?,
        settingsUri: Uri?,
        stateUri: Uri?,
        onCompleted: () -> Unit,
    ) {
        NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
            runChain(context, target, databaseUri, settingsUri, stateUri, onCompleted)
        }
    }

    /**
     * The chain body, exposed as a suspend function so its control flow is testable —
     * [start] only moves it off the UI thread.
     */
    internal suspend fun runChain(
        context: Context,
        target: String,
        databaseUri: Uri?,
        settingsUri: Uri?,
        stateUri: Uri?,
        onCompleted: () -> Unit,
    ) {
        val importDb = databaseUri != null
        val importSettingsPart = settingsUri != null
        val isActive = target == getActiveProfile(context)

        // The flag is set synchronously by the guarded block's onFailure handler (same
        // coroutine, same dispatcher), so it is final when the block returns.
        var dbSettingsFailed = false
        runGuardedImport(
            onFailure = { e ->
                dbSettingsFailed = true
                Timber.tag("ImportChain").e(e, "Import chain failed (target: $target)")
                withContext(NzikDispatchers.UI) {
                    Toaster.e("Import failed: ${e.message}")
                }
            },
            isDatabaseClosed = { importDb && Database.isClosed },
            showRestartPrompt = { RestartAppDialog.showDialog() }
        ) {
            databaseUri?.let { ImportDatabase.importTo(context, it, target) }
            settingsUri?.let { ImportSettings.importFile(context, it, target) }
        }
        // runGuardedImport swallows the exception (it already reported it): a failure
        // stops the chain before anything else is written — the host stays open for a
        // retry, so onCompleted must never be reached past a failed db/settings step.
        if (dbSettingsFailed) return

        // A target that only exists in the picked file(s) is not in the local profile
        // list yet: register it, so the data just written into it is not orphaned (the
        // all-mode state import carries the full list anyway — this only matters for
        // the database/settings-only chains).
        if ((importDb || importSettingsPart) &&
            target != DEFAULT_PROFILE_ID &&
            context.readProfileIds().none { it == target }
        ) {
            context.writeProfileEntries(
                context.currentProfileEntries() +
                    (target to context.profileDisplayName(target).orEmpty())
            )
        }

        // The profile state reports through a Result (it never throws): a failure
        // keeps the host open for a retry, and the restart prompt still applies
        // when the target is the active profile and the database or the settings
        // were replaced — the app must reload them.
        stateUri?.let { uri ->
            val result = RescueFiles.importProfileState(context, uri)
            if (result.isFailure) {
                withContext(NzikDispatchers.UI) {
                    Timber.tag("ImportChain").e(result.exceptionOrNull(), "Profile state import failed (target: $target)")
                    Toaster.e(context.getString(R.string.import_profile_state_failed))
                    if (isActive && (importDb || importSettingsPart)) {
                        RestartAppDialog.showDialog()
                    }
                }
                return
            }
        }

        withContext(NzikDispatchers.UI) {
            if (importDb && isActive) {
                // The active profile's live database was just replaced: reset the login
                // state so the app starts fresh after the restart (the same reset the
                // immediate import performs).
                MainApplication.cookieStatus = MainApplication.CookieStatus.NOT_LOGGED_IN
                context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
                    .edit().remove("ytCookieExpired").apply()
            }
            onCompleted()
            if (importDb || importSettingsPart) {
                if (isActive) {
                    RestartAppDialog.showDialog()
                } else {
                    val name = resolveProfileDisplayName(
                        target,
                        context.profileDisplayName(target),
                        context.getString(R.string.profile_base_name)
                    )
                    Toaster.i(context.getString(R.string.import_completed_profile, name))
                }
            }
        }
    }
}
