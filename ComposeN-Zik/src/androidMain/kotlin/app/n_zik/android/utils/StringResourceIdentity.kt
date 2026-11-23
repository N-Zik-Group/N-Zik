package app.n_zik.android.utils

/** Preserves published resource-name identities across the string naming migration. */
internal object StringResourceIdentity {
    /** Resolves one-to-one renames; unchanged, unknown and merged names pass through. */
    fun legacyName(resourceName: String): String = aliases[resourceName] ?: resourceName

    private val aliases: Map<String, String> by lazy {
        buildMap {
            addStringIdentityAliasesFirst()
            addStringIdentityAliasesSecond()
            addStringIdentityAliasesThird()
        }
    }
}
