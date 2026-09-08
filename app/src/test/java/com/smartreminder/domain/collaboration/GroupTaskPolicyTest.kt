package com.smartreminder.domain.collaboration

import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GroupTaskPolicyTest {

    private val creatorId = UserId("creator_1")
    private val assigneeId = UserId("assignee_1")
    private val otherUserId = UserId("other_1")
    private val dueAt = Instant.parse("2026-09-08T12:00:00Z")

    private fun createTask(status: GroupTaskStatus): GroupTask {
        return GroupTask(
            id = GroupTaskId("task_1"),
            groupId = CollaborationGroupId("group_1"),
            title = "Sample Task",
            description = null,
            createdBy = creatorId,
            assigneeId = assigneeId,
            dueAt = dueAt,
            status = status,
            version = 1L,
            createdAt = Instant.parse("2026-09-08T00:00:00Z"),
            updatedAt = Instant.parse("2026-09-08T00:00:00Z")
        )
    }

    // --- canReassign ---

    @Test
    fun `reassign allowed for creator, owner, or admin`() {
        val task = createTask(GroupTaskStatus.TODO)

        // Creator can reassign even if just a member
        assertTrue(GroupTaskPolicy.canReassign(actorId = creatorId, actorRole = GroupRole.MEMBER, task = task))

        // Owner can reassign any task
        assertTrue(GroupTaskPolicy.canReassign(actorId = otherUserId, actorRole = GroupRole.OWNER, task = task))

        // Admin can reassign any task
        assertTrue(GroupTaskPolicy.canReassign(actorId = otherUserId, actorRole = GroupRole.ADMIN, task = task))

        // Non-creator Member cannot reassign
        assertFalse(GroupTaskPolicy.canReassign(actorId = otherUserId, actorRole = GroupRole.MEMBER, task = task))
    }

    // --- canStart ---

    @Test
    fun `start task allowed only for assignee when in TODO state`() {
        val todoTask = createTask(GroupTaskStatus.TODO)
        val inProgressTask = createTask(GroupTaskStatus.IN_PROGRESS)
        val completedTask = createTask(GroupTaskStatus.COMPLETED)

        // Assignee can start TODO task
        assertTrue(GroupTaskPolicy.canStart(actorId = assigneeId, task = todoTask))

        // Assignee cannot start task that is already IN_PROGRESS or COMPLETED
        assertFalse(GroupTaskPolicy.canStart(actorId = assigneeId, task = inProgressTask))
        assertFalse(GroupTaskPolicy.canStart(actorId = assigneeId, task = completedTask))

        // Non-assignee cannot start (even if creator)
        assertFalse(GroupTaskPolicy.canStart(actorId = creatorId, task = todoTask))
    }

    // --- canComplete ---

    @Test
    fun `complete task allowed only for assignee when in TODO or IN_PROGRESS state`() {
        val todoTask = createTask(GroupTaskStatus.TODO)
        val inProgressTask = createTask(GroupTaskStatus.IN_PROGRESS)
        val completedTask = createTask(GroupTaskStatus.COMPLETED)
        val cancelledTask = createTask(GroupTaskStatus.CANCELLED)

        // Assignee can complete TODO or IN_PROGRESS task
        assertTrue(GroupTaskPolicy.canComplete(actorId = assigneeId, task = todoTask))
        assertTrue(GroupTaskPolicy.canComplete(actorId = assigneeId, task = inProgressTask))

        // Assignee cannot complete task already COMPLETED or CANCELLED
        assertFalse(GroupTaskPolicy.canComplete(actorId = assigneeId, task = completedTask))
        assertFalse(GroupTaskPolicy.canComplete(actorId = assigneeId, task = cancelledTask))

        // Non-assignee cannot complete (creator, admin, owner never have completion-by-proxy)
        assertFalse(GroupTaskPolicy.canComplete(actorId = creatorId, task = todoTask))
        assertFalse(GroupTaskPolicy.canComplete(actorId = otherUserId, task = inProgressTask))
    }

    // --- canCancel ---

    @Test
    fun `cancel task allowed for creator, owner, or admin when active`() {
        val todoTask = createTask(GroupTaskStatus.TODO)
        val inProgressTask = createTask(GroupTaskStatus.IN_PROGRESS)
        val completedTask = createTask(GroupTaskStatus.COMPLETED)
        val cancelledTask = createTask(GroupTaskStatus.CANCELLED)

        // Creator can cancel active tasks
        assertTrue(GroupTaskPolicy.canCancel(actorId = creatorId, actorRole = GroupRole.MEMBER, task = todoTask))
        assertTrue(GroupTaskPolicy.canCancel(actorId = creatorId, actorRole = GroupRole.MEMBER, task = inProgressTask))

        // Owner/Admin can cancel active tasks
        assertTrue(GroupTaskPolicy.canCancel(actorId = otherUserId, actorRole = GroupRole.OWNER, task = todoTask))
        assertTrue(GroupTaskPolicy.canCancel(actorId = otherUserId, actorRole = GroupRole.ADMIN, task = inProgressTask))

        // Non-creator Member cannot cancel
        assertFalse(GroupTaskPolicy.canCancel(actorId = otherUserId, actorRole = GroupRole.MEMBER, task = todoTask))

        // Cannot cancel already COMPLETED or CANCELLED tasks
        assertFalse(GroupTaskPolicy.canCancel(actorId = creatorId, actorRole = GroupRole.OWNER, task = completedTask))
        assertFalse(GroupTaskPolicy.canCancel(actorId = creatorId, actorRole = GroupRole.OWNER, task = cancelledTask))
    }

    // --- canReopen ---

    @Test
    fun `reopen task allowed for creator, owner, or admin when completed or cancelled`() {
        val todoTask = createTask(GroupTaskStatus.TODO)
        val inProgressTask = createTask(GroupTaskStatus.IN_PROGRESS)
        val completedTask = createTask(GroupTaskStatus.COMPLETED)
        val cancelledTask = createTask(GroupTaskStatus.CANCELLED)

        // Creator can reopen terminal tasks
        assertTrue(GroupTaskPolicy.canReopen(actorId = creatorId, actorRole = GroupRole.MEMBER, task = completedTask))
        assertTrue(GroupTaskPolicy.canReopen(actorId = creatorId, actorRole = GroupRole.MEMBER, task = cancelledTask))

        // Owner/Admin can reopen terminal tasks
        assertTrue(GroupTaskPolicy.canReopen(actorId = otherUserId, actorRole = GroupRole.OWNER, task = completedTask))
        assertTrue(GroupTaskPolicy.canReopen(actorId = otherUserId, actorRole = GroupRole.ADMIN, task = cancelledTask))

        // Non-creator Member cannot reopen
        assertFalse(GroupTaskPolicy.canReopen(actorId = otherUserId, actorRole = GroupRole.MEMBER, task = completedTask))

        // Cannot reopen already active tasks (TODO, IN_PROGRESS)
        assertFalse(GroupTaskPolicy.canReopen(actorId = creatorId, actorRole = GroupRole.OWNER, task = todoTask))
        assertFalse(GroupTaskPolicy.canReopen(actorId = creatorId, actorRole = GroupRole.OWNER, task = inProgressTask))
    }

    // --- isOverdue ---

    @Test
    fun `isOverdue returns true only when now is at or after dueAt and task is active`() {
        val beforeDue = Instant.parse("2026-09-08T11:59:59Z")
        val atDue = Instant.parse("2026-09-08T12:00:00Z")
        val afterDue = Instant.parse("2026-09-08T12:00:01Z")

        val todoTask = createTask(GroupTaskStatus.TODO)
        val inProgressTask = createTask(GroupTaskStatus.IN_PROGRESS)
        val completedTask = createTask(GroupTaskStatus.COMPLETED)
        val cancelledTask = createTask(GroupTaskStatus.CANCELLED)

        // Before due time -> not overdue
        assertFalse(GroupTaskPolicy.isOverdue(todoTask, beforeDue))
        assertFalse(GroupTaskPolicy.isOverdue(inProgressTask, beforeDue))

        // At or after due time for active tasks -> overdue
        assertTrue(GroupTaskPolicy.isOverdue(todoTask, atDue))
        assertTrue(GroupTaskPolicy.isOverdue(todoTask, afterDue))
        assertTrue(GroupTaskPolicy.isOverdue(inProgressTask, afterDue))

        // Completed and Cancelled tasks are never overdue even after dueAt
        assertFalse(GroupTaskPolicy.isOverdue(completedTask, afterDue))
        assertFalse(GroupTaskPolicy.isOverdue(cancelledTask, afterDue))
    }
}
