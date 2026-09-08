package com.smartreminder.domain.model.collaboration

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.time.Instant

data class CollaborationGroup(
    val id: CollaborationGroupId,
    val name: String,
    val description: String? = null,
    val createdBy: UserId,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(name.isNotBlank()) { "CollaborationGroup name must not be blank" }
    }
}
