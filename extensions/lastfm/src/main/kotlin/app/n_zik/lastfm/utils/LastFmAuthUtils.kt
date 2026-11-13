package app.n_zik.lastfm.utils

import java.security.MessageDigest

object LastFmAuthUtils {

    fun generateSignature(params: Map<String, String>, apiSecret: String): String {
        val sortedParams = params.toSortedMap()

        val stringBuilder = StringBuilder()
        for ((key, value) in sortedParams) {
            if (key != "api_sig" && key != "format") {
                stringBuilder.append(key).append(value)
            }
        }

        stringBuilder.append(apiSecret)

        return md5(stringBuilder.toString())
    }

    private fun md5(string: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(string.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
