package com.smartreminder.domain.model.schedule.ids

@JvmInline
value class RoutineItemId(val value: String) {
    init {
        require(value.isNotBlank()) { "RoutineItemId value must not be blank" }
    }
}
