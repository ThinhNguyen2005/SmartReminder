package com.smartreminder.domain.model.collaboration

import com.smartreminder.domain.model.collaboration.ids.GroupTaskId

data class GroupTaskReminder(
    val taskId: GroupTaskId,
    val offsetSeconds: Long
) {
    init {
        require(offsetSeconds > 0) { "GroupTaskReminder offsetSeconds must be positive" }
    }
}
