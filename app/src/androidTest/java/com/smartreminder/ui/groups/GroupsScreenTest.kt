package com.smartreminder.ui.groups

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import com.smartreminder.R
import com.smartreminder.domain.collaboration.GroupPermissions
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupInviteStatus
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.GroupTask
import com.smartreminder.domain.model.collaboration.GroupTaskStatus
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.GroupInviteId
import com.smartreminder.domain.model.collaboration.ids.GroupTaskId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.ui.groups.tasks.GroupTaskListItemUiModel
import com.smartreminder.ui.groups.tasks.GroupTaskPermissions
import com.smartreminder.ui.groups.tasks.GroupTasksAction
import com.smartreminder.ui.groups.tasks.GroupTasksLoadState
import com.smartreminder.ui.groups.tasks.GroupTasksUiState
import com.smartreminder.ui.theme.SmartReminderTheme
import java.time.Instant
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GroupsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun listRendersRealGroupsAndDispatchesOpenGroup() {
        val group = group("group-1", "Household")
        val actions = mutableListOf<GroupsAction>()

        composeRule.setContent {
            SmartReminderTheme {
                GroupsListScreen(
                    uiState = GroupsUiState(
                        loadState = GroupsLoadState.CONTENT,
                        groups = listOf(group)
                    ),
                    onAction = { action -> actions += action }
                )
            }
        }

        composeRule.onNodeWithText("Household").assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_new_group)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.groups_open_group_description, group.name)
        ).performClick()

        assertEquals(listOf(GroupsAction.OpenGroup(group.id)), actions)
    }

    @Test
    fun listShowsOfflineRefreshingAndRetryState() {
        val actions = mutableListOf<GroupsAction>()

        composeRule.setContent {
            SmartReminderTheme {
                GroupsListScreen(
                    uiState = GroupsUiState(
                        loadState = GroupsLoadState.CACHED_OFFLINE,
                        groups = listOf(group("group-1", "Cached")),
                        isCached = true,
                        isOffline = true
                    ),
                    onAction = { action -> actions += action }
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_offline_cached)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_retry)).performClick()
        assertTrue(actions.contains(GroupsAction.Refresh))
    }

    @Test
    fun offlineStatusIsAnnouncedAsPoliteLiveRegion() {
        composeRule.setContent {
            SmartReminderTheme {
                GroupsListScreen(
                    uiState = GroupsUiState(
                        loadState = GroupsLoadState.CACHED_OFFLINE,
                        groups = listOf(group("group-1", "Cached")),
                        isCached = true,
                        isOffline = true
                    ),
                    onAction = {}
                )
            }
        }

        val messageNode = composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_offline_cached)
        ).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, messageNode.config[SemanticsProperties.LiveRegion])
    }

    @Test
    fun detailHidesUnauthorizedMembershipActionsAndShowsTaskSection() {
        val group = group("group-1", "Household")
        val actor = member(group.id.value, "member-1", GroupRole.MEMBER)
        val state = GroupsUiState(
            loadState = GroupsLoadState.CONTENT,
            screen = GroupsScreen.DETAIL,
            selectedGroupId = group.id,
            selectedGroup = GroupDetailUiModel(
                group = group,
                members = listOf(actor),
                currentUserId = actor.userId,
                currentUserRole = GroupRole.MEMBER,
                actorPermissions = GroupPermissions(
                    canEditGroup = false,
                    canInviteMember = false,
                    canChangeRoles = false,
                    canTransferOwnership = false,
                    canDeleteGroup = false
                ),
                permissionsByMemberId = mapOf(
                    actor.userId to GroupPermissions(
                        canEditGroup = false,
                        canInviteMember = false,
                        canChangeRoles = false,
                        canTransferOwnership = false,
                        canDeleteGroup = false
                    )
                ),
                memberActionsByMemberId = mapOf(
                    actor.userId to GroupMemberUiPermissions(
                        canChangeRole = false,
                        canRemove = false,
                        canTransferOwnership = false
                    )
                ),
                canLeaveGroup = true
            )
        )

        composeRule.setContent {
            SmartReminderTheme {
                GroupDetailScreen(
                    uiState = state,
                    onAction = {},
                    taskUiState = GroupTasksUiState(),
                    onTaskAction = {}
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_tasks_title)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_tasks_empty_title)).assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.groups_invite_member))
                .fetchSemanticsNodes().size
        )
        assertEquals(
            0,
            composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.groups_manage_group))
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun detailEmbedsSuppliedTaskStateAndForwardsTaskActions() {
        val group = group("group-1", "Household")
        val assignee = member(group.id.value, "member-1", GroupRole.MEMBER)
        val task = GroupTask(
            id = GroupTaskId("task-1"),
            groupId = group.id,
            title = "Prepare slides",
            createdBy = assignee.userId,
            assigneeId = assignee.userId,
            dueAt = Instant.parse("2026-09-16T10:00:00Z"),
            status = GroupTaskStatus.TODO,
            version = 1L,
            createdAt = Instant.parse("2026-09-15T09:00:00Z"),
            updatedAt = Instant.parse("2026-09-15T09:00:00Z")
        )
        val taskActions = mutableListOf<GroupTasksAction>()

        composeRule.setContent {
            SmartReminderTheme {
                GroupDetailScreen(
                    uiState = detailState(group, listOf(assignee)),
                    onAction = {},
                    taskUiState = GroupTasksUiState(
                        loadState = GroupTasksLoadState.CONTENT,
                        selectedGroupId = group.id,
                        members = listOf(assignee),
                        tasks = listOf(
                            GroupTaskListItemUiModel(
                                task = task,
                                assignee = assignee,
                                isOverdue = false,
                                permissions = GroupTaskPermissions()
                            )
                        )
                    ),
                    onTaskAction = { taskActions += it }
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.groups_task_open_description, task.title)
        ).performClick()

        assertEquals(listOf(GroupTasksAction.OpenTask(task.id)), taskActions)
    }

    @Test
    fun taskDetailUsesStableFullContentShellOutsideGroupDetailList() {
        val group = group("group-1", "Household")
        val actor = member(group.id.value, "owner-1", GroupRole.OWNER)
        val task = GroupTask(
            id = GroupTaskId("task-1"),
            groupId = group.id,
            title = "Prepare slides",
            createdBy = actor.userId,
            assigneeId = actor.userId,
            dueAt = Instant.parse("2026-09-16T10:00:00Z"),
            status = GroupTaskStatus.TODO,
            version = 4L,
            createdAt = Instant.parse("2026-09-15T09:00:00Z"),
            updatedAt = Instant.parse("2026-09-15T09:00:00Z")
        )

        composeRule.setContent {
            SmartReminderTheme {
                GroupDetailScreen(
                    uiState = GroupsUiState(
                        screen = GroupsScreen.DETAIL,
                        loadState = GroupsLoadState.LOADING
                    ),
                    onAction = {},
                    taskUiState = GroupTasksUiState(
                        loadState = GroupTasksLoadState.CONTENT,
                        screen = com.smartreminder.ui.groups.tasks.GroupTasksScreen.DETAIL,
                        selectedGroupId = group.id,
                        selectedTaskId = task.id,
                        selectedTask = com.smartreminder.ui.groups.tasks.GroupTaskDetailUiModel(
                            details = com.smartreminder.domain.model.collaboration.GroupTaskDetails(task),
                            assignee = actor,
                            isOverdue = false,
                            permissions = com.smartreminder.ui.groups.tasks.GroupTaskPermissions()
                        )
                    ),
                    onTaskAction = {}
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_members_heading)
        ).assertDoesNotExist()
        composeRule.onNodeWithText(task.title).assertIsDisplayed()
    }

    @Test
    fun createDialogDispatchesTypedCreateAction() {
        val actions = mutableListOf<GroupsAction>()

        composeRule.setContent {
            SmartReminderTheme {
                GroupsDialogHost(
                    dialog = GroupsDialog.CreateGroup,
                    uiState = GroupsUiState(dialog = GroupsDialog.CreateGroup),
                    onAction = { action -> actions += action }
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_group_name))
            .performTextInput("Weekend plans")
        composeRule.onNode(
            hasText(composeRule.activity.getString(R.string.groups_confirm_create_group))
                .and(hasClickAction())
        ).performClick()

        assertEquals(
            GroupsAction.CreateGroup(name = "Weekend plans", description = null),
            actions.single { it is GroupsAction.CreateGroup }
        )
    }

    @Test
    fun pendingInviteRemainsActionableWhileGroupsAreLoading() {
        val invite = invite("invite-1", "group-1")
        val actions = mutableListOf<GroupsAction>()

        composeRule.setContent {
            SmartReminderTheme {
                GroupsListScreen(
                    uiState = GroupsUiState(
                        loadState = GroupsLoadState.LOADING,
                        pendingInvites = listOf(invite)
                    ),
                    onAction = { action -> actions += action }
                )
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(
                R.string.groups_invite_unknown_group,
                invite.groupId.value
            )
        ).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_review_invite))
            .performClick()
        assertTrue(actions.contains(GroupsAction.OpenInviteResponseDialog(invite.id)))

    }

    @Test
    fun pendingInviteRemainsActionableWhenGroupsFailed() {
        val invite = invite("invite-1", "group-1")
        val actions = mutableListOf<GroupsAction>()

        composeRule.setContent {
            SmartReminderTheme {
                GroupsListScreen(
                    uiState = GroupsUiState(
                        loadState = GroupsLoadState.ERROR,
                        error = GroupsUiError.Offline,
                        pendingInvites = listOf(invite)
                    ),
                    onAction = { action -> actions += action }
                )
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_review_invite))
            .performClick()
        assertTrue(actions.contains(GroupsAction.OpenInviteResponseDialog(invite.id)))
    }

    @Test
    fun detailErrorShowsRetryAndKeepsCachedMemberContent() {
        val group = group("group-1", "Household")
        val actor = member(group.id.value, "owner-1", GroupRole.OWNER, avatarUrl = null)
        val actions = mutableListOf<GroupsAction>()
        val state = detailState(
            group = group,
            members = listOf(actor),
            detailLoadState = GroupsDetailLoadState.CACHED_OFFLINE,
            detailError = GroupsUiError.Offline
        )

        composeRule.setContent {
            SmartReminderTheme {
                GroupDetailScreen(
                    uiState = state,
                    onAction = { actions += it },
                    taskUiState = GroupTasksUiState(),
                    onTaskAction = {}
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_error_offline))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_retry))
            .performClick()
        assertEquals(listOf(GroupsAction.OpenGroup(group.id)), actions)
        composeRule.onNodeWithText("A").assertIsDisplayed()
    }

    @Test
    fun unauthorizedDetailRedactionReturnsListWithoutPrivateContent() {
        val group = group("group-1", "Household")
        val source = GroupsUiState(
            loadState = GroupsLoadState.ERROR,
            error = GroupsUiError.NotAuthorized,
            groups = emptyList(),
            selectedGroupId = null,
            selectedGroup = null
        )
        composeRule.setContent {
            SmartReminderTheme {
                GroupsListScreen(uiState = source, onAction = {})
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_error_not_authorized)
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Household").assertCountEquals(0)
        composeRule.onAllNodesWithText("Ari").assertCountEquals(0)
    }

    @Test
    fun notFoundDetailRedactionReturnsListWithoutPrivateContent() {
        val group = group("group-1", "Household")
        val source = GroupsUiState(
            loadState = GroupsLoadState.ERROR,
            error = GroupsUiError.NotFound,
            groups = emptyList(),
            selectedGroupId = null,
            selectedGroup = null
        )
        composeRule.setContent {
            SmartReminderTheme {
                GroupsListScreen(uiState = source, onAction = {})
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_error_not_found)
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Household").assertCountEquals(0)
        composeRule.onAllNodesWithText("Ari").assertCountEquals(0)
    }

    @Test
    fun revokedDetailRemainsRedactedInsteadOfRetainingAReadOnlyCopy() {
        val state = GroupsUiState(
            loadState = GroupsLoadState.ERROR,
            error = GroupsUiError.NotAuthorized,
            groups = emptyList(),
            selectedGroupId = null,
            selectedGroup = null,
            detailLoadState = GroupsDetailLoadState.IDLE
        )
        composeRule.setContent {
            SmartReminderTheme {
                GroupsListScreen(uiState = state, onAction = {})
            }
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_error_not_authorized)
        ).assertIsDisplayed()
        composeRule.onAllNodesWithText("Household").assertCountEquals(0)
        composeRule.onAllNodesWithText("Ari").assertCountEquals(0)
    }

    @Test
    fun roleOptionsExposeMergedRadioButtonAndSelectedSemantics() {
        val group = group("group-1", "Household")
        val target = member(group.id.value, "member-1", GroupRole.MEMBER)
        composeRule.setContent {
            SmartReminderTheme {
                GroupsDialogHost(
                    dialog = GroupsDialog.ChangeMemberRole(group.id, target.userId),
                    uiState = GroupsUiState(
                        dialog = GroupsDialog.ChangeMemberRole(group.id, target.userId),
                        selectedGroup = detailState(group, listOf(target)).selectedGroup
                    ),
                    onAction = {}
                )
            }
        }

        val adminNode = composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_role_admin)
        ).fetchSemanticsNode()
        assertEquals(Role.RadioButton, adminNode.config[SemanticsProperties.Role])
        assertEquals(true, adminNode.config[SemanticsProperties.Selected])
    }

    @Test
    fun inviteResponseUsesSeparateAcceptConfirmationState() {
        val invite = invite("invite-1", "group-1")
        val actions = mutableListOf<GroupsAction>()

        composeRule.setContent {
            SmartReminderTheme {
                GroupsDialogHost(
                    dialog = GroupsDialog.RespondToInvite(invite.id),
                    uiState = GroupsUiState(
                        dialog = GroupsDialog.RespondToInvite(invite.id),
                        pendingInvites = listOf(invite)
                    ),
                    onAction = { actions += it }
                )
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_accept_invite))
            .performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_confirm_invite_accept_message)
        ).assertIsDisplayed()
        assertFalse(actions.any { it == GroupsAction.AcceptInvite(invite.id) })
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_accept_invite))
            .performClick()
        assertTrue(actions.contains(GroupsAction.AcceptInvite(invite.id)))
    }

    @Test
    fun inviteResponseUsesSeparateDeclineConfirmationState() {
        val invite = invite("invite-1", "group-1")
        val actions = mutableListOf<GroupsAction>()
        composeRule.setContent {
            SmartReminderTheme {
                GroupsDialogHost(
                    dialog = GroupsDialog.RespondToInvite(invite.id),
                    uiState = GroupsUiState(
                        dialog = GroupsDialog.RespondToInvite(invite.id),
                        pendingInvites = listOf(invite)
                    ),
                    onAction = { actions += it }
                )
            }
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_decline_invite))
            .performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.groups_confirm_invite_decline_message)
        ).assertIsDisplayed()
        assertFalse(actions.any { it == GroupsAction.DeclineInvite(invite.id) })
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_decline_invite))
            .performClick()
        assertTrue(actions.contains(GroupsAction.DeclineInvite(invite.id)))
    }

    @Test
    fun validationErrorIsVisibleAndMarkedAsSemanticsErrorInsideDialog() {
        val actions = mutableListOf<GroupsAction>()
        composeRule.setContent {
            SmartReminderTheme {
                GroupsDialogHost(
                    dialog = GroupsDialog.CreateGroup,
                    uiState = GroupsUiState(
                        dialog = GroupsDialog.CreateGroup,
                        error = GroupsUiError.Validation(GroupsValidationKind.GROUP_NAME_REQUIRED)
                    ),
                    onAction = { actions += it }
                )
            }
        }

        val expectedMessage = composeRule.activity.getString(R.string.groups_error_group_name_required)
        val errorNode = composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_group_name))
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertEquals(expectedMessage, errorNode.config[SemanticsProperties.Error])
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_group_name))
            .performTextInput("A name")
        assertTrue(actions.contains(GroupsAction.DismissError))
    }

    @Test
    fun blankGroupValidationUsesVietnameseResourceAndErrorSemantics() {
        val viContext = composeRule.activity.createConfigurationContext(
            Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(Locale("vi"))
            }
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides viContext) {
                SmartReminderTheme {
                    GroupsDialogHost(
                        dialog = GroupsDialog.CreateGroup,
                        uiState = GroupsUiState(
                            dialog = GroupsDialog.CreateGroup,
                            error = GroupsUiError.Validation(GroupsValidationKind.GROUP_NAME_REQUIRED)
                        ),
                        onAction = {}
                    )
                }
            }
        }

        val expectedMessage = viContext.getString(R.string.groups_error_group_name_required)
        val errorNode = composeRule.onNodeWithText(viContext.getString(R.string.groups_group_name))
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertEquals(expectedMessage, errorNode.config[SemanticsProperties.Error])
    }

    @Test
    fun invalidEmailValidationUsesVietnameseResourceWithoutRawDiagnostic() {
        val viContext = composeRule.activity.createConfigurationContext(
            Configuration(composeRule.activity.resources.configuration).apply {
                setLocale(Locale("vi"))
            }
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalContext provides viContext) {
                SmartReminderTheme {
                    GroupsDialogHost(
                        dialog = GroupsDialog.InviteMember(CollaborationGroupId("group-1")),
                        uiState = GroupsUiState(
                            dialog = GroupsDialog.InviteMember(CollaborationGroupId("group-1")),
                            error = GroupsUiError.Validation(
                                kind = GroupsValidationKind.EMAIL_INVALID,
                                diagnostic = "server says invite email is invalid"
                            )
                        ),
                        onAction = {}
                    )
                }
            }
        }

        val expectedMessage = viContext.getString(R.string.groups_error_email_invalid)
        val emailField = composeRule.onNodeWithText(viContext.getString(R.string.groups_invite_email))
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertEquals(expectedMessage, emailField.config[SemanticsProperties.Error])
        composeRule.onNodeWithText(expectedMessage).assertIsDisplayed()
        composeRule.onAllNodesWithText("server says invite email is invalid").assertCountEquals(0)
    }

    @Test
    fun serverMutationErrorIsVisibleInsideActiveDialogWithErrorSemantics() {
        composeRule.setContent {
            SmartReminderTheme {
                GroupsDialogHost(
                    dialog = GroupsDialog.InviteMember(CollaborationGroupId("group-1")),
                    uiState = GroupsUiState(
                        dialog = GroupsDialog.InviteMember(CollaborationGroupId("group-1")),
                        error = GroupsUiError.NotAuthorized
                    ),
                    onAction = {}
                )
            }
        }

        val errorMessage = composeRule.activity.getString(R.string.groups_error_not_authorized)
        val errorNode = composeRule.onNodeWithText(errorMessage).assertIsDisplayed()
            .fetchSemanticsNode()
        assertEquals(errorMessage, errorNode.config[SemanticsProperties.Error])
    }

    @Test
    fun memberActionControlRemainsIndependentlyAccessible() {
        val group = group("group-1", "Household")
        val actor = member(group.id.value, "owner-1", GroupRole.OWNER)
        val target = member(group.id.value, "member-1", GroupRole.MEMBER)
        val actions = mutableListOf<GroupsAction>()
        composeRule.setContent {
            SmartReminderTheme {
                GroupDetailScreen(
                    uiState = detailState(group, listOf(actor, target)),
                    onAction = { actions += it },
                    taskUiState = GroupTasksUiState(),
                    onTaskAction = {}
                )
            }
        }

        val memberActionsDescription = composeRule.activity.getString(
            R.string.groups_member_actions_description,
            "Member"
        )
        composeRule.onNodeWithContentDescription(memberActionsDescription).performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_change_role))
            .performClick()
        assertTrue(
            actions.contains(GroupsAction.OpenChangeMemberRoleDialog(target.userId))
        )
    }

    @Test
    fun actionRowsRemainVisibleAtTwoHundredPercentFontScaleInLightAndDarkThemes() {
        val group = group("group-1", "Household")
        val actor = member(group.id.value, "owner-1", GroupRole.OWNER)
        val state = detailState(group, listOf(actor))
        val expectedActions = listOf(
            R.string.groups_edit_group,
            R.string.groups_invite_member,
            R.string.groups_leave_group,
            R.string.groups_delete_group
        ).map(composeRule.activity::getString)

        val darkTheme = mutableStateOf(false)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                SmartReminderTheme(darkTheme = darkTheme.value) {
                    GroupDetailScreen(
                        uiState = state,
                        onAction = {},
                        taskUiState = GroupTasksUiState(),
                        onTaskAction = {}
                    )
                }
            }
        }
        expectedActions.forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
        composeRule.runOnIdle { darkTheme.value = true }
        expectedActions.forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test
    fun detailBackButtonDispatchesTypedBackAction() {
        val group = group("group-1", "Household")
        val actions = mutableListOf<GroupsAction>()
        composeRule.setContent {
            SmartReminderTheme {
                GroupDetailScreen(
                    uiState = detailState(group, listOf(member(group.id.value, "owner-1", GroupRole.OWNER))),
                    onAction = { actions += it },
                    taskUiState = GroupTasksUiState(),
                    onTaskAction = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            composeRule.activity.getString(R.string.groups_detail_back_description)
        ).performClick()
        assertEquals(listOf(GroupsAction.Back), actions)
    }

    private fun group(id: String, name: String): CollaborationGroup = CollaborationGroup(
        id = CollaborationGroupId(id),
        name = name,
        description = "Description",
        createdBy = UserId("owner-1"),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private fun member(
        groupId: String,
        userId: String,
        role: GroupRole,
        avatarUrl: String? = null
    ): GroupMember = GroupMember(
        groupId = CollaborationGroupId(groupId),
        userId = UserId(userId),
        role = role,
        joinedAt = Instant.parse("2026-01-01T00:00:00Z"),
        displayName = if (userId == "owner-1") "Ari" else "Member",
        avatarUrl = avatarUrl
    )

    private fun invite(id: String, groupId: String): GroupInvite = GroupInvite(
        id = GroupInviteId(id),
        groupId = CollaborationGroupId(groupId),
        inviterId = UserId("owner-1"),
        inviteeUserId = UserId("member-1"),
        status = GroupInviteStatus.PENDING,
        createdAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private fun detailState(
        group: CollaborationGroup,
        members: List<GroupMember>,
        detailLoadState: GroupsDetailLoadState = GroupsDetailLoadState.CONTENT,
        detailError: GroupsUiError? = null,
        isDetailAccessRestricted: Boolean = false
    ): GroupsUiState {
        val actor = members.firstOrNull { it.userId == UserId("owner-1") }
        val actorPermissions = actor?.let {
            GroupPermissions(
                canEditGroup = true,
                canInviteMember = true,
                canChangeRoles = true,
                canTransferOwnership = true,
                canDeleteGroup = true
            )
        }
        return GroupsUiState(
            loadState = GroupsLoadState.CONTENT,
            detailLoadState = detailLoadState,
            screen = GroupsScreen.DETAIL,
            selectedGroupId = group.id,
            selectedGroup = GroupDetailUiModel(
                group = group,
                members = members,
                permissionsByMemberId = members.associate {
                    it.userId to GroupPermissions(
                        canEditGroup = false,
                        canInviteMember = false,
                        canChangeRoles = false,
                        canTransferOwnership = false,
                        canDeleteGroup = false
                    )
                },
                currentUserId = actor?.userId,
                currentUserRole = actor?.role,
                actorPermissions = actorPermissions,
                memberActionsByMemberId = members.associate {
                    it.userId to GroupMemberUiPermissions(
                        canChangeRole = true,
                        canRemove = true,
                        canTransferOwnership = true
                    )
                },
                canLeaveGroup = actor != null
            ),
            detailError = detailError,
            isDetailAccessRestricted = isDetailAccessRestricted
        )
    }
}
