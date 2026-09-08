package com.smartreminder.domain.model.collaboration

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupReminderId
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.time.Instant

data class GroupReminder(
    val id: GroupReminderId,
    val groupId: CollaborationGroupId,
    val title: String,
    val description: String? = null,
    val createdBy: UserId,
    val audience: GroupReminderAudience,
    val remindAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(title.isNotBlank()) { "GroupReminder title must not be blank" }
    }
}
