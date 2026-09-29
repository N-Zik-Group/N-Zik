package app.n_zik.android.core.backup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests [backupPartsOf] (spec-profiles-page-face, import/export adaptation): Database and
 * Settings stay separate targets, Both keeps its database + settings scope, Accounts archives
 * only the profile state (the list + every face), and All combines the three. An unknown
 * target falls back to the stored default (database).
 */
class BackupTargetTest {

    @Test
    fun `database only targets the database`() {
        assertEquals(BackupParts(true, false, false), backupPartsOf(BackupManager.TARGET_DATABASE))
    }

    @Test
    fun `settings only targets the settings`() {
        assertEquals(BackupParts(false, true, false), backupPartsOf(BackupManager.TARGET_SETTINGS))
    }

    @Test
    fun `both keeps its database and settings scope`() {
        assertEquals(BackupParts(true, true, false), backupPartsOf(BackupManager.TARGET_BOTH))
    }

    @Test
    fun `accounts targets only the profile state`() {
        assertEquals(BackupParts(false, false, true), backupPartsOf(BackupManager.TARGET_ACCOUNTS))
    }

    @Test
    fun `all targets the database the settings and the profile state`() {
        assertEquals(BackupParts(true, true, true), backupPartsOf(BackupManager.TARGET_ALL))
    }

    @Test
    fun `an unknown target falls back to the stored default`() {
        assertEquals(BackupParts(true, false, false), backupPartsOf(-1))
        assertEquals(BackupParts(true, false, false), backupPartsOf(99))
    }
}
