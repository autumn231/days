package com.example.countdowndays.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EventEntity::class, TimelineNodeEntity::class, CategoryEntity::class,
        ReminderEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * v1 -> v2：重复规则、农历、分类、归档、提醒等字段。
         * 必须写迁移，绝不能再用破坏性迁移，否则老用户升级会丢掉全部记录。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN repeatType TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE events ADD COLUMN repeatEndDate INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN isLunar INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN lunarMonth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN lunarDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN categoryId INTEGER")
                db.execSQL("ALTER TABLE events ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN colorIndex INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN reminderEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN reminderAdvanceDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN reminderHour INTEGER NOT NULL DEFAULT 9")
                db.execSQL("ALTER TABLE events ADD COLUMN reminderMinute INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        colorIndex INTEGER NOT NULL DEFAULT 0,
                        sortOrder INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        /** v2 -> v3：展示页背景预设 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN backgroundPreset INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v3 -> v4（v3.23）：
         * 1) 时间线节点加文案与配图列；
         * 2) 新建 reminders 表承载多节点提醒，并把旧的单提醒
         *    （events 上的 reminderEnabled/AdvanceDays/Hour/Minute）
         *    原样搬成每个已开启提醒事件的第一条记录，一条不丢。
         *    events 上的旧列保留作镜像，不删。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE timeline_nodes ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE timeline_nodes ADD COLUMN imagePath TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS reminders (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        eventId INTEGER NOT NULL,
                        advanceDays INTEGER NOT NULL DEFAULT 0,
                        hour INTEGER NOT NULL DEFAULT 9,
                        minute INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(eventId) REFERENCES events(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_reminders_eventId ON reminders (eventId)"
                )
                db.execSQL(
                    """
                    INSERT INTO reminders (eventId, advanceDays, hour, minute)
                    SELECT id, reminderAdvanceDays, reminderHour, reminderMinute
                    FROM events WHERE reminderEnabled = 1
                    """.trimIndent()
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "countdown.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // 只在降级（装回旧版本）时清库；升级缺少迁移会直接抛错，便于开发期暴露问题
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
