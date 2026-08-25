package com.smartreminder.domain.model.schedule.ids

@JvmInline
value class ScheduleGroupId(val value: String) {
    init {
        require(value.isNotBlank()) { "ScheduleGroupId value must not be blank" }
    }
}
