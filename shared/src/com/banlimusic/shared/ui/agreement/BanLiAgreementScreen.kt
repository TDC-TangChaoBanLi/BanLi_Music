/**
 * 文件作用：两端共用的首次启动用户协议与 GD 音乐台声明界面。
 */
package com.banlimusic.shared.ui.agreement

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared agreement screen. Platform-specific storage/sync wording is injected
 * through [platformSections] so Android and desktop keep accurate details
 * without duplicating the layout.
 */
@Composable
fun BanLiAgreementScreen(
    onAccept: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    platformSections: @Composable ColumnScope.() -> Unit = {},
) {
    var accepted by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
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
                    .padding(20.dp),
            ) {
                AgreementContent(platformSections = platformSections)
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

@Composable
fun AgreementTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
fun AgreementText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
}

/**
 * Agreement body reused by the first-run screen and the settings dialog.
 */
@Composable
fun AgreementContent(
    modifier: Modifier = Modifier,
    platformSections: @Composable ColumnScope.() -> Unit = {},
) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = modifier) {
        AgreementTitle("用户协议")
        AgreementText(
            "本应用为个人学习与研究用途的音乐客户端。你应遵守所在地区的法律法规，" +
                "不得利用本应用传播违法内容，不得侵犯音乐创作者、表演者和权利人的合法权益。",
        )
        AgreementText(
            "应用内的收藏、播放历史、下载记录和设置保存在本机。" +
                "卸载应用或删除本机数据目录会清除对应数据。",
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
            "下载功能仅用于个人学习。下载目录由设置决定；导入本地音乐只读取系统媒体库，" +
                "不会移动或修改原始文件。",
        )

        platformSections()
        Spacer(Modifier.height(14.dp))

        AgreementTitle("第三方服务")
        AgreementText(
            "搜索、歌词、封面和播放地址由第三方音乐服务提供。应用无法保证这些服务的可用性、" +
                "准确性或持续性。你可以在设置中随时更换或停用音乐源。",
        )
    }
}