package it.fast4x.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Run(
    val text: String,
    val navigationEndpoint: NavigationEndpoint?,
)

@Serializable
data class Runs(
    val runs: List<Run> = listOf()
) {
    val text: String
        get() = runs.joinToString("") { it.text ?: "" }

    fun splitBySeparator(): List<List<Run>> {
        return runs.flatMapIndexed { index, run ->
            when {
                index == 0 || index == runs.lastIndex -> listOf(index)
                run.text?.trim() == "•" -> listOf(index - 1, index + 1)
                else -> emptyList()
            }
        }.windowed(size = 2, step = 2) { (from, to) -> runs.slice(from..to) }.let {
            it.ifEmpty {
                listOf(runs)
            }
        }
    }

    @Serializable
    data class Run(
        val text: String?,
        val navigationEndpoint: NavigationEndpoint?,
    )
}

fun List<Runs.Run>.splitBySeparator(): List<List<Runs.Run>> {
    val res = mutableListOf<List<Runs.Run>>()
    var tmp = mutableListOf<Runs.Run>()
    forEach { run ->
        if (run.text?.trim() == "•") {
            res.add(tmp)
            tmp = mutableListOf()
        } else {
            tmp.add(run)
        }
    }
    res.add(tmp)
    return res
}

fun <T> List<T>.oddElements() = filterIndexed { index, _ ->
    index % 2 == 0
}

object ArtistConjunctions {
    var conjunctions: List<String> = listOf("and")
}

fun List<Runs.Run>.splitArtistsByConjunction(): List<Runs.Run> {
    val result = mutableListOf<Runs.Run>()
    val words = ArtistConjunctions.conjunctions
    val conjunctionPattern = Regex(
        if (words.isNotEmpty()) " (${words.joinToString("|") { Regex.escape(it) }}) | & "
        else " & ",
        RegexOption.IGNORE_CASE
    )
    forEach { run ->
        val text = run.text
        if (text != null && text.contains(conjunctionPattern)) {
            val parts = text.split(conjunctionPattern)
            parts.forEachIndexed { index, part ->
                if (part.isNotBlank()) {
                    result.add(Runs.Run(part.trim(), if (index == 0) run.navigationEndpoint else null))
                }
            }
        } else if (text != null && (
            text.trim().equals("&", ignoreCase = true) ||
            text.trim().equals("•") ||
            words.any { text.trim().equals(it, ignoreCase = true) }
        )) {
            // Skip standalone "&" or standalone conjunction words
        } else {
            result.add(run)
        }
    }
    return result
}

fun List<List<Runs.Run>>.clean(): List<List<Runs.Run>> {
    val firstGroup = getOrNull(0) ?: return this
    val hasArtistSignals = firstGroup.any { it.navigationEndpoint != null } ||
        firstGroup.any { it.text?.contains(" & ") == true } ||
        ArtistConjunctions.conjunctions.any { conj ->
            firstGroup.any { it.text?.trim()?.equals(conj, ignoreCase = true) == true }
        }
    return if (hasArtistSignals) this else drop(1)
}