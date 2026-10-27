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

@Composable
private fun LegacyAgreementScreen(onAccept: () -> Unit) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val activity = LocalContext.current as? Activity

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Text(
                text = "欢迎使用 BanLiMusic",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "首次使用前，请阅读并同意以下内容。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                ) {
                    AgreementTitle("用户协议")
                    AgreementText(
                        "本应用为个人学习与研究用途的音乐客户端。你应遵守所在地区的法律法规，" +
                            "不得利用本应用传播违法内容，不得侵犯音乐创作者、表演者和权利人的合法权益。",
                    )
                    AgreementText(
                        "应用内的收藏、播放历史、下载记录和设置保存在本机。卸载应用会删除应用私有数据；" +
                            "Android 10 及以上写入系统音乐目录的下载文件是否保留由系统媒体库决定。",
                    )
                    Spacer(Modifier.height(14.dp))
                    AgreementTitle("GD 音乐台声明")
                    AgreementText(
                        "音乐数据来自 GD 音乐台（music.gdstudio.xyz），API 由 GD Studio 提供，" +
                            "采用 CC BY-NC 4.0 许可。该平台仅用于学习参考，请勿用于商业用途。",
                    )
                    AgreementText(
                        "请勿下载、传播受版权保护的内容。应用中的播放地址通常带时效，" +
                            "临时地址不应被视为永久下载地址。",
                    )
                    AgreementText(
                        "GD 音乐台限制每 5 分钟 50 次请求。应用已内置客户端限流，并强制请求间隔至少 1 秒；" +
                            "服务端仍可能拒绝请求或要求冷却。",
                    )
                    TextButton(onClick = { uriHandler.openUri("https://music.gdstudio.xyz") }) {
                        Text("打开 GD 音乐台")
                    }
                    Spacer(Modifier.height(6.dp))
                    AgreementTitle("下载与本地文件")
                    AgreementText(
                        "下载功能仅用于个人学习。默认下载目录为系统音乐目录下的 BanLiMusic 文件夹；" +
                            "如果平台不允许写入，会回退到应用外部音乐目录。导入本地音乐只读取系统媒体库，" +
                            "不会移动或修改原始文件。",
                    )
                    Spacer(Modifier.height(14.dp))
                    AgreementTitle("OneDrive 云同步（可选）")
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
                    Spacer(Modifier.height(14.dp))
                    AgreementTitle("第三方服务")
                    AgreementText(
                        "搜索、歌词、封面和播放地址由第三方音乐服务提供。应用无法保证这些服务的可用性、" +
                            "准确性或持续性。你可以在设置中随时更换或停用音乐源。",
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = accepted,
                    onCheckedChange = { accepted = it },
                )
                Text(
                    text = "我已阅读并同意上述用户协议与 GD 音乐台声明",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAccept,
                enabled = accepted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("同意并继续")
            }
            TextButton(
                onClick = { activity?.finish() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("不同意并退出")
            }
        }
    }
}

@Composable
private fun AgreementTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun AgreementText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
}
