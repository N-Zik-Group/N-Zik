package app.n_zik.android.core.coil

import coil3.disk.DiskCache

/** Coordinates cache access with validation and eviction of a confirmed Storage operation. */
internal class CleanupCoilCache(
    private val delegate: DiskCache,
    private val lock: Any,
) : DiskCache by delegate {
    private var leases = 0

    fun whileIdle(action: () -> Boolean): Boolean = synchronized(lock) {
        leases == 0 && action()
    }

    override fun openSnapshot(key: String): DiskCache.Snapshot? = synchronized(lock) {
        delegate.openSnapshot(key)?.let(::wrapSnapshot)
    }

    override fun openEditor(key: String): DiskCache.Editor? = synchronized(lock) {
        delegate.openEditor(key)?.let(::wrapEditor)
    }

    override fun clear() = synchronized(lock) { delegate.clear() }

    override fun remove(key: String): Boolean = synchronized(lock) { delegate.remove(key) }

    override fun shutdown() = synchronized(lock) { delegate.shutdown() }

    private fun wrapSnapshot(snapshot: DiskCache.Snapshot): DiskCache.Snapshot {
        val lease = Lease()
        return object : DiskCache.Snapshot by snapshot {
            override fun close() = lease.finish { snapshot.close() }

            override fun closeAndOpenEditor(): DiskCache.Editor? =
                lease.finish { snapshot.closeAndOpenEditor()?.let(::wrapEditor) }
        }
    }

    private fun wrapEditor(editor: DiskCache.Editor): DiskCache.Editor {
        val lease = Lease()
        return object : DiskCache.Editor by editor {
            override fun commit() = lease.finish { editor.commit() }

            override fun commitAndOpenSnapshot(): DiskCache.Snapshot? =
                lease.finish { editor.commitAndOpenSnapshot()?.let(::wrapSnapshot) }

            override fun abort() = lease.finish { editor.abort() }
        }
    }

    private inner class Lease {
        private var open = true

        init {
            leases++
        }

        fun <T> finish(action: () -> T): T = synchronized(lock) {
            try {
                action()
            } finally {
                if (open) {
                    open = false
                    leases--
                }
            }
        }
    }
}
