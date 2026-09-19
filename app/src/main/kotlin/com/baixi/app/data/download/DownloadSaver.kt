package com.baixi.app.data.download

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * 完成文件保存到公共 Download 目录：
 * - Android 10+（Q）：MediaStore.Downloads，无需存储权限；
 * - Android 9-：Environment.getExternalStoragePublicDirectory + WRITE_EXTERNAL_STORAGE。
 * 幽灵文件（文件已删但 MediaStore 残留）导致同名 insert 失败时，自动加时间戳防重保存。
 */
object DownloadSaver {

    private const val TAG = "Baixi-DL"

    /** 大文件拷贝缓冲：1MB（默认 copyTo 8KB 对 GB 级文件是灾难，IO 次数过多导致保存极慢） */
    private const val COPY_BUFFER_SIZE = 1 * 1024 * 1024

    /**
     * 保存文件到下载目录。
     * @param fileName 可为**相对路径**（如 "文件夹A/子/文件.mp4"，用于下载整个文件夹保持目录结构）；
     *                 纯文件名时保存到根目录。
     * @param targetDirUri 自定义保存目录（SAF tree Uri，content://...）；null 时用系统默认 Download
     * @return 保存成功后的标识（MediaStore uri 字符串 / SAF 文档 uri / 文件绝对路径）；失败返回 null
     */
    fun save(context: Context, fileName: String, source: File, targetDirUri: String? = null): String? {
        // 拆分相对路径与文件名：目录段与文件名分别清洗
        val clean = fileName.replace('\\', '/')
        val slash = clean.lastIndexOf('/')
        val dirRel = if (slash > 0) clean.substring(0, slash) else ""
        val baseName = if (slash >= 0) clean.substring(slash + 1) else clean
        val safeName = sanitizeFileName(baseName)
        // ★ 过滤空段与 "."/".." 段：防止 "文件夹/../../x" 之类的多级路径逃出下载目录
        val safeDir = dirRel.split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .joinToString("/") { sanitizeFileName(it) }
        // 自定义 SAF 目录：优先走系统文档树（适配 Android 10/11+ 分区存储与 Android 9-，无需额外存储权限）
        if (!targetDirUri.isNullOrBlank()) {
            return saveViaSaf(context, safeName, safeDir, source, targetDirUri)
        }
        // 默认目录：Android 10+ 优先 MediaStore；失败则回退传统文件路径（Android 9- 可用）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, safeName, safeDir, source)?.let { return it }
            Log.e(TAG, "MediaStore 保存失败，回退传统路径：$safeDir/$safeName")
        }
        saveLegacy(context, safeName, safeDir, source)?.let { return it }
        Log.e(TAG, "传统路径保存失败（Android 9- 需存储权限；Android 10+ 分区存储不可写），放弃保存")
        return null
    }

    /**
     * 通过 SAF 文档树保存（自定义下载目录）：
     * - tree uri 由用户经系统「选择文件夹」弹窗授权（takePersistableUriPermission 持久化）；
     * - 相对路径子目录逐级查找/创建（MIME_TYPE_DIR）；
     * - 文件名冲突自动加时间戳防重。
     */
    private fun saveViaSaf(
        context: Context,
        fileName: String,
        subDir: String,
        source: File,
        treeUriString: String
    ): String? {
        val resolver = context.contentResolver
        val treeUri = android.net.Uri.parse(treeUriString)
        return runCatching {
            // tree URI 不能直接作为 createDocument 的父目录（Android 10 抛 Invalid URI）：
            // 先取根文档 id，构建根 document URI 作为初始目录
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            var dirUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootDocId)
            // 定位（或创建）目标目录：相对路径逐级解析
            if (subDir.isNotBlank()) {
                for (part in subDir.split('/').filter { it.isNotBlank() }) {
                    dirUri = getOrCreateSafDir(resolver, treeUri, dirUri, part) ?: return@runCatching null
                }
            }
            // 候选：原名 → 时间戳防重名
            val candidates = buildList {
                add(fileName)
                repeat(3) { i -> add(timestampedName(fileName, i)) }
            }
            for (candidate in candidates) {
                try {
                    val docUri = DocumentsContract.createDocument(
                        resolver, dirUri, mimeOf(candidate), candidate
                    ) ?: continue
                    val wrote = resolver.openOutputStream(docUri)?.use { out ->
                        source.inputStream().use { it.copyTo(out, COPY_BUFFER_SIZE) }
                        true
                    } ?: run {
                        resolver.delete(docUri, null, null)
                        false
                    }
                    if (wrote) return docUri.toString()
                } catch (e: Exception) {
                    Log.e(TAG, "SAF 保存异常（$candidate）: ${e.message}")
                }
            }
            null
        }.getOrNull()
    }

    /**
     * 在父文档树下查找/创建指定名称的子目录，返回其文档 uri。
     * @param treeUri 原始 tree Uri（供 buildChildDocumentsUriUsingTree / buildDocumentUriUsingTree 使用）
     * @param parentDocUri 当前父目录的 document Uri（供 createDocument 使用）
     */
    private fun getOrCreateSafDir(
        resolver: ContentResolver,
        treeUri: android.net.Uri,
        parentDocUri: android.net.Uri,
        name: String
    ): android.net.Uri? {
        // 先查已存在的子目录：children 查询基于 tree Uri + 父目录文档 id
        val parentDocId = DocumentsContract.getDocumentId(parentDocUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val display = cursor.getString(1)
                val mime = cursor.getString(2)
                if (display == name && mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                }
            }
        }
        // 不存在则创建（父目录必须是 document Uri）
        return DocumentsContract.createDocument(
            resolver, parentDocUri, DocumentsContract.Document.MIME_TYPE_DIR, name
        )
    }

    /** 从 SAF tree uri 提取可读目录名（如 primary:Download/MyFolder → "Download/MyFolder"） */
    fun safDirDisplay(uriString: String): String {
        return runCatching {
            val treeId = DocumentsContract.getTreeDocumentId(android.net.Uri.parse(uriString))
            treeId.substringAfterLast(':').replace("%2F", "/").replace("%2f", "/")
                .ifBlank { "自定义目录" }
        }.getOrDefault("自定义目录")
    }

    /** 清洗文件名：非法字符替换为下划线，超长截断（保留扩展名），空名兜底；"."/".." 视为无效名 */
    private fun sanitizeFileName(name: String): String {
        var cleaned = name
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_")
            .trim()
        // ★ "." 与 ".." 是目录穿越段，绝不能作为文件名/目录名使用
        if (cleaned == "." || cleaned == "..") return fallbackName()
        if (cleaned.length > 120) {
            val ext = cleaned.substringAfterLast('.', "").take(10)
            val base = cleaned.substringBeforeLast('.').take(100)
            cleaned = if (ext.isNotBlank() && ext != cleaned) "$base.$ext" else cleaned.take(120)
        }
        return cleaned.ifBlank { fallbackName() }
    }

    /** 安全兜底文件名（空名 / 非法名 / 路径逃逸时使用） */
    private fun fallbackName(): String = "download_${System.currentTimeMillis()}"

    /**
     * MediaStore.Downloads 保存：
     * 1. 保存前清理同路径同名**幽灵**记录（文件已删但数据库仍在，部分 ROM 同名 insert 会返回 null）；
     *    ★ 物理文件真实存在的记录一律保留，绝不删用户既有文件；
     * 2. 原名被真实文件占用时不再抢名，直接用唯一名（原名+时间戳）另存；
     * 3. 均失败返回 null（上层报错，不再兜底私有目录）。
     */
    private fun saveViaMediaStore(context: Context, fileName: String, subDir: String, source: File): String? {
        val resolver = context.contentResolver
        val relativePath = if (subDir.isBlank()) {
            Environment.DIRECTORY_DOWNLOADS
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/$subDir"
        }
        // ★ 同名真实文件存在 → 保留用户文件，改用唯一名另存（不删、不覆盖）
        val nameOccupied = hasRealExistingFile(resolver, fileName, relativePath)
        if (nameOccupied) {
            Log.w(TAG, "同名文件真实存在，改用唯一文件名另存：$relativePath/$fileName")
        }
        // 候选：原名（未被真实文件占用时）→ 时间戳防重名（base.apk → base_20260812165000.apk → base_..._2.apk）
        val candidates = buildList {
            if (!nameOccupied) add(fileName)
            repeat(3) { i -> add(timestampedName(fileName, i)) }
        }
        for (candidate in candidates) {
            try {
                // 幽灵文件处理：只清理物理文件已不存在的残留记录
                removeMediaStoreDuplicates(resolver, candidate, relativePath)
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, candidate)
                    put(MediaStore.Downloads.MIME_TYPE, mimeOf(candidate))
                    put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: continue
                val wrote = resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { it.copyTo(out, COPY_BUFFER_SIZE) }
                    true
                } ?: run {
                    resolver.delete(uri, null, null)
                    false
                }
                if (!wrote) continue
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri.toString()
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore 保存异常（$candidate）: ${e.message}")
            }
        }
        return null
    }

    /** 在文件名扩展名前加时间戳防重：base.apk → base_20260812165000.apk */
    private fun timestampedName(fileName: String, attempt: Int): String {
        val dot = fileName.lastIndexOf('.')
        val base = if (dot > 0) fileName.substring(0, dot) else fileName
        val ext = if (dot > 0) fileName.substring(dot) else ""
        val ts = System.currentTimeMillis()
        return if (attempt == 0) "${base}_$ts$ext" else "${base}_${ts}_${attempt + 1}$ext"
    }

    /**
     * 清理 MediaStore 中指定路径下的同名**幽灵**记录（文件已删但数据库残留）。
     * ★ 删除前先确认对应物理文件确实不存在（DATA 路径 + openFileDescriptor 双重探测）：
     *   物理文件真实存在时保留记录不删，由调用方改用唯一文件名另存，避免删掉用户既有文件。
     */
    @Suppress("DEPRECATION")
    private fun removeMediaStoreDuplicates(
        resolver: ContentResolver,
        fileName: String,
        relativePath: String
    ) {
        runCatching {
            val selection = "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
            val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.MediaColumns.DATA)
            val ghostIds = mutableListOf<Long>()
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                arrayOf(fileName, relativePath),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    val dataPath =
                        if (dataIndex >= 0 && !cursor.isNull(dataIndex)) cursor.getString(dataIndex) else null
                    if (isRealFile(resolver, id, dataPath)) {
                        Log.w(TAG, "同名文件真实存在，保留不删（将改用唯一文件名另存）：$fileName")
                    } else {
                        ghostIds.add(id)
                    }
                }
            }
            for (id in ghostIds) {
                val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                resolver.delete(uri, null, null)
            }
        }.onFailure {
            Log.e(TAG, "清理 MediaStore 同名记录失败: ${it.message}")
        }
    }

    /**
     * 判断 MediaStore 记录对应的物理文件是否真实存在：
     * ① DATA 列给出真实路径且 File.exists()；② 否则用 openFileDescriptor 探测（分区存储下 DATA 常为空/不可靠）。
     */
    @Suppress("DEPRECATION")
    private fun isRealFile(resolver: ContentResolver, id: Long, dataPath: String?): Boolean {
        if (!dataPath.isNullOrBlank() && runCatching { File(dataPath).exists() }.getOrDefault(false)) {
            return true
        }
        return runCatching {
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    /** 指定路径下是否已存在同名**真实**文件（存在则不改用原名，直接唯一名另存） */
    @Suppress("DEPRECATION")
    private fun hasRealExistingFile(
        resolver: ContentResolver,
        fileName: String,
        relativePath: String
    ): Boolean = runCatching {
        val selection = "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.MediaColumns.DATA)
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(fileName, relativePath),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val dataIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val dataPath =
                    if (dataIndex >= 0 && !cursor.isNull(dataIndex)) cursor.getString(dataIndex) else null
                if (isRealFile(resolver, id, dataPath)) return@runCatching true
            }
        }
        false
    }.getOrDefault(false)

    private fun saveLegacy(context: Context, fileName: String, subDir: String, source: File): String? = runCatching {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val destDir = if (subDir.isBlank()) dir else File(dir, subDir)
        if (!destDir.exists()) destDir.mkdirs()
        // ★ 断言最终路径必须落在下载目录内（".." / 多级路径 / 符号链接兜底）；
        //   不满足则丢弃相对路径，改用安全的默认文件名直接写在下载根目录
        val root = dir.canonicalPath
        var dest = File(destDir, fileName)
        if (!isInsideDir(root, dest)) {
            Log.e(TAG, "目标路径逃出下载目录，改用安全默认文件名：$fileName")
            dest = File(dir, fallbackName())
        }
        source.copyTo(dest, overwrite = true)
        dest.absolutePath
    }.getOrNull()

    /** 目标文件 canonicalPath 是否位于下载根目录内（防 ".."/软链接逃逸） */
    private fun isInsideDir(rootCanonicalPath: String, file: File): Boolean = runCatching {
        val path = file.canonicalPath
        path == rootCanonicalPath || path.startsWith(rootCanonicalPath + File.separator)
    }.getOrDefault(false)

    /**
     * 删除已保存的本地文件（配合任务删除）。
     * @param savePath 保存时返回的 MediaStore uri 字符串 / SAF 文档 uri / 文件绝对路径
     * @return 是否删除成功（false 表示未找到或删除失败）
     */
    fun delete(context: Context, savePath: String): Boolean {
        if (savePath.isBlank()) return false
        return runCatching {
            if (savePath.startsWith("content://")) {
                val uri = android.net.Uri.parse(savePath)
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    deleteSafDocument(context, uri)
                } else {
                    context.contentResolver.delete(uri, null, null) > 0
                }
            } else {
                File(savePath).delete()
            }
        }.onFailure {
            Log.e(TAG, "删除本地文件失败: ${it.message}")
        }.getOrDefault(false)
    }

    /**
     * SAF 文档删除：多级 fallback，兼容国产 ROM 对 tree 授权子文档删除的权限/实现差异。
     * ① resolver.delete（标准） → ② deleteDocument（重试） → ③ 还原 tree Uri 逐级查找删除。
     */
    private fun deleteSafDocument(context: Context, docUri: android.net.Uri): Boolean {
        // ① 标准删除
        if (runCatching { context.contentResolver.delete(docUri, null, null) > 0 }.getOrDefault(false)) {
            return true
        }
        // ② DocumentsContract.deleteDocument 重试
        if (runCatching { DocumentsContract.deleteDocument(context.contentResolver, docUri) }.getOrDefault(false)) {
            return true
        }
        // ③ 还原 tree Uri（持久授权域），沿相对路径逐级 findFile 后删除
        return runCatching {
            val segments = docUri.pathSegments
            val treeIdx = segments.indexOf("tree")
            if (treeIdx < 0 || segments.size < treeIdx + 2) return@runCatching false
            // docUri: /tree/{treeId}/document/{fileDocId} → treeUri: /tree/{treeId}
            val treeUri = docUri.buildUpon().path("/" + segments.subList(0, treeIdx + 2).joinToString("/")).build()
            val treeDocId = android.net.Uri.decode(segments[treeIdx + 1])
            val fileDocId = DocumentsContract.getDocumentId(docUri)
            if (!fileDocId.startsWith(treeDocId)) return@runCatching false
            val relParts = fileDocId.removePrefix(treeDocId).trimStart('/').split('/').filter { it.isNotBlank() }
            if (relParts.isEmpty()) return@runCatching false
            val resolver = context.contentResolver
            var parentDocId = treeDocId
            for ((i, part) in relParts.withIndex()) {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
                var foundId: String? = null
                resolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                    ),
                    null, null, null
                )?.use { c ->
                    while (c.moveToNext()) {
                        if (c.getString(1) == part) {
                            foundId = c.getString(0)
                            break
                        }
                    }
                } ?: return@runCatching false
                val id = foundId ?: return@runCatching false
                if (i == relParts.lastIndex) {
                    // 最后一层即目标文件：基于 tree 构造文档 uri 删除
                    val target = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)
                    return@runCatching runCatching { resolver.delete(target, null, null) > 0 }
                        .getOrElse { DocumentsContract.deleteDocument(resolver, target) }
                }
                parentDocId = id
            }
            false
        }.getOrDefault(false)
    }

    private fun mimeOf(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "zip", "rar", "7z" -> "application/zip"
            "mp4", "mkv", "mov", "avi", "webm" -> "video/mp4"
            "mp3", "wav", "flac", "aac" -> "audio/mpeg"
            // ★ MIME 必须与扩展名严格一致：上一版把 png/gif/webp 全写成 image/jpeg，
            //   系统发现扩展名与 MIME 不符会按 MIME 修正扩展名 → 「xx.png (2).jpg」式改名
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "avif" -> "image/avif"
            "svg" -> "image/svg+xml"
            "txt", "md", "log" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}