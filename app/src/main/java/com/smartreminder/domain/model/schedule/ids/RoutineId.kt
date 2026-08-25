package com.smartreminder.domain.model.schedule.ids

@JvmInline
value class RoutineId(val value: String) {
    init {
        require(value.isNotBlank()) { "RoutineId value must not be blank" }
    }
}
