package app.n_zik.android.core.ondevice

import app.n_zik.android.enums.SortOrder

/** Groups available device items while preserving the existing order inside each group. */
internal fun <T> List<T>.groupByDeviceAvailability(
    order: SortOrder,
    isAvailableOnDevice: (T) -> Boolean,
): List<T> {
    val (available, other) = partition(isAvailableOnDevice)
    return if (order == SortOrder.Ascending) available + other else other + available
}
