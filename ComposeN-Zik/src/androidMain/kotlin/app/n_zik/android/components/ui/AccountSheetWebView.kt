package app.n_zik.android.components.ui

import android.content.Context
import android.view.MotionEvent
import android.webkit.WebView

/** Page gestures belong to WebView. The Compose sheet handles gestures that start on its handle. */
internal class AccountSheetWebView(context: Context) : WebView(context) {
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        val handled = super.dispatchTouchEvent(event)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
            (action == MotionEvent.ACTION_DOWN && !handled)
        ) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }
}
