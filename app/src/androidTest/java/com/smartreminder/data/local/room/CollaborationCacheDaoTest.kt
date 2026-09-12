package com.smartreminder.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
import com.smartreminder.data.local.room.entity.collaboration.PendingGroupCommandEntity
import com.smartreminder.data.local.room.model.collaboration.PendingGroupCommandState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollaborationCacheDaoTest {

    private lateinit var database: CueDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        ApplicationProvider.getApplicationContext<Context>().deleteDatabase(REOPEN_TEST_DATABASE_NAME)
    }

    @Test
    fun givenGroupsWithSameName_whenObserved_thenOrdersByStableIdTieBreaker() = runTest {
        database.collaborationCacheDao().upsertGroup(group(id = "group_b", name = "Same name"))
        database.collaborationCacheDao().upsertGroup(group(id = "group_a", name = "Same name"))

        val observedIds = database.collaborationCacheDao().observeGroups().first().map { it.id }

        assertEquals(listOf("group_a", "group_b"), observedIds)
    }

    @Test
    fun givenInviteBeforeGroupCache_whenUpserted_thenInviteIsObserved() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertInvites(
            listOf(
                CachedGroupInviteEntity(
                    id = "invite_1",
                    groupId = "group_not_cached",
                    inviterId = "owner_1",
                    inviteeUserId = "user_1",
                    status = "PENDING",
                    createdAt = 1L,
                    respondedAt = null
                )
            )
        )

        val observedIds = cacheDao.observeInvites().first().map { it.id }

        assertEquals(listOf("invite_1"), observedIds)
    }

    @Test
    fun givenMembersWithSameJoinedAt_whenObserved_thenOrdersByUserIdAndKeepsProfileFields() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertGroup(group())
        cacheDao.upsertMembers(
            listOf(
                member(userId = "user_b", displayName = "Binh", avatarUrl = "https://example.com/b.png"),
                member(userId = "user_a", displayName = "An", avatarUrl = null)
            )
        )

        val observed = cacheDao.observeMembers("group_1").first()

        assertEquals(listOf("user_a", "user_b"), observed.map { it.userId })
        assertEquals("An", observed[0].displayName)
        assertEquals("https://example.com/b.png", observed[1].avatarUrl)
    }

    @Test
    fun givenMembersForMultipleGroups_whenReplacingAndDeletingScopedMember_thenOnlyTargetRowsChange() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertGroup(group(id = "group_1"))
        cacheDao.upsertGroup(group(id = "group_2"))
        cacheDao.upsertMembers(
            listOf(
                member(groupId = "group_1", userId = "old_user"),
                member(groupId = "group_2", userId = "kept_user")
            )
        )

        cacheDao.replaceMembers(
            "group_1",
            listOf(member(groupId = "group_1", userId = "new_user"))
        )
        cacheDao.deleteMember(groupId = "group_1", userId = "new_user")

        assertEquals(emptyList<String>(), cacheDao.observeMembers("group_1").first().map { it.userId })
        assertEquals(listOf("kept_user"), cacheDao.observeMembers("group_2").first().map { it.userId })
    }

    @Test
    fun givenInvitesForMultipleGroups_whenReplacingAndDeletingScopedInvite_thenOnlyTargetRowsChange() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertInvites(
            listOf(
                invite(id = "invite_old", groupId = "group_1", createdAt = 1L),
                invite(id = "invite_other", groupId = "group_2", createdAt = 2L)
            )
        )

        cacheDao.replaceInvites(
            "group_1",
            listOf(invite(id = "invite_new", groupId = "group_1", createdAt = 3L))
        )
        assertEquals(listOf("invite_new"), cacheDao.observeInvites("group_1").first().map { it.id })
        assertEquals(listOf("invite_other"), cacheDao.observeInvites("group_2").first().map { it.id })

        cacheDao.deleteInvite(groupId = "group_1", inviteId = "invite_new")

        assertEquals(listOf("invite_other"), cacheDao.observeInvites().first().map { it.id })
    }

    @Test
    fun givenTasksWithSameCreatedAt_whenObserved_thenOrdersByStableIdTieBreaker() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertGroup(group())
        cacheDao.upsertTask(task(id = "task_b", createdAt = 500L))
        cacheDao.upsertTask(task(id = "task_a", createdAt = 500L))

        val observedIds = cacheDao.observeTasks("group_1").first().map { it.id }

        assertEquals(listOf("task_a", "task_b"), observedIds)
    }

    @Test
    fun givenTaskSnapshotsForMultipleGroups_whenReplacing_thenOnlyTargetGroupChanges() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertGroup(group(id = "group_1"))
        cacheDao.upsertGroup(group(id = "group_2"))
        cacheDao.upsertTasks(
            listOf(
                task(id = "task_old", groupId = "group_1", createdAt = 1L),
                task(id = "task_kept", groupId = "group_2", createdAt = 2L)
            )
        )

        cacheDao.replaceTasks(
            groupId = "group_1",
            tasks = listOf(task(id = "task_new", groupId = "group_1", createdAt = 3L))
        )

        assertEquals(listOf("task_new"), cacheDao.observeTasks("group_1").first().map { it.id })
        assertEquals(listOf("task_kept"), cacheDao.observeTasks("group_2").first().map { it.id })
    }

    @Test
    fun givenTaskWithReminders_whenReplaced_thenOffsetsAreSortedAndStaleOffsetsAreRemoved() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertGroup(group())
        val task = task(id = "task_1", groupId = "group_1", createdAt = 1L)
        cacheDao.replaceTaskWithReminders(
            task,
            listOf(
                CachedGroupTaskReminderEntity(taskId = task.id, offsetSeconds = 300L),
                CachedGroupTaskReminderEntity(taskId = task.id, offsetSeconds = 60L)
            )
        )

        assertEquals(
            listOf(60L, 300L),
            cacheDao.getTaskDetails("group_1", task.id)!!.reminders.map { it.offsetSeconds }
        )

        cacheDao.replaceTaskWithReminders(
            task.copy(updatedAt = 2L),
            listOf(CachedGroupTaskReminderEntity(taskId = task.id, offsetSeconds = 120L))
        )

        assertEquals(
            listOf(120L),
            cacheDao.getTaskDetails("group_1", task.id)!!.reminders.map { it.offsetSeconds }
        )
    }

    @Test
    fun givenGroupTaskSnapshotWithOffsets_whenReplaced_thenRowsAreWrittenTogetherAndScoped() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertGroup(group(id = "group_1"))
        cacheDao.upsertGroup(group(id = "group_2"))
        val groupOneTask = task(id = "task_1", groupId = "group_1", createdAt = 1L)
        val groupTwoTask = task(id = "task_2", groupId = "group_2", createdAt = 2L)

        cacheDao.replaceTasks(
            groupId = "group_1",
            tasks = listOf(groupOneTask),
            reminders = listOf(
                CachedGroupTaskReminderEntity(taskId = groupOneTask.id, offsetSeconds = 600L),
                CachedGroupTaskReminderEntity(taskId = groupOneTask.id, offsetSeconds = 60L)
            )
        )
        cacheDao.upsertTaskWithReminders(
            groupTwoTask,
            listOf(CachedGroupTaskReminderEntity(taskId = groupTwoTask.id, offsetSeconds = 120L))
        )

        assertEquals(listOf(60L, 600L), cacheDao.getTaskDetails("group_1", "task_1")!!.reminders.map { it.offsetSeconds })
        assertEquals(listOf(120L), cacheDao.getTaskDetails("group_2", "task_2")!!.reminders.map { it.offsetSeconds })
    }

    @Test
    fun givenScopedTaskDelete_whenDeleted_thenTaskAndReminderRowsAreRemoved() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertGroup(group())
        val task = task(id = "task_1", groupId = "group_1", createdAt = 1L)
        cacheDao.replaceTaskWithReminders(
            task,
            listOf(CachedGroupTaskReminderEntity(taskId = task.id, offsetSeconds = 60L))
        )

        cacheDao.deleteTask(groupId = "group_1", taskId = task.id)

        assertEquals(emptyList<CachedGroupTaskEntity>(), cacheDao.observeTasks("group_1").first())
        assertEquals(emptyList<CachedGroupTaskReminderEntity>(), cacheDao.getTaskReminders(task.id))
    }

    @Test
    fun givenTaskReplacementAndDeletion_whenCacheRowsChange_thenPendingCommandsRemain() = runTest {
        val cacheDao = database.collaborationCacheDao()
        val pendingDao = database.pendingGroupCommandDao()
        cacheDao.upsertGroup(group())
        val task = task(id = "task_1", groupId = "group_1", createdAt = 1L)
        pendingDao.enqueue(command(id = "command_task", aggregateId = task.id, createdAt = 2L))

        cacheDao.replaceTasks("group_1", listOf(task))
        cacheDao.deleteTask("group_1", task.id)

        assertEquals(1, pendingDao.pendingCount())
        assertEquals(listOf("command_task"), pendingDao.getCommandsForAggregate(task.id).map { it.id })
    }

    @Test
    fun givenCrossGroupTaskSnapshot_whenReplacing_thenExistingTargetRowsRemain() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertGroup(group())
        val existing = task(id = "task_existing", groupId = "group_1", createdAt = 1L)
        cacheDao.upsertTask(existing)

        var rejected = false
        try {
            cacheDao.replaceTasks(
                groupId = "group_1",
                tasks = listOf(task(id = "task_wrong", groupId = "group_2", createdAt = 2L))
            )
        } catch (_: IllegalArgumentException) {
            rejected = true
        }

        assertTrue(rejected)
        assertEquals(listOf(existing.id), cacheDao.observeTasks("group_1").first().map { it.id })
    }

    @Test
    fun givenCacheReplacement_whenMembersReplaced_thenPendingCommandsRemain() = runTest {
        val cacheDao = database.collaborationCacheDao()
        val pendingDao = database.pendingGroupCommandDao()
        cacheDao.upsertGroup(group())
        pendingDao.enqueue(command(id = "command_group", aggregateId = "task_1", createdAt = 100L))
        pendingDao.enqueue(command(id = "command_other", aggregateId = "task_2", createdAt = 200L))

        cacheDao.replaceMembers(
            "group_1",
            listOf(CachedGroupMemberEntity("group_1", "user_1", "MEMBER", 1L))
        )
        cacheDao.replaceMembers("group_1", emptyList())

        assertEquals(2, pendingDao.pendingCount())
        assertEquals(
            listOf("command_group"),
            pendingDao.getCommandsForAggregate("task_1").map { it.id }
        )
    }

    @Test
    fun givenCommandsForAggregateWithSameCreatedAt_whenQueried_thenOrdersByEnqueueSequence() = runTest {
        val pendingDao = database.pendingGroupCommandDao()
        pendingDao.enqueue(command(id = "command_b", aggregateId = "task_1", createdAt = 700L))
        pendingDao.enqueue(command(id = "command_a", aggregateId = "task_1", createdAt = 700L))
        pendingDao.enqueue(command(id = "command_other", aggregateId = "task_2", createdAt = 100L))

        val orderedIds = pendingDao.getCommandsForAggregate("task_1").map { it.id }

        assertEquals(listOf("command_b", "command_a"), orderedIds)
    }

    @Test
    fun givenPendingCommandsWithLexicallyConflictingIds_whenObserved_thenOrdersByEnqueueSequence() = runTest {
        val pendingDao = database.pendingGroupCommandDao()
        pendingDao.enqueue(command(id = "z_first", aggregateId = "task_1", createdAt = 700L))
        pendingDao.enqueue(command(id = "a_second", aggregateId = "task_2", createdAt = 700L))
        pendingDao.enqueue(
            command(
                id = "m_syncing",
                aggregateId = "task_3",
                createdAt = 700L,
                state = "SYNCING"
            )
        )

        assertEquals(
            listOf("z_first", "a_second"),
            pendingDao.observePending().first().map { it.id }
        )
        assertEquals(
            listOf("z_first", "a_second"),
            pendingDao.observeByState(PendingGroupCommandState.PENDING.name).first().map { it.id }
        )
    }

    @Test
    fun givenCreateEditStartWithLexicallyConflictingIds_whenReopened_thenPreservesEnqueueOrder() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database.close()
        context.deleteDatabase(REOPEN_TEST_DATABASE_NAME)
        database = Room.databaseBuilder(context, CueDatabase::class.java, REOPEN_TEST_DATABASE_NAME)
            .allowMainThreadQueries()
            .build()

        val pendingDao = database.pendingGroupCommandDao()
        pendingDao.enqueue(
            command(
                id = "z_create",
                aggregateId = "task_1",
                commandType = "CREATE_TASK",
                createdAt = 42L
            )
        )
        pendingDao.enqueue(
            command(
                id = "a_edit",
                aggregateId = "task_1",
                commandType = "EDIT_OWN_TASK_CONTENT",
                createdAt = 42L
            )
        )
        pendingDao.enqueue(
            command(
                id = "m_start",
                aggregateId = "task_1",
                commandType = "START_TASK",
                createdAt = 42L
            )
        )

        val expectedIds = listOf("z_create", "a_edit", "m_start")
        val expectedSequence = listOf(1L, 2L, 3L)
        val commandsBeforeReopen = pendingDao.getCommandsForAggregate("task_1")
        assertEquals(expectedIds, commandsBeforeReopen.map { it.id })
        assertEquals(expectedSequence, commandsBeforeReopen.map { it.enqueueSequence })

        database.close()
        database = Room.databaseBuilder(context, CueDatabase::class.java, REOPEN_TEST_DATABASE_NAME)
            .allowMainThreadQueries()
            .build()

        val commandsAfterReopen = database.pendingGroupCommandDao().getCommandsForAggregate("task_1")
        assertEquals(expectedIds, commandsAfterReopen.map { it.id })
        assertEquals(expectedSequence, commandsAfterReopen.map { it.enqueueSequence })
    }

    private fun group(
        id: String = "group_1",
        name: String = "Group"
    ) = CachedCollaborationGroupEntity(
        id = id,
        name = name,
        description = null,
        createdBy = "user_1",
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun task(
        id: String,
        groupId: String = "group_1",
        createdAt: Long
    ) = CachedGroupTaskEntity(
        id = id,
        groupId = groupId,
        title = id,
        description = null,
        createdBy = "user_1",
        assigneeId = "user_1",
        dueAt = 10_000L,
        status = "TODO",
        version = 1L,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun member(
        groupId: String = "group_1",
        userId: String,
        displayName: String? = null,
        avatarUrl: String? = null
    ) = CachedGroupMemberEntity(
        groupId = groupId,
        userId = userId,
        role = "MEMBER",
        joinedAt = 1L,
        displayName = displayName,
        avatarUrl = avatarUrl
    )

    private fun invite(id: String, groupId: String, createdAt: Long) = CachedGroupInviteEntity(
        id = id,
        groupId = groupId,
        inviterId = "owner_1",
        inviteeUserId = "user_1",
        status = "PENDING",
        createdAt = createdAt,
        respondedAt = null
    )

    private fun command(
        id: String,
        aggregateId: String,
        createdAt: Long,
        commandType: String = "START_TASK",
        state: String = PendingGroupCommandState.PENDING.name
    ) = PendingGroupCommandEntity(
        id = id,
        commandType = commandType,
        aggregateId = aggregateId,
        payloadJson = "{}",
        payloadVersion = 1,
        expectedVersion = null,
        createdAt = createdAt,
        enqueueSequence = 0L,
        attemptCount = 0,
        state = state,
        lastError = null
    )

    private companion object {
        const val REOPEN_TEST_DATABASE_NAME = "collaboration-command-order-test.db"
    }
}
