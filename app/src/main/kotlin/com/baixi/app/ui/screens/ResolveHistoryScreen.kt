package com.baixi.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.baixi.app.data.db.ResolveHistoryEntity
import com.baixi.app.data.repository.ResolveHistoryRepository
import kotlinx.coroutines.launch

/**
 * 解析历史页（全屏覆盖层）：
 * - 最近 100 条成功解析记录，星标置顶 + 时间倒序；
 * - 点击条目 → 重新解析（MainScreen 注入链接并切到解析页）；
 * - 搜索（title / url 模糊，大小写不敏感）+ 筛选（全部 / 仅收藏）；
 * - 长按条目 → 编辑标签（逗号分隔，最多 5 个、每个 ≤8 字）；
 * - 支持单条删除与一键清空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResolveHistoryScreen(
    repository: ResolveHistoryRepository,
    onReopen: (String) -> Unit,
    onBack: () -> Unit
) {
    val history by repository.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pendingClear by remember { mutableStateOf(false) }
    // 搜索关键字（title / url 模糊匹配，大小写不敏感）
    var query by rememberSaveable { mutableStateOf("") }
    // 筛选：false = 全部，true = 仅收藏
    var starredOnly by rememberSaveable { mutableStateOf(false) }
    // 长按待编辑标签的条目（null 表示未打开编辑弹窗）
    var tagTarget by remember { mutableStateOf<ResolveHistoryEntity?>(null) }

    // 过滤后的列表：星标置顶顺序由 DAO 排序保证，过滤不改变相对顺序
    val keyword = query.trim()
    val filtered = remember(history, keyword, starredOnly) {
        history.filter { item ->
            (!starredOnly || item.isStarred) &&
                (keyword.isEmpty() ||
                    item.title.contains(keyword, ignoreCase = true) ||
                    item.url.contains(keyword, ignoreCase = true))
        }
    }

    // 系统返回键 → 返回主页（而不是退出应用）
    BackHandler { onBack() }

    // Surface 承载背景（替代裸 Column + background）：Surface 自动把内容色设为 onSurface，
    // 裸容器下无 color 的 Text 会回退 M3 默认纯黑——深色模式黑底黑字不可读
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 顶栏：返回 + 标题 + 清空（statusBarsPadding 适配系统状态栏，避免顶栏顶进时间/电量区）
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
                text = "解析历史",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = { pendingClear = true }) { Text("清空") }
            }
        }

        // 搜索框（无前置图标，placeholder 提示）+ 全部 / 仅收藏筛选
        if (history.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("搜索历史") },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !starredOnly,
                    onClick = { starredOnly = false },
                    label = { Text("全部") },
                    colors = FilterChipDefaults.filterChipColors()
                )
                FilterChip(
                    selected = starredOnly,
                    onClick = { starredOnly = true },
                    label = { Text("仅收藏") },
                    colors = FilterChipDefaults.filterChipColors()
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${filtered.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (history.isEmpty()) {
            // 空状态
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无解析历史",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "解析成功的分享链接会自动记录在这里\n点击记录可快速重新解析",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (filtered.isEmpty()) {
            // 有历史但被搜索/筛选条件过滤完
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (starredOnly) "没有符合条件的收藏记录" else "没有匹配的历史记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        onClick = { onReopen(item.url) },
                        onToggleStar = { scope.launch { repository.toggleStar(item.id) } },
                        onEditTags = { tagTarget = item },
                        onDelete = { scope.launch { repository.delete(item.id) } }
                    )
                }
            }
        }
    }

    // 清空确认
    if (pendingClear) {
        AlertDialog(
            onDismissRequest = { pendingClear = false },
            title = { Text("清空解析历史") },
            text = { Text("将删除全部解析历史记录，且不可恢复。") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingClear = false
                        scope.launch { repository.clearAll() }
                    }
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = false }) { Text("取消") }
            }
        )
    }

    // 编辑标签（长按条目打开）：逗号分隔，最多 5 个、每个 ≤8 字
    tagTarget?.let { target ->
        TagEditDialog(
            initial = target.tags,
            onDismiss = { tagTarget = null },
            onConfirm = { tags ->
                tagTarget = null
                scope.launch { repository.updateTags(target.id, tags) }
            }
        )
    }
    }
}

/** 标签编辑弹窗：输入逗号分隔标签，校验通过后回写（逗号分隔字符串） */
@Composable
private fun TagEditDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑标签") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("如：电影,4K,学习") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = error ?: "逗号分隔，最多 5 个，每个不超过 8 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tags = parseTagInput(text)
                    if (tags == null) error = "标签不合法：最多 5 个，每个不超过 8 字"
                    else onConfirm(tags.joinToString(","))
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItemCard(
    item: ResolveHistoryEntity,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
    onEditTags: () -> Unit,
    onDelete: () -> Unit
) {
    // 星标切换弹性动画（收藏态常驻轻微放大强调）
    val starScale by animateFloatAsState(
        targetValue = if (item.isStarred) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "starScale"
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                // 长按 → 编辑标签
                onLongClick = onEditTags
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "未命名分享" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 平台徽标
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = platformDisplayName(item.platform),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatHistoryTime(item.createTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 标签（小号文字，如 "#电影 #4K"）
                if (item.tags.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTags(item.tags),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            // 星标收藏：金色填充星 / 空星边框，点击切换（星标条目排序置顶）
            IconButton(onClick = onToggleStar) {
                Icon(
                    if (item.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (item.isStarred) "取消收藏" else "收藏",
                    tint = if (item.isStarred) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            scaleX = starScale
                            scaleY = starScale
                        }
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** 标签解析：逗号/顿号/空白分隔，去重去掉 # 前缀；非法（>5 个或单个 >8 字）返回 null */
private fun parseTagInput(raw: String): List<String>? {
    val tags = raw.split(',', '，', '、', ' ', '\n', '\t')
        .map { it.trim().removePrefix("#").trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    if (tags.size > 5) return null
    if (tags.any { it.length > 8 }) return null
    return tags
}

/** 标签展示：逗号分隔字符串 → "#标签1 #标签2" */
private fun formatTags(tags: String): String =
    tags.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { "#$it" }

/** 平台中文名（历史徽标展示用） */
fun platformDisplayName(platform: String): String = when (platform) {
    "UC" -> "UC网盘"
    "XUNLEI" -> "迅雷"
    "BAIDU" -> "百度网盘"
    "C139" -> "139邮箱网盘"
    "PAN123" -> "123云盘"
    else -> "夸克网盘"
}

/** 历史时间展示：今天显示「今天 HH:mm」，更早显示完整日期 */
fun formatHistoryTime(timeMillis: Long): String {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = timeMillis }
    val now = java.util.Calendar.getInstance()
    fun p(n: Int) = n.toString().padStart(2, '0')
    val sameYear = now.get(java.util.Calendar.YEAR) == cal.get(java.util.Calendar.YEAR)
    val sameDay = sameYear && now.get(java.util.Calendar.DAY_OF_YEAR) == cal.get(java.util.Calendar.DAY_OF_YEAR)
    return if (sameDay) {
        "今天 ${p(cal.get(java.util.Calendar.HOUR_OF_DAY))}:${p(cal.get(java.util.Calendar.MINUTE))}"
    } else {
        "${cal.get(java.util.Calendar.YEAR)}-${p(cal.get(java.util.Calendar.MONTH) + 1)}-${p(cal.get(java.util.Calendar.DAY_OF_MONTH))} " +
            "${p(cal.get(java.util.Calendar.HOUR_OF_DAY))}:${p(cal.get(java.util.Calendar.MINUTE))}"
    }
}
