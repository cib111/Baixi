package com.baixi.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 解析历史：一次成功的分享解析记录。
 * 用于解析页「历史」快速重新解析（点击条目自动填入并开始解析）。
 */
@Entity(tableName = "resolve_history")
data class ResolveHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 分享链接（重新解析的输入） */
    val url: String,
    /** 分享标题（createSession 返回，展示用） */
    val title: String,
    /** 平台标识（QUARK / UC / XUNLEI / BAIDU / C139 / PAN123） */
    val platform: String,
    /** 记录时间（毫秒） */
    val createTime: Long,
    /** 星标收藏（常用分享链接；v12 新增，默认未标） */
    @ColumnInfo(defaultValue = "0") val isStarred: Boolean = false,
    /**
     * 自定义标签（逗号分隔，如 "电影,4K"；v14 新增，默认空串）。
     * 注意：String 列的 defaultValue 必须写成带单引号的 SQL 字面量 "''"（与本库
     * DownloadTaskEntity.etag/lastModified/cleanupId 一致），Room 会原样拼进
     * CREATE TABLE，必须与 MIGRATION_13_14 的 `DEFAULT ''` 完全相同。
     */
    @ColumnInfo(defaultValue = "''") val tags: String = ""
)
