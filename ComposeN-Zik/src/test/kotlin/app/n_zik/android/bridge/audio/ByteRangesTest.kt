package app.n_zik.android.bridge.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ByteRangesTest {

    @Test
    fun `no Range header is the whole file`() {
        assertEquals(ByteRange.Full, ByteRanges.resolve(null, 1_000))
    }

    @Test
    fun `closed range a-b is served as is and clamped to the last byte`() {
        assertEquals(ByteRange.Partial(0, 99), ByteRanges.resolve("bytes=0-99", 1_000))
        assertEquals(ByteRange.Partial(500, 999), ByteRanges.resolve("bytes=500-5000", 1_000))
        assertEquals(ByteRange.Partial(999, 999), ByteRanges.resolve("bytes=999-999", 1_000))
    }

    @Test
    fun `open range a- runs to the end of the file`() {
        assertEquals(ByteRange.Partial(400, 999), ByteRanges.resolve("bytes=400-", 1_000))
    }

    @Test
    fun `suffix range -n is the last n bytes, the whole file when longer`() {
        assertEquals(ByteRange.Partial(900, 999), ByteRanges.resolve("bytes=-100", 1_000))
        assertEquals(ByteRange.Partial(0, 999), ByteRanges.resolve("bytes=-5000", 1_000))
    }

    @Test
    fun `range outside the file or malformed is unsatisfiable`() {
        listOf("bytes=1000-", "bytes=1000-2000", "bytes=50-10", "bytes=-0", "bytes=-", "bytes=a-b", "bytes=0-1,5-9", "garbage")
            .forEach { assertEquals(ByteRange.Unsatisfiable, ByteRanges.resolve(it, 1_000), "range=$it") }
        assertEquals(ByteRange.Unsatisfiable, ByteRanges.resolve("bytes=0-", 0))
    }

    @Test
    fun `content ranges of a 206 and a 416`() {
        assertEquals("bytes 10-19/1000", ByteRanges.contentRange(ByteRange.Partial(10, 19), 1_000))
        assertEquals("bytes */1000", ByteRanges.unsatisfiedRange(1_000))
        assertEquals(10L, ByteRange.Partial(10, 19).length)
    }
}
