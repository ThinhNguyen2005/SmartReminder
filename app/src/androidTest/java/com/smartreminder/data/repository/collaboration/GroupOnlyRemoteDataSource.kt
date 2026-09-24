package com.smartreminder.data.repository.collaboration

import com.smartreminder.data.remote.collaboration.CollaborationMutationEnvelopeRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationRemoteDataSource
import com.smartreminder.data.remote.collaboration.CollaborationTaskDetailsRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationTaskRemoteDto
import com.smartreminder.data.remote.collaboration.CollaborationTaskReminderRemoteDto
import com.smartreminder.domain.repository.CancelGroupTaskCommand
import com.smartreminder.domain.repository.CompleteGroupTaskCommand
import com.smartreminder.domain.repository.CreateGroupTaskCommand
import com.smartreminder.domain.repository.EditGroupTaskCommand
import com.smartreminder.domain.repository.ReassignGroupTaskCommand
import com.smartreminder.domain.repository.ReopenGroupTaskCommand
import com.smartreminder.domain.repository.StartGroupTaskCommand

/** Test seam for membership-only remotes after the task transport contract became explicit. */
abstract class GroupOnlyRemoteDataSource : CollaborationRemoteDataSource {
    override suspend fun fetchTasks(groupId: String): List<CollaborationTaskRemoteDto> =
        error("task read is not used by this membership-only fake")

    override suspend fun fetchTask(groupId: String, taskId: String): CollaborationTaskRemoteDto? =
        error("task read is not used by this membership-only fake")

    override suspend fun fetchTaskReminders(
        groupId: String,
        taskId: String
    ): List<CollaborationTaskReminderRemoteDto> =
        error("task read is not used by this membership-only fake")

    override suspend fun fetchTaskDetails(
        groupId: String,
        taskId: String
    ): CollaborationTaskDetailsRemoteDto? =
        error("task read is not used by this membership-only fake")

    override suspend fun fetchTaskDetails(groupId: String): List<CollaborationTaskDetailsRemoteDto> =
        error("task read is not used by this membership-only fake")

    override suspend fun createTask(command: CreateGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        error("task mutation is not used by this membership-only fake")

    override suspend fun editTask(command: EditGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        error("task mutation is not used by this membership-only fake")

    override suspend fun reassignTask(command: ReassignGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        error("task mutation is not used by this membership-only fake")

    override suspend fun startTask(command: StartGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        error("task mutation is not used by this membership-only fake")

    override suspend fun completeTask(command: CompleteGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        error("task mutation is not used by this membership-only fake")

    override suspend fun cancelTask(command: CancelGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        error("task mutation is not used by this membership-only fake")

    override suspend fun reopenTask(command: ReopenGroupTaskCommand): CollaborationMutationEnvelopeRemoteDto =
        error("task mutation is not used by this membership-only fake")
}
