package com.baixi.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {

    @Query("SELECT * FROM download_task ORDER BY createTime DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Insert
    suspend fun insert(task: DownloadTaskEntity): Long

    @Query("SELECT * FROM download_task WHERE id = :id")
    suspend fun get(id: Long): DownloadTaskEntity?

    /** 活动任务（等待中/下载中/等待WiFi）：通知栏「暂停全部」使用 */
    @Query("SELECT * FROM download_task WHERE status IN (0, 1, 5) ORDER BY createTime DESC")
    suspend fun activeTasks(): List<DownloadTaskEntity>

    /** 可恢复任务（已暂停/失败）：通知栏「继续全部」使用 */
    @Query("SELECT * FROM download_task WHERE status IN (2, 4) ORDER BY createTime DESC")
    suspend fun resumableTasks(): List<DownloadTaskEntity>

    @Query("UPDATE download_task SET status = :status, downloadedSize = :downloadedSize, totalSize = :totalSize WHERE id = :id")
    suspend fun updateProgress(id: Long, status: Int, downloadedSize: Long, totalSize: Long)

    @Query("UPDATE download_task SET chunkCount = :chunkCount, plannedTotalSize = :totalSize WHERE id = :id")
    suspend fun updatePlan(id: Long, chunkCount: Int, totalSize: Long)

    @Query("UPDATE download_task SET status = 2 WHERE status = 1 OR status = 0 OR status = 5")
    suspend fun markInterruptedAsPaused()

    @Query("UPDATE download_task SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)

    /** 仅在任务仍活动（等待中/下载中/等待WiFi）时改状态：避免覆盖「已完成/失败」终态 */
    @Query("UPDATE download_task SET status = :status WHERE id = :id AND status IN (0, 1, 5)")
    suspend fun updateStatusIfActive(id: Long, status: Int)

    @Query("UPDATE download_task SET errorMsg = :errorMsg WHERE id = :id")
    suspend fun updateError(id: Long, errorMsg: String)

    /** 持久化请求头 JSON（Cookie/UA）：应用重启后恢复任务仍可续传 */
    @Query("UPDATE download_task SET requestHeadersJson = :json WHERE id = :id")
    suspend fun updateHeaders(id: Long, json: String)

    @Query("UPDATE download_task SET status = :status, savePath = :savePath WHERE id = :id")
    suspend fun complete(id: Long, status: Int, savePath: String)

    /** 记录服务器校验标识（ETag / Last-Modified）：下次续传前比对，识别服务器文件变化 */
    @Query("UPDATE download_task SET etag = :etag, lastModified = :lastModified WHERE id = :id")
    suspend fun updateValidators(id: Long, etag: String, lastModified: String)

    /** 设置任务优先级（数值越大越先占用下载槽位） */
    @Query("UPDATE download_task SET priority = :priority WHERE id = :id")
    suspend fun updatePriority(id: Long, priority: Int)

    /** 重命名已下载文件后同步任务记录里的文件名（「已下载」文件管理页用） */
    @Query("UPDATE download_task SET fileName = :name WHERE id = :id")
    suspend fun updateFileName(id: Long, name: String)

    /** 设置单任务线程数（0=跟随平台/全局设置） */
    @Query("UPDATE download_task SET threadOverride = :threads WHERE id = :id")
    suspend fun updateThreadOverride(id: Long, threads: Int)

    /** 上次会话处于「下载中」的任务（应用启动时用于中断恢复） */
    @Query("SELECT * FROM download_task WHERE status = 1 ORDER BY createTime DESC")
    suspend fun downloadingTasks(): List<DownloadTaskEntity>

    @Query("DELETE FROM download_task WHERE id = :id")
    suspend fun delete(id: Long)
}
