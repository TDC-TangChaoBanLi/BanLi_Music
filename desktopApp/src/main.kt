package com.banlimusic.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.banlimusic.app.core.datastore.SUPPORTED_SOURCES

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "BanLiMusic Desktop") {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                DesktopPlaceholder()
            }
        }
    }
}

@Composable
private fun DesktopPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("BanLiMusic Desktop", style = MaterialTheme.typography.headlineMedium)
        Text(
            "共享核心已接入。桌面播放、下载与本地音乐将在下一阶段实现。",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "已配置来源：" + SUPPORTED_SOURCES.joinToString(" / ") { it.label },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
