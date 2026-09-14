package com.smartreminder.ui.groups.tasks

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smartreminder.R
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskDetails
import com.smartreminder.domain.model.collaboration.GroupTaskReminder
import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.ui.theme.SmartReminderTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupTasksScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val groupId = CollaborationGroupId("group-1")
    private val taskId = GroupTaskId("task-1")
    private val ownerId = UserId("owner-1")
    private val memberId = UserId("member-1")

    @Test
    fun taskRowShowsAbsoluteMetadataStatusAndOverdueSemantics() {
        val task = task(status = GroupTaskStatus.TODO, dueAt = Instant.parse("2026-09-13T10:30:00Z"))
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            selectedGroupId = groupId,
            tasks = listOf(
                GroupTaskListItemUiModel(
                    task = task,
                    assignee = member(memberId, "Lin"),
                    isOverdue = true,
                    permissions = GroupTaskPermissions(canStart = true)
                )
            )
        )

        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = {})
            }
        }

        composeRule.onNodeWithText("Prepare slides").assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_task_assignee, "Lin")
        ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_status_todo))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_overdue))
            .assertIsDisplayed()
        composeRule.onNode(hasText("Due", substring = true)).assertIsDisplayed()

        val row = composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.groups_task_open_description, task.title)
        )
        row.assertIsDisplayed()
        assertEquals(
            LiveRegionMode.Polite,
            composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_overdue))
                .fetchSemanticsNode().config[SemanticsProperties.LiveRegion]
        )
    }

    @Test
    fun taskRowDispatchesOpenTaskAction() {
        val actions = mutableListOf<GroupTasksAction>()
        val task = task()
        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(
                    uiState = GroupTasksUiState(
                        loadState = GroupTasksLoadState.CONTENT,
                        selectedGroupId = groupId,
                        tasks = listOf(
                            GroupTaskListItemUiModel(
                                task = task,
                                assignee = member(memberId, "Lin"),
                                isOverdue = false,
                                permissions = GroupTaskPermissions()
                            )
                        )
                    ),
                    onAction = { actions += it }
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.groups_task_open_description, task.title)
        ).performClick()

        assertEquals(listOf(GroupTasksAction.OpenTask(task.id)), actions)
    }

    @Test
    fun detailActionsArePermissionGatedAndCancelRequiresConfirmation() {
        val actions = mutableListOf<GroupTasksAction>()
        val task = task(status = GroupTaskStatus.TODO)
        val detail = GroupTaskDetailUiModel(
            details = GroupTaskDetails(task, reminders = listOf(GroupTaskReminder(task.id, 900L))),
            assignee = member(memberId, "Lin"),
            isOverdue = false,
            permissions = GroupTaskPermissions(
                canEdit = true,
                canReassign = true,
                canStart = true,
                canComplete = false,
                canCancel = true,
                canReopen = false
            )
        )
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            detailLoadState = GroupTaskDetailLoadState.CONTENT,
            screen = GroupTasksScreen.DETAIL,
            selectedGroupId = groupId,
            selectedTaskId = task.id,
            members = listOf(member(ownerId, "Ari"), member(memberId, "Lin")),
            selectedTask = detail
        )

        var renderedState by mutableStateOf(state)
        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(
                    uiState = renderedState,
                    onAction = {
                        actions += it
                        if (it == GroupTasksAction.OpenCancelConfirmation) {
                            renderedState = renderedState.copy(
                                confirmation = GroupTaskConfirmation.Cancel(task.id)
                            )
                        }
                    }
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_start)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_edit)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_cancel)).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_cancel_confirm_message))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_confirm)).performClick()

        assertTrue(actions.contains(GroupTasksAction.StartTask))
        assertTrue(actions.contains(GroupTasksAction.OpenEditTask))
        assertTrue(actions.contains(GroupTasksAction.OpenCancelConfirmation))
        assertTrue(actions.contains(GroupTasksAction.ConfirmCancel))
        assertTrue(actions.none { it == GroupTasksAction.CompleteTask })
        assertTrue(actions.none { it == GroupTasksAction.OpenReopenConfirmation })
    }

    @Test
    fun confirmationStateRendersLocalizedCopyAndDispatchesConfirm() {
        val actions = mutableListOf<GroupTasksAction>()
        val task = task(status = GroupTaskStatus.TODO)
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            detailLoadState = GroupTaskDetailLoadState.CONTENT,
            screen = GroupTasksScreen.DETAIL,
            selectedGroupId = groupId,
            selectedTaskId = task.id,
            confirmation = GroupTaskConfirmation.Cancel(task.id),
            selectedTask = GroupTaskDetailUiModel(
                details = GroupTaskDetails(task),
                assignee = member(memberId, "Lin"),
                isOverdue = false,
                permissions = GroupTaskPermissions(canCancel = true)
            )
        )

        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = { actions += it })
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_task_cancel_confirm_message)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_confirm))
            .performClick()

        assertEquals(listOf(GroupTasksAction.ConfirmCancel), actions)
    }

    @Test
    fun standaloneDetailScrollsAtTwoHundredPercentFontScale() {
        val task = task()
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            detailLoadState = GroupTaskDetailLoadState.CONTENT,
            screen = GroupTasksScreen.DETAIL,
            selectedGroupId = groupId,
            selectedTaskId = task.id,
            selectedTask = GroupTaskDetailUiModel(
                details = GroupTaskDetails(task),
                assignee = member(memberId, "Lin"),
                isOverdue = false,
                permissions = GroupTaskPermissions(canEdit = true)
            )
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides androidx.compose.ui.unit.Density(1f, 2f)) {
                SmartReminderTheme {
                    GroupTasksContent(uiState = state, onAction = {}, embedded = false)
                }
            }
        }

        composeRule.onNode(hasScrollAction()).assertExists()
    }

    @Test
    fun embeddedDetailDoesNotAddSecondScrollContainer() {
        val task = task()
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            detailLoadState = GroupTaskDetailLoadState.CONTENT,
            screen = GroupTasksScreen.DETAIL,
            selectedGroupId = groupId,
            selectedTaskId = task.id,
            selectedTask = GroupTaskDetailUiModel(
                details = GroupTaskDetails(task),
                assignee = member(memberId, "Lin"),
                isOverdue = false,
                permissions = GroupTaskPermissions(canEdit = true)
            )
        )

        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = {}, embedded = true)
            }
        }

        composeRule.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }

    @Test
    fun detailBackDispatchesBackFromTask() {
        val actions = mutableListOf<GroupTasksAction>()
        val task = task()
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            detailLoadState = GroupTaskDetailLoadState.CONTENT,
            screen = GroupTasksScreen.DETAIL,
            selectedGroupId = groupId,
            selectedTaskId = task.id,
            selectedTask = GroupTaskDetailUiModel(
                details = GroupTaskDetails(task),
                assignee = member(memberId, "Lin"),
                isOverdue = false,
                permissions = GroupTaskPermissions()
            )
        )
        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = { actions += it })
            }
        }

        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(GroupTasksAction.BackFromTask), actions)
    }

    @Test
    fun editorBackDispatchesCancelEditor() {
        val actions = mutableListOf<GroupTasksAction>()
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            screen = GroupTasksScreen.EDITOR,
            selectedGroupId = groupId,
            members = listOf(member(ownerId, "Ari")),
            editor = GroupTaskEditorUiState(
                mode = GroupTaskEditorMode.Create,
                title = "Prepare slides",
                assigneeId = ownerId,
                dueAt = Instant.parse("2026-09-16T10:00:00Z"),
                reminderOffsetsSeconds = listOf(900L)
            )
        )
        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = { actions += it })
            }
        }

        composeRule.runOnIdle {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(GroupTasksAction.CancelEditor), actions)
    }

    @Test
    fun loadingDetailKeepsBackAffordanceAndDispatchesBack() {
        val actions = mutableListOf<GroupTasksAction>()
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.LOADING,
            detailLoadState = GroupTaskDetailLoadState.LOADING,
            screen = GroupTasksScreen.DETAIL,
            selectedGroupId = groupId,
            selectedTaskId = taskId
        )
        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = { actions += it })
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.groups_task_back_description)
        ).assertIsDisplayed().performClick()

        assertEquals(listOf(GroupTasksAction.BackFromTask), actions)
    }

    @Test
    fun deadlineDatePickerRendersMaterialDialogCopy() {
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            screen = GroupTasksScreen.EDITOR,
            selectedGroupId = groupId,
            members = listOf(member(ownerId, "Ari")),
            editor = GroupTaskEditorUiState(
                mode = GroupTaskEditorMode.Create,
                assigneeId = ownerId,
                reminderOffsetsSeconds = listOf(900L)
            )
        )
        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = {})
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_task_change_deadline)
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_task_date_picker_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_task_picker_cancel)
        ).performClick()
    }

    @Test
    fun deadlineTimePickerRendersMaterialDialogCopy() {
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            screen = GroupTasksScreen.EDITOR,
            selectedGroupId = groupId,
            members = listOf(member(ownerId, "Ari")),
            editor = GroupTaskEditorUiState(
                mode = GroupTaskEditorMode.Create,
                assigneeId = ownerId,
                dueAt = Instant.parse("2026-09-16T10:00:00Z"),
                reminderOffsetsSeconds = listOf(900L)
            )
        )
        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = {})
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_task_change_time)
        ).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_task_time_picker_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_task_picker_cancel)
        ).performClick()
    }

    @Test
    fun destructiveConfirmationRendersInDarkTheme() {
        val task = task(status = GroupTaskStatus.TODO)
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            detailLoadState = GroupTaskDetailLoadState.CONTENT,
            screen = GroupTasksScreen.DETAIL,
            selectedGroupId = groupId,
            selectedTaskId = task.id,
            confirmation = GroupTaskConfirmation.Cancel(task.id),
            selectedTask = GroupTaskDetailUiModel(
                details = GroupTaskDetails(task),
                assignee = member(memberId, "Lin"),
                isOverdue = false,
                permissions = GroupTaskPermissions(canCancel = true)
            )
        )
        composeRule.setContent {
            SmartReminderTheme(darkTheme = true) {
                GroupTasksContent(uiState = state, onAction = {})
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_task_cancel_confirm_title)
        ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_confirm))
            .assertIsDisplayed()
    }

    @Test
    fun unauthorizedDetailActionsAreNotRendered() {
        val task = task()
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            detailLoadState = GroupTaskDetailLoadState.CONTENT,
            screen = GroupTasksScreen.DETAIL,
            selectedGroupId = groupId,
            selectedTaskId = task.id,
            selectedTask = GroupTaskDetailUiModel(
                details = GroupTaskDetails(task),
                assignee = member(memberId, "Lin"),
                isOverdue = false,
                permissions = GroupTaskPermissions()
            )
        )
        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = {})
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_start))
            .assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_complete))
            .assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_cancel))
            .assertDoesNotExist()
    }

    @Test
    fun editorExposesMemberDeadlineAndOneToFiveReminderChoices() {
        val actions = mutableListOf<GroupTasksAction>()
        val editor = GroupTaskEditorUiState(
            mode = GroupTaskEditorMode.Create,
            title = "Prepare slides",
            assigneeId = memberId,
            dueAt = Instant.parse("2026-09-16T10:00:00Z"),
            reminderOffsetsSeconds = listOf(900L)
        )
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CONTENT,
            screen = GroupTasksScreen.EDITOR,
            selectedGroupId = groupId,
            members = listOf(member(ownerId, "Ari"), member(memberId, "Lin")),
            editor = editor
        )

        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = { actions += it })
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.groups_task_assignee, "Lin")
        ).performClick()
        composeRule.onNodeWithText("Ari", substring = false).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_reminder_15_minutes))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_save)).assertIsDisplayed()

        assertTrue(actions.contains(GroupTasksAction.ChangeAssignee(ownerId)))
        assertTrue(actions.any { it is GroupTasksAction.ChangeReminderOffsets })
    }

    @Test
    fun cachedOfflineErrorShowsRetryAndPendingMutationDisablesActions() {
        val actions = mutableListOf<GroupTasksAction>()
        val task = task()
        val state = GroupTasksUiState(
            loadState = GroupTasksLoadState.CACHED_OFFLINE,
            detailLoadState = GroupTaskDetailLoadState.CACHED_OFFLINE,
            screen = GroupTasksScreen.DETAIL,
            selectedGroupId = groupId,
            selectedTaskId = task.id,
            tasks = listOf(
                GroupTaskListItemUiModel(
                    task = task,
                    assignee = member(memberId, "Lin"),
                    isOverdue = false,
                    permissions = GroupTaskPermissions()
                )
            ),
            selectedTask = GroupTaskDetailUiModel(
                details = GroupTaskDetails(task),
                assignee = member(memberId, "Lin"),
                isOverdue = false,
                permissions = GroupTaskPermissions(canStart = true)
            ),
            isCached = true,
            isOffline = true,
            error = GroupTasksUiError.Offline,
            detailError = GroupTasksUiError.Offline,
            pendingMutation = PendingGroupTaskMutation(GroupTasksMutation.START, groupId, task.id)
        )

        composeRule.setContent {
            SmartReminderTheme {
                GroupTasksContent(uiState = state, onAction = { actions += it })
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_offline_cached))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_retry))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_task_start))
            .assertIsNotEnabled()
        assertTrue(actions.contains(GroupTasksAction.Retry))
    }

    private fun task(
        status: GroupTaskStatus = GroupTaskStatus.TODO,
        dueAt: Instant = Instant.parse("2026-09-16T10:00:00Z")
    ): GroupTask = GroupTask(
        id = taskId,
        groupId = groupId,
        title = "Prepare slides",
        description = "Review the deck",
        createdBy = ownerId,
        assigneeId = memberId,
        dueAt = dueAt,
        status = status,
        version = 3L,
        createdAt = Instant.parse("2026-09-10T00:00:00Z"),
        updatedAt = Instant.parse("2026-09-10T00:00:00Z")
    )

    private fun member(id: UserId, name: String): GroupMember = GroupMember(
        groupId = groupId,
        userId = id,
        role = if (id == ownerId) GroupRole.OWNER else GroupRole.MEMBER,
        joinedAt = Instant.parse("2026-09-10T00:00:00Z"),
        displayName = name
    )
}
