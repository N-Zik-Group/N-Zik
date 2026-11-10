package app.n_zik.android.playback.services

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheKeyFactory
import java.io.Closeable
import java.io.IOException
import java.util.IdentityHashMap

/** Pins an app reader's keys until its data source or file copy closes. */
@UnstableApi
internal object StreamingCacheReaders {
    private val lock = Any()
    private val readers = IdentityHashMap<Cache, MutableMap<String, Int>>()
    private val reservations = IdentityHashMap<Cache, MutableSet<String>>()

    fun acquire(cache: Cache, key: String): Closeable {
        synchronized(lock) {
            if (reservations[cache]?.contains(key) == true) throw IOException("The cache entry is being cleaned")
            val keys = readers.getOrPut(cache) { mutableMapOf() }
            keys[key] = keys.getOrDefault(key, 0) + 1
        }
        return object : Closeable {
            private var closed = false

            override fun close() {
                synchronized(lock) {
                    if (closed) return@synchronized
                    closed = true
                    val keys = checkNotNull(readers[cache])
                    val remaining = keys.getValue(key) - 1
                    if (remaining == 0) keys.remove(key) else keys[key] = remaining
                    if (keys.isEmpty()) readers.remove(cache)
                }
            }
        }
    }

    fun isReading(cache: Cache, key: String): Boolean = synchronized(lock) { readers[cache]?.containsKey(key) == true }

    fun isReserved(cache: Cache, key: String?): Boolean = synchronized(lock) {
        val reserved = reservations[cache] ?: return@synchronized false
        if (key == null) reserved.isNotEmpty() else key in reserved
    }

    /** Holds the idle keys across asynchronous Media3 removal without holding a thread lock. */
    fun reserveIdle(cache: Cache, keys: Set<String>): Closeable? = synchronized(lock) {
        if (keys.any { reservations[cache]?.contains(it) == true || readers[cache]?.containsKey(it) == true }) {
            return@synchronized null
        }
        val reserved = reservations.getOrPut(cache) { mutableSetOf() }
        reserved.addAll(keys)
        var closed = false
        Closeable {
            synchronized(lock) {
                if (!closed) {
                    closed = true
                    reserved.removeAll(keys)
                    if (reserved.isEmpty()) reservations.remove(cache)
                }
            }
        }
    }

    fun <T> ifIdle(cache: Cache, keys: Set<String>, action: () -> T): T? = synchronized(lock) {
        if (keys.any { readers[cache]?.containsKey(it) == true || reservations[cache]?.contains(it) == true }) null else action()
    }

    fun factory(cache: Cache, delegate: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val source = delegate.createDataSource()
        object : DataSource by source {
            private var lease: Closeable? = null

            override fun open(dataSpec: DataSpec): Long {
                check(lease == null)
                lease = acquire(cache, CacheKeyFactory.DEFAULT.buildCacheKey(dataSpec))
                return try {
                    source.open(dataSpec)
                } catch (failure: Throwable) {
                    try {
                        close()
                    } catch (closeFailure: Throwable) {
                        if (closeFailure !== failure) failure.addSuppressed(closeFailure)
                    }
                    throw failure
                }
            }

            override fun close() {
                val current = lease ?: return
                try {
                    source.close()
                } finally {
                    current.close()
                    lease = null
                }
            }
        }
    }
}
