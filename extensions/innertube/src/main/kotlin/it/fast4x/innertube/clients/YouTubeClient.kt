package it.fast4x.innertube.clients

import it.fast4x.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val api_key: String,
    val clientId: String? = null,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val referer: String? = null,
    val xClientName: Int? = null,
    val isEmbedded: Boolean = false,
    val loginSupported: Boolean = false,
    val loginRequired: Boolean = false,
    val useSignatureTimestamp: Boolean = true,
    val useWebPoTokens: Boolean = false,
    val includeUserAgentInContext: Boolean = false,
    val useMusicPlayerEndpoint: Boolean = false,
    val requirePoToken: Boolean = false,
    val buildId: String? = null,
    val cronetVersion: String? = null,
    val packageName: String? = null,
) {
    fun toContext(locale: YouTubeLocale, visitorData: String, dataSyncId: String? = null) = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            gl = locale.gl,
            hl = locale.hl,
            visitorData = visitorData,
            referer = referer,
            api_key = api_key,
            userAgent = if (includeUserAgentInContext) userAgent else null,
            xClientName = xClientName,
            osName = osName,
            osVersion = osVersion,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            androidSdkVersion = androidSdkVersion,
            loginSupported = loginSupported,
            loginRequired = loginRequired,
            isEmbedded = isEmbedded,
            useWebPoTokens = useWebPoTokens,
            useSignatureTimestamp = useSignatureTimestamp,
        ),
        user = Context.User(
            onBehalfOfUser = if (loginSupported) dataSyncId?.takeIf { it.isNotBlank() } else null
        )
    )

    companion object {
        private const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"
        private const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.3"
        private const val USER_AGENT_ANDROID = "Mozilla/5.0 (Linux; Android 6.0; Nexus 5 Build/MRA58N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/65.0.3325.181 Mobile Safari/537.36"

        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "5.34.51",
            api_key = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            clientId = "21",
            userAgent = "com.google.android.apps.youtube.music/5.34.51 (Linux; U; Android 11) gzip",
            osName = "Android",
            osVersion = "11",
            androidSdkVersion = "30",
            xClientName = 21,
            loginSupported = true,
            useSignatureTimestamp = true,
            includeUserAgentInContext = true,
        )

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5",
            clientVersion = "7.20260707.07.00",
            api_key = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            clientId = "7",
            xClientName = 7,
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
            includeUserAgentInContext = true,
        )

        val TVHTML5_SIMPLY_EMBEDDED_PLAYER = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            api_key = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            clientId = "85",
            userAgent = TVHTML5.userAgent,
            xClientName = 85,
            isEmbedded = true,
            loginSupported = true,
            loginRequired = false,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
            requirePoToken = true,
        )

        val TVHTML5_SIMPLY = YouTubeClient(
            clientName = "TVHTML5_SIMPLY",
            clientVersion = "1.0",
            api_key = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            clientId = "75",
            userAgent = TVHTML5.userAgent,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
            requirePoToken = true,
            useMusicPlayerEndpoint = true,
        )

        val MOBILE = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "21.26.364",
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            clientId = "3",
            userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            osName = "Android",
            osVersion = "11",
            androidSdkVersion = "30",
            xClientName = 3,
            loginSupported = false,
            useSignatureTimestamp = false,
            includeUserAgentInContext = true,
        )

        val ANDROID_NO_SDK = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "21.26.364",
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            clientId = "3",
            userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            osName = "Android",
            osVersion = "11",
            androidSdkVersion = "30",
            xClientName = 3,
            loginSupported = false,
            useSignatureTimestamp = false,
            includeUserAgentInContext = true,
        )

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientVersion = "2.20260708.00.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3",
            clientId = "1",
            userAgent = USER_AGENT_WEB,
            xClientName = 1,
            loginSupported = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260707.12.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            clientId = "67",
            userAgent = USER_AGENT_WEB,
            referer = REFERER_YOUTUBE_MUSIC,
            xClientName = 67,
            loginSupported = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val ANDROID_VR_1_43_32 = YouTubeClient(
            osName = "Android",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            clientName = "ANDROID_VR",
            clientVersion = "1.43.32",
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
            osVersion = "12",
            xClientName = 28,
            loginSupported = false,
            useSignatureTimestamp = false,
            includeUserAgentInContext = true,
            useMusicPlayerEndpoint = true,
            buildId = "SQ3A.220605.009.A1",
            cronetVersion = "107.0.5284.2",
            packageName = "com.google.android.apps.youtube.vr.oculus",
        )

        val ANDROID_VR_1_61_48 = YouTubeClient(
            osName = "Android",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            osVersion = "12",
            xClientName = 28,
            loginSupported = false,
            useSignatureTimestamp = false,
            includeUserAgentInContext = true,
            useMusicPlayerEndpoint = true,
            buildId = "SQ3A.220605.009.A1",
            cronetVersion = "132.0.6808.3",
            packageName = "com.google.android.apps.youtube.vr.oculus",
        )

        val ANDROID_VR_1_65_10 = YouTubeClient(
            osName = "Android",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            androidSdkVersion = "32",
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            osVersion = "12L",
            xClientName = 28,
            loginSupported = false,
            useSignatureTimestamp = false,
            includeUserAgentInContext = true,
            useMusicPlayerEndpoint = true,
        )

        val ANDROID_CREATOR = YouTubeClient(
            clientName = "ANDROID_CREATOR",
            clientVersion = "25.03.101",
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            clientId = "14",
            userAgent = "com.google.android.apps.youtube.creator/25.03.101 (Linux; U; Android 15; en_US; Pixel 9 Pro Fold; Build/AP3A.241005.015.A2; Cronet/132.0.6779.0)",
            osName = "Android",
            osVersion = "15",
            deviceMake = "Google",
            deviceModel = "Pixel 9 Pro Fold",
            androidSdkVersion = "35",
            xClientName = 14,
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            includeUserAgentInContext = true,
            buildId = "AP3A.241005.015.A2",
            cronetVersion = "132.0.6779.0",
            packageName = "com.google.android.apps.youtube.creator",
        )

        val IPADOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "21.26.4",
            api_key = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
            clientId = "5",
            userAgent = "com.google.ios.youtube/21.26.4 (iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)",
            osName = "iPadOS",
            osVersion = "17.7.10.21H450",
            deviceModel = "iPad7,6",
            xClientName = 5,
            loginSupported = false,
            useSignatureTimestamp = false,
            includeUserAgentInContext = true,
        )

        val ANDROID_VR_NO_AUTH = YouTubeClient(
            clientName = "ANDROID_VR",
            clientVersion = "1.61.48",
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            clientId = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            xClientName = 28,
            loginSupported = false,
            useSignatureTimestamp = false,
            includeUserAgentInContext = true,
            useMusicPlayerEndpoint = true,
        )

        val WEB_CREATOR = YouTubeClient(
            clientName = "WEB_CREATOR",
            clientVersion = "1.20260708.06.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3",
            clientId = "62",
            userAgent = USER_AGENT_WEB,
            xClientName = 62,
            loginSupported = true,
            loginRequired = true,
            useSignatureTimestamp = true,
            useWebPoTokens = true,
        )

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "21.26.4",
            api_key = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
            clientId = "5",
            userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
            xClientName = 5,
            loginSupported = false,
            useSignatureTimestamp = false,
            includeUserAgentInContext = true,
        )

        val VISIONOS = YouTubeClient(
            clientName = "VISIONOS",
            clientVersion = "1.02",
            api_key = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
            clientId = "101",
            userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
            osName = "visionOS",
            osVersion = "26.5.23O471",
            deviceMake = "Apple",
            deviceModel = "RealityDevice17,1",
            xClientName = 101,
            loginSupported = false,
            useSignatureTimestamp = false,
            useMusicPlayerEndpoint = true,
        )
    }
}
