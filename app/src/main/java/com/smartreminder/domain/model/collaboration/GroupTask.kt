package com.smartreminder.domain.model.collaboration

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.time.Instant

data class GroupTask(
    val id: GroupTaskId,
    val groupId: CollaborationGroupId,
    val title: String,
    val description: String? = null,
    val createdBy: UserId,
    val assigneeId: UserId,
    val dueAt: Instant,
    val status: GroupTaskStatus,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(title.isNotBlank()) { "GroupTask title must not be blank" }
        require(version >= 0) { "GroupTask version must not be negative" }
    }
}
