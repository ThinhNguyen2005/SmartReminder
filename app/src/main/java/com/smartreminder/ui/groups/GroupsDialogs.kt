package com.smartreminder.ui.groups

import androidx.annotation.StringRes
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.smartreminder.R
import com.smartreminder.domain.model.collaboration.GroupMember
import com.smartreminder.domain.model.collaboration.GroupRole
import com.smartreminder.domain.model.collaboration.ids.UserId
import com.smartreminder.ui.theme.CueTheme
import com.smartreminder.ui.theme.CueSpacing

@Composable
fun GroupsDialogHost(
    dialog: GroupsDialog?,
    uiState: GroupsUiState,
    onAction: (GroupsAction) -> Unit
) {
    when (dialog) {
        GroupsDialog.CreateGroup -> GroupEditorDialog(
            dialogKey = dialog,
            titleRes = R.string.groups_create_dialog_title,
            confirmRes = R.string.groups_confirm_create_group,
            initialName = "",
            initialDescription = "",
            pending = uiState.isMutationInProgress,
            error = uiState.error,
            onDismiss = { onAction(GroupsAction.DismissDialog) },
            onInputChanged = { onAction(GroupsAction.DismissError) },
            onConfirm = { name, description ->
                onAction(GroupsAction.CreateGroup(name, description))
            }
        )
        is GroupsDialog.UpdateGroup -> {
            val group = uiState.selectedGroup?.group
                ?: uiState.groups.firstOrNull { it.id == dialog.groupId }
            if (group != null) {
                GroupEditorDialog(
                    dialogKey = dialog,
                    titleRes = R.string.groups_update_dialog_title,
                    confirmRes = R.string.groups_save_changes,
                    initialName = group.name,
                    initialDescription = group.description.orEmpty(),
                    pending = uiState.isMutationInProgress,
                    error = uiState.error,
                    onDismiss = { onAction(GroupsAction.DismissDialog) },
                    onInputChanged = { onAction(GroupsAction.DismissError) },
                    onConfirm = { name, description ->
                        onAction(GroupsAction.UpdateGroup(name, description))
                    }
                )
            }
        }
        is GroupsDialog.InviteMember -> InviteMemberDialog(
            dialogKey = dialog,
            pending = uiState.isMutationInProgress,
            error = uiState.error,
            onDismiss = { onAction(GroupsAction.DismissDialog) },
            onInputChanged = { onAction(GroupsAction.DismissError) },
            onConfirm = { email -> onAction(GroupsAction.InviteMember(email)) }
        )
        is GroupsDialog.ChangeMemberRole -> {
            val member = uiState.selectedGroup?.members
                ?.firstOrNull { it.userId == dialog.memberId }
            if (member != null) {
                ChangeMemberRoleDialog(
                    dialogKey = dialog,
                    member = member,
                    pending = uiState.isMutationInProgress,
                    error = uiState.error,
                    onDismiss = { onAction(GroupsAction.DismissDialog) },
                    onInputChanged = { onAction(GroupsAction.DismissError) },
                    onConfirm = { role ->
                        onAction(GroupsAction.ChangeMemberRole(member.userId, role))
                    }
                )
            }
        }
        is GroupsDialog.RemoveMember -> {
            val member = memberFor(uiState, dialog.memberId)
            if (member != null) {
                DestructiveGroupDialog(
                    titleRes = R.string.groups_remove_dialog_title,
                    messageRes = R.string.groups_confirm_remove_message,
                    pending = uiState.isMutationInProgress,
                    error = uiState.error,
                    onDismiss = { onAction(GroupsAction.DismissDialog) },
                    onConfirm = { onAction(GroupsAction.RemoveMember(member.userId)) }
                )
            }
        }
        is GroupsDialog.TransferOwnership -> {
            val member = memberFor(uiState, dialog.memberId)
            if (member != null) {
                DestructiveGroupDialog(
                    titleRes = R.string.groups_transfer_dialog_title,
                    messageRes = R.string.groups_confirm_transfer_message,
                    pending = uiState.isMutationInProgress,
                    error = uiState.error,
                    onDismiss = { onAction(GroupsAction.DismissDialog) },
                    onConfirm = { onAction(GroupsAction.TransferOwnership(member.userId)) }
                )
            }
        }
        GroupsDialog.LeaveGroup -> DestructiveGroupDialog(
            titleRes = R.string.groups_leave_dialog_title,
            messageRes = R.string.groups_confirm_leave_message,
            pending = uiState.isMutationInProgress,
            error = uiState.error,
            onDismiss = { onAction(GroupsAction.DismissDialog) },
            onConfirm = { onAction(GroupsAction.LeaveGroup) }
        )
        GroupsDialog.DeleteGroup -> DestructiveGroupDialog(
            titleRes = R.string.groups_delete_dialog_title,
            messageRes = R.string.groups_confirm_delete_message,
            pending = uiState.isMutationInProgress,
            error = uiState.error,
            onDismiss = { onAction(GroupsAction.DismissDialog) },
            onConfirm = { onAction(GroupsAction.DeleteGroup) }
        )
        is GroupsDialog.RespondToInvite -> {
            val invite = uiState.pendingInvites.firstOrNull { it.id == dialog.inviteId }
            if (invite != null) {
                InviteResponseDialog(
                    dialogKey = dialog,
                    pending = uiState.isMutationInProgress,
                    error = uiState.error,
                    onDismiss = { onAction(GroupsAction.DismissDialog) },
                    onChoiceChanged = { onAction(GroupsAction.DismissError) },
                    onAccept = { onAction(GroupsAction.AcceptInvite(invite.id)) },
                    onDecline = { onAction(GroupsAction.DeclineInvite(invite.id)) }
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun GroupEditorDialog(
    dialogKey: Any,
    @StringRes titleRes: Int,
    @StringRes confirmRes: Int,
    initialName: String,
    initialDescription: String,
    pending: Boolean,
    error: GroupsUiError?,
    onDismiss: () -> Unit,
    onInputChanged: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var name by remember(dialogKey) { mutableStateOf(initialName) }
    var description by remember(dialogKey) { mutableStateOf(initialDescription) }
    val nameError = if (
        error is GroupsUiError.Validation &&
        error.kind == GroupsValidationKind.GROUP_NAME_REQUIRED
    ) {
        groupsErrorMessage(error)
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        onInputChanged()
                        name = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.groups_group_name)) },
                    singleLine = false,
                    enabled = !pending,
                    isError = nameError != null,
                    supportingText = {
                        nameError?.let { message ->
                            Text(
                                text = message,
                                modifier = Modifier.semantics {
                                    error(message)
                                    liveRegion = LiveRegionMode.Polite
                                }
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        onInputChanged()
                        description = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.groups_group_description)) },
                    minLines = 2,
                    enabled = !pending
                )
                DialogError(
                    error.takeUnless {
                        it is GroupsUiError.Validation &&
                            it.kind == GroupsValidationKind.GROUP_NAME_REQUIRED
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name.trim(), description.trim().takeIf(String::isNotBlank))
                },
                enabled = !pending,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(CueSpacing.Lg)
            ) {
                if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CueSpacing.Lg),
                        strokeWidth = CueSpacing.Xs / 2f,
                        color = CueTheme.colors.onCta
                    )
                } else {
                    Text(text = stringResource(confirmRes))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !pending,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
            ) {
                Text(text = stringResource(R.string.groups_cancel))
            }
        }
    )
}

@Composable
private fun InviteMemberDialog(
    dialogKey: Any,
    pending: Boolean,
    error: GroupsUiError?,
    onDismiss: () -> Unit,
    onInputChanged: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var email by remember(dialogKey) { mutableStateOf("") }
    val emailError = if (
        error is GroupsUiError.Validation &&
        error.kind == GroupsValidationKind.EMAIL_INVALID
    ) {
        groupsErrorMessage(error)
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.groups_invite_member_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Md)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        onInputChanged()
                        email = it
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(R.string.groups_invite_email)) },
                    singleLine = false,
                    enabled = !pending,
                    isError = emailError != null,
                    supportingText = {
                        emailError?.let { message ->
                            Text(
                                text = message,
                                modifier = Modifier.semantics {
                                    error(message)
                                    liveRegion = LiveRegionMode.Polite
                                }
                            )
                        }
                    }
                )
                DialogError(
                    error.takeUnless {
                        it is GroupsUiError.Validation &&
                            it.kind == GroupsValidationKind.EMAIL_INVALID
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(email.trim()) },
                enabled = !pending,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(CueSpacing.Lg)
            ) {
                if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CueSpacing.Lg),
                        strokeWidth = CueSpacing.Xs / 2f,
                        color = CueTheme.colors.onCta
                    )
                } else {
                    Text(text = stringResource(R.string.groups_send_invite))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !pending,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
            ) {
                Text(text = stringResource(R.string.groups_cancel))
            }
        }
    )
}

@Composable
private fun ChangeMemberRoleDialog(
    dialogKey: Any,
    member: GroupMember,
    pending: Boolean,
    error: GroupsUiError?,
    onDismiss: () -> Unit,
    onInputChanged: () -> Unit,
    onConfirm: (GroupRole) -> Unit
) {
    val initialRole = if (member.role == GroupRole.ADMIN) GroupRole.MEMBER else GroupRole.ADMIN
    var selectedRole by remember(dialogKey) { mutableStateOf(initialRole) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.groups_change_role_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)) {
                Text(
                    text = stringResource(R.string.groups_new_role),
                    style = MaterialTheme.typography.labelLarge,
                    color = CueTheme.colors.textSecondary
                )
                listOf(GroupRole.ADMIN, GroupRole.MEMBER).forEach { role ->
                    RoleOption(
                        role = role,
                        selected = selectedRole == role,
                        enabled = !pending,
                        onSelected = {
                            onInputChanged()
                            selectedRole = role
                        }
                    )
                }
                DialogError(error)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedRole) },
                enabled = !pending,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(CueSpacing.Lg)
            ) {
                if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CueSpacing.Lg),
                        strokeWidth = CueSpacing.Xs / 2f,
                        color = CueTheme.colors.onCta
                    )
                } else {
                    Text(text = stringResource(R.string.groups_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !pending,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
            ) {
                Text(text = stringResource(R.string.groups_cancel))
            }
        }
    )
}

@Composable
private fun RoleOption(
    role: GroupRole,
    selected: Boolean,
    enabled: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CueSpacing.Xxxl)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelected
            )
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(modifier = Modifier.width(CueSpacing.Sm))
        Text(text = stringResource(roleStringRes(role)))
    }
}

@Composable
private fun DestructiveGroupDialog(
    @StringRes titleRes: Int,
    @StringRes messageRes: Int,
    pending: Boolean,
    error: GroupsUiError?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Md)) {
                Text(text = stringResource(messageRes))
                DialogError(error)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !pending,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CueTheme.colors.error,
                    contentColor = CueTheme.colors.onCta
                ),
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(CueSpacing.Lg)
            ) {
                if (pending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(CueSpacing.Lg),
                        strokeWidth = CueSpacing.Xs / 2f,
                        color = CueTheme.colors.onCta
                    )
                } else {
                    Text(text = stringResource(R.string.groups_confirm))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !pending,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
            ) {
                Text(text = stringResource(R.string.groups_cancel))
            }
        }
    )
}

@Composable
private fun InviteResponseDialog(
    dialogKey: Any,
    pending: Boolean,
    error: GroupsUiError?,
    onDismiss: () -> Unit,
    onChoiceChanged: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var choice by remember(dialogKey) { mutableStateOf<InviteResponseChoice?>(null) }
    val isConfirming = choice != null
    AlertDialog(
        onDismissRequest = {
            if (isConfirming) {
                choice = null
            } else {
                onDismiss()
            }
        },
        title = { Text(text = stringResource(R.string.groups_invite_response_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CueSpacing.Md)) {
                Text(
                    text = when (choice) {
                        InviteResponseChoice.ACCEPT -> stringResource(
                            R.string.groups_confirm_invite_accept_message
                        )
                        InviteResponseChoice.DECLINE -> stringResource(
                            R.string.groups_confirm_invite_decline_message
                        )
                        null -> stringResource(R.string.groups_invite_response_choose_message)
                    }
                )
                DialogError(error)
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
            ) {
                if (choice == null) {
                    Button(
                        onClick = {
                            onChoiceChanged()
                            choice = InviteResponseChoice.ACCEPT
                        },
                        enabled = !pending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = CueSpacing.Xxxl),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(CueSpacing.Lg)
                    ) {
                        Text(text = stringResource(R.string.groups_accept_invite))
                    }
                    TextButton(
                        onClick = {
                            onChoiceChanged()
                            choice = InviteResponseChoice.DECLINE
                        },
                        enabled = !pending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = CueSpacing.Xxxl)
                    ) {
                        Text(text = stringResource(R.string.groups_decline_invite))
                    }
                } else {
                    Button(
                        onClick = {
                            if (choice == InviteResponseChoice.ACCEPT) onAccept() else onDecline()
                        },
                        enabled = !pending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = CueSpacing.Xxxl),
                        colors = if (choice == InviteResponseChoice.DECLINE) {
                            ButtonDefaults.buttonColors(
                                containerColor = CueTheme.colors.error,
                                contentColor = CueTheme.colors.onCta
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(CueSpacing.Lg)
                    ) {
                        if (pending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(CueSpacing.Lg),
                                strokeWidth = CueSpacing.Xs / 2f,
                                color = CueTheme.colors.onCta
                            )
                        } else {
                            Text(
                                text = stringResource(
                                    if (choice == InviteResponseChoice.ACCEPT) {
                                        R.string.groups_accept_invite
                                    } else {
                                        R.string.groups_decline_invite
                                    }
                                )
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isConfirming) choice = null else onDismiss()
                },
                enabled = !pending,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
            ) {
                Text(text = stringResource(R.string.groups_cancel))
            }
        }
    )
}

private enum class InviteResponseChoice {
    ACCEPT,
    DECLINE
}

@Composable
private fun DialogError(uiError: GroupsUiError?) {
    if (uiError == null) return
    val message = groupsErrorMessage(uiError)
    Text(
        text = message,
        color = CueTheme.colors.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                error(message)
                liveRegion = LiveRegionMode.Polite
            }
    )
}

private fun memberFor(uiState: GroupsUiState, memberId: UserId): GroupMember? =
    uiState.selectedGroup?.members?.firstOrNull { it.userId == memberId }

@StringRes
private fun roleStringRes(role: GroupRole): Int = when (role) {
    GroupRole.OWNER -> R.string.groups_role_owner
    GroupRole.ADMIN -> R.string.groups_role_admin
    GroupRole.MEMBER -> R.string.groups_role_member
}
