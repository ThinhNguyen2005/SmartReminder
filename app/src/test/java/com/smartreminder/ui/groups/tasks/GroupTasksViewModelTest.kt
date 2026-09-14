package com.smartreminder.ui.groups.tasks

import androidx.lifecycle.SavedStateHandle
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskDetails
import com.smartreminder.domain.model.collaboration.GroupTaskReminder
import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.domain.repository.AcceptInviteCommand
import com.smartreminder.domain.repository.CancelGroupTaskCommand
import com.smartreminder.domain.repository.ChangeMemberRoleCommand
import com.smartreminder.domain.repository.CollaborationError
import com.smartreminder.domain.repository.CollaborationMutationResult
import com.smartreminder.domain.repository.CollaborationRepository
import com.smartreminder.domain.repository.CompleteGroupTaskCommand
import com.smartreminder.domain.repository.CreateGroupCommand
import com.smartreminder.domain.repository.CreateGroupTaskCommand
import com.smartreminder.domain.repository.DeclineInviteCommand
import com.smartreminder.domain.repository.DeleteGroupCommand
import com.smartreminder.domain.repository.EditGroupTaskCommand
import com.smartreminder.domain.repository.InviteMemberCommand
import com.smartreminder.domain.repository.LeaveGroupCommand
import com.smartreminder.domain.repository.ReassignGroupTaskCommand
import com.smartreminder.domain.repository.RemoveMemberCommand
import com.smartreminder.domain.repository.ReopenGroupTaskCommand
import com.smartreminder.domain.repository.StartGroupTaskCommand
import com.smartreminder.domain.repository.TransferOwnershipCommand
import com.smartreminder.domain.repository.UpdateGroupCommand
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupTasksViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fixedNow = Instant.parse("2026-09-14T10:00:00Z")
    private val groupId = CollaborationGroupId("group-1")
    private val memberId = UserId("member-1")
    private val taskId = GroupTaskId("task-1")
    private lateinit var repository: FakeCollaborationRepository
    private lateinit var idGenerator: FakeGroupTaskIdGenerator

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCollaborationRepository(
            groups = listOf(group(groupId)),
            members = listOf(
                member(groupId, ownerId, GroupRole.OWNER),
                member(groupId, memberId, GroupRole.MEMBER)
            ),
            details = listOf(details(task(status = GroupTaskStatus.TODO)))
        )
        idGenerator = FakeGroupTaskIdGenerator()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `given cached tasks, when viewModel starts with restored ids, then exposes detail and policy permissions`() =
        runTest(dispatcher) {
            val viewModel = createViewModel(
                SavedStateHandle(
                    mapOf(
                        GroupTasksViewModel.SELECTED_GROUP_ID_KEY to groupId.value,
                        GroupTasksViewModel.SELECTED_TASK_ID_KEY to taskId.value
                    )
                )
            )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(GroupTasksScreen.DETAIL, state.screen)
            assertEquals(groupId, state.selectedGroupId)
            assertEquals(taskId, state.selectedTaskId)
            assertEquals(GroupTasksLoadState.CONTENT, state.loadState)
            assertEquals("Task 1", state.tasks.single().task.title)
            assertTrue(state.selectedTask?.permissions?.canEdit == true)
            assertTrue(state.selectedTask?.permissions?.canCancel == true)
            assertFalse(state.selectedTask?.isOverdue == true)
            assertEquals(1, repository.refreshTasksCalls)
        }

    @Test
    fun `given empty cache and successful refresh, then exposes empty state`() = runTest(dispatcher) {
        repository.setDetails(groupId, emptyList())

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(GroupTasksLoadState.EMPTY, viewModel.uiState.value.loadState)
        assertTrue(viewModel.uiState.value.tasks.isEmpty())
    }

    @Test
    fun `given cached tasks and offline refresh, then keeps content as cached offline`() = runTest(dispatcher) {
        repository.refreshTasksResult = CollaborationMutationResult.NetworkRequired

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(GroupTasksLoadState.CACHED_OFFLINE, viewModel.uiState.value.loadState)
        assertTrue(viewModel.uiState.value.isCached)
        assertTrue(viewModel.uiState.value.isOffline)
        assertEquals("Task 1", viewModel.uiState.value.tasks.single().task.title)
    }

    @Test
    fun `given selected task and offline refresh, then detail is cached offline too`() = runTest(dispatcher) {
        repository.refreshTasksResult = CollaborationMutationResult.NetworkRequired

        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        advanceUntilIdle()

        assertEquals(GroupTaskDetailLoadState.CACHED_OFFLINE, viewModel.uiState.value.detailLoadState)
        assertEquals(GroupTasksUiError.Offline, viewModel.uiState.value.detailError)
    }

    @Test
    fun `given no cached tasks and offline refresh, then exposes typed error`() = runTest(dispatcher) {
        repository.setDetails(groupId, emptyList())
        repository.refreshTasksResult = CollaborationMutationResult.NetworkRequired

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(GroupTasksLoadState.ERROR, viewModel.uiState.value.loadState)
        assertEquals(GroupTasksUiError.Offline, viewModel.uiState.value.error)
    }

    @Test
    fun `when task and member observations update, then list uses current assignee and permissions`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        repository.setMembers(groupId, listOf(member(groupId, memberId, GroupRole.MEMBER)))
        advanceUntilIdle()

        val item = viewModel.uiState.value.tasks.single()
        assertEquals(memberId, item.assignee?.userId)
        assertFalse(item.permissions.canEdit)
        assertFalse(item.permissions.canCancel)
    }

    @Test
    fun `when task observation fails, then Retry resubscribes task stream while member stream remains active`() =
        runTest(dispatcher) {
            repository.failNextTaskDetailsObservation(groupId, IOException("task stream unavailable"))
            repository.refreshTasksResult = CollaborationMutationResult.NetworkRequired

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(GroupTasksUiError.Offline, viewModel.uiState.value.error)

            repository.refreshTasksResult = CollaborationMutationResult.Applied
            viewModel.onAction(GroupTasksAction.Retry)
            advanceUntilIdle()

            assertEquals("Task 1", viewModel.uiState.value.tasks.single().task.title)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `when member observation fails, then Retry resubscribes member stream while task stream remains active`() =
        runTest(dispatcher) {
            repository.failNextMemberObservation(groupId, IOException("member stream unavailable"))
            repository.refreshTasksResult = CollaborationMutationResult.NetworkRequired

            val viewModel = createViewModel()
            advanceUntilIdle()
            assertEquals(GroupTasksUiError.Offline, viewModel.uiState.value.error)
            assertTrue(viewModel.uiState.value.members.isEmpty())

            repository.refreshTasksResult = CollaborationMutationResult.Applied
            viewModel.onAction(GroupTasksAction.Retry)
            advanceUntilIdle()

            assertEquals(setOf(ownerId, memberId), viewModel.uiState.value.members.map { it.userId }.toSet())
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `when opening edit, then editor mirrors full task aggregate`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.OpenEditTask)

        val editor = viewModel.uiState.value.editor
        assertEquals(GroupTaskEditorMode.Edit(taskId), editor?.mode)
        assertEquals(taskId, editor?.taskId)
        assertEquals("Task 1", editor?.title)
        assertEquals(memberId, editor?.assigneeId)
        assertEquals(Instant.parse("2026-09-15T10:00:00Z"), editor?.dueAt)
        assertEquals(listOf(3600L, 7200L), editor?.reminderOffsetsSeconds)
        assertEquals(3L, editor?.expectedVersion)
    }

    @Test
    fun `given valid create draft, when saving, then sends generated id and full command`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenCreateTask)
        viewModel.onAction(GroupTasksAction.ChangeTitle("  New task  "))
        viewModel.onAction(GroupTasksAction.ChangeDescription("Notes"))
        viewModel.onAction(GroupTasksAction.ChangeAssignee(memberId))
        viewModel.onAction(GroupTasksAction.ChangeDeadline(Instant.parse("2026-09-16T10:00:00Z")))
        viewModel.onAction(GroupTasksAction.ChangeReminderOffsets(listOf(1800L, 3600L)))

        viewModel.onAction(GroupTasksAction.SaveTask)
        advanceUntilIdle()

        assertEquals(
            CreateGroupTaskCommand(
                taskId = GroupTaskId("generated-1"),
                groupId = groupId,
                title = "New task",
                description = "Notes",
                assigneeId = memberId,
                dueAt = Instant.parse("2026-09-16T10:00:00Z"),
                reminderOffsetsSeconds = listOf(1800L, 3600L)
            ),
            repository.lastCreateTask
        )
        assertEquals(GroupTasksMutation.CREATE, repository.lastMutation)
        assertNull(viewModel.uiState.value.editor)
        assertEquals(GroupTaskId("generated-1"), viewModel.uiState.value.selectedTaskId)
    }

    @Test
    fun `when saving invalid editor, then exposes all inline field errors without mutation`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenCreateTask)
        viewModel.onAction(GroupTasksAction.ChangeReminderOffsets(listOf(0L, 0L, -1L, 2L, 3L, 4L)))
        viewModel.onAction(GroupTasksAction.SaveTask)

        val editor = viewModel.uiState.value.editor!!
        assertEquals(GroupTaskFieldError.TITLE_REQUIRED, editor.errors[GroupTaskField.TITLE])
        assertEquals(GroupTaskFieldError.ASSIGNEE_REQUIRED, editor.errors[GroupTaskField.ASSIGNEE])
        assertEquals(GroupTaskFieldError.DEADLINE_REQUIRED, editor.errors[GroupTaskField.DEADLINE])
        assertEquals(GroupTaskFieldError.REMINDER_OFFSETS_TOO_MANY, editor.errors[GroupTaskField.REMINDER_OFFSETS])
        assertEquals(GroupTasksUiError.Validation(GroupTaskField.TITLE, GroupTaskFieldError.TITLE_REQUIRED), viewModel.uiState.value.error)
        assertNull(repository.lastCreateTask)
    }

    @Test
    fun `given duplicate or nonpositive offsets within limit, then maps inline offsets error`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenCreateTask)
        viewModel.onAction(GroupTasksAction.ChangeReminderOffsets(listOf(60L, 60L, 0L)))
        viewModel.onAction(GroupTasksAction.SaveTask)

        assertEquals(
            GroupTaskFieldError.REMINDER_OFFSETS_NON_POSITIVE,
            viewModel.uiState.value.editor?.errors?.get(GroupTaskField.REMINDER_OFFSETS)
        )
    }

    @Test
    fun `given policy denied task action, then does not call repository and exposes typed authorization`() = runTest(dispatcher) {
        repository.currentUserId = UserId("outsider")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.OpenEditTask)
        viewModel.onAction(GroupTasksAction.StartTask)
        viewModel.onAction(GroupTasksAction.OpenCancelConfirmation)

        assertNull(viewModel.uiState.value.editor)
        assertNull(repository.lastStartTask)
        assertNull(viewModel.uiState.value.confirmation)
        assertEquals(GroupTasksUiError.NotAuthorized, viewModel.uiState.value.error)
    }

    @Test
    fun `given cancel or reopen action, then confirmation is required before command`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.OpenCancelConfirmation)

        assertEquals(GroupTaskConfirmation.Cancel(taskId), viewModel.uiState.value.confirmation)
        assertNull(repository.lastCancelTask)

        viewModel.onAction(GroupTasksAction.ConfirmCancel)
        advanceUntilIdle()
        assertEquals(CancelGroupTaskCommand(taskId, 3L), repository.lastCancelTask)

        repository.setDetails(groupId, listOf(details(task(status = GroupTaskStatus.CANCELLED))))
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenReopenConfirmation)
        assertEquals(GroupTaskConfirmation.Reopen(taskId), viewModel.uiState.value.confirmation)
        viewModel.onAction(GroupTasksAction.ConfirmReopen)
        advanceUntilIdle()
        assertEquals(ReopenGroupTaskCommand(taskId, 3L), repository.lastReopenTask)
    }

    @Test
    fun `when editing, then sends full edit command with expected version`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.OpenEditTask)
        viewModel.onAction(GroupTasksAction.ChangeTitle("Changed"))
        viewModel.onAction(GroupTasksAction.SaveTask)
        advanceUntilIdle()

        assertEquals(
            EditGroupTaskCommand(
                taskId = taskId,
                title = "Changed",
                description = null,
                assigneeId = memberId,
                dueAt = Instant.parse("2026-09-15T10:00:00Z"),
                reminderOffsetsSeconds = listOf(3600L, 7200L),
                expectedVersion = 3L
            ),
            repository.lastEditTask
        )
    }

    @Test
    fun `when reassigning starting and completing, then sends versioned commands`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.ReassignTask(ownerId))
        advanceUntilIdle()
        repository.setDetails(groupId, listOf(details(task(assigneeId = ownerId))))
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.StartTask)
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.CompleteTask)
        advanceUntilIdle()

        assertEquals(ReassignGroupTaskCommand(taskId, ownerId, 3L), repository.lastReassignTask)
        assertEquals(StartGroupTaskCommand(taskId, 3L), repository.lastStartTask)
        assertEquals(CompleteGroupTaskCommand(taskId, 3L), repository.lastCompleteTask)
    }

    @Test
    fun `when mutation conflicts, then clears progress and emits safe conflict feedback with refresh retry`() = runTest(dispatcher) {
        repository.currentUserId = memberId
        repository.mutationResult = CollaborationMutationResult.Conflict(CollaborationError.Conflict("secret"))
        val viewModel = createViewModel()
        val effects = mutableListOf<GroupTasksEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.StartTask)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingMutation)
        assertEquals(GroupTasksUiError.Conflict, viewModel.uiState.value.error)
        val failure = effects.filterIsInstance<GroupTasksEffect.MutationFailed>().single()
        assertEquals(GroupTasksAction.Refresh, failure.retryAction)
    }

    @Test
    fun `when mutation is offline, then exposes retry mutation without rendering raw detail`() = runTest(dispatcher) {
        repository.currentUserId = memberId
        repository.mutationResult = CollaborationMutationResult.NetworkRequired
        val viewModel = createViewModel()
        val effects = mutableListOf<GroupTasksEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.StartTask)
        advanceUntilIdle()

        assertEquals(GroupTasksUiError.Offline, viewModel.uiState.value.error)
        val failure = effects.filterIsInstance<GroupTasksEffect.MutationFailed>().single()
        assertEquals(GroupTasksAction.RetryLastMutation, failure.retryAction)
    }

    @Test
    fun `when offline mutation is retried, then reuses the versioned operation only after explicit retry`() = runTest(dispatcher) {
        repository.currentUserId = memberId
        repository.mutationResult = CollaborationMutationResult.NetworkRequired
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.StartTask)
        advanceUntilIdle()

        repository.mutationResult = CollaborationMutationResult.Applied
        viewModel.onAction(GroupTasksAction.RetryLastMutation)
        advanceUntilIdle()

        assertEquals(2, repository.startTaskCalls)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isMutationInProgress)
    }

    @Test
    fun `when create mutation is offline, then explicit retry reuses the generated command`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenCreateTask)
        viewModel.onAction(GroupTasksAction.ChangeTitle("New task"))
        viewModel.onAction(GroupTasksAction.ChangeAssignee(memberId))
        viewModel.onAction(GroupTasksAction.ChangeDeadline(Instant.parse("2026-09-16T10:00:00Z")))
        viewModel.onAction(GroupTasksAction.ChangeReminderOffsets(listOf(3600L)))

        repository.mutationResult = CollaborationMutationResult.NetworkRequired
        viewModel.onAction(GroupTasksAction.SaveTask)
        advanceUntilIdle()
        assertEquals(1, repository.createTaskCalls)
        assertEquals(GroupTasksUiError.Offline, viewModel.uiState.value.error)

        repository.mutationResult = CollaborationMutationResult.Applied
        viewModel.onAction(GroupTasksAction.RetryLastMutation)
        advanceUntilIdle()

        assertEquals(2, repository.createTaskCalls)
        assertNull(viewModel.uiState.value.error)
        assertEquals(GroupTaskId("generated-1"), viewModel.uiState.value.selectedTaskId)
    }

    @Test
    fun `when create save is retried after network failure, then the editor intent keeps one client task id`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            viewModel.onAction(GroupTasksAction.OpenCreateTask)
            viewModel.onAction(GroupTasksAction.ChangeTitle("New task"))
            viewModel.onAction(GroupTasksAction.ChangeAssignee(memberId))
            viewModel.onAction(GroupTasksAction.ChangeDeadline(Instant.parse("2026-09-16T10:00:00Z")))
            viewModel.onAction(GroupTasksAction.ChangeReminderOffsets(listOf(3600L)))
            repository.mutationResult = CollaborationMutationResult.NetworkRequired

            viewModel.onAction(GroupTasksAction.SaveTask)
            advanceUntilIdle()
            val firstCommand = repository.lastCreateTask

            viewModel.onAction(GroupTasksAction.SaveTask)
            advanceUntilIdle()

            assertEquals(2, repository.createTaskCalls)
            assertEquals(firstCommand?.taskId, repository.lastCreateTask?.taskId)
            assertEquals(firstCommand?.taskId, viewModel.uiState.value.editor?.clientTaskId)
        }

    @Test
    fun `when account changes, then editor and pending retry state are reset`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenCreateTask)
        viewModel.onAction(GroupTasksAction.ChangeTitle("New task"))
        viewModel.onAction(GroupTasksAction.ChangeAssignee(memberId))
        viewModel.onAction(GroupTasksAction.ChangeDeadline(Instant.parse("2026-09-16T10:00:00Z")))
        viewModel.onAction(GroupTasksAction.ChangeReminderOffsets(listOf(3600L)))
        repository.mutationResult = CollaborationMutationResult.NetworkRequired
        viewModel.onAction(GroupTasksAction.SaveTask)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.editor != null)
        assertEquals(GroupTasksUiError.Offline, viewModel.uiState.value.error)

        repository.currentUserId = UserId("account-b")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedGroupId)
        assertNull(state.selectedTaskId)
        assertNull(state.editor)
        assertNull(state.pendingMutation)
        assertNull(state.error)
        assertNull(state.detailError)
        assertEquals(GroupTasksScreen.LIST, state.screen)
        assertEquals(GroupTasksLoadState.IDLE, state.loadState)
        assertEquals(GroupTaskDetailLoadState.IDLE, state.detailLoadState)
    }

    @Test
    fun `when account changes during a mutation, then late completion cannot emit old effects`() = runTest(dispatcher) {
        val mutationCompletion = CompletableDeferred<CollaborationMutationResult>()
        repository.currentUserId = memberId
        repository.mutationCompletion = mutationCompletion
        val viewModel = createViewModel()
        val effects = mutableListOf<GroupTasksEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.StartTask)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isMutationInProgress)

        repository.currentUserId = UserId("account-b")
        advanceUntilIdle()
        val stateAfterSwitch = viewModel.uiState.value

        mutationCompletion.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()

        assertNull(stateAfterSwitch.selectedGroupId)
        assertNull(stateAfterSwitch.selectedTaskId)
        assertNull(stateAfterSwitch.pendingMutation)
        assertFalse(effects.any { it is GroupTasksEffect.MutationCompleted })
        assertFalse(viewModel.uiState.value.isMutationInProgress)
    }

    @Test
    fun `when create retry is canceled and a new editor intent starts, then it gets a new client task id`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()
            repository.mutationResult = CollaborationMutationResult.NetworkRequired
            viewModel.onAction(GroupTasksAction.OpenCreateTask)
            fillCreateEditor(viewModel, "First task")
            viewModel.onAction(GroupTasksAction.SaveTask)
            advanceUntilIdle()
            assertEquals(GroupTaskId("generated-1"), repository.lastCreateTask?.taskId)

            viewModel.onAction(GroupTasksAction.CancelEditor)
            viewModel.onAction(GroupTasksAction.RetryLastMutation)
            advanceUntilIdle()
            assertEquals(1, repository.createTaskCalls)

            viewModel.onAction(GroupTasksAction.OpenCreateTask)
            fillCreateEditor(viewModel, "Second task")
            repository.mutationResult = CollaborationMutationResult.Applied
            viewModel.onAction(GroupTasksAction.SaveTask)
            advanceUntilIdle()

            assertEquals(2, repository.createTaskCalls)
            assertEquals(GroupTaskId("generated-2"), repository.lastCreateTask?.taskId)
        }

    @Test
    fun `when cancel retry is followed by navigation, then it cannot cancel a reopened task`() = runTest(dispatcher) {
        repository.currentUserId = ownerId
        repository.mutationResult = CollaborationMutationResult.NetworkRequired
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.OpenCancelConfirmation)
        viewModel.onAction(GroupTasksAction.ConfirmCancel)
        advanceUntilIdle()
        assertEquals(1, repository.cancelTaskCalls)

        viewModel.onAction(GroupTasksAction.BackFromTask)
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        repository.mutationResult = CollaborationMutationResult.Applied
        viewModel.onAction(GroupTasksAction.RetryLastMutation)
        advanceUntilIdle()

        assertEquals(1, repository.cancelTaskCalls)
        assertEquals(GroupTaskId(taskId.value), viewModel.uiState.value.selectedTaskId)
    }

    @Test
    fun `when a new mutation fails after an offline retryable mutation, then the old retry is discarded`() = runTest(dispatcher) {
        repository.currentUserId = memberId
        repository.mutationResult = CollaborationMutationResult.NetworkRequired
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.StartTask)
        advanceUntilIdle()
        assertEquals(1, repository.startTaskCalls)

        repository.mutationResult = CollaborationMutationResult.Failure(CollaborationError.Unknown())
        viewModel.onAction(GroupTasksAction.CompleteTask)
        advanceUntilIdle()
        assertEquals(1, repository.completeTaskCalls)

        viewModel.onAction(GroupTasksAction.RetryLastMutation)
        advanceUntilIdle()

        assertEquals(1, repository.startTaskCalls)
        assertEquals(1, repository.completeTaskCalls)
    }

    @Test
    fun `when mutation fails generically, then exposes typed unknown feedback without retry`() = runTest(dispatcher) {
        repository.currentUserId = memberId
        repository.mutationResult = CollaborationMutationResult.Failure(CollaborationError.Unknown(IllegalStateException("secret")))
        val viewModel = createViewModel()
        val effects = mutableListOf<GroupTasksEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effects.collect { effects += it }
        }
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.StartTask)
        advanceUntilIdle()

        val failure = effects.filterIsInstance<GroupTasksEffect.MutationFailed>().single()
        assertEquals(GroupTasksUiError.Unknown, viewModel.uiState.value.error)
        assertNull(failure.retryAction)
    }

    @Test
    fun `when refresh for restored task reports not found, then safely falls back to group list`() = runTest(dispatcher) {
        repository.refreshTasksResult = CollaborationMutationResult.Failure(CollaborationError.NotFound)
        val viewModel = createViewModel(
            SavedStateHandle(
                mapOf(
                    GroupTasksViewModel.SELECTED_GROUP_ID_KEY to groupId.value,
                    GroupTasksViewModel.SELECTED_TASK_ID_KEY to "missing-task"
                )
            )
        )
        advanceUntilIdle()

        assertEquals(GroupTasksScreen.LIST, viewModel.uiState.value.screen)
        assertNull(viewModel.uiState.value.selectedTaskId)
        assertEquals(GroupTasksUiError.NotFound, viewModel.uiState.value.error)
    }

    @Test
    fun `when refresh for restored group reports not found, then safely falls back outside the group`() = runTest(dispatcher) {
        repository.refreshTasksResult = CollaborationMutationResult.Failure(CollaborationError.NotFound)
        val missingGroupId = CollaborationGroupId("missing-group")
        val viewModel = createViewModel(
            SavedStateHandle(
                mapOf(GroupTasksViewModel.SELECTED_GROUP_ID_KEY to missingGroupId.value)
            )
        )
        advanceUntilIdle()

        assertEquals(GroupTasksScreen.LIST, viewModel.uiState.value.screen)
        assertNull(viewModel.uiState.value.selectedGroupId)
        assertEquals(GroupTasksUiError.NotFound, viewModel.uiState.value.error)
    }

    @Test
    fun `factory creates task viewModel with injected clock id generator and saved selection`() = runTest(dispatcher) {
        val factory = GroupTasksViewModelFactory(
            repository = repository,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            idGenerator = idGenerator,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    GroupTasksViewModel.SELECTED_GROUP_ID_KEY to groupId.value,
                    GroupTasksViewModel.SELECTED_TASK_ID_KEY to taskId.value
                )
            )
        )

        val viewModel = factory.create(GroupTasksViewModel::class.java)
        advanceUntilIdle()

        assertEquals(taskId, viewModel.uiState.value.selectedTaskId)
    }

    @Test
    fun `when process restore contains malformed ids, then it safely falls back to list`() = runTest(dispatcher) {
        val viewModel = GroupTasksViewModel(
            repository = repository,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            idGenerator = idGenerator,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    GroupTasksViewModel.SELECTED_GROUP_ID_KEY to 42,
                    GroupTasksViewModel.SELECTED_TASK_ID_KEY to Any()
                )
            )
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedGroupId)
        assertNull(viewModel.uiState.value.selectedTaskId)
        assertEquals(GroupTasksLoadState.IDLE, viewModel.uiState.value.loadState)
    }

    @Test
    fun `when process restore contains a task without a group, then it clears the orphan selection`() = runTest(dispatcher) {
        val viewModel = GroupTasksViewModel(
            repository = repository,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            idGenerator = idGenerator,
            savedStateHandle = SavedStateHandle(
                mapOf(GroupTasksViewModel.SELECTED_TASK_ID_KEY to taskId.value)
            )
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedGroupId)
        assertNull(viewModel.uiState.value.selectedTaskId)
        assertEquals(GroupTasksScreen.LIST, viewModel.uiState.value.screen)
        assertEquals(GroupTaskDetailLoadState.IDLE, viewModel.uiState.value.detailLoadState)
    }

    @Test
    fun `when restored task cache arrives late, then pre-refresh empty snapshot cannot clear selection`() = runTest(dispatcher) {
        repository.setDetails(groupId, emptyList())
        val initialDetailsGate = CompletableDeferred<Unit>()
        val refreshCompletion = CompletableDeferred<CollaborationMutationResult>()
        repository.detailsInitialEmissionGate = initialDetailsGate
        repository.refreshTasksCompletion = refreshCompletion
        val viewModel = GroupTasksViewModel(
            repository = repository,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            idGenerator = idGenerator,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    GroupTasksViewModel.SELECTED_GROUP_ID_KEY to groupId.value,
                    GroupTasksViewModel.SELECTED_TASK_ID_KEY to taskId.value
                )
            )
        )
        advanceUntilIdle()
        assertEquals(0, repository.refreshTasksCalls)
        assertEquals(taskId, viewModel.uiState.value.selectedTaskId)

        initialDetailsGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repository.refreshTasksCalls)
        assertEquals(taskId, viewModel.uiState.value.selectedTaskId)

        refreshCompletion.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()

        assertEquals(taskId, viewModel.uiState.value.selectedTaskId)
        assertEquals(GroupTasksScreen.DETAIL, viewModel.uiState.value.screen)
    }

    @Test
    fun `when restored task refresh is offline, then later cache emissions cannot clear selection`() = runTest(dispatcher) {
        repository.setDetails(groupId, emptyList())
        val refreshCompletion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshTasksCompletion = refreshCompletion
        val viewModel = GroupTasksViewModel(
            repository = repository,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            idGenerator = idGenerator,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    GroupTasksViewModel.SELECTED_GROUP_ID_KEY to groupId.value,
                    GroupTasksViewModel.SELECTED_TASK_ID_KEY to taskId.value
                )
            )
        )
        advanceUntilIdle()
        refreshCompletion.complete(CollaborationMutationResult.NetworkRequired)
        advanceUntilIdle()

        repository.setDetails(
            groupId,
            listOf(details(task(taskId = GroupTaskId("other-task"), title = "Other task")))
        )
        advanceUntilIdle()

        assertEquals(taskId, viewModel.uiState.value.selectedTaskId)
        assertEquals(GroupTasksLoadState.CACHED_OFFLINE, viewModel.uiState.value.loadState)
    }

    @Test
    fun `when refresh requests overlap, then duplicate refresh is ignored and first result wins`() = runTest(dispatcher) {
        val refreshCompletion = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshTasksCompletion = refreshCompletion
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(1, repository.refreshTasksCalls)
        assertEquals(GroupTasksLoadState.OFFLINE_REFRESHING, viewModel.uiState.value.loadState)

        viewModel.onAction(GroupTasksAction.Refresh)
        viewModel.onAction(GroupTasksAction.Refresh)
        assertEquals(1, repository.refreshTasksCalls)

        refreshCompletion.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()
        assertEquals(GroupTasksLoadState.CONTENT, viewModel.uiState.value.loadState)
    }

    @Test
    fun `while mutation is in progress, then selection actions cannot race its result`() = runTest(dispatcher) {
        val mutationCompletion = CompletableDeferred<CollaborationMutationResult>()
        repository.currentUserId = memberId
        repository.mutationCompletion = mutationCompletion
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.onAction(GroupTasksAction.OpenTask(taskId))
        viewModel.onAction(GroupTasksAction.StartTask)
        assertTrue(viewModel.uiState.value.isMutationInProgress)

        viewModel.onAction(GroupTasksAction.BackToGroups)
        viewModel.onAction(GroupTasksAction.OpenGroup(CollaborationGroupId("group-2")))
        assertEquals(groupId, viewModel.uiState.value.selectedGroupId)
        assertEquals(taskId, viewModel.uiState.value.selectedTaskId)

        mutationCompletion.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isMutationInProgress)
    }

    @Test
    fun `when group changes, then old task emissions cannot overwrite new selection`() = runTest(dispatcher) {
        repository.addGroup(CollaborationGroupId("group-2"))
        repository.setMembers(
            CollaborationGroupId("group-2"),
            listOf(member(CollaborationGroupId("group-2"), ownerId, GroupRole.OWNER))
        )
        repository.setDetails(
            CollaborationGroupId("group-2"),
            listOf(details(task(CollaborationGroupId("group-2"), GroupTaskId("task-2"), "Task 2")))
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAction(GroupTasksAction.OpenGroup(CollaborationGroupId("group-2")))
        advanceUntilIdle()
        repository.setDetails(groupId, listOf(details(task(status = GroupTaskStatus.COMPLETED))))
        advanceUntilIdle()

        assertEquals(CollaborationGroupId("group-2"), viewModel.uiState.value.selectedGroupId)
        assertEquals("Task 2", viewModel.uiState.value.tasks.single().task.title)
    }

    @Test
    fun `when group changes during refresh, then the new group starts its own refresh`() = runTest(dispatcher) {
        val firstRefresh = CompletableDeferred<CollaborationMutationResult>()
        repository.refreshTasksCompletion = firstRefresh
        repository.addGroup(CollaborationGroupId("group-2"))
        repository.setMembers(
            CollaborationGroupId("group-2"),
            listOf(member(CollaborationGroupId("group-2"), ownerId, GroupRole.OWNER))
        )
        repository.setDetails(
            CollaborationGroupId("group-2"),
            listOf(details(task(CollaborationGroupId("group-2"), GroupTaskId("task-2"), "Task 2")))
        )

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(1, repository.refreshTasksCalls)

        repository.refreshTasksCompletion = null
        viewModel.onAction(GroupTasksAction.OpenGroup(CollaborationGroupId("group-2")))
        advanceUntilIdle()

        assertEquals(2, repository.refreshTasksCalls)
        assertEquals(CollaborationGroupId("group-2"), viewModel.uiState.value.selectedGroupId)
        assertEquals("Task 2", viewModel.uiState.value.tasks.single().task.title)

        firstRefresh.complete(CollaborationMutationResult.Applied)
        advanceUntilIdle()
        assertEquals(CollaborationGroupId("group-2"), viewModel.uiState.value.selectedGroupId)
        assertEquals("Task 2", viewModel.uiState.value.tasks.single().task.title)
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle = SavedStateHandle()): GroupTasksViewModel {
        val viewModel = GroupTasksViewModel(
            repository = repository,
            clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
            idGenerator = idGenerator,
            savedStateHandle = savedStateHandle
        )
        if (savedStateHandle.get<String>(GroupTasksViewModel.SELECTED_GROUP_ID_KEY) == null) {
            viewModel.onAction(GroupTasksAction.OpenGroup(groupId))
        }
        return viewModel
    }

    private fun fillCreateEditor(viewModel: GroupTasksViewModel, title: String) {
        viewModel.onAction(GroupTasksAction.ChangeTitle(title))
        viewModel.onAction(GroupTasksAction.ChangeAssignee(memberId))
        viewModel.onAction(GroupTasksAction.ChangeDeadline(Instant.parse("2026-09-16T10:00:00Z")))
        viewModel.onAction(GroupTasksAction.ChangeReminderOffsets(listOf(3600L)))
    }
}

private class FakeGroupTaskIdGenerator : GroupTaskIdGenerator {
    private var sequence = 0

    override fun nextTaskId(groupId: CollaborationGroupId): GroupTaskId = GroupTaskId("generated-${++sequence}")
}

private class FakeCollaborationRepository(
    groups: List<CollaborationGroup>,
    members: List<GroupMember>,
    details: List<GroupTaskDetails>
) : CollaborationRepository {
    private val groupsFlow = MutableStateFlow(groups)
    private val memberFlows = mutableMapOf<CollaborationGroupId, MutableStateFlow<List<GroupMember>>>()
    private val detailFlows = mutableMapOf<CollaborationGroupId, MutableStateFlow<List<GroupTaskDetails>>>()
    private val nextMemberObservationFailures = mutableMapOf<CollaborationGroupId, Throwable>()
    private val nextTaskDetailsObservationFailures = mutableMapOf<CollaborationGroupId, Throwable>()
    private val identityFlow = MutableStateFlow<UserId?>(ownerId)
    var currentUserId: UserId?
        get() = identityFlow.value
        set(value) { identityFlow.value = value }
    var refreshTasksResult: CollaborationMutationResult = CollaborationMutationResult.Applied
    var refreshTasksCompletion: CompletableDeferred<CollaborationMutationResult>? = null
    var detailsInitialEmissionGate: CompletableDeferred<Unit>? = null
    var mutationResult: CollaborationMutationResult = CollaborationMutationResult.Applied
    var mutationCompletion: CompletableDeferred<CollaborationMutationResult>? = null
    var refreshTasksCalls = 0
        private set
    var lastCreateTask: CreateGroupTaskCommand? = null
        private set
    var createTaskCalls = 0
        private set
    var lastEditTask: EditGroupTaskCommand? = null
        private set
    var lastReassignTask: ReassignGroupTaskCommand? = null
        private set
    var lastStartTask: StartGroupTaskCommand? = null
        private set
    var startTaskCalls = 0
        private set
    var lastCompleteTask: CompleteGroupTaskCommand? = null
        private set
    var lastCancelTask: CancelGroupTaskCommand? = null
        private set
    var cancelTaskCalls = 0
        private set
    var lastReopenTask: ReopenGroupTaskCommand? = null
        private set
    var completeTaskCalls = 0
        private set
    var lastMutation: GroupTasksMutation? = null
        private set

    init {
        this.groupsFlow.value.forEach { group ->
            memberFlows[group.id] = MutableStateFlow(emptyList())
            detailFlows[group.id] = MutableStateFlow(emptyList())
        }
        setMembers(groups.firstOrNull()?.id ?: error("test group missing"), members)
        setDetails(groups.firstOrNull()?.id ?: error("test group missing"), details)
    }

    fun addGroup(id: CollaborationGroupId) {
        groupsFlow.value = groupsFlow.value + group(id)
        memberFlows.getOrPut(id) { MutableStateFlow(emptyList()) }
        detailFlows.getOrPut(id) { MutableStateFlow(emptyList()) }
    }

    fun setMembers(groupId: CollaborationGroupId, members: List<GroupMember>) {
        memberFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }.value = members
    }

    fun setDetails(groupId: CollaborationGroupId, details: List<GroupTaskDetails>) {
        detailFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }.value = details
    }

    fun failNextMemberObservation(groupId: CollaborationGroupId, throwable: Throwable) {
        nextMemberObservationFailures[groupId] = throwable
    }

    fun failNextTaskDetailsObservation(groupId: CollaborationGroupId, throwable: Throwable) {
        nextTaskDetailsObservationFailures[groupId] = throwable
    }

    override fun currentUserId(): UserId? = currentUserId

    override fun observeCurrentUserId(): Flow<UserId?> = identityFlow.asStateFlow()

    override fun observeGroups(): Flow<List<CollaborationGroup>> = groupsFlow.asStateFlow()

    override fun observeGroup(groupId: CollaborationGroupId): Flow<CollaborationGroup?> =
        groupsFlow.map { groups -> groups.firstOrNull { it.id == groupId } }

    override fun observeMembers(groupId: CollaborationGroupId): Flow<List<GroupMember>> {
        val membersFlow = memberFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }.asStateFlow()
        return flow {
            nextMemberObservationFailures.remove(groupId)?.let { throw it }
            emitAll(membersFlow)
        }
    }

    override fun observeTasks(groupId: CollaborationGroupId): Flow<List<GroupTask>> =
        observeTaskDetails(groupId).map { details -> details.map { it.task } }

    override fun observeTaskDetails(groupId: CollaborationGroupId): Flow<List<GroupTaskDetails>> {
        val detailsFlow = detailFlows.getOrPut(groupId) { MutableStateFlow(emptyList()) }.asStateFlow()
        return flow {
            nextTaskDetailsObservationFailures.remove(groupId)?.let { throw it }
            detailsInitialEmissionGate?.await()
            emitAll(detailsFlow)
        }
    }

    override fun observeInvites(): Flow<List<GroupInvite>> = MutableStateFlow(emptyList())

    override suspend fun refreshTasks(groupId: CollaborationGroupId): CollaborationMutationResult {
        refreshTasksCalls += 1
        return refreshTasksCompletion?.await() ?: refreshTasksResult
    }

    override suspend fun refreshGroups() = CollaborationMutationResult.Applied
    override suspend fun refreshGroup(groupId: CollaborationGroupId) = CollaborationMutationResult.Applied
    override suspend fun refreshInvites() = CollaborationMutationResult.Applied
    override suspend fun clearSessionCache() = Unit
    override suspend fun createGroup(command: CreateGroupCommand) = CollaborationMutationResult.Applied
    override suspend fun updateGroup(command: UpdateGroupCommand) = CollaborationMutationResult.Applied
    override suspend fun inviteMember(command: InviteMemberCommand) = CollaborationMutationResult.Applied
    override suspend fun acceptInvite(inviteId: GroupInviteId) = CollaborationMutationResult.Applied
    override suspend fun declineInvite(inviteId: GroupInviteId) = CollaborationMutationResult.Applied
    override suspend fun changeMemberRole(command: ChangeMemberRoleCommand) = CollaborationMutationResult.Applied
    override suspend fun removeMember(command: RemoveMemberCommand) = CollaborationMutationResult.Applied
    override suspend fun transferOwnership(command: TransferOwnershipCommand) = CollaborationMutationResult.Applied
    override suspend fun leaveGroup(groupId: CollaborationGroupId) = CollaborationMutationResult.Applied
    override suspend fun deleteGroup(groupId: CollaborationGroupId) = CollaborationMutationResult.Applied

    private suspend fun mutation(type: GroupTasksMutation): CollaborationMutationResult {
        lastMutation = type
        return mutationCompletion?.await() ?: mutationResult
    }

    override suspend fun createTask(command: CreateGroupTaskCommand): CollaborationMutationResult {
        lastCreateTask = command
        createTaskCalls += 1
        return mutation(GroupTasksMutation.CREATE)
    }

    override suspend fun editTask(command: EditGroupTaskCommand): CollaborationMutationResult {
        lastEditTask = command
        return mutation(GroupTasksMutation.EDIT)
    }

    override suspend fun reassignTask(command: ReassignGroupTaskCommand): CollaborationMutationResult {
        lastReassignTask = command
        return mutation(GroupTasksMutation.REASSIGN)
    }

    override suspend fun startTask(command: StartGroupTaskCommand): CollaborationMutationResult {
        lastStartTask = command
        startTaskCalls += 1
        return mutation(GroupTasksMutation.START)
    }

    override suspend fun completeTask(command: CompleteGroupTaskCommand): CollaborationMutationResult {
        lastCompleteTask = command
        completeTaskCalls += 1
        return mutation(GroupTasksMutation.COMPLETE)
    }

    override suspend fun cancelTask(command: CancelGroupTaskCommand): CollaborationMutationResult {
        lastCancelTask = command
        cancelTaskCalls += 1
        return mutation(GroupTasksMutation.CANCEL)
    }

    override suspend fun reopenTask(command: ReopenGroupTaskCommand): CollaborationMutationResult {
        lastReopenTask = command
        return mutation(GroupTasksMutation.REOPEN)
    }
}

private val ownerId = UserId("owner-1")

private fun group(id: CollaborationGroupId) = CollaborationGroup(
    id = id,
    name = "Household",
    createdBy = ownerId,
    createdAt = Instant.parse("2026-09-01T00:00:00Z"),
    updatedAt = Instant.parse("2026-09-01T00:00:00Z")
)

private fun member(groupId: CollaborationGroupId, userId: UserId, role: GroupRole) = GroupMember(
    groupId = groupId,
    userId = userId,
    role = role,
    joinedAt = Instant.parse("2026-09-01T00:00:00Z"),
    displayName = userId.value
)

private fun task(
    groupId: CollaborationGroupId = CollaborationGroupId("group-1"),
    taskId: GroupTaskId = GroupTaskId("task-1"),
    title: String = "Task 1",
    assigneeId: UserId = UserId("member-1"),
    status: GroupTaskStatus = GroupTaskStatus.TODO
) = GroupTask(
    id = taskId,
    groupId = groupId,
    title = title,
    createdBy = ownerId,
    assigneeId = assigneeId,
    dueAt = Instant.parse("2026-09-15T10:00:00Z"),
    status = status,
    version = 3L,
    createdAt = Instant.parse("2026-09-10T00:00:00Z"),
    updatedAt = Instant.parse("2026-09-11T00:00:00Z")
)

private fun details(task: GroupTask) = GroupTaskDetails(
    task = task,
    reminders = listOf(
        GroupTaskReminder(task.id, 3600L),
        GroupTaskReminder(task.id, 7200L)
    )
)
