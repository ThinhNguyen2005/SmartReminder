package com.smartreminder.data.repository.collaboration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartreminder.data.local.room.CueDatabase
import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
import com.smartreminder.data.local.room.repository.RoomCollaborationCacheDataSource
import com.smartreminder.data.remote.collaboration.CollaborationGroupRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationInviteRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationMemberRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationMutationEnvelopeRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationTaskDetailsRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationTaskRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationTaskReminderRemoteDto
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.CancelGroupTaskCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CompleteGroupTaskCommand
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.CreateGroupTaskCommand
import com.smartreminder.domain.repository.DeclineInviteCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.EditGroupTaskCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.ReassignGroupTaskCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.ReopenGroupTaskCommand
import com.smartreminder.domain.repository.StartGroupTaskCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCollaborationRepositoryTaskRefreshTest {

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
    fun refreshTasksReplacesRoomTaskAndReminderAggregateAtomically() = runTest {
        val cache = RoomCollaborationCacheDataSource(database)
        cache.replaceGroup(cachedGroup(), emptyList())
        val remote = RoomTaskRemoteDataSource().apply {
            taskDetails = listOf(taskDetails(title = "Remote task"))
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        assertEquals(
            CollaborationMutationResult.Applied,
            repository.refreshTasks(CollaborationGroupId("group-1"))
        )
        assertEquals(
            listOf("Remote task"),
            repository.observeTasks(CollaborationGroupId("group-1"))
                .first()
                .map { it.title }
        )
        assertEquals(
            listOf(60L, 300L),
            repository.observeTaskDetails(CollaborationGroupId("group-1"))
                .first()
                .single()
                .reminders
                .map { it.offsetSeconds }
        )
        assertEquals(
            listOf("task-1"),
            database.collaborationCacheDao().observeTasks("group-1")
                .first()
                .map(CachedGroupTaskEntity::id)
        )
        assertEquals(
            listOf(60L, 300L),
            database.collaborationCacheDao().getTaskReminders("task-1")
                .map(CachedGroupTaskReminderEntity::offsetSeconds)
                .sorted()
        )
    }

    @Test
    fun appliedTaskMutationRefreshesRoomAggregateFromAuthoritativeRemote() = runTest {
        val cache = RoomCollaborationCacheDataSource(database)
        cache.replaceGroup(cachedGroup(), emptyList())
        cache.replaceTasks(
            groupId = "group-1",
            tasks = listOf(cachedTask(title = "Cached task")),
            reminders = listOf(CachedGroupTaskReminderEntity("task-1", 60L))
        )
        val remote = RoomTaskRemoteDataSource().apply {
            taskDetails = listOf(taskDetails(title = "After mutation"))
        }
        val repository = DefaultCollaborationRepository(cache, remote) { true }

        val result = repository.startTask(StartGroupTaskCommand(GroupTaskId("task-1"), 0L))

        assertEquals(CollaborationMutationResult.Applied, result)
        assertEquals(listOf("startTask"), remote.taskMutationCalls)
        assertEquals(
            listOf("After mutation"),
            repository.observeTasks(CollaborationGroupId("group-1"))
                .first()
                .map { it.title }
        )
    }

    private fun cachedGroup() = CachedCollaborationGroupEntity(
        id = "group-1",
        name = "Group",
        description = null,
        createdBy = "owner-1",
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun cachedTask(title: String) = CachedGroupTaskEntity(
        id = "task-1",
        groupId = "group-1",
        title = title,
        description = null,
        createdBy = "owner-1",
        assigneeId = "member-1",
        dueAt = Instant.parse("2026-09-12T11:00:00Z").toEpochMilli(),
        status = "TODO",
        version = 0L,
        createdAt = Instant.parse("2026-09-10T10:00:00Z").toEpochMilli(),
        updatedAt = Instant.parse("2026-09-10T10:00:00Z").toEpochMilli()
    )
}

private class RoomTaskRemoteDataSource : GroupOnlyRemoteDataSource() {
    var taskDetails: List<CollaborationTaskDetailsRemoteDto> = emptyList()
    val taskMutationCalls = mutableListOf<String>()

    override suspend fun fetchGroups(): List<CollaborationGroupRemoteDto> = emptyList()
    override suspend fun fetchGroup(groupId: String): CollaborationGroupRemoteDto? = null
    override suspend fun fetchMembers(groupId: String): List<CollaborationMemberRemoteDto> = emptyList()
    override suspend fun fetchInvites(): List<CollaborationInviteRemoteDto> = emptyList()
    override suspend fun fetchTaskDetails(groupId: String): List<CollaborationTaskDetailsRemoteDto> = taskDetails

    override suspend fun createGroup(command: CreateGroupCommand) = applied()
    override suspend fun updateGroup(command: UpdateGroupCommand) = applied()
    override suspend fun inviteMember(command: InviteMemberCommand) = applied()
    override suspend fun acceptInvite(command: AcceptInviteCommand) = applied()
    override suspend fun declineInvite(command: DeclineInviteCommand) = applied()
    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand) = applied()
    override suspend fun removeMember(command: RemoveMemberCommand) = applied()
    override suspend fun transferOwnership(command: TransferOwnershipCommand) = applied()
    override suspend fun leaveGroup(command: LeaveGroupCommand) = applied()
    override suspend fun deleteGroup(command: DeleteGroupCommand) = applied()

    override suspend fun createTask(command: CreateGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "createTask"
        return applied()
    }

    override suspend fun editTask(command: EditGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "editTask"
        return applied()
    }

    override suspend fun reassignTask(command: ReassignGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "reassignTask"
        return applied()
    }

    override suspend fun startTask(command: StartGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "startTask"
        return applied()
    }

    override suspend fun completeTask(command: CompleteGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "completeTask"
        return applied()
    }

    override suspend fun cancelTask(command: CancelGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "cancelTask"
        return applied()
    }

    override suspend fun reopenTask(command: ReopenGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto {
        taskMutationCalls += "reopenTask"
        return applied()
    }

    private fun applied() = CollaborationMutationEnvelopeRemoteDto(status = "APPLIED")
}

private fun taskDetails(title: String) = CollaborationTaskDetailsRemoteDto(
    task = CollaborationTaskRemoteDto(
        id = "task-1",
        groupId = "group-1",
        title = title,
        description = null,
        createdBy = "owner-1",
        assigneeId = UserId("member-1").value,
        dueAt = "2026-09-12T11:00:00Z",
        status = "TODO",
        version = 0L,
        createdAt = "2026-09-10T10:00:00Z",
        updatedAt = "2026-09-10T10:00:00Z"
    ),
    reminders = listOf(
        CollaborationTaskReminderRemoteDto("task-1", 300L),
        CollaborationTaskReminderRemoteDto("task-1", 60L)
    )
)
