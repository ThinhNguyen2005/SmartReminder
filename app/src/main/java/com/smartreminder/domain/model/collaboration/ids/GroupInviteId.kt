package com.smartreminder.domain.model.collaboration.ids

@JvmInline
value class GroupInviteId(val value: String) {
    init {
        require(value.isNotBlank()) { "GroupInviteId value must not be blank" }
    }
}
