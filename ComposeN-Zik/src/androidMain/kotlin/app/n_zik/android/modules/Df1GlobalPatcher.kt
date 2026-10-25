package app.n_zik.android.modules

/**
 * ELF patcher for the module's `libc++_shared.so` (spike-verified, round 3d).
 *
 * The module's `libavcodec.so` references `__gxx_personality_v0` without a
 * DT_NEEDED entry on the C++ runtime, so a plain load leaves it unresolved.
 * Setting DF_1_GLOBAL (DT_FLAGS_1 |= 0x2) puts the runtime's symbols in the
 * namespace global group, which bionic consults at link time. Pure byte-level
 * patch (little-endian scan for the tag) - unit-testable on the JVM.
 */
object Df1GlobalPatcher {

    const val DF_1_GLOBAL = 0x2

    /** DT_FLAGS_1 tag, little-endian: Elf64 0x6ffffffb, Elf32 0x6ffb. */
    private val TAG64 = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x6F, 0, 0, 0, 0)
    private val TAG32 = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0, 0)

    /** True when [data]'s DT_FLAGS_1 already carries [DF_1_GLOBAL]. */
    fun isPatched(data: ByteArray): Boolean {
        val flags = flagsValue(data) ?: return false
        return flags and DF_1_GLOBAL != 0
    }

    /** Current DT_FLAGS_1 value of [data], or null when the tag is absent. */
    fun flagsValue(data: ByteArray): Int? {
        val tag = tagFor(data) ?: return null
        val index = findTag(data, tag) ?: return null
        return leValue(data, index + tag.size, tag.size)
    }

    /**
     * Returns [data] with [DF_1_GLOBAL] set in its DT_FLAGS_1 entry (a copy -
     * the input is never mutated). Idempotent: an already-patched file comes
     * back unchanged. [IllegalArgumentException] when the ELF class is
     * unexpected or the tag is absent.
     */
    fun patch(data: ByteArray): ByteArray {
        val tag = tagFor(data) ?: throw IllegalArgumentException("not an ELF file")
        val index = findTag(data, tag) ?: throw IllegalArgumentException("DT_FLAGS_1 tag not found")
        val offset = index + tag.size
        val value = leValue(data, offset, tag.size)
        if (value and DF_1_GLOBAL != 0) return data
        val out = data.copyOf()
        out[offset] = (out[offset].toInt() or DF_1_GLOBAL).toByte()
        return out
    }

    private fun tagFor(data: ByteArray): ByteArray? {
        if (data.size < 5) return null
        return when (data[4].toInt() and 0xFF) {
            2 -> TAG64
            1 -> TAG32
            else -> null
        }
    }

    // The value field follows the tag; a plausible DT_FLAGS_1 is a small
    // unsigned value - the filter rejects coincidental tag matches.
    private fun findTag(data: ByteArray, tag: ByteArray): Int? {
        var i = 0
        val limit = data.size - 2 * tag.size
        while (i <= limit) {
            if (matches(data, i, tag) && leValue(data, i + tag.size, tag.size) in 0..0xFFFF) {
                return i
            }
            i++
        }
        return null
    }

    private fun matches(data: ByteArray, at: Int, pattern: ByteArray): Boolean {
        for (k in pattern.indices) {
            if (data[at + k] != pattern[k]) return false
        }
        return true
    }

    /** Little-endian unsigned value at [offset], [size] bytes (4 or 8). */
    private fun leValue(data: ByteArray, offset: Int, size: Int): Int {
        var value = 0
        for (k in 0 until size) {
            value = value or ((data[offset + k].toInt() and 0xFF) shl (8 * k))
        }
        return value
    }
}
