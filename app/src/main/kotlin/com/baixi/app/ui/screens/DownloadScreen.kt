package com.baixi.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.baixi.app.data.db.DownloadTaskEntity
import com.baixi.app.data.download.DownloadStats
import com.baixi.app.data.prefs.SettingsRepository
import com.baixi.app.ui.SnackbarController
import com.baixi.app.ui.components.ScrollToTopButton
import com.baixi.app.ui.screens.FilePreviewScreen
import com.baixi.app.ui.viewmodel.DownloadViewModel
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 下载页：任务列表（分片多线程下载 / 断点续传）、进度展示、暂停/继续/删除/打开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: DownloadViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val stats by viewModel.stats.collectAsState()
    // 下载统计（今日/累计/完成数）：本地 SharedPreferences，主线程直读安全
    val context = LocalContext.current
    // 先取 context 再放进 remember 的 calculation，避免在 calculation 内读 LocalContext
    val settingsRepository = remember { SettingsRepository(context) }
    var pendingDelete by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 状态过滤 Tab：0=全部 1=下载中 2=已下载 3=失败（暂停/等待WiFi 归「下载中」，语义：还没下完）
    var statusFilter by rememberSaveable { mutableStateOf(0) }
    // 应用内预览：当前预览的已完成任务；null 表示未在预览
    var previewTask by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    // 任务设置弹窗：长按任务卡片打开的目标任务；null 表示未打开
    var settingsTask by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    // ★ 以下派生数据全部 remember 住：实时统计每 250ms 就推一次新值并触发本函数重组，
    //   若不缓存，每秒要重算十几次「计数 + 过滤 + 排序 + 分组」（几百条任务时明显掉帧）。
    // 过滤条计数徽标（跟随任务列表实时变化）
    val activeCount = remember(tasks) {
        tasks.count {
            it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                it.status == DownloadTaskEntity.STATUS_PENDING ||
                it.status == DownloadTaskEntity.STATUS_PAUSED ||
                it.status == DownloadTaskEntity.STATUS_WAITING_WIFI
        }
    }
    val completedCount = remember(tasks) { tasks.count { it.status == DownloadTaskEntity.STATUS_COMPLETED } }
    val failedCount = remember(tasks) { tasks.count { it.status == DownloadTaskEntity.STATUS_FAILED } }
    // 批量操作栏按钮可用态（同样只在任务列表变化时重算）
    val hasActive = remember(tasks) {
        tasks.any {
            it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                it.status == DownloadTaskEntity.STATUS_PENDING ||
                it.status == DownloadTaskEntity.STATUS_WAITING_WIFI
        }
    }
    val hasResumable = remember(tasks) {
        tasks.any {
            it.status == DownloadTaskEntity.STATUS_PAUSED ||
                it.status == DownloadTaskEntity.STATUS_FAILED ||
                it.status == DownloadTaskEntity.STATUS_WAITING_WIFI
        }
    }
    // 先按状态 Tab 过滤（全部/下载中/已下载/失败），再按「下载中置顶 → 已暂停/失败 → 已完成」排序
    val filteredTasks = remember(tasks, statusFilter) { filterTaskByStatus(tasks, statusFilter) }
    val activeFirst = remember(filteredTasks) {
        filteredTasks.sortedWith(
            compareBy<DownloadTaskEntity> { task ->
                when (task.status) {
                    DownloadTaskEntity.STATUS_DOWNLOADING,
                    DownloadTaskEntity.STATUS_PENDING,
                    DownloadTaskEntity.STATUS_WAITING_WIFI -> 0
                    DownloadTaskEntity.STATUS_PAUSED,
                    DownloadTaskEntity.STATUS_FAILED -> 1
                    else -> 2 // COMPLETED
                }
            }.thenByDescending { it.createTime }
        )
    }
    // 根目录任务（无相对路径）单独显示；文件夹任务按「顶级目录」分组（整个文件夹归一组，内部子目录不拆）
    val rootTasks = remember(activeFirst) { activeFirst.filter { !it.fileName.contains('/') } }
    val folderGroups = remember(activeFirst) {
        activeFirst.filter { it.fileName.contains('/') }.groupBy { it.fileName.substringBefore('/') }
    }
    // 全局实时总速度：各任务速度之和（字节/秒）
    val totalSpeed = stats.values.sumOf { it.speed }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        if (tasks.isEmpty()) {
            // 完全无任务：全屏空状态（过滤条无意义，不显示）
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                EmptyDownloadState(modifier = Modifier.align(Alignment.Center))
            }
        } else {
            // 过滤条常驻顶部：空分类下也必须可见——否则用户会被锁死在空分类里
            StatusFilterBar(
                filter = statusFilter,
                onFilterChange = { statusFilter = it },
                activeCount = activeCount,
                completedCount = completedCount,
                failedCount = failedCount
            )
            if (filteredTasks.isEmpty()) {
                // 分类空状态：过滤条仍在，可直接切回其他分类
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    EmptyDownloadState(modifier = Modifier.align(Alignment.Center), filtered = true)
                }
            } else {
            // 顶部统计条 + 实时速度曲线（固定高度，不参与 weight 分配，避免挤压任务列表）
                DownloadStatsHeader(
                    statsTodayBytes = settingsRepository.statsTodayBytes,
                    statsTotalBytes = settingsRepository.statsTotalBytes,
                    statsTotalCount = settingsRepository.statsTotalCount,
                    totalSpeed = totalSpeed
                )
            // 批量操作栏：全部暂停 / 全部开始 / 删除全部
                DownloadBatchBar(
                    hasActive = hasActive,
                    hasResumable = hasResumable,
                    onPauseAll = { viewModel.pauseAll() },
                    onResumeAll = { viewModel.resumeAll() },
                    onDeleteAll = { showDeleteAllConfirm = true }
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(rootTasks, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            stats = stats[task.id],
                            onPause = { viewModel.pause(task.id) },
                            onResume = { viewModel.resume(task.id, ignoreMeteredWait = true) },
                            onRemove = { pendingDelete = task },
                            onPreview = { previewTask = task },
                            // 长按卡片打开「任务设置」弹窗（优先级 / 线程数）
                            onLongClick = { settingsTask = task },
                            modifier = Modifier.animateItem()
                        )
                    }

                    folderGroups.forEach { (folder, groupTasks) ->
                        item(key = "folder_$folder") {
                            FolderDownloadGroup(
                                folder = folder,
                                tasks = groupTasks,
                                stats = stats,
                                onPause = { viewModel.pause(it) },
                                onResume = { viewModel.resume(it, ignoreMeteredWait = true) },
                                onRemove = { pendingDelete = it },
                                onPreview = { previewTask = it },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
        }

        // 返回顶部悬浮按钮（列表滚离顶部后显示，长列表快速回到顶部）
        ScrollToTopButton(
            listState = listState,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp)
        )
    }
    // 删除二次确认（可选同时删除本地文件）
    pendingDelete?.let { task ->
        DeleteConfirmDialog(
            task = task,
            onDismiss = { pendingDelete = null },
            onConfirm = { deleteLocal ->
                pendingDelete = null
                viewModel.remove(task.id, deleteLocal)
            }
        )
    }

    // 应用内文件预览（全屏覆盖层）：直接把 savePath 交给预览页（content:// 或绝对路径皆可），
    // 由 FilePreviewScreen 内部按 URI 流式读取——不再预先复制整份文件到 cacheDir，
    // 因此点「预览」立刻进入预览页并有加载反馈，大文件（> 64MB）也能应用内预览。
    previewTask?.let { task ->
        val context = LocalContext.current
        if (task.savePath.isBlank()) {
            // savePath 为空（任务数据异常/文件未落盘）：保持原有「不可预览」处理——关闭预览层，
            // 不进入预览页（openSavedFile 对空路径本身也是直接 return，无需再调）
            LaunchedEffect(task.id) { previewTask = null }
        } else {
            FilePreviewScreen(
                fileName = task.fileName.substringAfterLast('/'),
                filePath = task.savePath,
                onOpenExternal = {
                    previewTask = null
                    openSavedFile(context, task.savePath)
                },
                onDismiss = { previewTask = null }
            )
        }
    }

    // 删除全部任务二次确认（可选同时删除本地文件）
    if (showDeleteAllConfirm) {
        // 「同时删除本地文件」勾选态（弹窗每次重新打开都重置）
        var deleteAllLocal by remember { mutableStateOf(false) }
        val hasCompletedFile = tasks.any {
            it.status == DownloadTaskEntity.STATUS_COMPLETED && it.savePath.isNotBlank()
        }
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirm = false },
            title = { Text("删除全部任务") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "确定删除所有下载任务吗？删除后任务记录将被清除，且不可恢复。",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (hasCompletedFile) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = deleteAllLocal,
                                onCheckedChange = { deleteAllLocal = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "同时删除本地文件",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Text(
                            text = "勾选后将一并删除所有已下载到 Download 目录的文件，且不可恢复。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAllConfirm = false
                        viewModel.removeAll(deleteAllLocal)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("全部删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllConfirm = false }) { Text("取消") }
            }
        )
    }

    // 任务设置弹窗（长按任务卡片打开）：用列表里的最新快照渲染，避免弹窗停留在旧状态
    val settingsTarget = settingsTask
    if (settingsTarget != null) {
        tasks.firstOrNull { it.id == settingsTarget.id }?.let { latest ->
            TaskSettingsDialog(
                task = latest,
                onDismiss = { settingsTask = null },
                // 选择立即生效：转发给 ViewModel（内部 viewModelScope → DownloadManager → DAO）
                onPriority = { viewModel.setPriority(latest.id, it) },
                onThreads = { viewModel.setThreadOverride(latest.id, it) }
            )
        }
    }
}

/** 批量操作栏：全部暂停 / 全部开始 / 删除全部（Material3 紧凑按钮，无可用操作时禁用） */
@Composable
private fun DownloadBatchBar(
    hasActive: Boolean,
    hasResumable: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onDeleteAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPauseAll, enabled = hasActive) {
            Icon(
                imageVector = Icons.Outlined.Pause,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("全部暂停")
        }
        TextButton(onClick = onResumeAll, enabled = hasResumable) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("全部开始")
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onDeleteAll) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("删除全部", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    task: DownloadTaskEntity,
    onDismiss: () -> Unit,
    onConfirm: (deleteLocal: Boolean) -> Unit
) {
    var deleteLocal by remember { mutableStateOf(false) }
    val hasLocalFile = task.status == DownloadTaskEntity.STATUS_COMPLETED && task.savePath.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除下载任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "确定删除「${task.fileName}」吗？",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (hasLocalFile) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = deleteLocal,
                            onCheckedChange = { deleteLocal = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "同时删除本地文件",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Text(
                    text = if (hasLocalFile) {
                        "勾选后将一并删除已下载到 Download 目录的文件，且不可恢复。"
                    } else if (task.status == DownloadTaskEntity.STATUS_COMPLETED) {
                        "该任务没有已完成的本地文件。"
                    } else {
                        "该任务尚未完成，删除后将同时清除已下载的临时文件，且不可恢复。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(deleteLocal) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) { Text("删除") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EmptyDownloadState(modifier: Modifier = Modifier, filtered: Boolean = false) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (filtered) "该分类下暂无任务" else "暂无下载任务",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        if (!filtered) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "在「解析」页解析分享链接\n点击文件即可加入下载队列",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** 状态过滤条：全部 / 下载中 / 已下载 / 失败，等分四枚；选中态胶囊高亮 + 计数徽标 */
@Composable
private fun StatusFilterBar(
    filter: Int,
    onFilterChange: (Int) -> Unit,
    activeCount: Int,
    completedCount: Int,
    failedCount: Int
) {
    val options = listOf(
        Triple(0, "全部", tasksNoArgPlaceholder()),
        Triple(1, "下载中", activeCount),
        Triple(2, "已下载", completedCount),
        Triple(3, "失败", failedCount)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label, count) ->
            val selected = filter == value
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onFilterChange(value) },
                shape = RoundedCornerShape(10.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (count > 0) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 占位：过滤条「全部」项徽标恒为 0（隐藏），仅为 Triple 类型统一 */
private fun tasksNoArgPlaceholder(): Int = 0

/**
 * 顶部统计条 + 实时速度曲线：
 * - 统计条固定高度（不参与 weight 分配，绝不挤压下方任务列表）；
 * - 左「今日」/ 中「累计」/ 右「完成 N 个」，再右侧在有流量时显示全局总速度；
 * - 下方 Canvas 折线展示最近 60 个采样点（每 500ms 采样一次），纵轴按本窗口最大值归一。
 */
@Composable
private fun DownloadStatsHeader(
    statsTodayBytes: Long,
    statsTotalBytes: Long,
    statsTotalCount: Int,
    totalSpeed: Long
) {
    // LaunchedEffect(Unit) 只启动一次，协程体不会随重组重启；
    // 若直接闭包捕获 totalSpeed 会永远读到首帧的 0，必须用 rememberUpdatedState 读最新值。
    val latestSpeed by rememberUpdatedState(totalSpeed.toFloat())
    // 最近 60 个采样点（500ms × 60 = 30 秒滑动窗口）
    val samples = remember { mutableStateListOf<Float>() }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            samples.add(latestSpeed)
            // 只保留窗口内的最新 60 个点，避免列表无限增长
            while (samples.size > 60) samples.removeAt(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StatsLegendItem("今日", formatSize(statsTodayBytes))
            Spacer(modifier = Modifier.width(14.dp))
            StatsLegendItem("累计", formatSize(statsTotalBytes))
            Spacer(modifier = Modifier.width(14.dp))
            StatsLegendItem("完成", "$statsTotalCount 个")
            Spacer(modifier = Modifier.weight(1f))
            // 无流量时不占位显示 0 B/s，避免视觉噪声；用 weight 占住左侧空白保证布局稳定
            if (totalSpeed > 0) {
                Text(
                    text = "总速度 ${formatSpeed(totalSpeed)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
        SpeedSparkline(samples = samples, modifier = Modifier.fillMaxWidth().height(36.dp))
    }
}

/** 统计条单项：左侧彩色小圆点 + 标签 + 数值 */
@Composable
private fun StatsLegendItem(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(6.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary
        ) {}
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "$label $value",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * 实时速度折线（无坐标轴、无依赖的极简实现）：
 * - 纵轴按本窗口采样最大值归一（每条采样线 relative 比例即高度比例）；
 * - 全部采样 <= 0 时画一条居中基线，避免除零与空图。
 */
@Composable
private fun SpeedSparkline(samples: List<Float>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        if (samples.isEmpty()) return@Canvas
        // maxOrNull：samples 可能为空，isEmpty 已在上方提前返回，这里再兜一层
        val maxValue = samples.maxOrNull() ?: 0f
        if (maxValue <= 0f) {
            // 静止/无流量：画一条居中的基线，保持信息区高度稳定
            drawLine(
                color = baselineColor,
                start = androidx.compose.ui.geometry.Offset(0f, h / 2f),
                end = androidx.compose.ui.geometry.Offset(w, h / 2f),
                strokeWidth = 1.5.dp.toPx()
            )
            return@Canvas
        }
        // 顶部留 2dp 余量，避免峰值被画布边缘裁掉一半描边
        val topPadding = 2.dp.toPx()
        val usableHeight = (h - topPadding).coerceAtLeast(1f)
        val stepX = if (samples.size > 1) w / (samples.size - 1) else 0f
        val path = Path()
        samples.forEachIndexed { index, value ->
            val ratio = (value / maxValue).coerceIn(0f, 1f)
            val x = index * stepX
            val y = h - ratio * usableHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 1.5.dp.toPx()))
    }
}

/**
 * 任务设置弹窗（长按任务卡片打开）：优先级 + 单任务线程数。
 * 选择立即生效（调用方直接转发给 ViewModel）；下载中禁用线程档位，避免已下载分片失效。
 */
@Composable
private fun TaskSettingsDialog(
    task: DownloadTaskEntity,
    onDismiss: () -> Unit,
    onPriority: (Int) -> Unit,
    onThreads: (Int) -> Unit
) {
    // 下载中改线程数会使分片规划变化、已下载分片失效，因此禁用线程档位
    val downloading = task.status == DownloadTaskEntity.STATUS_DOWNLOADING
    val priorities = listOf(0 to "普通", 1 to "较高", 2 to "最高")
    val threadOptions = listOf(0, 8, 16, 32, 64, 128)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("任务设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 顶部：文件名（单行省略，长文件名不撑破弹窗）
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "选择立即生效",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ---------- 优先级 ----------
                Text(
                    text = "优先级",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                priorities.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPriority(value) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = task.priority == value,
                            onClick = { onPriority(value) }
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // ---------- 线程数 ----------
                Text(
                    text = "线程数",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (downloading) {
                    Text(
                        text = "下载中修改线程数会使已下载分片失效，请先暂停任务",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                threadOptions.forEach { value ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !downloading) { onThreads(value) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = task.threadOverride == value,
                            onClick = { onThreads(value) },
                            enabled = !downloading
                        )
                        Text(
                            text = if (value == 0) "跟随设置" else "$value 线程",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (downloading) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/**
 * 文件夹下载组：同一「顶级目录」下的所有任务合并为一个可展开卡片。
 * 收起时显示文件夹名 + 统计 + 总体进度；展开后显示子任务（含子文件夹内文件）紧凑列表。
 */
@Composable
private fun FolderDownloadGroup(
    folder: String,
    tasks: List<DownloadTaskEntity>,
    stats: Map<Long, DownloadStats>,
    onPause: (Long) -> Unit,
    onResume: (Long) -> Unit,
    onRemove: (DownloadTaskEntity) -> Unit,
    onPreview: (DownloadTaskEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }
    val completed = tasks.count { it.status == DownloadTaskEntity.STATUS_COMPLETED }
    val totalSize = tasks.sumOf { it.totalSize }
    // 聚合显示钳制：任何单项竞态残留都不会让"已下载 > 总大小"
    val downloaded = minOf(tasks.sumOf { it.downloadedSize }, totalSize)
    val fraction = if (totalSize > 0) {
        (downloaded.toFloat() / totalSize).coerceIn(0f, 1f)
    } else 0f
    val done = completed == tasks.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column {
            // 头部：点击展开/收起
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 文件夹图标（圆角方块，主色容器）
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${completed}/${tasks.size} 个文件 · ${formatSize(downloaded)} / ${formatSize(totalSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 总体进度徽标
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (done) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = if (done) "已完成" else "${(fraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (done) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 展开区：总体进度条 + 子任务紧凑列表
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(200), expandFrom = Alignment.Top),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150), shrinkTowards = Alignment.Top)
            ) {
                Column {
                    // 总体进度条（细条，圆角）
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    // 子任务列表（紧凑行，含子文件夹内文件）
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tasks.forEach { task ->
                            DownloadSubTaskRow(
                                task = task,
                                stats = stats[task.id],
                                onPause = { onPause(task.id) },
                                onResume = { onResume(task.id) },
                                onRemove = { onRemove(task) },
                                onPreview = { onPreview(task) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 文件夹组内子任务紧凑行：相对路径 + 状态 + 进度条 + 操作按钮。
 * 相比独立任务卡更轻量，适合嵌套在文件夹组内。
 */
@Composable
private fun DownloadSubTaskRow(
    task: DownloadTaskEntity,
    stats: DownloadStats?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDownloading = task.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
        task.status == DownloadTaskEntity.STATUS_PENDING
    val fraction = if (task.totalSize > 0) {
        (task.downloadedSize.toFloat() / task.totalSize).coerceIn(0f, 1f)
    } else 0f
    // 显示相对路径（去掉顶级目录前缀，如 "A/B/b.mp4" → "B/b.mp4"）
    val displayName = task.fileName.substringAfter('/')

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 文件小图标
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isDownloading && stats != null && stats.speed > 0) {
                            "${DownloadTaskEntity.statusText(task.status)} · ${formatSpeed(stats.speed)}"
                        } else {
                            taskStatusLine(task)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (task.status == DownloadTaskEntity.STATUS_FAILED) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                // 主操作（暂停/继续/重试/打开）
                when (task.status) {
                    DownloadTaskEntity.STATUS_DOWNLOADING,
                    DownloadTaskEntity.STATUS_PENDING -> IconButton(onClick = onPause, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                        Icon(
                            Icons.Outlined.Pause, contentDescription = "暂停",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(CARD_ACTION_ICON)
                        )
                    }
                    DownloadTaskEntity.STATUS_WAITING_WIFI -> IconButton(onClick = onResume, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                        Icon(
                            Icons.Outlined.WifiOff, contentDescription = "立即开始（忽略等待WiFi）",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(CARD_ACTION_ICON)
                        )
                    }
                    DownloadTaskEntity.STATUS_PAUSED -> IconButton(onClick = onResume, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                        Icon(
                            Icons.Outlined.PlayArrow, contentDescription = "继续",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(CARD_ACTION_ICON)
                        )
                    }
                    DownloadTaskEntity.STATUS_FAILED -> IconButton(onClick = onResume, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                        Icon(
                            Icons.Outlined.Refresh, contentDescription = "重试",
                            tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(CARD_ACTION_ICON)
                        )
                    }
                    DownloadTaskEntity.STATUS_COMPLETED -> {
                        // 文件可能已被用户手动删除：存在才显示预览/打开，否则提示已删除。
                        // ★ 探测必须离开主线程：fileExistsCheck 走 ContentResolver binder 查询，
                        //   若放在组合期（remember 的 calculation）同步执行，几十条完成任务会掉帧。
                        //   这里用可空状态：null=探测中（按「存在」渲染，避免按钮闪烁/误藏），
                        //   true=存在，false=确认已删除。
                        var fileExists by remember(task.id, task.status) { mutableStateOf<Boolean?>(null) }
                        LaunchedEffect(task.id, task.status) {
                            fileExists = withContext(Dispatchers.IO) {
                                fileExistsCheck(context, task.savePath)
                            }
                        }
                        // null（探测中）与 true 都按「文件在」渲染，只有明确 false 才显示已删除
                        if (fileExists != false) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 预览：仅支持类型（图片/视频/音频/文本/PDF）显示按钮，其余只有「打开」
                                if (supportsPreview(task.fileName)) {
                                    IconButton(onClick = onPreview, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                                        Icon(
                                            Icons.Outlined.Visibility, contentDescription = "预览",
                                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(CARD_ACTION_ICON)
                                        )
                                    }
                                }
                                // 兜底：用其他应用打开
                                IconButton(
                                    onClick = { openSavedFile(context, task.savePath) },
                                    modifier = Modifier.size(CARD_ACTION_SIZE)
                                ) {
                                    Icon(
                                        Icons.Outlined.OpenInNew, contentDescription = "打开",
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(CARD_ACTION_ICON)
                                    )
                                }
                            }
                        } else {
                            // 文件已被删除：仅提示（删除按钮在行尾常驻，可清理任务记录）。
                            // Text 用 weight 占满剩余宽度并限单行：无宽度约束时窄行会被逐字换行成竖排、撑高卡片
                            // ★ 这个 Row 是外层 Row 的非权重子项，而它内部又有 Text(weight(1f))：
                            //   若它自己不带 weight，会按「填满可用宽度」测量，把外层 Column(weight(1f))
                            //   挤成 0 宽 → 文件名/状态逐字竖排、卡片被撑得很高。故必须自带 weight(1f)。
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.FolderOff, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "此文件已被删除",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
                // 删除
                IconButton(onClick = onRemove, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                    Icon(
                        Icons.Outlined.Delete, contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(CARD_ACTION_ICON)
                    )
                }
            }
            // 细进度条
            LinearProgressIndicator(
                progress = { if (task.status == DownloadTaskEntity.STATUS_COMPLETED) 1f else fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5f)),
                color = if (task.status == DownloadTaskEntity.STATUS_FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }
}

/**
 * 卡片行尾操作按钮的统一尺寸。
 *
 * 此前同一个卡片里「暂停/预览/打开」是默认 48dp、「删除」是 32dp，而且包按钮的 Row 没有
 * 垂直居中（默认 Top）—— 于是小的那个按钮看起来「大小不一样、还比别的高一点」。
 * 现在全部用 36dp 触摸区 + 20dp 图标，并统一 CenterVertically。
 */
private val CARD_ACTION_SIZE = 36.dp
private val CARD_ACTION_ICON = 20.dp

/** 任务卡片：信息层次为「文件名 / 状态·进度 / 实时速度（进行中）」。长按打开任务设置弹窗。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    stats: DownloadStats?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    onPreview: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDownloading = task.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
        task.status == DownloadTaskEntity.STATUS_PENDING
    val fraction = if (task.totalSize > 0) {
        (task.downloadedSize.toFloat() / task.totalSize).coerceIn(0f, 1f)
    } else 0f
    // 第二行副标题：状态 · 进度% · 已下载/总大小（已完成用总体积代替 100% 与重复体积）
    val statusSubtitle = taskCardStatusLine(task)
    // 第三行（仅进行中且有实时统计时）：速度 · 剩余时间 · 线程数
    val liveStatsLine = if (isDownloading && stats != null && stats.speed > 0) {
        "${formatSpeed(stats.speed)} · 剩余 ${formatRemain(stats.remainMillis)} · ${stats.chunkCount} 线程"
    } else null

    Card(
        // onClick 留空：卡片原本无点击行为，这里只为长按提供「任务设置」入口。
        // combinedClickable 属 ExperimentalFoundationApi，@OptIn 加在本函数上（不依赖调用方注解）
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { /* 保持原有无点击行为，仅长按生效 */ },
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    // 第二行：状态 · 进度% · 已下载/总大小（见 taskCardStatusLine）
                    Text(
                        text = statusSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 第三行：进行中且有实时统计时显示 速度 · 剩余时间 · 线程数（无数据不占位）
                    if (liveStatsLine != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = liveStatsLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // 主操作按钮
                when (task.status) {
                    DownloadTaskEntity.STATUS_DOWNLOADING,
                    DownloadTaskEntity.STATUS_PENDING -> IconButton(onClick = onPause, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                        Icon(Icons.Outlined.Pause, contentDescription = "暂停", tint = MaterialTheme.colorScheme.primary)
                    }
                    DownloadTaskEntity.STATUS_WAITING_WIFI -> IconButton(onClick = onResume, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                        Icon(
                            Icons.Outlined.WifiOff, contentDescription = "立即开始（忽略等待WiFi）",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DownloadTaskEntity.STATUS_PAUSED -> IconButton(onClick = onResume, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = "继续", tint = MaterialTheme.colorScheme.primary)
                    }
                    DownloadTaskEntity.STATUS_FAILED -> IconButton(onClick = onResume, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "重试", tint = MaterialTheme.colorScheme.error)
                    }
                    DownloadTaskEntity.STATUS_COMPLETED -> {
                        // 文件可能已被用户手动删除：存在才显示预览/打开等按钮，否则提示已删除。
                        // ★ 探测必须离开主线程（与子任务行同款改法）：remember 的 calculation 在
                        //   组合期主线程同步跑 ContentResolver binder 查询，几十条完成任务会掉帧。
                        //   null=探测中（按「存在」渲染，避免按钮闪烁/误藏），true=存在，false=已删除。
                        var fileExists by remember(task.id, task.status) { mutableStateOf<Boolean?>(null) }
                        LaunchedEffect(task.id, task.status) {
                            fileExists = withContext(Dispatchers.IO) {
                                fileExistsCheck(context, task.savePath)
                            }
                        }
                        if (fileExists != false) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // APK 文件：额外显示「安装」按钮
                                if (task.fileName.endsWith(".apk", true)) {
                                    IconButton(
                                        onClick = { installApk(context, task.savePath, task.fileName) },
                                        modifier = Modifier.size(CARD_ACTION_SIZE)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.SystemUpdate,
                                            contentDescription = "安装",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                // 预览：仅支持类型（图片/视频/音频/文本/PDF）显示按钮，其余只有「打开」
                                if (supportsPreview(task.fileName)) {
                                    IconButton(onClick = onPreview, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                                        Icon(Icons.Outlined.Visibility, contentDescription = "预览", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                IconButton(
                                    onClick = { openSavedFile(context, task.savePath) },
                                    modifier = Modifier.size(CARD_ACTION_SIZE)
                                ) {
                                    Icon(Icons.Outlined.OpenInNew, contentDescription = "打开", tint = MaterialTheme.colorScheme.primary)
                                }
                                // 删除按钮：完成态右上角小按钮，error 红与其他操作色统一
                                IconButton(onClick = onRemove, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(CARD_ACTION_ICON)
                                    )
                                }
                            }
                        } else {
                            // 文件已被删除：仅提示 + 删除任务入口（清理废记录）。
                            // Text 用 weight 占满剩余宽度并限单行：无宽度约束时窄行会被逐字换行成竖排、撑高卡片
                            // ★ 同上：内层 Row 必须自带 weight(1f)，否则会吃掉整行宽度，
                            //   把外层 Column(weight(1f)) 挤成 0 宽（文字竖排、卡片异常变高）。
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.FolderOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "此文件已被删除",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 4.dp)
                                )
                                IconButton(onClick = onRemove, modifier = Modifier.size(CARD_ACTION_SIZE)) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "删除",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(CARD_ACTION_ICON)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 失败原因（红色小字展示具体错误）
            if (task.status == DownloadTaskEntity.STATUS_FAILED && task.errorMsg.isNotBlank()) {
                Text(
                    text = "失败原因：${task.errorMsg}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 实时统计行已上移到信息区第三行（速度 · 剩余时间 · 线程数），此处不再重复渲染

            // 进度条
            // 进度条：完成/失败态无跟踪意义，隐藏（避免完成卡片始终满格的视觉冗余）
            if (task.status != DownloadTaskEntity.STATUS_COMPLETED &&
                task.status != DownloadTaskEntity.STATUS_FAILED
            ) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // 完成态：体积已合并进副标题、删除已移至右上角，底部整行省略（卡片更紧凑）
            if (task.status != DownloadTaskEntity.STATUS_COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = progressText(task),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

/**
 * 检查任务保存路径是否真实存在：
 * - 纯路径 / file:// → File.exists() 判定；
 * - content://（SAF 自定义目录 / MediaStore）→ 元数据查询，只取一行、不读文件内容。
 * ★ 不读流是关键：拿「能不能打开输入流」当存在性判定有两个坑——
 *   ① 上一版写成 openInputStream(...)?.use { null } ?: false，成功也被 use{} 吃掉，
 *      表达式恒 null → 恒判「不存在」，刚下完的文件必然显示已删除；
 *   ② 即便写对，它也要真的建立文件流，且必须放在 IO 线程（调用点已 remember 只探测一次）。
 *   元数据查询只走 binder 拿一行游标，代价最小，也不会因文件正在被写而失败。
 * 底层描述符兜底只做 openFileDescriptor，不读取任何字节。
 */
private fun fileExistsCheck(context: android.content.Context, savePath: String): Boolean {
    if (savePath.isBlank()) return false
    if (!savePath.startsWith("content://")) return File(savePath).exists()
    val uri = Uri.parse(savePath)
    val resolver = context.contentResolver
    // ① 元数据查询：SAF 文档用 COLUMN_DOCUMENT_ID，MediaStore 等其他 provider 用 _id
    val projection = if (DocumentsContract.isDocumentUri(context, uri)) {
        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
    } else {
        arrayOf("_id")
    }
    val byMetadata = runCatching {
        resolver.query(uri, projection, null, null, null)?.use { it.moveToFirst() }
    }.getOrNull()
    if (byMetadata != null) return byMetadata
    // ② 元数据不可用（个别 ROM 不支持该 projection / 返回空游标）→ 仅打开描述符探测，不读字节
    return runCatching {
        resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    }.getOrDefault(false)
}

/** 状态过滤：0=全部 1=下载中（含暂停/等待WiFi，语义：还没下完） 2=已下载 3=失败 */
private fun filterTaskByStatus(tasks: List<DownloadTaskEntity>, filter: Int): List<DownloadTaskEntity> =
    when (filter) {
        1 -> tasks.filter {
            it.status == DownloadTaskEntity.STATUS_DOWNLOADING ||
                it.status == DownloadTaskEntity.STATUS_PENDING ||
                it.status == DownloadTaskEntity.STATUS_PAUSED ||
                it.status == DownloadTaskEntity.STATUS_WAITING_WIFI
        }
        2 -> tasks.filter { it.status == DownloadTaskEntity.STATUS_COMPLETED }
        3 -> tasks.filter { it.status == DownloadTaskEntity.STATUS_FAILED }
        else -> tasks
    }

private fun taskStatusLine(task: DownloadTaskEntity): String {
    val status = DownloadTaskEntity.statusText(task.status)
    return if (task.totalSize > 0) {
        // 显示值钳制到 total（防恢复竞态残留导致显示超总大小）
        val shown = minOf(task.downloadedSize, task.totalSize)
        "$status · ${formatSize(shown)} / ${formatSize(task.totalSize)}"
    } else {
        status
    }
}

private fun progressText(task: DownloadTaskEntity): String {
    if (task.totalSize <= 0) return ""
    // 显示值钳制到 total（防恢复竞态残留导致显示超总大小）
    val shown = minOf(task.downloadedSize, task.totalSize)
    val percent = (shown * 100 / task.totalSize).toInt().coerceIn(0, 100)
    return "已下载 ${formatSize(shown)} / ${formatSize(task.totalSize)} · $percent%"
}

/**
 * 任务卡片第二行文案：状态 · 进度% · 已下载/总大小。
 * - 已完成：体积即总大小，省略 100% 与重复的「已下载/总大小」双写；
 * - 总大小未知：退化为仅状态，避免出现无意义的 0%；
 * - 其余状态：状态 · 百分比 · 已下载/总大小（显示值钳制到 total）。
 */
private fun taskCardStatusLine(task: DownloadTaskEntity): String {
    val status = DownloadTaskEntity.statusText(task.status)
    if (task.status == DownloadTaskEntity.STATUS_COMPLETED) {
        return if (task.totalSize > 0) "$status · ${formatSize(task.totalSize)}" else status
    }
    if (task.totalSize <= 0) return status
    val shown = minOf(task.downloadedSize, task.totalSize)
    val percent = (shown * 100 / task.totalSize).toInt().coerceIn(0, 100)
    return "$status · $percent% · ${formatSize(shown)} / ${formatSize(task.totalSize)}"
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return String.format("%.1f %s", value, units[i])
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var value = bytesPerSec.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return String.format("%.1f %s", value, units[i])
}

private fun formatRemain(millis: Long): String {
    if (millis < 0) return "计算中"
    val sec = millis / 1000
    return when {
        sec < 60 -> "${sec}秒"
        sec < 3600 -> "${sec / 60}分${sec % 60}秒"
        else -> "${sec / 3600}时${(sec % 3600) / 60}分"
    }
}

private fun openSavedFile(context: android.content.Context, savePath: String) {
    if (savePath.isBlank()) return
    val uri = if (savePath.startsWith("content://")) {
        Uri.parse(savePath)
    } else {
        // Android 7.0+ 禁止暴露 file:// URI，必须经 FileProvider 转 content://
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(savePath))
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    }.onFailure {
        SnackbarController.show("无法打开该文件")
    }
}

/** 安装 APK：检查「安装未知来源应用」权限（Android 8+），ACTION_VIEW 调起系统安装器 */
private fun installApk(context: android.content.Context, savePath: String, fileName: String) {
    if (savePath.isBlank()) {
        SnackbarController.show("文件不存在")
        return
    }
    // Android 8+：需先授予「安装未知来源应用」
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        !context.packageManager.canRequestPackageInstalls()
    ) {
        SnackbarController.show("请先允许安装未知来源应用")
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )
        runCatching { context.startActivity(intent) }.onFailure {
            SnackbarController.show("无法打开设置")
        }
        return
    }
    val uri = if (savePath.startsWith("content://")) {
        Uri.parse(savePath)
    } else {
        // Android 7.0+ 禁止暴露 file:// URI，必须经 FileProvider 转 content://
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(savePath))
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "安装应用"))
    }.onFailure {
        SnackbarController.show("无法打开安装器")
    }
}