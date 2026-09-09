package com.smartreminder.domain.repository

import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class CreateGroupTaskCommand(
    val taskId: GroupTaskId,
    val groupId: CollaborationGroupId,
    val title: String,
    val description: String? = null,
    val assigneeId: UserId,
    val dueAt: Instant,
    val reminderOffsetsSeconds: List<Long>
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(reminderOffsetsSeconds.isNotEmpty()) { "at least 1 reminder offset required" }
        require(reminderOffsetsSeconds.size <= 5) { "at most 5 reminder offsets allowed" }
        require(reminderOffsetsSeconds.all { it > 0 }) { "all reminder offsets must be positive" }
        require(reminderOffsetsSeconds.distinct().size == reminderOffsetsSeconds.size) {
            "reminder offsets must be unique"
        }
    }
}

data class EditOwnGroupTaskContentCommand(
    val taskId: GroupTaskId,
    val title: String,
    val description: String? = null,
    val expectedVersion: Long
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(expectedVersion >= 0) { "expectedVersion must not be negative" }
    }
}

interface CollaborationRepository {
    fun observeGroups(): Flow<List<CollaborationGroup>>
    fun observeGroup(groupId: CollaborationGroupId): Flow<CollaborationGroup?>
    fun observeMembers(groupId: CollaborationGroupId): Flow<List<GroupMember>>
    fun observeTasks(groupId: CollaborationGroupId): Flow<List<GroupTask>>
    fun observeInvites(): Flow<List<GroupInvite>>

    suspend fun createTask(command: CreateGroupTaskCommand): CollaborationMutationResult
    suspend fun startTask(taskId: GroupTaskId): CollaborationMutationResult
    suspend fun completeTask(taskId: GroupTaskId): CollaborationMutationResult
    suspend fun editOwnTaskContent(command: EditOwnGroupTaskContentCommand): CollaborationMutationResult
}
