package app.n_zik.android.components.settings.appearance.preview

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.n_zik.android.R
import app.n_zik.android.components.dialog.export.BackupDetailOption
import app.n_zik.android.components.settings.common.SettingsDescription
import app.n_zik.android.core.network.OfflineMode
import app.n_zik.android.core.profiles.profileDataPrefs
import app.n_zik.android.core.utils.Toaster
import app.n_zik.android.utils.coroutines.NzikDispatchers
import app.n_zik.android.utils.getActiveProfile
import app.n_zik.android.utils.preferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class ThemeCollectionImport(val source: Uri, val ids: Set<String>) {
    companion object {
        val Saver = listSaver<ThemeCollectionImport?, String>(
            save = { selection -> selection?.let { listOf(it.source.toString()) + it.ids } ?: emptyList() },
            restore = { values -> values.firstOrNull()?.let { ThemeCollectionImport(Uri.parse(it), values.drop(1).toSet()) } },
        )
    }
}

internal fun restoreSelectedThemeCollection(
    context: Context,
    target: String,
    selection: ThemeCollectionImport,
): ThemeCollectionResult =
    checkNotNull(context.contentResolver.openInputStream(selection.source)).use {
        restoreThemeCollection(context, it, ThemeStoreLibrary(profileDataPrefs(context, target)), selection.ids)
    }

internal class ThemeCollectionBackupState(
    val canSubmit: Boolean,
    val primaryLabelRes: Int,
    private val onSubmit: () -> Unit,
    private val content: @Composable () -> Unit,
) {
    fun submit() = onSubmit()

    @Composable
    fun Content() = content()
}

@Composable
internal fun rememberThemeCollectionBackup(
    export: Boolean,
    selected: Boolean,
    onComplete: () -> Unit,
    onImportPrepared: ((ThemeCollectionImport) -> Unit)? = null,
): ThemeCollectionBackupState {
    val context = LocalContext.current
    val profile = getActiveProfile(context)
    val library = remember(context, profile) { ThemeStoreLibrary(context.preferences) }
    val offline by OfflineMode.rememberEnabled()
    val catalog = remember(context, offline) { themeStoreCatalog(context, offline) }
    val scope = rememberCoroutineScope()
    val complete by rememberUpdatedState(onComplete)
    var source by rememberSaveable(profile) { mutableStateOf<Uri?>(null) }
    var selection by rememberSaveable(profile, export) { mutableStateOf<ArrayList<String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<ThemeCollectionEntry>?>(null) }
    val downloads = remember(library, selected, export) {
        if (selected && export) library.downloads().filter { it.theme != null && it.theme.themeId != BundledThemeId }
        else emptyList()
    }

    LaunchedEffect(selected) {
        if (!selected && !export) {
            source = null
            entries = null
            selection = null
        }
    }

    LaunchedEffect(source, profile) {
        val uri = source ?: return@LaunchedEffect
        busy = true
        try {
            entries = withContext(NzikDispatchers.DATA) {
                checkNotNull(context.contentResolver.openInputStream(uri)).use {
                    readThemeCollectionIndex(context, it)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.tag("ThemeStore").w(error, "Could not read theme backup")
            entries = null
            source = null
            Toaster.e(R.string.theme_backup_archive_error)
        } finally {
            busy = false
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (error: SecurityException) {
                Timber.tag("ThemeStore").d(error, "Theme archive provider did not grant persistent read access")
            }
            selection = null
            entries = null
            source = uri
        }
    }
    val downloadOptions = downloads.map { it.id to it.name }
    val options = if (export) downloadOptions else entries.orEmpty().map { it.id to it.name }
    val allIds = options.map { it.first }
    val checked = selection?.intersect(allIds.toSet()) ?: allIds.toSet()

    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            val themes = downloads.filter { it.id in checked }
            busy = true
            scope.launch {
                try {
                    withContext(NzikDispatchers.DATA) {
                        check(getActiveProfile(context) == profile)
                        writeStagedThemeArchive(context, uri) {
                            writeThemeCollectionArchive(context, it, themes, catalog, allowNetwork = !offline)
                        }
                    }
                    Toaster.s(R.string.theme_backup_export_success)
                    complete()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Timber.tag("ThemeStore").w(error, "Could not export theme backup")
                    Toaster.e(R.string.theme_backup_write_error)
                } finally {
                    busy = false
                }
            }
        }
    }

    fun restore(selection: ThemeCollectionImport) {
        busy = true
        scope.launch {
            try {
                val result = withContext(NzikDispatchers.DATA) {
                    check(getActiveProfile(context) == profile)
                    restoreSelectedThemeCollection(context, profile, selection)
                }
                Toaster.s(context.getString(R.string.theme_backup_import_success, result.imported, result.skipped))
                complete()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag("ThemeStore").w(error, "Could not restore theme backup")
                Toaster.e(R.string.theme_backup_restore_error)
            } finally {
                busy = false
            }
        }
    }

    return ThemeCollectionBackupState(
        canSubmit = selected && !busy && when {
            export -> checked.isNotEmpty()
            source == null -> true
            else -> entries != null && checked.isNotEmpty()
        },
        primaryLabelRes = when {
            export -> R.string.theme_backup_export_selection
            entries == null -> R.string.theme_backup_choose_zip
            else -> R.string.theme_backup_import_selection
        },
        onSubmit = submit@{
            if (busy || !selected) return@submit
            when {
                export && checked.isNotEmpty() -> outputPicker.launch("n-zik-themes.zip")
                !export && source == null -> filePicker.launch(arrayOf("application/zip", "application/octet-stream"))
                !export && entries != null && checked.isNotEmpty() -> {
                    val selection = ThemeCollectionImport(checkNotNull(source), checked)
                    if (onImportPrepared == null) restore(selection)
                    else onImportPrepared(selection)
                }
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 44.dp, bottom = 8.dp)) {
            if (options.isEmpty() && export) {
                SettingsDescription(stringResource(R.string.theme_backup_no_downloads))
            } else {
                if (options.size > 1) {
                    BackupDetailOption(
                        stringResource(R.string.theme_backup_select_all), checked.size == options.size, !busy,
                    ) { select -> selection = ArrayList(if (select) allIds else emptyList()) }
                }
                options.forEach { (id, name) ->
                    BackupDetailOption(name, id in checked, !busy) { select ->
                        selection = ArrayList(if (select) checked + id else checked - id)
                    }
                }
            }
        }
    }
}
