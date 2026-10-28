package com.banlimusic.desktop.data.sync

import com.banlimusic.app.data.sync.OneDriveAuthConfig

/**
 * Desktop OneDrive configuration. The client ID is the same public native-client
 * registration used by Android; Entra must additionally list the loopback URI
 * below under "Mobile and desktop applications".
 */
object DesktopOneDriveConfig {

    const val CLIENT_ID: String = "188469c5-9b63-49a9-8449-ea8fea226247"
    // Entra ignores the port for localhost redirect URIs. The login server
    // binds an ephemeral port to avoid Windows reserved-port ranges.
    const val REDIRECT_URI: String = "http://localhost/callback"
    const val TENANT: String = "consumers"

    val isConfigured: Boolean get() = CLIENT_ID.isNotBlank()

    fun authConfig(): OneDriveAuthConfig = OneDriveAuthConfig(
        clientId = CLIENT_ID,
        redirectUri = REDIRECT_URI,
        tenant = TENANT,
    )
}
