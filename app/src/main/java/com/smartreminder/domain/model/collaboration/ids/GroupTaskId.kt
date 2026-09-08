package com.smartreminder.domain.model.collaboration.ids

@JvmInline
value class GroupTaskId(val value: String) {
    init {
        require(value.isNotBlank()) { "GroupTaskId value must not be blank" }
    }
}
