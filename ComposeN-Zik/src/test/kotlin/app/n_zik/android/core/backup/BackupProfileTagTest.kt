package app.n_zik.android.core.backup

import app.n_zik.android.core.rescue.RescueFiles
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Tests [RescueFiles.backupProfileTagOf] (spec-profiles-page-face, import/export
 * adaptation): the cross-profile warning relies on the profile tag carried by a
 * profiled backup/export file name. The backup timestamp anchors the match from
 * the end, the app name anchors the prefix — anything else is untagged.
 */
class BackupProfileTagTest {

    private val app = "N-Zik"

    // --- tagged names: the tag is read back

    @Test
    fun `a database auto backup name carries its profile`() {
        assertEquals("work", RescueFiles.backupProfileTagOf("N-Zik_work_2026-09-29_120000_AutoBackup.sqlite", app))
    }

    @Test
    fun `a settings auto backup name carries its profile`() {
        assertEquals("work", RescueFiles.backupProfileTagOf("N-Zik_work_2026-09-29_120000_Settings_AutoBackup.csv", app))
    }

    @Test
    fun `a profile list auto backup name carries its profile`() {
        assertEquals("work", RescueFiles.backupProfileTagOf("N-Zik_work_2026-09-29_120000_Profiles_AutoBackup.txt", app))
    }

    @Test
    fun `a face auto backup name carries its profile`() {
        assertEquals("work", RescueFiles.backupProfileTagOf("N-Zik_work_2026-09-29_120000_Face_AutoBackup.jpg", app))
    }

    @Test
    fun `the default profile tag is read`() {
        assertEquals("default", RescueFiles.backupProfileTagOf("N-Zik_default_2026-09-29_120000_AutoBackup.sqlite", app))
    }

    @Test
    fun `a profile id containing an underscore is read whole`() {
        assertEquals("my_work", RescueFiles.backupProfileTagOf("N-Zik_my_work_2026-09-29_120000_Face_AutoBackup.jpg", app))
    }

    @Test
    fun `a manual profile list export name carries its profile`() {
        assertEquals("work", RescueFiles.backupProfileTagOf("N-Zik_work_2026-09-29_120000_Profiles_Export.txt", app))
    }

    @Test
    fun `a manual face export name carries its profile`() {
        assertEquals("work", RescueFiles.backupProfileTagOf("N-Zik_work_2026-09-29_120000_Face_Export.jpg", app))
    }

    // --- untagged names: null, never a wrong tag

    @Test
    fun `a legacy unprofiled database name has no tag`() {
        assertNull(RescueFiles.backupProfileTagOf("N-Zik_2026-09-29_120000_AutoBackup.sqlite", app))
    }

    @Test
    fun `a legacy unprofiled settings name has no tag`() {
        assertNull(RescueFiles.backupProfileTagOf("N-Zik_2026-09-29_120000_Settings_AutoBackup.csv", app))
    }

    @Test
    fun `a name from another app has no tag`() {
        assertNull(RescueFiles.backupProfileTagOf("Other_work_2026-09-29_120000_AutoBackup.sqlite", app))
    }

    @Test
    fun `an unrelated file name has no tag`() {
        assertNull(RescueFiles.backupProfileTagOf("random.txt", app))
        assertNull(RescueFiles.backupProfileTagOf("N-Zik 2026-09-29 Database.sqlite", app))
    }

    @Test
    fun `a wrong extension has no tag`() {
        assertNull(RescueFiles.backupProfileTagOf("N-Zik_work_2026-09-29_120000_AutoBackup.bin", app))
    }

    // --- untrusted tags: never propagated

    @Test
    fun `an unsafe tag is not trusted`() {
        assertNull(RescueFiles.backupProfileTagOf("N-Zik_a/b_2026-09-29_120000_AutoBackup.sqlite", app))
    }
}
