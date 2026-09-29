package app.n_zik.android.components.ui.screens.profiles

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * Tests the profile-files purge run on profile deletion (spec-profiles-page-face, matrix
 * row DELETE): [deleteProfileAvatar] removes only the deleted profile's own files
 * directory (its custom face photo), leaves every other profile's files untouched, and
 * tolerates a profile that never had any files.
 */
class ProfileAvatarPurgeTest {

    private fun tempFiles(): File = Files.createTempDirectory("nzik-profile-purge").toFile()

    private fun contextWith(files: File): Context = mockk {
        every { filesDir } returns files
    }

    @Test
    fun purgeRemovesOnlyTheDeletedProfileFiles() {
        val files = tempFiles()
        val deletedAvatar = File(files, "profiles/work/avatar.jpg").apply {
            parentFile?.mkdirs()
            writeText("work photo")
        }
        val otherAvatar = File(files, "profiles/other/avatar.jpg").apply {
            parentFile?.mkdirs()
            writeText("other photo")
        }

        deleteProfileAvatar(contextWith(files), "work")

        assertFalse(deletedAvatar.exists(), "the deleted profile's custom photo must be purged")
        assertFalse(File(files, "profiles/work").exists(), "the deleted profile's files dir must be purged")
        assertTrue(otherAvatar.exists(), "another profile's files must stay untouched")
    }

    @Test
    fun purgeToleratesAProfileWithoutFiles() {
        val files = tempFiles()
        // A profile that never set a custom photo has no files dir — the purge is a no-op.
        deleteProfileAvatar(contextWith(files), "ghost")
        assertTrue(!File(files, "profiles").exists() || File(files, "profiles").listFiles().orEmpty().isEmpty())
    }
}
