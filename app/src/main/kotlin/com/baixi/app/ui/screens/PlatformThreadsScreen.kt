package com.baixi.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudCircle
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baixi.app.data.prefs.SettingsRepository

/**
 * 分平台下载线程数设置页（全屏覆盖层）：
 * - 网盘 CDN 对单文件并发 Range 请求敏感，允许为夸克/UC/百度/139/123 分别设置下载线程数；
 * - 0 = 跟随全局（默认），选项按平台 CDN 特性给出推荐档位；
 * - 迅雷不在此页：CDN 硬限约 8 并发 Range，引擎固定 8 线程下载，无选项可设。
 */
@Composable
fun PlatformThreadsScreen(
    settings: SettingsRepository,
    globalThreads: Int,
    onBack: () -> Unit
) {
    var quarkThreads by remember { mutableIntStateOf(settings.quarkDownloadThreads) }
    var ucThreads by remember { mutableIntStateOf(settings.ucDownloadThreads) }
    var baiduThreads by remember { mutableIntStateOf(settings.baiduDownloadThreads) }
    var c139Threads by remember { mutableIntStateOf(settings.c139DownloadThreads) }
    var pan123Threads by remember { mutableIntStateOf(settings.pan123DownloadThreads) }
    // 当前正在编辑的平台（非空时弹单选弹窗）
    var editing by remember { mutableStateOf<PlatformThreadKey?>(null) }

    // 系统返回键 → 返回设置页（编辑弹窗打开时由弹窗优先消费返回键）
    BackHandler { onBack() }

    fun threadsOf(key: PlatformThreadKey): Int = when (key) {
        PlatformThreadKey.QUARK -> quarkThreads
        PlatformThreadKey.UC -> ucThreads
        PlatformThreadKey.BAIDU -> baiduThreads
        PlatformThreadKey.C139 -> c139Threads
        PlatformThreadKey.PAN123 -> pan123Threads
    }

    fun setThreads(key: PlatformThreadKey, value: Int) {
        when (key) {
            PlatformThreadKey.QUARK -> {
                quarkThreads = value; settings.quarkDownloadThreads = value
            }
            PlatformThreadKey.UC -> {
                ucThreads = value; settings.ucDownloadThreads = value
            }
            PlatformThreadKey.BAIDU -> {
                baiduThreads = value; settings.baiduDownloadThreads = value
            }
            PlatformThreadKey.C139 -> {
                c139Threads = value; settings.c139DownloadThreads = value
            }
            PlatformThreadKey.PAN123 -> {
                pan123Threads = value; settings.pan123DownloadThreads = value
            }
        }
    }

    // 平台条目（图标与登录引导页同风格区分）：迅雷 CDN 硬限 8 线程、无选项，不在此页列出
    val items = listOf(
        Triple(PlatformThreadKey.QUARK, Icons.Outlined.CloudDownload, "夸克网盘"),
        Triple(PlatformThreadKey.UC, Icons.Outlined.Cloud, "UC 网盘"),
        Triple(PlatformThreadKey.BAIDU, Icons.Outlined.CloudCircle, "百度网盘"),
        Triple(PlatformThreadKey.C139, Icons.Outlined.CloudSync, "139 网盘"),
        Triple(PlatformThreadKey.PAN123, Icons.Outlined.CloudDone, "123 云盘")
    )

    // Surface 承载背景（替代裸 Column + background）：Surface 自动把内容色设为 onSurface，
    // 裸容器下无 color 的 Text 会回退 M3 默认纯黑——深色模式黑底黑字不可读
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶栏：返回 + 标题（statusBarsPadding 适配系统状态栏）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "分平台下载线程数",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // 小屏 / 横屏兜底：条目 + 说明卡放不下时可滚动，保证 5 个平台条目都可点达
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 说明卡：解释「跟随全局」语义与平台线程数的用途
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "网盘服务器对单文件并发连接数敏感，过高可能触发限流。为单个平台设置的线程数仅对该平台的下载生效，" +
                            "「跟随全局」时使用全局线程数（当前 $globalThreads）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 平台条目：统一由一张 Card 承载，条目之间用分割线（与设置页同一套视觉语言）。
            // 最后一项之后不画分割线，避免卡片底部出现悬空分隔线。
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    items.forEachIndexed { index, (key, icon, title) ->
                        // >0 表示该平台已单独定制线程数（非「跟随全局」）：图标容器用主色 tonal 强调
                        val threads = threadsOf(key)
                        PlatformThreadsItem(
                            icon = icon,
                            title = title,
                            description = threadsDesc(threads, globalThreads),
                            highlighted = threads > 0,
                            onClick = { editing = key }
                        )
                        if (index != items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 56.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 平台线程数单选弹窗：0=跟随全局，档位最高 512（迅雷不在此页，引擎固定 8）
    editing?.let { key ->
        val current = threadsOf(key)
        // 档位：0=跟随全局 + 1~512；迅雷固定 8 线程（CDN 硬限），不在此页配置
        val options: List<Int> = listOf(0, 4, 8, 16, 32, 64, 128, 256, 512)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = {
                Text(
                    when (key) {
                        PlatformThreadKey.QUARK -> "夸克网盘"
                        PlatformThreadKey.UC -> "UC 网盘"
                        PlatformThreadKey.BAIDU -> "百度网盘"
                        PlatformThreadKey.C139 -> "139 网盘"
                        PlatformThreadKey.PAN123 -> "123 云盘"
                    }
                )
            },
            text = {
                // 档位多（9 项）单列排布，小屏/横屏可能超高：加滚动兜底
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    when (key) {
                        PlatformThreadKey.BAIDU -> Text(
                            text = "百度普通账号下载限速明显，提升线程效果有限",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        else -> {}
                    }
                    if (key == PlatformThreadKey.BAIDU) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    options.forEach { value ->
                        val selected = value == current
                        // 选中态：背景/文字色用 animateColorAsState 平滑过渡，避免切换时的生硬跳变
                        val rowBackground by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                            animationSpec = tween(durationMillis = 200),
                            label = "threadOptionBackground"
                        )
                        val rowTextColor by animateColorAsState(
                            targetValue = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            animationSpec = tween(durationMillis = 200),
                            label = "threadOptionText"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(rowBackground)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(bounded = true)
                                ) {
                                    setThreads(key, value)
                                    editing = null
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // onClick=null：选中由整行点击驱动，RadioButton 纯展示（保持原行为）
                            RadioButton(selected = selected, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (value == 0) "跟随全局（$globalThreads 线程）" else "$value 线程",
                                style = MaterialTheme.typography.bodyLarge,
                                color = rowTextColor
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { editing = null }) { Text("取消") }
            }
        )
    }
    }
}

/** 平台枚举（本页内部用） */
private enum class PlatformThreadKey { QUARK, UC, BAIDU, C139, PAN123 }

/** 当前值描述（条目副文案） */
private fun threadsDesc(value: Int, globalThreads: Int): String =
    if (value <= 0) "跟随全局（$globalThreads 线程）" else "$value 线程"

/** 平台条目行：tonal 图标容器 + 名称 + 当前值 + 右箭头（与设置页 SettingsItem 同一套视觉语言） */
@Composable
private fun PlatformThreadsItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    /** true = 该平台已单独定制线程数（非「跟随全局」），图标容器用主色 tonal 强调并平滑过渡 */
    highlighted: Boolean = false
) {
    // 图标容器/图标色随「是否单独定制」平滑过渡，避免列表值变化时颜色突变
    val containerColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = tween(durationMillis = 240),
        label = "platformIconContainer"
    )
    val contentColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 240),
        label = "platformIconContent"
    )
    // 条目已由外层分组 Card 承载，本行只负责 ripple + 内容（不再自己套 Card，避免卡中卡）
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(containerColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier.widthIn(min = 40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}