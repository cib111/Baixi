package com.baixi.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale

/** 排序维度：名称 */
const val SORT_NAME = 0

/** 排序维度：大小 */
const val SORT_SIZE = 1

/** 排序维度：时间 */
const val SORT_TIME = 2

/**
 * 网盘/分享列表的搜索 + 排序工具栏（紧凑一行）：
 * - 左侧：单行搜索框（无前置图标，清空按钮在有内容时出现），只做本地过滤，不发任何网络请求
 * - 右侧：排序方式下拉菜单（名称/大小/时间）+ 升降序切换按钮
 *
 * 组件只承载 UI 与状态回调，真正的过滤/排序由纯函数 [filterAndSortFiles] 完成，
 * 便于各页面在 remember(...) 中调用，且原始文件列表不被修改。
 */
@Composable
fun CloudListToolbar(
    query: String,
    onQueryChange: (String) -> Unit,
    sortMode: Int,
    ascending: Boolean,
    onSortModeChange: (Int) -> Unit,
    onAscendingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // 排序方式菜单展开状态（仅本组件内部使用）
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 搜索框：单行、占位文字提示，有内容时显示清空按钮（不使用前置图标以保持紧凑）
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            placeholder = {
                Text(
                    text = "搜索文件",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
            },
            textStyle = MaterialTheme.typography.bodyMedium,
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "清空搜索",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                null
            },
            shape = MaterialTheme.shapes.large
        )

        // 排序方式入口：名称 / 大小 / 时间（当前项显示勾选）
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "排序方式",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                SortModeMenuItem(
                    label = "名称",
                    mode = SORT_NAME,
                    currentMode = sortMode,
                    onClick = {
                        onSortModeChange(SORT_NAME)
                        menuExpanded = false
                    }
                )
                SortModeMenuItem(
                    label = "大小",
                    mode = SORT_SIZE,
                    currentMode = sortMode,
                    onClick = {
                        onSortModeChange(SORT_SIZE)
                        menuExpanded = false
                    }
                )
                SortModeMenuItem(
                    label = "时间",
                    mode = SORT_TIME,
                    currentMode = sortMode,
                    onClick = {
                        onSortModeChange(SORT_TIME)
                        menuExpanded = false
                    }
                )
            }
        }

        // 升降序切换：升序箭头朝上，降序把同一箭头旋转 180°
        IconButton(onClick = { onAscendingChange(!ascending) }) {
            Icon(
                imageVector = Icons.Outlined.ArrowUpward,
                contentDescription = if (ascending) "升序排列" else "降序排列",
                modifier = Modifier.rotate(if (ascending) 0f else 180f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 排序菜单单项：当前选中维度显示勾选图标。 */
@Composable
private fun SortModeMenuItem(
    label: String,
    mode: Int,
    currentMode: Int,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        onClick = onClick,
        trailingIcon = {
            if (currentMode == mode) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    )
}

/**
 * 本地过滤 + 排序（纯函数，无副作用，不修改入参列表）：
 * 1. query 为空（或过滤后仅空白）时不筛选；
 * 2. 名称模糊匹配：忽略大小写、忽略空白字符；
 * 3. isDirOf 非空时，文件夹恒排在文件之前（同组内再按维度排序；为 null 时跳过该规则）；
 * 4. 按 sortMode 选择比较维度，ascending 控制升/降序；比较值相同时保持原顺序（sortedWith 为稳定排序）。
 */
fun <T> filterAndSortFiles(
    items: List<T>,
    query: String,
    sortMode: Int,
    ascending: Boolean,
    nameOf: (T) -> String,
    sizeOf: (T) -> Long,
    timeOf: (T) -> Long,
    isDirOf: ((T) -> Boolean)? = null
): List<T> {
    // 关键字归一化：去首尾空白 + 去中间所有空白 + 转小写
    val keyword = normalizeFileName(query)
    val filtered = if (keyword.isEmpty()) {
        items
    } else {
        items.filter { normalizeFileName(nameOf(it)).contains(keyword) }
    }

    val comparator = Comparator<T> { a, b ->
        val dirA = isDirOf?.invoke(a) == true
        val dirB = isDirOf?.invoke(b) == true
        when {
            // 文件夹优先（isDirOf 为 null 时两者恒为 false，自然跳过该规则）
            dirA != dirB -> if (dirA) -1 else 1
            else -> {
                val raw = when (sortMode) {
                    SORT_SIZE -> sizeOf(a).compareTo(sizeOf(b))
                    SORT_TIME -> timeOf(a).compareTo(timeOf(b))
                    else -> normalizeFileName(nameOf(a)).compareTo(normalizeFileName(nameOf(b)))
                }
                if (ascending) raw else -raw
            }
        }
    }
    return filtered.sortedWith(comparator)
}

/** 名称归一化：去掉全部空白（含制表符/全角空格）并统一小写，用于模糊匹配与名称排序。 */
private fun normalizeFileName(raw: String): String =
    raw.filterNot { it.isWhitespace() }.lowercase(Locale.ROOT)

/**
 * 文件时间解析为毫秒时间戳（纯函数）：
 * - 纯数字：按 Unix 秒处理（*1000）；若本身已是毫秒级（>= 1e10）则原样返回，容错各平台；
 * - 其余用 SimpleDateFormat 依次尝试 ISO / 常规 / 纯日期三种格式（不使用 java.time，规避 minSdk 23 脱糖问题）；
 * - 解析失败返回 0，保证排序不抛异常。
 */
fun parseFileTime(raw: String): Long {
    val text = raw.trim()
    if (text.isEmpty()) return 0L
    // 百度/夸克/UC 等返回 Unix 秒字符串
    text.toLongOrNull()?.let { value ->
        return if (value >= 10_000_000_000L) value else value * 1000L
    }
    // 123/迅雷/139 等返回 ISO 字符串（可能带毫秒/时区后缀，SimpleDateFormat 只解析模式匹配到的前缀）
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (pattern in patterns) {
        try {
            val format = SimpleDateFormat(pattern, Locale.getDefault())
            format.isLenient = false
            val date = format.parse(text)
            if (date != null) return date.time
        } catch (_: Exception) {
            // 该格式不匹配，继续尝试下一种
        }
    }
    return 0L
}
