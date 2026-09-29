package app.n_zik.android.core.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the auto-backup file names: every backup file carries the active profile ID so a
 * backup taken under one profile stays recognizable when it is imported later, and the
 * retention classifier keeps matching the legacy (unprofiled) file names.
 */
class AutoBackupFileNameTest {

    private val date = "2026-09-29_120000"

    // ──────────────────────────────────────────────────────────────────────
    // File name builder
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `database backup name carries the active profile`() {
        assertEquals(
            "N-Zik_work_${date}_AutoBackup.sqlite",
            autoBackupFileName("N-Zik", "work", date, AutoBackupKind.DATABASE)
        )
    }

    @Test
    fun `base profile backup name carries the default id`() {
        assertEquals(
            "N-Zik_default_${date}_AutoBackup.sqlite",
            autoBackupFileName("N-Zik", "default", date, AutoBackupKind.DATABASE)
        )
    }

    @Test
    fun `settings backup name carries the active profile`() {
        assertEquals(
            "N-Zik_work_${date}_Settings_AutoBackup.csv",
            autoBackupFileName("N-Zik", "work", date, AutoBackupKind.SETTINGS)
        )
    }

    @Test
    fun `profile state backup name carries the active profile`() {
        assertEquals(
            "N-Zik_work_${date}_Profiles_AutoBackup.txt",
            autoBackupFileName("N-Zik", "work", date, AutoBackupKind.PROFILE_STATE)
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Retention classifier
    // ──────────────────────────────────────────────────────────────────────

    @Test
    fun `legacy database backup name is still recognized`() {
        assertEquals(AutoBackupKind.DATABASE, autoBackupKindOf("N-Zik_${date}_AutoBackup.sqlite"))
    }

    @Test
    fun `legacy settings backup name is still recognized`() {
        assertEquals(AutoBackupKind.SETTINGS, autoBackupKindOf("N-Zik_${date}_Settings_AutoBackup.csv"))
    }

    @Test
    fun `profiled database backup name is recognized`() {
        assertEquals(AutoBackupKind.DATABASE, autoBackupKindOf("N-Zik_work_${date}_AutoBackup.sqlite"))
    }

    @Test
    fun `profile state backup name is recognized`() {
        assertEquals(AutoBackupKind.PROFILE_STATE, autoBackupKindOf("N-Zik_work_${date}_Profiles_AutoBackup.txt"))
    }

    @Test
    fun `a legacy face auto backup name is recognized as the profile state`() {
        assertEquals(AutoBackupKind.PROFILE_STATE, autoBackupKindOf("N-Zik_work_${date}_Face_AutoBackup.jpg"))
    }

    @Test
    fun `non backup names are not recognized`() {
        assertNull(autoBackupKindOf("notes.txt"))
        assertNull(autoBackupKindOf("N-Zik_${date}_AutoBackup_notes.txt"))
        assertNull(autoBackupKindOf("data.db"))
        assertNull(autoBackupKindOf(""))
    }
}
