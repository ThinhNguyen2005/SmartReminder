package com.smartreminder.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.PendingGroupCommandEntity
import com.smartreminder.data.local.room.model.collaboration.PendingGroupCommandState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun givenTasksWithSameCreatedAt_whenObserved_thenOrdersByStableIdTieBreaker() = runTest {
        val cacheDao = database.collaborationCacheDao()
        cacheDao.upsertGroup(group())
        cacheDao.upsertTask(task(id = "task_b", createdAt = 500L))
        cacheDao.upsertTask(task(id = "task_a", createdAt = 500L))

        val observedIds = cacheDao.observeTasks("group_1").first().map { it.id }

        assertEquals(listOf("task_a", "task_b"), observedIds)
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

    private fun task(id: String, createdAt: Long) = CachedGroupTaskEntity(
        id = id,
        groupId = "group_1",
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
