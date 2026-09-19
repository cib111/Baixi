package com.baixi.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 解析历史 DAO：最近 100 条（Flow 观察刷新）、单删、清空 */
@Dao
interface ResolveHistoryDao {

    // 星标优先置顶，其余按时间倒序（最近 100 条）
    @Query("SELECT * FROM resolve_history ORDER BY isStarred DESC, createTime DESC LIMIT 100")
    fun observeAll(): Flow<List<ResolveHistoryEntity>>

    /** 切换星标（收藏 / 取消收藏） */
    @Query("UPDATE resolve_history SET isStarred = CASE isStarred WHEN 1 THEN 0 ELSE 1 END WHERE id = :id")
    suspend fun toggleStar(id: Long)

    /** 覆写标签（逗号分隔字符串；v14 新增，标签编辑用） */
    @Query("UPDATE resolve_history SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: Long, tags: String)

    @Insert
    suspend fun insert(item: ResolveHistoryEntity)

    @Query("DELETE FROM resolve_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM resolve_history")
    suspend fun clearAll()
}
