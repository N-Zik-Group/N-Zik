package app.n_zik.android.core.profiles

import android.content.Context
import android.system.ErrnoException
import android.system.OsConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal object LegacySeparationStatus {
    enum class Cause { CONFLICT, QUOTA, SPACE, UNVERIFIED }

    class Conflict(message: String) : IllegalStateException(message)
    class Quota(message: String) : IllegalStateException(message)

    val failures = MutableStateFlow<Map<Pair<ProfileStorageIdentity, ProfileDataItem>, Cause>>(emptyMap())

    fun record(context: Context, id: String, category: ProfileDataItem, error: Throwable) {
        val owner = profileStorageIdentity(context, id) ?: return
        if (!context.profileShares(category, id)) return
        val cause = when (error) {
            is Conflict -> Cause.CONFLICT
            is Quota -> Cause.QUOTA
            else -> if (generateSequence(error) { it.cause?.takeUnless { cause -> cause === it } }
                    .any { it is ErrnoException && it.errno == OsConstants.ENOSPC }
            ) Cause.SPACE else Cause.UNVERIFIED
        }
        failures.update { it + ((owner to category) to cause) }
    }
}
