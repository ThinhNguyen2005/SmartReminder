package com.smartreminder.data.remote.collaboration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Primitive PostgREST representation of a collaboration group. */
@Serializable
data class CollaborationGroupRemoteDto(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

/** User profile projection joined by the data source for a member row. */
@Serializable
data class UserProfileRemoteDto(
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null
)

/** Primitive group membership row plus its optional profile projection. */
@Serializable
data class CollaborationMemberRemoteDto(
    @SerialName("group_id") val groupId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    @SerialName("joined_at") val joinedAt: String,
    @SerialName("user_profiles") val profile: UserProfileRemoteDto? = null
)

/** Primitive invitation row returned by the read-side PostgREST query. */
@Serializable
data class CollaborationInviteRemoteDto(
    val id: String,
    @SerialName("group_id") val groupId: String,
    @SerialName("inviter_id") val inviterId: String,
    @SerialName("invitee_user_id") val inviteeUserId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("responded_at") val respondedAt: String? = null
)

/** Primitive PostgREST representation of a task row. */
@Serializable
data class CollaborationTaskRemoteDto(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val title: String,
    val description: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("assignee_id") val assigneeId: String,
    @SerialName("due_at") val dueAt: String,
    val status: String,
    val version: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

/** Primitive PostgREST representation of a task-owned reminder offset. */
@Serializable
data class CollaborationTaskReminderRemoteDto(
    @SerialName("task_id") val taskId: String,
    @SerialName("offset_seconds") val offsetSeconds: Long
)

/** A task row paired with its task-owned reminder offsets. */
data class CollaborationTaskDetailsRemoteDto(
    val task: CollaborationTaskRemoteDto,
    val reminders: List<CollaborationTaskReminderRemoteDto> = emptyList()
)

/**
 * PostgREST shape for a task row with its child offsets embedded in the same
 * snapshot. The relation name is deliberately kept explicit because this is
 * the JSON key emitted by `select=*,group_task_reminders(*)`.
 */
@Serializable
data class CollaborationTaskWithRemindersRemoteDto(
    val id: String,
    @SerialName("group_id") val groupId: String,
    val title: String,
    val description: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("assignee_id") val assigneeId: String,
    @SerialName("due_at") val dueAt: String,
    val status: String,
    val version: Long,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("group_task_reminders")
    val reminders: List<CollaborationTaskReminderRemoteDto> = emptyList()
)

/** Backward-compatible names for callers that use the domain model prefix. */
typealias GroupTaskRemoteDto = CollaborationTaskRemoteDto
typealias GroupTaskReminderRemoteDto = CollaborationTaskReminderRemoteDto

@Serializable
data class CollaborationErrorRemoteDto(
    val code: String,
    val detail: String? = null
)

/** Exact JSON envelope returned by collaboration membership and task RPCs. */
@Serializable
data class CollaborationMutationEnvelopeRemoteDto(
    val status: String,
    val error: CollaborationErrorRemoteDto? = null,
    val data: JsonObject = JsonObject(emptyMap())
)
