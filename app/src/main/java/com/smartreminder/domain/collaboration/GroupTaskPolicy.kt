package com.smartreminder.domain.collaboration

import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.UserId
import java.time.Instant

object GroupTaskPolicy {

    fun canReassign(actorId: UserId, actorRole: GroupRole, task: GroupTask): Boolean {
        return actorId == task.createdBy || actorRole == GroupRole.OWNER || actorRole == GroupRole.ADMIN
    }

    fun canStart(actorId: UserId, task: GroupTask): Boolean {
        return actorId == task.assigneeId && task.status == GroupTaskStatus.TODO
    }

    fun canComplete(actorId: UserId, task: GroupTask): Boolean {
        return actorId == task.assigneeId && (task.status == GroupTaskStatus.TODO || task.status == GroupTaskStatus.IN_PROGRESS)
    }

    fun canCancel(actorId: UserId, actorRole: GroupRole, task: GroupTask): Boolean {
        val isPrivileged = actorId == task.createdBy || actorRole == GroupRole.OWNER || actorRole == GroupRole.ADMIN
        val isActive = task.status == GroupTaskStatus.TODO || task.status == GroupTaskStatus.IN_PROGRESS
        return isPrivileged && isActive
    }

    fun canReopen(actorId: UserId, actorRole: GroupRole, task: GroupTask): Boolean {
        val isPrivileged = actorId == task.createdBy || actorRole == GroupRole.OWNER || actorRole == GroupRole.ADMIN
        val isTerminal = task.status == GroupTaskStatus.COMPLETED || task.status == GroupTaskStatus.CANCELLED
        return isPrivileged && isTerminal
    }

    fun isOverdue(task: GroupTask, now: Instant): Boolean {
        val isTerminal = task.status == GroupTaskStatus.COMPLETED || task.status == GroupTaskStatus.CANCELLED
        if (isTerminal) return false
        return !now.isBefore(task.dueAt)
    }
}
