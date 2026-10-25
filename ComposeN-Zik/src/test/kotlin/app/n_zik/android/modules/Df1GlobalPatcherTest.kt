package app.n_zik.android.modules

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * JVM tests for [Df1GlobalPatcher] - synthetic little-endian ELF byte arrays
 * covering the exact patch applied to the module's `libc++_shared.so` at load.
 */
class Df1GlobalPatcherTest {

    // Zero-filled filler (cannot contain the tag pattern) with one DT_FLAGS_1
    // entry at a fixed offset: Elf64 = 8-byte tag + 8-byte value at 32..43.
    private fun elf64(flagsValue: Int): ByteArray {
        val data = ByteArray(64)
        data[0] = 0x7F
        data[1] = 'E'.code.toByte()
        data[2] = 'L'.code.toByte()
        data[3] = 'F'.code.toByte()
        data[4] = 2 // ELFCLASS64
        data[5] = 1 // little endian
        data[32] = 0xFB.toByte() // DT_FLAGS_1 tag, LE 0x6ffffffb (8 bytes, 32..39)
        data[33] = 0xFF.toByte()
        data[34] = 0xFF.toByte()
        data[35] = 0x6F
        // data[36..39] = 0x00000000 (upper bytes of the tag) - zero by default
        data[40] = (flagsValue and 0xFF).toByte() // 8-byte LE value, 40..47
        data[41] = ((flagsValue shr 8) and 0xFF).toByte()
        data[42] = ((flagsValue shr 16) and 0xFF).toByte()
        data[43] = ((flagsValue shr 24) and 0xFF).toByte()
        return data
    }

    // Elf32 (armeabi-v7a) = 4-byte tag + 4-byte value at 24..31.
    private fun elf32(flagsValue: Int): ByteArray {
        val data = ByteArray(48)
        data[0] = 0x7F
        data[1] = 'E'.code.toByte()
        data[2] = 'L'.code.toByte()
        data[3] = 'F'.code.toByte()
        data[4] = 1 // ELFCLASS32
        data[5] = 1 // little endian
        data[24] = 0xFB.toByte() // DT_FLAGS_1 tag, LE 0x6ffb (rest is zero)
        data[25] = 0xFF.toByte()
        data[28] = (flagsValue and 0xFF).toByte()
        data[29] = ((flagsValue shr 8) and 0xFF).toByte()
        return data
    }

    @Test
    fun `flagsValue should read the NOW flag of an unpatched Elf64 file`() {
        assertEquals(1, Df1GlobalPatcher.flagsValue(elf64(1)))
        assertFalse(Df1GlobalPatcher.isPatched(elf64(1)))
    }

    @Test
    fun `patch should set DF_1_GLOBAL on an unpatched Elf64 file`() {
        val patched = Df1GlobalPatcher.patch(elf64(1)) // NOW
        assertEquals(3, Df1GlobalPatcher.flagsValue(patched)) // NOW | GLOBAL
        assertTrue(Df1GlobalPatcher.isPatched(patched))
    }

    @Test
    fun `patch should be a no-op when DF_1_GLOBAL is already set`() {
        val already = elf64(3)
        assertArrayEquals(already, Df1GlobalPatcher.patch(already))
    }

    @Test
    fun `patch should handle Elf32 files (armeabi-v7a)`() {
        val patched = Df1GlobalPatcher.patch(elf32(1))
        assertEquals(3, Df1GlobalPatcher.flagsValue(patched))
        assertTrue(Df1GlobalPatcher.isPatched(patched))
    }

    @Test
    fun `patch should throw on an unexpected ELF class`() {
        val data = ByteArray(64)
        data[4] = 0
        assertThrows(IllegalArgumentException::class.java) { Df1GlobalPatcher.patch(data) }
    }

    @Test
    fun `patch should throw when the DT_FLAGS_1 tag is absent`() {
        val data = ByteArray(64)
        data[0] = 0x7F
        data[1] = 'E'.code.toByte()
        data[2] = 'L'.code.toByte()
        data[3] = 'F'.code.toByte()
        data[4] = 2
        data[5] = 1
        assertThrows(IllegalArgumentException::class.java) { Df1GlobalPatcher.patch(data) }
    }

    @Test
    fun `patch should reject a coincidental tag match with an implausible value`() {
        val data = elf64(0)
        data[42] = 0x01 // value becomes 0x10000 - not a plausible DT_FLAGS_1
        assertThrows(IllegalArgumentException::class.java) { Df1GlobalPatcher.patch(data) }
    }
}
