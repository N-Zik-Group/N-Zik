package app.n_zik.android.recognition

import android.util.Log
import app.n_zik.android.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class RecognizedTrack(
    val title: String,
    val subtitle: String,
    val coverUrl: String? = null,
    val backgroundUrl: String? = null,
    val genre: String? = null
)

class ShazamRepository {

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val signature by lazy { ShazamSignature() }

    suspend fun identify(duration: Int, data: ByteArray): RecognizedTrack? {
        if (data.isEmpty()) return null

        val shorts = data.toShortArray()
        val signatureUri = runCatching {
            signature.safeCreate(shorts)
        }.onFailure {
            Log.e(TAG, "Signature generation failed", it)
        }.getOrNull() ?: return null

        val sampleMs = duration * 1000

        // Try proxy first (rate-limited to 10 req/min, but faster and no CORS)
        identifyViaProxy(sampleMs, signatureUri)?.let { return it }
        // Fallback to direct Shazam endpoint
        return identifyDirect(sampleMs, signatureUri)
    }

    // ── Proxy (preferred) ────────────────────────────────────────────────────
    private fun identifyViaProxy(sampleMs: Int, signatureUri: String): RecognizedTrack? =
        runCatching {
            val body = JSONObject().apply {
                put("signatureUri", signatureUri)
                put("sampleMs", sampleMs)
            }.toString()

            val response = client.newCall(
                Request.Builder()
                    .url(proxyUrl)
                    .post(body.toRequestBody(JSON))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("apikey", proxyApiKey)
                    .build()
            ).execute()

            when (response.code) {
                429 -> {
                    Log.w(TAG, "Proxy rate-limited — trying direct")
                    return null
                }
                !in 200..299 -> {
                    Log.w(TAG, "Proxy failed code=${response.code}")
                    return null
                }
            }

            val responseBody = response.body?.use { it.string() } ?: return null
            parseTrack(responseBody)
        }.onFailure {
            Log.w(TAG, "Proxy request exception", it)
        }.getOrNull()

    // ── Direct Shazam endpoint (fallback) ────────────────────────────────────
    private fun identifyDirect(sampleMs: Int, signatureUri: String): RecognizedTrack? =
        runCatching {
            val timestamp = Calendar.getInstance().timeInMillis.toInt()
            val reqName   = Random(timestamp).nextLong().toString()

            val body = JSONObject().apply {
                put("geolocation", JSONObject().apply {
                    put("altitude",  Random(timestamp).nextDouble() * 400 + 100)
                    put("latitude",  Random(timestamp).nextDouble() * 180 - 90)
                    put("longitude", Random(timestamp).nextDouble() * 360 - 180)
                })
                put("signature", JSONObject().apply {
                    put("samplems",  sampleMs)
                    put("timestamp", timestamp)
                    put("uri",       signatureUri)
                })
                put("timestamp", timestamp)
                put("timezone",  TIMEZONES.random())
            }.toString()

            val uuid1 = uuidFromNamespace(NS_DNS, reqName)
            val uuid2 = uuidFromNamespace(NS_URL, reqName)
            val url = "${baseUrl}discovery/v5/en/US/android/-/tag/$uuid1/$uuid2" +
                "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&video=v3"

            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .post(body.toRequestBody(JSON))
                    .header("User-Agent", USER_AGENTS.random())
                    .header("Content-Language", "en_US")
                    .header("Content-Type", "application/json")
                    .build()
            ).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Direct request failed code=${response.code}")
                return null
            }

            val responseBody = response.body?.use { it.string() } ?: return null
            parseTrack(responseBody)
        }.onFailure {
            Log.w(TAG, "Direct request exception", it)
        }.getOrNull()

    // ── Response parser ───────────────────────────────────────────────────────
    private fun parseTrack(json: String): RecognizedTrack? {
        return try {
            val root    = JSONObject(json)
            val matches = root.optJSONArray("matches")
            if (matches != null && matches.length() == 0) return null

            val track = root.optJSONObject("track") ?: return null
            if (track.length() == 0) return null

            val title    = track.optString("title").trim()
            val subtitle = track.optString("subtitle").trim()
            if (title.isBlank() || subtitle.isBlank()) return null

            val images = track.optJSONObject("images")
            val genres = track.optJSONObject("genres")

            RecognizedTrack(
                title    = title,
                subtitle = subtitle,
                coverUrl = images?.optString("coverarthq").takeUnless { it.isNullOrBlank() }
                    ?: images?.optString("coverart").takeUnless { it.isNullOrBlank() },
                backgroundUrl = images?.optString("background").takeUnless { it.isNullOrBlank() },
                genre    = genres?.optString("primary").takeUnless { it.isNullOrBlank() }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse track JSON", e)
            null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun ByteArray.toShortArray(): ShortArray {
        val shorts = ShortArray(size / 2)
        ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        return shorts
    }

    private fun uuidFromNamespace(namespace: UUID, value: String): String {
        val nsBytes = ByteBuffer.allocate(16)
            .putLong(namespace.mostSignificantBits)
            .putLong(namespace.leastSignificantBits)
            .array()
        val digest = MessageDigest.getInstance("SHA-1").apply {
            update(nsBytes)
            update(value.toByteArray())
        }.digest().copyOf(16)
        digest[6] = ((digest[6].toInt() and 0x0F) or 0x50).toByte()
        digest[8] = ((digest[8].toInt() and 0x3F) or 0x80).toByte()
        val buf = ByteBuffer.wrap(digest)
        return UUID(buf.long, buf.long).toString()
    }

    companion object {
        private const val TAG      = "ShazamRepository"
        private val JSON      = "application/json".toMediaType()
        private val NS_DNS    = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8")
        private val NS_URL    = UUID.fromString("6ba7b811-9dad-11d1-80b4-00c04fd430c8")
        private val baseUrl: String
            get() = "https://amp.shazam.com/"
        private val proxyUrl: String
            get() = "https://proxy.fast4x.it/identify"
        private val proxyApiKey: String
            get() = BuildConfig.SHAZAM_PROXY_API_KEY

        private val USER_AGENTS = listOf(
            "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
            "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)"
        )
        private val TIMEZONES = listOf(
            "Europe/London",
            "America/New_York",
            "Asia/Tokyo",
            Locale.getDefault().toLanguageTag().ifBlank { "Africa/Nairobi" }
        )
    }
}
