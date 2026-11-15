package app.n_zik.android.components.settings.accounts

import app.n_zik.android.utils.coroutines.NzikDispatchers
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal suspend fun githubContributionBlock(id: Long): String? = runInterruptible(NzikDispatchers.DATA) {
    val url = URL("https://raw.githubusercontent.com/N-Zik-Group/N-Zik-Theme-Store/main/catalog/creators/blocked.json")
    val connection = url.openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.instanceFollowRedirects = false
        val status = connection.responseCode
        // Older catalog revisions do not have a contribution policy yet.
        if (status == 404) return@runInterruptible null
        check(status == 200)
        val document = connection.inputStream.bufferedReader().use { reader ->
            val data = CharArray(128 * 1024)
            var count = 0
            while (count < data.size) {
                val read = reader.read(data, count, data.size - count)
                if (read == -1) break
                count += read
            }
            check(count < data.size)
            String(data, 0, count)
        }
        contributionBlockMessage(document, id)
    } finally {
        connection.disconnect()
    }
}

@Serializable
private data class ContributionPolicy(val schemaVersion: Int, val accounts: List<BlockedAccount>)

@Serializable
private data class BlockedAccount(val githubId: Long, val message: String)

internal fun contributionBlockMessage(document: String, id: Long): String? {
    val policy = Json.decodeFromString<ContributionPolicy>(document)
    check(policy.schemaVersion == 1)
    return policy.accounts.firstOrNull { it.githubId == id }?.message?.take(400)
}
