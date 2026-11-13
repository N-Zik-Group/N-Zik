package app.n_zik.android.ui.components.tab.toolbar

import androidx.annotation.StringRes
import app.n_zik.android.core.utils.Toaster

interface Descriptive: Clickable {

    @get:StringRes
    val messageId: Int

    /**
     * What happens when user holds this icon for a while.
     *
     * By default, this will send out message
     */
    override fun onLongClick() = Toaster.i(messageId)
}


