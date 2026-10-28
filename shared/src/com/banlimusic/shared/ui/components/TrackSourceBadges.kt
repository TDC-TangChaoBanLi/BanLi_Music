/**
 * 文件作用：提供两端共用的来源名称、来源徽标与多来源提示。
 */
package com.banlimusic.shared.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.banlimusic.app.core.model.Track
import com.banlimusic.app.domain.playback.SourceOrdering

/** Sources that are not music providers and must not be shown as "GD..." badges. */
val NON_PROVIDER_SOURCE_IDS = setOf("local", "download")

/** Short provider name without the API prefix. */
fun providerLabel(providerId: String): String = when (providerId) {
    "gd-netease" -> "网易云"
    "gd-joox" -> "JOOX"
    "gd-bilibili" -> "B站"
    "local" -> "本地"
    "download" -> "已下载"
    else -> providerId.substringAfterLast('-')
}

/** API prefix shown before the provider name, e.g. "GD" for GD Music Station. */
fun sourcePrefix(providerId: String): String? = when {
    providerId.startsWith("gd-") -> "GD"
    else -> null
}

/** Full display name: API prefix + short provider name, e.g. "GD网易云". */
fun sourceDisplayName(providerId: String): String {
    val prefix = sourcePrefix(providerId) ?: return providerLabel(providerId)
    val label = providerLabel(providerId)
    val latin = label.firstOrNull()?.code?.let { it < 128 } == true
    return if (latin) "$prefix $label" else "$prefix$label"
}

/** Same as [sourceDisplayName] but with the API prefix rendered in bold. */
fun sourceDisplayNameAnnotated(providerId: String): AnnotatedString = buildAnnotatedString {
    val prefix = sourcePrefix(providerId)
    val label = providerLabel(providerId)
    if (prefix != null) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(prefix) }
        if (label.firstOrNull()?.code?.let { it < 128 } == true) append(" ")
    }
    append(label)
}

@Composable
fun SourceBadge(providerId: String) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = sourceDisplayNameAnnotated(providerId),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

/**
 * Renders every meaningful source for a track: the API provider, a "本地" badge
 * for imported files and an extra "已下载" badge for downloaded copies.
 */
@Composable
fun TrackSourceBadges(
    track: Track,
    downloaded: Boolean = false,
    preferredProviderId: String? = null,
    onSourceClick: (() -> Unit)? = null,
) {
    val orderedSources = SourceOrdering.reorderSources(track.sources, preferredProviderId)
    val providerSources = orderedSources.filterNot { it.providerId in NON_PROVIDER_SOURCE_IDS }
    val hasLocalImport = orderedSources.any { it.providerId == "local" }
    val hasDownload = downloaded || orderedSources.any { it.providerId == "download" }
    Row {
        if (hasLocalImport) {
            SourceBadge("local")
        } else {
            providerSources.firstOrNull()?.let { SourceBadge(it.providerId) }
            if (providerSources.size > 1) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "+${providerSources.size - 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = if (onSourceClick != null) {
                        Modifier
                            .clickable(onClick = onSourceClick)
                            .padding(horizontal = 2.dp)
                    } else {
                        Modifier
                    },
                )
            }
        }
        if (hasDownload) {
            Spacer(Modifier.width(4.dp))
            SourceBadge("download")
        }
    }
}
