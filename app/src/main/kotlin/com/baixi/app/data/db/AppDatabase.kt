package com.baixi.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QuarkAccountEntity::class, DownloadTaskEntity::class, UCAccountEntity::class, XunleiAccountEntity::class, BaiduAccountEntity::class, C139AccountEntity::class, Pan123AccountEntity::class, ResolveHistoryEntity::class],
    version = 14,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun quarkAccountDao(): QuarkAccountDao

    abstract fun downloadTaskDao(): DownloadTaskDao

    abstract fun resolveHistoryDao(): ResolveHistoryDao

    abstract fun ucAccountDao(): UCAccountDao

    abstract fun xunleiAccountDao(): XunleiAccountDao

    abstract fun baiduAccountDao(): BaiduAccountDao

    abstract fun c139AccountDao(): C139AccountDao

    abstract fun pan123AccountDao(): Pan123AccountDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "baixi.db"
                )
                    .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    // 早期开发版（1-8）无可靠 schema；从 v9 起必须保留凭证和下载任务
                    .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5, 6, 7, 8)
                    .build()
                    .also { instance = it }
            }

        // v14：解析历史新增「标签」列（逗号分隔字符串，历史页标签编辑用）
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE resolve_history ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        // v13：下载任务新增「服务器校验标识」「优先级」「单任务线程数覆盖」
        // - etag/lastModified：断点续传前检测服务器文件是否变化（变化即清分片重下，避免拼出损坏文件）
        // - priority：高优先级任务优先占用下载槽位
        // - threadOverride：单任务线程数（0=跟随平台/全局设置）
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_task ADD COLUMN etag TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE download_task ADD COLUMN lastModified TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE download_task ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_task ADD COLUMN threadOverride INTEGER NOT NULL DEFAULT 0")
            }
        }

        // v12：解析历史加星标列（收藏常用分享链接，列表置顶）
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE resolve_history ADD COLUMN isStarred INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 解析历史表（解析页「历史」入口，v11 新增）
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS resolve_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "url TEXT NOT NULL, " +
                        "title TEXT NOT NULL, " +
                        "platform TEXT NOT NULL, " +
                        "createTime INTEGER NOT NULL)"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE download_task ADD COLUMN requestHeadersJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE download_task ADD COLUMN chunkCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_task ADD COLUMN plannedTotalSize INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE download_task ADD COLUMN cleanupId TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
