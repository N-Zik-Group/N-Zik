package app.n_zik.android.core.coil

import coil3.disk.DiskCache

/** Coordinates cache access with validation and eviction of a confirmed Storage operation. */
internal class CleanupCoilCache(
    private val delegate: DiskCache,
    private val lock: Any,
    private val resolveKey: (String) -> String? = { it },
) : DiskCache by delegate {
    private var leases = 0

    fun whileIdle(action: () -> Boolean): Boolean = synchronized(lock) {
        leases == 0 && action()
    }

    override fun openSnapshot(key: String): DiskCache.Snapshot? = synchronized(lock) {
        resolveKey(key)?.let(delegate::openSnapshot)?.let { wrapSnapshot(key, it) }
    }

    override fun openEditor(key: String): DiskCache.Editor? = synchronized(lock) {
        resolveKey(key)?.let(delegate::openEditor)?.let { wrapEditor(key, it) }
    }

    override fun clear() = synchronized(lock) { delegate.clear() }

    override fun remove(key: String): Boolean = synchronized(lock) {
        resolveKey(key)?.let(delegate::remove) ?: false
    }

    override fun shutdown() = synchronized(lock) { delegate.shutdown() }

    private fun wrapSnapshot(key: String, snapshot: DiskCache.Snapshot): DiskCache.Snapshot {
        val lease = Lease()
        return object : DiskCache.Snapshot by snapshot {
            override fun close() = lease.finish { snapshot.close() }

            override fun closeAndOpenEditor(): DiskCache.Editor? = lease.finish {
                if (resolveKey(key) == null) {
                    snapshot.close()
                    null
                } else snapshot.closeAndOpenEditor()?.let { wrapEditor(key, it) }
            }
        }
    }

    private fun wrapEditor(key: String, editor: DiskCache.Editor): DiskCache.Editor {
        val lease = Lease()
        return object : DiskCache.Editor by editor {
            override fun commit() = lease.finish {
                if (resolveKey(key) == null) editor.abort() else editor.commit()
            }

            override fun commitAndOpenSnapshot(): DiskCache.Snapshot? = lease.finish {
                if (resolveKey(key) == null) {
                    editor.abort()
                    null
                } else editor.commitAndOpenSnapshot()?.let { wrapSnapshot(key, it) }
            }

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
