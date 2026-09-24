package com.smartreminder.ui.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.smartreminder.R
import com.smartreminder.domain.model.collaboration.CollaborationGroup
import com.smartreminder.domain.model.collaboration.GroupInvite
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.CollaborationGroupId
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.ui.theme.CueTheme
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.SmartReminderTheme
import com.smartreminder.ui.groups.tasks.GroupTasksAction
import com.smartreminder.ui.groups.tasks.GroupTasksContent
import com.smartreminder.ui.groups.tasks.GroupTasksScreen
import com.smartreminder.ui.groups.tasks.GroupTasksUiState
import java.time.Instant

@Composable
fun GroupsListScreen(
    uiState: GroupsUiState,
    onAction: (GroupsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val screenDescription = stringResource(R.string.groups_screen_description)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CueTheme.colors.background)
            .semantics(mergeDescendants = false) {
                contentDescription = screenDescription
            }
    ) {
        GroupsListHeader(onNewGroup = { onAction(GroupsAction.OpenCreateGroupDialog) })
        GroupsListContent(uiState = uiState, onAction = onAction)
    }
}

@Composable
private fun GroupsListHeader(onNewGroup: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Md)
    ) {
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = CueTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.groups_title),
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary
            )
            Text(
                text = stringResource(R.string.groups_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = CueTheme.colors.textSecondary
            )
        }
        Button(
            onClick = onNewGroup,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CueSpacing.Xxxl),
            shape = RoundedCornerShape(CueSpacing.Lg),
            contentPadding = PaddingValues(horizontal = CueSpacing.Lg, vertical = CueSpacing.Sm)
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                modifier = Modifier.size(CueSpacing.Lg)
            )
            Spacer(modifier = Modifier.width(CueSpacing.Sm))
            Text(text = stringResource(R.string.groups_new_group))
        }
    }
}

@Composable
private fun GroupsListContent(
    uiState: GroupsUiState,
    onAction: (GroupsAction) -> Unit
) {
    val showOfflineBanner = uiState.loadState == GroupsLoadState.CACHED_OFFLINE ||
        uiState.loadState == GroupsLoadState.OFFLINE_REFRESHING
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CueSpacing.Xl,
            end = CueSpacing.Xl,
            bottom = CueSpacing.Xxl
        ),
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        if (showOfflineBanner) {
            item(key = "groups_offline_banner") {
                GroupsStatusBanner(
                    message = stringResource(
                        if (uiState.loadState == GroupsLoadState.OFFLINE_REFRESHING) {
                            R.string.groups_offline_refreshing
                        } else {
                            R.string.groups_offline_cached
                        }
                    ),
                    icon = {
                        Icon(
                            imageVector = if (uiState.loadState == GroupsLoadState.OFFLINE_REFRESHING) {
                                Icons.Outlined.Refresh
                            } else {
                                Icons.Outlined.Warning
                            },
                            contentDescription = null,
                            tint = CueTheme.colors.warning
                        )
                    },
                    action = {
                        TextButton(
                            onClick = { onAction(GroupsAction.Refresh) },
                            modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
                        ) {
                            Text(text = stringResource(R.string.groups_retry))
                        }
                    }
                )
            }
        }
        if (uiState.loadState == GroupsLoadState.ERROR && uiState.groups.isNotEmpty()) {
            item(key = "groups_error_banner") {
                GroupsErrorBanner(
                    error = uiState.error,
                    onRetry = { onAction(GroupsAction.Refresh) }
                )
            }
        }
        if (uiState.pendingInvites.isNotEmpty()) {
            item(key = "groups_pending_invites_heading") {
                Text(
                    text = stringResource(R.string.groups_invites_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = CueTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = CueSpacing.Sm)
                )
            }
            items(
                items = uiState.pendingInvites,
                key = { invite -> "invite_${invite.id.value}" }
            ) { invite ->
                PendingInviteCard(
                    invite = invite,
                    group = uiState.groups.firstOrNull { it.id == invite.groupId },
                    onReview = {
                        onAction(GroupsAction.OpenInviteResponseDialog(invite.id))
                    }
                )
            }
        }
        if (uiState.loadState == GroupsLoadState.LOADING && uiState.groups.isEmpty()) {
            item(key = "groups_loading") {
                GroupsLoadingState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp)
                )
            }
        } else if (uiState.loadState == GroupsLoadState.ERROR && uiState.groups.isEmpty()) {
            item(key = "groups_error") {
                GroupsErrorState(
                    uiError = uiState.error,
                    onRetry = { onAction(GroupsAction.Refresh) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp)
                )
            }
        } else if (uiState.groups.isEmpty()) {
            item(key = "groups_empty") {
                GroupsEmptyState()
            }
        } else {
            item(key = "groups_list_heading") {
                Text(
                    text = stringResource(R.string.groups_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = CueTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = CueSpacing.Sm)
                )
            }
            items(
                items = uiState.groups,
                key = { group -> "group_${group.id.value}" }
            ) { group ->
                GroupCard(
                    group = group,
                    onOpen = { onAction(GroupsAction.OpenGroup(group.id)) }
                )
            }
        }
        if (uiState.isMutationInProgress) {
            item(key = "groups_mutation_progress") {
                GroupsStatusBanner(
                    message = stringResource(R.string.groups_saving),
                    icon = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(CueSpacing.Lg),
                            strokeWidth = CueSpacing.Xs / 2
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: CollaborationGroup,
    onOpen: () -> Unit
) {
    val description = group.description?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.groups_group_no_description)
    val openDescription = stringResource(R.string.groups_open_group_description, group.name)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = openDescription
            }
            .clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = CueTheme.colors.surface,
        border = BorderStroke(1.dp, CueTheme.colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CueSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(CueSpacing.Xxxl)
                    .background(CueTheme.colors.accentContainer, RoundedCornerShape(CueSpacing.Md)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Group,
                    contentDescription = null,
                    tint = CueTheme.colors.accent,
                    modifier = Modifier.size(CueSpacing.Xl)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = CueTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CueTheme.colors.textSecondary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PendingInviteCard(
    invite: GroupInvite,
    group: CollaborationGroup?,
    onReview: () -> Unit
) {
    val title = if (group == null) {
        stringResource(R.string.groups_invite_unknown_group, invite.groupId.value)
    } else {
        stringResource(R.string.groups_invite_group, group.name)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) {
                contentDescription = title
            },
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = CueTheme.colors.accentContainer,
        border = BorderStroke(1.dp, CueTheme.colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CueSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.PersonAdd,
                contentDescription = null,
                tint = CueTheme.colors.accent,
                modifier = Modifier.size(CueSpacing.Xl)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = CueTheme.colors.textPrimary
                )
                Text(
                    text = stringResource(R.string.groups_invite_from, invite.inviterId.value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CueTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = onReview,
                    modifier = Modifier.heightIn(min = CueSpacing.Xxxl),
                    contentPadding = PaddingValues(horizontal = CueSpacing.Sm)
                ) {
                    Text(text = stringResource(R.string.groups_review_invite))
                }
            }
        }
    }
}

@Composable
private fun GroupsLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(CueSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.size(CueSpacing.Md))
        Text(
            text = stringResource(R.string.groups_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = CueTheme.colors.textSecondary
        )
    }
}

@Composable
private fun GroupsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = CueSpacing.Xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        Icon(
            imageVector = Icons.Outlined.Group,
            contentDescription = null,
            tint = CueTheme.colors.accent,
            modifier = Modifier.size(CueSpacing.Xxl)
        )
        Text(
            text = stringResource(R.string.groups_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = CueTheme.colors.textPrimary
        )
        Text(
            text = stringResource(R.string.groups_empty_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = CueTheme.colors.textSecondary
        )
    }
}

@Composable
private fun GroupsErrorState(
    uiError: GroupsUiError?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val message = groupsErrorMessage(uiError)
    Column(
        modifier = modifier.padding(CueSpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Md, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            tint = CueTheme.colors.error,
            modifier = Modifier.size(CueSpacing.Xxl)
        )
        Text(
            text = stringResource(R.string.groups_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = CueTheme.colors.textPrimary
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = CueTheme.colors.textSecondary,
            modifier = Modifier.semantics {
                error(message)
            }
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.heightIn(min = CueSpacing.Xxxl),
            shape = RoundedCornerShape(CueSpacing.Lg)
        ) {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(CueSpacing.Sm))
            Text(text = stringResource(R.string.groups_retry))
        }
    }
}

@Composable
private fun GroupsErrorBanner(
    error: GroupsUiError?,
    onRetry: () -> Unit
) {
    val message = groupsErrorMessage(error)
    GroupsStatusBanner(
        message = message,
        isError = true,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Warning,
                contentDescription = null,
                tint = CueTheme.colors.error
            )
        },
        action = {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
            ) {
                Text(text = stringResource(R.string.groups_retry))
            }
        }
    )
}

@Composable
private fun GroupsStatusBanner(
    message: String,
    icon: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null,
    isError: Boolean = false
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                if (isError) error(message)
            },
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = CueTheme.colors.surfaceSubtle,
        border = BorderStroke(1.dp, CueTheme.colors.border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Sm),
            verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon()
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CueTheme.colors.textSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        }
                )
            }
            action?.let { content ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    content()
                }
            }
        }
    }
}

@Composable
fun GroupDetailScreen(
    uiState: GroupsUiState,
    onAction: (GroupsAction) -> Unit,
    modifier: Modifier = Modifier,
    taskUiState: GroupTasksUiState,
    onTaskAction: (GroupTasksAction) -> Unit
) {
    if (taskUiState.screen != GroupTasksScreen.LIST) {
        GroupTasksContent(
            uiState = taskUiState,
            onAction = onTaskAction,
            modifier = modifier.fillMaxSize(),
            embedded = false
        )
        return
    }
    val selectedDetail = uiState.selectedGroup
    if (selectedDetail == null) {
        if (uiState.loadState == GroupsLoadState.LOADING) {
            GroupsLoadingState(modifier = modifier.fillMaxSize())
        } else {
            GroupsErrorState(
                uiError = uiState.error ?: GroupsUiError.NotFound,
                onRetry = { onAction(GroupsAction.Refresh) },
                modifier = modifier.fillMaxSize()
            )
        }
        return
    }
    val detail = if (
        uiState.isDetailAccessRestricted ||
        uiState.detailError is GroupsUiError.NotAuthorized ||
        uiState.detailError is GroupsUiError.NotFound
    ) {
        selectedDetail.copy(
            currentUserRole = null,
            actorPermissions = null,
            permissionsByMemberId = emptyMap(),
            memberActionsByMemberId = emptyMap(),
            canLeaveGroup = false
        )
    } else {
        selectedDetail
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CueTheme.colors.background)
    ) {
        val backDescription = stringResource(R.string.groups_detail_back_description)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Sm, vertical = CueSpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onAction(GroupsAction.Back) },
                modifier = Modifier
                    .size(CueSpacing.Xxxl)
                    .semantics { contentDescription = backDescription }
            ) {
                Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
            Text(
                text = detail.group.name,
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = CueSpacing.Xl,
                end = CueSpacing.Xl,
                bottom = CueSpacing.Xxl
            ),
            verticalArrangement = Arrangement.spacedBy(CueSpacing.Lg)
        ) {
            when (uiState.detailLoadState) {
                GroupsDetailLoadState.LOADING -> {
                    item(key = "group_detail_loading") {
                        GroupsLoadingState(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp)
                        )
                    }
                }
                GroupsDetailLoadState.OFFLINE_REFRESHING -> {
                    item(key = "group_detail_refreshing") {
                        GroupsStatusBanner(
                            message = stringResource(R.string.groups_offline_refreshing),
                            icon = {
                                LinearProgressIndicator(
                                    modifier = Modifier.width(CueSpacing.Xl),
                                    color = CueTheme.colors.accent
                                )
                            },
                            action = {
                                TextButton(
                                    onClick = { onAction(GroupsAction.OpenGroup(detail.id)) },
                                    modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
                                ) {
                                    Text(text = stringResource(R.string.groups_retry))
                                }
                            }
                        )
                    }
                }
                GroupsDetailLoadState.ERROR,
                GroupsDetailLoadState.CACHED_OFFLINE -> {
                    item(key = "group_detail_error") {
                        GroupsErrorBanner(
                            error = uiState.detailError,
                            onRetry = { onAction(GroupsAction.OpenGroup(detail.id)) }
                        )
                    }
                }
                GroupsDetailLoadState.IDLE,
                GroupsDetailLoadState.CONTENT -> Unit
            }
            item(key = "group_info") {
                GroupInfoCard(detail = detail, onAction = onAction)
            }
            item(key = "group_members_heading") {
                Text(
                    text = stringResource(R.string.groups_members_heading),
                    style = MaterialTheme.typography.titleMedium,
                    color = CueTheme.colors.textPrimary
                )
            }
            items(
                items = detail.members,
                key = { member -> "member_${member.userId.value}" }
            ) { member ->
                GroupMemberRow(
                    member = member,
                    permissions = detail.memberActionsByMemberId[member.userId],
                    onAction = onAction
                )
            }
            item(key = "group_tasks") {
                GroupTasksContent(
                    uiState = taskUiState,
                    onAction = onTaskAction,
                    modifier = Modifier.fillMaxWidth(),
                    embedded = true
                )
            }
        }
    }
}

@Composable
private fun GroupInfoCard(
    detail: GroupDetailUiModel,
    onAction: (GroupsAction) -> Unit
) {
    val actorPermissions = detail.actorPermissions
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = CueTheme.colors.surface,
        border = BorderStroke(1.dp, CueTheme.colors.border)
    ) {
        Column(
            modifier = Modifier.padding(CueSpacing.Lg),
            verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            Text(
                text = detail.group.name,
                style = MaterialTheme.typography.headlineLarge,
                color = CueTheme.colors.textPrimary
            )
            Text(
                text = detail.group.description?.takeIf(String::isNotBlank)
                    ?: stringResource(R.string.groups_group_no_description),
                style = MaterialTheme.typography.bodyLarge,
                color = CueTheme.colors.textSecondary
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
            ) {
                if (actorPermissions?.canEditGroup == true) {
                    OutlinedButton(
                        onClick = { onAction(GroupsAction.OpenUpdateGroupDialog) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = CueSpacing.Xxxl),
                        contentPadding = PaddingValues(horizontal = CueSpacing.Md)
                    ) {
                        Icon(imageVector = Icons.Outlined.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(CueSpacing.Xs))
                        Text(text = stringResource(R.string.groups_edit_group))
                    }
                }
                if (actorPermissions?.canInviteMember == true) {
                    Button(
                        onClick = { onAction(GroupsAction.OpenInviteMemberDialog) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = CueSpacing.Xxxl),
                        contentPadding = PaddingValues(horizontal = CueSpacing.Md),
                        shape = RoundedCornerShape(CueSpacing.Lg)
                    ) {
                        Icon(imageVector = Icons.Outlined.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(CueSpacing.Xs))
                        Text(text = stringResource(R.string.groups_invite_member))
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
            ) {
                if (detail.canLeaveGroup) {
                    TextButton(
                        onClick = { onAction(GroupsAction.OpenLeaveGroupDialog) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = CueSpacing.Xxxl)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Outlined.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(CueSpacing.Xs))
                        Text(text = stringResource(R.string.groups_leave_group))
                    }
                }
                if (actorPermissions?.canDeleteGroup == true) {
                    TextButton(
                        onClick = { onAction(GroupsAction.OpenDeleteGroupDialog) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = CueSpacing.Xxxl),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = CueTheme.colors.error
                        )
                    ) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(CueSpacing.Xs))
                        Text(text = stringResource(R.string.groups_delete_group))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupMemberRow(
    member: GroupMember,
    permissions: GroupMemberUiPermissions?,
    onAction: (GroupsAction) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val name = member.displayName?.takeIf(String::isNotBlank) ?: member.userId.value
    val roleDescription = groupRoleLabel(member.role)
    val actionsDescription = stringResource(R.string.groups_member_actions_description, name)
    val hasActions = permissions?.let {
        it.canChangeRole || it.canRemove || it.canTransferOwnership
    } == true
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = false) {
                contentDescription = name
                stateDescription = roleDescription
            },
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = CueTheme.colors.surface,
        border = BorderStroke(1.dp, CueTheme.colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CueSpacing.Lg, vertical = CueSpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MemberAvatar(member = member)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = CueTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                GroupRoleBadge(role = member.role)
            }
            if (hasActions) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(CueSpacing.Xxxl)
                            .semantics {
                                contentDescription = actionsDescription
                            }
                    ) {
                        Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (permissions?.canChangeRole == true) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.groups_change_role)) },
                                onClick = {
                                    menuExpanded = false
                                    onAction(GroupsAction.OpenChangeMemberRoleDialog(member.userId))
                                }
                            )
                        }
                        if (permissions?.canRemove == true) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.groups_remove_member)) },
                                onClick = {
                                    menuExpanded = false
                                    onAction(GroupsAction.OpenRemoveMemberDialog(member.userId))
                                }
                            )
                        }
                        if (permissions?.canTransferOwnership == true) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.groups_transfer_ownership)) },
                                onClick = {
                                    menuExpanded = false
                                    onAction(GroupsAction.OpenTransferOwnershipDialog(member.userId))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRoleBadge(role: GroupRole) {
    val roleLabel = groupRoleLabel(role)
    Surface(
        modifier = Modifier.semantics {
            stateDescription = roleLabel
        },
        shape = CircleShape,
        color = when (role) {
            GroupRole.OWNER -> CueTheme.colors.accentContainer
            GroupRole.ADMIN -> CueTheme.colors.surfaceSubtle
            GroupRole.MEMBER -> CueTheme.colors.surfaceSubtle
        }
    ) {
        Text(
            text = roleLabel,
            style = MaterialTheme.typography.labelSmall,
            color = when (role) {
                GroupRole.OWNER -> CueTheme.colors.accentStrong
                else -> CueTheme.colors.textSecondary
            },
            modifier = Modifier.padding(horizontal = CueSpacing.Sm, vertical = CueSpacing.Xs)
        )
    }
}

@Composable
private fun groupRoleLabel(role: GroupRole): String = stringResource(
    when (role) {
        GroupRole.OWNER -> R.string.groups_role_owner
        GroupRole.ADMIN -> R.string.groups_role_admin
        GroupRole.MEMBER -> R.string.groups_role_member
    }
)

@Composable
private fun MemberAvatar(member: GroupMember) {
    val name = member.displayName?.takeIf(String::isNotBlank) ?: member.userId.value
    val imageUrl = member.avatarUrl?.takeIf(String::isNotBlank)
    val avatarDescription = stringResource(R.string.groups_member_avatar_description, name)
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(CueSpacing.Xxxl)
            .border(2.dp, CueTheme.colors.borderStrong, CircleShape)
            .semantics {
                contentDescription = avatarDescription
            },
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null && !imageFailed) {
            AsyncImage(
                model = imageUrl,
                contentDescription = avatarDescription,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier
                    .size(CueSpacing.Xxl)
                    .clip(CircleShape)
            )
        } else {
            Text(
                text = initialsFor(name),
                style = MaterialTheme.typography.titleMedium,
                color = CueTheme.colors.accentStrong
            )
        }
    }
}

private fun initialsFor(name: String): String = name
    .trim()
    .split(Regex("\\s+"))
    .filter(String::isNotBlank)
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar() }
    .joinToString("")
    .ifBlank { "?" }

@androidx.annotation.StringRes
internal fun groupsErrorStringRes(error: GroupsUiError?): Int = when (error) {
    GroupsUiError.MissingConfiguration -> R.string.groups_error_missing_config
    GroupsUiError.Offline -> R.string.groups_error_offline
    GroupsUiError.NotAuthorized -> R.string.groups_error_not_authorized
    GroupsUiError.NotFound -> R.string.groups_error_not_found
    GroupsUiError.MemberNotFound -> R.string.groups_error_member_not_found
    GroupsUiError.AlreadyMember -> R.string.groups_error_already_member
    GroupsUiError.InviteAlreadyPending -> R.string.groups_error_invite_pending
    is GroupsUiError.Validation -> when (error.kind) {
        GroupsValidationKind.GROUP_NAME_REQUIRED -> R.string.groups_error_group_name_required
        GroupsValidationKind.EMAIL_INVALID -> R.string.groups_error_email_invalid
        GroupsValidationKind.ROLE_INVALID -> R.string.groups_error_role_invalid
        GroupsValidationKind.GENERAL -> R.string.groups_error_validation_general
    }
    is GroupsUiError.Conflict -> R.string.groups_error_conflict
    is GroupsUiError.InvalidState -> R.string.groups_error_invalid_state
    is GroupsUiError.MappingFailure -> R.string.groups_error_mapping
    is GroupsUiError.Unknown, null -> R.string.groups_error_unknown
}

@Composable
internal fun groupsErrorMessage(error: GroupsUiError?): String =
    stringResource(groupsErrorStringRes(error))

@Preview(showBackground = true)
@Composable
private fun GroupsListScreenPreview() {
    val previewGroup = CollaborationGroup(
        id = CollaborationGroupId("preview-group"),
        name = "Preview group",
        description = "Preview-only content",
        createdBy = UserId("preview-owner"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
    SmartReminderTheme {
        GroupsListScreen(
            uiState = GroupsUiState(
                loadState = GroupsLoadState.CONTENT,
                groups = listOf(previewGroup)
            ),
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupDetailScreenPreview() {
    val previewGroup = CollaborationGroup(
        id = CollaborationGroupId("preview-group"),
        name = "Preview group",
        description = "Preview-only content",
        createdBy = UserId("preview-owner"),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
    val previewMember = GroupMember(
        groupId = previewGroup.id,
        userId = UserId("preview-owner"),
        role = GroupRole.OWNER,
        joinedAt = Instant.EPOCH,
        displayName = "Preview owner"
    )
    SmartReminderTheme {
        GroupDetailScreen(
            uiState = GroupsUiState(
                loadState = GroupsLoadState.CONTENT,
                screen = GroupsScreen.DETAIL,
                selectedGroupId = previewGroup.id,
                selectedGroup = GroupDetailUiModel(
                    group = previewGroup,
                    members = listOf(previewMember),
                    permissionsByMemberId = emptyMap(),
                    actorPermissions = com.smartreminder.domain.collaboration.GroupPermissions(
                        canEditGroup = true,
                        canInviteMember = true,
                        canChangeRoles = true,
                        canTransferOwnership = true,
                        canDeleteGroup = true
                    ),
                    memberActionsByMemberId = mapOf(
                        previewMember.userId to GroupMemberUiPermissions(
                            canChangeRole = false,
                            canRemove = false,
                            canTransferOwnership = false
                        )
                    )
                )
            ),
            onAction = {},
            taskUiState = GroupTasksUiState(),
            onTaskAction = {}
        )
    }
}
