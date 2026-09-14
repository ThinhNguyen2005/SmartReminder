package com.smartreminder.ui.groups.tasks

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId

fun interface GroupTaskIdGenerator {
    fun nextTaskId(groupId: CollaborationGroupId): GroupTaskId
}
