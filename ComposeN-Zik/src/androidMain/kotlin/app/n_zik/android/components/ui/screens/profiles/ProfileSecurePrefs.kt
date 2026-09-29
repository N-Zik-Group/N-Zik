package app.n_zik.android.components.ui.screens.profiles

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.ConcurrentHashMap
import app.it.fast4x.rimusic.utils.DEFAULT_PROFILE_ID

/**
 * The per-profile secure prefs, cached per profile ID: the keystore-backed
 * [EncryptedSharedPreferences] instance is expensive to create (MasterKey + keystore
 * round trip), so it is built once per profile and reused from any thread (thread-safe
 * via [ConcurrentHashMap.computeIfAbsent]).
 */
private val securePrefsCache = ConcurrentHashMap<String, SharedPreferences>()

/**
 * Removes the cached instance of [profileId] (profile deletion): without it, the
 * in-memory [EncryptedSharedPreferences] would keep holding the deleted credentials
 * and re-materialize the file on its next commit.
 */
internal fun evictProfileSecurePrefs(profileId: String) {
    securePrefsCache.remove(profileId)
}

/** Secure prefs of a profile: `secure_preferences` for the base, `secure_preferences_<id>` otherwise. */
internal fun profileSecurePrefs(context: Context, profileId: String): SharedPreferences =
    securePrefsCache.computeIfAbsent(profileId) {
        val masterKey = MasterKey.Builder(context, MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            if (profileId == DEFAULT_PROFILE_ID) "secure_preferences" else "secure_preferences_$profileId",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
