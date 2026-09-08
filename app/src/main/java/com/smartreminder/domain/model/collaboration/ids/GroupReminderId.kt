package com.smartreminder.domain.model.collaboration.ids

@JvmInline
value class GroupReminderId(val value: String) {
    init {
        require(value.isNotBlank()) { "GroupReminderId value must not be blank" }
    }
}
