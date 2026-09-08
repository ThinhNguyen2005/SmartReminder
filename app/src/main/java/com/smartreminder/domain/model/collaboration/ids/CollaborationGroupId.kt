package com.smartreminder.domain.model.collaboration.ids

@JvmInline
value class CollaborationGroupId(val value: String) {
    init {
        require(value.isNotBlank()) { "CollaborationGroupId value must not be blank" }
    }
}
