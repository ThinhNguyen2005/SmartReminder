package com.smartreminder.data.local.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
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
    }

    @Test
    fun givenGroupsWithSameName_whenObserved_thenOrdersByStableIdTieBreaker() = runTest {
        database.collaborationCacheDao().upsertGroup(group(id = "group_b", name = "Same name"))
        database.collaborationCacheDao().upsertGroup(group(id = "group_a", name = "Same name"))

        val observedIds = database.collaborationCacheDao().observeGroups().first().map { it.id }

        assertEquals(listOf("group_a", "group_b"), observedIds)
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
        pendingDao.insert(command(id = "command_group", aggregateId = "task_1", createdAt = 100L))
        pendingDao.insert(command(id = "command_other", aggregateId = "task_2", createdAt = 200L))

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
    fun givenCommandsForAggregateWithSameCreatedAt_whenQueried_thenOrdersOldestFirstByStableId() = runTest {
        val pendingDao = database.pendingGroupCommandDao()
        pendingDao.insert(command(id = "command_b", aggregateId = "task_1", createdAt = 700L))
        pendingDao.insert(command(id = "command_a", aggregateId = "task_1", createdAt = 700L))
        pendingDao.insert(command(id = "command_other", aggregateId = "task_2", createdAt = 100L))

        val orderedIds = pendingDao.getCommandsForAggregate("task_1").map { it.id }

        assertEquals(listOf("command_a", "command_b"), orderedIds)
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

    private fun command(id: String, aggregateId: String, createdAt: Long) = PendingGroupCommandEntity(
        id = id,
        commandType = "START_TASK",
        aggregateId = aggregateId,
        payloadJson = "{}",
        payloadVersion = 1,
        expectedVersion = null,
        createdAt = createdAt,
        attemptCount = 0,
        state = PendingGroupCommandState.PENDING.name,
        lastError = null
    )
}
