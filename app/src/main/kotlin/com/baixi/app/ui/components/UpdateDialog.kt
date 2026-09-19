package com.baixi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.baixi.app.data.update.UpdateInfo

/**
 * 「发现新版本」弹窗：展示版本对比 + 更新说明，提供「下载并安装 / 跳过此版本 / 以后再说」。
 *
 * 下载走应用自身的多线程下载器（下载页可见进度、完成通知里也能看到），
 * 下载完成后由调用方拉起系统安装界面 —— 所以这里不需要进度条。
 */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    currentVersion: String,
    onDownload: () -> Unit,
    onSkipVersion: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SystemUpdate, contentDescription = null) },
        title = { Text("发现新版本 ${info.version}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "当前版本 $currentVersion → 最新版本 ${info.version}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (info.apkSize > 0) {
                    Text(
                        text = "安装包 ${formatApkSize(info.apkSize)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
                if (info.changelog.isNotBlank()) {
                    Text("更新内容", style = MaterialTheme.typography.titleSmall)
                    // 更新说明可能很长：限高 + 滚动，避免弹窗撑破屏幕
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = info.changelog,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "本次发布没有填写更新说明。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = androidx.compose.ui.Modifier.height(2.dp))
                Text(
                    text = "提示：更新包下载完成后会自动打开系统安装界面；" +
                        "若提示「应用未安装」，说明安装包签名与当前版本不一致（例如官方包与本地调试包），" +
                        "需卸载后重装。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDownload) { Text("下载并安装") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onSkipVersion) { Text("跳过此版本") }
                TextButton(onClick = onDismiss) { Text("以后再说") }
            }
        }
    )
}

/** 安装包大小文案（KB / MB） */
private fun formatApkSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> "${bytes / 1024} KB"
    else -> "$bytes B"
}
