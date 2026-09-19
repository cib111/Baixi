package com.baixi.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import android.webkit.MimeTypeMap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.baixi.app.data.db.DownloadTaskEntity
import com.baixi.app.ui.SnackbarController
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 已下载文件管理页：搜索 / 类型筛选 / 缩略图 / 打开 / 分享 / 重命名 / 批量删除。
 *
 * 宿主只传「已完成且 savePath 非空」的任务；写数据库与删除本地文件均由宿主回调负责，
 * 本文件不做导航、不碰数据库，也不依赖 DownloadManager / DownloadSaver。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedFilesScreen(
    tasks: List<DownloadTaskEntity>,
    onBack: () -> Unit,
    onRename: (DownloadTaskEntity, String) -> Unit,
    onDelete: (List<DownloadTaskEntity>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // 过滤态：搜索关键字 / 类型分组；多选集合用 id 记录，跨重组稳定
    var query by rememberSaveable { mutableStateOf("") }
    var typeKey by rememberSaveable { mutableStateOf(TYPE_ALL) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    // 排序态：默认按时间倒序（最新在前）；再点同一项切换升降序
    var sortKey by rememberSaveable { mutableStateOf(SORT_TIME) }
    var sortAsc by rememberSaveable { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DownloadTaskEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<List<DownloadTaskEntity>>(emptyList()) }

    val multiSelect = selectedIds.isNotEmpty()

    // 系统返回键：多选态先退出多选；否则返回上一页（此前缺 BackHandler → 直接退出整个应用）
    BackHandler {
        if (multiSelect) selectedIds = emptySet() else onBack()
    }

    // 类型 + 关键字过滤，再按所选方式排序（默认时间倒序 = 最新在前）
    val visible = remember(tasks, query, typeKey, sortKey, sortAsc) {
        val keyword = query.trim().lowercase(Locale.ROOT)
        val base = tasks
            .filter { it.savePath.isNotBlank() }
            .filter { typeKey == TYPE_ALL || typeGroupOf(it.fileName) == typeKey }
            .filter { keyword.isEmpty() || it.fileName.lowercase(Locale.ROOT).contains(keyword) }
        val sorted = when (sortKey) {
            SORT_NAME -> base.sortedBy { it.fileName.substringAfterLast('/').lowercase(Locale.ROOT) }
            SORT_SIZE -> base.sortedBy { if (it.totalSize > 0) it.totalSize else it.downloadedSize }
            else -> base.sortedBy { it.createTime }
        }
        if (sortAsc) sorted else sorted.reversed()
    }

    // 选中项随过滤结果收敛：被搜索/筛选隐藏的项自动移出选中集合
    LaunchedEffect(visible) {
        if (selectedIds.isNotEmpty()) {
            val alive = visible.mapTo(HashSet()) { it.id }
            val pruned = selectedIds.filterTo(HashSet()) { it in alive }
            if (pruned.size != selectedIds.size) selectedIds = pruned
        }
    }

    // ★ 全屏覆盖层必须自带不透明背景：否则会「浮」在下层页面上、下层内容透出来
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = if (multiSelect) "已选择 ${selectedIds.size} 项" else "已下载",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 排序：时间 / 名称 / 大小；再次点同一项切换升/降序
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Outlined.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                            listOf(
                                SORT_TIME to "按时间",
                                SORT_NAME to "按名称",
                                SORT_SIZE to "按大小"
                            ).forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = label + if (sortKey == key) {
                                                if (sortAsc) "  ↑" else "  ↓"
                                            } else {
                                                ""
                                            }
                                        )
                                    },
                                    onClick = {
                                        if (sortKey == key) {
                                            sortAsc = !sortAsc
                                        } else {
                                            sortKey = key
                                            sortAsc = false
                                        }
                                        sortMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    // 多选总开关：有选中即处于多选态，点右上角按钮统一退出
                    IconButton(onClick = { selectedIds = emptySet() }) {
                        Icon(
                            imageVector = if (multiSelect) Icons.Outlined.Close else Icons.Outlined.Check,
                            contentDescription = if (multiSelect) "退出多选" else "多选（长按列表项进入）"
                        )
                    }
                }
            )

            // 搜索：按文件名模糊过滤（大小写不敏感）
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text("搜索文件名") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Close, contentDescription = "清除")
                        }
                    }
                }
            )

            // 类型筛选：横向滚动 Chip 行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TYPE_GROUPS.forEach { (key, label) ->
                    FilterChip(
                        selected = typeKey == key,
                        onClick = { typeKey = key },
                        label = { Text(label) }
                    )
                }
            }

            if (visible.isEmpty()) {
                EmptyDownloadedState(
                    // 有任务但被过滤掉 → 提示换条件；确实没有任务 → 提示还没有已下载的文件
                    filtered = tasks.any { it.savePath.isNotBlank() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 6.dp,
                        bottom = if (multiSelect) 96.dp else 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(visible, key = { it.id }) { task ->
                        DownloadedFileRow(
                            task = task,
                            multiSelect = multiSelect,
                            checked = task.id in selectedIds,
                            onOpen = { openDownloadedFile(context, task.savePath) },
                            onShare = { shareDownloadedFile(context, task) },
                            onRename = { renameTarget = task },
                            onDelete = { pendingDelete = listOf(task) },
                            onToggleSelect = {
                                selectedIds = if (task.id in selectedIds) {
                                    selectedIds - task.id
                                } else {
                                    selectedIds + task.id
                                }
                            }
                        )
                    }
                }
            }
        }

        // 多选底部操作栏：全选 / 分享（仅单选）/ 删除
        if (multiSelect) {
            val allSelected = visible.isNotEmpty() && visible.all { it.id in selectedIds }
            MultiSelectActionBar(
                allSelected = allSelected,
                canShare = selectedIds.size == 1,
                onToggleAll = {
                    selectedIds = if (allSelected) emptySet() else visible.mapTo(HashSet()) { it.id }
                },
                onShare = {
                    tasks.firstOrNull { it.id in selectedIds }?.let { shareDownloadedFile(context, it) }
                },
                onDelete = { pendingDelete = tasks.filter { it.id in selectedIds } },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // 重命名弹窗：本地改名成功才回调宿主写库
    renameTarget?.let { task ->
        RenameDialog(
            task = task,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                if (renameDownloadedFile(context, task, newName)) onRename(task, newName)
            }
        )
    }

    // 删除二次确认（记录 + 本地文件由宿主 onDelete 完成）
    if (pendingDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDelete = emptyList() },
            title = { Text("删除文件") },
            text = {
                Text(
                    if (pendingDelete.size == 1) {
                        "确定删除「${pendingDelete.first().fileName.substringAfterLast('/')}」吗？\n" +
                            "记录与本地文件都会被删除，且不可恢复。"
                    } else {
                        "确定删除选中的 ${pendingDelete.size} 个文件吗？\n" +
                            "记录与本地文件都会被删除，且不可恢复。"
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targets = pendingDelete
                        pendingDelete = emptyList()
                        selectedIds = emptySet()
                        onDelete(targets)
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = emptyList() }) { Text("取消") }
            }
        )
    }
}

/** 空状态：无文件 →「还没有已下载的文件」；被过滤 → 提示换条件 */
@Composable
private fun EmptyDownloadedState(filtered: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
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
                    imageVector = if (filtered) Icons.Outlined.Search else Icons.Outlined.FolderOpen,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (filtered) "没有符合条件的文件" else "还没有已下载的文件",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
        if (filtered) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "换个关键字或类型试试",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 单行：缩略图 + 文件名（1 行省略）+ 副标题；单击打开、长按进入多选 */
@Composable
private fun DownloadedFileRow(
    task: DownloadTaskEntity,
    multiSelect: Boolean,
    checked: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onToggleSelect: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val displayName = task.fileName.substringAfterLast('/')

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            // 单击/长按统一用 detectTapGestures，避免 clickable 与长按手势叠加
            .pointerInput(task.id, multiSelect) {
                detectTapGestures(
                    onTap = { if (multiSelect) onToggleSelect() else onOpen() },
                    onLongPress = { if (!multiSelect) onToggleSelect() }
                )
            },
        shape = RoundedCornerShape(14.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (multiSelect) {
                Checkbox(checked = checked, onCheckedChange = { onToggleSelect() })
                Spacer(modifier = Modifier.width(4.dp))
            }

            FileThumbnail(task = task)
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatSize(if (task.totalSize > 0) task.totalSize else task.downloadedSize) +
                        " · " + formatTime(task.createTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 行尾操作：分享 + 溢出菜单（打开/重命名）；多选态下隐藏，避免语义冲突
            if (!multiSelect) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "分享",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "更多操作",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("打开") },
                            onClick = {
                                menuOpen = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 多选底部操作栏：全选/取消全选、分享（仅单选可用）、删除 */
@Composable
private fun MultiSelectActionBar(
    allSelected: Boolean,
    canShare: Boolean,
    onToggleAll: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onToggleAll) {
                Icon(
                    imageVector = if (allSelected) Icons.Outlined.Close else Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (allSelected) "取消全选" else "全选")
            }
            TextButton(onClick = onShare, enabled = canShare) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("分享")
            }
            TextButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 重命名弹窗：校验非空且不含 '/'，确认后交给宿主写库 */
@Composable
private fun RenameDialog(
    task: DownloadTaskEntity,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val oldName = task.fileName.substringAfterLast('/')
    var input by remember(task.id) { mutableStateOf(oldName) }
    val trimmed = input.trim()
    val invalid = trimmed.isEmpty() || trimmed.contains('/')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = invalid && input.isNotEmpty(),
                label = { Text("文件名") },
                supportingText = {
                    Text(
                        if (invalid && input.isNotEmpty()) "文件名不能为空且不能包含 /"
                        else "含扩展名，例如 photo.jpg"
                    )
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = !invalid) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 缩略图：图片/视频异步加载（IO 线程 + 内存缓存 + 失败记忆），其余类型直接显示类型图标。
 * 未加载完成 / 加载失败都回退类型图标，不会留白，也不会随滚动反复重试。
 */
@Composable
private fun FileThumbnail(task: DownloadTaskEntity) {
    val context = LocalContext.current
    val groupKey = typeGroupOf(task.fileName)
    // 图片/视频才尝试缩略图，其余类型不产生任何加载开销
    val thumbKey = if (groupKey == TYPE_IMAGE || groupKey == TYPE_VIDEO) {
        "${task.id}|${task.savePath}"
    } else {
        null
    }

    var bitmap by remember(task.id) { mutableStateOf(thumbKey?.let { ThumbnailCache.get(it) }) }
    var failed by remember(task.id) { mutableStateOf(thumbKey != null && ThumbnailCache.isFailed(thumbKey)) }

    LaunchedEffect(task.id, task.savePath) {
        if (thumbKey == null || bitmap != null || failed) return@LaunchedEffect
        val loaded: ImageBitmap? = withContext(Dispatchers.IO) {
            runCatching {
                loadThumbnailSync(context, task.savePath, isVideo = groupKey == TYPE_VIDEO)
            }.getOrNull()
        }
        if (loaded != null) {
            ThumbnailCache.put(thumbKey, loaded)
            bitmap = loaded
        } else {
            ThumbnailCache.markFailed(thumbKey)
            failed = true
        }
    }

    Surface(
        modifier = Modifier.size(60.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Box(contentAlignment = Alignment.Center) {
            val shown = bitmap
            if (shown != null) {
                Image(
                    bitmap = shown,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = typeIconOf(groupKey),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = typeTintOf(groupKey)
                )
            }
        }
    }
}

/** 缩略图内存缓存（约 16MB 位图上限）+ 失败 key 记忆，避免滚动抖动与重复解码 */
private object ThumbnailCache {
    private val cache = LruCache<String, ImageBitmap>(16 * 1024 * 1024)
    private val failedKeys = HashSet<String>()

    fun get(key: String): ImageBitmap? = runCatching { cache.get(key) }.getOrNull()

    fun put(key: String, bitmap: ImageBitmap) {
        runCatching {
            failedKeys.remove(key)
            cache.put(key, bitmap)
        }
    }

    fun isFailed(key: String): Boolean = runCatching { failedKeys.contains(key) }.getOrDefault(false)

    fun markFailed(key: String) {
        runCatching {
            if (failedKeys.size > 512) failedKeys.clear()
            failedKeys.add(key)
        }
    }
}

/** 同步加载缩略图（调用方保证在 IO 线程），任何异常都返回 null 由 UI 回退图标 */
private fun loadThumbnailSync(context: Context, savePath: String, isVideo: Boolean): ImageBitmap? {
    if (savePath.isBlank()) return null
    val bitmap = if (savePath.startsWith("content://")) {
        loadThumbnailFromUri(context, Uri.parse(savePath), isVideo)
    } else {
        loadThumbnailFromFile(File(savePath), isVideo)
    }
    return bitmap?.asImageBitmap()
}

/** 绝对路径：API 29+ 用 ThumbnailUtils 新接口；API 23~28 降采样 / createVideoThumbnail(path) */
private fun loadThumbnailFromFile(file: File, isVideo: Boolean): Bitmap? {
    if (!file.exists()) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val size = Size(THUMB_SIZE_PX, THUMB_SIZE_PX)
        return if (isVideo) {
            runCatching { ThumbnailUtils.createVideoThumbnail(file, size, null) }.getOrNull()
        } else {
            runCatching { ThumbnailUtils.createImageThumbnail(file, size, null) }.getOrNull()
        }
    }
    // API 23~28
    if (isVideo) {
        @Suppress("DEPRECATION")
        return runCatching {
            ThumbnailUtils.createVideoThumbnail(file.absolutePath, MediaStore.Video.Thumbnails.MINI_KIND)
        }.getOrNull()
    }
    return runCatching { decodeSampledFile(file.absolutePath, THUMB_SIZE_PX) }.getOrNull()
}

/** content://：API 29+ 优先 loadThumbnail；低版本按类型兜底（视频取帧 / 图片降采样） */
private fun loadThumbnailFromUri(context: Context, uri: Uri, isVideo: Boolean): Bitmap? {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val loaded = runCatching {
            resolver.loadThumbnail(uri, Size(THUMB_SIZE_PX, THUMB_SIZE_PX), null)
        }.getOrNull()
        if (loaded != null) return loaded
    }
    if (isVideo) {
        return runCatching { extractVideoFrame(context, uri) }.getOrNull()
    }
    // SAF 文档/普通文件流：openInputStream 降采样
    val byStream = runCatching {
        resolver.openInputStream(uri)?.use { decodeSampledStream(it, THUMB_SIZE_PX) }
    }.getOrNull()
    if (byStream != null) return byStream
    // MediaStore 图片可能不支持 openInputStream：兜底旧接口
    @Suppress("DEPRECATION")
    return runCatching { MediaStore.Images.Media.getBitmap(resolver, uri) }.getOrNull()
}

/** 视频取帧：API 29+ 用 (Context, Uri)；低版本 SAF 文档走文件描述符，其余用 (Context, Uri) */
private fun extractVideoFrame(context: Context, uri: Uri): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            retriever.setDataSource(context, uri)
        } else if (DocumentsContract.isDocumentUri(context, uri)) {
            // 低版本 createVideoThumbnail 只认文件路径，SAF 文档只能走 retriever 描述符
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
            }
        } else {
            retriever.setDataSource(context, uri)
        }
        retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } finally {
        runCatching { retriever.release() }
    }
}

/** 文件降采样解码（按目标边长算 inSampleSize） */
private fun decodeSampledFile(path: String, target: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val sample = sampleSizeOf(bounds, target) ?: return null
    return runCatching {
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}

/** 流降采样解码：流不可重复读，先读入字节数组再解码 */
private fun decodeSampledStream(input: InputStream, target: Int): Bitmap? {
    val bytes = runCatching { input.readBytes() }.getOrNull() ?: return null
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val sample = sampleSizeOf(bounds, target) ?: return null
    return runCatching {
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }.getOrNull()
}

/** 目标边长 → inSampleSize；尺寸非法返回 null */
private fun sampleSizeOf(bounds: BitmapFactory.Options, target: Int): Int? {
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
        sample *= 2
    }
    return sample
}

/** 用系统应用打开：content:// 直接使用，绝对路径经 FileProvider 转换 */
private fun openDownloadedFile(context: Context, savePath: String) {
    val uri = uriOf(context, savePath)
    if (uri == null) {
        SnackbarController.show("文件不存在")
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeOf(savePath))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(intent) }.isFailure) {
        SnackbarController.show("无法打开该文件")
    }
}

/** 分享单个文件：ACTION_SEND + EXTRA_STREAM + 读权限（多文件分享系统支持不一，只做单选） */
private fun shareDownloadedFile(context: Context, task: DownloadTaskEntity) {
    val uri = uriOf(context, task.savePath)
    if (uri == null) {
        SnackbarController.show("文件不存在")
        return
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mimeOf(task.fileName)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "分享文件").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (runCatching { context.startActivity(chooser) }.isFailure) {
        SnackbarController.show("分享失败")
    }
}

/**
 * 重命名：SAF 文档用 DocumentsContract.renameDocument，绝对路径用 File.renameTo。
 * 返回 true 表示本地已改名，宿主可写库；false 时已提示过，调用方不要调用 onRename。
 */
private fun renameDownloadedFile(context: Context, task: DownloadTaskEntity, newName: String): Boolean {
    if (newName.isBlank() || newName.contains('/')) {
        SnackbarController.show("文件名不合法")
        return false
    }
    if (task.savePath.startsWith("content://")) {
        val uri = Uri.parse(task.savePath)
        if (!DocumentsContract.isDocumentUri(context, uri)) {
            // MediaStore 等 provider 没有通用改名接口，避免误改
            SnackbarController.show("该位置不支持重命名")
            return false
        }
        val renamed = runCatching {
            DocumentsContract.renameDocument(context.contentResolver, uri, newName)
        }.getOrNull()
        if (renamed == null) {
            SnackbarController.show("重命名失败")
            return false
        }
        return true
    }

    val source = File(task.savePath)
    val parent = source.parentFile
    if (!source.exists() || parent == null) {
        SnackbarController.show("重命名失败")
        return false
    }
    val target = File(parent, newName)
    if (target.absolutePath == source.absolutePath) return true
    if (target.exists()) {
        SnackbarController.show("已存在同名文件")
        return false
    }
    if (!runCatching { source.renameTo(target) }.getOrDefault(false)) {
        SnackbarController.show("重命名失败")
        return false
    }
    return true
}

/** savePath → 其他应用可读的 content:// URI；失败返回 null */
private fun uriOf(context: Context, savePath: String): Uri? {
    if (savePath.isBlank()) return null
    if (savePath.startsWith("content://")) return runCatching { Uri.parse(savePath) }.getOrNull()
    return runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(savePath))
    }.getOrNull()
}

/** 扩展名 → MIME；apk 单独处理（MimeTypeMap 不返回安装包 MIME） */
private fun mimeOf(name: String): String {
    val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (ext == "apk") return "application/vnd.android.package-archive"
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

/** 大小格式化（与下载页展示口径一致） */
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.size - 1) {
        value /= 1024
        i++
    }
    return String.format(Locale.US, "%.1f %s", value, units[i])
}

/** 保存时间格式化（SimpleDateFormat 实例按文件缓存，列表滚动不重复创建） */
private val timeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

private fun formatTime(millis: Long): String =
    runCatching { timeFormatter.format(Date(millis)) }.getOrDefault("-")

/** 按扩展名分组 */
private fun typeGroupOf(fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (ext) {
        in EXT_IMAGE -> TYPE_IMAGE
        in EXT_VIDEO -> TYPE_VIDEO
        in EXT_AUDIO -> TYPE_AUDIO
        in EXT_DOC -> TYPE_DOC
        in EXT_ARCHIVE -> TYPE_ARCHIVE
        else -> TYPE_OTHER
    }
}

/** 类型图标（均为 material-icons-extended 的 Outlined 图标） */
private fun typeIconOf(group: String): ImageVector = when (group) {
    TYPE_IMAGE -> Icons.Outlined.Image
    TYPE_VIDEO -> Icons.Outlined.VideoFile
    TYPE_AUDIO -> Icons.Outlined.Audiotrack
    TYPE_DOC -> Icons.Outlined.Description
    TYPE_ARCHIVE -> Icons.Outlined.FolderZip
    else -> Icons.Outlined.InsertDriveFile
}

/** 类型图标着色：仅用于类型区分 */
private fun typeTintOf(group: String): Color = when (group) {
    TYPE_IMAGE -> Color(0xFF3F7BE0)
    TYPE_VIDEO -> Color(0xFF8E44AD)
    TYPE_AUDIO -> Color(0xFF1E9E6A)
    TYPE_DOC -> Color(0xFFD98324)
    TYPE_ARCHIVE -> Color(0xFF9A6B2F)
    else -> Color(0xFF6B7280)
}

// ===== 类型常量与扩展名表 =====

private const val TYPE_ALL = "all"

/** 排序方式：时间（默认，最新在前）/ 名称 / 大小 */
private const val SORT_TIME = 0
private const val SORT_NAME = 1
private const val SORT_SIZE = 2
private const val TYPE_IMAGE = "image"
private const val TYPE_VIDEO = "video"
private const val TYPE_AUDIO = "audio"
private const val TYPE_DOC = "doc"
private const val TYPE_ARCHIVE = "archive"
private const val TYPE_OTHER = "other"

/** 缩略图目标边长（px） */
private const val THUMB_SIZE_PX = 160

private val EXT_IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
private val EXT_VIDEO = setOf("mp4", "mkv", "avi", "mov", "flv", "webm", "ts")
private val EXT_AUDIO = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg")
private val EXT_DOC = setOf("pdf", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "epub")
private val EXT_ARCHIVE = setOf("zip", "rar", "7z", "tar", "gz")

/** 筛选 Chip：全部 / 图片 / 视频 / 音频 / 文档 / 压缩包 / 其它 */
private val TYPE_GROUPS: List<Pair<String, String>> = listOf(
    TYPE_ALL to "全部",
    TYPE_IMAGE to "图片",
    TYPE_VIDEO to "视频",
    TYPE_AUDIO to "音频",
    TYPE_DOC to "文档",
    TYPE_ARCHIVE to "压缩包",
    TYPE_OTHER to "其它"
)
