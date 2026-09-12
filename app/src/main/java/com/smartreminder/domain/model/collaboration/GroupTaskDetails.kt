package com.smartreminder.domain.model.collaboration

/**
 * A cached task snapshot with its task-owned reminder offsets.
 *
 * Reminder offsets stay separate from [GroupTask] because they are a child
 * collection in both Room and Supabase, while this type gives detail readers
 * one consistent aggregate to consume.
 */
data class GroupTaskDetails(
    val task: GroupTask,
    val reminders: List<GroupTaskReminder> = emptyList()
) {
    init {
        require(reminders.all { it.taskId == task.id }) {
            "Every task reminder must belong to the task"
        }
        require(reminders.map(GroupTaskReminder::offsetSeconds).distinct().size == reminders.size) {
            "Task reminder offsets must be unique"
        }
    }
}
