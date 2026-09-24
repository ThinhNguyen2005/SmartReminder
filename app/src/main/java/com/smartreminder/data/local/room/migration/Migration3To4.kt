package com.smartreminder.data.local.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tasks` (
                `id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `scheduled_date_epoch_day` INTEGER NOT NULL,
                `scheduled_minute` INTEGER NOT NULL,
                `duration_minutes` INTEGER NOT NULL,
                `is_completed` INTEGER NOT NULL,
                `priority` TEXT NOT NULL,
                `category` TEXT,
                `is_virtual` INTEGER NOT NULL,
                `attendees` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_tasks_scheduled_date_epoch_day` ON `tasks` (`scheduled_date_epoch_day`)"
        )
    }
}
