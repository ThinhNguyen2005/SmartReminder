package com.smartreminder.data.local.room

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartreminder.data.local.room.migration.MIGRATION_1_2
import com.smartreminder.data.local.room.migration.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollaborationRoomMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CueDatabase::class.java
    )

    @Test
    fun migration1To2_preservesLegacyDataAndCreatesCollaborationTables() {
        helper.createDatabase(TEST_DATABASE_NAME, 1).apply {
            execSQL(
                """
                INSERT INTO schedule_groups (
                    id, name, icon_key, color_key, sort_order, is_archived, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("legacy_group", "Legacy group", "folder", "blue", 2, 0, 100L, 200L)
            )
            execSQL(
                """
                INSERT INTO routines (
                    id, group_id, name, description, icon_key, color_key, enabled,
                    sort_order, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "legacy_routine",
                    "legacy_group",
                    "Legacy routine",
                    "Kept across migration",
                    "sun",
                    "amber",
                    1,
                    1,
                    300L,
                    400L
                )
            )
            execSQL(
                "INSERT INTO routine_weekly_days (routine_id, day_of_week) VALUES (?, ?)",
                arrayOf<Any?>("legacy_routine", 1)
            )
            execSQL(
                """
                INSERT INTO routine_items (
                    id, routine_id, title, scheduled_minute, duration_minutes, sort_order, enabled
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("legacy_item", "legacy_routine", "Legacy item", 480, 30, 0, 1)
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_NAME,
            2,
            true,
            MIGRATION_1_2
        )
        try {
            assertEquals("Legacy group", migrated.queryString("SELECT name FROM schedule_groups WHERE id = 'legacy_group'"))
            assertEquals(
                "Legacy routine",
                migrated.queryString("SELECT name FROM routines WHERE id = 'legacy_routine'")
            )
            assertEquals(
                "Legacy item",
                migrated.queryString("SELECT title FROM routine_items WHERE id = 'legacy_item'")
            )
            assertEquals(
                1,
                migrated.queryLong("SELECT day_of_week FROM routine_weekly_days WHERE routine_id = 'legacy_routine'")
            )

            val expectedTables = setOf(
                "cached_collaboration_groups",
                "cached_group_members",
                "cached_group_invites",
                "cached_group_tasks",
                "cached_group_task_reminders",
                "cached_group_reminders",
                "pending_group_commands"
            )
            val actualTables = buildSet {
                migrated.query(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE 'cached_%' OR name = 'pending_group_commands'"
                ).use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
            }
            assertTrue(actualTables.containsAll(expectedTables))

            val pendingColumns = buildSet {
                migrated.query("PRAGMA table_info(pending_group_commands)").use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow("name")
                    while (cursor.moveToNext()) add(cursor.getString(nameColumn))
                }
            }
            assertTrue(pendingColumns.contains("enqueue_sequence"))
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migration2To3_preservesSchedulesRoutinesCollaborationAndPendingCommandSchema() {
        helper.createDatabase(TEST_DATABASE_V2_TO_V3, 2).apply {
            execSQL(
                """
                INSERT INTO schedule_groups (
                    id, name, icon_key, color_key, sort_order, is_archived, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("legacy_group", "Legacy group", "folder", "blue", 2, 0, 100L, 200L)
            )
            execSQL(
                """
                INSERT INTO routines (
                    id, group_id, name, description, icon_key, color_key, enabled,
                    sort_order, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "legacy_routine",
                    "legacy_group",
                    "Legacy routine",
                    "Kept across 2 to 3",
                    "sun",
                    "amber",
                    1,
                    1,
                    300L,
                    400L
                )
            )
            execSQL(
                "INSERT INTO routine_weekly_days (routine_id, day_of_week) VALUES (?, ?)",
                arrayOf<Any?>("legacy_routine", 1)
            )
            execSQL(
                """
                INSERT INTO routine_items (
                    id, routine_id, title, scheduled_minute, duration_minutes, sort_order, enabled
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("legacy_item", "legacy_routine", "Legacy item", 480, 30, 0, 1)
            )
            execSQL(
                """
                INSERT INTO cached_collaboration_groups (
                    id, name, description, created_by, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("collab_group", "Collaboration group", null, "owner_1", 500L, 600L)
            )
            execSQL(
                """
                INSERT INTO cached_group_members (group_id, user_id, role, joined_at)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("collab_group", "member_1", "MEMBER", 700L)
            )
            execSQL(
                """
                INSERT INTO cached_group_invites (
                    id, group_id, inviter_id, invitee_user_id, status, created_at, responded_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>("invite_1", "collab_group", "owner_1", "member_2", "PENDING", 800L, null)
            )
            execSQL(
                """
                INSERT INTO cached_group_tasks (
                    id, group_id, title, description, created_by, assignee_id, due_at,
                    status, version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "task_1",
                    "collab_group",
                    "Cached task",
                    null,
                    "owner_1",
                    "member_1",
                    9_000L,
                    "TODO",
                    1L,
                    900L,
                    1_000L
                )
            )
            execSQL(
                "INSERT INTO cached_group_task_reminders (task_id, offset_seconds) VALUES (?, ?)",
                arrayOf<Any?>("task_1", 300L)
            )
            execSQL(
                """
                INSERT INTO cached_group_reminders (
                    id, group_id, title, description, created_by, audience_type,
                    audience_user_id, remind_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "reminder_1",
                    "collab_group",
                    "Cached reminder",
                    null,
                    "owner_1",
                    "EVERYONE",
                    null,
                    10_000L,
                    900L,
                    1_000L
                )
            )
            execSQL(
                """
                INSERT INTO pending_group_commands (
                    id, command_type, aggregate_id, payload_json, payload_version,
                    expected_version, created_at, enqueue_sequence, attempt_count, state, last_error
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    "command_1",
                    "START_TASK",
                    "task_1",
                    "{}",
                    1,
                    null,
                    1_100L,
                    1L,
                    0,
                    "PENDING",
                    null
                )
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE_V2_TO_V3,
            3,
            true,
            MIGRATION_2_3
        )
        try {
            assertEquals("Legacy group", migrated.queryString("SELECT name FROM schedule_groups WHERE id = 'legacy_group'"))
            assertEquals("Legacy routine", migrated.queryString("SELECT name FROM routines WHERE id = 'legacy_routine'"))
            assertEquals("Legacy item", migrated.queryString("SELECT title FROM routine_items WHERE id = 'legacy_item'"))
            assertEquals(1L, migrated.queryLong("SELECT day_of_week FROM routine_weekly_days WHERE routine_id = 'legacy_routine'"))
            assertEquals("Collaboration group", migrated.queryString("SELECT name FROM cached_collaboration_groups WHERE id = 'collab_group'"))
            assertEquals("member_1", migrated.queryString("SELECT user_id FROM cached_group_members WHERE group_id = 'collab_group'"))
            assertEquals("invite_1", migrated.queryString("SELECT id FROM cached_group_invites WHERE id = 'invite_1'"))
            assertEquals("task_1", migrated.queryString("SELECT id FROM cached_group_tasks WHERE id = 'task_1'"))
            assertEquals(300L, migrated.queryLong("SELECT offset_seconds FROM cached_group_task_reminders WHERE task_id = 'task_1'"))
            assertEquals("reminder_1", migrated.queryString("SELECT id FROM cached_group_reminders WHERE id = 'reminder_1'"))
            assertEquals("command_1", migrated.queryString("SELECT id FROM pending_group_commands WHERE id = 'command_1'"))
            assertTrue(migrated.queryStringOrNull("SELECT display_name FROM cached_group_members WHERE group_id = 'collab_group'") == null)
            assertTrue(migrated.queryStringOrNull("SELECT avatar_url FROM cached_group_members WHERE group_id = 'collab_group'") == null)

            val pendingColumns = migrated.queryColumnNames("PRAGMA table_info(pending_group_commands)")
            assertEquals(
                listOf(
                    "id",
                    "command_type",
                    "aggregate_id",
                    "payload_json",
                    "payload_version",
                    "expected_version",
                    "created_at",
                    "enqueue_sequence",
                    "attempt_count",
                    "state",
                    "last_error"
                ),
                pendingColumns
            )
        } finally {
            migrated.close()
        }
    }

    private fun SupportSQLiteDatabase.queryString(sql: String): String {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
    }

    private fun SupportSQLiteDatabase.queryLong(sql: String): Long {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }
    }

    private fun SupportSQLiteDatabase.queryStringOrNull(sql: String): String? {
        return query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            if (cursor.isNull(0)) null else cursor.getString(0)
        }
    }

    private fun SupportSQLiteDatabase.queryColumnNames(sql: String): List<String> {
        return buildList {
            query(sql).use { cursor ->
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }
    }

    private companion object {
        const val TEST_DATABASE_NAME = "collaboration-migration-test.db"
        const val TEST_DATABASE_V2_TO_V3 = "collaboration-migration-v2-to-v3-test.db"
    }
}
