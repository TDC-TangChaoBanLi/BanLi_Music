/**
 * 文件作用：首次启动时的用户协议与 GD 音乐台声明界面。
 */
package com.banlimusic.app.feature.agreement

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banlimusic.shared.ui.agreement.AgreementText
import com.banlimusic.shared.ui.agreement.AgreementTitle
import com.banlimusic.shared.ui.agreement.BanLiAgreementScreen

@Composable
fun AgreementScreen(onAccept: () -> Unit) {
    val activity = LocalContext.current as? Activity
    BanLiAgreementScreen(
        onAccept = onAccept,
        onExit = { activity?.finish() },
        platformSections = {
            Spacer(Modifier.height(14.dp))
            AgreementTitle("Android 本地数据与云同步")
            AgreementText(
                "应用内的收藏、播放历史、下载记录和设置保存在本机。卸载应用会删除应用私有数据；" +
                    "Android 10 及以上写入系统音乐目录的下载文件是否保留由系统媒体库决定。",
            )
            AgreementText(
                "你可以选择使用自己的 Microsoft 账号登录，把收藏夹和收藏歌曲同步到自己的 " +
                    "OneDrive 应用专属目录（应用 / BanLiMusic / library.json）。" +
                    "登录令牌仅保存在本机的 Android Keystore 加密存储中，应用不会读取该目录之外的 " +
                    "OneDrive 文件，也不会把同步数据发送到开发者服务器。",
            )
            AgreementText(
                "你可以随时在设置中断开 OneDrive，并选择是否删除云端同步文件。" +
                    "同步文件只包含收藏夹名称、曲目标识与曲目元数据，不包含音频文件、播放历史或登录令牌。",
            )
        },
    )
}
