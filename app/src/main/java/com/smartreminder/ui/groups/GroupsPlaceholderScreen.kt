package com.smartreminder.ui.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.smartreminder.R
import com.smartreminder.ui.theme.CueTheme
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.SmartReminderTheme

private data class GroupPreviewModel(
    val members: List<PreviewMember>,
    val tasks: List<PreviewTask>
)

private data class PreviewMember(
    val nameRes: Int,
    val initialsRes: Int,
    val isCurrentUser: Boolean
)

private data class PreviewTask(
    val id: String,
    val titleRes: Int,
    val assigneeRes: Int,
    val dueRes: Int,
    val accent: PreviewTaskAccent,
    val isCurrentUser: Boolean
)

private enum class PreviewTaskAccent {
    ACCENT,
    SUCCESS,
    WARNING
}

private val GROUPS_PREVIEW = GroupPreviewModel(
    members = listOf(
        PreviewMember(
            nameRes = R.string.groups_preview_member_minh,
            initialsRes = R.string.groups_preview_member_initials_minh,
            isCurrentUser = false
        ),
        PreviewMember(
            nameRes = R.string.groups_preview_member_you,
            initialsRes = R.string.groups_preview_member_initials_you,
            isCurrentUser = true
        ),
        PreviewMember(
            nameRes = R.string.groups_preview_member_lan,
            initialsRes = R.string.groups_preview_member_initials_lan,
            isCurrentUser = false
        ),
        PreviewMember(
            nameRes = R.string.groups_preview_member_khoa,
            initialsRes = R.string.groups_preview_member_initials_khoa,
            isCurrentUser = false
        )
    ),
    tasks = listOf(
        PreviewTask(
            id = "database",
            titleRes = R.string.groups_preview_task_database,
            assigneeRes = R.string.groups_preview_member_minh,
            dueRes = R.string.groups_preview_due_aug_20,
            accent = PreviewTaskAccent.SUCCESS,
            isCurrentUser = false
        ),
        PreviewTask(
            id = "login",
            titleRes = R.string.groups_preview_task_login,
            assigneeRes = R.string.groups_preview_member_you,
            dueRes = R.string.groups_preview_due_tomorrow,
            accent = PreviewTaskAccent.ACCENT,
            isCurrentUser = true
        ),
        PreviewTask(
            id = "presentation",
            titleRes = R.string.groups_preview_task_presentation,
            assigneeRes = R.string.groups_preview_member_lan,
            dueRes = R.string.groups_preview_due_aug_23,
            accent = PreviewTaskAccent.WARNING,
            isCurrentUser = false
        )
    )
)

@Composable
fun GroupsPlaceholderScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CueTheme.colors.background)
    ) {
        GroupPreviewHeader()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = CueSpacing.Xl,
                end = CueSpacing.Xl,
                bottom = CueSpacing.Xxl
            ),
            verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
        ) {
            item(key = "assignment") {
                GroupAssignmentBanner()
            }
            item(key = "members") {
                Column {
                    Spacer(modifier = Modifier.height(CueSpacing.Xl))
                    GroupMembersSection(members = GROUPS_PREVIEW.members)
                }
            }
            item(key = "task_heading") {
                Column {
                    Spacer(modifier = Modifier.height(CueSpacing.Xl))
                    GroupTaskHeader()
                }
            }
            items(
                items = GROUPS_PREVIEW.tasks,
                key = { task -> "task_${task.id}" }
            ) { task ->
                PreviewTaskCard(task = task)
            }
            item(key = "add_task") {
                Column {
                    Spacer(modifier = Modifier.height(CueSpacing.Sm))
                    AddTaskPreviewButton()
                }
            }
        }
    }
}

@Composable
private fun GroupPreviewHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Lg),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.groups_preview_title),
                style = MaterialTheme.typography.titleLarge,
                color = CueTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.groups_preview_static_note),
                style = MaterialTheme.typography.bodySmall,
                color = CueTheme.colors.textSecondary
            )
        }
        IconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.defaultMinSize(
                minWidth = CueSpacing.Xxxl,
                minHeight = CueSpacing.Xxxl
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.groups_preview_settings_description)
            )
        }
    }
}

@Composable
private fun GroupAssignmentBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = CueTheme.colors.accentContainer,
        border = BorderStroke(1.dp, CueTheme.colors.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CueSpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(CueSpacing.Md),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(CueSpacing.Xxxl)
                    .background(CueTheme.colors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Assignment,
                    contentDescription = null,
                    tint = CueTheme.colors.onCta,
                    modifier = Modifier.size(CueSpacing.Lg)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CueSpacing.Xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = stringResource(R.string.groups_preview_assignment_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = CueTheme.colors.accentStrong
                    )
                    Text(
                        text = stringResource(R.string.groups_preview_assignment_time),
                        style = MaterialTheme.typography.bodySmall,
                        color = CueTheme.colors.textSecondary
                    )
                }
                Text(
                    text = stringResource(
                        R.string.groups_preview_assignment_message,
                        stringResource(R.string.groups_preview_assignment_task)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CueTheme.colors.textPrimary
                )
            }
        }
    }
}

@Composable
private fun GroupMembersSection(
    members: List<PreviewMember>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pluralStringResource(
                    id = R.plurals.groups_preview_members_count,
                    count = members.size,
                    members.size
                ),
                style = MaterialTheme.typography.titleMedium,
                color = CueTheme.colors.textPrimary
            )
            TextButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.heightIn(min = CueSpacing.Xxxl)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    modifier = Modifier.size(CueSpacing.Lg)
                )
                Spacer(modifier = Modifier.width(CueSpacing.Xs))
                Text(
                    text = stringResource(R.string.groups_preview_invite),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(CueSpacing.Lg),
            color = CueTheme.colors.surface,
            border = BorderStroke(1.dp, CueTheme.colors.border)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = CueSpacing.Lg, vertical = CueSpacing.Lg),
                horizontalArrangement = Arrangement.spacedBy(CueSpacing.Lg)
            ) {
                items(
                    items = members,
                    key = { member -> "member_${member.nameRes}" }
                ) { member ->
                    PreviewMember(member = member)
                }
            }
        }
    }
}

@Composable
private fun PreviewMember(member: PreviewMember) {
    val memberName = stringResource(member.nameRes)
    val memberDescription = stringResource(
        R.string.groups_preview_member_avatar_description,
        memberName
    )
    Column(
        modifier = Modifier
            .width(CueSpacing.Xxl + CueSpacing.Xl)
            .semantics(mergeDescendants = true) {
                contentDescription = memberDescription
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CueSpacing.Xs)
    ) {
        Box(
            modifier = Modifier
                .size(CueSpacing.Xxxl)
                .background(
                    color = if (member.isCurrentUser) {
                        CueTheme.colors.accentContainer
                    } else {
                        CueTheme.colors.surfaceSubtle
                    },
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = if (member.isCurrentUser) {
                        CueTheme.colors.accent
                    } else {
                        CueTheme.colors.border
                    },
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(member.initialsRes),
                style = MaterialTheme.typography.titleMedium,
                color = if (member.isCurrentUser) {
                    CueTheme.colors.accentStrong
                } else {
                    CueTheme.colors.textSecondary
                }
            )
        }
        Text(
            text = memberName,
            style = MaterialTheme.typography.bodySmall,
            color = if (member.isCurrentUser) {
                CueTheme.colors.accentStrong
            } else {
                CueTheme.colors.textPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GroupTaskHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.groups_preview_task_list_title),
            style = MaterialTheme.typography.titleMedium,
            color = CueTheme.colors.textPrimary
        )
        IconButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.defaultMinSize(
                minWidth = CueSpacing.Xxxl,
                minHeight = CueSpacing.Xxxl
            )
        ) {
            Icon(
                imageVector = Icons.Outlined.FilterList,
                contentDescription = stringResource(R.string.groups_preview_filter_description)
            )
        }
    }
}

@Composable
private fun PreviewTaskCard(
    task: PreviewTask,
    modifier: Modifier = Modifier
) {
    val indicatorColor = when (task.accent) {
        PreviewTaskAccent.ACCENT -> CueTheme.colors.accent
        PreviewTaskAccent.SUCCESS -> CueTheme.colors.success
        PreviewTaskAccent.WARNING -> CueTheme.colors.warning
    }
    val taskTitleColor = if (task.isCurrentUser) {
        CueTheme.colors.accentStrong
    } else {
        CueTheme.colors.textPrimary
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CueSpacing.Lg),
        color = CueTheme.colors.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (task.isCurrentUser) CueTheme.colors.accent else CueTheme.colors.border
        )
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
                    .size(width = CueSpacing.Xs, height = CueSpacing.Xxl)
                    .background(indicatorColor, RoundedCornerShape(CueSpacing.Xs))
            )
            Checkbox(
                checked = false,
                onCheckedChange = null,
                enabled = false,
                modifier = Modifier.size(CueSpacing.Xxxl)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CueSpacing.Xs)
            ) {
                Text(
                    text = stringResource(task.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = taskTitleColor
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CueSpacing.Sm)
                ) {
                    Surface(
                        shape = RoundedCornerShape(CueSpacing.Sm),
                        color = if (task.isCurrentUser) {
                            CueTheme.colors.accentContainer
                        } else {
                            CueTheme.colors.surfaceSubtle
                        }
                    ) {
                        Text(
                            text = stringResource(task.assigneeRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (task.isCurrentUser) {
                                CueTheme.colors.accentStrong
                            } else {
                                CueTheme.colors.textSecondary
                            },
                            modifier = Modifier.padding(horizontal = CueSpacing.Sm, vertical = CueSpacing.Xs)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CueSpacing.Xs)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Event,
                            contentDescription = null,
                            tint = CueTheme.colors.textSecondary,
                            modifier = Modifier.size(CueSpacing.Lg)
                        )
                        Text(
                            text = stringResource(task.dueRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = CueTheme.colors.textSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddTaskPreviewButton(modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CueSpacing.Xxxl),
        shape = RoundedCornerShape(CueSpacing.Lg),
        border = BorderStroke(1.dp, CueTheme.colors.border)
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(CueSpacing.Sm))
        Text(
            text = stringResource(R.string.groups_preview_add_task),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupsPlaceholderScreenPreview() {
    SmartReminderTheme {
        GroupsPlaceholderScreen()
    }
}
