package com.smartreminder.domain.repository

import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.time.Instant

private const val MIN_TASK_REMINDER_COUNT = 1
private const val MAX_TASK_REMINDER_COUNT = 5

internal fun requireTaskTitle(title: String) {
    require(title.isNotBlank()) { "title must not be blank" }
}

internal fun requireTaskAssignee(assigneeId: UserId) {
    require(assigneeId.value.isNotBlank()) { "assigneeId must not be blank" }
}

internal fun requireTaskReminderOffsets(reminderOffsetsSeconds: List<Long>) {
    require(reminderOffsetsSeconds.size >= MIN_TASK_REMINDER_COUNT) {
        "at least 1 reminder offset required"
    }
    require(reminderOffsetsSeconds.size <= MAX_TASK_REMINDER_COUNT) {
        "at most 5 reminder offsets allowed"
    }
    require(reminderOffsetsSeconds.all { it > 0 }) {
        "all reminder offsets must be positive"
    }
    require(reminderOffsetsSeconds.distinct().size == reminderOffsetsSeconds.size) {
        "reminder offsets must be unique"
    }
}

internal fun requireExpectedTaskVersion(expectedVersion: Long) {
    require(expectedVersion >= 0) { "expectedVersion must not be negative" }
}

/** Full edit of an existing task, including its assignee, deadline, and offsets. */
data class EditGroupTaskCommand(
    val taskId: GroupTaskId,
    val title: String,
    val description: String? = null,
    val assigneeId: UserId,
    val dueAt: Instant,
    val reminderOffsetsSeconds: List<Long>,
    val expectedVersion: Long
) {
    init {
        requireTaskTitle(title)
        requireTaskAssignee(assigneeId)
        requireTaskReminderOffsets(reminderOffsetsSeconds)
        requireExpectedTaskVersion(expectedVersion)
    }
}

/** Changes the sole assignee of an existing task. */
data class ReassignGroupTaskCommand(
    val taskId: GroupTaskId,
    val assigneeId: UserId,
    val expectedVersion: Long
) {
    val newAssigneeId: UserId
        get() = assigneeId

    init {
        requireTaskAssignee(assigneeId)
        requireExpectedTaskVersion(expectedVersion)
    }
}

data class StartGroupTaskCommand(
    val taskId: GroupTaskId,
    val expectedVersion: Long
) {
    init {
        requireExpectedTaskVersion(expectedVersion)
    }
}

data class CompleteGroupTaskCommand(
    val taskId: GroupTaskId,
    val expectedVersion: Long
) {
    init {
        requireExpectedTaskVersion(expectedVersion)
    }
}

data class CancelGroupTaskCommand(
    val taskId: GroupTaskId,
    val expectedVersion: Long
) {
    init {
        requireExpectedTaskVersion(expectedVersion)
    }
}

data class ReopenGroupTaskCommand(
    val taskId: GroupTaskId,
    val expectedVersion: Long
) {
    init {
        requireExpectedTaskVersion(expectedVersion)
    }
}
