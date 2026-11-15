package app.n_zik.android.components.ui.screens.rescue

import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.dialog.common.ButtonRow
import app.n_zik.android.components.dialog.common.DialogButton
import app.n_zik.android.components.dialog.common.DialogFrame
import app.n_zik.android.components.dialog.export.BackupDetailOption
import app.n_zik.android.components.settings.appearance.preview.BundledThemeId
import app.n_zik.android.components.settings.appearance.preview.ThemeCollectionEntry
import app.n_zik.android.components.settings.appearance.preview.ThemeStoreLibrary
import app.n_zik.android.components.settings.appearance.preview.readRescueThemeArchive
import app.n_zik.android.components.settings.appearance.preview.restoreRescueThemeArchive
import app.n_zik.android.components.settings.appearance.preview.themeStoreCatalog
import app.n_zik.android.components.settings.appearance.preview.writeRescueThemeArchive
import app.n_zik.android.components.settings.appearance.preview.writeStagedThemeArchive
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.network.fullOfflineModeKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
internal fun RescueThemesCard(profiles: Map<String, String>, guardWrite: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val profile = profiles.keys.singleOrNull()
    val recent = remember(context) {
        context.getSharedPreferences("rescueThemeArchives", android.content.Context.MODE_PRIVATE)
    }
    var previous by remember(profile) {
        mutableStateOf(profile?.let { recent.getString(it, null) }?.let(Uri::parse))
    }
    var operation by rememberSaveable(profile) { mutableStateOf<RescueThemeAction?>(null) }
    var requestedProfile by rememberSaveable { mutableStateOf<String?>(null) }
    var importedArchive by rememberSaveable(profile) { mutableStateOf<Uri?>(null) }
    val input = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = requestedProfile
        requestedProfile = null
        if (uri != null && target != null && target == profile) {
            importedArchive = uri
            operation = RescueThemeAction.Import
        }
    }

    fun rememberArchive(uri: Uri) {
        val id = profile ?: return
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            recent.edit { putString(id, uri.toString()) }
            previous = uri
        } catch (error: SecurityException) {
            Timber.tag("Rescue").d(error, "Theme archive provider did not grant persistent read access")
        }
    }

    RescueSectionCard(stringResource(R.string.recovery_theme_backup_title), R.drawable.ui) {
        RescueActionCard(
            R.drawable.import_outline, stringResource(R.string.common_action_import),
            stringResource(R.string.backup_import_themes_description), enabled = profile != null,
        ) {
            requestedProfile = profile
            input.launch(arrayOf("application/zip", "application/octet-stream"))
        }
        RescueActionCard(
            R.drawable.export_outline, stringResource(R.string.common_action_export),
            stringResource(R.string.backup_export_themes_description), enabled = profile != null,
        ) { operation = RescueThemeAction.Export }
        RescueActionCard(
            R.drawable.restore, stringResource(R.string.recovery_theme_backup_restore_action),
            stringResource(R.string.recovery_theme_backup_restore_description), enabled = previous != null,
            disabledReason = stringResource(R.string.recovery_no_backup),
        ) { operation = RescueThemeAction.Restore }
    }
    operation?.let {
        RescueThemesDialog(
            exporting = it == RescueThemeAction.Export,
            profiles = profiles,
            initialArchive = when (it) {
                RescueThemeAction.Import -> importedArchive
                RescueThemeAction.Restore -> previous
                RescueThemeAction.Export -> null
            },
            guardWrite = guardWrite,
            onArchiveUsed = ::rememberArchive,
            onDismiss = {
                operation = null
                importedArchive = null
            },
        )
    }
}

private enum class RescueThemeAction { Import, Export, Restore }

@Composable
private fun RescueThemesDialog(
    exporting: Boolean,
    profiles: Map<String, String>,
    initialArchive: Uri?,
    guardWrite: (() -> Unit) -> Unit,
    onArchiveUsed: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val libraries = remember(context, profiles) {
        profiles.mapValues { (id, _) -> ThemeStoreLibrary(profileDataPrefs(context, id)) }
    }
    var source by remember { mutableStateOf<Uri?>(null) }
    var options by remember { mutableStateOf<Map<String, List<ThemeCollectionEntry>>>(emptyMap()) }
    var selected by remember { mutableStateOf<Set<Pair<String, String>>>(emptySet()) }
    var omittedProfiles by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(exporting || initialArchive != null) }

    fun showFailure(error: Exception) {
        Timber.tag("Rescue").w(error, "Theme archive operation failed")
        Toasty.error(context, context.getString(R.string.recovery_theme_backup_operation_failed), Toast.LENGTH_LONG, true).show()
    }

    suspend fun perform(operation: suspend () -> Unit) {
        busy = true
        try {
            operation()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            showFailure(error)
        } finally {
            busy = false
        }
    }

    fun selectOptions(entries: Map<String, List<ThemeCollectionEntry>>) {
        options = entries.filterKeys { it in profiles }.filterValues { it.isNotEmpty() }
        omittedProfiles = entries.keys.any { it !in profiles }
        selected = options.flatMap { (profile, themes) -> themes.map { profile to it.id } }.toSet()
    }

    LaunchedEffect(libraries, exporting) {
        if (exporting) perform {
            selectOptions(withContext(NzikDispatchers.DATA) {
                libraries.mapValues { (_, library) ->
                    library.downloads().filter { it.theme != null && it.theme.themeId != BundledThemeId }
                        .map { ThemeCollectionEntry(it.id, it.name) }
                }
            })
        }
    }

    suspend fun loadArchive(uri: Uri) {
        source = null
        selectOptions(emptyMap())
        perform {
            val entries = withContext(NzikDispatchers.DATA) {
                checkNotNull(context.contentResolver.openInputStream(uri)).use {
                    readRescueThemeArchive(context, it, ordinaryTarget = profiles.keys.singleOrNull())
                }
            }
            source = uri
            selectOptions(entries)
        }
    }

    LaunchedEffect(initialArchive) {
        initialArchive?.let { loadArchive(it) }
    }

    val output = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val picks = selected.groupBy({ it.first }, { it.second })
            scope.launch {
                perform {
                    withContext(NzikDispatchers.DATA) {
                        val themes = picks.mapValues { (profile, ids) ->
                            ids.map { checkNotNull(libraries.getValue(profile).download(it)) }
                        }
                        writeStagedThemeArchive(context, uri) {
                            val networkProfiles = picks.keys.filterNot { profile ->
                                profileDataPrefs(context, profile).getBoolean(fullOfflineModeKey, false)
                            }.toSet()
                            writeRescueThemeArchive(context, it, themes, themeStoreCatalog(context, offline = false), networkProfiles)
                        }
                    }
                    onArchiveUsed(uri)
                    Toasty.success(context, context.getString(R.string.theme_backup_export_success), Toast.LENGTH_SHORT, true).show()
                    onDismiss()
                }
            }
        }
    }

    fun submit() {
        val archive = source
        when {
            exporting -> output.launch("n-zik-rescue-themes.zip")
            archive == null || selected.isEmpty() -> Unit
            else -> guardWrite {
                val picks = selected.groupBy({ it.first }, { it.second }).mapValues { (_, ids) -> ids.toSet() }
                scope.launch {
                    perform {
                        val result = withContext(NzikDispatchers.DATA) {
                            checkNotNull(context.contentResolver.openInputStream(archive)).use {
                                restoreRescueThemeArchive(context, it, libraries, picks)
                            }
                        }
                        onArchiveUsed(archive)
                        Toasty.success(context, context.getString(R.string.theme_backup_import_success, result.imported, result.skipped),
                            Toast.LENGTH_SHORT, true).show()
                        onDismiss()
                    }
                }
            }
        }
    }

    val all = options.flatMap { (profile, themes) -> themes.map { profile to it.id } }.toSet()
    DialogFrame(
        onDismiss = { if (!busy) onDismiss() },
        title = stringResource(if (exporting) R.string.theme_backup_export_selection else R.string.theme_backup_import_selection),
        footer = {
            ButtonRow(listOf(
                DialogButton(stringResource(R.string.common_action_cancel), onDismiss, enabled = !busy, danger = true),
                DialogButton(
                    text = stringResource(if (exporting) R.string.common_action_export else R.string.common_action_import),
                    onClick = ::submit, enabled = !busy && selected.isNotEmpty(), primary = true,
                ),
            ))
        },
    ) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(
                    if (exporting) R.string.backup_export_themes_description
                    else R.string.backup_import_themes_description,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            if (omittedProfiles) {
                Text(stringResource(R.string.recovery_theme_backup_no_match), style = MaterialTheme.typography.bodySmall)
            }
            if (exporting && !busy && options.isEmpty()) {
                Text(stringResource(R.string.theme_backup_no_downloads))
            }
            if (all.size > 1) {
                BackupDetailOption(stringResource(R.string.theme_backup_select_all), all == selected, !busy) {
                    selected = if (it) all else emptySet()
                }
            }
            options.forEach { (profile, themes) ->
                Text(profiles.getValue(profile), Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelLarge)
                themes.forEach { theme ->
                    val entry = profile to theme.id
                    BackupDetailOption(theme.name, entry in selected, !busy) {
                        selected = if (it) selected + entry else selected - entry
                    }
                }
            }
        }
    }
}
