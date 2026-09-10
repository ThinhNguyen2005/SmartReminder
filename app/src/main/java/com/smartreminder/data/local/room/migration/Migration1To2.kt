package com.smartreminder.data.local.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // cached_collaboration_groups
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_collaboration_groups` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `created_by` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        // cached_group_members
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_group_members` (
                `group_id` TEXT NOT NULL,
                `user_id` TEXT NOT NULL,
                `role` TEXT NOT NULL,
                `joined_at` INTEGER NOT NULL,
                PRIMARY KEY(`group_id`, `user_id`),
                FOREIGN KEY(`group_id`) REFERENCES `cached_collaboration_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // cached_group_invites
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_group_invites` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `inviter_id` TEXT NOT NULL,
                `invitee_user_id` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `responded_at` INTEGER,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`group_id`) REFERENCES `cached_collaboration_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_group_invites_group_id` ON `cached_group_invites` (`group_id`)")

        // cached_group_tasks
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_group_tasks` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `created_by` TEXT NOT NULL,
                `assignee_id` TEXT NOT NULL,
                `due_at` INTEGER NOT NULL,
                `status` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`group_id`) REFERENCES `cached_collaboration_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_group_tasks_group_id` ON `cached_group_tasks` (`group_id`)")

        // cached_group_task_reminders
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_group_task_reminders` (
                `task_id` TEXT NOT NULL,
                `offset_seconds` INTEGER NOT NULL,
                PRIMARY KEY(`task_id`, `offset_seconds`),
                FOREIGN KEY(`task_id`) REFERENCES `cached_group_tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )

        // cached_group_reminders
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cached_group_reminders` (
                `id` TEXT NOT NULL,
                `group_id` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `description` TEXT,
                `created_by` TEXT NOT NULL,
                `audience_type` TEXT NOT NULL,
                `audience_user_id` TEXT,
                `remind_at` INTEGER NOT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`group_id`) REFERENCES `cached_collaboration_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_group_reminders_group_id` ON `cached_group_reminders` (`group_id`)")

        // pending_group_commands
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_group_commands` (
                `id` TEXT NOT NULL,
                `command_type` TEXT NOT NULL,
                `aggregate_id` TEXT NOT NULL,
                `payload_json` TEXT NOT NULL,
                `payload_version` INTEGER NOT NULL,
                `expected_version` INTEGER,
                `created_at` INTEGER NOT NULL,
                `attempt_count` INTEGER NOT NULL,
                `state` TEXT NOT NULL,
                `last_error` TEXT,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_group_commands_state` ON `pending_group_commands` (`state`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_group_commands_created_at` ON `pending_group_commands` (`created_at`)")
    }
}
