package com.example.countdowndays

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.countdowndays.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 数据库迁移测试：手工按 v1 的表结构建库并塞入数据，再让 Room 走 1->2->3 迁移打开。
 * Room 在迁移后会逐表校验结构与实体是否一致，迁移 SQL 写错（典型如默认值与实体
 * @ColumnInfo 的 defaultValue 对不上）会在打开时直接抛异常，这里能第一时间拦住。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MigrationTest {

    @Test
    fun migrate1To3_keepsDataAndMatchesSchema() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test.db"
        context.deleteDatabase(dbName)

        // v1 的建表语句：还没有 v2 迁移新增的那些列，也没有 categories 表
        val sqlite = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `events` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`imagePath` TEXT, " +
                "`is_pinned` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `timeline_nodes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`eventId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`time` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        sqlite.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_timeline_nodes_eventId` ON `timeline_nodes` (`eventId`)"
        )
        sqlite.execSQL(
            "INSERT INTO events (name, description, date, imagePath, is_pinned, createdAt) " +
                "VALUES ('旧事件', 'v1 时期的数据', 1790000000000, NULL, 1, 1700000000000)"
        )
        sqlite.execSQL(
            "INSERT INTO timeline_nodes (eventId, name, time, createdAt) " +
                "VALUES (1, '节点一', 1790000000000, 1700000000000)"
        )
        sqlite.version = 1
        sqlite.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        try {
            val events = runBlocking { db.eventDao().getAllEvents() }
            assertEquals(1, events.size)
            val e = events.single().event
            assertEquals("旧事件", e.name)
            assertTrue(e.isPinned)
            // v2 / v3 新增字段应带迁移里声明的默认值
            assertEquals("NONE", e.repeatType)
            assertEquals(9, e.reminderHour)
            assertEquals(0, e.backgroundPreset)
            assertEquals(1, events.single().nodes.size)
            // categories 表由迁移新建，此时应为空表可查
            val categories = runBlocking { db.categoryDao().getAll() }
            assertEquals(0, categories.size)
            // 节点 v4 新列应带默认值
            val node = events.single().nodes.single()
            assertEquals("", node.note)
            assertEquals(null, node.imagePath)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    /**
     * v3 -> v4：按 v3 的完整表结构建库，塞入一个开了提醒的事件和一个没开的，
     * 迁移后旧单提醒应原样变成 reminders 表里的第一条，没开的事件不应凭空多出提醒。
     */
    @Test
    fun migrate3To4_movesLegacyReminder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "migration-test-34.db"
        context.deleteDatabase(dbName)

        val sqlite = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `events` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`date` INTEGER NOT NULL, " +
                "`imagePath` TEXT, " +
                "`is_pinned` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "`repeatType` TEXT NOT NULL DEFAULT 'NONE', " +
                "`repeatEndDate` INTEGER, " +
                "`isLunar` INTEGER NOT NULL DEFAULT 0, " +
                "`lunarMonth` INTEGER NOT NULL DEFAULT 0, " +
                "`lunarDay` INTEGER NOT NULL DEFAULT 0, " +
                "`categoryId` INTEGER, " +
                "`archived` INTEGER NOT NULL DEFAULT 0, " +
                "`colorIndex` INTEGER NOT NULL DEFAULT 0, " +
                "`sortOrder` INTEGER NOT NULL DEFAULT 0, " +
                "`reminderEnabled` INTEGER NOT NULL DEFAULT 0, " +
                "`reminderAdvanceDays` INTEGER NOT NULL DEFAULT 0, " +
                "`reminderHour` INTEGER NOT NULL DEFAULT 9, " +
                "`reminderMinute` INTEGER NOT NULL DEFAULT 0, " +
                "`backgroundPreset` INTEGER NOT NULL DEFAULT 0)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `timeline_nodes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`eventId` INTEGER NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`time` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, " +
                "FOREIGN KEY(`eventId`) REFERENCES `events`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        sqlite.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_timeline_nodes_eventId` ON `timeline_nodes` (`eventId`)"
        )
        sqlite.execSQL(
            "CREATE TABLE IF NOT EXISTS `categories` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`colorIndex` INTEGER NOT NULL DEFAULT 0, " +
                "`sortOrder` INTEGER NOT NULL DEFAULT 0)"
        )
        // 事件 1：开了提醒（提前 7 天 08:30）；事件 2：没开
        sqlite.execSQL(
            "INSERT INTO events (name, description, date, imagePath, is_pinned, createdAt, " +
                "reminderEnabled, reminderAdvanceDays, reminderHour, reminderMinute) " +
                "VALUES ('有提醒', '', 1790000000000, NULL, 0, 1700000000000, 1, 7, 8, 30)"
        )
        sqlite.execSQL(
            "INSERT INTO events (name, description, date, imagePath, is_pinned, createdAt, " +
                "reminderEnabled, reminderAdvanceDays, reminderHour, reminderMinute) " +
                "VALUES ('没提醒', '', 1790000000000, NULL, 0, 1700000000000, 0, 3, 9, 0)"
        )
        sqlite.version = 3
        sqlite.close()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
        try {
            val dao = db.eventDao()
            val moved = runBlocking { dao.getReminders(1) }
            assertEquals(1, moved.size)
            assertEquals(7, moved.single().advanceDays)
            assertEquals(8, moved.single().hour)
            assertEquals(30, moved.single().minute)
            // 没开提醒的事件不能凭空多出提醒行
            assertEquals(0, runBlocking { dao.getReminders(2) }.size)
            // 事件本体与旧字段镜像保持不变
            val events = runBlocking { dao.getAllEvents() }
            assertEquals(2, events.size)
            assertEquals(7, events.first { it.event.id == 1L }.event.reminderAdvanceDays)
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
