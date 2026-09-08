package com.smartreminder.domain.model.collaboration

import com.smartreminder.domain.model.collaboration.ids.UserId

sealed interface GroupReminderAudience {
    data class Member(val userId: UserId) : GroupReminderAudience
    data object Everyone : GroupReminderAudience
}
