package com.smartreminder.data.local.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `cached_group_members` ADD COLUMN `display_name` TEXT")
        db.execSQL("ALTER TABLE `cached_group_members` ADD COLUMN `avatar_url` TEXT")
    }
}
