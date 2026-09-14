package com.smartreminder.ui.groups.tasks

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import java.util.UUID

fun interface GroupTaskIdGenerator {
    fun nextTaskId(groupId: CollaborationGroupId): GroupTaskId
}

/** Production id source for client-generated task ids used by the idempotent create RPC. */
object UuidGroupTaskIdGenerator : GroupTaskIdGenerator {
    override fun nextTaskId(groupId: CollaborationGroupId): GroupTaskId =
        GroupTaskId(UUID.randomUUID().toString())
}
