package app.n_zik.android.core.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.n_zik.android.BuildConfig
import app.n_zik.android.core.rescue.RescueFiles
import app.n_zik.android.core.profiles.ProfileStorageIdentity
import app.n_zik.android.core.profiles.ProfileWorkOwner
import app.n_zik.android.core.profiles.profileDataPrefs
import kotlinx.coroutines.CancellationException
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import app.it.fast4x.rimusic.utils.*
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import java.time.Year

object BackupManager {
    const val PREF_INTERVAL = "autoBackupIntervalKey"
    const val PREF_DB_CHANGE = "autoBackupOnDbChangeKey"
    const val PREF_URI = "autoBackupUriKey"
    const val PREF_MAX_BACKUPS = "autoBackupMaxBackupsKey"
    const val PREF_CUSTOM_INTERVAL_VALUE = "autoBackupCustomIntervalValueKey"
    const val PREF_CUSTOM_INTERVAL_UNIT = "autoBackupCustomIntervalUnitKey"
    const val PREF_SCHEDULE_HOUR = "autoBackupScheduleHourKey"
    const val PREF_SCHEDULE_DAY_OF_WEEK = "autoBackupScheduleDayOfWeekKey"
    const val PREF_SCHEDULE_DAY_OF_MONTH = "autoBackupScheduleDayOfMonthKey"
    const val PREF_TARGET = "autoBackupTargetKey"
    const val PREF_INCLUDE_YTB = "autoBackupIncludeYtbKey"
    const val PREF_INCLUDE_DISCORD = "autoBackupIncludeDiscordKey"
    const val PREF_INCLUDE_LASTFM = "autoBackupIncludeLastfmKey"
    const val PREF_INCLUDE_PROXY = "autoBackupIncludeProxyKey"
    const val PREF_PRE_INSTALL = "autoBackupPreInstallKey"

    const val TARGET_DATABASE = 0
    const val TARGET_SETTINGS = 1
    const val TARGET_BOTH = 2
    const val TARGET_ACCOUNTS = 3
    const val TARGET_ALL = 4

    const val INTERVAL_NONE = 0
    const val INTERVAL_HOURLY = 1
    const val INTERVAL_DAILY = 2
    const val INTERVAL_WEEKLY = 3
    const val INTERVAL_CUSTOM = 4
    const val INTERVAL_MONTHLY = 5

    const val UNIT_MINUTES = 1
    const val UNIT_HOURS = 2
    const val UNIT_DAYS = 3
    const val UNIT_WEEKS = 4
    const val UNIT_MONTHS = 5

    private const val WORK_NAME = "AutoBackupWorker"

    fun scheduleBackup(context: Context, interval: Int) {
        val owner = ProfileWorkOwner.capture(context) ?: return
        scheduleBackup(context, interval, owner)
    }

    internal fun scheduleBackup(context: Context, interval: Int, owner: ProfileStorageIdentity) {
        if (!ProfileWorkOwner.isBound(context, owner)) return
        Timber.tag("AutoBackup").d("scheduleBackup called with interval: $interval")
        val workManager = WorkManager.getInstance(context)
        val workName = ProfileWorkOwner.name(WORK_NAME, owner)
        workManager.cancelUniqueWork(WORK_NAME)
        if (interval == INTERVAL_NONE) {
            workManager.cancelUniqueWork(workName)
            return
        }

        val prefs = profileDataPrefs(context, owner.id)
        val now = LocalDateTime.now()
        var targetTime = now

        when (interval) {
            INTERVAL_HOURLY -> {
                targetTime = now.plusHours(1)
            }
            INTERVAL_CUSTOM -> {
                val value = prefs.getInt(PREF_CUSTOM_INTERVAL_VALUE, 12).toLong()
                val unit = prefs.getInt(PREF_CUSTOM_INTERVAL_UNIT, UNIT_HOURS)
                targetTime = when (unit) {
                    UNIT_MINUTES -> now.plusMinutes(value)
                    UNIT_HOURS -> now.plusHours(value)
                    UNIT_DAYS -> now.plusDays(value)
                    UNIT_WEEKS -> now.plusWeeks(value)
                    UNIT_MONTHS -> now.plusMonths(value)
                    else -> now.plusHours(value)
                }
            }
            INTERVAL_DAILY -> {
                val hour = prefs.getInt(PREF_SCHEDULE_HOUR, 2)
                targetTime = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
                if (!targetTime.isAfter(now)) {
                    targetTime = targetTime.plusDays(1)
                }
            }
            INTERVAL_WEEKLY -> {
                val hour = prefs.getInt(PREF_SCHEDULE_HOUR, 2)
                val dayOfWeek = prefs.getInt(PREF_SCHEDULE_DAY_OF_WEEK, 1) // 1 = Monday
                targetTime = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
                
                while (targetTime.dayOfWeek.value != dayOfWeek || !targetTime.isAfter(now)) {
                    targetTime = targetTime.plusDays(1)
                }
            }
            INTERVAL_MONTHLY -> {
                val hour = prefs.getInt(PREF_SCHEDULE_HOUR, 2)
                val dayOfMonth = prefs.getInt(PREF_SCHEDULE_DAY_OF_MONTH, 1)
                targetTime = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
                
                val maxDays = targetTime.month.length(Year.isLeap(targetTime.year.toLong()))
                targetTime = targetTime.withDayOfMonth(minOf(dayOfMonth, maxDays))
                
                if (!targetTime.isAfter(now)) {
                    targetTime = targetTime.plusMonths(1)
                    val nextMaxDays = targetTime.month.length(Year.isLeap(targetTime.year.toLong()))
                    targetTime = targetTime.withDayOfMonth(minOf(dayOfMonth, nextMaxDays))
                }
            }
        }

        val initialDelay = ChronoUnit.MILLIS.between(now, targetTime)
        Timber.tag("AutoBackup").d("scheduleBackup: Enqueuing work with delay $initialDelay ms (Target time: $targetTime)")

        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInputData(ProfileWorkOwner.input(owner))
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()

        workManager.enqueueUniqueWork(
            workName,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun triggerOnChangeBackup(context: Context) {
        val owner = ProfileWorkOwner.capture(context) ?: return
        if (!ProfileWorkOwner.isBound(context, owner)) return
        val prefs = profileDataPrefs(context, owner.id)
        val interval = prefs.getInt(PREF_INTERVAL, INTERVAL_NONE)
        if (interval == INTERVAL_NONE) return

        val enabled = prefs.getBoolean(PREF_DB_CHANGE, false)

        if (!enabled) return

        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setInputData(ProfileWorkOwner.input(owner))
            .setInitialDelay(1, TimeUnit.MINUTES) // Debounce 1 minute
            .build()


        WorkManager.getInstance(context).enqueueUniqueWork(
            ProfileWorkOwner.name(WORK_NAME, owner),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    suspend fun executePreInstallBackup(context: Context): Boolean {
        val owner = ProfileWorkOwner.capture(context) ?: return false
        if (!ProfileWorkOwner.isBound(context, owner)) return false
        val prefs = profileDataPrefs(context, owner.id)
        val enabled = prefs.getBoolean(PREF_PRE_INSTALL, false)
        if (!enabled) return true

        val uriString = prefs.getString(PREF_URI, "") ?: ""
        if (uriString.isEmpty()) {
            Timber.tag("AutoBackup").w("Pre-install backup: no URI configured")
            return false
        }

        Timber.tag("AutoBackup").i("Pre-install backup triggered")
        return executeBackup(context, uriString, owner)
    }

    suspend fun executeBackup(context: Context, uriString: String): Boolean =
        ProfileWorkOwner.capture(context)?.let { executeBackup(context, uriString, it) } ?: false

    internal suspend fun executeBackup(context: Context, uriString: String, owner: ProfileStorageIdentity): Boolean {
        if (!ProfileWorkOwner.isBound(context, owner)) return false
        Timber.tag("AutoBackup").i("Starting executeBackup to URI: $uriString")
        if (uriString.isEmpty()) return false
        val treeUri = Uri.parse(uriString)

        return withContext(NzikDispatchers.DATA) {
            try {
                val prefs = profileDataPrefs(context, owner.id)
                val outputs = mutableListOf<OwnedAutoBackupRetention.Output>()
                val target = prefs.getInt(PREF_TARGET, TARGET_DATABASE)
                val includeYtb = prefs.getBoolean(PREF_INCLUDE_YTB, false)
                val includeDiscord = prefs.getBoolean(PREF_INCLUDE_DISCORD, false)
                val includeLastfm = prefs.getBoolean(PREF_INCLUDE_LASTFM, false)
                val includeProxy = prefs.getBoolean(PREF_INCLUDE_PROXY, false)
                Timber.tag("AutoBackup").d("executeBackup settings: Target=$target, IncludeYtb=$includeYtb, IncludeDiscord=$includeDiscord, IncludeLastfm=$includeLastfm, IncludeProxy=$includeProxy")
                
                var success = true
                val date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss"))
                // Every backup file carries the active profile ID in its name: a backup
                // taken under one profile must stay recognizable as such, so it can never
//
                // be mistaken for another profile's data when it is imported later.
                val profile = owner.id
                val parts = backupPartsOf(target)

                if (parts.database) {
                    Timber.tag("AutoBackup").d("Starting database backup...")
                    DatabaseBackup.prepareExport(context, profile).use { export ->
                        val fileName = autoBackupFileName(
                            BuildConfig.APP_NAME, profile, date, AutoBackupKind.DATABASE, export.format.extension
                        )
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(
                            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                        )
                        val newDocUri = DocumentsContract.createDocument(
                            context.contentResolver, docUri, export.format.mime, fileName
                        )
                        if (newDocUri == null) {
                            Timber.tag("AutoBackup").e("Failed to create document for database backup")
                            success = false
                        } else {
                            export.writeTo(context, newDocUri)
                            outputs += OwnedAutoBackupRetention.Output(newDocUri, AutoBackupKind.DATABASE)
                            Timber.tag("AutoBackup").i("Database backup successful to $fileName")
                        }
                    }
                }

                if (parts.settings) {
                    check(ProfileWorkOwner.isBound(context, owner))
                    Timber.tag("AutoBackup").d("Starting settings backup...")
                    val fileName = autoBackupFileName(BuildConfig.APP_NAME, profile, date, AutoBackupKind.SETTINGS)
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri, DocumentsContract.getTreeDocumentId(treeUri)
                    )
                    val newDocUri = DocumentsContract.createDocument(
                        context.contentResolver, docUri, "text/csv", fileName
                    )
                    if (newDocUri != null) {
                        exportSettings(context, newDocUri, owner, includeYtb, includeDiscord, includeLastfm, includeProxy)
                        outputs += OwnedAutoBackupRetention.Output(newDocUri, AutoBackupKind.SETTINGS)
                        Timber.tag("AutoBackup").i("Settings backup successful to $fileName")
                    } else {
                        Timber.tag("AutoBackup").e("Failed to create document for settings backup")
                        success = false
                    }

                }

                if (parts.profileState) {
                    // The profile state that neither the database nor the settings CSV carries:
                    // the list of profile IDs and the base64 face of every profile that has one,
//
                    // in a single .txt. Nothing to archive (a fresh install) is not a failure —
                    // the backup simply carries no copy of it.
                    Timber.tag("AutoBackup").d("Starting profile state backup...")
                    check(ProfileWorkOwner.isBound(context, owner))
                    if (ProfileStateArchive.hasState(context)) {
                        val document = backupProfileState(context, treeUri, profile, date)
                        if (document == null) success = false
                        else outputs += OwnedAutoBackupRetention.Output(document, AutoBackupKind.PROFILE_STATE)
                    }
                }

                if (success && ProfileWorkOwner.isBound(context, owner)) {
                    runCatching {
                        OwnedAutoBackupRetention.recordAndPrune(
                            context, owner, treeUri, outputs, prefs.getInt(PREF_MAX_BACKUPS, 5),
                        )
                    }.onFailure { error ->
                        Timber.tag("AutoBackup").w(error, "Verified backup retention could not finish")
                    }
                } else success = false
                Timber.tag("AutoBackup").i("executeBackup finished. Success: $success")
                success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Timber.tag("AutoBackup").e(e, "executeBackup failed with exception")
                false
            }
        }
    }

    private suspend fun exportSettings(
        context: Context,
        uri: Uri,
        owner: ProfileStorageIdentity,
        includeYtb: Boolean,
        includeDiscord: Boolean,
        includeLastfm: Boolean,
        includeProxy: Boolean,
    ) {
        val entries = SettingsPreferencesBridge.export(context, profileDataPrefs(context, owner.id))
        check(ProfileWorkOwner.isBound(context, owner))
        val credentials = context.encryptedPreferences.all
        check(ProfileWorkOwner.isBound(context, owner))

        Timber.tag("AutoBackup").d("exportSettings: Base settings count: ${entries.size}")

        // Single source of truth (RescueFiles): the selected credential groups plus, when
        // checked, the proxy key — exactly as the manual export and the rescue build them.
        entries.addAll(
            RescueFiles.buildCredentialExport(
                credentials,
                includeYtb,
                includeDiscord,
                includeLastfm,
                includeProxy
            )
        )

        Timber.tag("AutoBackup").d("exportSettings: Final settings count to write: ${entries.size} (Include Ytb: $includeYtb, Include Discord: $includeDiscord, Include Lastfm: $includeLastfm, Include Proxy: $includeProxy)")

        checkNotNull(context.contentResolver.openOutputStream(uri)) { "Cannot open the settings backup destination" }.use { outStream ->
            csvWriter().open(outStream) {
                writeRow("Type", "Key", "Value")
                flush()
                entries.forEach { writeRow(it.first, it.second, it.third) }
                close()
            }
        }
    }

    /** Writes the installed profile registry and faces as a single document. */
    private suspend fun backupProfileState(context: Context, treeUri: Uri, profile: String, date: String): Uri? {
        val fileName = autoBackupFileName(BuildConfig.APP_NAME, profile, date, AutoBackupKind.PROFILE_STATE)
        val docUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val newDocUri = DocumentsContract.createDocument(context.contentResolver, docUri, "text/plain", fileName)
            ?: return null
        val outStream = context.contentResolver.openOutputStream(newDocUri) ?: return null
        outStream.use { out -> ProfileStateArchive.writeArchive(context, out) }
        Timber.tag("AutoBackup").i("Profile state backup successful to $fileName")
        return newDocUri
    }

    suspend fun verifyBackupLocation(context: Context) {
        val owner = ProfileWorkOwner.capture(context) ?: return
        if (!ProfileWorkOwner.isBound(context, owner)) return
        val prefs = profileDataPrefs(context, owner.id)
        val workName = ProfileWorkOwner.name(WORK_NAME, owner)
        val uriString = prefs.getString(PREF_URI, "") ?: ""
        if (uriString.isEmpty()) return

        try {
            val treeUri = Uri.parse(uriString)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri)
            )
            
            var exists = false
            try {
                val cursor = withContext(NzikDispatchers.DATA) {
                    context.contentResolver.query(
                        docUri,
                        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                        null, null, null
                    )
                }
                cursor?.use {
                    if (it.moveToFirst()) exists = true
                }
            } catch (e: Exception) {
                exists = false
            }

            if (!exists) {
                Timber.tag("AutoBackup").w("Backup location is invalid or missing. Resetting PREF_URI.")
                prefs.edit().putString(PREF_URI, "").apply()
                // Stop the backup from running and reset interval to avoid silent failures
                prefs.edit().putInt(PREF_INTERVAL, INTERVAL_NONE).apply()
                WorkManager.getInstance(context).cancelUniqueWork(workName)
            }
        } catch (e: Exception) {
            Timber.tag("AutoBackup").e(e, "Error verifying backup location")
            prefs.edit().putString(PREF_URI, "").apply()
            prefs.edit().putInt(PREF_INTERVAL, INTERVAL_NONE).apply()
            WorkManager.getInstance(context).cancelUniqueWork(workName)
        }
    }
}

/** Kind of an auto-backup file. The infix and the extension are part of the file name. */
internal enum class AutoBackupKind(val infix: String, val extension: String) {
    DATABASE("", "sqlite"),
    SETTINGS("_Settings", "csv"),
    PROFILE_STATE("_Profiles", "txt");
}

/**
 * Name of an auto-backup file: app name, active profile ID, timestamp, kind. The profile ID is
 * part of every name so a backup taken under one profile stays recognizable when it is imported
 * later.
 */
internal fun autoBackupFileName(
    appName: String,
    profile: String,
    date: String,
    kind: AutoBackupKind,
    extension: String = kind.extension,
): String =
    "${appName}_${profile}_${date}${kind.infix}_AutoBackup.$extension"

private val databaseAutoBackupName = Regex(
    """${Regex.escape(BuildConfig.APP_NAME)}_(?:(.+)_)?\d{4}-\d{2}-\d{2}_\d{6}_AutoBackup(?: \(\d+\))?\.((?i:sqlite|db|zip))""",
)

/** Classifies generated backups, including legacy database, settings and profile-face names. */
internal fun autoBackupKindOf(displayName: String): AutoBackupKind? {
    if (!displayName.contains("AutoBackup")) return null
    return when {
        isDatabaseAutoBackup(displayName) -> AutoBackupKind.DATABASE
        displayName.endsWith("Settings_AutoBackup.csv") -> AutoBackupKind.SETTINGS
        displayName.endsWith("Profiles_AutoBackup.txt") -> AutoBackupKind.PROFILE_STATE
        displayName.endsWith("Face_AutoBackup.jpg") -> AutoBackupKind.PROFILE_STATE
        else -> null
    }
}

private fun isDatabaseAutoBackup(displayName: String): Boolean {
    val match = databaseAutoBackupName.matchEntire(displayName) ?: return false
    val profile = match.groupValues[1]
    return if (profile.isEmpty()) match.groupValues[2].equals("sqlite", ignoreCase = true) else isProfileIdSafe(profile)
}

/** The backup parts a target covers: the database, the settings CSV, the profile state. */
internal data class BackupParts(
    val database: Boolean,
    val settings: Boolean,
    val profileState: Boolean
)

/**
 * The parts backed up for [target] (pure, unit-tested without Android): Database and Settings
 * stay separate, Both keeps its database + settings scope, Accounts archives only the profile
 * state (the list and the face of every profile that has one), All adds it to database +
 * settings. An unknown target falls back to the stored default.
 */
internal fun backupPartsOf(target: Int): BackupParts = when (target) {
    BackupManager.TARGET_DATABASE -> BackupParts(true, false, false)
    BackupManager.TARGET_SETTINGS -> BackupParts(false, true, false)
    BackupManager.TARGET_BOTH -> BackupParts(true, true, false)
    BackupManager.TARGET_ACCOUNTS -> BackupParts(false, false, true)
    BackupManager.TARGET_ALL -> BackupParts(true, true, true)
    else -> BackupParts(true, false, false)
}
