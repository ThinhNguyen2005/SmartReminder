package com.smartreminder.data.remote.collaboration

import com.smartreminder.data.local.room.entity.collaboration.CachedCollaborationGroupEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupInviteEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupMemberEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupInviteStatus
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
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
import java.util.Locale

object CollaborationRemoteMapper {

    fun toDomain(dto: CollaborationGroupRemoteDto): CollaborationGroup =
        CollaborationGroup(
            id = CollaborationGroupId(dto.id),
            name = dto.name,
            description = dto.description,
            createdBy = UserId(dto.createdBy),
            createdAt = Instant.parse(dto.createdAt),
            updatedAt = Instant.parse(dto.updatedAt)
        )

    fun toCache(dto: CollaborationGroupRemoteDto): CachedCollaborationGroupEntity =
        CachedCollaborationGroupEntity(
            id = dto.id,
            name = dto.name,
            description = dto.description,
            createdBy = dto.createdBy,
            createdAt = Instant.parse(dto.createdAt).toEpochMilli(),
            updatedAt = Instant.parse(dto.updatedAt).toEpochMilli()
        )

    fun toDomain(dto: CollaborationMemberRemoteDto): GroupMember =
        GroupMember(
            groupId = CollaborationGroupId(dto.groupId),
            userId = UserId(dto.userId),
            role = parseRole(dto.role),
            joinedAt = Instant.parse(dto.joinedAt)
        )

    fun toCache(dto: CollaborationMemberRemoteDto): CachedGroupMemberEntity =
        CachedGroupMemberEntity(
            groupId = dto.groupId,
            userId = dto.userId,
            role = parseRole(dto.role).name,
            joinedAt = Instant.parse(dto.joinedAt).toEpochMilli(),
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
            createdAt = Instant.parse(dto.createdAt),
            respondedAt = dto.respondedAt?.let(Instant::parse)
        )

    fun toCache(dto: CollaborationInviteRemoteDto): CachedGroupInviteEntity =
        CachedGroupInviteEntity(
            id = dto.id,
            groupId = dto.groupId,
            inviterId = dto.inviterId,
            inviteeUserId = dto.inviteeUserId,
            status = parseInviteStatus(dto.status).name,
            createdAt = Instant.parse(dto.createdAt).toEpochMilli(),
            respondedAt = dto.respondedAt?.let(Instant::parse)?.toEpochMilli()
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
            joinedAt = Instant.ofEpochMilli(entity.joinedAt)
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
        else -> error("Unknown collaboration member role: $raw")
    }

    private fun parseInviteStatus(raw: String): GroupInviteStatus = when (raw.uppercase(Locale.ROOT)) {
        "PENDING" -> GroupInviteStatus.PENDING
        "ACCEPTED" -> GroupInviteStatus.ACCEPTED
        "DECLINED" -> GroupInviteStatus.DECLINED
        else -> error("Unknown collaboration invite status: $raw")
    }

    private fun parseTaskStatus(raw: String): GroupTaskStatus = when (raw.uppercase(Locale.ROOT)) {
        "TODO" -> GroupTaskStatus.TODO
        "IN_PROGRESS" -> GroupTaskStatus.IN_PROGRESS
        "COMPLETED" -> GroupTaskStatus.COMPLETED
        "CANCELLED" -> GroupTaskStatus.CANCELLED
        else -> error("Unknown collaboration task status: $raw")
    }

    private fun parseMutationStatus(raw: String): CollaborationMutationStatus = when (raw.uppercase(Locale.ROOT)) {
        "APPLIED" -> CollaborationMutationStatus.APPLIED
        "QUEUED" -> CollaborationMutationStatus.QUEUED
        "NETWORK_REQUIRED" -> CollaborationMutationStatus.NETWORK_REQUIRED
        "CONFLICT" -> CollaborationMutationStatus.CONFLICT
        "NOT_AUTHORIZED" -> CollaborationMutationStatus.NOT_AUTHORIZED
        "MEMBER_NOT_FOUND" -> CollaborationMutationStatus.MEMBER_NOT_FOUND
        "ALREADY_MEMBER" -> CollaborationMutationStatus.ALREADY_MEMBER
        "INVITE_ALREADY_PENDING" -> CollaborationMutationStatus.INVITE_ALREADY_PENDING
        "INVALID_STATE" -> CollaborationMutationStatus.INVALID_STATE
        else -> CollaborationMutationStatus.FAILURE
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
}
