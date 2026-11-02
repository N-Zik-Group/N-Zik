package app.n_zik.android.components.glass

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import app.n_zik.android.components.ui.screens.profiles.purgeProfileData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GlassProfilePreferencesIoFailureTest {
    private val context = FailingStorageContext(ApplicationProvider.getApplicationContext())
    private val migration get() = context.store(GlassProfilePreferences.MIGRATION_NAME)
    private val target get() = context.store(GlassProfilePreferences.fileName("work"))

    @Test fun `failed target write never persists completion and retries after restart`() {
        seedLegacy()
        target.failedCommits = 1

        val available = GlassProfilePreferences.preferences(context, "work")
        assertEquals(11f, available.getFloat(glassBlurKey, 0f), 0f)
        assertFalse(migration.getBoolean("profile:work", false))
        assertEquals(11f, target.getFloat(glassBlurKey, 0f), 0f)
        assertFalse(target.persisted.containsKey(glassBlurKey))

        context.restart()
        val retried = GlassProfilePreferences.preferences(context, "work")
        assertEquals(11f, retried.getFloat(glassBlurKey, 0f), 0f)
        assertEquals(11f, target.persisted[glassBlurKey])
        assertEquals(true, migration.persisted["profile:work"])
    }

    @Test fun `failed target write retries in the same process despite updated memory`() {
        seedLegacy()
        target.failedCommits = 1
        val available = GlassProfilePreferences.preferences(context, "work")
        assertEquals(11f, available.getFloat(glassBlurKey, 0f), 0f)

        GlassProfilePreferences.preferences(context, "work")

        assertEquals(11f, target.persisted[glassBlurKey])
        assertEquals(true, migration.persisted["profile:work"])
        assertEquals(2, target.commitAttempts)
    }

    @Test fun `failed snapshot commit preserves readable values without marking migration complete`() {
        seedLegacy()
        migration.failedCommits = 1

        val available = GlassProfilePreferences.preferences(context, "work")
        assertEquals(11f, available.getFloat(glassBlurKey, 0f), 0f)
        assertFalse(migration.getBoolean("snapshot_ready", false))
        assertFalse(migration.getBoolean("profile:work", false))

        GlassProfilePreferences.preferences(context, "work")

        assertEquals(11f, target.persisted[glassBlurKey])
        assertEquals(true, migration.persisted["snapshot_ready"])
    }

    @Test fun `failed completion commit stays retryable without overwriting existing target choices`() {
        seedLegacy()
        GlassProfilePreferences.preferences(context, "default")
        target.edit().putFloat(glassBlurKey, 18f).commit()
        migration.failedCommits = 1

        val available = GlassProfilePreferences.preferences(context, "work")
        assertEquals(18f, available.getFloat(glassBlurKey, 0f), 0f)
        assertFalse(migration.getBoolean("profile:work", false))

        GlassProfilePreferences.preferences(context, "work")

        assertEquals(18f, target.persisted[glassBlurKey])
        assertEquals(true, migration.persisted["profile:work"])
    }

    @Test fun `retry rebuilds a failed snapshot without retaining removed legacy values`() {
        seedLegacy()
        migration.failedCommits = 2
        GlassProfilePreferences.preferences(context, "work")
        assertFalse(migration.getBoolean("snapshot_ready", false))
        context.store(GlassProfilePreferences.LEGACY_NAME).edit().clear().commit()

        GlassProfilePreferences.preferences(context, "work")

        assertFalse(migration.contains("value:$glassBlurKey"))
        assertEquals(true, migration.persisted["profile:work"])
    }

    @Test fun `profile purge propagates deletion failure and a later retry removes the file`() {
        seedLegacy()
        GlassProfilePreferences.preferences(context, "work")
        context.deletionSucceeds = false

        val failure = assertThrows(IllegalStateException::class.java) {
            purgeProfileData(context, "work")
        }
        assertEquals("Could not delete Glass settings", failure.message)
        assertTrue(target.persisted.containsKey(glassBlurKey))

        context.deletionSucceeds = true
        GlassProfilePreferences.delete(context, "work")
        assertTrue(target.persisted.isEmpty())
        assertTrue(GlassProfilePreferences.preferences(context, "work").all.isEmpty())
    }

    @Test fun `deletion stops when failed migration would allow old values to return on ID reuse`() {
        seedLegacy()
        GlassProfilePreferences.preferences(context, "default")
        migration.failedCommits = 1

        val failure = assertThrows(IllegalStateException::class.java) {
            GlassProfilePreferences.delete(context, "work")
        }
        assertEquals("Could not complete Glass migration before deletion", failure.message)
        assertTrue(target.persisted.containsKey(glassBlurKey))

        GlassProfilePreferences.delete(context, "work")
        assertTrue(GlassProfilePreferences.preferences(context, "work").all.isEmpty())
    }

    private fun seedLegacy() {
        context.store(GlassProfilePreferences.LEGACY_NAME).edit().putFloat(glassBlurKey, 11f).commit()
    }

    private class FailingStorageContext(base: Context) : ContextWrapper(base) {
        private val stores = mutableMapOf<String, FailingPreferences>()
        var deletionSucceeds = true
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = store(name)
        fun store(name: String): FailingPreferences = stores.getOrPut(name) { FailingPreferences() }
        fun restart() = stores.values.forEach { it.restart() }
        override fun deleteSharedPreferences(name: String): Boolean {
            if (!deletionSucceeds) return false
            stores.remove(name)
            return true
        }
    }

    /** Models Android's memory-before-disk commit contract, including failed durable writes. */
    private class FailingPreferences : SharedPreferences {
        private var memory = mutableMapOf<String, Any?>()
        var persisted: Map<String, Any?> = emptyMap()
            private set
        var failedCommits = 0
        var commitAttempts = 0
        fun restart() { memory = persisted.toMutableMap() }
        override fun getAll(): Map<String, *> = memory.toMap()
        override fun contains(key: String): Boolean = memory.containsKey(key)
        override fun getString(key: String, defValue: String?): String? = memory[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (memory[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = memory[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = memory[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = memory[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = memory[key] as? Boolean ?: defValue
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val changes = mutableMapOf<String, Any?>()
            private var clear = false
            override fun putString(key: String, value: String?) = apply { changes[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = apply { changes[key] = values?.toSet() }
            override fun putInt(key: String, value: Int) = apply { changes[key] = value }
            override fun putLong(key: String, value: Long) = apply { changes[key] = value }
            override fun putFloat(key: String, value: Float) = apply { changes[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
            override fun remove(key: String) = apply { changes[key] = null }
            override fun clear() = apply { clear = true }
            override fun apply() { commit() }
            override fun commit(): Boolean {
                commitAttempts++
                if (clear) memory.clear()
                changes.forEach { (key, value) -> if (value == null) memory.remove(key) else memory[key] = value }
                if (failedCommits > 0) { failedCommits--; return false }
                persisted = memory.toMap()
                return true
            }
        }
    }
}
