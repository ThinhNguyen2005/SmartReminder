package com.smartreminder.domain.model.collaboration.ids

@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "UserId value must not be blank" }
    }
}
