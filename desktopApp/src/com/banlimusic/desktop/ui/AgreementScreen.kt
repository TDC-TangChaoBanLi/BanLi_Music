package com.banlimusic.desktop.ui

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.banlimusic.shared.ui.agreement.BanLiAgreementScreen

@Composable
fun DesktopAgreementScreen(onAccept: () -> Unit, onExit: () -> Unit) {
    BanLiAgreementScreen(
        onAccept = onAccept,
        onExit = onExit,
        platformSections = {
            Spacer(Modifier.height(12.dp))
            AgreementTitle("桌面端说明")
            AgreementText(
                "桌面端复用 Android 端的共享核心，目前支持搜索、收藏和 MP3 播放" +
                    "（标准/较高/高品音质）。无损音质需要后续接入新的解码后端。",
            )
            AgreementText(
                "桌面端的收藏与缓存分别位于用户配置目录和缓存目录；删除这些目录会清除对应数据。",
            )
        },
    )
}

@Composable
private fun LegacyDesktopAgreementScreen(onAccept: () -> Unit, onExit: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 42.dp, vertical = 30.dp),
    ) {
        Text(
            text = "欢迎使用 BanLiMusic",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "首次使用前，请阅读并同意以下内容。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
            ) {
                AgreementContent()
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = accepted, onCheckedChange = { accepted = it })
            Text(
                text = "我已阅读并同意上述用户协议与 GD 音乐台声明",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onAccept,
                enabled = accepted,
                modifier = Modifier.weight(1f),
            ) {
                Text("同意并继续")
            }
            TextButton(onClick = onExit, modifier = Modifier.padding(start = 8.dp)) {
                Text("不同意并退出")
            }
        }
    }
}

/** Shared agreement copy, also shown from the settings page. */
@Composable
fun AgreementContent() {
    val uriHandler = LocalUriHandler.current
    AgreementTitle("用户协议")
    AgreementText(
        "本应用为个人学习与研究用途的音乐客户端。你应遵守所在地区的法律法规，" +
            "不得利用本应用传播违法内容，不得侵犯音乐创作者、表演者和权利人的合法权益。",
    )
    AgreementText(
        "应用内的收藏、播放记录和设置保存在本机。桌面端的收藏与缓存分别位于用户配置目录和缓存目录；" +
            "删除这些目录会清除对应数据。",
    )
    Spacer(Modifier.height(12.dp))
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
    Spacer(Modifier.height(8.dp))
    AgreementTitle("桌面端说明")
    AgreementText(
        "桌面端复用 Android 端的共享核心，目前支持搜索、收藏和 MP3 播放（标准/较高/高品音质）。" +
            "无损音质需要后续接入新的解码后端。",
    )
    Spacer(Modifier.height(12.dp))
    AgreementTitle("第三方服务")
    AgreementText(
        "搜索、歌词、封面和播放地址由第三方音乐服务提供。应用无法保证这些服务的可用性、" +
            "准确性或持续性。你可以在设置中随时更换或停用音乐源。",
    )
}

@Composable
private fun AgreementTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(5.dp))
}

@Composable
private fun AgreementText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(7.dp))
}
