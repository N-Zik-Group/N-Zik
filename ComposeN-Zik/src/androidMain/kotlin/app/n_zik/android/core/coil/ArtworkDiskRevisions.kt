package app.n_zik.android.core.coil

/** Logical request keys carry freshness while Coil's underlying disk keys stay durable. */
internal class ArtworkDiskRevisions {
    private data class State(
        val counter: Long = 0L,
        val clearedAt: Long = 0L,
        val refreshed: Map<String, Long> = emptyMap(),
    )

    @Volatile
    private var state = State()

    fun current(base: String): Long = state.let { it.refreshed[base] ?: it.clearedAt }

    fun key(base: String): String = "$base$SEPARATOR${current(base)}"

    fun resolve(key: String): String? {
        val separator = key.lastIndexOf(SEPARATOR)
        if (separator == -1) return key
        val base = key.substring(0, separator)
        return base.takeIf { key.substring(separator + SEPARATOR.length).toLongOrNull() == current(base) }
    }

    // Mutations are serialized with disk eviction by ImageCacheFactory.
    fun refresh(bases: Set<String>) {
        val revision = state.counter + 1
        state = state.copy(counter = revision, refreshed = state.refreshed + bases.associateWith { revision })
    }

    fun clear() {
        val revision = state.counter + 1
        state = State(counter = revision, clearedAt = revision)
    }

    private companion object {
        const val SEPARATOR = "#nzik-artwork-revision="
    }
}
