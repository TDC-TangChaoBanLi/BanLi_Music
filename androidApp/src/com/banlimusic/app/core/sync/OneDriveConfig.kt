/**
 * 文件作用：集中定义 OneDrive 应用注册参数与当前安装包的签名回调地址。
 */
package com.banlimusic.app.core.sync

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.banlimusic.app.data.sync.OneDriveAuthConfig
import java.security.MessageDigest

object OneDriveConfig {

    /**
     * 官方 Entra 应用注册的 Application (client) ID。
     *
     * 这是公开标识符，不是密钥，允许提交到仓库；它会随 APK 分发，并出现在授权 URL 中。
     * 回调 URI 与包名 + 签名绑定：使用其他签名或包名构建时，Entra 会返回 AADSTS50011，
     * 需要自行注册应用并替换此值。留空时同步页面会显示“未配置”并给出回调信息。
     */
    const val CLIENT_ID: String = "188469c5-9b63-49a9-8449-ea8fea226247"

    /** 个人 Microsoft 账号使用 consumers 端点。 */
    const val TENANT: String = "consumers"
    const val REDIRECT_SCHEME: String = "msauth"
    const val REDIRECT_HOST: String = "com.banlimusic.app"

    val isConfigured: Boolean get() = CLIENT_ID.isNotBlank()

    fun redirectUri(signatureHash: String): String =
        if (signatureHash.isBlank()) {
            ""
        } else {
            // Entra's Android platform stores the URL-encoded Base64 hash, e.g. `...%2F...%3D`.
            "$REDIRECT_SCHEME://$REDIRECT_HOST/${Uri.encode(signatureHash)}"
        }

    fun authConfig(signatureHash: String): OneDriveAuthConfig = OneDriveAuthConfig(
        clientId = CLIENT_ID,
        redirectUri = redirectUri(signatureHash),
        tenant = TENANT,
    )
}

/**
 * Derives the `msauth://` signature path from the certificate that signed the
 * installed APK. Entra's Android platform registration expects the standard
 * Base64 form (URL-encoded when placed in the URI).
 */
object OneDriveSignature {

    fun sha1Base64(context: Context): String {
        val bytes = signingCertificate(context) ?: return ""
        val digest = MessageDigest.getInstance("SHA-1").digest(bytes)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    /** Hex form expected by the Entra "Android" platform signature field. */
    fun sha1Hex(context: Context): String {
        val bytes = signingCertificate(context) ?: return ""
        return MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { byte -> "%02X".format(java.util.Locale.US, byte.toInt() and 0xFF) }
    }

    private fun signingCertificate(context: Context): ByteArray? {
        val manager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = manager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES,
            )
            info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            val info = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            info.signatures?.firstOrNull()?.toByteArray()
        }
    }
}
