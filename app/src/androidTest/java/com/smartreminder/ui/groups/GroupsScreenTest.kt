package com.smartreminder.ui.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.smartreminder.R
import com.smartreminder.domain.collaboration.GroupPermissions
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.ui.theme.SmartReminderTheme
import java.time.Instant
import org.junit.Assert.assertEquals
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
    fun detailHidesUnauthorizedMembershipActionsAndShowsG3Lock() {
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
                GroupDetailScreen(uiState = state, onAction = {})
            }
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.groups_tasks_coming_g3)).assertIsDisplayed()
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

    private fun group(id: String, name: String): CollaborationGroup = CollaborationGroup(
        id = CollaborationGroupId(id),
        name = name,
        description = "Description",
        createdBy = UserId("owner-1"),
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z")
    )

    private fun member(groupId: String, userId: String, role: GroupRole): GroupMember = GroupMember(
        groupId = CollaborationGroupId(groupId),
        userId = UserId(userId),
        role = role,
        joinedAt = Instant.parse("2026-01-01T00:00:00Z"),
        displayName = "Member"
    )
}
