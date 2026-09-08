package com.smartreminder.domain.model.collaboration

import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.GroupReminderId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class CollaborationDomainModelTest {

    private val testInstant = Instant.parse("2026-09-06T12:00:00Z")

    // --- Typed IDs Invariants ---

    @Test(expected = IllegalArgumentException::class)
    fun `given blank collaboration group id when created then throws`() {
        CollaborationGroupId("   ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given empty collaboration group id when created then throws`() {
        CollaborationGroupId("")
    }

    @Test
    fun `given non-blank collaboration group id when created then value is preserved`() {
        val id = CollaborationGroupId("group_123")
        assertEquals("group_123", id.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given blank group task id when created then throws`() {
        GroupTaskId("   ")
    }

    @Test
    fun `given non-blank group task id when created then value is preserved`() {
        val id = GroupTaskId("task_456")
        assertEquals("task_456", id.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given blank group invite id when created then throws`() {
        GroupInviteId("   ")
    }

    @Test
    fun `given non-blank group invite id when created then value is preserved`() {
        val id = GroupInviteId("invite_789")
        assertEquals("invite_789", id.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given blank group reminder id when created then throws`() {
        GroupReminderId("   ")
    }

    @Test
    fun `given non-blank group reminder id when created then value is preserved`() {
        val id = GroupReminderId("reminder_101")
        assertEquals("reminder_101", id.value)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given blank user id when created then throws`() {
        UserId("   ")
    }

    @Test
    fun `given non-blank user id when created then value is preserved`() {
        val id = UserId("user_auth_1")
        assertEquals("user_auth_1", id.value)
    }

    // --- Enums ---

    @Test
    fun `verify GroupRole exact values`() {
        assertEquals(
            listOf(GroupRole.OWNER, GroupRole.ADMIN, GroupRole.MEMBER),
            GroupRole.entries
        )
    }

    @Test
    fun `verify GroupInviteStatus exact values`() {
        assertEquals(
            listOf(GroupInviteStatus.PENDING, GroupInviteStatus.ACCEPTED, GroupInviteStatus.DECLINED),
            GroupInviteStatus.entries
        )
    }

    @Test
    fun `verify GroupTaskStatus exact values without OVERDUE`() {
        assertEquals(
            listOf(GroupTaskStatus.TODO, GroupTaskStatus.IN_PROGRESS, GroupTaskStatus.COMPLETED, GroupTaskStatus.CANCELLED),
            GroupTaskStatus.entries
        )
    }

    // --- CollaborationGroup Invariants ---

    @Test(expected = IllegalArgumentException::class)
    fun `given blank group name when created then throws`() {
        CollaborationGroup(
            id = CollaborationGroupId("g1"),
            name = "   ",
            description = "Valid description",
            createdBy = UserId("u1"),
            createdAt = testInstant,
            updatedAt = testInstant
        )
    }

    @Test
    fun `given valid group parameters when created then fields are preserved`() {
        val group = CollaborationGroup(
            id = CollaborationGroupId("g1"),
            name = "Project Team",
            description = "Team workspace",
            createdBy = UserId("u1"),
            createdAt = testInstant,
            updatedAt = testInstant
        )
        assertEquals(CollaborationGroupId("g1"), group.id)
        assertEquals("Project Team", group.name)
        assertEquals("Team workspace", group.description)
        assertEquals(UserId("u1"), group.createdBy)
        assertEquals(testInstant, group.createdAt)
        assertEquals(testInstant, group.updatedAt)
    }

    // --- GroupMember Invariants ---

    @Test
    fun `given valid group member parameters when created then fields are preserved`() {
        val member = GroupMember(
            groupId = CollaborationGroupId("g1"),
            userId = UserId("u1"),
            role = GroupRole.OWNER,
            joinedAt = testInstant
        )
        assertEquals(CollaborationGroupId("g1"), member.groupId)
        assertEquals(UserId("u1"), member.userId)
        assertEquals(GroupRole.OWNER, member.role)
        assertEquals(testInstant, member.joinedAt)
    }

    // --- GroupInvite Invariants ---

    @Test
    fun `given valid group invite parameters when created then fields are preserved`() {
        val invite = GroupInvite(
            id = GroupInviteId("inv1"),
            groupId = CollaborationGroupId("g1"),
            inviterId = UserId("u1"),
            inviteeUserId = UserId("u2"),
            status = GroupInviteStatus.PENDING,
            createdAt = testInstant,
            respondedAt = null
        )
        assertEquals(GroupInviteId("inv1"), invite.id)
        assertEquals(CollaborationGroupId("g1"), invite.groupId)
        assertEquals(UserId("u1"), invite.inviterId)
        assertEquals(UserId("u2"), invite.inviteeUserId)
        assertEquals(GroupInviteStatus.PENDING, invite.status)
        assertEquals(testInstant, invite.createdAt)
        assertNull(invite.respondedAt)
    }

    // --- GroupTask Invariants ---

    @Test(expected = IllegalArgumentException::class)
    fun `given blank task title when created then throws`() {
        GroupTask(
            id = GroupTaskId("t1"),
            groupId = CollaborationGroupId("g1"),
            title = "   ",
            description = null,
            createdBy = UserId("u1"),
            assigneeId = UserId("u2"),
            dueAt = testInstant,
            status = GroupTaskStatus.TODO,
            version = 0L,
            createdAt = testInstant,
            updatedAt = testInstant
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given negative task version when created then throws`() {
        GroupTask(
            id = GroupTaskId("t1"),
            groupId = CollaborationGroupId("g1"),
            title = "Task 1",
            description = null,
            createdBy = UserId("u1"),
            assigneeId = UserId("u2"),
            dueAt = testInstant,
            status = GroupTaskStatus.TODO,
            version = -1L,
            createdAt = testInstant,
            updatedAt = testInstant
        )
    }

    @Test
    fun `given valid group task parameters when created then fields are preserved`() {
        val task = GroupTask(
            id = GroupTaskId("t1"),
            groupId = CollaborationGroupId("g1"),
            title = "Implement Feature",
            description = "Feature details",
            createdBy = UserId("u1"),
            assigneeId = UserId("u2"),
            dueAt = testInstant,
            status = GroupTaskStatus.TODO,
            version = 1L,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        assertEquals(GroupTaskId("t1"), task.id)
        assertEquals(CollaborationGroupId("g1"), task.groupId)
        assertEquals("Implement Feature", task.title)
        assertEquals("Feature details", task.description)
        assertEquals(UserId("u1"), task.createdBy)
        assertEquals(UserId("u2"), task.assigneeId)
        assertEquals(testInstant, task.dueAt)
        assertEquals(GroupTaskStatus.TODO, task.status)
        assertEquals(1L, task.version)
        assertEquals(testInstant, task.createdAt)
        assertEquals(testInstant, task.updatedAt)
    }

    // --- GroupTaskReminder Invariants ---

    @Test(expected = IllegalArgumentException::class)
    fun `given zero reminder offset when created then throws`() {
        GroupTaskReminder(
            taskId = GroupTaskId("t1"),
            offsetSeconds = 0L
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `given negative reminder offset when created then throws`() {
        GroupTaskReminder(
            taskId = GroupTaskId("t1"),
            offsetSeconds = -60L
        )
    }

    @Test
    fun `given positive reminder offset when created then value is preserved`() {
        val reminder = GroupTaskReminder(
            taskId = GroupTaskId("t1"),
            offsetSeconds = 1800L
        )
        assertEquals(GroupTaskId("t1"), reminder.taskId)
        assertEquals(1800L, reminder.offsetSeconds)
    }

    // --- GroupReminderAudience Invariants ---

    @Test
    fun `given Member audience when created then userId is preserved`() {
        val audience = GroupReminderAudience.Member(UserId("u_member"))
        assertEquals(UserId("u_member"), audience.userId)
    }

    @Test
    fun `given Everyone audience then it is singleton object`() {
        val audience1 = GroupReminderAudience.Everyone
        val audience2 = GroupReminderAudience.Everyone
        assertTrue(audience1 === audience2)
    }

    // --- GroupReminder Invariants ---

    @Test(expected = IllegalArgumentException::class)
    fun `given blank reminder title when created then throws`() {
        GroupReminder(
            id = GroupReminderId("r1"),
            groupId = CollaborationGroupId("g1"),
            title = "   ",
            description = null,
            createdBy = UserId("u1"),
            audience = GroupReminderAudience.Everyone,
            remindAt = testInstant,
            createdAt = testInstant,
            updatedAt = testInstant
        )
    }

    @Test
    fun `given valid group reminder parameters when created then fields are preserved`() {
        val reminder = GroupReminder(
            id = GroupReminderId("r1"),
            groupId = CollaborationGroupId("g1"),
            title = "Standup Meeting",
            description = "Daily sync",
            createdBy = UserId("u1"),
            audience = GroupReminderAudience.Member(UserId("u2")),
            remindAt = testInstant,
            createdAt = testInstant,
            updatedAt = testInstant
        )
        assertEquals(GroupReminderId("r1"), reminder.id)
        assertEquals(CollaborationGroupId("g1"), reminder.groupId)
        assertEquals("Standup Meeting", reminder.title)
        assertEquals("Daily sync", reminder.description)
        assertEquals(UserId("u1"), reminder.createdBy)
        assertEquals(GroupReminderAudience.Member(UserId("u2")), reminder.audience)
        assertEquals(testInstant, reminder.remindAt)
        assertEquals(testInstant, reminder.createdAt)
        assertEquals(testInstant, reminder.updatedAt)
    }
}
