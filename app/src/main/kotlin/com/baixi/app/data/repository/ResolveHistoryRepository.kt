package com.baixi.app.data.repository

import com.baixi.app.data.db.ResolveHistoryDao
import com.baixi.app.data.db.ResolveHistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 解析历史仓库：DAO 薄封装。
 * - ResolveViewModel 解析成功后写入；
 * - ResolveHistoryScreen 观察列表（Flow 自动刷新）并执行单删/清空。
 */
class ResolveHistoryRepository(private val dao: ResolveHistoryDao) {

    /** 最近 100 条解析历史（按时间倒序） */
    fun observeAll(): Flow<List<ResolveHistoryEntity>> = dao.observeAll()

    /** 记录一次成功解析 */
    suspend fun insert(item: ResolveHistoryEntity) = dao.insert(item)

    /** 删除单条记录 */
    suspend fun delete(id: Long) = dao.delete(id)

    /** 一键清空 */
    suspend fun clearAll() = dao.clearAll()

    /** 切换星标（收藏 / 取消收藏） */
    suspend fun toggleStar(id: Long) = dao.toggleStar(id)

    /** 覆写标签（逗号分隔字符串，标签编辑用） */
    suspend fun updateTags(id: Long, tags: String) = dao.updateTags(id, tags)
}
