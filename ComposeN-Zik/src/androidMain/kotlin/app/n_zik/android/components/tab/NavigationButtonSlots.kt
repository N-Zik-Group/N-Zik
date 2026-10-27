package app.n_zik.android.components.tab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.snapshots.Snapshot

/** Navigation slots ordered by tab index, updated when a child composition registers again. */
internal class NavigationButtonSlots<T> : Iterable<T> {
    private data class Slot<T>(val owner: Any, val index: Int, val content: T)

    private val slots = mutableStateListOf<Slot<T>>()
    private val slotCount = derivedStateOf(structuralEqualityPolicy()) { contents().size }

    val size: Int get() = slotCount.value

    fun clear() = slots.clear()

    operator fun set(index: Int, content: T) {
        update(index, index, content)
    }

    /** Registers at this composition position and removes the slot when it leaves. */
    @Composable
    fun register(index: Int, content: T) {
        val owner = remember(this) { Any() }
        // Skeleton reads the count immediately after add(), before effects run. Avoid
        // subscribing the registration scope to its own lookup while filling this frame.
        Snapshot.withoutReadObservation { update(owner, index, content) }
        DisposableEffect(this, owner) {
            onDispose { slots.removeAll { it.owner == owner } }
        }
    }

    private fun update(owner: Any, index: Int, content: T) {
        val position = slots.indexOfFirst { it.owner == owner }
        val slot = Slot(owner, index, content)
        if (position < 0) slots.add(slot) else slots[position] = slot
    }

    private fun contents(): List<T> {
        val byIndex = linkedMapOf<Int, T>()
        slots.forEach { byIndex[it.index] = it.content }
        // An index is the navigation position. Branch replacements can create their
        // owners after unchanged siblings, so registration lifetime cannot define order.
        return byIndex.toSortedMap().values.toList()
    }

    override fun iterator(): Iterator<T> = contents().iterator()
}
