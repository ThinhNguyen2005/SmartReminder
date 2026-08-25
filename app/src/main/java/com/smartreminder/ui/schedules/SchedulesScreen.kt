package com.smartreminder.ui.schedules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.smartreminder.R
import com.smartreminder.domain.model.schedule.ids.RoutineId
import com.smartreminder.domain.model.schedule.ids.ScheduleGroupId
import com.smartreminder.ui.schedules.components.GroupFilterRow
import com.smartreminder.ui.schedules.components.RoutineCard
import com.smartreminder.ui.schedules.components.SchedulesEmptyState
import com.smartreminder.ui.theme.CueSpacing
import com.smartreminder.ui.theme.SmartReminderTheme
import java.time.DayOfWeek

@Composable
fun SchedulesScreen(
    uiState: SchedulesUiState,
    onAction: (SchedulesAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage = when (uiState.error) {
        SchedulesError.LOAD_FAILED -> stringResource(R.string.schedules_error_load)
        SchedulesError.UPDATE_ROUTINE_FAILED -> stringResource(R.string.schedules_error_update_routine)
        null -> null
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            onAction(SchedulesAction.DismissError)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Unified Screen Header (Matching Today, Tasks, Profile headlineLarge design)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CueSpacing.Xl, vertical = CueSpacing.Xl),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.nav_schedules),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { onAction(SchedulesAction.ManageGroups) }) {
                        Icon(
                            imageVector = Icons.Outlined.Folder,
                            contentDescription = stringResource(R.string.schedules_manage_groups),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Group Filter Chips
                GroupFilterRow(
                    groups = uiState.groupFilters,
                    selectedGroupId = uiState.selectedGroupId,
                    onSelectGroup = { onAction(SchedulesAction.SelectGroup(it)) },
                    modifier = Modifier.padding(bottom = CueSpacing.Sm)
                )

                if (uiState.sections.isEmpty()) {
                    val filteredGroupName = uiState.groupFilters
                        .firstOrNull { it.id == uiState.selectedGroupId }
                        ?.name

                    SchedulesEmptyState(
                        groupName = filteredGroupName,
                        onCreateRoutine = { onAction(SchedulesAction.CreateRoutine) }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = CueSpacing.Xxxl + CueSpacing.Xxxl)
                    ) {
                        uiState.sections.forEach { section ->
                            val sectionLabel = section.label
                            if (sectionLabel != null) {
                                item(key = "section_${section.groupId?.value ?: "ungrouped"}") {
                                    val sectionHeader = when (sectionLabel) {
                                        is RoutineSectionLabelUiModel.Group -> sectionLabel.name
                                        RoutineSectionLabelUiModel.Ungrouped -> stringResource(R.string.schedules_group_ungrouped)
                                    }

                                    Text(
                                        text = sectionHeader,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(
                                            horizontal = CueSpacing.Xl,
                                            vertical = CueSpacing.Md
                                        )
                                    )
                                }
                            }

                            items(
                                items = section.routines,
                                key = { it.id.value }
                            ) { routine ->
                                RoutineCard(
                                    routine = routine,
                                    onOpen = { onAction(SchedulesAction.OpenRoutine(routine.id)) },
                                    onSetEnabled = { onAction(SchedulesAction.SetRoutineEnabled(routine.id, it)) },
                                    modifier = Modifier.padding(
                                        horizontal = CueSpacing.Xl,
                                        vertical = CueSpacing.Xs
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button
        FloatingActionButton(
            onClick = { onAction(SchedulesAction.CreateRoutine) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(CueSpacing.Xl)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.schedules_create_routine_fab)
            )
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CueSpacing.Xl)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SchedulesScreenPopulatedPreview() {
    val sampleState = SchedulesUiState(
        isLoading = false,
        selectedGroupId = null,
        groupFilters = listOf(
            GroupFilterUiModel(ScheduleGroupId("study"), "Study"),
            GroupFilterUiModel(ScheduleGroupId("personal"), "Personal")
        ),
        sections = listOf(
            RoutineSectionUiModel(
                groupId = ScheduleGroupId("study"),
                label = RoutineSectionLabelUiModel.Group("Study"),
                routines = listOf(
                    RoutineCardUiModel(
                        id = RoutineId("univ"),
                        name = "University Day",
                        recurrence = RecurrenceUiModel(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)),
                        itemCount = 4,
                        enabled = true
                    ),
                    RoutineCardUiModel(
                        id = RoutineId("exam"),
                        name = "Exam Preparation",
                        recurrence = RecurrenceUiModel(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)),
                        itemCount = 3,
                        enabled = false
                    )
                )
            ),
            RoutineSectionUiModel(
                groupId = ScheduleGroupId("personal"),
                label = RoutineSectionLabelUiModel.Group("Personal"),
                routines = listOf(
                    RoutineCardUiModel(
                        id = RoutineId("morning"),
                        name = "Morning Routine",
                        recurrence = RecurrenceUiModel(DayOfWeek.entries.toSet()),
                        itemCount = 5,
                        enabled = true
                    )
                )
            )
        )
    )

    SmartReminderTheme {
        SchedulesScreen(
            uiState = sampleState,
            onAction = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SchedulesScreenEmptyPreview() {
    val sampleState = SchedulesUiState(
        isLoading = false,
        selectedGroupId = null,
        groupFilters = emptyList(),
        sections = emptyList()
    )

    SmartReminderTheme {
        SchedulesScreen(
            uiState = sampleState,
            onAction = {}
        )
    }
}
