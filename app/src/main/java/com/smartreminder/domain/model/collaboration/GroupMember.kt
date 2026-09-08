package com.smartreminder.domain.model.collaboration

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.time.Instant

data class GroupMember(
    val groupId: CollaborationGroupId,
    val userId: UserId,
    val role: GroupRole,
    val joinedAt: Instant
)
