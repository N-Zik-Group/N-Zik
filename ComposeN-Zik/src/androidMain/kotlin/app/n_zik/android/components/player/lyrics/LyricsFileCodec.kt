package app.n_zik.android.components.player.lyrics

import app.n_zik.android.enums.lyrics.LyricsType
import app.n_zik.android.models.Lyrics
import org.snakeyaml.engine.v2.api.Dump
import org.snakeyaml.engine.v2.api.DumpSettings
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.api.lowlevel.Parse
import org.snakeyaml.engine.v2.common.NonPrintableStyle
import org.snakeyaml.engine.v2.events.CollectionStartEvent
import org.snakeyaml.engine.v2.events.Event
import org.snakeyaml.engine.v2.events.ScalarEvent
import org.snakeyaml.engine.v2.exceptions.YamlEngineException
import org.snakeyaml.engine.v2.nodes.Tag
import org.snakeyaml.engine.v2.schema.CoreSchema

/** Lyricsfile decoded into the stored format consumed by the corresponding lyrics view. */
internal data class ImportedLyrics(val type: LyricsType, val text: String)

internal const val LYRICSFILE_WORD_TIMINGS_PREFIX = "<lyricsfile|"
private const val LYRICSFILE_LINE_PREFIX = "{lyricsfile-line:"

internal data class LyricsFileLineText(val text: String, val endMs: Long?)

internal fun parseLyricsFileLineText(text: String): LyricsFileLineText? {
    if (!text.startsWith(LYRICSFILE_LINE_PREFIX)) return null
    val endOfTag = text.indexOf('}', startIndex = LYRICSFILE_LINE_PREFIX.length)
    if (endOfTag == -1) return null
    val timing = text.substring(LYRICSFILE_LINE_PREFIX.length, endOfTag)
    val endMs = if (timing.isEmpty()) null else timing.toLongOrNull() ?: return null
    return LyricsFileLineText(text.substring(endOfTag + 1), endMs)
}

internal fun unescapeLyricsFileWord(text: String): String =
    text.replace("%7C", "|").replace("%25", "%")

/**
 * Converts the Lyricsfile 1.0 draft to and from the existing N-Zik lyrics rows.
 * Each row holds one lyrics variant, so alternate plain text is not retained alongside timed lyrics.
 */
internal object LyricsFileCodec {
    const val MAX_FILE_BYTES = 1024 * 1024
    private const val MAX_YAML_DEPTH = 12
    private const val MAX_YAML_EVENTS = 60_000

    // The internal line-timestamp parser accepts up to nine digits for the minute field.
    private const val MAX_TIMESTAMP_MS = 59_999_999_999_999L
    private const val VERSION = "1.0"

    private val loadSettings = LoadSettings.builder()
        .setSchema(CoreSchema())
        .setAllowDuplicateKeys(false)
        .setAllowRecursiveKeys(false)
        .setAllowNonScalarKeys(false)
        .setMaxAliasesForCollections(0)
        .setCodePointLimit(MAX_FILE_BYTES)
        .setUseMarks(false)
        .build()

    private val dumpSettings = DumpSettings.builder()
        .setSchema(CoreSchema())
        .setNonPrintableStyle(NonPrintableStyle.ESCAPE)
        .setSplitLines(false)
        .build()

    private val standardTags = setOf(
        Tag.MAP, Tag.SEQ, Tag.STR, Tag.INT, Tag.BOOL, Tag.NULL
    ).mapTo(mutableSetOf()) { it.value }

    private data class TimedWord(val text: String, val startMs: Long, val endMs: Long?)
    private data class TimedLine(
        val text: String,
        val startMs: Long,
        val endMs: Long? = null,
        val words: List<TimedWord> = emptyList()
    )

    fun decode(yaml: String): ImportedLyrics {
        requireFits(yaml)
        val document = try {
            validateYamlStructure(yaml)
            Load(loadSettings).loadFromString(yaml)
        } catch (error: YamlEngineException) {
            throw IllegalArgumentException("Invalid Lyricsfile YAML", error)
        }

        validateYamlValues(document)
        val root = document as? Map<*, *> ?: throw IllegalArgumentException("Expected a Lyricsfile mapping")
        require(root["version"] == VERSION) { "Unsupported Lyricsfile version" }

        val metadata = root["metadata"] as? Map<*, *> ?: throw IllegalArgumentException("Expected Lyricsfile metadata")
        for (key in listOf("title", "artist")) metadata.string(key)
        for (key in listOf("album", "language")) {
            if (key in metadata) metadata.string(key)
        }
        if ("duration_ms" in metadata) metadata.time("duration_ms")
        if ("offset_ms" in metadata) metadata.integer("offset_ms")
        val instrumental = if ("instrumental" in metadata) metadata["instrumental"] else false
        require(instrumental is Boolean) { "Expected an instrumental flag" }

        val plain = if ("plain" in root) root.string("plain") else null
        val lines = root.list("lines").map(::readLine).sortedBy(TimedLine::startMs)
        require(!instrumental || (plain.isNullOrEmpty() && lines.isEmpty())) {
            "Instrumental Lyricsfile contains lyrics"
        }

        return if (lines.any { it.text.isNotBlank() || it.words.isNotEmpty() }) {
            val type = if (lines.any { it.words.isNotEmpty() }) LyricsType.Karaoke else LyricsType.Synced
            ImportedLyrics(type, renderTimedLyrics(lines))
        } else {
            require(!plain.isNullOrBlank()) { "Lyricsfile has no usable lyrics" }
            ImportedLyrics(LyricsType.Unsynced, plain)
        }
    }

    fun encode(lyrics: Lyrics, title: String?, artist: String?): String {
        val text = lyrics.data.orEmpty()
        requireFits(text)
        require(text.isNotBlank()) { "No lyrics to export" }

        val root = linkedMapOf<String, Any>(
            "version" to VERSION,
            "metadata" to linkedMapOf("title" to title.orEmpty(), "artist" to artist.orEmpty())
        )
        when (lyrics.type) {
            LyricsType.Unsynced.name -> root["plain"] = text
            LyricsType.Synced.name -> root["lines"] = writeLines(
                parseSyncedSentences(text, preserveText = true).drop(1).map { TimedLine(it.text, it.timeMs, it.endMs) }
            )
            LyricsType.Karaoke.name -> root["lines"] = writeLines(
                parseKaraokeLrc(text, preserveText = true).map { line ->
                    val words = wordsForExport(line)
                    val wordsText = words.joinToString("") { it.text }
                    val lineText = if (wordsText.trim() == line.text) wordsText else line.text
                    TimedLine(lineText, line.timeMs, line.endMs, words)
                }
            )
            else -> throw IllegalArgumentException("Unsupported stored lyrics type")
        }
        return Dump(dumpSettings).dumpToString(root).also(::requireFits)
    }

    private fun validateYamlStructure(yaml: String) {
        var depth = 0
        var eventCount = 0
        for (event in Parse(loadSettings).parseString(yaml)) {
            require(++eventCount <= MAX_YAML_EVENTS) { "Lyricsfile has too many YAML elements" }
            when (event.eventId) {
                Event.ID.MappingStart, Event.ID.SequenceStart -> {
                    require(++depth <= MAX_YAML_DEPTH) { "Lyricsfile nesting is too deep" }
                }
                Event.ID.MappingEnd, Event.ID.SequenceEnd -> depth--
                Event.ID.Alias -> throw IllegalArgumentException("Lyricsfile aliases are unsupported")
                else -> Unit
            }
            val tag = when (event) {
                is ScalarEvent -> event.tag.orElse(null)
                is CollectionStartEvent -> event.tag.orElse(null)
                else -> null
            }
            require(tag == null || tag in standardTags) { "Unsupported Lyricsfile YAML tag" }
        }
    }

    private fun validateYamlValues(value: Any?) {
        when (value) {
            is Map<*, *> -> for ((key, child) in value) {
                require(key is String) { "Expected a string Lyricsfile field name" }
                validateYamlValues(child)
            }
            is List<*> -> value.forEach(::validateYamlValues)
            is String, is Int, is Long, is Boolean, null -> Unit
            else -> throw IllegalArgumentException("Unsupported Lyricsfile YAML value")
        }
    }

    private fun readLine(value: Any?): TimedLine {
        val line = value as? Map<*, *> ?: throw IllegalArgumentException("Expected a Lyricsfile line")
        val text = line.string("text")
        require(text.isSingleLine()) { "Expected single-line timed lyrics" }
        val start = line.time("start_ms")
        val end = line.optionalEnd(start)
        val words = line.list("words").map { item ->
            val word = item as? Map<*, *> ?: throw IllegalArgumentException("Expected a Lyricsfile word")
            val wordText = word.string("text")
            require(wordText.isSingleLine()) { "Expected a single-line word" }
            val wordStart = word.time("start_ms")
            TimedWord(wordText, wordStart, word.optionalEnd(wordStart))
        }
        return TimedLine(text, start, end, words)
    }

    private fun Map<*, *>.string(key: String): String =
        this[key] as? String ?: throw IllegalArgumentException("Expected a string for $key")

    private fun Map<*, *>.list(key: String): List<*> {
        if (key !in this) return emptyList<Any>()
        return this[key] as? List<*> ?: throw IllegalArgumentException("Expected a list for $key")
    }

    private fun Map<*, *>.integer(key: String): Long =
        when (val time = this[key]) {
            is Int -> time.toLong()
            is Long -> time
            else -> throw IllegalArgumentException("Expected integer milliseconds for $key")
        }

    private fun Map<*, *>.time(key: String): Long {
        val value = integer(key)
        require(value in 0..MAX_TIMESTAMP_MS) { "Invalid timing for $key" }
        return value
    }

    private fun Map<*, *>.optionalEnd(start: Long): Long? =
        if ("end_ms" in this) {
            time("end_ms").also { require(it >= start) { "Lyrics end precedes its start" } }
        } else {
            null
        }

    private fun renderTimedLyrics(lines: List<TimedLine>): String = buildString {
        for (line in lines) {
            val minutes = (line.startMs / 60_000).toString().padStart(2, '0')
            val seconds = ((line.startMs / 1000) % 60).toString().padStart(2, '0')
            val fraction = (line.startMs % 1000).toString().padStart(3, '0')
            append('[').append(minutes).append(':').append(seconds).append('.').append(fraction).append(']')
            if (line.endMs != null || "{bg}" in line.text || "{agent:" in line.text ||
                line.text.startsWith(LYRICSFILE_LINE_PREFIX)
            ) {
                append(LYRICSFILE_LINE_PREFIX).append(line.endMs?.toString().orEmpty()).append('}')
            }
            appendLine(line.text)
            if (line.words.isNotEmpty()) {
                append(LYRICSFILE_WORD_TIMINGS_PREFIX)
                for ((index, word) in line.words.withIndex()) {
                    if (index != 0) append('|')
                    val inferredEnd = line.words.getOrNull(index + 1)?.startMs?.takeIf { it >= word.startMs }
                        ?: line.endMs?.takeIf { it >= word.startMs }
                        ?: word.startMs
                    append(word.text.replace("%", "%25").replace("|", "%7C"))
                    append(':').append(secondsString(word.startMs))
                    append(':').append(secondsString(word.endMs ?: inferredEnd))
                    if (word.endMs == null) append('~')
                }
                appendLine('>')
            }
        }
    }

    private fun secondsString(timeMs: Long): String =
        "${timeMs / 1000}.${(timeMs % 1000).toString().padStart(3, '0')}"

    private fun wordsForExport(line: KaraokeLine): List<TimedWord> {
        if (line.words.isEmpty()) return emptyList()
        val words = line.words.map { word ->
            TimedWord(word.text, word.startMs, word.endMs.takeIf { word.hasExplicitEnd })
        }
        if (words.joinToString("") { it.text }.trim() == line.text) return words

        // BetterLyrics stores token text separately from the spaces between tokens.
        // Recover those spaces from the complete line when the tokens can be aligned.
        var nextOffset = 0
        val offsets = words.map { word ->
            val index = line.text.indexOf(word.text, startIndex = nextOffset)
            if (index == -1) return words
            nextOffset = index + word.text.length
            index
        }
        return words.mapIndexed { index, word ->
            val start = if (index == 0) 0 else offsets[index]
            val end = offsets.getOrElse(index + 1) { line.text.length }
            word.copy(text = line.text.substring(start, end))
        }
    }

    private fun writeLines(lines: List<TimedLine>): List<Map<String, Any>> {
        require(lines.any { it.text.isNotBlank() || it.words.isNotEmpty() }) { "No timed lyrics to export" }
        return lines.sortedBy(TimedLine::startMs).map { line ->
            linkedMapOf<String, Any>("text" to line.text, "start_ms" to line.startMs).apply {
                line.endMs?.let {
                    require(it in line.startMs..MAX_TIMESTAMP_MS) { "Invalid stored line timing" }
                    put("end_ms", it)
                }
                if (line.words.isNotEmpty()) {
                    put("words", line.words.map { word ->
                        require(word.startMs in 0..MAX_TIMESTAMP_MS &&
                            (word.endMs == null || word.endMs in word.startMs..MAX_TIMESTAMP_MS)
                        ) { "Invalid stored word timing" }
                        linkedMapOf<String, Any>("text" to word.text, "start_ms" to word.startMs).apply {
                            word.endMs?.let { put("end_ms", it) }
                        }
                    })
                }
            }
        }
    }

    private fun String.isSingleLine(): Boolean = '\r' !in this && '\n' !in this

    private fun requireFits(text: String) {
        require(text.length <= MAX_FILE_BYTES && text.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
            "Lyricsfile exceeds the size limit"
        }
    }
}
