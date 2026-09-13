package com.smartreminder.data.remote.collaboration

import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupInviteStatus
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskDetails
import com.smartreminder.domain.model.collaboration.GroupTaskReminder
import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.CollaborationErrorCode
import com.smartreminder.domain.repository.CollaborationErrorEnvelope
import com.smartreminder.domain.repository.CollaborationMutationEnvelope
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CollaborationMutationStatus
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/** Typed task data returned inside a G3 mutation envelope. */
data class CollaborationTaskMutationResponse(
    val result: CollaborationMutationResult,
    val taskId: GroupTaskId? = null,
    val assigneeId: UserId? = null,
    val status: GroupTaskStatus? = null,
    val version: Long? = null,
    val currentVersion: Long? = null,
    val idempotent: Boolean? = null,
    val noOp: Boolean? = null
)

object CollaborationRemoteMapper {

    fun toDomain(dto: CollaborationGroupRemoteDto): CollaborationGroup =
        CollaborationGroup(
            id = CollaborationGroupId(dto.id),
            name = dto.name,
            description = dto.description,
            createdBy = UserId(dto.createdBy),
            createdAt = parseInstant(dto.createdAt, "created_at"),
            updatedAt = parseInstant(dto.updatedAt, "updated_at")
        )

    fun toCache(dto: CollaborationGroupRemoteDto): CachedCollaborationGroupEntity =
        CachedCollaborationGroupEntity(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            createdBy = dto.createdBy,
            createdAt = parseInstant(dto.createdAt, "created_at").toEpochMilli(),
            updatedAt = parseInstant(dto.updatedAt, "updated_at").toEpochMilli()
        )

    fun toDomain(dto: CollaborationMemberRemoteDto): GroupMember =
        GroupMember(
            groupId = CollaborationGroupId(dto.groupId),
            userId = UserId(dto.userId),
            role = parseRole(dto.role),
            joinedAt = parseInstant(dto.joinedAt, "joined_at"),
            displayName = dto.profile?.displayName,
            avatarUrl = dto.profile?.avatarUrl
        )

    fun toCache(dto: CollaborationMemberRemoteDto): CachedGroupMemberEntity =
        CachedGroupMemberEntity(
            groupId = dto.groupId,
            userId = dto.userId,
            role = parseRole(dto.role).name,
            joinedAt = parseInstant(dto.joinedAt, "joined_at").toEpochMilli(),
            displayName = dto.profile?.displayName,
            avatarUrl = dto.profile?.avatarUrl
        )

    fun toDomain(dto: CollaborationInviteRemoteDto): GroupInvite =
        GroupInvite(
            id = GroupInviteId(dto.id),
            groupId = CollaborationGroupId(dto.groupId),
            inviterId = UserId(dto.inviterId),
            inviteeUserId = UserId(dto.inviteeUserId),
            status = parseInviteStatus(dto.status),
            createdAt = parseInstant(dto.createdAt, "created_at"),
            respondedAt = dto.respondedAt?.let { parseInstant(it, "responded_at") }
        )

    fun toDomain(dto: CollaborationTaskRemoteDto): GroupTask = try {
        GroupTask(
            id = GroupTaskId(dto.id),
            groupId = CollaborationGroupId(dto.groupId),
            title = dto.title,
            description = dto.description,
            createdBy = UserId(dto.createdBy),
            assigneeId = UserId(dto.assigneeId),
            dueAt = parseInstant(dto.dueAt, "due_at"),
            status = parseTaskStatus(dto.status),
            version = dto.version,
            createdAt = parseInstant(dto.createdAt, "created_at"),
            updatedAt = parseInstant(dto.updatedAt, "updated_at")
        )
    } catch (failure: CollaborationMappingException) {
        throw failure
    } catch (failure: IllegalArgumentException) {
        throw CollaborationMappingException("Invalid collaboration task ${dto.id}", failure)
    }

    fun toDomain(dtos: List<CollaborationTaskRemoteDto>): List<GroupTask> =
        dtos.map(::toDomain)
            .sortedWith(compareBy<GroupTask> { it.createdAt }.thenBy { it.id.value })

    fun toDomain(dto: CollaborationTaskReminderRemoteDto): GroupTaskReminder = try {
        GroupTaskReminder(
            taskId = GroupTaskId(dto.taskId),
            offsetSeconds = dto.offsetSeconds
        )
    } catch (failure: IllegalArgumentException) {
        throw CollaborationMappingException(
            "Invalid reminder offset for collaboration task ${dto.taskId}",
            failure
        )
    }

    fun toDomain(
        dto: CollaborationTaskRemoteDto,
        expectedGroupId: String
    ): GroupTask {
        requireTaskGroup(dto, expectedGroupId)
        return toDomain(dto)
    }

    fun toCache(dto: CollaborationTaskRemoteDto): CachedGroupTaskEntity {
        val task = toDomain(dto)
        return CachedGroupTaskEntity(
            id = task.id.value,
            groupId = task.groupId.value,
            title = task.title,
            description = task.description,
            createdBy = task.createdBy.value,
            assigneeId = task.assigneeId.value,
            dueAt = task.dueAt.toEpochMilli(),
            status = task.status.name,
            version = task.version,
            createdAt = task.createdAt.toEpochMilli(),
            updatedAt = task.updatedAt.toEpochMilli()
        )
    }

    fun toCache(dto: CollaborationTaskReminderRemoteDto): CachedGroupTaskReminderEntity =
        CachedGroupTaskReminderEntity(
            taskId = dto.taskId,
            offsetSeconds = toDomain(dto).offsetSeconds
        )

    fun toDetailsDomain(
        task: CollaborationTaskRemoteDto,
        reminders: List<CollaborationTaskReminderRemoteDto>
    ): GroupTaskDetails {
        val domainTask = toDomain(task)
        val domainReminders = reminders
            .sortedWith(
                compareBy<CollaborationTaskReminderRemoteDto> { it.offsetSeconds }
                    .thenBy { it.taskId }
            )
            .map { reminder ->
                if (reminder.taskId != task.id) {
                    throw CollaborationMappingException(
                        "Reminder ${reminder.taskId} does not belong to task ${task.id}"
                    )
                }
                toDomain(reminder)
            }
        return try {
            GroupTaskDetails(task = domainTask, reminders = domainReminders)
        } catch (failure: IllegalArgumentException) {
            throw CollaborationMappingException(
                "Invalid reminder set for collaboration task ${task.id}",
                failure
            )
        }
    }

    fun toDetailsDomain(
        details: CollaborationTaskDetailsRemoteDto
    ): GroupTaskDetails = toDetailsDomain(details.task, details.reminders)

    /** Converts a single embedded PostgREST row without losing child ordering. */
    fun toDetailsRemote(
        dto: CollaborationTaskWithRemindersRemoteDto
    ): CollaborationTaskDetailsRemoteDto {
        val reminders = dto.reminders
            .sortedWith(
                compareBy<CollaborationTaskReminderRemoteDto> { it.offsetSeconds }
                    .thenBy { it.taskId }
            )
        reminders.forEach { reminder ->
            if (reminder.taskId != dto.id) {
                throw CollaborationMappingException(
                    "Reminder ${reminder.taskId} does not belong to task ${dto.id}"
                )
            }
        }
        return CollaborationTaskDetailsRemoteDto(
            task = CollaborationTaskRemoteDto(
                id = dto.id,
                groupId = dto.groupId,
                title = dto.title,
                description = dto.description,
                createdBy = dto.createdBy,
                assigneeId = dto.assigneeId,
                dueAt = dto.dueAt,
                status = dto.status,
                version = dto.version,
                createdAt = dto.createdAt,
                updatedAt = dto.updatedAt
            ),
            reminders = reminders
        )
    }

    fun toDetailsDomain(
        details: List<CollaborationTaskDetailsRemoteDto>
    ): List<GroupTaskDetails> = details
        .map(::toDetailsDomain)
        .sortedWith(compareBy<GroupTaskDetails> { it.task.createdAt }.thenBy { it.task.id.value })

    fun toCache(dto: CollaborationInviteRemoteDto): CachedGroupInviteEntity =
        CachedGroupInviteEntity(
            id = dto.id,
            groupId = dto.groupId,
            inviterId = dto.inviterId,
            inviteeUserId = dto.inviteeUserId,
            status = parseInviteStatus(dto.status).name,
            createdAt = parseInstant(dto.createdAt, "created_at").toEpochMilli(),
            respondedAt = dto.respondedAt?.let { parseInstant(it, "responded_at") }?.toEpochMilli()
        )

    fun fromCache(entity: CachedCollaborationGroupEntity): CollaborationGroup =
        CollaborationGroup(
            id = CollaborationGroupId(entity.id),
            name = entity.name,
            description = entity.description,
            createdBy = UserId(entity.createdBy),
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt)
        )

    fun fromCache(entity: CachedGroupMemberEntity): GroupMember =
        GroupMember(
            groupId = CollaborationGroupId(entity.groupId),
            userId = UserId(entity.userId),
            role = parseRole(entity.role),
            joinedAt = Instant.ofEpochMilli(entity.joinedAt),
            displayName = entity.displayName,
            avatarUrl = entity.avatarUrl
        )

    fun fromCache(entity: CachedGroupInviteEntity): GroupInvite =
        GroupInvite(
            id = GroupInviteId(entity.id),
            groupId = CollaborationGroupId(entity.groupId),
            inviterId = UserId(entity.inviterId),
            inviteeUserId = UserId(entity.inviteeUserId),
            status = parseInviteStatus(entity.status),
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            respondedAt = entity.respondedAt?.let(Instant::ofEpochMilli)
        )

    fun fromCache(entity: CachedGroupTaskEntity): GroupTask =
        GroupTask(
            id = GroupTaskId(entity.id),
            groupId = CollaborationGroupId(entity.groupId),
            title = entity.title,
            description = entity.description,
            createdBy = UserId(entity.createdBy),
            assigneeId = UserId(entity.assigneeId),
            dueAt = Instant.ofEpochMilli(entity.dueAt),
            status = parseTaskStatus(entity.status),
            version = entity.version,
            createdAt = Instant.ofEpochMilli(entity.createdAt),
            updatedAt = Instant.ofEpochMilli(entity.updatedAt)
        )

    fun toMutationResult(dto: CollaborationMutationEnvelopeRemoteDto): CollaborationMutationResult =
        CollaborationMutationResult.fromEnvelope(
            CollaborationMutationEnvelope(
                status = parseMutationStatus(dto.status),
                error = dto.error?.let {
                    CollaborationErrorEnvelope(
                        code = parseErrorCode(it.code),
                        detail = it.detail
                    )
                }
            )
        )

    /** Maps a task RPC envelope while retaining its typed response data. */
    fun toTaskMutationResponse(
        dto: CollaborationMutationEnvelopeRemoteDto
    ): CollaborationTaskMutationResponse {
        val data = dto.data
        return CollaborationTaskMutationResponse(
            result = toTaskMutationResult(dto),
            taskId = data.stringValue("task_id")?.let(::parseTaskId),
            assigneeId = data.stringValue("assignee_id")?.let(::parseUserId),
            status = data.stringValue("status")?.let(::parseTaskStatus),
            version = data.longValue("version"),
            currentVersion = data.longValue("current_version"),
            idempotent = data.booleanValue("idempotent"),
            noOp = data.booleanValue("no_op")
        )
    }

    /** G2 membership is online-only; a legacy queue response is never success here. */
    fun toG2MutationResult(dto: CollaborationMutationEnvelopeRemoteDto): CollaborationMutationResult =
        when (val result = toMutationResult(dto)) {
            CollaborationMutationResult.Queued -> CollaborationMutationResult.Failure(
                com.smartreminder.domain.repository.CollaborationError.InvalidState(
                    "Queued collaboration membership response is not supported"
                )
            )
            else -> result
        }

    /** G3 task commands are online-only; a queued envelope is never accepted. */
    fun toTaskMutationResult(dto: CollaborationMutationEnvelopeRemoteDto): CollaborationMutationResult =
        when (val result = toMutationResult(dto)) {
            CollaborationMutationResult.Queued -> CollaborationMutationResult.Failure(
                com.smartreminder.domain.repository.CollaborationError.InvalidState(
                    "Queued collaboration task response is not supported"
                )
            )
            else -> result
        }

    /** Maps the create RPC envelope and preserves Task 2's typed group id payload. */
    fun toCreateGroupMutationResult(
        dto: CollaborationMutationEnvelopeRemoteDto
    ): CollaborationMutationResult {
        val result = toG2MutationResult(dto)
        if (result !== CollaborationMutationResult.Applied) return result

        val groupId = (dto.data["group_id"] as? JsonPrimitive)?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?.let(::CollaborationGroupId)
        return groupId?.let(CollaborationMutationResult::Created)
            ?: CollaborationMutationResult.Applied
    }

    fun toMutationEnvelope(dto: CollaborationMutationEnvelopeRemoteDto): CollaborationMutationEnvelope =
        CollaborationMutationEnvelope(
            status = parseMutationStatus(dto.status),
            error = dto.error?.let {
                CollaborationErrorEnvelope(parseErrorCode(it.code), it.detail)
            }
        )

    private fun parseRole(raw: String): GroupRole = when (raw.uppercase(Locale.ROOT)) {
        "OWNER" -> GroupRole.OWNER
        "ADMIN" -> GroupRole.ADMIN
        "MEMBER" -> GroupRole.MEMBER
        else -> throw CollaborationMappingException("Unknown collaboration member role: $raw")
    }

    private fun parseInviteStatus(raw: String): GroupInviteStatus = when (raw.uppercase(Locale.ROOT)) {
        "PENDING" -> GroupInviteStatus.PENDING
        "ACCEPTED" -> GroupInviteStatus.ACCEPTED
        "DECLINED" -> GroupInviteStatus.DECLINED
        else -> throw CollaborationMappingException("Unknown collaboration invite status: $raw")
    }

    private fun parseTaskStatus(raw: String): GroupTaskStatus = when (raw.uppercase(Locale.ROOT)) {
        "TODO" -> GroupTaskStatus.TODO
        "IN_PROGRESS" -> GroupTaskStatus.IN_PROGRESS
        "COMPLETED" -> GroupTaskStatus.COMPLETED
        "CANCELLED" -> GroupTaskStatus.CANCELLED
        else -> throw CollaborationMappingException("Unknown collaboration task status: $raw")
    }

    private fun parseMutationStatus(raw: String): CollaborationMutationStatus = when (raw.uppercase(Locale.ROOT)) {
        "APPLIED" -> CollaborationMutationStatus.APPLIED
        "QUEUED" -> CollaborationMutationStatus.QUEUED
        "NETWORK_REQUIRED" -> CollaborationMutationStatus.NETWORK_REQUIRED
        "CONFLICT" -> CollaborationMutationStatus.CONFLICT
        "NOT_AUTHORIZED" -> CollaborationMutationStatus.NOT_AUTHORIZED
        "NOT_FOUND" -> CollaborationMutationStatus.NOT_FOUND
        "VALIDATION" -> CollaborationMutationStatus.VALIDATION
        "MEMBER_NOT_FOUND" -> CollaborationMutationStatus.MEMBER_NOT_FOUND
        "ALREADY_MEMBER" -> CollaborationMutationStatus.ALREADY_MEMBER
        "INVITE_ALREADY_PENDING" -> CollaborationMutationStatus.INVITE_ALREADY_PENDING
        "INVALID_STATE" -> CollaborationMutationStatus.INVALID_STATE
        "FAILURE" -> CollaborationMutationStatus.FAILURE
        else -> throw CollaborationMappingException("Unknown collaboration mutation status: $raw")
    }

    private fun parseErrorCode(raw: String): CollaborationErrorCode = when (raw.uppercase(Locale.ROOT)) {
        "NETWORK_UNAVAILABLE" -> CollaborationErrorCode.NETWORK_UNAVAILABLE
        "NOT_AUTHORIZED" -> CollaborationErrorCode.NOT_AUTHORIZED
        "NOT_FOUND" -> CollaborationErrorCode.NOT_FOUND
        "CONFLICT" -> CollaborationErrorCode.CONFLICT
        "INVALID_STATE" -> CollaborationErrorCode.INVALID_STATE
        "VALIDATION" -> CollaborationErrorCode.VALIDATION
        "MEMBER_NOT_FOUND" -> CollaborationErrorCode.MEMBER_NOT_FOUND
        "INVITE_ALREADY_PENDING" -> CollaborationErrorCode.INVITE_ALREADY_PENDING
        "ALREADY_MEMBER" -> CollaborationErrorCode.ALREADY_MEMBER
        "SYNC_REJECTED" -> CollaborationErrorCode.SYNC_REJECTED
        else -> CollaborationErrorCode.UNKNOWN
    }

    private fun parseInstant(raw: String, field: String): Instant = try {
        Instant.parse(raw)
    } catch (failure: DateTimeParseException) {
        throw CollaborationMappingException("Invalid collaboration $field timestamp: $raw", failure)
    }

    private fun requireTaskGroup(dto: CollaborationTaskRemoteDto, expectedGroupId: String) {
        if (dto.groupId != expectedGroupId) {
            throw CollaborationMappingException(
                "Task ${dto.id} belongs to group ${dto.groupId}, not $expectedGroupId"
            )
        }
    }

    private fun parseTaskId(raw: String): GroupTaskId = try {
        GroupTaskId(raw)
    } catch (failure: IllegalArgumentException) {
        throw CollaborationMappingException("Invalid task_id in collaboration mutation data", failure)
    }

    private fun parseUserId(raw: String): UserId = try {
        UserId(raw)
    } catch (failure: IllegalArgumentException) {
        throw CollaborationMappingException("Invalid assignee_id in collaboration mutation data", failure)
    }

    private fun JsonObject.stringValue(key: String): String? {
        if (!containsKey(key)) return null
        val primitive = this[key] as? JsonPrimitive
            ?: throw CollaborationMappingException("Invalid $key in collaboration mutation data")
        return primitive.contentOrNull ?: throw CollaborationMappingException(
            "Invalid $key in collaboration mutation data"
        )
    }

    private fun JsonObject.longValue(key: String): Long? {
        if (!containsKey(key)) return null
        val primitive = this[key] as? JsonPrimitive
            ?: throw CollaborationMappingException("Invalid $key in collaboration mutation data")
        return primitive.longOrNull ?: throw CollaborationMappingException(
            "Invalid $key in collaboration mutation data"
        )
    }

    private fun JsonObject.booleanValue(key: String): Boolean? {
        if (!containsKey(key)) return null
        val primitive = this[key] as? JsonPrimitive
            ?: throw CollaborationMappingException("Invalid $key in collaboration mutation data")
        return primitive.booleanOrNull ?: throw CollaborationMappingException(
            "Invalid $key in collaboration mutation data"
        )
    }
}
