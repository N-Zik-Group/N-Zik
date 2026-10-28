package app.it.fast4x.rimusic

const val PINNED_PREFIX = "pinned:"

const val MODIFIED_PREFIX = "modified:"
const val MONTHLY_PREFIX = "monthly:"

const val EXPLICIT_PREFIX = "e:"
const val LOCAL_KEY_PREFIX = "local:"
const val YTP_PREFIX = "account:"


/**
 * Assumption: all prefixes end with ":" and have at least 1 (other) character.
 * Removes a "prefix of prefixes" including multiple times the same prefix (at different locations).
 */
fun cleanPrefix(text: String): String {
    val splitText = text.split(":")
    var i = 0
    while (i < splitText.size-1) {
        if ("${splitText[i]}:" !in listOf(PINNED_PREFIX, MODIFIED_PREFIX, MONTHLY_PREFIX,
                EXPLICIT_PREFIX, LOCAL_KEY_PREFIX, YTP_PREFIX)) {
            break
        }
        i++
    }
    if(i >= splitText.size) return ""
    return splitText.subList(i, splitText.size).joinToString(":")
        .removePrefix("\uD83C\uDD74 ")
}

fun String.hasExplicitPrefix(): Boolean {
    val splitText = this.split(":")
    var i = 0
    while (i < splitText.size-1) {
        val prefix = "${splitText[i]}:"
        if (prefix == EXPLICIT_PREFIX) return true
        if (prefix !in listOf(PINNED_PREFIX, MODIFIED_PREFIX, MONTHLY_PREFIX,
                EXPLICIT_PREFIX, LOCAL_KEY_PREFIX, YTP_PREFIX)) {
            break
        }
        i++
    }
    return false
}

fun String.stripExplicitEmoji(): String {
    return this.replace("\uD83C\uDD74", "").trimStart()
}
