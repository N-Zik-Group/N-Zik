package app.kreate.android.me.knighthat.utils

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.MainThread
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import es.dmoral.toasty.Toasty
import app.n_zik.android.R
import app.n_zik.android.appContext
import app.n_zik.android.utils.toast.BottomNavigationToastPlacement
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import org.intellij.lang.annotations.MagicConstant

/**
 * This singleton handles displaying **toast** to users.
 *
 * It sits on top of [Toasty] (which already an abstraction
 * of [Toast.makeText]).
 *
 * Additional features include:
 * - Ensure toasts are displayed on **_main_** thread
 * - Customizable background and foreground colors
 * - Adjusts position when floating navigation bar is active
 *
 * Future plans:
 * - Handles console outputs for debugging
 */
object Toaster {

    enum class Type(
        @ColorInt val background: Int,
        @ColorInt val foreground: Int,
        @DrawableRes private val iconId: Int
    ) {
        NORMAL(
            Color.rgb( 108, 117, 125 ),
            Color.WHITE,
            -1
        ),
        SUCCESS(
            Color.rgb( 25, 135, 84 ),
            Color.WHITE,
            R.drawable.checkmark
        ),
        INFO(
            Color.rgb( 13, 110, 253 ),
            Color.WHITE,
            R.drawable.information
        ),
        WARNING(
            Color.rgb( 255, 193, 7 ),
            Color.rgb( 33, 37, 41 ),
            R.drawable.alert
        ),
        ERROR(
            Color.rgb( 220, 53, 69 ),
            Color.WHITE,
            R.drawable.close
        );

        val icon: Drawable by lazy {
            when( this ) {
                NORMAL -> ColorDrawable(Color.TRANSPARENT)
                else -> AppCompatResources.getDrawable( appContext(), iconId )!!
            }
        }
    }

    @MainThread
    fun toast(
        message: String,
        type: Type = Type.NORMAL,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT,
        icon: Drawable = type.icon,
        background: Int,
        foreground: Int
    ) {
        // Spec pc-ui-parity-audit-fixes, decision 7 (2026-10-10): explicit exception to the legacy rule — every phone toast reaches the PC bridge
        app.n_zik.android.utils.ToastRelay.onLegacyToast( message, type.name )
        NzikDispatchers.fireAndForget(NzikDispatchers.UI).launch {
            val toast = Toasty.custom(
                appContext(), message, icon, background, foreground, duration, icon != Type.NORMAL.icon, true
            )
            BottomNavigationToastPlacement.applyTo(toast)
            toast.show()
        }
    }

    @MainThread
    fun toast(
        message: String,
        type: Type = Type.NORMAL,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT,
        icon: Drawable = type.icon
    ) = this.toast( message, type, duration, icon, type.background, type.foreground )

    @MainThread
    fun toast(
        @StringRes messageId: Int,
        type: Type = Type.NORMAL,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT,
        icon: Drawable = type.icon,
        vararg formatArgs: Any?
    ) = this.toast( app.n_zik.android.utils.ToastRelay.legacyResource( messageId, formatArgs ), type, duration, icon )

    @MainThread
    fun toast(
        @StringRes messageId: Int,
        @DrawableRes iconId: Int,
        type: Type = Type.NORMAL,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast(
        message = app.n_zik.android.utils.ToastRelay.legacyResource( messageId, emptyArray() ),
        type = type,
        duration = duration,
        icon = AppCompatResources.getDrawable( appContext(), iconId )!!
    )

    fun n( message: String, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( message, Type.NORMAL, duration )

    fun n(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast( messageId, Type.NORMAL, duration, Type.NORMAL.icon, *formatArgs )

    fun s( message: String, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( message, Type.SUCCESS, duration )

    fun s( @StringRes messageId: Int, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( messageId, Type.SUCCESS, duration )

    fun s(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast( messageId, Type.SUCCESS, duration, Type.SUCCESS.icon, *formatArgs )

    fun i( message: String, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( message, Type.INFO, duration )

    fun i( @StringRes messageId: Int, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( messageId, Type.INFO, duration )

    fun i(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast( messageId, Type.INFO, duration, Type.INFO.icon, *formatArgs )

    fun w( message: String, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( message, Type.WARNING, duration )

    fun w( @StringRes messageId: Int, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( messageId, Type.WARNING, duration )

    fun w(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast( messageId, Type.WARNING, duration, Type.WARNING.icon, *formatArgs )

    fun e( message: String, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( message, Type.ERROR, duration )

    fun e( @StringRes messageId: Int, @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT ) =
        this.toast( messageId, Type.ERROR, duration )

    fun e(
        @StringRes messageId: Int,
        vararg formatArgs: Any?,
        @MagicConstant(valuesFromClass = Toast::class) duration: Int = Toast.LENGTH_SHORT
    ) = this.toast( messageId, Type.ERROR, duration, Type.ERROR.icon, *formatArgs )

    /*

        PRE-DEFINED TOASTS

     */

    fun done() = this.s( R.string.done )

    fun noInternet() = this.e( R.string.no_connection )
}


