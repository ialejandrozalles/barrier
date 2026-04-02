package org.barrierfoss.androidclient.data

import org.barrierfoss.androidclient.protocol.ProtocolConstants

data class ConnectionConfig(
    val serverHost: String,
    val serverPort: Int,
    val screenName: String,
) {
    fun isValid(): Boolean {
        return serverHost.isNotBlank() &&
            screenName.isNotBlank() &&
            serverPort in 1..65535
    }

    companion object {
        val DEFAULT = ConnectionConfig(
            serverHost = "",
            serverPort = ProtocolConstants.DEFAULT_PORT,
            screenName = "android-client",
        )
    }
}
