package com.smartreminder.data.local.room

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartreminder.data.local.room.migration.MIGRATION_1_2
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

    private companion object {
        const val TEST_DATABASE_NAME = "collaboration-migration-test.db"
    }
}
