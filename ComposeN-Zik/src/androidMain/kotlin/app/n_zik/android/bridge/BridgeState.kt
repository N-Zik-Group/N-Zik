package app.n_zik.android.bridge

/** Lifecycle of the local PC bridge server, observed by the UI. */
sealed interface BridgeState {
    data object Stopped : BridgeState
    data object Starting : BridgeState
    /** [viaHotspot]: listening on the phone's own hotspot instead of a Wi-Fi network it joined. */
    data class Running(val ip: String, val port: Int, val viaHotspot: Boolean = false) : BridgeState
    data object Stopping : BridgeState

    /** Start refused or server stopped: neither on Wi-Fi nor sharing a hotspot (contract §11.1). */
    data object NotOnWifi : BridgeState

    /** Start failed for an unexpected reason (e.g. no port could be bound). */
    data object Failed : BridgeState
}
