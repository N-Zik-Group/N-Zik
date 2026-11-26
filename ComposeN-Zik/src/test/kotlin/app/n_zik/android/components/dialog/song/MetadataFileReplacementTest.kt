package app.n_zik.android.components.dialog.song

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class MetadataFileReplacementTest {
    @TempDir lateinit var directory: File

    @Test
    fun `a failed replacement leaves the source intact and cleans the staged file`() {
        val source = File(directory, "track.mp3").apply { writeText("original audio") }
        val encoded = File(directory, "encoded.mp3").apply { writeText("updated audio") }

        assertThrows(IOException::class.java) {
            replaceMetadataFile(source, encoded, beforeReplace = {}) { staged, destination ->
                assertEquals(destination.parentFile, staged.parentFile)
                assertEquals("original audio", destination.readText())
                assertEquals("updated audio", staged.readText())
                throw IOException("rename failed")
            }
        }

        assertEquals("original audio", source.readText())
        assertEquals(setOf(source.name, encoded.name), directory.list().orEmpty().toSet())
    }

    @Test
    fun `replacement publishes the complete encoded file`() {
        val source = File(directory, "track.mp3").apply { writeText("longer original audio") }
        val encoded = File(directory, "encoded.mp3").apply { writeText("updated") }

        replaceMetadataFile(source, encoded, beforeReplace = {}) { staged, destination ->
            Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }

        assertEquals("updated", source.readText())
        assertEquals(setOf(source.name, encoded.name), directory.list().orEmpty().toSet())
    }

    @Test
    fun `media stream replacement truncates the previous contents and removes the recovery copy`() {
        val source = File(directory, "track.mp3").apply { writeText("longer original audio") }
        val encoded = File(directory, "encoded.mp3").apply { writeText("updated") }

        replaceMediaStream(source, encoded) { FileOutputStream(source, true) }

        assertEquals("updated", source.readText())
        assertEquals(setOf(source.name, encoded.name), directory.list().orEmpty().toSet())
    }

    @Test
    fun `media stream write failure restores the original bytes`() {
        val source = File(directory, "track.mp3").apply { writeText("original audio") }
        val encoded = File(directory, "encoded.mp3").apply { writeText("updated audio") }
        var opens = 0

        val failure = assertThrows(IOException::class.java) {
            replaceMediaStream(source, encoded) {
                if (opens++ == 0) failingStream(source) else FileOutputStream(source, true)
            }
        }

        assertEquals("partial write", failure.message)
        assertEquals(2, opens)
        assertEquals("original audio", source.readText())
        assertEquals(setOf(source.name, encoded.name), directory.list().orEmpty().toSet())
    }

    @Test
    fun `failed restoration leaves a durable copy of the original bytes`() {
        val source = File(directory, "track.mp3").apply { writeText("original audio") }
        val encoded = File(directory, "encoded.mp3").apply { writeText("updated audio") }
        var opens = 0

        val failure = assertThrows(MetadataRestoreException::class.java) {
            replaceMediaStream(source, encoded) {
                if (opens++ == 0) failingStream(source) else throw IOException("restore failed")
            }
        }

        assertEquals("partial write", failure.cause?.message)
        assertEquals("restore failed", failure.suppressed.single().message)
        assertTrue(failure.recoveryFile.name.startsWith("metadata-123-"))
        assertEquals("original audio", failure.recoveryFile.readText())
    }

    @Test
    fun `a denied writable stream leaves the original intact and propagates the permission exception`() {
        val source = File(directory, "track.mp3").apply { writeText("original audio") }
        val encoded = File(directory, "encoded.mp3").apply { writeText("updated audio") }

        assertThrows(SecurityException::class.java) {
            replaceMediaStream(source, encoded) { throw SecurityException("permission required") }
        }

        assertEquals("original audio", source.readText())
        assertEquals(setOf(source.name, encoded.name), directory.list().orEmpty().toSet())
    }

    private fun replaceMediaStream(source: File, encoded: File, openWritable: () -> FileOutputStream) {
        writeMetadataWithRecovery(
            encoded = encoded,
            backupDirectory = directory,
            backupPrefix = "metadata-123-",
            openOriginal = source::inputStream,
            openWritable = openWritable,
            syncBackupDirectory = {},
            beforeWrite = {},
        )
    }

    private fun failingStream(source: File): FileOutputStream = object : FileOutputStream(source, true) {
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            super.write(bytes, offset, minOf(3, length))
            throw IOException("partial write")
        }
    }
}
