package app.n_zik.android.extensions.youtubelogin

import android.R.attr.resource
import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import app.n_zik.innertube.Innertube
import app.n_zik.android.R
import app.n_zik.android.colorPalette
import app.n_zik.android.LocalPlayerAwareWindowInsets
import app.n_zik.android.enums.NavRoutes
import app.n_zik.android.utils.ytVisitorDataKey
import app.n_zik.android.utils.ytCookieKey
import app.n_zik.android.utils.rememberEncryptedPreference
import app.n_zik.android.utils.ytAccountNameKey
import app.n_zik.android.utils.ytAccountEmailKey
import app.n_zik.android.utils.ytAccountChannelHandleKey
import app.n_zik.android.utils.rememberEncryptedPreference
import app.n_zik.android.utils.rememberPreference
import app.n_zik.android.utils.ytAccountThumbnailKey
import app.n_zik.android.utils.ytDataSyncIdKey
import app.n_zik.android.utils.coroutines.NzikDispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLogin(
    onLogin: (String) -> Unit
) {

    val scope = rememberCoroutineScope()

    var visitorData by rememberEncryptedPreference(key = ytVisitorDataKey, defaultValue = Innertube.DEFAULT_VISITOR_DATA)
    var dataSyncId by rememberEncryptedPreference(key = ytDataSyncIdKey, defaultValue = "")
    var cookie by rememberEncryptedPreference(key = ytCookieKey, defaultValue = "")
    var accountName by rememberEncryptedPreference(key = ytAccountNameKey, defaultValue = "")
    var accountEmail by rememberEncryptedPreference(key = ytAccountEmailKey, defaultValue = "")
    var accountChannelHandle by rememberEncryptedPreference(key = ytAccountChannelHandleKey, defaultValue = "")
    var accountThumbnail by rememberEncryptedPreference(key = ytAccountThumbnailKey, defaultValue = "")

    var webView: WebView? = null

    Column (
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    var hasCompletedLogin = false
                    webViewClient = object : WebViewClient() {
                        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                            // Do nothing here, we will handle it in onPageFinished
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            loadUrl("javascript:Android.onRetrieveVisitorData(window.yt.config_.VISITOR_DATA)")
                            loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt.config_.DATASYNC_ID)")

                            if (url?.startsWith("https://music.youtube.com") == true && !hasCompletedLogin) {
                                cookie = CookieManager.getInstance().getCookie(url)
                                hasCompletedLogin = true

                                NzikDispatchers.fireAndForget(NzikDispatchers.DATA).launch {
                                    kotlinx.coroutines.delay(500)

                                    Innertube.cookie = cookie
                                    Innertube.dataSyncId = dataSyncId
                                    Innertube.visitorData = visitorData

                                    Innertube.accountInfo().onSuccess {
                                        Timber.tag("YouTubeLogin").d("onPageFinished accountInfo() $it")
                                        accountName = it?.name.orEmpty()
                                        accountEmail = it?.email.orEmpty()
                                        accountChannelHandle = it?.channelHandle.orEmpty()
                                        accountThumbnail = it?.thumbnailUrl.orEmpty()
                                        onLogin(cookie)                                     
                                    }.onFailure {
                                        Timber.tag("YouTubeLogin").e("Error : ${it.stackTraceToString()}")
                                        hasCompletedLogin = false // Allow retry
                                    }
                                }
                            }
                        }
                    }
                    settings.apply {
                        javaScriptEnabled = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onRetrieveVisitorData(newVisitorData: String?) {
                            if (newVisitorData != null) {
                                visitorData = newVisitorData
                            }
                        }
                        @JavascriptInterface
                        fun onRetrieveDataSyncId(newDataSyncId: String?) {
                            if (newDataSyncId != null) {
                                dataSyncId = newDataSyncId.substringBefore("||")
                            }
                        }
                    }, "Android")
                    webView = this
                    loadUrl("https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F")
                }
            }
        )

        BackHandler(enabled = webView?.canGoBack() == true) {
            webView?.goBack()
        }


    }



}




