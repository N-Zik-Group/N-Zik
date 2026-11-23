package app.n_zik.android.components.player.lyrics

import kotlin.math.roundToLong

import androidx.compose.runtime.Immutable

/**
 * A single word with its start and end time in milliseconds.
 */
@Immutable
data class KaraokeWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val charStartIndex: Int = 0,
    val hasExplicitEnd: Boolean = true
)

/** A single karaoke line: the text, its timestamp, word-level timings, and agent/background info for positioning. */
@Immutable
data class KaraokeLine(
    val timeMs: Long,
    val text: String,
    val words: List<KaraokeWord>,
    val agent: String? = null,     // "v1", "v2", "v1000", etc.
    val isBackground: Boolean = false,
    val endMs: Long? = null
)

/**
 * Parse the extended LRC format produced by TTMLParser.toLRC().
 *
 * Format: `[MM:SS.cc]{agent:v1|bg}Line text` followed by a word line
 * `<word1:startSec:endSec|word2:startSec:endSec>`.
 * Imported Lyricsfile can use millisecond line tags and escaped word text.
 */
fun parseKaraokeLrc(text: String, preserveText: Boolean = false): List<KaraokeLine> {
    val lines = if (preserveText) text.lines() else text.trim().lines()
    val result = mutableListOf<KaraokeLine>()

    val agentRegex = Regex("\\{agent:([^}]+)\\}")
    val bgRegex = Regex("\\{bg\\}")

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.startsWith("[")) {
            // Parse [MM:SS.cc]text
            val closeBracket = line.indexOf(']')
            if (closeBracket < 0) { i++; continue }
            val timeStr = line.substring(1, closeBracket)
            var lineText = line.substring(closeBracket + 1)
            val importedLine = parseLyricsFileLineText(lineText)

            // Parse agent tag
            val agentMatch = if (importedLine == null) agentRegex.find(lineText) else null
            val agent = agentMatch?.groupValues?.get(1)
            if (agentMatch != null) {
                lineText = lineText.replaceFirst(agentRegex, "")
            }

            // Parse background tag
            val isBg = importedLine == null && bgRegex.containsMatchIn(lineText)
            if (isBg) {
                lineText = lineText.replaceFirst(bgRegex, "")
            }

            val untaggedText = importedLine?.text ?: lineText
            val cleanText = if (preserveText) untaggedText else untaggedText.trim()
            val timeMs = parseLrcTime(timeStr)
            if (timeMs == null) {
                i++
                continue
            }

            // Check if next lines are word-timing lines (can be multiline)
            var words = emptyList<KaraokeWord>()
            if (i + 1 < lines.size && lines[i + 1].trim().startsWith("<")) {
                val wordsRawBuilder = StringBuilder()
                i++ // Move to the first word timing line
                while (i < lines.size) {
                    val lineTrim = lines[i].trim()
                    wordsRawBuilder.append(lineTrim)
                    if (lineTrim.endsWith(">")) {
                        break
                    }
                    i++
                }
                words = parseWordTimings(wordsRawBuilder.toString())
                var searchIndex = 0
                words = words.map { w ->
                    val idx = cleanText.indexOf(w.text, searchIndex)
                    if (idx >= 0) {
                        searchIndex = idx + w.text.length
                        w.copy(charStartIndex = idx)
                    } else {
                        w
                    }
                }
            }

            result.add(KaraokeLine(timeMs, cleanText, words, agent, isBg, importedLine?.endMs))
        }
        i++
    }
    return result
}

private val karaokeTimestamp = Regex("""(\d{1,9}):([0-5]\d)(?:[.:](\d{1,3}))?""")

private fun parseLrcTime(timeStr: String): Long? {
    val match = karaokeTimestamp.matchEntire(timeStr) ?: return null
    val (minutes, seconds, fraction) = match.destructured
    return minutes.toLong() * 60_000 + seconds.toLong() * 1000 + fraction.padEnd(3, '0').toLong()
}

private fun parseWordTimings(line: String): List<KaraokeWord> {
    // Format: <word1:startSec:endSec|word2:startSec:endSec>
    val imported = line.startsWith(LYRICSFILE_WORD_TIMINGS_PREFIX)
    val content = line.removePrefix(if (imported) LYRICSFILE_WORD_TIMINGS_PREFIX else "<").removeSuffix(">")
    if (content.isBlank()) return emptyList()

    return content.split("|").mapNotNull { part ->
        // Handle words that may contain colons (unlikely but defensive)
        // Format: text:startTime:endTime
        val lastColon = part.lastIndexOf(':')
        if (lastColon < 0) return@mapNotNull null
        val secondLastColon = part.lastIndexOf(':', lastColon - 1)
        if (secondLastColon < 0) return@mapNotNull null

        val encodedWordText = part.substring(0, secondLastColon)
        val wordText = if (imported) unescapeLyricsFileWord(encodedWordText) else encodedWordText
        val endField = part.substring(lastColon + 1)
        val inferredEnd = imported && endField.endsWith('~')
        val startSec = part.substring(secondLastColon + 1, lastColon).toDoubleOrNull()
        val endSec = (if (inferredEnd) endField.dropLast(1) else endField).toDoubleOrNull()
        if (startSec == null || endSec == null || !startSec.isFinite() || !endSec.isFinite()) return@mapNotNull null
        KaraokeWord(
            text = wordText,
            startMs = (startSec * 1000).roundToLong(),
            endMs = (endSec * 1000).roundToLong(),
            hasExplicitEnd = !inferredEnd
        )
    }
}

/** Finds the initial and per-line gaps, keeping one interval indicator during overlapping duet gaps. */
fun computeKaraokeGapWindows(lines: List<KaraokeLine>): Pair<Pair<Long, Long>?, Map<Int, Pair<Long, Long>>> {
    val firstStart = lines.firstOrNull { !it.isBackground }?.timeMs ?: 0L
    val initialGapWindow = if (firstStart > 3000L) Pair(0L, firstStart - 650L) else null

    val gapWindows = buildMap {
        lines.forEachIndexed { index, line ->
            if (line.isBackground) return@forEachIndexed
            val startMs = line.timeMs
            val nextStartMs = if (index < lines.size - 1) lines.drop(index + 1).firstOrNull { !it.isBackground }?.timeMs ?: (startMs + 10000L) else startMs + 10000L
            val lineEndMs = if (line.words.isNotEmpty()) line.words.maxOf { it.endMs } else line.timeMs + 2000L

            val gap = nextStartMs - lineEndMs
            if (gap > 2500L) {
                put(index, Pair(lineEndMs, nextStartMs - 650L))
            }
        }
    }.toMutableMap().apply {
        val keysToRemove = mutableSetOf<Int>()
        for ((idx1, window1) in this) {
            for ((idx2, window2) in this) {
                if (idx1 < idx2) {
                    if (window1.first < window2.second && window1.second > window2.first) {
                        keysToRemove.add(idx1)
                    }
                }
            }
        }
        keysToRemove.forEach { remove(it) }
    }

    return initialGapWindow to gapWindows
}
