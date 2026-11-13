package app.n_zik.android.core.profiles

import android.content.Context
import androidx.work.Data
import androidx.work.workDataOf
import app.it.fast4x.rimusic.utils.getActiveProfile
import app.it.fast4x.rimusic.utils.isProfileIdSafe

internal object ProfileWorkOwner {
    private const val PROFILE = "owner_profile"
    private const val GENERATION = "owner_generation"

    fun capture(context: Context): ProfileStorageIdentity? =
        profileStorageIdentity(context, getActiveProfile(context))

    fun name(base: String, owner: ProfileStorageIdentity): String = "$base:${owner.id}:${owner.generation}"

    fun input(owner: ProfileStorageIdentity): Data =
        workDataOf(PROFILE to owner.id, GENERATION to owner.generation)

    fun read(data: Data): ProfileStorageIdentity? {
        val id = data.getString(PROFILE)?.takeIf(::isProfileIdSafe) ?: return null
        val generation = data.getString(GENERATION) ?: return null
        return ProfileStorageIdentity(id, generation)
    }

    fun exists(context: Context, owner: ProfileStorageIdentity): Boolean =
        profileStorageIdentity(context, owner.id) == owner

    fun isBound(context: Context, owner: ProfileStorageIdentity): Boolean =
        isCurrentProcessStorageProfile(context, owner)
}
