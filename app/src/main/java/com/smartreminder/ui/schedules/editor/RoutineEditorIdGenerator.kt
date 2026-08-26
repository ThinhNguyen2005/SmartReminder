package com.smartreminder.ui.schedules.editor

import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.RoutineItemId
import java.util.UUID

interface RoutineEditorIdGenerator {
    fun newRoutineId(): RoutineId
    fun newRoutineItemId(): RoutineItemId
}

object UuidRoutineEditorIdGenerator : RoutineEditorIdGenerator {
    override fun newRoutineId(): RoutineId = RoutineId(UUID.randomUUID().toString())

    override fun newRoutineItemId(): RoutineItemId = RoutineItemId(UUID.randomUUID().toString())
}
