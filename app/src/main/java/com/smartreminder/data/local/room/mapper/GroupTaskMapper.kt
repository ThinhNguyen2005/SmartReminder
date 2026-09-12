package com.smartreminder.data.local.room.mapper

import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskEntity
import com.smartreminder.data.local.room.entity.collaboration.CachedGroupTaskReminderEntity
import com.smartreminder.data.local.room.relation.GroupTaskWithRemindersEntity
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskDetails
import com.smartreminder.domain.model.collaboration.GroupTaskReminder
import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.time.Instant

/** Converts the primitive Room task snapshot into the typed collaboration model. */
object GroupTaskMapper {

    fun toDomain(entities: List<CachedGroupTaskEntity>): List<GroupTask> =
        entities
            .sortedWith(compareBy<CachedGroupTaskEntity> { it.createdAt }.thenBy { it.id })
            .map(::toDomain)

    fun toDomain(entity: CachedGroupTaskEntity): GroupTask = GroupTask(
        id = GroupTaskId(entity.id),
        groupId = CollaborationGroupId(entity.groupId),
        title = entity.title,
        description = entity.description,
        createdBy = UserId(entity.createdBy),
        assigneeId = UserId(entity.assigneeId),
        dueAt = Instant.ofEpochMilli(entity.dueAt),
        status = parseStatus(entity.status, entity.id),
        version = entity.version,
        createdAt = Instant.ofEpochMilli(entity.createdAt),
        updatedAt = Instant.ofEpochMilli(entity.updatedAt)
    )

    fun toEntity(domain: GroupTask): CachedGroupTaskEntity = CachedGroupTaskEntity(
        id = domain.id.value,
        groupId = domain.groupId.value,
        title = domain.title,
        description = domain.description,
        createdBy = domain.createdBy.value,
        assigneeId = domain.assigneeId.value,
        dueAt = domain.dueAt.toEpochMilli(),
        status = domain.status.name,
        version = domain.version,
        createdAt = domain.createdAt.toEpochMilli(),
        updatedAt = domain.updatedAt.toEpochMilli()
    )

    fun toDomain(entity: CachedGroupTaskReminderEntity): GroupTaskReminder = try {
        GroupTaskReminder(
            taskId = GroupTaskId(entity.taskId),
            offsetSeconds = entity.offsetSeconds
        )
    } catch (failure: IllegalArgumentException) {
        throw IllegalStateException(
            "Corrupt task reminder for task ${entity.taskId}: offset ${entity.offsetSeconds}",
            failure
        )
    }

    fun toEntity(domain: GroupTaskReminder): CachedGroupTaskReminderEntity =
        CachedGroupTaskReminderEntity(
            taskId = domain.taskId.value,
            offsetSeconds = domain.offsetSeconds
        )

    fun toDetailsDomain(relation: GroupTaskWithRemindersEntity): GroupTaskDetails {
        val task = toDomain(relation.task)
        val reminders = relation.reminders
            .sortedWith(compareBy<CachedGroupTaskReminderEntity> { it.offsetSeconds }.thenBy { it.taskId })
            .map { reminder ->
                check(reminder.taskId == relation.task.id) {
                    "Task reminder ${reminder.taskId} does not belong to task ${relation.task.id}"
                }
                toDomain(reminder)
            }
        return GroupTaskDetails(task = task, reminders = reminders)
    }

    fun toDetailsDomain(relations: List<GroupTaskWithRemindersEntity>): List<GroupTaskDetails> =
        relations
            .sortedWith(
                compareBy<GroupTaskWithRemindersEntity> { it.task.createdAt }
                    .thenBy { it.task.id }
            )
            .map(::toDetailsDomain)

    fun toEntity(details: GroupTaskDetails): GroupTaskWithRemindersEntity =
        GroupTaskWithRemindersEntity(
            task = toEntity(details.task),
            reminders = details.reminders
                .sortedWith(compareBy<GroupTaskReminder> { it.offsetSeconds }.thenBy { it.taskId.value })
                .map(::toEntity)
        )

    private fun parseStatus(raw: String, taskId: String): GroupTaskStatus =
        GroupTaskStatus.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
            ?: throw IllegalStateException("Corrupt status '$raw' in cached task $taskId")
}
