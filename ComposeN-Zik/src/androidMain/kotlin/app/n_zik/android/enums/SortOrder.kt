package app.n_zik.android.enums

import org.jetbrains.annotations.Contract

enum class SortOrder( val rotationZ: Float, val asSqlString: String ) {
    Ascending( 0f, "ASC" ),
    Descending( 180f, "DESC" );

    operator fun not() = when (this) {
        Ascending -> Descending
        Descending -> Ascending
    }

    /**
     * Attempt to apply sort order based on selected value.
     *
     * The provided list [items] is always assumed to be sorted
     * in ascending order. Therefore, it only get reversed
     * when [Descending] is selected.
     *
     * Return list is always a new list to prevent unwanted results
     *
     * @return a new list regardless selected value
     */
    @Contract(
        value = "_->new",
        pure = true
    )
    fun <T> applyTo( items: List<T> ): List<T> =
        when( this ) {
            Descending -> items.reversed()
            Ascending -> items.toList()
        }
}



