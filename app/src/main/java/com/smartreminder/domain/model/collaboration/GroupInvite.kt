package com.smartreminder.domain.model.collaboration

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.time.Instant

data class GroupInvite(
    val id: GroupInviteId,
    val groupId: CollaborationGroupId,
    val inviterId: UserId,
    val inviteeUserId: UserId,
    val status: GroupInviteStatus,
    val createdAt: Instant,
    val respondedAt: Instant? = null
)
