package app.n_zik.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionButton(
    val subscribeButtonRenderer: SubscribeButtonRenderer,
) {
    @Serializable
    data class SubscribeButtonRenderer(
        val subscriberCountText: Runs,
        val subscribed: Boolean,
        val channelId: String,
        val longSubscriberCountText: Runs
    )
}