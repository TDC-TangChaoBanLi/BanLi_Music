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
import com.banlimusic.shared.ui.agreement.AgreementText
import com.banlimusic.shared.ui.agreement.AgreementTitle
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
